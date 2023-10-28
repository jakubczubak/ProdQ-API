package com.example.infraboxapi.materialDescription;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.PositiveOrZero;
import javax.validation.constraints.Size;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MaterialDescriptionDTO {

    private Integer id;

    @NotBlank(message = "Field 'name' cannot be blank")
    @Size(min = 2, max = 100, message = "Field 'name' must have a length between 2 and 100 characters")
    private String name;

    @NotNull(message = "Field 'density' cannot be null")
    @PositiveOrZero(message = "Field 'density' must be a positive number or zero")
    private float density;
}
