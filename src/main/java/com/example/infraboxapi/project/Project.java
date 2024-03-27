package com.example.infraboxapi.project;

import com.example.infraboxapi.productionItem.ProductionItem;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "_project")
public class Project {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    private String name;
    private String status;
    private double hourlyRate;

    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.EAGER, orphanRemoval = true)

    @JoinColumn(name = "project_id")
    private List<ProductionItem> productionItems;
    private double productionTime;
    private BigDecimal materialValue;
    private BigDecimal toolValue;
    private BigDecimal productionValue;
    private BigDecimal productionValueBasedOnDepartmentCost;
    private BigDecimal totalProductionValue;
    @Column(name = "updated_on")
    private String updatedOn;
    @Column(name = "craeted_on")
    private String createdOn;

    @PreUpdate
    public void preUpdate() {
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        updatedOn = now.format(formatter);
    }

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        createdOn = now.format(formatter);
    }
}
