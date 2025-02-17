package com.example.infraboxapi.accessorieItem;


import com.example.infraboxapi.accessorie.Accessorie;
import com.example.infraboxapi.accessorie.AccessorieReposotory;
import com.example.infraboxapi.notification.NotificationDescription;
import com.example.infraboxapi.notification.NotificationService;
import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
public class AccessorieItemService {

    private final AccessorieItemRepository accessorieItemRepository;
    private final AccessorieReposotory accessorieReposotory;
    private final NotificationService notificationService;


    @Transactional
    public void createAccessorieItem(AccessorieItemDTO accessorieItemDTO) {
        Accessorie accessorie = accessorieReposotory.findById(accessorieItemDTO.getAccessorieGroupID())
                .orElseThrow(() -> new RuntimeException("Accessorie not found"));

        AccessorieItem newAccessorieItem = AccessorieItem.builder()

                .diameter(accessorieItemDTO.getDiameter())
                .length(accessorieItemDTO.getLength())
                .name(accessorieItemDTO.getName())
                .type(accessorieItemDTO.getType())
                .quantity(accessorieItemDTO.getQuantity())
                .minQuantity(accessorieItemDTO.getMinQuantity())
                .price(accessorieItemDTO.getPrice())
                .link(accessorieItemDTO.getLink())
                .additionalInfo(accessorieItemDTO.getAdditionalInfo())
                .build();

        accessorie.getAccessorieItems().add(newAccessorieItem);

        accessorieReposotory.save(accessorie);

        notificationService.createAndSendNotification("Accessorie item '" + accessorieItemDTO.getName() + "' created", NotificationDescription.AccessoriesItemAdded);
}

    @Transactional
    public void deleteAccessorieItem(Integer id) {

        String accessorieItemName = accessorieItemRepository.findById(id).orElseThrow(() -> new RuntimeException("Accessorie item not found")).getName();
        accessorieItemRepository.deleteById(id);
        notificationService.createAndSendNotification("Accessorie item '" + accessorieItemName + "' deleted", NotificationDescription.AccessoriesItemDeleted);

    }

    @Transactional
    public void updateAccessorieItem(AccessorieItemDTO accessorieItemDTO) {
        AccessorieItem accessorieItem = accessorieItemRepository.findById(accessorieItemDTO.getId())
                .orElseThrow(() -> new RuntimeException("Accessorie item not found"));

        StringBuilder notificationMessage = new StringBuilder("The accessory item ")
                .append(accessorieItem.getName())
                .append(" has been updated. Changes:");

        checkAndNotifyQuantityChange(accessorieItem, accessorieItemDTO);

        // Check for price change
        if (accessorieItem.getPrice().compareTo(accessorieItemDTO.getPrice()) != 0) {
            notificationMessage.append("\nPrice: from ")
                    .append(accessorieItem.getPrice())
                    .append(" to ")
                    .append(accessorieItemDTO.getPrice());
        }

        // Update other fields and append changes to notification message
        if (accessorieItem.getMinQuantity() != accessorieItemDTO.getMinQuantity()) {
            notificationMessage.append("\nMin Quantity: from ")
                    .append(accessorieItem.getMinQuantity())
                    .append(" to ")
                    .append(accessorieItemDTO.getMinQuantity());
        }
        if (accessorieItem.getLength() != accessorieItemDTO.getLength()) {
            notificationMessage.append("\nLength: from ")
                    .append(accessorieItem.getLength())
                    .append(" to ")
                    .append(accessorieItemDTO.getLength());
        }
        if (accessorieItem.getDiameter() != accessorieItemDTO.getDiameter()) {
            notificationMessage.append("\nDiameter: from ")
                    .append(accessorieItem.getDiameter())
                    .append(" to ")
                    .append(accessorieItemDTO.getDiameter());
        }
        if (!accessorieItem.getAdditionalInfo().equals(accessorieItemDTO.getAdditionalInfo())) {
            notificationMessage.append("\nAdditional Info: from ")
                    .append(accessorieItem.getAdditionalInfo())
                    .append(" to ")
                    .append(accessorieItemDTO.getAdditionalInfo());
        }

        // Now, update the AccessorieItem entity with the new values
        accessorieItem.setDiameter(accessorieItemDTO.getDiameter());
        accessorieItem.setLength(accessorieItemDTO.getLength());
        accessorieItem.setName(accessorieItemDTO.getName());
        accessorieItem.setType(accessorieItemDTO.getType());
        accessorieItem.setQuantity(accessorieItemDTO.getQuantity());
        accessorieItem.setMinQuantity(accessorieItemDTO.getMinQuantity());
        accessorieItem.setPrice(accessorieItemDTO.getPrice());
        accessorieItem.setLink(accessorieItemDTO.getLink());
        accessorieItem.setAdditionalInfo(accessorieItemDTO.getAdditionalInfo());

        // Save updated AccessorieItem
        accessorieItemRepository.save(accessorieItem);

        // Send notification for the updated accessory item
        notificationService.createAndSendNotification(notificationMessage.toString(), NotificationDescription.AccessoriesItemUpdated);
    }

    public void checkAndNotifyQuantityChange(AccessorieItem accessorieItem, AccessorieItemDTO accessorieItemDTO) {
        float oldQuantity = accessorieItem.getQuantity();
        float newQuantity = accessorieItemDTO.getQuantity();

        // Check if quantities are integers
        boolean isOldQuantityInteger = (oldQuantity % 1 == 0);
        boolean isNewQuantityInteger = (newQuantity % 1 == 0);

        // Convert to string representation
        String oldQuantityStr = isOldQuantityInteger ? String.valueOf((int) oldQuantity) : String.valueOf(oldQuantity);
        String newQuantityStr = isNewQuantityInteger ? String.valueOf((int) newQuantity) : String.valueOf(newQuantity);

        // Check for quantity change
        if (oldQuantity != newQuantity) {
            String message;
            if (newQuantity > oldQuantity) {
                message = "Quantity of accessorie item '" + accessorieItem.getName() + "' increased from " + oldQuantityStr + " to " + newQuantityStr + ".";
            } else {
                message = "Quantity of accessorie item '" + accessorieItem.getName() + "' decreased from " + oldQuantityStr + " to " + newQuantityStr + ".";
            }

            // Send quantity change notification
            notificationService.createAndSendQuantityNotification(message, NotificationDescription.AccessoriesItemUpdated);
        }
    }



}

