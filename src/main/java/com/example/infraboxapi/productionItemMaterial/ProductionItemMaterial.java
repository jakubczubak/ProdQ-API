package com.example.infraboxapi.productionItemMaterial;

import com.example.infraboxapi.materialType.MaterialType;
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
@Table(name = "_production_item_material")
public class ProductionItemMaterial {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    @ManyToOne
    @JoinColumn(name = "material_type_id")
    private MaterialType materialType;
    private BigDecimal pricePerKg;
    private String type;
    private float z;
    private float y;
    private float x;
    private float diameter;
    private float length;
    private float thickness;
}
