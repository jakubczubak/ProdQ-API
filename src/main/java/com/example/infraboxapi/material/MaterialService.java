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
    public void createMaterial(MaterialDTO materialDTO) {

        MaterialGroup materialGroup = materialGroupRepository.findById(materialDTO.getMaterialGroupID())
                .orElseThrow(() -> new RuntimeException("Material group not found"));


        Material newMaterial = new Material();
        newMaterial.setPricePerKg(materialDTO.getPricePerKg());
        newMaterial.setPrice(materialDTO.getPrice());
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
}
