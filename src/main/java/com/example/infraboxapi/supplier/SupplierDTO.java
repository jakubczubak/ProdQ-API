package com.example.infraboxapi.supplier;


import jakarta.persistence.ElementCollection;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SupplierDTO {
    private Integer id;

    private String name;
    private String surname;
    private String phoneNumber;
    private String email;
    private String companyName;
    private String position;
    private String companyLogo;
    private String companyWebsite;
    private String companyTaxId;
    private List<String> tagList;
}
