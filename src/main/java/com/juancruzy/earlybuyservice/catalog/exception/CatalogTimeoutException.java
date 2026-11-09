package com.juancruzy.earlybuyservice.catalog.exception;

import com.juancruzy.earlybuyservice.shared.exception.ApplicationException;
import com.juancruzy.earlybuyservice.shared.exception.ErrorCode;

public class CatalogTimeoutException extends ApplicationException {

    public CatalogTimeoutException() {
        super(ErrorCode.TIMEOUT_CATALOG);
    }
}
