package com.xiyouji.exception;

/**
 * Shared-state storage is unavailable. Distributed mode must fail fast rather
 * than silently switching to an instance-local memory store.
 */
public class StorageUnavailableException extends BusinessException {

    public StorageUnavailableException(String message, Throwable cause) {
        super("SHARED_STORAGE_UNAVAILABLE", message, 503, cause);
    }
}
