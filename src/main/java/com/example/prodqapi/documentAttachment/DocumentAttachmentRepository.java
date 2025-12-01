package com.example.prodqapi.documentAttachment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for DocumentAttachment entity
 *
 * Provides CRUD operations and custom queries for document attachments.
 */
@Repository
public interface DocumentAttachmentRepository extends JpaRepository<DocumentAttachment, Long> {

    /**
     * Find all documents for a specific order, ordered by display order and upload date
     *
     * @param orderId Order ID
     * @return List of document attachments sorted by displayOrder ASC, uploadDate DESC
     */
    List<DocumentAttachment> findByOrderIdOrderByDisplayOrderAscUploadDateDesc(Long orderId);

    /**
     * Find documents by order ID and category
     *
     * @param orderId Order ID
     * @param category Document category (INVOICE, DELIVERY_NOTE, CERTIFICATE, PHOTO)
     * @return List of document attachments matching criteria
     */
    List<DocumentAttachment> findByOrderIdAndCategory(Long orderId, DocumentCategory category);

    /**
     * Count total documents for an order
     *
     * @param orderId Order ID
     * @return Number of documents attached to the order
     */
    long countByOrderId(Long orderId);

    /**
     * Check if a document with specific filename exists for an order
     * Useful for preventing duplicate uploads
     *
     * @param orderId Order ID
     * @param fileName File name
     * @return True if document exists, false otherwise
     */
    boolean existsByOrderIdAndFileName(Long orderId, String fileName);

    /**
     * Delete all documents for a specific order
     * Useful for cleanup when order is deleted
     *
     * @param orderId Order ID
     */
    void deleteByOrderId(Long orderId);
}
