package com.example.prodqapi.accessorieItem;

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
public class AccessorieItemDTO {

    private Integer id;

    @NotBlank(message = "Field 'name' cannot be blank")
    @Size(min = 1, max = 100, message = "Field 'name' must have a length between 1 and 100 characters")
    private String name;

    @NotBlank(message = "Field 'type' cannot be blank")
    @Size(min = 1, max = 50, message = "Field 'type' must have a length between 1 and 50 characters")
    private String type;

    @PositiveOrZero(message = "Value must be a positive number or zero")
    private float diameter;

    @PositiveOrZero(message = "Value must be a positive number or zero")
    private float length;

    @PositiveOrZero(message = "Value must be a positive number or zero")
    private Integer quantity;

    @PositiveOrZero(message = "Value must be a positive number or zero")
    private Integer minQuantity;

    @DecimalMin(value = "0", message = "Price must be greater than or equal to 0")
    private BigDecimal price;


    @NotNull(message = "Field 'link' cannot be null")
    private String link;

    @NotNull(message = "Field 'additional info' cannot be null")
    private String additionalInfo;

    private Integer accessorieGroupID;


}
