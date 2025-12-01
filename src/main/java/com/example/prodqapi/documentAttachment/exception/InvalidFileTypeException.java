package com.example.prodqapi.documentAttachment.exception;

import com.example.prodqapi.documentAttachment.DocumentCategory;
import lombok.Getter;

import java.util.List;

/**
 * Exception thrown when uploaded file type is not allowed for the specified category
 */
@Getter
public class InvalidFileTypeException extends RuntimeException {
    private final String fileType;
    private final DocumentCategory category;
    private final List<String> allowedTypes;

    public InvalidFileTypeException(String message) {
        super(message);
        this.fileType = null;
        this.category = null;
        this.allowedTypes = null;
    }

    public InvalidFileTypeException(
            String message,
            String fileType,
            DocumentCategory category,
            List<String> allowedTypes
    ) {
        super(message);
        this.fileType = fileType;
        this.category = category;
        this.allowedTypes = allowedTypes;
    }

    public InvalidFileTypeException(
            String fileType,
            DocumentCategory category,
            List<String> allowedTypes
    ) {
        super(String.format(
                "File type '%s' not allowed for category %s. Allowed types: %s",
                fileType,
                category,
                String.join(", ", allowedTypes)
        ));
        this.fileType = fileType;
        this.category = category;
        this.allowedTypes = allowedTypes;
    }
}
