package com.example.infraboxapi.materialGroup;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.web.multipart.MultipartFile;

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

    private MultipartFile file;

    @NotNull(message = "Field 'materialTypeID' cannot be null")
    private Integer materialTypeID;


}
