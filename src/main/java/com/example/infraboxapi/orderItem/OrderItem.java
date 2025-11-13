package com.example.infraboxapi.orderItem;

import com.example.infraboxapi.accessorie.Accessorie;
import com.example.infraboxapi.material.Material;
import com.example.infraboxapi.tool.Tool;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "_order_item")
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    private String name;
    private float quantity;
    private float receivedQuantity;

    @ManyToOne
    private Material material;

    @ManyToOne
    private Tool tool;

    @ManyToOne
    private Accessorie accessorie;

    private BigDecimal newPrice;
    private boolean priceUpdated;


}
