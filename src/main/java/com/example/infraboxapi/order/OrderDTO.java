package com.example.infraboxapi.order;

import com.example.infraboxapi.orderItem.OrderItemDTO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderDTO {
    private Integer id;

    @NotBlank(message = "Field 'name' cannot be blank")
    private String name;

    @NotBlank(message = "Field 'date' cannot be blank")
    private String date;

    @NotBlank(message = "Field 'status' cannot be blank")
    private String status;

    // Optional - can be derived from supplier relationship
    private String supplierEmail;

    private String supplierMessage;  // Umożliwienie długich wiadomości

    @PositiveOrZero(message = "Value must be a positive number or zero")
    private double totalNet; // Total net price (before VAT)

    @PositiveOrZero(message = "Value must be a positive number or zero")
    private double totalVat; // Total VAT amount

    @PositiveOrZero(message = "Value must be a positive number or zero")
    private double totalGross; // Total gross price (net + VAT)

    private Integer supplierId;

    private String expectedDeliveryDate;

    private String trackingNumber;

    private List<Map<String, Object>> changes; // Changes for audit log

    @Valid
    private List<OrderItemDTO> orderItems;
}
