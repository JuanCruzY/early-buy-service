package com.juancruzy.earlybuyservice.auth.security;

import java.io.IOException;
import java.util.Collections;
import java.util.regex.Pattern;

import com.juancruzy.earlybuyservice.shared.exception.ApplicationException;
import com.juancruzy.earlybuyservice.shared.exception.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

public class BearerAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(BearerAuthenticationFilter.class);
    private static final Pattern BEARER = Pattern.compile(
            "^Bearer +([a-zA-Z0-9\\-._~+/]+=*)$", Pattern.CASE_INSENSITIVE);

    private final AuthenticationManager authenticationManager;
    private final SecurityErrorHandler errorHandler;
    private final RequestMatcher protectedEndpoints;

    public BearerAuthenticationFilter(AuthenticationManager authenticationManager,
                                      SecurityErrorHandler errorHandler, RequestMatcher protectedEndpoints) {
        this.authenticationManager = authenticationManager;
        this.errorHandler = errorHandler;
        this.protectedEndpoints = protectedEndpoints;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return "OPTIONS".equals(request.getMethod()) || !protectedEndpoints.matches(request);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                  FilterChain filterChain) throws ServletException, IOException {
        try {
            String token = extractToken(request);
            var authentication = authenticationManager.authenticate(
                    new PreAuthenticatedAuthenticationToken(null, token));
            var context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
        } catch (ApplicationException exception) {
            SecurityContextHolder.clearContext();
            if (exception.getErrorCode() == ErrorCode.AUTH_UNAVAILABLE) {
                log.warn("No se pudo consultar Auth para autenticar la solicitud");
            }
            errorHandler.write(request, response, exception.getErrorCode());
            return;
        } catch (AuthenticationException exception) {
            SecurityContextHolder.clearContext();
            errorHandler.write(request, response, ErrorCode.INVALID_JWT);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private String extractToken(HttpServletRequest request) {
        var values = Collections.list(request.getHeaders(HttpHeaders.AUTHORIZATION));
        if (values.isEmpty() || (values.size() == 1 && values.getFirst().isBlank())) {
            throw new ApplicationException(ErrorCode.MISSING_JWT);
        }
        if (values.size() != 1) {
            throw new ApplicationException(ErrorCode.INVALID_JWT);
        }

        String value = values.getFirst().strip();
        if (value.equalsIgnoreCase("Bearer")) {
            throw new ApplicationException(ErrorCode.MISSING_JWT);
        }
        var matcher = BEARER.matcher(value);
        if (!matcher.matches()) {
            throw new ApplicationException(ErrorCode.INVALID_JWT);
        }
        return matcher.group(1);
    }
}
