package com.example.infraboxapi.mrp;

/**
 * Status of MRP analysis result lifecycle.
 */
public enum MrpAnalysisStatus {
    PENDING,        // Analysis completed, no action taken yet
    DRAFT_CREATED,  // Draft purchase order created
    ORDERED,        // Purchase order submitted
    RESOLVED        // Issue resolved (resource available)
}
