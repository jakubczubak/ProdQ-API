package com.example.infraboxapi.materialGroup;

import com.example.infraboxapi.material.MaterialDTO;
import com.example.infraboxapi.materialDescription.MaterialDescriptionDTO;
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
    private String name;
    private String type;
    private String imageURL;
    private MaterialDescriptionDTO materialDescription;
    private List<MaterialDTO> materials;

}
