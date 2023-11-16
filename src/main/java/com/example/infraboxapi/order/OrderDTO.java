package com.example.infraboxapi.order;


import com.example.infraboxapi.orderItem.OrderItem;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderDTO {
    private Integer id;
    private String name;
    private String date;
    private String status;
    private String supplierEmail;
    private String supplierMessage;
    private boolean isAddedToWarehouse;
    private boolean isQuantityInTransportSet;
    private double totalPrice;
    private List<OrderItem> orderItems;

}
