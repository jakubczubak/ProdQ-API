package com.example.infraboxapi.calculation;


import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CalculationDTO {
    private Integer id;
    @PositiveOrZero(message = "Value must be a positive number or zero")
    private Integer shiftLength;
    @PositiveOrZero(message = "Value must be a positive number or zero")
    private Integer numberOfMachines;
    @PositiveOrZero(message = "Value must be a positive number or zero")
    private double hourlyRate;
    @PositiveOrZero(message = "Value must be a positive number or zero")
    private double income;
    @PositiveOrZero(message = "Value must be a positive number or zero")
    private double toolCost;
    @PositiveOrZero(message = "Value must be a positive number or zero")
    private double materialCost;
    @PositiveOrZero(message = "Value must be a positive number or zero")
    private double factor;
    @PositiveOrZero(message = "Value must be a positive number or zero")
    private double camTime;
    @PositiveOrZero(message = "Value must be a positive number or zero")
    private double variableCostsII;
    @PositiveOrZero(message = "Value must be a positive number or zero")
    private double variableCostsI;
    @PositiveOrZero(message = "Value must be a positive number or zero")
    private double leasingPrice;
    @PositiveOrZero(message = "Value must be a positive number or zero")
    private double toolsPrice;
    @PositiveOrZero(message = "Value must be a positive number or zero")
    private double depreciationPrice;
    @PositiveOrZero(message = "Value must be a positive number or zero")
    private double mediaPrice;
    @PositiveOrZero(message = "Value must be a positive number or zero")
    private double pricePerKwh;
    @PositiveOrZero(message = "Value must be a positive number or zero")
    private double operatingHours;
    @PositiveOrZero(message = "Value must be a positive number or zero")
    private double powerConsumption;
    @PositiveOrZero(message = "Value must be a positive number or zero")
    private double employeeCosts;

    @NotBlank(message = "Field 'status' cannot be blank")
    @Size(min = 1, max = 100, message = "Field 'name' must have a length between 1 and 100 characters")
    private String status;
    private String selectedDate;
    @NotBlank(message = "Field 'name' cannot be blank")
    @Size(min = 1, max = 100, message = "Field 'name' must have a length between 1 and 100 characters")
    private String calculationName;
    @PositiveOrZero(message = "Value must be a positive number or zero")
    private double billingPeriod;
    @PositiveOrZero(message = "Value must be a positive number or zero")
    private double startupFee;
    @PositiveOrZero(message = "Value must be a positive number or zero")
    private double cncOrderValuation;
}
