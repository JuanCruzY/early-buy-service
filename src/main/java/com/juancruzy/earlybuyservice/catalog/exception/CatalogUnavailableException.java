package com.juancruzy.earlybuyservice.catalog.exception;

import com.juancruzy.earlybuyservice.shared.exception.ApplicationException;
import com.juancruzy.earlybuyservice.shared.exception.ErrorCode;

public class CatalogUnavailableException extends ApplicationException {

    public CatalogUnavailableException() {
        super(ErrorCode.CATALOG_UNAVAILABLE);
    }

    public CatalogUnavailableException(Throwable cause) {
        super(ErrorCode.CATALOG_UNAVAILABLE, cause);
    }
}
