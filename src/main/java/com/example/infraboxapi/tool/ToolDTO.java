package com.example.infraboxapi.tool;


import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolDTO {

    private Integer id;

    @NotBlank(message = "Field 'name' cannot be blank")
    @Size(min = 1, max = 100, message = "Field 'name' must have a length between 1 and 100 characters")
    private String name;

    @NotBlank(message = "Field 'type' cannot be blank")
    @Size(min = 1, max = 50, message = "Field 'type' must have a length between 1 and 50 characters")
    private String type;

    @PositiveOrZero(message = "Value must be a positive number or zero")
    private float dc;

    @PositiveOrZero(message = "Value must be a positive number or zero")
    private float cfl;

    @PositiveOrZero(message = "Value must be a positive number or zero")
    private float oal;

    @PositiveOrZero(message = "Value must be a positive number or zero")
    private Integer quantity;

    @PositiveOrZero(message = "Value must be a positive number or zero")
    private Integer minQuantity;

    @DecimalMin(value = "0", message = "Price must be greater than or equal to 0")
    private BigDecimal price;

    @NotNull(message = "Field 'imageURL' cannot be null")
    private String toolID;

    @NotNull(message = "Field 'imageURL' cannot be null")
    private String link;

    @NotNull(message = "Field 'imageURL' cannot be null")
    private String additionalInfo;
    @PositiveOrZero(message = "Value must be a positive number or zero")
    private float quantityInTransit;

    private Integer toolGroupID;

    private String updatedOn;

}
