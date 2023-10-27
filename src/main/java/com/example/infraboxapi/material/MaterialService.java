package com.example.infraboxapi.material;

import com.example.infraboxapi.materialGroup.MaterialGroup;
import com.example.infraboxapi.materialGroup.MaterialGroupRepository;
import com.example.infraboxapi.materialPriceHistory.MaterialPriceHistory;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;

@Service
@AllArgsConstructor
public class MaterialService {

    private final MaterialGroupRepository materialGroupRepository;
    private final MaterialRepository materialRepository;
    public void createMaterial(MaterialDTO materialDTO) {

        MaterialGroup materialGroup = materialGroupRepository.findById(materialDTO.getMaterialGroupID())
                .orElseThrow(() -> new RuntimeException("Material group not found"));


        Material newMaterial = Material.builder()

                .diameter(materialDTO.getDiameter())
                .length(materialDTO.getLength())
                .thickness(materialDTO.getThickness())
                .name(materialDTO.getName())
                .price(materialDTO.getPrice())
                .pricePerKg(materialDTO.getPricePerKg())
                .minQuantity(materialDTO.getMinQuantity())
                .quantity(materialDTO.getQuantity())
                .z(materialDTO.getZ())
                .y(materialDTO.getY())
                .x(materialDTO.getX())
                .type(materialDTO.getType())
                .quantityInTransit(materialDTO.getQuantityInTransit())

                .build();

        LocalDateTime currentDateTime = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        MaterialPriceHistory materialPriceHistory = MaterialPriceHistory.builder()
                        .price(materialDTO.getPrice())
                        .date(currentDateTime.format(formatter)).

                build();

        newMaterial.setMaterialPriceHistoryList(new ArrayList<>());
        newMaterial.getMaterialPriceHistoryList().add(materialPriceHistory);

        materialGroup.getMaterials().add(newMaterial);

        materialGroupRepository.save(materialGroup);
    }

    public void deleteMaterial(Integer id) {

        materialRepository.deleteById(id);
    }

    public void updateMaterial(MaterialDTO materialDTO) {

        Material material = materialRepository.findById(materialDTO.getId())
                .orElseThrow(() -> new RuntimeException("Material not found"));


        //Check if pricePerKg is changed, if yes, add new price to history, if not, do nothing, just update the material

        if(material.getPricePerKg().compareTo(materialDTO.getPricePerKg()) != 0){
            LocalDateTime currentDateTime = LocalDateTime.now();
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

            MaterialPriceHistory materialPriceHistory = MaterialPriceHistory.builder()
                    .price(materialDTO.getPrice())
                    .date(currentDateTime.format(formatter)).

                            build();

            material.getMaterialPriceHistoryList().add(materialPriceHistory);
        }

        material.setPricePerKg(materialDTO.getPricePerKg());
        material.setPrice(materialDTO.getPrice());
        material.setMinQuantity(materialDTO.getMinQuantity());
        material.setQuantity(materialDTO.getQuantity());
        material.setZ(materialDTO.getZ());
        material.setY(materialDTO.getY());
        material.setX(materialDTO.getX());
        material.setDiameter(materialDTO.getDiameter());
        material.setLength(materialDTO.getLength());
        material.setThickness(materialDTO.getThickness());
        material.setName(materialDTO.getName());
        material.setType(materialDTO.getType());
        material.setQuantityInTransit(materialDTO.getQuantityInTransit());

        materialRepository.save(material);
    }
}
