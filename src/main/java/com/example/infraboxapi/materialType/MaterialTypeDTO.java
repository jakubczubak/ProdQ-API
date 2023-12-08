package com.example.infraboxapi.materialType;

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
public class MaterialTypeDTO {

    private Integer id;

    @NotBlank(message = "Field 'name' cannot be blank")
    @Size(min = 2, max = 100, message = "Field 'name' must have a length between 2 and 100 characters")
    private String name;

    @NotNull(message = "Field 'density' cannot be null")
    @PositiveOrZero(message = "Field 'density' must be a positive number or zero")
    private float density;
}
