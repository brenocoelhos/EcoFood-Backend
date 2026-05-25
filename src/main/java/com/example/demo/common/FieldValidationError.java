package com.example.demo.common;

public record FieldValidationError(
        String field,
        String message
) {
}
