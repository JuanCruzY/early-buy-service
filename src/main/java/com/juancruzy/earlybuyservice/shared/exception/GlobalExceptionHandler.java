package com.juancruzy.earlybuyservice.shared.exception;

import java.time.Instant;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApplicationException.class)
    public @Nullable ResponseEntity<Object> handleApplicationException(
            ApplicationException exception, WebRequest request) {
        ErrorCode code = exception.getErrorCode();
        return handleExceptionInternal(exception, createError(code.getStatus(), code, request),
                new HttpHeaders(), code.getStatus(), request);
    }

    @ExceptionHandler(Exception.class)
    public @Nullable ResponseEntity<Object> handleUnexpectedException(
            Exception exception, WebRequest request) {
        if (exception instanceof ErrorResponse errorResponse) {
            return handleExceptionInternal(exception, null, errorResponse.getHeaders(),
                    errorResponse.getStatusCode(), request);
        }

        ResponseStatus responseStatus = AnnotatedElementUtils.findMergedAnnotation(
                exception.getClass(), ResponseStatus.class);
        HttpStatusCode status = responseStatus == null
                ? ErrorCode.INTERNAL_ERROR.getStatus() : responseStatus.code();
        return handleExceptionInternal(exception, null, new HttpHeaders(), status, request);
    }

    @Override
    protected @Nullable ResponseEntity<Object> handleExceptionInternal(
            Exception exception, @Nullable Object body, HttpHeaders headers,
            HttpStatusCode statusCode, WebRequest request) {
        if (statusCode.is5xxServerError()) {
            log.error("Error al procesar la solicitud {}", getPath(request), exception);
        }

        ErrorCode fallbackCode = statusCode.is5xxServerError()
                ? ErrorCode.INTERNAL_ERROR : ErrorCode.INVALID_REQUEST;
        ApiError error = body instanceof ApiError apiError
                ? apiError : createError(statusCode, fallbackCode, request);

        return super.handleExceptionInternal(exception, error, headers, statusCode, request);
    }

    private ApiError createError(HttpStatusCode status, ErrorCode code, WebRequest request) {
        return new ApiError(Instant.now(), status.value(), code.name(),
                code.getDefaultMessage(), getPath(request));
    }

    private String getPath(WebRequest request) {
        return ((ServletWebRequest) request).getRequest().getRequestURI();
    }
}
