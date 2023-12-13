package com.example.infraboxapi.materialGroup;

import com.example.infraboxapi.material.MaterialDTO;
import com.example.infraboxapi.materialType.MaterialTypeDTO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


import java.util.List;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder

public class MaterialGroupDTO {

    private Integer id;
    @NotBlank(message = "Field 'name' cannot be blank")
    @Size(min = 2, max = 100, message = "Field 'name' must have a length between 2 and 100 characters")
    private String name;

    @NotBlank(message = "Field 'type' cannot be blank")
    @Size(min = 2, max = 50, message = "Field 'type' must have a length between 2 and 50 characters")
    private String type;

    @Size(max = 255, message = "Field 'imageURL' cannot exceed 255 characters")
    @NotNull(message = "Field 'imageURL' cannot be null")
    private String imageURL;

    @Valid // Ensure the nested object is validated as well
    private MaterialTypeDTO materialType;

    @Valid // Ensure the elements in the list are validated
    private List<MaterialDTO> materials;

}
