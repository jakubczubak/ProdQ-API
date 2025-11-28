package com.example.prodqapi.supplier;


import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;


@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "_supplier")
public class Supplier {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
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
    @ElementCollection
    private List<String> tagList;
}


