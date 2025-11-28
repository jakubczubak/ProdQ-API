package com.example.prodqapi.mrp;

/**
 * Priority levels for MRP analysis results.
 * Lower order value = higher priority.
 */
public enum MrpPriority {
    CRITICAL(1, "Stop produkcji - brak zasobu na startujące zlecenie"),
    HIGH(2, "Brak zasobu na zlecenie startujące w < 3 dni"),
    MEDIUM(3, "Poniżej minimalnego stanu magazynowego"),
    LOW(4, "Uzupełnienie typowe / optymalizacja kosztów");

    private final int order;
    private final String description;

    MrpPriority(int order, String description) {
        this.order = order;
        this.description = description;
    }

    public int getOrder() {
        return order;
    }

    public String getDescription() {
        return description;
    }
}
