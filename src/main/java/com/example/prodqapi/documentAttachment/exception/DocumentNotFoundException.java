package com.example.prodqapi.documentAttachment.exception;

import lombok.Getter;

/**
 * Exception thrown when a document attachment is not found
 */
@Getter
public class DocumentNotFoundException extends RuntimeException {
    private final Long documentId;

    public DocumentNotFoundException(String message) {
        super(message);
        this.documentId = null;
    }

    public DocumentNotFoundException(String message, Long documentId) {
        super(message);
        this.documentId = documentId;
    }

    public DocumentNotFoundException(Long documentId) {
        super("Document not found with ID: " + documentId);
        this.documentId = documentId;
    }
}
