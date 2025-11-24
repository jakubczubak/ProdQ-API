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
}
