package com.acme.salary;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;

@ExtendWith(OutputCaptureExtension.class)
class HealthEndpointIT extends AbstractMariaDbIntegrationTest {

    private static final String HEADER = "X-Correlation-Id";

    @LocalServerPort
    private int port;

    private final HttpClient client = HttpClient.newHttpClient();

    @Test
    void healthIsUpWithoutAuthenticationAndCarriesAGeneratedCorrelationId() throws Exception {
        HttpResponse<String> response = get("/actuator/health", null);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"status\":\"UP\"");
        assertThat(response.headers().firstValue(HEADER)).isPresent();
    }

    @Test
    void infoIsAvailableWithoutAuthenticationAndCarriesACorrelationId() throws Exception {
        HttpResponse<String> response = get("/actuator/info", null);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue(HEADER)).isPresent();
    }

    @Test
    void healthRevealsNoComponentDetailsToAnonymousCallers() throws Exception {
        HttpResponse<String> response = get("/actuator/health", null);

        assertThat(response.body()).doesNotContain("components").doesNotContain("details");
    }

    @Test
    void actuatorEndpointsOtherThanHealthAndInfoAreNotAvailableToAnonymousCallers() throws Exception {
        for (String path : new String[] {"/actuator/env", "/actuator/beans", "/actuator/metrics"}) {
            // refused before routing (401) while anonymous; never served
            assertThat(get(path, null).statusCode()).isIn(401, 404);
        }
    }

    @Test
    void aSuppliedCorrelationIdIsEchoedAndAppearsInStructuredJsonLogs(CapturedOutput output)
            throws Exception {
        HttpResponse<String> response = get("/actuator/health", "trace-abc-123");

        assertThat(response.headers().firstValue(HEADER)).contains("trace-abc-123");
        assertThat(output.getAll())
                .contains("\"correlationId\":\"trace-abc-123\"")
                .contains("\"message\":");
    }

    private HttpResponse<String> get(String path, String correlationId) throws Exception {
        HttpRequest.Builder request =
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET();
        if (correlationId != null) {
            request.header(HEADER, correlationId);
        }
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}
