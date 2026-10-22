package com.zanzibar.workflow.exception;

import org.springframework.http.HttpStatus;

/** An expected workflow/business-rule failure with an appropriate HTTP status. */
public class WorkflowException extends RuntimeException {

    private final HttpStatus status;

    public WorkflowException(String message) {
        this(HttpStatus.BAD_REQUEST, message, null);
    }

    public WorkflowException(HttpStatus status, String message) {
        this(status, message, null);
    }

    public WorkflowException(String message, Throwable cause) {
        this(HttpStatus.BAD_REQUEST, message, cause);
    }

    public WorkflowException(HttpStatus status, String message, Throwable cause) {
        super(message, cause);
        this.status = status == null ? HttpStatus.BAD_REQUEST : status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
