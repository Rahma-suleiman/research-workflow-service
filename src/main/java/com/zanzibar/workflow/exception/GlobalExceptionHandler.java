package com.zanzibar.workflow.exception;

import jakarta.validation.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(WorkflowException.class)
    public ResponseEntity<Map<String, String>> handleWorkflowException(
        WorkflowException exception
    ) {
        return error(exception.getStatus(), exception.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidationException(
        MethodArgumentNotValidException exception
    ) {
        String message = exception.getBindingResult().getFieldErrors()
            .stream()
            .map(error -> error.getField() + ": " + error.getDefaultMessage())
            .findFirst()
            .orElse("Request validation failed.");
        return error(HttpStatus.BAD_REQUEST, message);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Map<String, String>> handleConstraintViolation(
        ConstraintViolationException exception
    ) {
        return error(HttpStatus.BAD_REQUEST, "Request validation failed.");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> handleUnreadableRequest(
        HttpMessageNotReadableException exception
    ) {
        return error(HttpStatus.BAD_REQUEST,
            "Invalid request body. Check the JSON fields and enum values.");
    }

    @ExceptionHandler({DataIntegrityViolationException.class,
        ObjectOptimisticLockingFailureException.class})
    public ResponseEntity<Map<String, String>> handleConflict(Exception exception) {
        return error(HttpStatus.CONFLICT,
            "The record conflicts with another change. Reload the workflow and retry if appropriate.");
    }

    private ResponseEntity<Map<String, String>> error(
        HttpStatus status,
        String message
    ) {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("status", status.name());
        body.put("message", message == null ? "Request failed." : message);
        return ResponseEntity.status(status).body(body);
    }
}
