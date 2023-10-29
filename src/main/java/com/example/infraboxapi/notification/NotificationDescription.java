package com.example.infraboxapi.notification;

import com.example.infraboxapi.materialGroup.MaterialGroup;
import lombok.Getter;

@Getter
public enum NotificationDescription {
    MaterialAdded("NEW MATERIAL ADDED"),
    MaterialUpdated("MATERIAL UPDATED"),
    MaterialDeleted("MATERIAL DELETED"),

    MaterialGroupAdded("NEW MATERIAL GROUP ADDED"),

    MaterialGroupUpdated("MATERIAL GROUP UPDATED"),

    MaterialGroupDeleted("MATERIAL GROUP DELETED");

    private final String description;

    NotificationDescription(String description) {
        this.description = description;
    }

}
