package com.arslanca.orchestrai.core.exception;

import com.arslanca.orchestrai.core.exception.type.ApiErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class ApiErrorResponseWriter {

    private final ObjectMapper mapper;

    public ApiErrorResponseWriter(ObjectMapper mapper) {
        this.mapper = mapper.copy().findAndRegisterModules();
    }

    public void write(HttpServletResponse response, HttpStatus status, String errorCode, String message) throws IOException {
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(java.nio.charset.StandardCharsets.UTF_8.name());
        response.setStatus(status.value());
        var apiError = ApiErrorResponse.of(status.value(), errorCode, message);
        mapper.writeValue(response.getOutputStream(), apiError);
    }
}
