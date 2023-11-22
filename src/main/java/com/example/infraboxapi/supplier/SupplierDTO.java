package com.example.infraboxapi.supplier;


import jakarta.persistence.ElementCollection;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.validator.constraints.URL;
import org.springframework.format.annotation.NumberFormat;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SupplierDTO {
    private Integer id;

    @NotBlank(message = "Field 'name' cannot be blank")
    @Size(min = 1, max = 100, message = "Field 'name' must have a length between 1 and 100 characters")
    private String name;
    @NotBlank(message = "Field 'surname' cannot be blank")
    @Size(min = 1, max = 100, message = "Field 'surname' must have a length between 1 and 100 characters")
    private String surname;
    @NumberFormat
    private String phoneNumber;
    @Email(message = "Field 'email' must be a valid email address")
    private String email;
    @NotBlank(message = "Field 'company name' cannot be blank")
    @Size(min = 1, max = 100, message = "Field 'company name' must have a length between 1 and 100 characters")
    private String companyName;
    @NotBlank(message = "Field 'position' cannot be blank")
    @Size(min = 1, max = 100, message = "Field 'position' must have a length between 1 and 100 characters")
    private String position;
    @URL(message = "Field 'company logo' must be a valid URL")
    private String companyLogo;
    @URL(message = "Field 'company logo' must be a valid URL")
    private String companyWebsite;
    @NotBlank(message = "Field 'position' cannot be blank")
    @Size(min = 1, max = 100, message = "Field 'position' must have a length between 1 and 100 characters")
    private String companyTaxId;
    private List<String> tagList;
}
