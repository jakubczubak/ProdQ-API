package com.example.infraboxapi.departmentCost;


import jakarta.validation.constraints.PositiveOrZero;
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
    @PositiveOrZero(message = "Value must be a positive number or zero")
    private Integer billingPeriod;
    @PositiveOrZero(message = "Value must be a positive number or zero")
    private Integer employeeCosts;
    @PositiveOrZero(message = "Value must be a positive number or zero")
    private Integer powerConsumption;
    @PositiveOrZero(message = "Value must be a positive number or zero")
    private Integer operatingHours;
    @PositiveOrZero(message = "Value must be a positive number or zero")
    private Integer pricePerKwh;
    @PositiveOrZero(message = "Value must be a positive number or zero")
    private Integer mediaPrice;
    @PositiveOrZero(message = "Value must be a positive number or zero")
    private Integer depreciationPrice;
    @PositiveOrZero(message = "Value must be a positive number or zero")
    private Integer toolsPrice;
    @PositiveOrZero(message = "Value must be a positive number or zero")
    private Integer leasingPrice;
    @PositiveOrZero(message = "Value must be a positive number or zero")
    private Integer variableCostsI;
    @PositiveOrZero(message = "Value must be a positive number or zero")
    private Integer variableCostsII;
}
