package com.example.infraboxapi.mrp;

import com.example.infraboxapi.accessorieItem.AccessorieItem;
import com.example.infraboxapi.accessorieItem.AccessorieItemRepository;
import com.example.infraboxapi.material.Material;
import com.example.infraboxapi.material.MaterialRepository;
import com.example.infraboxapi.materialGroup.MaterialGroup;
import com.example.infraboxapi.materialReservation.MaterialReservation;
import com.example.infraboxapi.materialReservation.MaterialReservationRepository;
import com.example.infraboxapi.materialReservation.ReservationStatus;
import com.example.infraboxapi.mrp.dto.*;
import com.example.infraboxapi.order.Order;
import com.example.infraboxapi.order.OrderRepository;
import com.example.infraboxapi.orderItem.OrderItem;
import com.example.infraboxapi.productionQueueItem.ProductionQueueItem;
import com.example.infraboxapi.supplier.Supplier;
import com.example.infraboxapi.supplier.SupplierRepository;
import com.example.infraboxapi.tool.Tool;
import com.example.infraboxapi.tool.ToolRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class MrpService {

    private final MrpAnalysisResultRepository analysisResultRepository;
    private final MrpOrderSuggestionGroupRepository suggestionGroupRepository;
    private final MaterialRepository materialRepository;
    private final MaterialReservationRepository reservationRepository;
    private final ToolRepository toolRepository;
    private final AccessorieItemRepository accessorieItemRepository;
    private final SupplierRepository supplierRepository;
    private final OrderRepository orderRepository;
    private final ObjectMapper objectMapper;

    private static final int CRITICAL_DAYS_THRESHOLD = 0;  // Today or overdue
    private static final int HIGH_DAYS_THRESHOLD = 3;       // Within 3 days
    private static final double SAFETY_STOCK_FACTOR = 1.2;  // 20% buffer

    // Store the last analysis run time (persists until server restart)
    private volatile LocalDateTime lastAnalysisRunTime;

    /**
     * Run full MRP analysis for all resource types
     */
    @Transactional
    public List<MrpOrderSuggestionGroup> runFullAnalysisWithGrouping() {
        log.info("Starting full MRP analysis...");

        // Record analysis start time
        this.lastAnalysisRunTime = LocalDateTime.now();

        // Clear previous pending analyses and suggestions
        suggestionGroupRepository.dismissAllPending(LocalDateTime.now());

        // Analyze all resources
        List<MrpAnalysisResult> allAnalyses = new ArrayList<>();
        allAnalyses.addAll(analyzeMaterials());
        allAnalyses.addAll(analyzeTools());
        allAnalyses.addAll(analyzeAccessories());

        // Filter only analyses that need action (non-null priority)
        List<MrpAnalysisResult> actionableAnalyses = allAnalyses.stream()
                .filter(a -> a.getPriority() != null)
                .collect(Collectors.toList());

        log.info("Found {} actionable analyses from {} total", actionableAnalyses.size(), allAnalyses.size());

        // Group into order suggestions
        List<MrpOrderSuggestionGroup> groups = groupAnalysesIntoSuggestions(actionableAnalyses);

        log.info("Created {} suggestion groups", groups.size());

        return groups;
    }

    /**
     * Analyze materials for shortages and stock issues
     */
    @Transactional
    public List<MrpAnalysisResult> analyzeMaterials() {
        log.info("Analyzing materials...");
        List<MrpAnalysisResult> results = new ArrayList<>();

        List<Material> materials = materialRepository.findAll();

        for (Material material : materials) {
            MrpAnalysisResult analysis = analyzeMaterial(material);
            if (analysis != null) {
                results.add(analysisResultRepository.save(analysis));
            }
        }

        log.info("Material analysis completed: {} results", results.size());
        return results;
    }

    /**
     * Analyze tools for shortages and stock issues
     */
    @Transactional
    public List<MrpAnalysisResult> analyzeTools() {
        log.info("Analyzing tools...");
        List<MrpAnalysisResult> results = new ArrayList<>();

        List<Tool> tools = toolRepository.findAll();

        for (Tool tool : tools) {
            MrpAnalysisResult analysis = analyzeTool(tool);
            if (analysis != null) {
                results.add(analysisResultRepository.save(analysis));
            }
        }

        log.info("Tool analysis completed: {} results", results.size());
        return results;
    }

    /**
     * Analyze accessories for shortages and stock issues
     */
    @Transactional
    public List<MrpAnalysisResult> analyzeAccessories() {
        log.info("Analyzing accessories...");
        List<MrpAnalysisResult> results = new ArrayList<>();

        List<AccessorieItem> accessories = accessorieItemRepository.findAll();

        for (AccessorieItem accessorie : accessories) {
            MrpAnalysisResult analysis = analyzeAccessorie(accessorie);
            if (analysis != null) {
                results.add(analysisResultRepository.save(analysis));
            }
        }

        log.info("Accessorie analysis completed: {} results", results.size());
        return results;
    }

    /**
     * Analyze a single material
     */
    private MrpAnalysisResult analyzeMaterial(Material material) {
        MaterialGroup group = material.getMaterialGroup();
        String groupType = group != null ? group.getType() : "Plate";
        boolean isPlate = "Plate".equalsIgnoreCase(groupType);

        // Get stock quantity
        BigDecimal currentStock;
        String unit;
        if (isPlate) {
            currentStock = material.getStockQuantity() != null
                    ? BigDecimal.valueOf(material.getStockQuantity())
                    : BigDecimal.ZERO;
            unit = "szt";
        } else {
            currentStock = material.getTotalStockLength() != null
                    ? BigDecimal.valueOf(material.getTotalStockLength())
                    : BigDecimal.ZERO;
            unit = "mm";
        }

        // Get reserved quantity
        Double reservedRaw = reservationRepository.sumReservedQuantity(
                material.getId(),
                ReservationStatus.RESERVED,
                null
        );
        BigDecimal reserved = reservedRaw != null ? BigDecimal.valueOf(reservedRaw) : BigDecimal.ZERO;

        // Calculate available
        BigDecimal available = currentStock.subtract(reserved);

        // Get in transit
        BigDecimal inTransit = BigDecimal.valueOf(material.getQuantityInTransit());

        // Effective stock = available + in transit
        BigDecimal effectiveStock = available.add(inTransit);

        // Min quantity
        BigDecimal minQuantity = BigDecimal.valueOf(material.getMinQuantity());

        // Get required from reservations
        BigDecimal required = reserved;

        // Find affected productions and earliest deadline
        List<MaterialReservation> reservations = reservationRepository
                .findByMaterialIdAndStatus(material.getId(), ReservationStatus.RESERVED);

        List<ProductionQueueItem> affectedProductions = reservations.stream()
                .map(MaterialReservation::getProductionQueueItem)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        LocalDate earliestNeedDate = findEarliestDeadline(affectedProductions);

        // Determine priority
        MrpPriority priority = determinePriority(available, required, minQuantity, effectiveStock, earliestNeedDate);

        if (priority == null) {
            return null; // No issue, skip
        }

        // Calculate shortage
        BigDecimal shortage = BigDecimal.ZERO;
        if (available.compareTo(required) < 0) {
            shortage = required.subtract(available);
        } else if (effectiveStock.compareTo(minQuantity) < 0) {
            shortage = minQuantity.subtract(effectiveStock);
        }

        // Calculate suggested order quantity
        BigDecimal suggestedQty = calculateSuggestedOrderQuantity(shortage, minQuantity, effectiveStock);

        // Calculate estimated cost
        BigDecimal estimatedCost = BigDecimal.ZERO;
        if (material.getPrice() != null) {
            estimatedCost = material.getPrice().multiply(suggestedQty);
        }

        return MrpAnalysisResult.builder()
                .resourceType(ResourceType.MATERIAL)
                .resourceId(material.getId())
                .resourceName(buildMaterialName(material))
                .priority(priority)
                .currentStock(currentStock)
                .reservedQuantity(reserved)
                .availableQuantity(available)
                .inTransit(inTransit)
                .minQuantity(minQuantity)
                .requiredQuantity(required)
                .shortageQuantity(shortage)
                .suggestedOrderQty(suggestedQty)
                .unit(unit)
                .earliestNeedDate(earliestNeedDate)
                .affectedProductions(serializeAffectedProductions(affectedProductions))
                .status(MrpAnalysisStatus.PENDING)
                .analyzedAt(LocalDateTime.now())
                .preferredSupplier(material.getPreferredSupplier())
                .estimatedCost(estimatedCost)
                .build();
    }

    /**
     * Analyze a single tool
     */
    private MrpAnalysisResult analyzeTool(Tool tool) {
        BigDecimal currentStock = BigDecimal.valueOf(tool.getQuantity());
        BigDecimal minQuantity = BigDecimal.valueOf(tool.getMinQuantity());
        BigDecimal inTransit = BigDecimal.valueOf(tool.getQuantityInTransit());
        BigDecimal reserved = BigDecimal.ZERO; // Tools don't have reservations currently
        BigDecimal available = currentStock.subtract(reserved);
        BigDecimal effectiveStock = available.add(inTransit);

        // Tools don't have production reservations, so no deadline-based priority
        // Only check against min quantity
        MrpPriority priority = null;
        if (effectiveStock.compareTo(minQuantity) < 0) {
            priority = MrpPriority.MEDIUM;
        } else if (effectiveStock.compareTo(minQuantity.multiply(BigDecimal.valueOf(SAFETY_STOCK_FACTOR))) < 0) {
            priority = MrpPriority.LOW;
        }

        if (priority == null) {
            return null;
        }

        BigDecimal shortage = minQuantity.subtract(effectiveStock).max(BigDecimal.ZERO);
        BigDecimal suggestedQty = calculateSuggestedOrderQuantity(shortage, minQuantity, effectiveStock);

        BigDecimal estimatedCost = BigDecimal.ZERO;
        if (tool.getPrice() != null) {
            estimatedCost = tool.getPrice().multiply(suggestedQty);
        }

        return MrpAnalysisResult.builder()
                .resourceType(ResourceType.TOOL)
                .resourceId(tool.getId())
                .resourceName(tool.getName() + (tool.getToolID() != null ? " (" + tool.getToolID() + ")" : ""))
                .priority(priority)
                .currentStock(currentStock)
                .reservedQuantity(reserved)
                .availableQuantity(available)
                .inTransit(inTransit)
                .minQuantity(minQuantity)
                .requiredQuantity(BigDecimal.ZERO)
                .shortageQuantity(shortage)
                .suggestedOrderQty(suggestedQty)
                .unit("szt")
                .earliestNeedDate(null)
                .affectedProductions(null)
                .status(MrpAnalysisStatus.PENDING)
                .analyzedAt(LocalDateTime.now())
                .preferredSupplier(tool.getPreferredSupplier())
                .estimatedCost(estimatedCost)
                .build();
    }

    /**
     * Analyze a single accessorie item
     */
    private MrpAnalysisResult analyzeAccessorie(AccessorieItem accessorie) {
        BigDecimal currentStock = BigDecimal.valueOf(accessorie.getQuantity());
        BigDecimal minQuantity = BigDecimal.valueOf(accessorie.getMinQuantity());
        BigDecimal inTransit = BigDecimal.valueOf(accessorie.getQuantityInTransit());
        BigDecimal reserved = BigDecimal.ZERO;
        BigDecimal available = currentStock.subtract(reserved);
        BigDecimal effectiveStock = available.add(inTransit);

        MrpPriority priority = null;
        if (effectiveStock.compareTo(minQuantity) < 0) {
            priority = MrpPriority.MEDIUM;
        } else if (effectiveStock.compareTo(minQuantity.multiply(BigDecimal.valueOf(SAFETY_STOCK_FACTOR))) < 0) {
            priority = MrpPriority.LOW;
        }

        if (priority == null) {
            return null;
        }

        BigDecimal shortage = minQuantity.subtract(effectiveStock).max(BigDecimal.ZERO);
        BigDecimal suggestedQty = calculateSuggestedOrderQuantity(shortage, minQuantity, effectiveStock);

        BigDecimal estimatedCost = BigDecimal.ZERO;
        if (accessorie.getPrice() != null) {
            estimatedCost = accessorie.getPrice().multiply(suggestedQty);
        }

        return MrpAnalysisResult.builder()
                .resourceType(ResourceType.ACCESSORIE)
                .resourceId(accessorie.getId())
                .resourceName(accessorie.getName())
                .priority(priority)
                .currentStock(currentStock)
                .reservedQuantity(reserved)
                .availableQuantity(available)
                .inTransit(inTransit)
                .minQuantity(minQuantity)
                .requiredQuantity(BigDecimal.ZERO)
                .shortageQuantity(shortage)
                .suggestedOrderQty(suggestedQty)
                .unit("szt")
                .earliestNeedDate(null)
                .affectedProductions(null)
                .status(MrpAnalysisStatus.PENDING)
                .analyzedAt(LocalDateTime.now())
                .preferredSupplier(accessorie.getPreferredSupplier())
                .estimatedCost(estimatedCost)
                .build();
    }

    /**
     * Determine MRP priority based on availability and deadlines
     */
    private MrpPriority determinePriority(
            BigDecimal available,
            BigDecimal required,
            BigDecimal minQuantity,
            BigDecimal effectiveStock,
            LocalDate earliestNeedDate
    ) {
        LocalDate today = LocalDate.now();

        boolean hasShortage = available.compareTo(required) < 0;

        // CRITICAL: Shortage on production starting today or overdue
        if (hasShortage && earliestNeedDate != null) {
            if (!earliestNeedDate.isAfter(today)) {
                return MrpPriority.CRITICAL;
            }

            // HIGH: Shortage on production within 3 days
            if (earliestNeedDate.isBefore(today.plusDays(HIGH_DAYS_THRESHOLD + 1))) {
                return MrpPriority.HIGH;
            }
        }

        // MEDIUM: Below minimum stock
        if (effectiveStock.compareTo(minQuantity) < 0) {
            return MrpPriority.MEDIUM;
        }

        // LOW: Approaching minimum (within safety buffer)
        BigDecimal safetyThreshold = minQuantity.multiply(BigDecimal.valueOf(SAFETY_STOCK_FACTOR));
        if (effectiveStock.compareTo(safetyThreshold) < 0) {
            return MrpPriority.LOW;
        }

        // If there's a shortage but deadline is far, it's still worth noting
        if (hasShortage) {
            return MrpPriority.LOW;
        }

        return null; // No issue
    }

    /**
     * Calculate suggested order quantity
     */
    private BigDecimal calculateSuggestedOrderQuantity(
            BigDecimal shortage,
            BigDecimal minQuantity,
            BigDecimal effectiveStock
    ) {
        // Base: cover the shortage
        BigDecimal baseQty = shortage.max(BigDecimal.ZERO);

        // Target: minQuantity + 20% safety buffer
        BigDecimal targetStock = minQuantity.multiply(BigDecimal.valueOf(SAFETY_STOCK_FACTOR));

        // If after covering shortage we're still below target, add more
        BigDecimal afterShortage = effectiveStock.add(baseQty);
        if (afterShortage.compareTo(targetStock) < 0) {
            baseQty = baseQty.add(targetStock.subtract(afterShortage));
        }

        // Round up to whole units
        return baseQty.setScale(0, RoundingMode.CEILING);
    }

    /**
     * Group analyses into order suggestion groups by supplier and resource type
     */
    @Transactional
    public List<MrpOrderSuggestionGroup> groupAnalysesIntoSuggestions(List<MrpAnalysisResult> analyses) {
        Map<String, MrpOrderSuggestionGroup> groups = new LinkedHashMap<>();

        for (MrpAnalysisResult analysis : analyses) {
            Supplier supplier = analysis.getPreferredSupplier();
            ResourceType type = analysis.getResourceType();

            String groupKey = buildGroupKey(supplier, type);

            MrpOrderSuggestionGroup group = groups.computeIfAbsent(groupKey, k -> {
                MrpOrderSuggestionGroup newGroup = MrpOrderSuggestionGroup.builder()
                        .supplier(supplier)
                        .resourceType(type)
                        .groupName(buildGroupName(supplier, type))
                        .highestPriority(MrpPriority.LOW)
                        .itemCount(0)
                        .estimatedTotalNet(BigDecimal.ZERO)
                        .estimatedTotalGross(BigDecimal.ZERO)
                        .status(SuggestionStatus.PENDING)
                        .analyses(new ArrayList<>())
                        .build();
                return suggestionGroupRepository.save(newGroup);
            });

            group.addAnalysis(analysis);

            // Update totals
            if (analysis.getEstimatedCost() != null) {
                group.setEstimatedTotalNet(
                        group.getEstimatedTotalNet().add(analysis.getEstimatedCost())
                );
                // Assume 23% VAT
                group.setEstimatedTotalGross(
                        group.getEstimatedTotalNet().multiply(BigDecimal.valueOf(1.23))
                );
            }
        }

        // Calculate suggested order dates for each group
        for (MrpOrderSuggestionGroup group : groups.values()) {
            calculateSuggestedOrderDate(group);
            suggestionGroupRepository.save(group);
        }

        // Sort by priority, then by earliest need date
        return groups.values().stream()
                .sorted(Comparator
                        .comparing((MrpOrderSuggestionGroup g) -> g.getHighestPriority().getOrder())
                        .thenComparing(g -> g.getEarliestNeedDate() != null ? g.getEarliestNeedDate() : LocalDate.MAX))
                .collect(Collectors.toList());
    }

    /**
     * Create an order from a suggestion group
     */
    @Transactional
    public Order createOrderFromSuggestionGroup(Integer groupId, Integer supplierId) {
        MrpOrderSuggestionGroup group = suggestionGroupRepository.findByIdWithAnalyses(groupId);
        if (group == null) {
            throw new IllegalArgumentException("Suggestion group not found");
        }

        Supplier supplier = supplierId != null
                ? supplierRepository.findById(supplierId).orElseThrow(() -> new IllegalArgumentException("Supplier not found"))
                : group.getSupplier();

        if (supplier == null) {
            throw new IllegalArgumentException("Supplier is required to create an order");
        }

        // Create order
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("Europe/Warsaw"));
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

        // Build user-friendly order name
        String supplierDisplayName = supplier.getCompanyName() != null
                ? supplier.getCompanyName()
                : supplier.getName();
        String formattedDate = now.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));

        Order order = Order.builder()
                .name("MRP: " + supplierDisplayName + " (" + formattedDate + ")")
                .date(now.format(formatter))
                .status("pending")
                .supplier(supplier)
                .supplierEmail(supplier.getEmail())
                .totalNet(group.getEstimatedTotalNet().doubleValue())
                .totalVat(group.getEstimatedTotalNet().multiply(BigDecimal.valueOf(0.23)).doubleValue())
                .totalGross(group.getEstimatedTotalGross().doubleValue())
                .orderItems(new ArrayList<>())
                .build();

        // Create order items from analyses
        for (MrpAnalysisResult analysis : group.getAnalyses()) {
            OrderItem item = createOrderItemFromAnalysis(analysis);
            if (item != null) {
                order.getOrderItems().add(item);
            }

            // Update analysis status - mark as resolved so counters update correctly
            analysis.setStatus(MrpAnalysisStatus.RESOLVED);
            analysis.setResolvedAt(LocalDateTime.now());
            analysisResultRepository.save(analysis);
        }

        // Save order
        Order savedOrder = orderRepository.save(order);

        // Update suggestion group
        group.setStatus(SuggestionStatus.CONVERTED_TO_ORDER);
        group.setGeneratedOrder(savedOrder);
        suggestionGroupRepository.save(group);

        log.info("Created order {} from suggestion group {}", savedOrder.getId(), groupId);

        return savedOrder;
    }

    /**
     * Dismiss a suggestion group
     */
    @Transactional
    public void dismissSuggestion(Integer groupId, String reason) {
        MrpOrderSuggestionGroup group = suggestionGroupRepository.findById(groupId)
                .orElseThrow(() -> new IllegalArgumentException("Suggestion group not found"));

        group.setStatus(SuggestionStatus.DISMISSED);
        group.setDismissedAt(LocalDateTime.now());
        group.setDismissedReason(reason);

        // Also update related analyses
        for (MrpAnalysisResult analysis : group.getAnalyses()) {
            analysis.setStatus(MrpAnalysisStatus.RESOLVED);
            analysis.setResolvedAt(LocalDateTime.now());
        }

        suggestionGroupRepository.save(group);
    }

    /**
     * Get active (pending) suggestion groups
     */
    @Transactional(readOnly = true)
    public List<MrpOrderSuggestionGroupDTO> getActiveSuggestions() {
        List<MrpOrderSuggestionGroup> groups = suggestionGroupRepository.findPendingSuggestionsOrderedByPriority();
        return groups.stream()
                .map(this::mapToGroupDTO)
                .collect(Collectors.toList());
    }

    /**
     * Get dashboard summary
     */
    @Transactional(readOnly = true)
    public MrpDashboardDTO getDashboard() {
        long criticalCount = analysisResultRepository.countByPriorityAndStatusNot(MrpPriority.CRITICAL, MrpAnalysisStatus.RESOLVED);
        long highCount = analysisResultRepository.countByPriorityAndStatusNot(MrpPriority.HIGH, MrpAnalysisStatus.RESOLVED);
        long mediumCount = analysisResultRepository.countByPriorityAndStatusNot(MrpPriority.MEDIUM, MrpAnalysisStatus.RESOLVED);
        long lowCount = analysisResultRepository.countByPriorityAndStatusNot(MrpPriority.LOW, MrpAnalysisStatus.RESOLVED);

        List<MrpAnalysisResult> activeAnalyses = analysisResultRepository.findActiveAnalysesOrderedByPriority();

        BigDecimal totalShortageValue = activeAnalyses.stream()
                .map(a -> a.getEstimatedCost() != null ? a.getEstimatedCost() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal criticalValue = activeAnalyses.stream()
                .filter(a -> a.getPriority() == MrpPriority.CRITICAL)
                .map(a -> a.getEstimatedCost() != null ? a.getEstimatedCost() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long pendingGroups = suggestionGroupRepository.countByHighestPriorityAndStatus(MrpPriority.CRITICAL, SuggestionStatus.PENDING)
                + suggestionGroupRepository.countByHighestPriorityAndStatus(MrpPriority.HIGH, SuggestionStatus.PENDING)
                + suggestionGroupRepository.countByHighestPriorityAndStatus(MrpPriority.MEDIUM, SuggestionStatus.PENDING)
                + suggestionGroupRepository.countByHighestPriorityAndStatus(MrpPriority.LOW, SuggestionStatus.PENDING);

        List<MrpAnalysisResultDTO> topCritical = activeAnalyses.stream()
                .filter(a -> a.getPriority() == MrpPriority.CRITICAL || a.getPriority() == MrpPriority.HIGH)
                .limit(5)
                .map(this::mapToAnalysisDTO)
                .collect(Collectors.toList());

        // Use stored lastAnalysisRunTime, fallback to stream result if not set
        LocalDateTime lastAnalysis = this.lastAnalysisRunTime != null
                ? this.lastAnalysisRunTime
                : activeAnalyses.stream()
                        .map(MrpAnalysisResult::getAnalyzedAt)
                        .max(LocalDateTime::compareTo)
                        .orElse(null);

        return MrpDashboardDTO.builder()
                .criticalCount(criticalCount)
                .highCount(highCount)
                .mediumCount(mediumCount)
                .lowCount(lowCount)
                .totalCount(criticalCount + highCount + mediumCount + lowCount)
                .materialCount(activeAnalyses.stream().filter(a -> a.getResourceType() == ResourceType.MATERIAL).count())
                .toolCount(activeAnalyses.stream().filter(a -> a.getResourceType() == ResourceType.TOOL).count())
                .accessorieCount(activeAnalyses.stream().filter(a -> a.getResourceType() == ResourceType.ACCESSORIE).count())
                .totalShortageValue(totalShortageValue)
                .criticalShortageValue(criticalValue)
                .pendingSuggestionGroups(pendingGroups)
                .topCriticalItems(topCritical)
                .lastAnalysisAt(lastAnalysis)
                .statusMessage(buildStatusMessage(criticalCount, highCount))
                .build();
    }

    // ==================== Helper Methods ====================

    private String buildGroupKey(Supplier supplier, ResourceType type) {
        String supplierId = supplier != null ? String.valueOf(supplier.getId()) : "no_supplier";
        return supplierId + "_" + type.name();
    }

    private String buildGroupName(Supplier supplier, ResourceType type) {
        String supplierName = supplier != null ? supplier.getCompanyName() : "Brak dostawcy";
        return supplierName + " - " + type.getDisplayName();
    }

    private String buildMaterialName(Material material) {
        // Simply use material name - it already contains all needed information
        if (material.getName() != null && !material.getName().isBlank()) {
            return material.getName();
        }

        // Fallback: if no name, build from group and dimensions
        StringBuilder name = new StringBuilder();
        MaterialGroup group = material.getMaterialGroup();

        if (group != null) {
            name.append(group.getName()).append(" ");

            if ("Plate".equalsIgnoreCase(group.getType())) {
                name.append(String.format("%.0fx%.0fx%.0fmm", material.getX(), material.getY(), material.getZ()));
            } else if ("Rod".equalsIgnoreCase(group.getType())) {
                name.append(String.format("ø%.0f L%.0fmm", material.getDiameter(), material.getLength()));
            } else if ("Tube".equalsIgnoreCase(group.getType())) {
                Float inner = material.getInnerDiameter();
                name.append(String.format("ø%.0f/%.0f L%.0fmm",
                        material.getDiameter(),
                        inner != null ? inner : 0f,
                        material.getLength()));
            }
        }

        return name.toString().trim();
    }

    private LocalDate findEarliestDeadline(List<ProductionQueueItem> productions) {
        return productions.stream()
                .map(ProductionQueueItem::getDeadline)
                .filter(Objects::nonNull)
                .map(this::parseDeadline)
                .filter(Objects::nonNull)
                .min(LocalDate::compareTo)
                .orElse(null);
    }

    private LocalDate parseDeadline(String deadline) {
        if (deadline == null || deadline.isBlank()) {
            return null;
        }
        try {
            // Try common formats
            for (String pattern : Arrays.asList("yyyy-MM-dd", "dd.MM.yyyy", "dd-MM-yyyy")) {
                try {
                    return LocalDate.parse(deadline, DateTimeFormatter.ofPattern(pattern));
                } catch (DateTimeParseException ignored) {
                }
            }
        } catch (Exception e) {
            log.warn("Could not parse deadline: {}", deadline);
        }
        return null;
    }

    private String serializeAffectedProductions(List<ProductionQueueItem> productions) {
        if (productions == null || productions.isEmpty()) {
            return null;
        }
        try {
            List<Map<String, Object>> simplified = productions.stream()
                    .map(p -> {
                        Map<String, Object> map = new HashMap<>();
                        map.put("id", p.getId());
                        map.put("name", p.getOrderName() + " - " + p.getPartName());
                        map.put("deadline", p.getDeadline());
                        return map;
                    })
                    .collect(Collectors.toList());
            return objectMapper.writeValueAsString(simplified);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize affected productions", e);
            return null;
        }
    }

    private void calculateSuggestedOrderDate(MrpOrderSuggestionGroup group) {
        LocalDate earliestNeed = group.getEarliestNeedDate();
        Integer leadTime = group.getEstimatedLeadTimeDays();

        if (earliestNeed != null && leadTime != null && leadTime > 0) {
            group.setSuggestedOrderDate(earliestNeed.minusDays(leadTime));
        } else if (earliestNeed != null) {
            // No lead time info, suggest ordering today if critical
            if (group.getHighestPriority() == MrpPriority.CRITICAL ||
                    group.getHighestPriority() == MrpPriority.HIGH) {
                group.setSuggestedOrderDate(LocalDate.now());
            }
        }
    }

    private OrderItem createOrderItemFromAnalysis(MrpAnalysisResult analysis) {
        OrderItem item = OrderItem.builder()
                .name(analysis.getResourceName())
                .quantity(analysis.getSuggestedOrderQty().floatValue())
                .receivedQuantity(0)
                .vatRate(23)
                .discount(0.0f)
                .build();

        switch (analysis.getResourceType()) {
            case MATERIAL:
                Material material = materialRepository.findById(analysis.getResourceId()).orElse(null);
                if (material != null) {
                    item.setMaterial(material);
                    item.setNewPrice(material.getPrice());
                    item.setPricePerKg(material.getPricePerKg());
                }
                break;
            case TOOL:
                Tool tool = toolRepository.findById(analysis.getResourceId()).orElse(null);
                if (tool != null) {
                    item.setTool(tool);
                    item.setNewPrice(tool.getPrice());
                }
                break;
            case ACCESSORIE:
                // Note: OrderItem links to Accessorie (parent), not AccessorieItem
                // This might need adjustment based on actual data model
                AccessorieItem accessorieItem = accessorieItemRepository.findById(analysis.getResourceId()).orElse(null);
                if (accessorieItem != null) {
                    item.setNewPrice(accessorieItem.getPrice());
                }
                break;
        }

        return item;
    }

    private String buildStatusMessage(long criticalCount, long highCount) {
        if (criticalCount > 0) {
            return String.format("⚠️ UWAGA: %d krytycznych pozycji wymaga natychmiastowej akcji!", criticalCount);
        } else if (highCount > 0) {
            return String.format("⚡ %d pozycji wymaga pilnej uwagi (< 3 dni)", highCount);
        } else {
            return "✅ Brak krytycznych niedoborów";
        }
    }

    // ==================== DTO Mappers ====================

    private MrpAnalysisResultDTO mapToAnalysisDTO(MrpAnalysisResult analysis) {
        return MrpAnalysisResultDTO.builder()
                .id(analysis.getId())
                .resourceType(analysis.getResourceType())
                .resourceId(analysis.getResourceId())
                .resourceName(analysis.getResourceName())
                .priority(analysis.getPriority())
                .priorityDescription(analysis.getPriority() != null ? analysis.getPriority().getDescription() : null)
                .currentStock(analysis.getCurrentStock())
                .reservedQuantity(analysis.getReservedQuantity())
                .availableQuantity(analysis.getAvailableQuantity())
                .inTransit(analysis.getInTransit())
                .minQuantity(analysis.getMinQuantity())
                .requiredQuantity(analysis.getRequiredQuantity())
                .shortageQuantity(analysis.getShortageQuantity())
                .suggestedOrderQty(analysis.getSuggestedOrderQty())
                .unit(analysis.getUnit())
                .earliestNeedDate(analysis.getEarliestNeedDate())
                .affectedProductions(analysis.getAffectedProductions())
                .status(analysis.getStatus())
                .analyzedAt(analysis.getAnalyzedAt())
                .preferredSupplierId(analysis.getPreferredSupplier() != null ? analysis.getPreferredSupplier().getId() : null)
                .preferredSupplierName(analysis.getPreferredSupplier() != null ? analysis.getPreferredSupplier().getCompanyName() : null)
                .estimatedCost(analysis.getEstimatedCost())
                .suggestionGroupId(analysis.getSuggestionGroup() != null ? analysis.getSuggestionGroup().getId() : null)
                .build();
    }

    private MrpOrderSuggestionGroupDTO mapToGroupDTO(MrpOrderSuggestionGroup group) {
        List<MrpAnalysisResultDTO> analysisDTOs = group.getAnalyses() != null
                ? group.getAnalyses().stream().map(this::mapToAnalysisDTO).collect(Collectors.toList())
                : new ArrayList<>();

        return MrpOrderSuggestionGroupDTO.builder()
                .id(group.getId())
                .groupName(group.getGroupName())
                .supplierId(group.getSupplier() != null ? group.getSupplier().getId() : null)
                .supplierName(group.getSupplier() != null ? group.getSupplier().getName() : null)
                .supplierCompanyName(group.getSupplier() != null ? group.getSupplier().getCompanyName() : null)
                .resourceType(group.getResourceType())
                .resourceTypeDisplay(group.getResourceType() != null ? group.getResourceType().getDisplayName() : null)
                .highestPriority(group.getHighestPriority())
                .priorityDescription(group.getHighestPriority() != null ? group.getHighestPriority().getDescription() : null)
                .itemCount(group.getItemCount())
                .estimatedTotalNet(group.getEstimatedTotalNet())
                .estimatedTotalGross(group.getEstimatedTotalGross())
                .estimatedLeadTimeDays(group.getEstimatedLeadTimeDays())
                .suggestedOrderDate(group.getSuggestedOrderDate())
                .earliestNeedDate(group.getEarliestNeedDate())
                .status(group.getStatus())
                .createdAt(group.getCreatedAt())
                .analyses(analysisDTOs)
                .generatedOrderId(group.getGeneratedOrder() != null ? group.getGeneratedOrder().getId() : null)
                .build();
    }
}
