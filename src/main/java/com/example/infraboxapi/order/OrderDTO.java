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

    @Email(message = "Field 'supplierEmail' must be a valid email address")
    private String supplierEmail;

    private String supplierMessage;  // Umożliwienie długich wiadomości

    @PositiveOrZero(message = "Value must be a positive number or zero")
    private double totalPrice;

    private Integer supplierId;

    private String expectedDeliveryDate;

    @Valid
    private List<OrderItemDTO> orderItems;
}
