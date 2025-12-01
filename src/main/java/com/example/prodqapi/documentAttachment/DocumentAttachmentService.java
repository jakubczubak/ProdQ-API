package com.example.prodqapi.documentAttachment;

import com.example.prodqapi.order.Order;
import com.example.prodqapi.order.OrderRepository;
import com.example.prodqapi.orderChangeLog.OrderChangeLog;
import com.example.prodqapi.orderChangeLog.OrderChangeLogRepository;
import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service for managing document attachments
 *
 * Handles upload, download, delete operations for order documents.
 * Implements file validation, filesystem storage, and metadata management.
 */
@Service
@AllArgsConstructor
public class DocumentAttachmentService {

    private final DocumentAttachmentRepository documentRepository;
    private final OrderRepository orderRepository;
    private final OrderChangeLogRepository orderChangeLogRepository;

    // Configuration constants
    private static final String UPLOAD_BASE_DIR = "uploads/order-documents/";
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB

    // Allowed file types per category
    private static final List<String> INVOICE_TYPES = Arrays.asList("pdf", "jpg", "jpeg", "png");
    private static final List<String> DELIVERY_NOTE_TYPES = Arrays.asList("pdf", "jpg", "jpeg", "png");
    private static final List<String> CERTIFICATE_TYPES = Arrays.asList("pdf", "jpg", "jpeg", "png");
    private static final List<String> PHOTO_TYPES = Arrays.asList("jpg", "jpeg", "png");

    /**
     * Upload document for an order
     *
     * @param orderId Order ID (converted from Integer to Long)
     * @param file Uploaded file
     * @param category Document category
     * @param userId User ID who uploaded the document
     * @return DocumentAttachmentDTO with metadata
     * @throws IOException If file operations fail
     * @throws IllegalArgumentException If validation fails
     */
    @Transactional
    public DocumentAttachmentDTO uploadDocument(
            Integer orderId,
            MultipartFile file,
            DocumentCategory category,
            Long userId
    ) throws IOException {

        // Convert Integer orderId to Long for entity
        Long orderIdLong = orderId != null ? orderId.longValue() : null;

        // Validate order exists
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found with ID: " + orderId));

        // Validate file
        validateFile(file, category);

        // Generate unique filename
        String originalFilename = file.getOriginalFilename();
        String fileExtension = getFileExtension(originalFilename);
        String uniqueFilename = UUID.randomUUID().toString() + "_" + originalFilename;

        // Create directory structure: uploads/order-documents/{orderId}/
        Path orderDir = Paths.get(UPLOAD_BASE_DIR, String.valueOf(orderId));
        Files.createDirectories(orderDir);

        // Save file
        Path filePath = orderDir.resolve(uniqueFilename);
        Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

        // Create entity
        DocumentAttachment document = DocumentAttachment.builder()
                .order(order)
                .fileName(originalFilename)
                .filePath(filePath.toString())
                .fileSize(file.getSize())
                .fileType(file.getContentType())
                .category(category)
                .uploadedByUserId(userId)
                .displayOrder(getNextDisplayOrder(orderIdLong))
                .build();

        // Save to database
        document = documentRepository.save(document);

        // AUTOMATIC STATUS TRANSITION: If uploading INVOICE and order is in invoice_pending status,
        // automatically transition to invoice_data_pending (Three-Way Match workflow)
        if (category == DocumentCategory.INVOICE && "invoice_pending".equals(order.getStatus())) {
            order.setStatus("invoice_data_pending");
            orderRepository.save(order);

            // Create changelog entry
            ZonedDateTime now = ZonedDateTime.now(ZoneId.of("Europe/Warsaw"));
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
            OrderChangeLog changeLog = OrderChangeLog.builder()
                    .orderId(orderId)
                    .type("invoice_uploaded")
                    .field("invoice")
                    .oldValue(null)
                    .newValue(originalFilename)
                    .description("Invoice uploaded: " + originalFilename + " - Status changed to invoice_data_pending")
                    .date(now.format(formatter))
                    .build();
            orderChangeLogRepository.save(changeLog);
        }

        // Map to DTO and return
        return mapToDTO(document);
    }

    /**
     * Get all documents for an order
     *
     * @param orderId Order ID
     * @return List of document DTOs sorted by displayOrder and uploadDate
     */
    public List<DocumentAttachmentDTO> getDocuments(Integer orderId) {
        Long orderIdLong = orderId != null ? orderId.longValue() : null;
        return documentRepository.findByOrderIdOrderByDisplayOrderAscUploadDateDesc(orderIdLong)
                .stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    /**
     * Get documents by category
     *
     * @param orderId Order ID
     * @param category Document category
     * @return List of document DTOs matching criteria
     */
    public List<DocumentAttachmentDTO> getDocumentsByCategory(Integer orderId, DocumentCategory category) {
        Long orderIdLong = orderId != null ? orderId.longValue() : null;
        return documentRepository.findByOrderIdAndCategory(orderIdLong, category)
                .stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    /**
     * Download document (returns file as byte array)
     *
     * @param documentId Document ID
     * @return File contents as byte array
     * @throws IOException If file read fails
     * @throws IllegalArgumentException If document not found
     */
    public byte[] downloadDocument(Long documentId) throws IOException {
        DocumentAttachment document = documentRepository.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found with ID: " + documentId));

        Path filePath = Paths.get(document.getFilePath());
        if (!Files.exists(filePath)) {
            throw new IOException("File not found on filesystem: " + document.getFilePath());
        }

        return Files.readAllBytes(filePath);
    }

    /**
     * Get document metadata (for download operations)
     *
     * @param documentId Document ID
     * @return DocumentAttachment entity
     * @throws IllegalArgumentException If document not found
     */
    public DocumentAttachment getDocumentById(Long documentId) {
        return documentRepository.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found with ID: " + documentId));
    }

    /**
     * Delete document (cleanup filesystem and database)
     *
     * @param documentId Document ID
     * @throws IOException If file deletion fails
     * @throws IllegalArgumentException If document not found
     */
    @Transactional
    public void deleteDocument(Long documentId) throws IOException {
        DocumentAttachment document = documentRepository.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found with ID: " + documentId));

        // Capture order reference and document category BEFORE deletion
        Order order = document.getOrder();
        Integer orderId = order.getId();
        DocumentCategory deletedCategory = document.getCategory();
        String deletedFileName = document.getFileName();

        // Delete physical file
        Path filePath = Paths.get(document.getFilePath());
        Files.deleteIfExists(filePath);

        // Delete database record
        documentRepository.delete(document);

        // Check if this was an INVOICE and if any INVOICE documents remain
        if (deletedCategory == DocumentCategory.INVOICE) {
            List<DocumentAttachment> remainingInvoices = documentRepository
                .findByOrderIdAndCategory(orderId.longValue(), DocumentCategory.INVOICE);

            // If no invoices remain AND order is in invoice_data_pending, revert status
            if (remainingInvoices.isEmpty() && "invoice_data_pending".equals(order.getStatus())) {
                order.setStatus("invoice_pending");
                orderRepository.save(order);

                // Create audit log entry
                ZonedDateTime now = ZonedDateTime.now(ZoneId.of("Europe/Warsaw"));
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
                OrderChangeLog changeLog = OrderChangeLog.builder()
                        .orderId(orderId)
                        .type("invoice_deleted")
                        .field("invoice")
                        .oldValue(deletedFileName)
                        .newValue(null)
                        .description("Last invoice deleted - Status reverted to invoice_pending")
                        .date(now.format(formatter))
                        .build();
                orderChangeLogRepository.save(changeLog);
            }
        }
    }

    /**
     * Update display order (for drag-drop reordering)
     *
     * @param documentId Document ID
     * @param newOrder New display order
     * @throws IllegalArgumentException If document not found
     */
    @Transactional
    public void updateDisplayOrder(Long documentId, Integer newOrder) {
        DocumentAttachment document = documentRepository.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found with ID: " + documentId));

        document.setDisplayOrder(newOrder);
        documentRepository.save(document);
    }

    /**
     * Delete all documents for an order (cleanup when order is deleted)
     *
     * @param orderId Order ID
     */
    @Transactional
    public void deleteAllForOrder(Integer orderId) {
        Long orderIdLong = orderId != null ? orderId.longValue() : null;
        List<DocumentAttachment> documents = documentRepository
                .findByOrderIdOrderByDisplayOrderAscUploadDateDesc(orderIdLong);

        for (DocumentAttachment doc : documents) {
            try {
                Path filePath = Paths.get(doc.getFilePath());
                Files.deleteIfExists(filePath);
            } catch (IOException e) {
                // Log but don't fail - continue cleanup
                System.err.println("Failed to delete file: " + doc.getFilePath());
            }
        }

        documentRepository.deleteByOrderId(orderIdLong);
    }

    // ========== HELPER METHODS ==========

    /**
     * Validate uploaded file
     *
     * @param file Uploaded file
     * @param category Document category
     * @throws IllegalArgumentException If validation fails
     */
    private void validateFile(MultipartFile file, DocumentCategory category) {
        // Check if file is empty
        if (file.isEmpty()) {
            throw new IllegalArgumentException("File is empty");
        }

        // Check file size
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("File size exceeds maximum limit of 10MB");
        }

        // Check filename
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isEmpty()) {
            throw new IllegalArgumentException("Invalid filename");
        }

        // Check file extension
        String fileExtension = getFileExtension(originalFilename).toLowerCase();
        List<String> allowedTypes = getAllowedTypesForCategory(category);

        if (!allowedTypes.contains(fileExtension)) {
            throw new IllegalArgumentException(
                    "File type not allowed for category " + category + ". Allowed types: " +
                            String.join(", ", allowedTypes)
            );
        }
    }

    /**
     * Get allowed file extensions for a category
     *
     * @param category Document category
     * @return List of allowed file extensions
     */
    private List<String> getAllowedTypesForCategory(DocumentCategory category) {
        return switch (category) {
            case INVOICE -> INVOICE_TYPES;
            case DELIVERY_NOTE -> DELIVERY_NOTE_TYPES;
            case CERTIFICATE -> CERTIFICATE_TYPES;
            case PHOTO -> PHOTO_TYPES;
        };
    }

    /**
     * Extract file extension from filename
     *
     * @param filename Filename
     * @return File extension (without dot)
     */
    private String getFileExtension(String filename) {
        if (filename == null || filename.isEmpty()) {
            return "";
        }
        int lastDotIndex = filename.lastIndexOf('.');
        if (lastDotIndex == -1) {
            return "";
        }
        return filename.substring(lastDotIndex + 1);
    }

    /**
     * Get next display order for a new document
     *
     * @param orderId Order ID
     * @return Next display order (incremented from current max)
     */
    private Integer getNextDisplayOrder(Long orderId) {
        long count = documentRepository.countByOrderId(orderId);
        return (int) count;
    }

    /**
     * Map DocumentAttachment entity to DTO
     *
     * @param document DocumentAttachment entity
     * @return DocumentAttachmentDTO
     */
    private DocumentAttachmentDTO mapToDTO(DocumentAttachment document) {
        return DocumentAttachmentDTO.builder()
                .id(document.getId())
                .orderId(document.getOrder().getId().longValue())
                .fileName(document.getFileName())
                .fileSize(document.getFileSize())
                .fileType(document.getFileType())
                .category(document.getCategory())
                .uploadDate(document.getUploadDate())
                .uploadedByUserId(document.getUploadedByUserId())
                .displayOrder(document.getDisplayOrder())
                .downloadUrl(DocumentAttachmentDTO.generateDownloadUrl(document.getId()))
                .build();
    }
}
