package com.example.infraboxapi.recyclingItem;

import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecyclingItemDTO {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    @NotBlank(message = "Field 'name' cannot be blank")
    @Size(min = 1, max = 100, message = "Field 'name' must have a length between 1 and 100 characters")
    private String name;
    @PositiveOrZero
    private Double quantity; //kg
    @PositiveOrZero
    private BigDecimal pricePerKg;
    @PositiveOrZero
    private BigDecimal totalPrice;

}
