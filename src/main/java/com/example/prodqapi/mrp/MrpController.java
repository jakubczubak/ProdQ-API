package com.example.prodqapi.mrp;

import com.example.prodqapi.mrp.dto.*;
import com.example.prodqapi.order.Order;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/mrp")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class MrpController {

    private final MrpService mrpService;

    /**
     * Run full MRP analysis and return dashboard summary
     */
    @GetMapping("/analyze")
    public ResponseEntity<MrpDashboardDTO> runAnalysis() {
        log.info("Running MRP analysis on demand");
        mrpService.runFullAnalysisWithGrouping();
        MrpDashboardDTO dashboard = mrpService.getDashboard();
        return ResponseEntity.ok(dashboard);
    }

    /**
     * Get dashboard summary without running new analysis
     */
    @GetMapping("/dashboard")
    public ResponseEntity<MrpDashboardDTO> getDashboard() {
        MrpDashboardDTO dashboard = mrpService.getDashboard();
        return ResponseEntity.ok(dashboard);
    }

    /**
     * Get all pending order suggestions
     */
    @GetMapping("/suggestions")
    public ResponseEntity<List<MrpOrderSuggestionGroupDTO>> getSuggestions() {
        List<MrpOrderSuggestionGroupDTO> suggestions = mrpService.getActiveSuggestions();
        return ResponseEntity.ok(suggestions);
    }

    /**
     * Create an order from a suggestion group
     */
    @PostMapping("/suggestions/{groupId}/create-order")
    public ResponseEntity<Order> createOrderFromSuggestion(
            @PathVariable Integer groupId,
            @Valid @RequestBody CreateOrderFromSuggestionRequest request
    ) {
        log.info("Creating order from suggestion group {} with supplier {}", groupId, request.getSupplierId());
        Order order = mrpService.createOrderFromSuggestionGroup(groupId, request.getSupplierId());
        return ResponseEntity.ok(order);
    }

    /**
     * Dismiss a suggestion group
     */
    @PostMapping("/suggestions/{groupId}/dismiss")
    public ResponseEntity<Void> dismissSuggestion(
            @PathVariable Integer groupId,
            @RequestBody(required = false) Map<String, String> body
    ) {
        String reason = body != null ? body.get("reason") : null;
        log.info("Dismissing suggestion group {} with reason: {}", groupId, reason);
        mrpService.dismissSuggestion(groupId, reason);
        return ResponseEntity.ok().build();
    }

    /**
     * Run analysis for materials only
     */
    @GetMapping("/analyze/materials")
    public ResponseEntity<List<MrpAnalysisResult>> analyzeMaterials() {
        List<MrpAnalysisResult> results = mrpService.analyzeMaterials();
        return ResponseEntity.ok(results);
    }

    /**
     * Run analysis for tools only
     */
    @GetMapping("/analyze/tools")
    public ResponseEntity<List<MrpAnalysisResult>> analyzeTools() {
        List<MrpAnalysisResult> results = mrpService.analyzeTools();
        return ResponseEntity.ok(results);
    }

    /**
     * Run analysis for accessories only
     */
    @GetMapping("/analyze/accessories")
    public ResponseEntity<List<MrpAnalysisResult>> analyzeAccessories() {
        List<MrpAnalysisResult> results = mrpService.analyzeAccessories();
        return ResponseEntity.ok(results);
    }
}
