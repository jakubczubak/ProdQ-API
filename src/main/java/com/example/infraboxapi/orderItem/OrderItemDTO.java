package com.example.infraboxapi.orderItem;


import com.example.infraboxapi.material.Material;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderItemDTO {

    private Integer id;
    private String name;
    private Integer quantity;
    private String itemType;
    private Integer itemID;
}
