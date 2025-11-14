package com.example.infraboxapi.orderItem;


import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderItemDTO {

    private Integer id;
    @NotBlank(message = "Field 'name' cannot be blank")
    @Size(min = 1, max = 100, message = "Field 'name' must have a length between 1 and 100 characters")
    private String name;
    @PositiveOrZero(message = "Value must be a positive number or zero")
    private float quantity;
    @NotBlank(message = "Field 'item type' cannot be blank")
    @Size(min = 1, max = 100, message = "Field 'item type' must have a length between 1 and 100 characters")
    private String itemType; // "material" | "tool" | "accessorie"
    @PositiveOrZero(message = "Value must be a positive number or zero")
    private Integer itemID;

    @Builder.Default
    private Integer vatRate = 23; // VAT rate percentage (default 23%)

    @Builder.Default
    @PositiveOrZero(message = "Discount must be a positive number or zero")
    private Float discount = 0.0f; // Discount percentage (0-100)

    @PositiveOrZero(message = "Price override must be a positive number or zero")
    private Double priceOverride; // Optional price override (null = use default price)
}
