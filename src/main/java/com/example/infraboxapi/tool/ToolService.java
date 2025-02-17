package com.example.infraboxapi.tool;

import com.example.infraboxapi.notification.NotificationDescription;
import com.example.infraboxapi.notification.NotificationService;
import com.example.infraboxapi.toolGroup.ToolGroup;
import com.example.infraboxapi.toolGroup.ToolGroupRepository;
import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

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

        toolGroup.getTools().add(newTool);
        toolGroupRepository.save(toolGroup);

        notificationService.createAndSendNotification("A new tool '" + newTool.getName() + "' has been added successfully.", NotificationDescription.ToolAdded);
    }

    @Transactional
    public void deleteTool(Integer id) {
        String toolName = toolRepository.findById(id).orElseThrow(() -> new RuntimeException("Tool not found")).getName();
        toolRepository.deleteById(id);
        notificationService.createAndSendNotification("The tool '" + toolName + "' has been successfully deleted.", NotificationDescription.ToolDeleted);
    }

    @Transactional
    public void updateTool(ToolDTO toolDTO) {
        Tool tool = toolRepository.findById(toolDTO.getId())
                .orElseThrow(() -> new RuntimeException("Tool not found"));

        StringBuilder notificationMessage = new StringBuilder("The tool ")
                .append(tool.getName())
                .append(" has been updated. Changes:");

        // Sprawdzenie zmiany ilości
        if (tool.getQuantity() != toolDTO.getQuantity()) {
            checkAndNotifyQuantityChange(tool, toolDTO);
        }

        // Pozostałe zmiany
        if (tool.getMinQuantity() != toolDTO.getMinQuantity()) {
            notificationMessage.append("\nMin Quantity: from ")
                    .append(tool.getMinQuantity())
                    .append(" to ")
                    .append(toolDTO.getMinQuantity());
        }
        if (tool.getDc() != toolDTO.getDc()) {
            notificationMessage.append("\nDC: from ")
                    .append(tool.getDc())
                    .append(" to ")
                    .append(toolDTO.getDc());
        }
        if (tool.getCfl() != toolDTO.getCfl()) {
            notificationMessage.append("\nCFL: from ")
                    .append(tool.getCfl())
                    .append(" to ")
                    .append(toolDTO.getCfl());
        }
        if (tool.getOal() != toolDTO.getOal()) {
            notificationMessage.append("\nOAL: from ")
                    .append(tool.getOal())
                    .append(" to ")
                    .append(toolDTO.getOal());
        }
        if (!tool.getAdditionalInfo().equals(toolDTO.getAdditionalInfo())) {
            notificationMessage.append("\nAdditional info: from ")
                    .append(tool.getAdditionalInfo())
                    .append(" to ")
                    .append(toolDTO.getAdditionalInfo());
        }

        // Aktualizacja narzędzia
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

        // Wysyłanie powiadomienia o zaktualizowanym narzędziu
        notificationService.createAndSendNotification(notificationMessage.toString(), NotificationDescription.ToolUpdated);
    }

    private void checkAndNotifyQuantityChange(Tool tool, ToolDTO toolDTO) {
        float oldQuantity = tool.getQuantity();
        float newQuantity = toolDTO.getQuantity();

        // Sprawdzenie, czy liczby są całkowite
        boolean isOldQuantityInteger = (oldQuantity % 1 == 0);
        boolean isNewQuantityInteger = (newQuantity % 1 == 0);

        // Konwersja do ciągu znaków
        String oldQuantityStr = isOldQuantityInteger ? String.valueOf((int) oldQuantity) : String.valueOf(oldQuantity);
        String newQuantityStr = isNewQuantityInteger ? String.valueOf((int) newQuantity) : String.valueOf(newQuantity);

        // Sprawdzenie zmiany ilości
        if (oldQuantity != newQuantity) {
            String message;
            if (newQuantity > oldQuantity) {
                message = "Tool '" + tool.getName() + "' quantity increased from " + oldQuantityStr + " to " + newQuantityStr + ".";
            } else {
                message = "Tool '" + tool.getName() + "' quantity decreased from " + oldQuantityStr + " to " + newQuantityStr + ".";
            }

            // Wysyłanie powiadomienia
            notificationService.createAndSendQuantityNotification(
                    message,
                    NotificationDescription.ToolQuantityUpdated);
        }
    }
}