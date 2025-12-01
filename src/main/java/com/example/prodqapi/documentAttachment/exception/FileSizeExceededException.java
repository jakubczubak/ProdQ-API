package com.example.prodqapi.documentAttachment.exception;

import lombok.Getter;

/**
 * Exception thrown when uploaded file size exceeds the maximum allowed limit
 */
@Getter
public class FileSizeExceededException extends RuntimeException {
    private final Long fileSize;
    private final Long maxSize;

    public FileSizeExceededException(String message) {
        super(message);
        this.fileSize = null;
        this.maxSize = null;
    }

    public FileSizeExceededException(String message, Long fileSize, Long maxSize) {
        super(message);
        this.fileSize = fileSize;
        this.maxSize = maxSize;
    }

    public FileSizeExceededException(Long fileSize, Long maxSize) {
        super(String.format(
                "File size %d bytes exceeds maximum limit of %d bytes (%.2f MB)",
                fileSize,
                maxSize,
                maxSize / (1024.0 * 1024.0)
        ));
        this.fileSize = fileSize;
        this.maxSize = maxSize;
    }
}
