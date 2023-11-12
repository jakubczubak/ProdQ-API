package com.example.infraboxapi.departmentCost;


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
@Table(name = "_department_cost")
public class DepartmentCost {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    private double billingPeriod;
    private double employeeCosts;
    private double powerConsumption;
    private double operatingHours;
    private double pricePerKwh;
    private double mediaPrice;
    private double depreciationPrice;
    private double toolsPrice;
    private double leasingPrice;
    private double variableCostsI;
    private double variableCostsII;
}
