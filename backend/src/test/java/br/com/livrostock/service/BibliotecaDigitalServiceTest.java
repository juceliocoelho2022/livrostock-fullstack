package br.com.livrostock.service;

import br.com.livrostock.domain.ExemplarDigital;
import br.com.livrostock.domain.Livro;
import br.com.livrostock.repository.ExemplarDigitalRepository;
import br.com.livrostock.repository.LivroRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class BibliotecaDigitalServiceTest {
    @TempDir Path tempDir;
    private LivroRepository livros;
    private ExemplarDigitalRepository exemplares;
    private BibliotecaDigitalService service;
    private AuditoriaService auditoria;

    @BeforeEach
    void preparar() {
        livros = mock(LivroRepository.class);
        exemplares = mock(ExemplarDigitalRepository.class);
        auditoria = mock(AuditoriaService.class);
        service = new BibliotecaDigitalService(livros, exemplares, auditoria, tempDir.toString());
    }

    private Livro livroAtivo() {
        return new Livro("Exemplo de teste", "Autoria própria", "9780000000001",
                "Tecnologia", new BigDecimal("12.50"), 2);
    }

    private void livroDisponivel() {
        when(livros.findById(1L)).thenReturn(Optional.of(livroAtivo()));
    }

    private MockMultipartFile pdf(byte[] bytes) {
        return new MockMultipartFile("arquivo", "exemplar.pdf", "application/pdf", bytes);
    }

    @Test
    void armazenaPdfComChecksumEReferenciaDeLivro() throws Exception {
        livroDisponivel();
        byte[] bytes = "%PDF-1.4\nconteudo de teste\n%%EOF\n".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        when(exemplares.saveAndFlush(any(ExemplarDigital.class))).thenAnswer(a -> a.getArgument(0));

        ExemplarDigital salvo = service.adicionar(1L, pdf(bytes));

        assertEquals("exemplar.pdf", salvo.getNomeOriginal());
        assertEquals("application/pdf", salvo.getTipoMime());
        assertEquals(bytes.length, salvo.getTamanhoBytes());
        assertEquals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)), salvo.getSha256());
        assertArrayEquals(bytes, Files.readAllBytes(tempDir.resolve(salvo.getNomeArmazenado())));
        assertTrue(salvo.getNomeArmazenado().matches("[a-f0-9-]{36}\\.pdf"));
        verify(exemplares).saveAndFlush(any(ExemplarDigital.class));
    }

    @Test
    void rejeitaConteudoQueNaoEUmPdfESemDeixarArquivoParcial() throws Exception {
        livroDisponivel();
        assertThrows(IllegalArgumentException.class,
                () -> service.adicionar(1L, pdf("arquivo qualquer".getBytes())));
        verifyNoInteractions(exemplares);
        try (var arquivos = Files.list(tempDir)) { assertEquals(0, arquivos.count()); }
    }

    @Test
    void rejeitaArquivoVazio() {
        livroDisponivel();
        assertThrows(IllegalArgumentException.class, () -> service.adicionar(1L, pdf(new byte[0])));
        verifyNoInteractions(exemplares);
    }

    @Test
    void rejeitaExtensaoNaoPdf() {
        livroDisponivel();
        MockMultipartFile arquivo = new MockMultipartFile("arquivo", "script.txt", "text/plain",
                "%PDF-1.4\n".getBytes());
        assertThrows(IllegalArgumentException.class, () -> service.adicionar(1L, arquivo));
        verifyNoInteractions(exemplares);
    }

    @Test
    void rejeitaTamanhoAcimaDeVinteMiBSemLerConteudo() throws Exception {
        livroDisponivel();
        MultipartFile arquivo = mock(MultipartFile.class);
        when(arquivo.isEmpty()).thenReturn(false);
        when(arquivo.getSize()).thenReturn(20L * 1024 * 1024 + 1);
        assertThrows(IllegalArgumentException.class, () -> service.adicionar(1L, arquivo));
        verify(arquivo, never()).getInputStream();
        verifyNoInteractions(exemplares);
    }

    @Test
    void rejeitaLivroInexistente() {
        when(livros.findById(99L)).thenReturn(Optional.empty());
        assertThrows(RecursoNaoEncontradoException.class,
                () -> service.adicionar(99L, pdf("%PDF-1.4".getBytes())));
        verifyNoInteractions(exemplares);
    }

    @Test
    void rejeitaLivroInativo() {
        Livro inativo = livroAtivo();
        inativo.desativar();
        when(livros.findById(1L)).thenReturn(Optional.of(inativo));
        assertThrows(RecursoNaoEncontradoException.class,
                () -> service.adicionar(1L, pdf("%PDF-1.4".getBytes())));
        verifyNoInteractions(exemplares);
    }

    @Test
    void removeArquivoSePersistenciaFalha() throws Exception {
        livroDisponivel();
        when(exemplares.saveAndFlush(any(ExemplarDigital.class)))
                .thenThrow(new IllegalStateException("Falha simulada de persistência"));
        assertThrows(IllegalStateException.class,
                () -> service.adicionar(1L, pdf("%PDF-1.4\nTeste".getBytes())));
        try (var arquivos = Files.list(tempDir)) { assertEquals(0, arquivos.count()); }
    }

    @Test
    void naoPermiteConsultarExemplarDeOutroLivro() {
        Livro relacionado = mock(Livro.class);
        when(relacionado.getId()).thenReturn(5L);
        ExemplarDigital exemplar = new ExemplarDigital(relacionado, "arquivo.pdf",
                "aleatorio.pdf", 9, "a".repeat(64));
        when(exemplares.findById(10L)).thenReturn(Optional.of(exemplar));
        assertThrows(RecursoNaoEncontradoException.class, () -> service.localizar(1L, 10L));
    }

    @Test
    void listaDocumentosDoLivro() {
        livroDisponivel();
        when(exemplares.findByLivroIdOrderByCriadoEmDesc(1L)).thenReturn(List.of());
        assertTrue(service.listar(1L).isEmpty());
        verify(exemplares).findByLivroIdOrderByCriadoEmDesc(1L);
    }

    @Test
    void naoExponeArquivoAusente() {
        ExemplarDigital exemplar = new ExemplarDigital(livroAtivo(), "test.pdf",
                "nao-existe.pdf", 10, "a".repeat(64));
        assertThrows(RecursoNaoEncontradoException.class, () -> service.abrirArquivo(exemplar));
    }
}
