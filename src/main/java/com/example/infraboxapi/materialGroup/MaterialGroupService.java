package com.example.infraboxapi.materialGroup;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;


@Service
@AllArgsConstructor
public class MaterialGroupService {

    private final MaterialGroupRepository materialGroupRepository;
    public void createMaterialGroup(MaterialGroupDTO materialGroupDTO){

        MaterialGroup materialGroup = MaterialGroup.builder()
                .materials(materialGroupDTO.getMaterials())
                .materialDescription(materialGroupDTO.getMaterialDescription())
                .imageURL(materialGroupDTO.getImageURL())
                .materialDescription(materialGroupDTO.getMaterialDescription())
                .type(materialGroupDTO.getType())
                .name(materialGroupDTO.getName())
                .build();

            materialGroupRepository.save(materialGroup);

    }
}
