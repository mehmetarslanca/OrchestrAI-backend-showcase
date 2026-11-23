package com.arslanca.orchestrai.core.exception;

import com.arslanca.orchestrai.core.exception.type.InfrastructureUnavailableException;
import com.arslanca.orchestrai.core.exception.type.HeavyModelResolutionException;
import com.arslanca.orchestrai.core.exception.type.LlmResponseParseException;
import com.arslanca.orchestrai.core.exception.type.ResourceNotFoundException;
import com.arslanca.orchestrai.core.exception.type.SecretEncryptionException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.context.request.ServletWebRequest;
import org.junit.jupiter.api.Assertions;

class GlobalExceptionHandlerWebMvcTest {

    private MockMvc mockMvc;
    private GlobalExceptionHandler exceptionHandler;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        exceptionHandler = new GlobalExceptionHandler();
        mockMvc = MockMvcBuilders.standaloneSetup(new TestController())
                .setControllerAdvice(exceptionHandler)
                .setValidator(validator)
                .build();
    }

    @Test
    void shouldSuppressGenericErrorForCommittedSseResponseByAcceptHeader() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Accept", "text/event-stream");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setCommitted(true);
        
        ServletWebRequest webRequest = new ServletWebRequest(request, response);
        ResponseEntity<?> result = exceptionHandler.handleGeneric(new RuntimeException("Test error"), webRequest);
        
        Assertions.assertNull(result);
    }
    
    @Test
    void shouldSuppressGenericErrorForCommittedSseResponseByContentType() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setContentType("text/event-stream;charset=UTF-8");
        response.setCommitted(true);
        
        ServletWebRequest webRequest = new ServletWebRequest(request, response);
        ResponseEntity<?> result = exceptionHandler.handleGeneric(new RuntimeException("Test error"), webRequest);
        
        Assertions.assertNull(result);
    }

    @Test
    void shouldSuppressLlmParseErrorForCommittedSseResponse() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Accept", "text/event-stream");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setCommitted(true);

        ServletWebRequest webRequest = new ServletWebRequest(request, response);
        ResponseEntity<?> result = exceptionHandler.handleLlmResponseParseError(
                new LlmResponseParseException("bad json"), webRequest);

        Assertions.assertNull(result);
    }

    @Test
    void shouldSuppressIllegalStateForSseResponseWithEventStreamContentType() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setContentType("text/event-stream;charset=UTF-8");

        ServletWebRequest webRequest = new ServletWebRequest(request, response);
        ResponseEntity<?> result = exceptionHandler.handleIllegalState(
                new IllegalStateException("stream failed"), webRequest);

        Assertions.assertNull(result);
    }

    @Test
    void shouldNotSuppressGenericErrorForUncommittedResponse() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Accept", "text/event-stream");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setCommitted(false); // not committed
        
        ServletWebRequest webRequest = new ServletWebRequest(request, response);
        ResponseEntity<?> result = exceptionHandler.handleGeneric(new RuntimeException("Test error"), webRequest);
        
        Assertions.assertNotNull(result);
        Assertions.assertEquals(500, result.getStatusCode().value());
    }

    @Test
    void shouldMapResourceNotFoundToStandardizedPayload() throws Exception {
        mockMvc.perform(get("/test/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"))
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    void shouldMapValidationErrorsToStandardizedPayload() throws Exception {
        mockMvc.perform(post("/test/validate")
                        .contentType(APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    @Test
    void shouldMapInfrastructureErrorsTo503Payload() throws Exception {
        mockMvc.perform(get("/test/unavailable"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("SERVICE_UNAVAILABLE"));
    }

    @Test
    void shouldMapHeavyModelResolutionErrorsToStandardizedPayload() throws Exception {
        mockMvc.perform(get("/test/heavy-model-invalid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("HEAVY_MODEL_INVALID"));
    }

    @Test
    void shouldMapSecretEncryptionErrorsTo500Payload() throws Exception {
        mockMvc.perform(get("/test/secret-encryption"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("SECRET_ENCRYPTION_ERROR"));
    }

    @RestController
    static class TestController {

        @GetMapping("/test/not-found")
        ResponseEntity<Void> notFound() {
            throw new ResourceNotFoundException("missing");
        }

        @GetMapping("/test/unavailable")
        ResponseEntity<Void> unavailable() {
            throw new InfrastructureUnavailableException("redis unavailable");
        }

        @GetMapping("/test/heavy-model-invalid")
        ResponseEntity<Void> heavyModelInvalid() {
            throw HeavyModelResolutionException.badRequest("invalid heavy model");
        }

        @GetMapping("/test/secret-encryption")
        ResponseEntity<Void> secretEncryption() {
            throw new SecretEncryptionException("encryption failed");
        }

        @PostMapping("/test/validate")
        ResponseEntity<Void> validate(@Valid @RequestBody final TestRequest request) {
            return ResponseEntity.ok().build();
        }
    }

    record TestRequest(@NotBlank String name) {
    }
}
