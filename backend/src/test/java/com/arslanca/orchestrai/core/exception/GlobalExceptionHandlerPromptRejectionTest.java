package com.arslanca.orchestrai.core.exception;

import com.arslanca.orchestrai.core.exception.type.PromptRejectionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerPromptRejectionTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new TestController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void shouldMapPromptRejectionTo422Payload() throws Exception {
        mockMvc.perform(get("/test/prompt-rejection"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("PROMPT_REJECTED"))
                .andExpect(jsonPath("$.code").value(422))
                .andExpect(jsonPath("$.message").value("Your request could not be processed."));
    }

    @RestController
    static class TestController {
        @GetMapping("/test/prompt-rejection")
        void triggerPromptRejection() {
            throw new PromptRejectionException("Your request could not be processed.");
        }
    }
}
