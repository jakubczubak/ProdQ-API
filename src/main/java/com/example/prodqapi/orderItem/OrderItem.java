package com.example.prodqapi.orderItem;

import com.example.prodqapi.accessorie.Accessorie;
import com.example.prodqapi.material.Material;
import com.example.prodqapi.tool.Tool;
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

    @Column(name = "previously_added_to_inventory")
    @Builder.Default
    private Float previouslyAddedToInventory = 0.0f; // Track what was already added to warehouse

    @ManyToOne
    private Material material;

    @ManyToOne
    private Tool tool;

    @ManyToOne
    private Accessorie accessorie;

    private BigDecimal newPrice;
    private boolean priceUpdated;

    @Column(name = "price_change_reason", length = 500)
    private String priceChangeReason; // Reason for price change (required when >10% difference)

    @Column(name = "vat_rate")
    @Builder.Default
    private Integer vatRate = 23; // VAT rate percentage (default 23%)

    @Column(name = "discount")
    @Builder.Default
    private Float discount = 0.0f; // Discount percentage (0-100)

    @Column(name = "price_per_kg")
    private BigDecimal pricePerKg; // Price per kilogram for materials (PLN/kg)

}
