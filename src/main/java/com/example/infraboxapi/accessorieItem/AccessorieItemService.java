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

        notificationService.createAndSendNotification("Accessorie item '" + accessorieItemDTO.getName() + "' created", NotificationDescription.AccessorieItemAdded);
}

    @Transactional
    public void deleteAccessorieItem(Integer id) {

        String accessorieItemName = accessorieItemRepository.findById(id).orElseThrow(() -> new RuntimeException("Accessorie item not found")).getName();
        accessorieItemRepository.deleteById(id);
        notificationService.createAndSendNotification("Accessorie item '" + accessorieItemName + "' deleted", NotificationDescription.AccessorieItemDeleted);

    }

    @Transactional
    public void updateAccessorieItem(AccessorieItemDTO accessorieItemDTO) {
        AccessorieItem accessorieItem = accessorieItemRepository.findById(accessorieItemDTO.getId())
                .orElseThrow(() -> new RuntimeException("Accessorie item not found"));

        checkAndNotifyQuantityChange(accessorieItem, accessorieItemDTO);

        accessorieItem.setDiameter(accessorieItemDTO.getDiameter());
        accessorieItem.setLength(accessorieItemDTO.getLength());
        accessorieItem.setName(accessorieItemDTO.getName());
        accessorieItem.setType(accessorieItemDTO.getType());
        accessorieItem.setQuantity(accessorieItemDTO.getQuantity());
        accessorieItem.setMinQuantity(accessorieItemDTO.getMinQuantity());
        accessorieItem.setPrice(accessorieItemDTO.getPrice());
        accessorieItem.setLink(accessorieItemDTO.getLink());
        accessorieItem.setAdditionalInfo(accessorieItemDTO.getAdditionalInfo());

        accessorieItemRepository.save(accessorieItem);

        notificationService.createAndSendNotification("Accessorie item '" + accessorieItemDTO.getName() + "' updated", NotificationDescription.AccessorieItemUpdated);
    }

    public void checkAndNotifyQuantityChange(AccessorieItem accessorieItem, AccessorieItemDTO accessorieItemDTO) {
        if(accessorieItem.getQuantity() < accessorieItemDTO.getQuantity()) {
            notificationService.createAndSendNotification("Quantity of accessorie item '" + accessorieItem.getName() + "' increased from " + accessorieItem.getQuantity()  + " to " + accessorieItemDTO.getQuantity(), NotificationDescription.AccessorieItemUpdated);
        } else if(accessorieItem.getQuantity() > accessorieItemDTO.getQuantity()) {
            notificationService.createAndSendNotification("Quantity of accessorie item '" + accessorieItem.getName() + "' decreased from " + accessorieItem.getQuantity()  + " to " + accessorieItemDTO.getQuantity(), NotificationDescription.AccessorieItemUpdated);
        }
    }

}

