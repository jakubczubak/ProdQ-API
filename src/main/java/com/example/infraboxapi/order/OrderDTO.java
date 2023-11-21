package com.example.infraboxapi.order;


import com.example.infraboxapi.orderItem.OrderItem;
import com.example.infraboxapi.orderItem.OrderItemDTO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
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
    @Size(min = 1, max = 100, message = "Field 'name' must have a length between 1 and 100 characters")
    private String name;
    @NotBlank(message = "Field 'date' cannot be blank")
    @Size(min = 1, max = 100, message = "Field 'date' must have a length between 1 and 100 characters")
    private String date;
    @NotBlank(message = "Field 'status' cannot be blank")
    @Size(min = 1, max = 100, message = "Field 'status' must have a length between 1 and 100 characters")
    private String status;
    @Email(message = "Field 'supplierEmail' must be a valid email address")
    private String supplierEmail;
    @NotBlank(message = "Field 'supplier message' cannot be blank")
    private String supplierMessage;
    @PositiveOrZero(message = "Value must be a positive number or zero")
    private double totalPrice;
    @Valid
    private List<OrderItemDTO> orderItems;

}
