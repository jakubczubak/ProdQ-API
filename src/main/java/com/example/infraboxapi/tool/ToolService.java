package com.example.infraboxapi.tool;


import com.example.infraboxapi.notification.NotificationDescription;
import com.example.infraboxapi.notification.NotificationService;
import com.example.infraboxapi.toolGroup.ToolGroup;
import com.example.infraboxapi.toolGroup.ToolGroupRepository;
import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
@AllArgsConstructor
public class ToolService {

    private final ToolRepository toolRepository;
    private final ToolGroupRepository toolGroupRepository;
    private final NotificationService notificationService;

    @Transactional
    public void createTool(ToolDTO toolDTO) {
        ToolGroup toolGroup = toolGroupRepository.findById(toolDTO.getToolGroupID())
                .orElseThrow(() -> new RuntimeException("Tool group not found"));

        Tool newTool = Tool.builder()

                .dc(toolDTO.getDc())
                .cfl(toolDTO.getCfl())
                .oal(toolDTO.getOal())
                .name(toolDTO.getName())
                .type(toolDTO.getType())
                .quantity(toolDTO.getQuantity())
                .minQuantity(toolDTO.getMinQuantity())
                .price(toolDTO.getPrice())
                .toolID(toolDTO.getToolID())
                .link(toolDTO.getLink())
                .additionalInfo(toolDTO.getAdditionalInfo())
                .quantityInTransit(toolDTO.getQuantityInTransit())

                .build();


        LocalDateTime now = LocalDateTime.now();

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

        newTool.setUpdatedOn(now.format(formatter));

        toolGroup.getTools().add(newTool);

        toolGroupRepository.save(toolGroup);

        notificationService.createAndSendNotification("Tool " + toolDTO.getName() + " created", NotificationDescription.ToolAdded);
    }

    @Transactional
    public void deleteTool(Integer id) {

        String toolName = toolRepository.findById(id).orElseThrow(() -> new RuntimeException("Tool not found")).getName();
        toolRepository.deleteById(id);
        notificationService.createAndSendNotification("Tool " + toolName + " deleted", NotificationDescription.ToolDeleted);

    }

    @Transactional
    public void updateTool(ToolDTO toolDTO) {

        Tool tool = toolRepository.findById(toolDTO.getId()).orElseThrow(() -> new RuntimeException("Tool not found"));

        tool.setDc(toolDTO.getDc());
        tool.setCfl(toolDTO.getCfl());
        tool.setOal(toolDTO.getOal());
        tool.setName(toolDTO.getName());
        tool.setType(toolDTO.getType());
        tool.setQuantity(toolDTO.getQuantity());
        tool.setMinQuantity(toolDTO.getMinQuantity());
        tool.setPrice(toolDTO.getPrice());
        tool.setToolID(toolDTO.getToolID());
        tool.setLink(toolDTO.getLink());
        tool.setAdditionalInfo(toolDTO.getAdditionalInfo());
        tool.setQuantityInTransit(toolDTO.getQuantityInTransit());


        toolRepository.save(tool);

        notificationService.createAndSendNotification("Tool " + toolDTO.getName() + " has been successfully updated", NotificationDescription.ToolUpdated);
    }
}
