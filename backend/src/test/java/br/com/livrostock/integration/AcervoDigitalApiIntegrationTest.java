package br.com.livrostock.integration;

import br.com.livrostock.repository.ExemplarDigitalRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

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
        "livrostock.storage.pdf-dir=./target/arquivos-acervo-v08-ci"
})
class AcervoDigitalApiIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired ExemplarDigitalRepository exemplares;

    private String token(String usuario, String senha) throws Exception {
        String body = mapper.writeValueAsString(Map.of("usuario", usuario, "senha", senha));
        String response = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(response).get("accessToken").asText();
    }

    private long importar(String jwt, String titulo, String categoria) throws Exception {
        String driveId = "drive_v08_" + UUID.randomUUID().toString().replace("-", "");
        byte[] pdf = "%PDF-1.4\nTeste privado de integracao\n%%EOF\n"
                .getBytes(StandardCharsets.UTF_8);
        JsonNode body = mapper.readTree(mvc.perform(multipart("/api/v1/importacoes-digitais/drive")
                        .file(new MockMultipartFile("arquivo", "acervo-v08.pdf", "application/pdf", pdf))
                        .param("driveFileId", driveId)
                        .param("titulo", titulo).param("autor", "Autor CI")
                        .param("categoria", categoria)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
        return body.get("id").asLong();
    }

    @Test
    void pesquisaPaginaProtegidaELeituraDoPdf() throws Exception {
        String admin = token("bibliotecario", "senha-bibliotecario-de-teste");
        String leitor = token("leitor", "senha-leitor-de-teste");
        String titulo = "Exclusivo V08 " + UUID.randomUUID().toString().replace("-", "");
        long livroId = importar(admin, titulo, "Engenharia de Software");

        mvc.perform(get("/api/v1/acervo-digital").param("busca", titulo)
                        .param("categoria", "Engenharia de Software")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + leitor))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].titulo").value(titulo))
                .andExpect(jsonPath("$.content[0].livroId").value((int) livroId))
                .andExpect(jsonPath("$.content[0].nomeOriginal").value("acervo-v08.pdf"))
                .andExpect(jsonPath("$.content[0].sha256").isString())
                .andExpect(jsonPath("$.content[0].nomeArmazenado").doesNotExist());

        long exemplarId = exemplares.findByLivroIdOrderByCriadoEmDesc(livroId).get(0).getId();
        mvc.perform(get("/api/v1/livros/" + livroId + "/exemplares-digitais/" + exemplarId + "/arquivo")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + leitor))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF));

        mvc.perform(get("/api/v1/acervo-digital")
                        .param("busca", titulo).param("categoria", "Categoria inexistente")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + leitor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));

        mvc.perform(get("/api/v1/acervo-digital").param("busca", titulo))
                .andExpect(status().isUnauthorized());

        mvc.perform(get("/api/v1/acervo-digital").param("size", "51")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + leitor))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/acervo-digital").param("page", "-1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + leitor))
                .andExpect(status().isBadRequest());

        mvc.perform(delete("/api/v1/livros/" + livroId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/acervo-digital").param("busca", titulo)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + leitor))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void paginaTamanhoUmRetornaMetadadosSemCaminhosInternos() throws Exception {
        String admin = token("bibliotecario", "senha-bibliotecario-de-teste");
        String titulo = "Paginacao V08 " + UUID.randomUUID().toString().replace("-", "");
        importar(admin, titulo, "Qualidade");
        JsonNode resposta = mapper.readTree(mvc.perform(get("/api/v1/acervo-digital")
                        .param("size", "1").param("page", "0").param("busca", titulo)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andReturn().getResponse().getContentAsString());
        assertEquals(1, resposta.get("content").size());
        assertFalse(resposta.get("content").get(0).has("nomeArmazenado"));
        assertFalse(resposta.get("content").get(0).has("origemDriveId"));
    }
}
