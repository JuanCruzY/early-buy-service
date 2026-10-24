package com.juancruzy.earlybuyservice.auth.exception;

import com.juancruzy.earlybuyservice.shared.exception.ApplicationException;
import com.juancruzy.earlybuyservice.shared.exception.ErrorCode;

public class AuthUnavailableException extends ApplicationException {

    public AuthUnavailableException() {
        super(ErrorCode.AUTH_UNAVAILABLE);
    }

    public AuthUnavailableException(Throwable cause) {
        super(ErrorCode.AUTH_UNAVAILABLE, cause);
    }
}
