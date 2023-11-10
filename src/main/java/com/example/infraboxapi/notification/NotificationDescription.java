package com.example.infraboxapi.notification;

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
    ToolDeleted("TOOL DELETED");

    private final String description;

    NotificationDescription(String description) {
        this.description = description;
    }

}
