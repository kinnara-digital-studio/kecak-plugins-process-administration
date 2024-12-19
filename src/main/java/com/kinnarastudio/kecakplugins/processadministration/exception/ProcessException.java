package com.kinnarastudio.kecakplugins.processadministration.exception;

public class ProcessException extends Exception {
    public ProcessException(String message) {
        super(message);
    }

    public ProcessException(String message, Throwable cause) {
        super(message, cause);
    }
}
