package com.example.infraboxapi.orderItem;


import com.example.infraboxapi.material.Material;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
    private Integer quantity;
    @NotBlank(message = "Field 'item type' cannot be blank")
    @Size(min = 1, max = 100, message = "Field 'item type' must have a length between 1 and 100 characters")
    private String itemType;
    @PositiveOrZero(message = "Value must be a positive number or zero")
    private Integer itemID;
}
