package com.sopuro.appeal_system;

import com.sopuro.appeal_system.dtos.GenericErrorResponse;
import com.sopuro.appeal_system.exceptions.appeal.CaseNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.time.Instant;

@ControllerAdvice
public class RestExceptionHandler {
    @ExceptionHandler(CaseNotFoundException.class)
    public ResponseEntity<GenericErrorResponse> handleError(CaseNotFoundException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new GenericErrorResponse(404, request.getRequestURI(), ex.getMessage(), Instant.now()));
    }
}
