package com.juancruzy.earlybuyservice.auth.exception;

import com.juancruzy.earlybuyservice.shared.exception.ApplicationException;
import com.juancruzy.earlybuyservice.shared.exception.ErrorCode;

public class InvalidJwtException extends ApplicationException {

    public InvalidJwtException() {
        super(ErrorCode.INVALID_JWT);
    }
}
