package com.example.infraboxapi.supplier;


import com.example.infraboxapi.notification.NotificationService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import com.example.infraboxapi.notification.NotificationDescription;

import java.util.List;

@Service
@AllArgsConstructor
public class SupplierService {

    private final SupplierRepository supplierRepository;
    private final NotificationService notificationService;
    public void deleteSupplier(Integer id) {

        Supplier supplier = supplierRepository.findById(id).orElseThrow(() -> new RuntimeException("Supplier not found"));
        supplierRepository.delete(supplier);
        notificationService.createAndSendNotification("The supplier '" + supplier.getName() + "' has been successfully deleted.", NotificationDescription.SupplierDeleted);

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

            notificationService.createAndSendNotification("A new supplier has been added: " + supplier.getName(), NotificationDescription.SupplierAdded);
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

        notificationService.createAndSendNotification(
                "The supplier '" + supplier.getName() + "' has been updated successfully.",
                NotificationDescription.SupplierUpdated
        );
    }
}
