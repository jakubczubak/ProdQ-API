package com.example.infraboxapi.material;

import com.example.infraboxapi.materialGroup.MaterialGroup;
import com.example.infraboxapi.materialGroup.MaterialGroupRepository;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;

@Service
@AllArgsConstructor
public class MaterialService {

    private final MaterialRepository materialRepository;
    private final MaterialGroupRepository materialGroupRepository;
    public void createMaterial(MaterialDTO materialDTO) {

        MaterialGroup materialGroup = materialGroupRepository.findById(materialDTO.getMaterialGroupID())
                .orElseThrow(() -> new RuntimeException("Material group not found"));

        // Tworzenie nowego obiektu Material
        Material newMaterial = new Material();
        newMaterial.setPricePerKg(materialDTO.getPricePerKg());
        newMaterial.setMinQuantity(materialDTO.getMinQuantity());
        newMaterial.setQuantity(materialDTO.getQuantity());
        newMaterial.setZ(materialDTO.getZ());
        newMaterial.setY(materialDTO.getY());
        newMaterial.setX(materialDTO.getX());
        newMaterial.setDiameter(materialDTO.getDiameter());
        newMaterial.setLength(materialDTO.getLength());
        newMaterial.setName(materialDTO.getName());
        newMaterial.setType(materialDTO.getType());
        newMaterial.setQuantityInTransit(materialDTO.getQuantityInTransit());

        // Przypisanie materiału do grupy

        // Zapisanie materiału w bazie danych
        materialGroup.getMaterials().add(newMaterial);

        materialGroupRepository.save(materialGroup);
    }
}
