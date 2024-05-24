package com.example.infraboxapi.productionItemMaterial;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class ProductionItemMaterialDTO {

    private Integer id;
    private Integer materialTypeID;
    @DecimalMin(value = "0", message = "Price per kg must be greater than or equal to 0")
    private BigDecimal pricePerKg;
    private String type;
    @PositiveOrZero(message = "Value must be a positive number or zero")
    private float z;
    @PositiveOrZero(message = "Value must be a positive number or zero")
    private float y;
    @PositiveOrZero(message = "Value must be a positive number or zero")
    private float x;
    @PositiveOrZero(message = "Value must be a positive number or zero")
    private float diameter;
    @PositiveOrZero(message = "Value must be a positive number or zero")
    private float length;
    @PositiveOrZero(message = "Value must be a positive number or zero")
    private float thickness;
}
