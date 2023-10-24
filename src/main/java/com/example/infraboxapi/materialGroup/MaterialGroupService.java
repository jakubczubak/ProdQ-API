package com.example.infraboxapi.materialGroup;

import com.example.infraboxapi.material.Material;
import com.example.infraboxapi.material.MaterialDTO;
import com.example.infraboxapi.materialDescription.MaterialDescription;
import com.example.infraboxapi.materialDescription.MaterialDescriptionDTO;
import com.example.infraboxapi.materialPriceHistory.MaterialPriceHistory;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;


@Service
@AllArgsConstructor
public class MaterialGroupService {

    private final MaterialGroupRepository materialGroupRepository;
    public void createMaterialGroup(MaterialGroupDTO materialGroupDTO){

        MaterialGroup materialGroup = MaterialGroup.builder()
                .name(materialGroupDTO.getName())
                .type(materialGroupDTO.getType())
                .imageURL(materialGroupDTO.getImageURL())
                .materialDescription(convertToMaterialDescription(materialGroupDTO.getMaterialDescription()))
                .materials(new ArrayList<>())
                .build();

        materialGroupRepository.save(materialGroup);

    }

    public void updateMaterialGroup(MaterialGroupDTO materialGroupDTO) {

        MaterialGroup materialGroup = materialGroupRepository.findById(materialGroupDTO.getId()).orElseThrow(() -> new RuntimeException("Material Group not found"));
        materialGroup.setId(materialGroupDTO.getId());
        materialGroup.setImageURL(materialGroupDTO.getImageURL());
        materialGroup.setType(materialGroupDTO.getType());
        materialGroup.setName(materialGroupDTO.getName());

        materialGroupRepository.save(materialGroup);
    }

    public void deleteMaterialGroup(Integer id) {

        MaterialGroup materialGroup = materialGroupRepository.findById(id).orElseThrow(() -> new RuntimeException("Material Group not found"));
        materialGroupRepository.delete(materialGroup);
    }


    public MaterialGroup getMaterialGroup(Integer id) {

        return materialGroupRepository.findById(id).orElseThrow(() -> new RuntimeException("Material Group not found"));
    }

    public Iterable<MaterialGroup> getMaterialGroups() {
        return materialGroupRepository.findAll();
    }




    public MaterialDescription convertToMaterialDescription(MaterialDescriptionDTO materialDescriptionDTO){
        return MaterialDescription.builder()
                .id(materialDescriptionDTO.getId())
                .name(materialDescriptionDTO.getName())
                .density(materialDescriptionDTO.getDensity())
                .build();
    }
}
