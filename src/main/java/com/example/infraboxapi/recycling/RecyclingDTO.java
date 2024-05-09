package com.example.infraboxapi.recycling;

import com.example.infraboxapi.recyclingItem.RecyclingItemDTO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecyclingDTO {

    private Integer id;
    private String wasteType;

    private String wasteCode;
    @Size(min = 1, max = 100, message = "Field 'company' must have a length between 1 and 100 characters")
    private String company;
    private String taxID;
    private String carID;
    @DateTimeFormat(pattern = "DD/MM/YYYY")
    private String date;
    @DateTimeFormat(pattern = "HH:mm")
    private String time;
    @PositiveOrZero(message = "Value must be a positive number or zero")
    private BigDecimal totalPrice;
    @Valid
    private List<RecyclingItemDTO> recyclingItems;
    private MultipartFile filePDF;
}
