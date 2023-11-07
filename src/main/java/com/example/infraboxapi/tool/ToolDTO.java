package com.example.infraboxapi.tool;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolDTO {

    private Integer id;
    private String name;
    private String type;
    private float dc;
    private float cfl;
    private float oal;
    private float quantity;
    private float minQuantity;
    private float price;
    private String toolID;
    private String eShopLink;
    private String additionalInfo;
    private float quantityInTransit;
    private Integer toolGroupID;

    private String updatedOn;

}
