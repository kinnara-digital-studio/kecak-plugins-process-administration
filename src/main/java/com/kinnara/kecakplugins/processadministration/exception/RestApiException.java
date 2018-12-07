package com.kinnara.kecakplugins.processadministration.exception;

public class RestApiException extends Exception {
    private int httpErrorCode;

    public RestApiException(String message) {
        super(message);
    }

    public RestApiException(int errorCode, String message) {
        super(message);
        this.httpErrorCode = errorCode;
    }

    public int getErrorCode() {
        return httpErrorCode;
    }
}
