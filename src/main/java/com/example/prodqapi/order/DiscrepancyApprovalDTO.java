package com.example.prodqapi.order;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for approving invoice discrepancies
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiscrepancyApprovalDTO {

    private String justification;
}
