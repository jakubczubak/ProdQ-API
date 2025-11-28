package com.example.prodqapi.material;


import com.example.prodqapi.materialPriceHistory.MaterialPriceHistory;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MaterialDTO {

    private Integer id;

    @DecimalMin(value = "0", message = "Price per kg must be greater than or equal to 0")
    private BigDecimal pricePerKg;

    @PositiveOrZero(message = "Minimum quantity must be a positive number or zero")
    private float minQuantity;

    @PositiveOrZero(message = "Stock quantity must be a positive number")
    private Integer stockQuantity;  // For Plates: number of pieces

    @PositiveOrZero(message = "Total stock length must be a positive number")
    private Float totalStockLength;  // For Rods/Tubes: total length in mm

    @PositiveOrZero(message = "Value must be a positive number or zero")
    private float z;

    @PositiveOrZero(message = "Value must be a positive number or zero")
    private float y;

    @PositiveOrZero(message = "Value must be a positive number or zero")
    private float x;

    @PositiveOrZero(message = "Diameter must be a positive number or zero")
    private float diameter;

    @PositiveOrZero(message = "Length must be a positive number or zero")
    private float length;

    @PositiveOrZero(message = "Inner diameter must be a positive number or zero")
    private Float innerDiameter;  // For Tubes: inner diameter

    @NotBlank(message = "Field 'name' cannot be blank")
    @Size(min = 1, max = 100, message = "Field 'name' must have a length between 1 and 100 characters")
    private String name;

    @DecimalMin(value = "0", message = "Price must be greater than or equal to 0")
    private BigDecimal price;

    @NotBlank(message = "Field 'type' cannot be blank")
    @Size(min = 1, max = 50, message = "Field 'type' must have a length between 1 and 50 characters")
    private String type;

    @NotNull(message = "Quantity in transit cannot be null")
    @Min(value = 0, message = "Quantity in transit must be greater than or equal to 0")
    private Integer quantityInTransit;

    private String updatedOn;

    @Size(max = 100, message = "Field 'additionalInfo' must have a length less than or equal to 100 characters")
    private String additionalInfo;

    private Integer materialGroupID;

    @Valid // Ensure the elements in the list are validated
    private List<MaterialPriceHistory> materialPriceHistoryList;

}
