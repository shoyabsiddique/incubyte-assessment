package com.acme.salary.support;

import com.acme.salary.auth.persistence.AppUserEntity;
import com.acme.salary.auth.persistence.AppUserRepository;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** A signed-in HTTP client for integration tests: creates its own user, signs in and speaks JSON. */
public final class ApiClient {

    public record Reply(int status, JsonNode body) {}

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String PASSWORD = "Api-Client-Pass-12345";

    private final HttpClient http = HttpClient.newHttpClient();
    private final int port;
    private final UUID userId = UUID.randomUUID();
    private final String email = "api-" + userId + "@acme.example";
    private String token;

    public ApiClient(int port, AppUserRepository users, PasswordEncoder encoder) throws Exception {
        this.port = port;
        users.save(new AppUserEntity(userId, email, encoder.encode(PASSWORD), "Api Client"));
        Reply login = send(
                "POST",
                "/api/v1/auth/login",
                "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}",
                false);
        this.token = login.body().get("accessToken").asString();
    }

    public UUID userId() {
        return userId;
    }

    public String email() {
        return email;
    }

    public Reply get(String path) throws Exception {
        return send("GET", path, null, true);
    }

    public Reply post(String path, String body) throws Exception {
        return send("POST", path, body, true);
    }

    public Reply put(String path, String body) throws Exception {
        return send("PUT", path, body, true);
    }

    public Reply withToken(String method, String path, String body) throws Exception {
        return send(method, path, body, true);
    }

    public Reply withoutToken(String method, String path, String body) throws Exception {
        return send(method, path, body, false);
    }

    private Reply send(String method, String path, String body, boolean signedIn) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json");
        if (signedIn) {
            request.header("Authorization", "Bearer " + token);
        }
        request.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        HttpResponse<String> response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
        String text = response.body();
        return new Reply(response.statusCode(), text == null || text.isBlank() ? null : JSON.readTree(text));
    }
}
