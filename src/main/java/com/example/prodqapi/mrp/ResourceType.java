package com.example.prodqapi.mrp;

/**
 * Types of resources tracked by MRP system.
 */
public enum ResourceType {
    MATERIAL("Materiały"),
    TOOL("Narzędzia"),
    ACCESSORIE("Akcesoria");

    private final String displayName;

    ResourceType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
