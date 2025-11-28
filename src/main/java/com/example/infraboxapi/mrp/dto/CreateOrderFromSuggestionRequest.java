package com.example.infraboxapi.mrp.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateOrderFromSuggestionRequest {

    @NotNull(message = "Supplier ID is required")
    private Integer supplierId;

    private String orderName;

    private String notes;
}
