package com.example.infraboxapi.recycling;

import com.example.infraboxapi.notification.NotificationDescription;
import com.example.infraboxapi.notification.NotificationService;
import com.example.infraboxapi.recyclingItem.RecyclingItem;
import com.example.infraboxapi.recyclingItem.RecyclingItemDTO;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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

        for (RecyclingItemDTO recyclingItemDTO : recyclingDTO.getRecyclingItems()) {
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
                .totalPrice(recyclingDTO.getTotalPrice())
                .build();

        recyclingRepository.save(recycling);
        notificationService.createAndSendNotification("Recycling '" + recycling.getCompany() + "' has been added.", NotificationDescription.RecyclingAdded);

    }


    private RecyclingItem createRecyclingItemFromDTO(RecyclingItemDTO recyclingItemDTO) {

        return RecyclingItem.builder()
                .name(recyclingItemDTO.getName())
                .quantity(recyclingItemDTO.getQuantity())
                .pricePerKg(recyclingItemDTO.getPricePerKg())
                .totalPrice(recyclingItemDTO.getTotalPrice())
                .build();
    }

    public void deleteRecycling(Integer id) {

        recyclingRepository.deleteById(id);
        notificationService.createAndSendNotification("Recycling with id '" + id + "' has been deleted.", NotificationDescription.RecyclingDeleted);
    }

    public void updateRecycling(RecyclingDTO recyclingDTO) {

        Optional<Recycling> recyclingOptional = recyclingRepository.findById(recyclingDTO.getId());

        if (recyclingOptional.isPresent()) {
            Recycling recycling = recyclingOptional.get();

            recycling.setCarID(recyclingDTO.getCarID());
            recycling.setCompany(recyclingDTO.getCompany());
            recycling.setDate(recyclingDTO.getDate());
            recycling.setTaxID(recyclingDTO.getTaxID());
            recycling.setWasteType(recyclingDTO.getWasteType());
            recycling.setTime(recyclingDTO.getTime());
            recycling.setWasteCode(recyclingDTO.getWasteCode());
            recycling.setTotalPrice(recyclingDTO.getTotalPrice());

            List<RecyclingItem> recyclingItems = new ArrayList<>();

            for (RecyclingItemDTO recyclingItemDTO : recyclingDTO.getRecyclingItems()) {
                RecyclingItem recyclingItem = createRecyclingItemFromDTO(recyclingItemDTO);
                recyclingItems.add(recyclingItem);
            }

            recycling.setRecyclingItems(recyclingItems);

            recyclingRepository.save(recycling);
            notificationService.createAndSendNotification("Recycling '" + recycling.getCompany() + "' has been updated.", NotificationDescription.RecyclingUpdated);
        }
    }

    public Double getTotalRecyclingQuantity() {
        List<Recycling> recyclingList = recyclingRepository.findAll();
        double totalQuantity = 0;

        for (Recycling recycling : recyclingList) {
            for (RecyclingItem recyclingItem : recycling.getRecyclingItems()) {
                totalQuantity += Math.round(recyclingItem.getQuantity() * 100.0) / 100.0;
            }
        }

        return totalQuantity;
    }


    public Double getTotalRefund() {
        List<Recycling> recyclingList = recyclingRepository.findAll();
        double totalRefund = 0;

        for (Recycling recycling : recyclingList) {
            totalRefund += Math.round(recycling.getTotalPrice().doubleValue() * 100.0) / 100.0;
        }

        return totalRefund;
    }

}
