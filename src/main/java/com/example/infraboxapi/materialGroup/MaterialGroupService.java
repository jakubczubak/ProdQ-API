package com.example.infraboxapi.materialGroup;

import com.example.infraboxapi.material.Material;
import com.example.infraboxapi.materialDescription.MaterialDescription;
import com.example.infraboxapi.materialDescription.MaterialDescriptionDTO;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;


@Service
@AllArgsConstructor
public class MaterialGroupService {

    private final MaterialGroupRepository materialGroupRepository;
    public void createMaterialGroup(MaterialGroupDTO materialGroupDTO){

        System.out.println(materialGroupDTO);
        MaterialDescription materialDescription = MaterialDescription.builder()
                .name(materialGroupDTO.getMaterialDescription().getName())
                .density(materialGroupDTO.getMaterialDescription().getDensity())
                .build();


        MaterialGroup materialGroup = MaterialGroup.builder()
                .name(materialGroupDTO.getName())
                .type(materialGroupDTO.getType())
                .imageURL(materialGroupDTO.getImageURL())
                .materialDescription(materialDescription)
                .materials(null)
                .build();


        System.out.println(materialGroup);

        System.out.println(materialGroupRepository.save(materialGroup));
    }

    public void updateMaterialGroup(MaterialGroupDTO materialGroupDTO) {

//        List<Material> materials = materialGroupDTO.getMaterials().stream().map(materialDTO -> Material.builder()
//                .name(materialDTO.getName())
//                .price(materialDTO.getPrice())
//                .build()).toList();
//
//        MaterialDescription materialDescription = MaterialDescription.builder()
//                .name(materialGroupDTO.getMaterialDescriptionDTO().getName())
//                .density(materialGroupDTO.getMaterialDescriptionDTO().getDensity())
//                .build();
//
//        MaterialGroup materialGroup = materialGroupRepository.findById(materialGroupDTO.getId()).orElseThrow(() -> new RuntimeException("Material Group not found"));
//        materialGroup.setMaterials(materials);
//        materialGroup.setMaterialDescription(materialDescription);
//        materialGroup.setImageURL(materialGroupDTO.getImageURL());
//        materialGroup.setType(materialGroupDTO.getType());
//        materialGroup.setName(materialGroupDTO.getName());
//        materialGroupRepository.save(materialGroup);

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
}
