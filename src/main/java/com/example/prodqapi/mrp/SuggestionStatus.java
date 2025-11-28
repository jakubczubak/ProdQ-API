package com.example.prodqapi.mrp;

/**
 * Status of order suggestion group.
 */
public enum SuggestionStatus {
    PENDING,            // Suggestion active, waiting for user action
    CONVERTED_TO_ORDER, // User created order from this suggestion
    DISMISSED           // User dismissed/ignored this suggestion
}
