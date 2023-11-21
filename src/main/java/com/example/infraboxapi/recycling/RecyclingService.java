package com.example.infraboxapi.recycling;

import com.example.infraboxapi.notification.NotificationDescription;
import com.example.infraboxapi.notification.NotificationService;
import com.example.infraboxapi.recyclingItem.RecyclingItem;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@AllArgsConstructor
public class RecyclingService {

    private final RecyclingRepository recyclingRepository;
    private final NotificationService notificationService;
    public List<Recycling> getAllRecycling() {
        return recyclingRepository.findAll();
    }

    public void addRecycling(RecyclingDTO recyclingDTO) {

        List<RecyclingItem> recyclingItems = new ArrayList<>();

        for (RecyclingItem recyclingItemDTO : recyclingDTO.getRecyclingItems()){
            RecyclingItem recyclingItem = createRecyclingItemFromDTO(recyclingItemDTO);
            recyclingItems.add(recyclingItem);
        }

        Recycling recycling = Recycling.builder()
                .recyclingItems(recyclingItems)
                .carID(recyclingDTO.getCarID())
                .company(recyclingDTO.getCompany())
                .date(recyclingDTO.getDate())
                .taxID(recyclingDTO.getTaxID())
                .wasteType(recyclingDTO.getWasteType())
                .time(recyclingDTO.getTime())
                .wasteCode(recyclingDTO.getWasteCode())
                .build();

        recyclingRepository.save(recycling);
        notificationService.createAndSendNotification("Recycling '" + recycling.getCompany() + "' has been added.", NotificationDescription.RecyclingAdded);

    }


    private RecyclingItem createRecyclingItemFromDTO(RecyclingItem recyclingItemDTO) {

        return RecyclingItem.builder()
                .name(recyclingItemDTO.getName())
                .quantity(recyclingItemDTO.getQuantity())
                .pricePerKg(recyclingItemDTO.getPricePerKg())
                .totalPrice(recyclingItemDTO.getTotalPrice())
                .build();
    }

    public void deleteRecycling(Integer id) {

        recyclingRepository.deleteById(id);
    }

    public void updateRecycling(RecyclingDTO recyclingDTO) {
    }
}
