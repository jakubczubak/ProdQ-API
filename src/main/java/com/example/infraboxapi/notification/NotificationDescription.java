package com.example.infraboxapi.notification;

import com.example.infraboxapi.calculation.Calculation;
import com.example.infraboxapi.departmentCost.DepartmentCost;
import lombok.Getter;

@Getter
public enum NotificationDescription {
    MaterialAdded("NEW MATERIAL ADDED"),
    MaterialUpdated("MATERIAL UPDATED"),
    MaterialDeleted("MATERIAL DELETED"),

    MaterialGroupAdded("NEW MATERIAL GROUP ADDED"),

    MaterialGroupUpdated("MATERIAL GROUP UPDATED"),

    MaterialGroupDeleted("MATERIAL GROUP DELETED"),

    MaterialScanner("MATERIAL SCANNER"),
    ToolScanner("TOOL SCANNER"),

    ToolGroupAdded("NEW TOOL GROUP ADDED"),
    ToolGroupUpdated("TOOL GROUP UPDATED"),
    ToolGroupDeleted("TOOL GROUP DELETED"),

    ToolAdded("NEW TOOL ADDED"),
    ToolUpdated("TOOL UPDATED"),
    ToolDeleted("TOOL DELETED"),

    DepartmentCostUpdated("DEPARTMENT COST UPDATED"),

    CalculationAdded("NEW CALCULATION ADDED"),
    CalculationUpdated("CALCULATION UPDATED"),
    CalculationDeleted("CALCULATION DELETED"),

    OrderAdded("NEW ORDER ADDED");


    private final String description;

    NotificationDescription(String description) {
        this.description = description;
    }

}
