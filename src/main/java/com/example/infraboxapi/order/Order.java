package com.example.infraboxapi.order;


import com.example.infraboxapi.orderItem.OrderItem;
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
@Table(name = "_order")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    private String name;
    private String date;
    private String status;
    private String supplierEmail;
    private String supplierMessage;
    private double totalPrice;
    private boolean externalQuantityUpdated;
    private boolean transitQuantitySet;


    @OneToMany(cascade = CascadeType.ALL)
    @JoinColumn(name = "order_id")
    private List<OrderItem> orderItems;
}
