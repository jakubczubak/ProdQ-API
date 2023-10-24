package com.example.infraboxapi.material;


import com.example.infraboxapi.materialGroup.MaterialGroup;
import com.example.infraboxapi.materialPriceHistory.MaterialPriceHistory;
import jakarta.persistence.*;
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
@Entity
@Table(name = "_material")
public class Material {
    @Id
    @GeneratedValue
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

    private Integer parentID;

    private String type;

    private Integer quantityInTransit;
    @ManyToOne
    @JoinColumn(name = "material_group_id")
    private MaterialGroup materialGroup;

    @OneToMany(mappedBy = "material", cascade = CascadeType.ALL)
    private List<MaterialPriceHistory> prices;

}
