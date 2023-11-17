package com.example.infraboxapi.orderItem;

import com.example.infraboxapi.material.Material;
import com.example.infraboxapi.tool.Tool;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "_order_item")
public class OrderItem {

    @Id
    @GeneratedValue(strategy =GenerationType.IDENTITY)
    private Integer id;
    private String name;
    private Integer quantity;
    @ManyToOne
    private Material material;
    @ManyToOne
    private Tool tool;


}
