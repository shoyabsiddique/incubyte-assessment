package com.acme.salary.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.acme.salary.AbstractMariaDbIntegrationTest;
import com.acme.salary.auth.persistence.AppUserEntity;
import com.acme.salary.auth.persistence.AppUserRepository;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Every row of the sign-in matrix, over real HTTP against the real application and MariaDB. */
class AuthenticationIT extends AbstractMariaDbIntegrationTest {

    private static final String PASSWORD = "Correct-Horse-9";
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @LocalServerPort
    private int port;

    @Autowired
    private AppUserRepository users;

    @Autowired
    private PasswordEncoder encoder;

    @Autowired
    private JwtEncoder jwtEncoder;

    private final HttpClient client = HttpClient.newHttpClient();
    private String email;
    private UUID userId;

    @BeforeEach
    void createAUser() {
        userId = UUID.randomUUID();
        email = "hr-" + userId + "@acme.example";
        users.save(new AppUserEntity(userId, email, encoder.encode(PASSWORD), "Test HR"));
    }

    @Test
    void validCredentialsReturnAOneHourBearerToken() throws Exception {
        HttpResponse<String> response = login(email, PASSWORD);

        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode body = JSON.readTree(response.body());
        assertThat(body.get("accessToken").asString()).isNotBlank();
        assertThat(body.get("tokenType").asString()).isEqualTo("Bearer");
        assertThat(body.get("expiresIn").asLong()).isEqualTo(3600);
        assertThat(body.get("user").get("email").asString()).isEqualTo(email);
        assertThat(body.get("user").get("displayName").asString()).isEqualTo("Test HR");
    }

    @Test
    void theEmailIsMatchedIgnoringCaseAndSpaces() throws Exception {
        assertThat(login("  " + email.toUpperCase() + " ", PASSWORD).statusCode()).isEqualTo(200);
    }

    @Test
    void everyKindOfFailedLoginGetsTheSameGenericAnswer() throws Exception {
        HttpResponse<String> wrongPassword = login(email, "not-the-password");
        HttpResponse<String> unknownEmail = login("nobody-" + UUID.randomUUID() + "@acme.example", PASSWORD);
        HttpResponse<String> blankFields = post("/api/v1/auth/login", "{\"email\":\"\",\"password\":\"\"}");
        HttpResponse<String> missingFields = post("/api/v1/auth/login", "{}");

        for (HttpResponse<String> response : java.util.List.of(wrongPassword, unknownEmail, blankFields, missingFields)) {
            assertThat(response.statusCode()).isEqualTo(401);
            assertThat(response.headers().firstValue("Content-Type").orElse("")).startsWith("application/problem+json");
            JsonNode body = JSON.readTree(response.body());
            assertThat(body.get("code").asString()).isEqualTo("INVALID_CREDENTIALS");
            assertThat(body.get("detail").asString()).isEqualTo("Invalid email or password");
        }
    }

    @Test
    void aBodyThatIsNotJsonIsABadRequest() throws Exception {
        HttpResponse<String> response = post("/api/v1/auth/login", "this is not json");

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(JSON.readTree(response.body()).get("code").asString()).isEqualTo("MALFORMED_REQUEST");
    }

    @Test
    void aProtectedCallWithoutATokenIsRefusedAsAProblem() throws Exception {
        HttpResponse<String> response = get("/api/v1/auth/me", null);

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.headers().firstValue("Content-Type").orElse("")).startsWith("application/problem+json");
        assertThat(JSON.readTree(response.body()).get("code").asString()).isEqualTo("UNAUTHENTICATED");
    }

    @Test
    void aValidTokenOpensProtectedCallsAndIdentifiesTheUser() throws Exception {
        String token = tokenFor(login(email, PASSWORD));

        HttpResponse<String> response = get("/api/v1/auth/me", token);

        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode body = JSON.readTree(response.body());
        assertThat(body.get("id").asString()).isEqualTo(userId.toString());
        assertThat(body.get("email").asString()).isEqualTo(email);
    }

    @Test
    void aTamperedTokenIsRefused() throws Exception {
        String token = tokenFor(login(email, PASSWORD));
        String tampered = token.substring(0, token.length() - 3) + (token.endsWith("AAA") ? "BBB" : "AAA");

        assertThat(get("/api/v1/auth/me", tampered).statusCode()).isEqualTo(401);
    }

    @Test
    void anExpiredTokenIsRefused() throws Exception {
        Instant twoHoursAgo = Instant.now().minusSeconds(7200);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(userId.toString())
                .claim("email", email)
                .issuedAt(twoHoursAgo)
                .expiresAt(twoHoursAgo.plusSeconds(3600))
                .build();
        String expired = jwtEncoder
                .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();

        assertThat(get("/api/v1/auth/me", expired).statusCode()).isEqualTo(401);
    }

    @Test
    void healthAndInfoStayPublic() throws Exception {
        assertThat(get("/actuator/health", null).statusCode()).isEqualTo(200);
        assertThat(get("/actuator/info", null).statusCode()).isEqualTo(200);
    }

    @Test
    void onlyTheConfiguredFrontendOriginPassesCorsPreflight() throws Exception {
        HttpResponse<String> allowed = preflight("http://localhost:4200");
        HttpResponse<String> other = preflight("https://evil.example");

        assertThat(allowed.headers().firstValue("Access-Control-Allow-Origin")).contains("http://localhost:4200");
        assertThat(other.headers().firstValue("Access-Control-Allow-Origin")).isEmpty();
    }

    // ---- helpers -----------------------------------------------------------------------------

    private String tokenFor(HttpResponse<String> loginResponse) {
        return JSON.readTree(loginResponse.body()).get("accessToken").asString();
    }

    private HttpResponse<String> login(String email, String password) throws Exception {
        String body = JSON.writeValueAsString(java.util.Map.of("email", email, "password", password));
        return post("/api/v1/auth/login", body);
    }

    private HttpResponse<String> post(String path, String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri(path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(String path, String bearerToken) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(uri(path)).GET();
        if (bearerToken != null) {
            request.header("Authorization", "Bearer " + bearerToken);
        }
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> preflight(String origin) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri("/api/v1/auth/login"))
                .header("Origin", origin)
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "content-type,authorization")
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }
}
