package com.example.infraboxapi.recycling;

import com.example.infraboxapi.recyclingItem.RecyclingItemDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
    private String company;
    private String taxID;
    private String carID;
    private String date;
    private String time;
    private BigDecimal totalPrice;
    private List<RecyclingItemDTO> recyclingItems;
}
