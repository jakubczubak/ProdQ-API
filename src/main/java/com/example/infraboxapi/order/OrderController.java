package com.example.infraboxapi.order;


import com.example.infraboxapi.common.CommonService;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.util.List;

@RestController
@RequestMapping("/api/order/")
@AllArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final CommonService commonService;

    @GetMapping("/all")
    public ResponseEntity<List<Order>> getAllOrders() {
        try {
            return ResponseEntity.ok(orderService.getAllOrders());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<Order> getOrderById(@PathVariable Integer id) {
        try {
            Order order = orderService.getOrderById(id);
            if (order != null) {
                return ResponseEntity.ok(order);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    @PostMapping("/add")
    public ResponseEntity<?> addOrder(@Valid @RequestBody OrderDTO orderDTO, BindingResult bindingResult) {

        if (bindingResult.hasErrors()) {
            return commonService.handleBindingResult(bindingResult);
        }

        try {
            Order createdOrder = orderService.addOrder(orderDTO);
            return ResponseEntity.ok(createdOrder);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @DeleteMapping("/delete/{id}")
    public ResponseEntity<String> deleteOrder(@PathVariable Integer id) {
        try {
            orderService.deleteOrder(id);
            return ResponseEntity.ok("Order deleted successfully");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @PutMapping("/update")
    public ResponseEntity<String> updateOrder(@RequestBody OrderDTO orderDTO) {
        try {
            orderService.updateOrderFromDTO(orderDTO);
            return ResponseEntity.ok("Order updated successfully");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @PatchMapping("/{orderId}/item/{itemId}/price")
    public ResponseEntity<String> updateItemPrice(
            @PathVariable Integer orderId,
            @PathVariable Integer itemId,
            @RequestBody java.math.BigDecimal newPrice) {

        try {
            orderService.updateItemPrice(orderId, itemId, newPrice);
            return ResponseEntity.ok("Price updated successfully");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @PostMapping("/{orderId}/partial-delivery")
    public ResponseEntity<String> partialDelivery(
            @PathVariable Integer orderId,
            @RequestBody java.util.List<com.example.infraboxapi.orderItem.OrderItem> updatedItems) {

        try {
            orderService.partialDelivery(orderId, updatedItems);
            return ResponseEntity.ok("Partial delivery processed successfully");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @PostMapping("/{orderId}/mark-invoice-pending")
    public ResponseEntity<String> markInvoicePending(@PathVariable Integer orderId) {
        try {
            orderService.markInvoicePending(orderId);
            return ResponseEntity.ok("Order marked as awaiting invoice");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    /**
     * Update quality rating for a delivered order
     * POST /api/order/{orderId}/quality-rating
     */
    @PostMapping("/{orderId}/quality-rating")
    public ResponseEntity<String> updateQualityRating(
            @PathVariable Integer orderId,
            @RequestBody java.util.Map<String, Object> payload) {
        try {
            Integer rating = (Integer) payload.get("rating");
            String notes = (String) payload.get("notes");

            if (rating == null || rating < 1 || rating > 5) {
                return ResponseEntity.badRequest().body("Rating must be between 1 and 5");
            }

            orderService.updateQualityRating(orderId, rating, notes);
            return ResponseEntity.ok("Quality rating updated successfully");
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @PostMapping("/{orderId}/mark-invoice-received")
    public ResponseEntity<String> markInvoiceReceived(@PathVariable Integer orderId) {
        try {
            orderService.markInvoiceReceived(orderId);
            return ResponseEntity.ok("Invoice marked as received");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @PostMapping("/{orderId}/close")
    public ResponseEntity<String> closeOrder(@PathVariable Integer orderId) {
        try {
            orderService.closeOrder(orderId);
            return ResponseEntity.ok("Order closed successfully");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @PostMapping("/{orderId}/close-short")
    public ResponseEntity<String> closeOrderShort(
            @PathVariable Integer orderId,
            @RequestBody java.util.Map<String, String> payload) {
        try {
            String reason = payload.get("reason");

            // Validate reason on controller level as well
            if (reason == null || reason.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                    .body("Reason is required for closing order as incomplete");
            }
            if (reason.trim().length() < 10) {
                return ResponseEntity.badRequest()
                    .body("Reason must be at least 10 characters long");
            }

            orderService.closeOrderShort(orderId, reason);
            return ResponseEntity.ok("Order marked for incomplete closure. Awaiting invoice.");
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @PostMapping("/{orderId}/finalize-closed-short")
    public ResponseEntity<String> finalizeClosedShort(@PathVariable Integer orderId) {
        try {
            orderService.finalizeClosedShort(orderId);
            return ResponseEntity.ok("Order finalized as incomplete");
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @PostMapping("/{orderId}/invoice/upload")
    public ResponseEntity<String> uploadInvoice(
            @PathVariable Integer orderId,
            @RequestParam("file") MultipartFile file) {

        try {
            orderService.uploadInvoice(orderId, file);
            return ResponseEntity.ok("Invoice uploaded successfully");
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @GetMapping("/{orderId}/invoice/download")
    public ResponseEntity<Resource> downloadInvoice(@PathVariable Integer orderId) {

        try {
            Path filePath = orderService.getInvoiceFilePath(orderId);
            Resource resource = new UrlResource(filePath.toUri());

            if (resource.exists() && resource.isReadable()) {
                String filename = orderService.getInvoiceFileName(orderId);
                return ResponseEntity.ok()
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                        .body(resource);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    @DeleteMapping("/{orderId}/invoice/delete")
    public ResponseEntity<String> deleteInvoice(@PathVariable Integer orderId) {

        try {
            orderService.deleteInvoice(orderId);
            return ResponseEntity.ok("Invoice deleted successfully");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    // ==================== THREE-WAY MATCH ENDPOINTS ====================

    /**
     * Save invoice items entered by user
     * POST /api/order/{orderId}/invoice/save-items
     */
    @PostMapping("/{orderId}/invoice/save-items")
    public ResponseEntity<?> saveInvoiceItems(
            @PathVariable Integer orderId,
            @RequestBody InvoiceItemsDTO invoiceItemsDTO) {

        try {
            orderService.saveInvoiceItems(orderId, invoiceItemsDTO);
            Order updatedOrder = orderService.getOrderById(orderId);
            return ResponseEntity.ok(updatedOrder);
        } catch (IllegalStateException | IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    /**
     * Perform three-way match reconciliation
     * POST /api/order/{orderId}/invoice/reconcile
     */
    @PostMapping("/{orderId}/invoice/reconcile")
    public ResponseEntity<?> performReconciliation(@PathVariable Integer orderId) {

        try {
            com.example.infraboxapi.invoiceReconciliation.InvoiceReconciliation reconciliation =
                orderService.performThreeWayMatch(orderId);

            // Convert to DTO
            InvoiceReconciliationDTO dto = convertToReconciliationDTO(reconciliation);
            return ResponseEntity.ok(dto);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            // Race condition: Another thread already created the reconciliation
            // This is OK - just return the existing reconciliation
            System.out.println("DEBUG: DataIntegrityViolationException caught - concurrent reconciliation creation detected. Fetching existing reconciliation.");
            try {
                com.example.infraboxapi.invoiceReconciliation.InvoiceReconciliation existingReconciliation =
                    orderService.getExistingReconciliation(orderId);
                InvoiceReconciliationDTO dto = convertToReconciliationDTO(existingReconciliation);
                return ResponseEntity.ok(dto);
            } catch (Exception ex) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to retrieve existing reconciliation: " + ex.getMessage());
            }
        } catch (IllegalStateException | IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    /**
     * Approve invoice discrepancies with justification
     * POST /api/order/{orderId}/invoice/approve-discrepancies
     */
    @PostMapping("/{orderId}/invoice/approve-discrepancies")
    public ResponseEntity<String> approveDiscrepancies(
            @PathVariable Integer orderId,
            @RequestBody DiscrepancyApprovalDTO approvalDTO) {

        try {
            orderService.approveInvoiceDiscrepancies(orderId, approvalDTO.getJustification());
            return ResponseEntity.ok("Discrepancies approved successfully");
        } catch (IllegalStateException | IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    /**
     * Helper method to convert InvoiceReconciliation entity to DTO
     */
    private InvoiceReconciliationDTO convertToReconciliationDTO(
            com.example.infraboxapi.invoiceReconciliation.InvoiceReconciliation reconciliation) {

        // Parse discrepancies JSON
        List<com.example.infraboxapi.invoiceReconciliation.InvoiceDiscrepancy> discrepancies = new java.util.ArrayList<>();
        if (reconciliation.getDiscrepanciesJson() != null && !reconciliation.getDiscrepanciesJson().isEmpty()) {
            try {
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                discrepancies = mapper.readValue(
                    reconciliation.getDiscrepanciesJson(),
                    mapper.getTypeFactory().constructCollectionType(
                        List.class,
                        com.example.infraboxapi.invoiceReconciliation.InvoiceDiscrepancy.class
                    )
                );
            } catch (Exception e) {
                // Log error but continue
            }
        }

        // Build totals
        InvoiceReconciliationDTO.TotalsDTO totals = InvoiceReconciliationDTO.TotalsDTO.builder()
                .po(InvoiceReconciliationDTO.ThreeWayTotal.builder()
                        .net(reconciliation.getPoTotalNet())
                        .vat(reconciliation.getPoTotalVat())
                        .gross(reconciliation.getPoTotalGross())
                        .build())
                .delivery(InvoiceReconciliationDTO.ThreeWayTotal.builder()
                        .net(reconciliation.getDeliveryTotalNet())
                        .vat(reconciliation.getDeliveryTotalVat())
                        .gross(reconciliation.getDeliveryTotalGross())
                        .build())
                .invoice(InvoiceReconciliationDTO.ThreeWayTotal.builder()
                        .net(reconciliation.getInvoiceTotalNet())
                        .vat(reconciliation.getInvoiceTotalVat())
                        .gross(reconciliation.getInvoiceTotalGross())
                        .build())
                .build();

        return InvoiceReconciliationDTO.builder()
                .orderId(reconciliation.getOrder().getId())
                .reconciliationDate(reconciliation.getReconciliationDate())
                .status(reconciliation.getReconciliationStatus())
                .totals(totals)
                .discrepancies(discrepancies)
                .hasDiscrepancies(!discrepancies.isEmpty())
                .build();
    }
}
