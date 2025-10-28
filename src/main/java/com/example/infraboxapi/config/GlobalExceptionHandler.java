package com.example.infraboxapi.config;

import com.example.infraboxapi.materialReservation.exception.InsufficientMaterialException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(InsufficientMaterialException.class)
    public ResponseEntity<Map<String, Object>> handleInsufficientMaterialException(
        InsufficientMaterialException ex
    ) {
        Map<String, Object> error = new HashMap<>();
        error.put("error", "INSUFFICIENT_MATERIAL");
        error.put("message", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }
}