package com.example.infraboxapi.recycling;

import com.example.infraboxapi.recyclingItem.RecyclingItem;
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
@Table(name = "_recycling")
public class Recycling {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    private String wasteType;
    private String wasteCode;
    private String company;
    private String taxID;
    private String carID;
    private String date;
    private String time;
    private BigDecimal totalPrice;
    @OneToMany(cascade = CascadeType.ALL)
    @JoinColumn(name = "recycling_id")
    private List<RecyclingItem> recyclingItems;
}


