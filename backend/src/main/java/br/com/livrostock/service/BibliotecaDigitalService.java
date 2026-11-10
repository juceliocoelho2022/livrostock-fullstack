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
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
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

    public BibliotecaDigitalService(LivroRepository livros, ExemplarDigitalRepository exemplares,
                                    @Value("${livrostock.storage.pdf-dir:./data/pdfs}") String pdfDir) {
        this.livros = livros;
        this.exemplares = exemplares;
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

    public Resource abrirArquivo(ExemplarDigital exemplar) {
        Path arquivo = diretorio.resolve(exemplar.getNomeArmazenado()).normalize();
        if (!arquivo.startsWith(diretorio) || !Files.isRegularFile(arquivo)) {
            throw new RecursoNaoEncontradoException("Arquivo digital indisponível");
        }
        return new FileSystemResource(arquivo);
    }
}
