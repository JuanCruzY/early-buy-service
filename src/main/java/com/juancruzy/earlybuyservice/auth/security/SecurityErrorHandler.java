package com.juancruzy.earlybuyservice.auth.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import com.juancruzy.earlybuyservice.shared.exception.ApiError;
import com.juancruzy.earlybuyservice.shared.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class SecurityErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public SecurityErrorHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException exception) throws IOException {
        write(request, response, ErrorCode.MISSING_JWT);
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException exception) throws IOException {
        write(request, response, ErrorCode.ACCESS_DENIED);
    }

    public void write(HttpServletRequest request, HttpServletResponse response,
                      ErrorCode code) throws IOException {
        response.setStatus(code.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        if (code.getStatus() == HttpStatus.UNAUTHORIZED) {
            response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        }
        ApiError error = new ApiError(Instant.now(), code.getStatus().value(), code.name(),
                code.getDefaultMessage(), request.getRequestURI());
        objectMapper.writeValue(response.getOutputStream(), error);
    }
}
