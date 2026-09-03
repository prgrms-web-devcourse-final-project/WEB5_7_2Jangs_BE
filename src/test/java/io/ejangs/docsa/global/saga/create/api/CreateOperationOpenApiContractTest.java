package io.ejangs.docsa.global.saga.create.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CreateOperationOpenApiContractTest {

    private static final List<String> CREATE_PATHS = List.of(
            "/api/document",
            "/api/document/{documentId}/branch",
            "/api/document/{docId}/commit",
            "/api/document/{docId}/merge"
    );

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("생성 API는 필수 UUID 멱등 키와 생성 작업 충돌 응답을 공개한다")
    void documentsCreateOperationContract() throws Exception {
        String responseBody = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode openApi = objectMapper.readTree(responseBody);

        for (String path : CREATE_PATHS) {
            JsonNode post = openApi.path("paths").path(path).path("post");
            JsonNode idempotencyKey = findParameter(post.path("parameters"), "Idempotency-Key");

            assertThat(idempotencyKey.path("in").asText()).as(path).isEqualTo("header");
            assertThat(idempotencyKey.path("required").asBoolean()).as(path).isTrue();
            assertThat(idempotencyKey.path("schema").path("format").asText()).as(path).isEqualTo("uuid");
            assertThat(post.path("responses").has("409")).as(path).isTrue();
        }
    }

    private JsonNode findParameter(JsonNode parameters, String name) {
        return StreamSupport.stream(parameters.spliterator(), false)
                .filter(parameter -> name.equals(parameter.path("name").asText()))
                .findFirst()
                .orElse(objectMapper.createObjectNode());
    }
}
