package com.acme.salary.shared.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.acme.salary.shared.domain.DomainException;
import com.acme.salary.shared.domain.ErrorKind;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.mock.web.MockHttpServletResponse;

class ProblemDetailsTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    void mapsEachKindOfDomainErrorToItsHttpStatusAndKeepsTheCode() {
        Map<ErrorKind, HttpStatus> expected = Map.of(
                ErrorKind.INVALID_INPUT, HttpStatus.BAD_REQUEST,
                ErrorKind.UNAUTHENTICATED, HttpStatus.UNAUTHORIZED,
                ErrorKind.FORBIDDEN, HttpStatus.FORBIDDEN,
                ErrorKind.NOT_FOUND, HttpStatus.NOT_FOUND,
                ErrorKind.CONFLICT, HttpStatus.CONFLICT);

        expected.forEach((kind, status) -> {
            ProblemDetail problem = handler.handleDomainError(new DomainException(kind, "SOME_CODE", "Something happened"));

            assertThat(problem.getStatus()).isEqualTo(status.value());
            assertThat(problem.getDetail()).isEqualTo("Something happened");
            assertThat(problem.getProperties()).containsEntry("code", "SOME_CODE");
        });
    }

    @Test
    void anUnreadableBodyIsAMalformedRequest() {
        ProblemDetail problem = handler.handleUnreadableBody();

        assertThat(problem.getStatus()).isEqualTo(400);
        assertThat(problem.getProperties()).containsEntry("code", "MALFORMED_REQUEST");
    }

    @Test
    void writesSecurityFailuresInTheSameProblemShape() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        ProblemJson.write(response, HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Authentication is required");

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).startsWith("application/problem+json");
        assertThat(response.getContentAsString())
                .contains("\"status\":401")
                .contains("\"code\":\"UNAUTHENTICATED\"")
                .contains("\"detail\":\"Authentication is required\"");
    }

    @Test
    void escapesControlCharactersSoTheBodyIsAlwaysValidJson() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        ProblemJson.write(response, HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "line one\nline\ttwo\u0001");

        assertThat(response.getContentAsString()).contains("line one\\nline\\ttwo\\u0001");
    }

    @Test
    void anUnexpectedFailureBecomesAGenericProblemWithoutLeakingDetails() {
        ProblemDetail problem = handler.handleUnexpected(new IllegalStateException("database password is hunter2"));

        assertThat(problem.getStatus()).isEqualTo(500);
        assertThat(problem.getProperties()).containsEntry("code", "INTERNAL_ERROR");
        assertThat(problem.getDetail()).doesNotContain("hunter2");
    }

    @Test
    void escapesQuotesAndBackslashesInTheDetail() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        ProblemJson.write(response, HttpStatus.FORBIDDEN, "ACCESS_DENIED", "say \"no\" \\ now");

        assertThat(response.getContentAsString()).contains("\"detail\":\"say \\\"no\\\" \\\\ now\"");
    }
}
