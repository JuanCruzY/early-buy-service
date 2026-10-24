package com.juancruzy.earlybuyservice.shared.exception;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(GlobalExceptionHandlerTests.ErrorTestController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({GlobalExceptionHandler.class, GlobalExceptionHandlerTests.ErrorTestController.class})
class GlobalExceptionHandlerTests {

    @Autowired
    private MockMvc mockMvc;

    @ParameterizedTest
    @CsvSource({
            "MISSING_FIELDS, 400",
            "INVALID_CHANNEL, 400",
            "INVALID_ARTICLE_ID, 400",
            "INVALID_CONTACT, 400",
            "MISSING_JWT, 401",
            "INVALID_JWT, 401",
            "ACCESS_DENIED, 403",
            "AUTH_UNAVAILABLE, 503",
            "NOT_OWNER, 403",
            "ARTICLE_NOT_FOUND, 404",
            "SUBSCRIPTION_NOT_FOUND, 404",
            "ALREADY_SUBSCRIBED, 409",
            "ARTICLE_HAS_STOCK, 409",
            "INVALID_STATUS_TRANSITION, 409",
            "TIMEOUT_CATALOG, 503",
            "INVALID_REQUEST, 400",
            "INTERNAL_ERROR, 500"
    })
    void mapsControlledErrorsToTheirHttpStatus(ErrorCode code, int expectedStatus) throws Exception {
        String path = "/test/errors/controlled/" + code.name();

        mockMvc.perform(get(path))
                .andExpect(status().is(expectedStatus))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.timestamp").isString())
                .andExpect(jsonPath("$.status").value(expectedStatus))
                .andExpect(jsonPath("$.code").value(code.name()))
                .andExpect(jsonPath("$.message").value(code.getDefaultMessage()))
                .andExpect(jsonPath("$.path").value(path))
                .andExpect(jsonPath("$.*", hasSize(5)));
    }

    @Test
    void rejectsMalformedJsonWithUniformError() throws Exception {
        mockMvc.perform(post("/test/errors/body")
                        .contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.path").value("/test/errors/body"));
    }

    @Test
    void hidesUnexpectedExceptionDetails() throws Exception {
        mockMvc.perform(get("/test/errors/unexpected"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value(ErrorCode.INTERNAL_ERROR.getDefaultMessage()))
                .andExpect(jsonPath("$.path").value("/test/errors/unexpected"))
                .andExpect(jsonPath("$.*", hasSize(5)))
                .andExpect(content().string(not(containsString("private diagnostic detail"))));
    }

    @Test
    void preservesMethodNotAllowedStatusAndAllowHeader() throws Exception {
        mockMvc.perform(put("/test/errors/body"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string(HttpHeaders.ALLOW, containsString("POST")))
                .andExpect(jsonPath("$.status").value(405))
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void preservesUnsupportedMediaTypeStatus() throws Exception {
        mockMvc.perform(post("/test/errors/body")
                        .contentType(MediaType.TEXT_PLAIN).content("text"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.status").value(415))
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void preservesResponseStatusException() throws Exception {
        mockMvc.perform(get("/test/errors/status"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void preservesAnnotatedExceptionStatus() throws Exception {
        mockMvc.perform(get("/test/errors/annotated-status"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @RestController
    @TestComponent
    @RequestMapping("/test/errors")
    static class ErrorTestController {

        @GetMapping("/controlled/{code}")
        public void controlled(@PathVariable ErrorCode code) {
            throw new ApplicationException(code);
        }

        @PostMapping(value = "/body", consumes = MediaType.APPLICATION_JSON_VALUE)
        public TestBody body(@RequestBody TestBody body) {
            return body;
        }

        @GetMapping("/unexpected")
        public void unexpected() {
            throw new IllegalStateException("private diagnostic detail");
        }

        @GetMapping("/status")
        public void responseStatus() {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS);
        }

        @GetMapping("/annotated-status")
        public void annotatedStatus() {
            throw new ForbiddenTestException();
        }
    }

    record TestBody(String value) {
    }

    @ResponseStatus(HttpStatus.FORBIDDEN)
    static class ForbiddenTestException extends RuntimeException {
    }
}
