package com.codeassistant.backend.exception;

public class AiBusyException extends RuntimeException {

    public AiBusyException(String message) {
        super(message);
    }
}
