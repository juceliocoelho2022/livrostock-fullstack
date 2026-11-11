package br.com.livrostock.integration;

import br.com.livrostock.domain.ExemplarDigital;
import br.com.livrostock.repository.ExemplarDigitalRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureObservability
@ActiveProfiles("secure")
@EnabledIfEnvironmentVariable(named = "LIVROSTOCK_INTEGRATION_DB", matches = "true")
@TestPropertySource(properties = {
        "LIVROSTOCK_JWT_SECRET=abcdefghijklmnopqrstuvwxyz0123456789-TESTE-INTEGRACAO",
        "LIVROSTOCK_LIBRARIAN_PASSWORD=senha-bibliotecario-de-teste",
        "LIVROSTOCK_READER_PASSWORD=senha-leitor-de-teste",
        "livrostock.storage.pdf-dir=./target/arquivos-integridade-v09-ci"
})
class IntegridadePdfIntegrationTest {
    private static final Path DIRETORIO = Path.of("./target/arquivos-integridade-v09-ci").toAbsolutePath();
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired ExemplarDigitalRepository exemplares;

    private String token(String usuario, String senha) throws Exception {
        String corpo = mapper.writeValueAsString(Map.of("usuario", usuario, "senha", senha));
        String retorno = mvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(retorno).path("accessToken").asText();
    }

    private String estado(String jwt, long exemplarId) throws Exception {
        String json = mvc.perform(get("/api/v1/acervo-digital/integridade")
                .param("size", "20").header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        for (JsonNode item : mapper.readTree(json).path("content")) {
            if (item.path("exemplarId").asLong() == exemplarId) {
                assertFalse(item.has("nomeArmazenado"), "Nao divulgar caminho interno");
                return item.path("estado").asText();
            }
        }
        fail("Exemplar da integracao nao encontrado na primeira pagina");
        return "";
    }

    @Test
    void auditaPdfIntactoAdulteradoEAusenteComControleDeAcesso() throws Exception {
        String admin = token("bibliotecario", "senha-bibliotecario-de-teste");
        String leitor = token("leitor", "senha-leitor-de-teste");
        byte[] original = "%PDF-1.4\nArquivo de integracao\n%%EOF\n".getBytes(StandardCharsets.UTF_8);
        String driveId = "v09_" + UUID.randomUUID().toString().replace("-", "");
        String body = mvc.perform(multipart("/api/v1/importacoes-digitais/drive")
                .file(new MockMultipartFile("arquivo", "integridade.pdf", "application/pdf", original))
                .param("driveFileId", driveId).param("titulo", "Integridade " + driveId)
                .param("autor", "Autor CI").param("categoria", "Testes")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();

        long livroId = mapper.readTree(body).path("id").asLong();
        ExemplarDigital pdf = exemplares.findByLivroIdOrderByCriadoEmDesc(livroId).get(0);
        Path arquivo = DIRETORIO.resolve(pdf.getNomeArmazenado());

        try {
            assertEquals("OK", estado(admin, pdf.getId()));
            mvc.perform(get("/api/v1/livros/" + livroId + "/exemplares-digitais/" + pdf.getId() + "/arquivo")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + leitor))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_PDF));

            byte[] alterado = original.clone();
            alterado[10] = (byte) (alterado[10] == 'X' ? 'Y' : 'X');
            Files.write(arquivo, alterado);
            assertEquals("DIVERGENTE", estado(admin, pdf.getId()));
            mvc.perform(get("/api/v1/livros/" + livroId + "/exemplares-digitais/" + pdf.getId() + "/arquivo")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + leitor))
                    .andExpect(status().isConflict());

            Files.delete(arquivo);
            assertEquals("AUSENTE", estado(admin, pdf.getId()));
            mvc.perform(get("/api/v1/livros/" + livroId + "/exemplares-digitais/" + pdf.getId() + "/arquivo")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + leitor))
                    .andExpect(status().isNotFound());
        } finally {
            Files.write(arquivo, original);
        }

        // No modo secure, Prometheus e métricas do Actuator são administrativos.
        mvc.perform(get("/actuator/prometheus")).andExpect(status().isUnauthorized());
        mvc.perform(get("/actuator/prometheus")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + leitor))
                .andExpect(status().isForbidden());
        mvc.perform(get("/actuator/prometheus")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "livrostock_pdf_integridade_verificacoes_total")));
        mvc.perform(get("/actuator/metrics")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + leitor))
                .andExpect(status().isForbidden());
        mvc.perform(get("/actuator/metrics")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
                .andExpect(status().isOk());

        mvc.perform(get("/api/v1/acervo-digital/integridade"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/acervo-digital/integridade")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + leitor))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/acervo-digital/integridade").param("size", "21")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
                .andExpect(status().isBadRequest());
    }
}
