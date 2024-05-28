package com.example.infraboxapi.productionItem;

import com.example.infraboxapi.FilePDF.FilePDF;
import com.example.infraboxapi.FilePDF.FilePDFService;
import com.example.infraboxapi.materialType.MaterialType;
import com.example.infraboxapi.materialType.MaterialTypeRepository;
import com.example.infraboxapi.notification.NotificationDescription;
import com.example.infraboxapi.notification.NotificationService;
import com.example.infraboxapi.productionItemMaterial.ProductionItemMaterial;
import com.example.infraboxapi.project.Project;
import com.example.infraboxapi.project.ProjectRepository;
import com.example.infraboxapi.project.ProjectService;
import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
@AllArgsConstructor
public class ProductionItemService {

    private final ProductionItemRepository productionItemRepository;
    private final FilePDFService filePDFService;
    private final NotificationService notificationService;
    private final ProjectRepository projectRepository;
    private final ProjectService projectService;
    private final MaterialTypeRepository materialTypeRepository;

    @Transactional
    public void createProductionItem(ProductionItemDTO productionItemDTO) throws IOException {

        MaterialType materialType = materialTypeRepository.findById(productionItemDTO.getMaterialTypeID()).orElseThrow(() -> new RuntimeException("Material Type not found"));

        ProductionItemMaterial productionItemMaterial = ProductionItemMaterial.builder()
                .materialType(materialType)
                .pricePerKg(productionItemDTO.getPricePerKg())
                .type(productionItemDTO.getType())
                .x(productionItemDTO.getX())
                .y(productionItemDTO.getY())
                .z(productionItemDTO.getZ())
                .diameter(productionItemDTO.getDiameter())
                .length(productionItemDTO.getLength())
                .thickness(productionItemDTO.getThickness())
                .build();

        Project project = projectRepository.findById(productionItemDTO.getProjectID()).orElseThrow(() -> new RuntimeException("Project not found"));

        ProductionItem productionItem = ProductionItem.builder()
                .partName(productionItemDTO.getPartName())
                .quantity(productionItemDTO.getQuantity())
                .status(productionItemDTO.getStatus())
                .camTime(productionItemDTO.getCamTime())
                .materialValue(productionItemDTO.getMaterialValue())
                .toolValue(productionItemDTO.getToolValue())
                .partType(productionItemDTO.getPartType())
                .startUpTime(productionItemDTO.getStartUpTime())
                .finishingTime(productionItemDTO.getFinishingTime())
                .totalTime(productionItemDTO.getTotalTime())
                .factor(productionItemDTO.getFactor())
                .fixtureTime(productionItemDTO.getFixtureTime())
                .typeOfProcessing(productionItemDTO.getTypeOfProcessing())
                .productionItemMaterial(productionItemMaterial)
                .build();

        if (productionItemDTO.getFilePDF() != null) {
            FilePDF filePDF = filePDFService.createFile(productionItemDTO.getFilePDF());
            productionItem.setFilePDF(filePDF);

        }

        project.getProductionItems().add(productionItem);

        projectRepository.save(project);


        notificationService.createAndSendNotification("A new production item has been added: `" + productionItem.getPartName() + "`", NotificationDescription.ProductionItemAdded);
    }

    public Iterable<ProductionItem> getProductionItems() {
        return productionItemRepository.findAll();
    }

    public void deleteProductionItem(Integer id) {
        ProductionItem pr = productionItemRepository.findById(id).orElseThrow(() -> new RuntimeException("Production Item not found"));
        productionItemRepository.deleteById(id);
        notificationService.createAndSendNotification("A production item has been deleted: `" + pr.getPartName() + "`", NotificationDescription.ProductionItemDeleted);
    }

    public void updateProductionItem(ProductionItemDTO productionItemDTO) throws IOException {
        ProductionItem productionItem = productionItemRepository.findById(productionItemDTO.getId()).orElseThrow(() -> new RuntimeException("Production Item not found"));
        productionItem.setPartName(productionItemDTO.getPartName());
        productionItem.setQuantity(productionItemDTO.getQuantity());
        productionItem.setStatus(productionItemDTO.getStatus());
        productionItem.setCamTime(productionItemDTO.getCamTime());
        productionItem.setMaterialValue(productionItemDTO.getMaterialValue());
        productionItem.setToolValue(productionItemDTO.getToolValue());
        productionItem.setPartType(productionItemDTO.getPartType());
        productionItem.setStartUpTime(productionItemDTO.getStartUpTime());
        productionItem.setFactor(productionItemDTO.getFactor());
        productionItem.setFixtureTime(productionItemDTO.getFixtureTime());
        productionItem.setFinishingTime(productionItemDTO.getFinishingTime());
        productionItem.setTotalTime(productionItemDTO.getTotalTime());
        productionItem.setTypeOfProcessing(productionItemDTO.getTypeOfProcessing());

        MaterialType materialType = materialTypeRepository.findById(productionItemDTO.getMaterialTypeID()).orElseThrow(() -> new RuntimeException("Material Type not found"));

        ProductionItemMaterial productionItemMaterial = productionItem.getProductionItemMaterial();
        productionItemMaterial.setMaterialType(materialType);
        productionItemMaterial.setPricePerKg(productionItemDTO.getPricePerKg());
        productionItemMaterial.setType(productionItemDTO.getType());
        productionItemMaterial.setX(productionItemDTO.getX());
        productionItemMaterial.setY(productionItemDTO.getY());
        productionItemMaterial.setZ(productionItemDTO.getZ());
        productionItemMaterial.setDiameter(productionItemDTO.getDiameter());
        productionItemMaterial.setLength(productionItemDTO.getLength());
        productionItemMaterial.setThickness(productionItemDTO.getThickness());

        productionItem.setProductionItemMaterial(productionItemMaterial);

        if (productionItemDTO.getFilePDF() != null) {
            FilePDF filePDF = filePDFService.updateFile(productionItemDTO.getFilePDF(), productionItem.getFilePDF());
            productionItem.setFilePDF(filePDF);
        }else{
            productionItem.setFilePDF(null);

        }

        productionItemRepository.save(productionItem);
        notificationService.createAndSendNotification(
                "The production item '" + productionItem.getPartName() + "' has been updated successfully.",
                NotificationDescription.ProductionItemUpdated
        );
    }

}
