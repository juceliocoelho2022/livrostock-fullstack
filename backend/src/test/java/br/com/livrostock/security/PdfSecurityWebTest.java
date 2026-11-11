package br.com.livrostock.security;

import br.com.livrostock.api.AuthController;
import br.com.livrostock.api.UsuarioController;
import br.com.livrostock.domain.Usuario;
import br.com.livrostock.domain.PerfilUsuario;
import br.com.livrostock.repository.UsuarioRepository;
import br.com.livrostock.service.UsuarioService;
import br.com.livrostock.api.LivroController;
import br.com.livrostock.api.AuditoriaController;
import br.com.livrostock.service.EstoqueService;
import br.com.livrostock.service.AuditoriaService;
import org.springframework.data.domain.Page;
import br.com.livrostock.api.BibliotecaDigitalController;
import br.com.livrostock.domain.ExemplarDigital;
import br.com.livrostock.domain.Livro;
import br.com.livrostock.service.BibliotecaDigitalService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.security.core.userdetails.User;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers={AuthController.class, BibliotecaDigitalController.class, LivroController.class, AuditoriaController.class, UsuarioController.class})
@Import({PdfSecurityConfiguration.class, AuditoriaMvcConfiguration.class})
@ActiveProfiles("secure")
@TestPropertySource(properties={
        "LIVROSTOCK_JWT_SECRET=abcdefghijklmnopqrstuvwxzy0123456789-EXEMPLO-SOMENTE-TESTE",
        "LIVROSTOCK_LIBRARIAN_PASSWORD=senha-laboratorio-123",
        "LIVROSTOCK_READER_PASSWORD=leitor-laboratorio-123"
})
class PdfSecurityWebTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @MockitoBean BibliotecaDigitalService service;
    @MockitoBean EstoqueService estoque;
    @MockitoBean AuditoriaService auditoria;
    @MockitoBean UsuarioService usuarioService;
    @MockitoBean UsuarioRepository usuarios;
    private final AtomicInteger versaoBibliotecario = new AtomicInteger(0);
    private final AtomicBoolean bibliotecarioAtivo = new AtomicBoolean(true);

    @BeforeEach
    void configurarUsuariosPersistidos() {
        versaoBibliotecario.set(0);
        bibliotecarioAtivo.set(true);
        Usuario bibliotecario = mock(Usuario.class);
        when(bibliotecario.getVersaoToken()).thenAnswer(i -> versaoBibliotecario.get());
        when(bibliotecario.isAtivo()).thenAnswer(i -> bibliotecarioAtivo.get());
        when(bibliotecario.getPerfil()).thenReturn(PerfilUsuario.BIBLIOTECARIO);
        Usuario leitor = mock(Usuario.class);
        when(leitor.getVersaoToken()).thenReturn(0);
        when(leitor.isAtivo()).thenReturn(true);
        when(leitor.getPerfil()).thenReturn(PerfilUsuario.LEITOR);
        when(usuarios.findByNomeUsuario("bibliotecario")).thenReturn(Optional.of(bibliotecario));
        when(usuarios.findByNomeUsuario("leitor")).thenReturn(Optional.of(leitor));
        org.springframework.security.crypto.password.PasswordEncoder encoder =
                new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder();
        when(usuarioService.loadUserByUsername("bibliotecario")).thenReturn(
                User.withUsername("bibliotecario").password(encoder.encode("senha-laboratorio-123"))
                        .roles("BIBLIOTECARIO").build());
        when(usuarioService.loadUserByUsername("leitor")).thenReturn(
                User.withUsername("leitor").password(encoder.encode("leitor-laboratorio-123"))
                        .roles("LEITOR").build());
    }


    private String login(String usuario,String senha) throws Exception {
        String body = mapper.writeValueAsString(new AuthController.LoginRequest(usuario,senha));
        String json = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode response=mapper.readTree(json);
        return response.get("accessToken").asText();
    }

    @Test void loginIndicaModoSeguro() throws Exception {
        mvc.perform(get("/api/v1/auth/mode"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true));
    }

    @Test void semTokenNaoListaExemplares() throws Exception {
        mvc.perform(get("/api/v1/livros/1/exemplares-digitais"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test void tokenInvalidoNaoListaExemplares() throws Exception {
        mvc.perform(get("/api/v1/livros/1/exemplares-digitais")
                .header("Authorization","Bearer token-invalido"))
                .andExpect(status().isUnauthorized());
    }

    @Test void credenciaisInvalidasNaoGeramToken() throws Exception {
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"usuario\":\"leitor\",\"senha\":\"incorreta\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test void leitorPodeListarPDFs() throws Exception {
        String token=login("leitor","leitor-laboratorio-123");
        when(service.listar(1L)).thenReturn(List.of());
        mvc.perform(get("/api/v1/livros/1/exemplares-digitais")
                .header("Authorization","Bearer "+token))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test void leitorNaoPodeFazerUpload() throws Exception {
        String token=login("leitor","leitor-laboratorio-123");
        MockMultipartFile file=new MockMultipartFile("arquivo","teste.pdf","application/pdf","%PDF-1.4".getBytes());
        mvc.perform(multipart("/api/v1/livros/1/exemplares-digitais").file(file)
                .header("Authorization","Bearer "+token))
                .andExpect(status().isForbidden());
        verify(service,never()).adicionar(anyLong(),any());
    }

    @Test void bibliotecarioPodeFazerUpload() throws Exception {
        String token=login("bibliotecario","senha-laboratorio-123");
        Livro livro=mock(Livro.class);
        when(livro.getId()).thenReturn(1L);
        ExemplarDigital exemplar=mock(ExemplarDigital.class);
        when(exemplar.getLivro()).thenReturn(livro);
        when(exemplar.getId()).thenReturn(7L);
        when(exemplar.getNomeOriginal()).thenReturn("teste.pdf");
        when(exemplar.getTipoMime()).thenReturn("application/pdf");
        when(exemplar.getSha256()).thenReturn("a".repeat(64));
        when(service.adicionar(eq(1L),any())).thenReturn(exemplar);
        MockMultipartFile file=new MockMultipartFile("arquivo","teste.pdf","application/pdf","%PDF-1.4".getBytes());
        mvc.perform(multipart("/api/v1/livros/1/exemplares-digitais").file(file)
                .header("Authorization","Bearer "+token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.livroId").value(1));
    }

    @Test void semTokenNaoBaixaPdf() throws Exception {
        mvc.perform(get("/api/v1/livros/1/exemplares-digitais/10/arquivo"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test void leitorPodeLerPdfAutorizado() throws Exception {
        String token=login("leitor","leitor-laboratorio-123");
        Livro livro=mock(Livro.class);
        when(livro.getId()).thenReturn(1L);
        ExemplarDigital exemplar=mock(ExemplarDigital.class);
        when(exemplar.getLivro()).thenReturn(livro);
        when(service.localizar(1L,10L)).thenReturn(exemplar);
        when(service.abrirArquivo(exemplar))
                .thenReturn(new org.springframework.core.io.ByteArrayResource("%PDF-1.4".getBytes()));
        mvc.perform(get("/api/v1/livros/1/exemplares-digitais/10/arquivo")
                .header("Authorization","Bearer "+token))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options","nosniff"))
                .andExpect(header().string("Content-Disposition",org.hamcrest.Matchers.containsString("inline")))
                .andExpect(content().contentType(MediaType.APPLICATION_PDF));
    }

    @Test void catalogoExigeTokenNoModoSeguro() throws Exception {
        mvc.perform(get("/api/v1/livros")).andExpect(status().isUnauthorized());
        verifyNoInteractions(estoque);
    }

    @Test void leitorPodeConsultarCatalogo() throws Exception {
        String token=login("leitor","leitor-laboratorio-123");
        when(estoque.listar(isNull(),any())).thenReturn(Page.empty());
        mvc.perform(get("/api/v1/livros").header("Authorization","Bearer "+token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test void leitorNaoPodeCadastrarLivro() throws Exception {
        String token=login("leitor","leitor-laboratorio-123");
        mvc.perform(post("/api/v1/livros").header("Authorization","Bearer "+token)
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isForbidden());
        verify(estoque,never()).criar(any());
        verifyNoInteractions(auditoria);
    }

    @Test void leitorNaoPodeEditarOuDesativarLivro() throws Exception {
        String token=login("leitor","leitor-laboratorio-123");
        mvc.perform(put("/api/v1/livros/1").header("Authorization","Bearer "+token)
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/livros/1").header("Authorization","Bearer "+token))
            .andExpect(status().isForbidden());
        verify(estoque,never()).atualizar(anyLong(),any());
        verify(estoque,never()).desativar(anyLong());
    }

    @Test void leitorNaoPodeMovimentarEstoque() throws Exception {
        String token=login("leitor","leitor-laboratorio-123");
        mvc.perform(post("/api/v1/livros/1/movimentacoes").header("Authorization","Bearer "+token)
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isForbidden());
        verify(estoque,never()).movimentar(anyLong(),any());
    }

    @Test void bibliotecarioPodeDesativarLivro() throws Exception {
        String token=login("bibliotecario","senha-laboratorio-123");
        mvc.perform(delete("/api/v1/livros/1").header("Authorization","Bearer "+token))
            .andExpect(status().isNoContent());
        verify(estoque).desativar(1L);
        verify(auditoria).registrar("bibliotecario","LIVRO_DESATIVAR","DELETE",
                "/api/v1/livros/1",204);
    }

    @Test void auditoriaSomenteBibliotecario() throws Exception {
        String reader=login("leitor","leitor-laboratorio-123");
        mvc.perform(get("/api/v1/auditoria").header("Authorization","Bearer "+reader))
            .andExpect(status().isForbidden());
        String librarian=login("bibliotecario","senha-laboratorio-123");
        when(auditoria.listar(any())).thenReturn(Page.empty());
        mvc.perform(get("/api/v1/auditoria").header("Authorization","Bearer "+librarian))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test void leitorNaoPodeListarUsuarios() throws Exception {
        String token = login("leitor", "leitor-laboratorio-123");
        mvc.perform(get("/api/v1/usuarios").header("Authorization", "Bearer " + token))
            .andExpect(status().isForbidden());
        verify(usuarioService, never()).listar(any());
    }

    @Test void bibliotecarioPodeListarUsuariosSemExporSenhas() throws Exception {
        String token = login("bibliotecario", "senha-laboratorio-123");
        when(usuarioService.listar(any())).thenReturn(Page.empty());
        mvc.perform(get("/api/v1/usuarios").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test void leitorNaoPodeCadastrarUsuario() throws Exception {
        String token = login("leitor", "leitor-laboratorio-123");
        mvc.perform(post("/api/v1/usuarios").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isForbidden());
        verify(usuarioService, never()).cadastrar(any(), any(), any());
    }

    @Test void contaDesativadaInvalidaJwtJaEmitido() throws Exception {
        String token = login("bibliotecario", "senha-laboratorio-123");
        bibliotecarioAtivo.set(false);
        mvc.perform(get("/api/v1/livros").header("Authorization", "Bearer " + token))
            .andExpect(status().isUnauthorized());
        verifyNoInteractions(estoque);
    }

    @Test void senhaAlteradaInvalidaJwtJaEmitido() throws Exception {
        String token = login("bibliotecario", "senha-laboratorio-123");
        versaoBibliotecario.incrementAndGet();
        mvc.perform(get("/api/v1/livros").header("Authorization", "Bearer " + token))
            .andExpect(status().isUnauthorized());
        verifyNoInteractions(estoque);
    }

}
