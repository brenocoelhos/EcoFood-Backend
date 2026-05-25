package com.example.demo.common;

import java.util.List;

public record ValidationErrorResponse(
        String message,
        List<FieldValidationError> errors
) {
}
