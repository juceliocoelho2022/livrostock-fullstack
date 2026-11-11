package br.com.livrostock.integration;

import br.com.livrostock.domain.Livro;
import br.com.livrostock.repository.LivroRepository;
import br.com.livrostock.repository.ExemplarDigitalRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
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
        "livrostock.storage.pdf-dir=./target/arquivos-importacao-ci"
})
class ImportacaoDigitalIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired LivroRepository livros;
    @Autowired ExemplarDigitalRepository exemplares;

    private String token(String usuario, String senha) throws Exception {
        String corpo = mapper.writeValueAsString(Map.of("usuario", usuario, "senha", senha));
        String resposta = mvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(resposta).get("accessToken").asText();
    }

    private org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder upload(
            String driveId) {
        byte[] pdf = "%PDF-1.4\nExemplo de teste local\n%%EOF\n".getBytes(StandardCharsets.UTF_8);
        return multipart("/api/v1/importacoes-digitais/drive")
                .file(new MockMultipartFile("arquivo", "livro-ci.pdf", "application/pdf", pdf))
                .param("driveFileId", driveId)
                .param("titulo", "Livro digital de homologação")
                .param("autor", "Autor de teste")
                .param("categoria", "Tecnologia");
    }

    @Test
    void importaPdfSemInventarIsbnERecusaDuplicidade() throws Exception {
        String bibliotecario = token("bibliotecario", "senha-bibliotecario-de-teste");
        String driveId = "drive_ci_" + UUID.randomUUID().toString().replace("-", "");
        JsonNode resposta = mapper.readTree(mvc.perform(upload(driveId)
                .header("Authorization", "Bearer " + bibliotecario))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.somenteDigital").value(true))
                .andExpect(jsonPath("$.isbn").isEmpty())
                .andExpect(jsonPath("$.estoqueBaixo").value(false))
                .andExpect(jsonPath("$.quantidade").value(0))
                .andReturn().getResponse().getContentAsString());

        long id = resposta.get("id").asLong();
        Livro registro = livros.findById(id).orElseThrow();
        assertTrue(registro.isSomenteDigital());
        assertEquals(driveId, registro.getOrigemDriveId());
        assertNull(registro.getIsbn());
        assertEquals(1, exemplares.findByLivroIdOrderByCriadoEmDesc(id).size());

        mvc.perform(upload(driveId).header("Authorization", "Bearer " + bibliotecario))
                .andExpect(status().isConflict());
        assertEquals(1, livros.findAll().stream().filter(l -> driveId.equals(l.getOrigemDriveId())).count());
    }

    @Test
    void leitorNaoPodeImportarPdf() throws Exception {
        String leitor = token("leitor", "senha-leitor-de-teste");
        String driveId = "drive_ci_" + UUID.randomUUID().toString().replace("-", "");
        mvc.perform(upload(driveId).header("Authorization", "Bearer " + leitor))
                .andExpect(status().isForbidden());
        assertFalse(livros.existsByOrigemDriveId(driveId));
    }
}
