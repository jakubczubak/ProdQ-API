package com.example.infraboxapi.productionItem;

import com.example.infraboxapi.FileImage.FileImage;
import com.example.infraboxapi.FilePDF.FilePDF;
import com.example.infraboxapi.FilePDF.FilePDFService;
import com.example.infraboxapi.materialGroup.MaterialGroup;
import com.example.infraboxapi.materialGroup.MaterialGroupDTO;
import com.example.infraboxapi.materialType.MaterialType;
import com.example.infraboxapi.notification.NotificationDescription;
import com.example.infraboxapi.notification.NotificationService;
import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;

@Service
@AllArgsConstructor
public class ProductionItemService {

    private final ProductionItemRepository productionItemRepository;
    private final FilePDFService filePDFService;
    private final NotificationService notificationService;

    @Transactional
    public void createProductionItem(ProductionItemDTO productionItemDTO) throws IOException {




        ProductionItem productionItem = ProductionItem.builder()
                .partName(productionItemDTO.getPartName())
                .quantity(productionItemDTO.getQuantity())
                .status(productionItemDTO.getStatus())
                .camTime(productionItemDTO.getCamTime())
                .materialValue(productionItemDTO.getMaterialValue())
                .partType(productionItemDTO.getPartType())
                .build();

        if(productionItemDTO.getFilePDF() != null) {

            FilePDF filePDF = filePDFService.createFile(productionItemDTO.getFilePDF());
            productionItem.setFilePDF(filePDF);
        }

        productionItemRepository.save(productionItem);

        notificationService.createAndSendNotification("A new production item has been added: " + productionItem.getPartName(), NotificationDescription.ProductionItemAdded);

    }
}
