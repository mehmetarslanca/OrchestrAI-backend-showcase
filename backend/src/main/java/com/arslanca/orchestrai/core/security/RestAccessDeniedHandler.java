package com.arslanca.orchestrai.core.security;

import com.arslanca.orchestrai.core.exception.ApiErrorResponseWriter;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private final ApiErrorResponseWriter errorWriter;

    public RestAccessDeniedHandler(ApiErrorResponseWriter errorWriter) {
        this.errorWriter = errorWriter;
    }


    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException) throws IOException, ServletException {
        errorWriter.write(response, HttpStatus.FORBIDDEN, "FORBIDDEN", "Forbidden: " + accessDeniedException.getMessage());
    }
}
