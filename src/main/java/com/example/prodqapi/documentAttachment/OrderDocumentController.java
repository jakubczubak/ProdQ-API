package com.example.prodqapi.documentAttachment;

import lombok.AllArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * REST Controller for Document Attachment operations
 *
 * Provides endpoints for uploading, downloading, and managing document attachments for orders.
 *
 * Permissions:
 * - Upload/View/Download: ADMIN + USER
 * - Delete: ADMIN only
 */
@RestController
@RequestMapping("/api/order")
@AllArgsConstructor
public class OrderDocumentController {

    private final DocumentAttachmentService documentService;

    /**
     * Upload document for an order
     * POST /api/order/{orderId}/documents/upload
     *
     * Access: ADMIN + USER
     *
     * @param orderId Order ID
     * @param file Uploaded file
     * @param category Document category (INVOICE, DELIVERY_NOTE, CERTIFICATE, PHOTO)
     * @return DocumentAttachmentDTO with metadata
     */
    @PostMapping("/{orderId}/documents/upload")
    @PreAuthorize("hasAnyRole('ADMIN', 'USER')")
    public ResponseEntity<?> uploadDocument(
            @PathVariable Integer orderId,
            @RequestParam("file") MultipartFile file,
            @RequestParam("category") DocumentCategory category,
            @RequestHeader(value = "Authorization", required = false) String authHeader
    ) {
        try {
            // TODO: Extract userId from JWT token in authHeader
            // For now, using null - should be replaced with actual user ID extraction
            Long userId = extractUserIdFromToken(authHeader);

            DocumentAttachmentDTO document = documentService.uploadDocument(orderId, file, category, userId);
            return ResponseEntity.ok(document);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to upload document: " + e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    /**
     * Get all documents for an order
     * GET /api/order/{orderId}/documents
     *
     * Access: ADMIN + USER
     *
     * @param orderId Order ID
     * @return List of DocumentAttachmentDTO
     */
    @GetMapping("/{orderId}/documents")
    @PreAuthorize("hasAnyRole('ADMIN', 'USER')")
    public ResponseEntity<?> getDocuments(@PathVariable Integer orderId) {
        try {
            List<DocumentAttachmentDTO> documents = documentService.getDocuments(orderId);
            return ResponseEntity.ok(documents);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    /**
     * Get documents by category for an order
     * GET /api/order/{orderId}/documents/category/{category}
     *
     * Access: ADMIN + USER
     *
     * @param orderId Order ID
     * @param category Document category
     * @return List of DocumentAttachmentDTO matching criteria
     */
    @GetMapping("/{orderId}/documents/category/{category}")
    @PreAuthorize("hasAnyRole('ADMIN', 'USER')")
    public ResponseEntity<?> getDocumentsByCategory(
            @PathVariable Integer orderId,
            @PathVariable DocumentCategory category
    ) {
        try {
            List<DocumentAttachmentDTO> documents = documentService.getDocumentsByCategory(orderId, category);
            return ResponseEntity.ok(documents);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    /**
     * Download document
     * GET /api/order/documents/{documentId}/download
     *
     * Access: ADMIN + USER
     *
     * @param documentId Document ID
     * @return Document file as Resource
     */
    @GetMapping("/documents/{documentId}/download")
    @PreAuthorize("hasAnyRole('ADMIN', 'USER')")
    public ResponseEntity<Resource> downloadDocument(@PathVariable Long documentId) {
        try {
            // Get document metadata
            DocumentAttachment document = documentService.getDocumentById(documentId);

            // Read file contents
            byte[] fileData = documentService.downloadDocument(documentId);
            ByteArrayResource resource = new ByteArrayResource(fileData);

            // Determine content type
            String contentType = document.getFileType();
            if (contentType == null) {
                contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;
            }

            // Build response headers
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType(contentType));
            headers.setContentDispositionFormData("attachment", document.getFileName());
            headers.setContentLength(fileData.length);

            return new ResponseEntity<>(resource, headers, HttpStatus.OK);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    /**
     * Delete document
     * DELETE /api/order/documents/{documentId}
     *
     * Access: ADMIN ONLY
     *
     * @param documentId Document ID
     * @return Success message
     */
    @DeleteMapping("/documents/{documentId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> deleteDocument(@PathVariable Long documentId) {
        try {
            documentService.deleteDocument(documentId);
            return ResponseEntity.ok("Document deleted successfully");
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to delete document: " + e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    /**
     * Update display order for drag-drop reordering
     * PATCH /api/order/documents/{documentId}/order
     *
     * Access: ADMIN + USER
     *
     * @param documentId Document ID
     * @param newOrder New display order
     * @return Success message
     */
    @PatchMapping("/documents/{documentId}/order")
    @PreAuthorize("hasAnyRole('ADMIN', 'USER')")
    public ResponseEntity<String> updateDisplayOrder(
            @PathVariable Long documentId,
            @RequestBody Integer newOrder
    ) {
        try {
            documentService.updateDisplayOrder(documentId, newOrder);
            return ResponseEntity.ok("Display order updated successfully");
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    // ========== HELPER METHODS ==========

    /**
     * Extract user ID from JWT token
     * TODO: Implement proper JWT token parsing
     *
     * @param authHeader Authorization header (Bearer token)
     * @return User ID or null
     */
    private Long extractUserIdFromToken(String authHeader) {
        // TODO: Implement JWT token parsing to extract user ID
        // For now, returning null - should be replaced with actual implementation
        // Example implementation:
        // if (authHeader != null && authHeader.startsWith("Bearer ")) {
        //     String token = authHeader.substring(7);
        //     Claims claims = Jwts.parser().setSigningKey(secretKey).parseClaimsJws(token).getBody();
        //     return claims.get("userId", Long.class);
        // }
        return null;
    }
}
