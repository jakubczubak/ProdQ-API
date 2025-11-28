package com.example.prodqapi.supplier;


import com.example.prodqapi.notification.NotificationDescription;
import com.example.prodqapi.notification.NotificationService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@AllArgsConstructor
public class SupplierService {

    private final SupplierRepository supplierRepository;
    private final NotificationService notificationService;

    public void deleteSupplier(Integer id) {

        Supplier supplier = supplierRepository.findById(id).orElseThrow(() -> new RuntimeException("Supplier not found"));
        supplierRepository.delete(supplier);
        notificationService.sendNotification(NotificationDescription.SupplierDeleted, Map.of("name", supplier.getCompanyName()));

    }

    public List<Supplier> getAllSuppliers() {

        return supplierRepository.findAll();
    }

    public void createSupplier(SupplierDTO supplierDTO) {

        Supplier supplier = Supplier.builder()
                .name(supplierDTO.getName())
                .surname(supplierDTO.getSurname())
                .phoneNumber(supplierDTO.getPhoneNumber())
                .email(supplierDTO.getEmail())
                .companyName(supplierDTO.getCompanyName())
                .position(supplierDTO.getPosition())
                .companyLogo(supplierDTO.getCompanyLogo())
                .companyWebsite(supplierDTO.getCompanyWebsite())
                .companyTaxId(supplierDTO.getCompanyTaxId())
                .tagList(supplierDTO.getTagList())
                .build();

        supplierRepository.save(supplier);

        notificationService.sendNotification(NotificationDescription.SupplierAdded, Map.of("name", supplier.getCompanyName()));
    }

    public void updateSupplier(SupplierDTO supplierDTO) {

        Supplier supplier = supplierRepository.findById(supplierDTO.getId()).orElseThrow(() -> new RuntimeException("Supplier not found"));
        supplier.setId(supplierDTO.getId());
        supplier.setName(supplierDTO.getName());
        supplier.setSurname(supplierDTO.getSurname());
        supplier.setPhoneNumber(supplierDTO.getPhoneNumber());
        supplier.setEmail(supplierDTO.getEmail());
        supplier.setCompanyName(supplierDTO.getCompanyName());
        supplier.setPosition(supplierDTO.getPosition());
        supplier.setCompanyLogo(supplierDTO.getCompanyLogo());
        supplier.setCompanyWebsite(supplierDTO.getCompanyWebsite());
        supplier.setCompanyTaxId(supplierDTO.getCompanyTaxId());
        supplier.setTagList(supplierDTO.getTagList());

        supplierRepository.save(supplier);

        notificationService.sendNotification(NotificationDescription.SupplierUpdated, Map.of("name", supplier.getCompanyName()));
    }
}
