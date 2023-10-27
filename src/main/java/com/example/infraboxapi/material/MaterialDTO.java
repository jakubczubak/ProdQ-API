package com.example.infraboxapi.material;


import com.example.infraboxapi.materialPriceHistory.MaterialPriceHistory;
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
public class MaterialDTO {

    private Integer id;
    private BigDecimal pricePerKg;
    private float minQuantity;
    private float quantity;

    private float z;
    private float y;
    private float x;
    private float diameter;

    private float length;

    private String name;

    private BigDecimal price;

    private String type;

    private Integer quantityInTransit;


    private String updatedOn;

    private Integer materialGroupID;

    private List<MaterialPriceHistory> materialPriceHistoryList;

}
