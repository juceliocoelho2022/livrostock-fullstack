package br.com.livrostock.integration;

import br.com.livrostock.domain.PerfilUsuario;
import br.com.livrostock.repository.UsuarioRepository;
import br.com.livrostock.repository.AuditoriaEventoRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("secure")
@EnabledIfEnvironmentVariable(named = "LIVROSTOCK_INTEGRATION_DB", matches = "true")
@TestPropertySource(properties = {
    "LIVROSTOCK_JWT_SECRET=abcdefghijklmnopqrstuvwxyz0123456789-TESTE-INTEGRACAO",
    "LIVROSTOCK_LIBRARIAN_PASSWORD=senha-bibliotecario-de-teste",
    "LIVROSTOCK_READER_PASSWORD=senha-leitor-de-teste",
    "livrostock.storage.pdf-dir=./target/arquivos-integration"
})
class UsuarioPostgresIntegrationTest {
    @Autowired UsuarioRepository usuarios;
    @Autowired AuditoriaEventoRepository auditoria;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    private String login(String usuario, String senha) throws Exception {
        String body = mapper.writeValueAsString(new java.util.HashMap<>(java.util.Map.of(
                "usuario", usuario, "senha", senha)));
        String json = mvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(json).get("accessToken").asText();
    }

    @Test
    void migracaoV4ESeedConservamCredenciaisComHash() {
        var bibliotecario = usuarios.findByNomeUsuario("bibliotecario").orElseThrow();
        var leitor = usuarios.findByNomeUsuario("leitor").orElseThrow();
        assertEquals(PerfilUsuario.BIBLIOTECARIO, bibliotecario.getPerfil());
        assertEquals(PerfilUsuario.LEITOR, leitor.getPerfil());
        assertTrue(bibliotecario.getSenhaHash().startsWith("$2"));
        assertNotEquals("senha-bibliotecario-de-teste", bibliotecario.getSenhaHash());
        assertTrue(usuarios.count() >= 2);
    }

    @Test
    void novoUsuarioPersisteESenhaAlteradaRevogaTokenAnterior() throws Exception {
        String admin = login("bibliotecario", "senha-bibliotecario-de-teste");
        String nome = "ci_" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String criar = mapper.writeValueAsString(java.util.Map.of(
                "nomeUsuario", nome, "senha", "senha-leitor-inicial-123", "perfil", "LEITOR"));

        JsonNode resposta = mapper.readTree(mvc.perform(post("/api/v1/usuarios")
                .header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON).content(criar))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nomeUsuario").value(nome))
                .andReturn().getResponse().getContentAsString());

        long id = resposta.get("id").asLong();
        assertTrue(usuarios.findByNomeUsuario(nome).isPresent());
        String tokenAntigo = login(nome, "senha-leitor-inicial-123");

        mvc.perform(put("/api/v1/usuarios/" + id + "/senha")
                .header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"senha\":\"nova-senha-leitor-segura-123\"}"))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/livros")
                .header("Authorization", "Bearer " + tokenAntigo))
                .andExpect(status().isUnauthorized());
        login(nome, "nova-senha-leitor-segura-123");
    }

    @Test
    void cadastroDeLivroETrilhaDeAuditoriaPersistemJuntos() throws Exception {
        String admin = login("bibliotecario", "senha-bibliotecario-de-teste");
        String isbn = "979" + System.currentTimeMillis()%10000000000L;
        String body = mapper.writeValueAsString(java.util.Map.of(
                "titulo","Registro auditado",
                "autor","Autor de integração",
                "isbn",isbn,
                "categoria","Tecnologia",
                "preco",24.99,
                "estoqueMinimo",1));
        mvc.perform(post("/api/v1/livros")
                .header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());

        assertTrue(auditoria.findAll().stream().anyMatch(evento ->
                "LIVRO_CRIAR".equals(evento.getAcao())
                && "bibliotecario".equals(evento.getUsuario())));
    }
}
