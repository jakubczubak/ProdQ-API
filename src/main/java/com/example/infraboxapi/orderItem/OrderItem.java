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

    @Column(name = "vat_rate")
    @Builder.Default
    private Integer vatRate = 23; // VAT rate percentage (default 23%)

    @Column(name = "discount")
    @Builder.Default
    private Float discount = 0.0f; // Discount percentage (0-100)

}
