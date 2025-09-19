package com.example.infraboxapi.tool;

import com.example.infraboxapi.notification.NotificationDescription;
import com.example.infraboxapi.notification.NotificationService;
import com.example.infraboxapi.orderItem.OrderItem; // Dodany import
import com.example.infraboxapi.orderItem.OrderItemRepository; // Dodany import
import com.example.infraboxapi.toolGroup.ToolGroup;
import com.example.infraboxapi.toolGroup.ToolGroupRepository;
import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List; // Dodany import
import java.util.Objects;

@Service
@AllArgsConstructor
public class ToolService {

    private final ToolRepository toolRepository;
    private final ToolGroupRepository toolGroupRepository;
    private final NotificationService notificationService;
    private final OrderItemRepository orderItemRepository; // Dodane pole

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
        // Sprawdź, czy narzędzie istnieje, zanim zaczniesz działać
        Tool toolToDelete = toolRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Tool not found with id: " + id));

        // 1. Znajdź wszystkie pozycje zamówień (OrderItem) powiązane z tym narzędziem
        List<OrderItem> relatedOrderItems = orderItemRepository.findByToolId(id);

        // 2. W każdej powiązanej pozycji zamówienia ustaw pole 'tool' na null, aby zerwać powiązanie
        for (OrderItem item : relatedOrderItems) {
            item.setTool(null);
        }
        orderItemRepository.saveAll(relatedOrderItems); // Zapisz zmiany

        // 3. Teraz, gdy powiązania są usunięte, możesz bezpiecznie usunąć narzędzie
        String toolName = toolToDelete.getName();
        toolRepository.deleteById(id);

        // 4. Wyślij powiadomienie
        notificationService.createAndSendNotification("The tool '" + toolName + "' has been successfully deleted.", NotificationDescription.ToolDeleted);
    }

    @Transactional
    public void updateTool(ToolDTO toolDTO) {
        Tool tool = toolRepository.findById(toolDTO.getId())
                .orElseThrow(() -> new RuntimeException("Tool not found"));

        StringBuilder notificationMessage = new StringBuilder("The tool ")
                .append(tool.getName())
                .append(" has been updated. Changes:");

        if (tool.getQuantity() != toolDTO.getQuantity()) {
            checkAndNotifyQuantityChange(tool, toolDTO);
        }

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
        if (!Objects.equals(tool.getAdditionalInfo(), toolDTO.getAdditionalInfo())) {
            notificationMessage.append("\nAdditional info: from ")
                    .append(tool.getAdditionalInfo())
                    .append(" to ")
                    .append(toolDTO.getAdditionalInfo());
        }

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

        notificationService.createAndSendNotification(notificationMessage.toString(), NotificationDescription.ToolUpdated);
    }

    private void checkAndNotifyQuantityChange(Tool tool, ToolDTO toolDTO) {
        float oldQuantity = tool.getQuantity();
        float newQuantity = toolDTO.getQuantity();

        boolean isOldQuantityInteger = (oldQuantity % 1 == 0);
        boolean isNewQuantityInteger = (newQuantity % 1 == 0);

        String oldQuantityStr = isOldQuantityInteger ? String.valueOf((int) oldQuantity) : String.valueOf(oldQuantity);
        String newQuantityStr = isNewQuantityInteger ? String.valueOf((int) newQuantity) : String.valueOf(newQuantity);

        if (oldQuantity != newQuantity) {
            String message;
            if (newQuantity > oldQuantity) {
                message = "Tool '" + tool.getName() + "' quantity increased from " + oldQuantityStr + " to " + newQuantityStr + ".";
            } else {
                message = "Tool '" + tool.getName() + "' quantity decreased from " + oldQuantityStr + " to " + newQuantityStr + ".";
            }

            notificationService.createAndSendQuantityNotification(
                    message,
                    NotificationDescription.ToolQuantityUpdated);
        }
    }
}