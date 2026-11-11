package br.com.livrostock.service;

import br.com.livrostock.domain.ExemplarDigital;
import br.com.livrostock.domain.Livro;
import br.com.livrostock.repository.ExemplarDigitalRepository;
import br.com.livrostock.repository.LivroRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.LinkOption;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
public class BibliotecaDigitalService {
    private static final long LIMITE_BYTES = 20L * 1024 * 1024;
    private static final byte[] PDF_HEADER = {'%', 'P', 'D', 'F', '-'};
    private final LivroRepository livros;
    private final ExemplarDigitalRepository exemplares;
    private final Path diretorio;
    private final AuditoriaService auditoria;

    public BibliotecaDigitalService(LivroRepository livros, ExemplarDigitalRepository exemplares,
                                    AuditoriaService auditoria,
                                    @Value("${livrostock.storage.pdf-dir:./data/pdfs}") String pdfDir) {
        this.livros = livros;
        this.exemplares = exemplares;
        this.auditoria = auditoria;
        this.diretorio = Path.of(pdfDir).toAbsolutePath().normalize();
    }

    @Transactional
    public ExemplarDigital adicionar(Long livroId, MultipartFile arquivo) {
        Livro livro = livros.findById(livroId)
                .filter(Livro::isAtivo)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Livro não encontrado ou inativo"));
        if (arquivo == null || arquivo.isEmpty()) {
            throw new IllegalArgumentException("Selecione um arquivo PDF");
        }
        if (arquivo.getSize() > LIMITE_BYTES) {
            throw new IllegalArgumentException("PDF excede o limite de 20 MB");
        }
        String nomeOriginal = arquivo.getOriginalFilename();
        if (nomeOriginal == null || !nomeOriginal.toLowerCase(java.util.Locale.ROOT).endsWith(".pdf")) {
            throw new IllegalArgumentException("O arquivo deve ter extensão .pdf");
        }
        nomeOriginal = Path.of(nomeOriginal.replace('\\', '/')).getFileName().toString();
        if (nomeOriginal.length() > 255) {
            throw new IllegalArgumentException("Nome do arquivo muito longo");
        }
        Path destino = diretorio.resolve(UUID.randomUUID() + ".pdf").normalize();
        boolean concluido = false;
        try {
            Files.createDirectories(diretorio);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = arquivo.getInputStream();
                 var output = Files.newOutputStream(destino, StandardOpenOption.CREATE_NEW)) {
                byte[] header = input.readNBytes(5);
                if (!java.util.Arrays.equals(header, PDF_HEADER)) {
                    throw new IllegalArgumentException("Conteúdo inválido: não é um PDF");
                }
                output.write(header);
                digest.update(header);
                byte[] buffer = new byte[8192];
                int lidos;
                long total = header.length;
                while ((lidos = input.read(buffer)) != -1) {
                    total += lidos;
                    if (total > LIMITE_BYTES) {
                        throw new IllegalArgumentException("PDF excede o limite de 20 MB");
                    }
                    output.write(buffer, 0, lidos);
                    digest.update(buffer, 0, lidos);
                }
            }
            ExemplarDigital exemplar = new ExemplarDigital(
                    livro, nomeOriginal, destino.getFileName().toString(),
                    Files.size(destino), HexFormat.of().formatHex(digest.digest()));
            ExemplarDigital salvo = exemplares.saveAndFlush(exemplar);
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
                // Mesmo commit do registro do PDF: falha de auditoria cancela a transação.
                auditoria.registrar(auth.getName(), "PDF_UPLOAD", "POST",
                        "/api/v1/livros/" + livroId + "/exemplares-digitais", 201);
            }
            // O sistema de arquivos não é transacional; remover o PDF se o commit SQL falhar.
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCompletion(int status) {
                        if (status != STATUS_COMMITTED) {
                            try { Files.deleteIfExists(destino); }
                            catch (IOException ignored) { /* monitorar falhas de limpeza */ }
                        }
                    }
                });
            }
            concluido = true;
            return salvo;
        } catch (IOException | NoSuchAlgorithmException ex) {
            throw new IllegalStateException("Falha no armazenamento do arquivo", ex);
        } finally {
            if (!concluido) {
                try { Files.deleteIfExists(destino); }
                catch (IOException ignored) { /* limpeza de órfãos requer monitoramento */ }
            }
        }
    }

    @Transactional(readOnly = true)
    public List<ExemplarDigital> listar(Long livroId) {
        livros.findById(livroId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Livro não encontrado"));
        return exemplares.findByLivroIdOrderByCriadoEmDesc(livroId);
    }

    @Transactional(readOnly = true)
    public ExemplarDigital localizar(Long livroId, Long exemplarId) {
        ExemplarDigital exemplar = exemplares.findById(exemplarId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Exemplar não encontrado"));
        if (!exemplar.getLivro().getId().equals(livroId)) {
            throw new RecursoNaoEncontradoException("Exemplar não pertence ao livro");
        }
        return exemplar;
    }

    /** Resultado de auditoria sem divulgação do caminho físico do PDF. */
    public enum EstadoIntegridade { OK, AUSENTE, DIVERGENTE, ERRO_LEITURA }

    /**
     * Compara tamanho e SHA-256 de arquivos locais com o registro do banco.
     * Não altera arquivos nem metadados. Links simbólicos não são seguidos.
     */
    public EstadoIntegridade verificarIntegridade(ExemplarDigital exemplar) {
        Path arquivo = diretorio.resolve(exemplar.getNomeArmazenado()).normalize();
        if (!arquivo.startsWith(diretorio)) return EstadoIntegridade.DIVERGENTE;
        if (!Files.isRegularFile(arquivo, LinkOption.NOFOLLOW_LINKS)) return EstadoIntegridade.AUSENTE;

        try {
            if (Files.size(arquivo) != exemplar.getTamanhoBytes()) {
                return EstadoIntegridade.DIVERGENTE;
            }
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = Files.newInputStream(arquivo)) {
                byte[] buffer = new byte[8192];
                int lidos;
                while ((lidos = input.read(buffer)) != -1) {
                    digest.update(buffer, 0, lidos);
                }
            }
            String atual = HexFormat.of().formatHex(digest.digest());
            return atual.equalsIgnoreCase(exemplar.getSha256())
                    ? EstadoIntegridade.OK : EstadoIntegridade.DIVERGENTE;
        } catch (IOException | SecurityException e) {
            return EstadoIntegridade.ERRO_LEITURA;
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 não está disponível", e);
        }
    }

    /**
     * Só expõe PDFs verificados. Não confundir esta validação com antivírus.
     * Arquivos alterados não podem ser entregues ao leitor.
     */
    public Resource abrirArquivo(ExemplarDigital exemplar) {
        EstadoIntegridade estado = verificarIntegridade(exemplar);
        if (estado == EstadoIntegridade.AUSENTE) {
            throw new RecursoNaoEncontradoException("Arquivo digital indisponível");
        }
        if (estado == EstadoIntegridade.DIVERGENTE) {
            throw new IllegalStateException("Integridade do PDF comprometida");
        }
        if (estado == EstadoIntegridade.ERRO_LEITURA) {
            throw new IllegalStateException("Não foi possível verificar a integridade do PDF");
        }
        Path arquivo = diretorio.resolve(exemplar.getNomeArmazenado()).normalize();
        return new FileSystemResource(arquivo);
    }
}
