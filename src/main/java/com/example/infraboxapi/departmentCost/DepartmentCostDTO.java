package com.example.infraboxapi.departmentCost;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DepartmentCostDTO {

    private Integer id;
    private Integer billingPeriod;
    private Integer employeeCosts;
    private Integer powerConsumption;
    private Integer operatingHours;
    private Integer pricePerKwh;
    private Integer mediaPrice;
    private Integer depreciationPrice;
    private Integer toolsPrice;
    private Integer leasingPrice;
    private Integer variableCostsI;
    private Integer variableCostsII;
}
