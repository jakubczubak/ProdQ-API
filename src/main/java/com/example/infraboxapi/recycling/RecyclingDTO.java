package com.example.infraboxapi.recycling;

import com.example.infraboxapi.recyclingItem.RecyclingItemDTO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.util.List;
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecyclingDTO {

    private Integer id;
    @NotBlank(message = "Field 'waste type' cannot be blank")
    @Size(min = 1, max = 100, message = "Field 'waste type' must have a length between 1 and 100 characters")
    private String wasteType;
    @NotBlank(message = "Field 'waste code' cannot be blank")
    @Size(min = 1, max = 100, message = "Field 'waste code' must have a length between 1 and 100 characters")
    private String wasteCode;
    @NotBlank(message = "Field 'company' cannot be blank")
    @Size(min = 1, max = 100, message = "Field 'company' must have a length between 1 and 100 characters")
    private String company;
    @NotBlank(message = "Field 'taxID' cannot be blank")
    @Size(min = 1, max = 100, message = "Field 'taxID' must have a length between 1 and 100 characters")
    private String taxID;
    @NotBlank(message = "Field 'carID' cannot be blank")
    @Size(min = 1, max = 100, message = "Field 'carID' must have a length between 1 and 100 characters")
    private String carID;
    @DateTimeFormat(pattern = "DD/MM/YYYY")
    private String date;
    @DateTimeFormat(pattern = "HH:mm")
    private String time;
    @PositiveOrZero(message = "Value must be a positive number or zero")
    private BigDecimal totalPrice;
    @Valid
    private List<RecyclingItemDTO> recyclingItems;
}
