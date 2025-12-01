package com.example.prodqapi.documentAttachment;

import com.example.prodqapi.order.Order;
import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Document Attachment Entity
 *
 * Represents files attached to orders (invoices, delivery notes, certificates, photos).
 * Supports multiple files per order with categorization and ordering.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "document_attachment")
public class DocumentAttachment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Parent order for this document
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    @JsonBackReference("order-documents")
    private Order order;

    /**
     * Original filename (as uploaded by user)
     */
    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    /**
     * Server filesystem path (unique filename with UUID)
     */
    @Column(name = "file_path", nullable = false, length = 512)
    private String filePath;

    /**
     * File size in bytes
     */
    @Column(name = "file_size", nullable = false)
    private Long fileSize;

    /**
     * MIME type (e.g., application/pdf, image/jpeg)
     */
    @Column(name = "file_type", nullable = false, length = 100)
    private String fileType;

    /**
     * Document category (INVOICE, DELIVERY_NOTE, CERTIFICATE, PHOTO)
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 50)
    private DocumentCategory category;

    /**
     * Upload timestamp
     */
    @Column(name = "upload_date", nullable = false, updatable = false)
    private LocalDateTime uploadDate;

    /**
     * User ID who uploaded the document (nullable for system uploads)
     */
    @Column(name = "uploaded_by_user_id")
    private Long uploadedByUserId;

    /**
     * Display order for sorting documents (lower = higher priority)
     * Used for manual reordering in gallery
     */
    @Column(name = "display_order", nullable = false)
    @Builder.Default
    private Integer displayOrder = 0;

    /**
     * Audit: creation timestamp
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Audit: last update timestamp
     */
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /**
     * Set timestamps on creation
     */
    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
        uploadDate = now;
    }

    /**
     * Update timestamp on modification
     */
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
