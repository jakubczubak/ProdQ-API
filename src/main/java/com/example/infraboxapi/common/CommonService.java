package com.example.infraboxapi.common;


import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.validation.BindingResult;

import java.util.ArrayList;
import java.util.List;

@Service
public class CommonService {
    public ResponseEntity<String> handleBindingResult(BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            List<String> errors = new ArrayList<>();
            bindingResult.getAllErrors().forEach(error -> {
                String errorMessage = error.getDefaultMessage();
                errors.add(errorMessage);
            });

            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid data. Errors: " + errors);
        }else {
            return ResponseEntity.ok("No errors found.");
        }
    }
}
