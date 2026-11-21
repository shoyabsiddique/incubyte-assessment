package com.acme.salary.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.acme.salary.AbstractMariaDbIntegrationTest;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** The interactive API documentation is public, but the data endpoints it describes still need a token. */
class ApiDocumentationIT extends AbstractMariaDbIntegrationTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @LocalServerPort
    private int port;

    private final HttpClient client = HttpClient.newHttpClient();

    private HttpResponse<String> get(String path) throws Exception {
        return client.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void theSwaggerPageIsServedWithoutSigningIn() throws Exception {
        HttpResponse<String> response = get("/swagger-ui/index.html");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).containsIgnoringCase("swagger");
    }

    @Test
    void theOpenApiDocumentDescribesTheApiAndABearerScheme() throws Exception {
        HttpResponse<String> response = get("/v3/api-docs");

        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode document = JSON.readTree(response.body());
        assertThat(document.get("info").get("title").asString()).isEqualTo("ACME Salary Management API");
        assertThat(document.get("paths").has("/api/v1/auth/login")).isTrue();
        assertThat(document.get("paths").has("/api/v1/employees")).isTrue();
        assertThat(document.get("components").get("securitySchemes").get("bearerAuth").get("scheme").asString())
                .isEqualTo("bearer");
    }

    @Test
    void theDataEndpointsStillNeedAToken() throws Exception {
        assertThat(get("/api/v1/employees").statusCode()).isEqualTo(401);
    }
}
