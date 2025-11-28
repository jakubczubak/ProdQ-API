package com.example.infraboxapi.supplier;

import com.example.infraboxapi.order.Order;
import com.example.infraboxapi.order.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Service for calculating and managing supplier performance KPIs.
 *
 * KPI Formula:
 * overallScore = (onTimeDeliveryRate * 0.5) + (qualityRate * 0.3) + (leadTimeScore * 0.2)
 *
 * where:
 * - onTimeDeliveryRate = (onTimeOrders / completedOrders) * 100
 * - qualityRate = (avgQualityRating / 5) * 100
 * - leadTimeScore = max(0, 100 - (avgLeadTimeDays - 7) * 3)
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class SupplierPerformanceService {

    private final SupplierPerformanceRepository performanceRepository;
    private final SupplierRepository supplierRepository;
    private final OrderRepository orderRepository;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    // === Public Methods ===

    /**
     * Get performance metrics for a specific supplier
     */
    public SupplierPerformanceDTO getPerformance(Integer supplierId) {
        Optional<SupplierPerformance> performance = performanceRepository.findBySupplierId(supplierId);
        return performance.map(SupplierPerformanceDTO::fromEntity).orElse(null);
    }

    /**
     * Get ranking of all suppliers by overall score
     */
    public List<SupplierPerformanceDTO> getSupplierRanking() {
        return performanceRepository.findAllOrderByOverallScoreDesc()
                .stream()
                .map(SupplierPerformanceDTO::fromEntity)
                .collect(Collectors.toList());
    }

    /**
     * Recalculate performance metrics for all suppliers
     * Called by scheduled job
     */
    @Transactional
    public void recalculateAllSuppliers() {
        log.info("Starting supplier performance recalculation for all suppliers...");

        List<Supplier> suppliers = supplierRepository.findAll();
        int successCount = 0;
        int errorCount = 0;

        for (Supplier supplier : suppliers) {
            try {
                calculateAndSavePerformance(supplier);
                successCount++;
            } catch (Exception e) {
                log.error("Error calculating performance for supplier {}: {}", supplier.getId(), e.getMessage());
                errorCount++;
            }
        }

        log.info("Supplier performance recalculation completed. Success: {}, Errors: {}", successCount, errorCount);
    }

    /**
     * Recalculate performance for a single supplier
     */
    @Transactional
    public SupplierPerformanceDTO recalculateSupplier(Integer supplierId) {
        Supplier supplier = supplierRepository.findById(supplierId)
                .orElseThrow(() -> new RuntimeException("Supplier not found: " + supplierId));

        SupplierPerformance performance = calculateAndSavePerformance(supplier);
        return SupplierPerformanceDTO.fromEntity(performance);
    }

    /**
     * Find the best supplier from a list of supplier IDs
     * Returns the supplier with the highest overall score
     */
    public Supplier findBestSupplier(List<Integer> supplierIds) {
        if (supplierIds == null || supplierIds.isEmpty()) {
            return null;
        }

        Double bestScore = -1.0;
        Supplier bestSupplier = null;

        for (Integer supplierId : supplierIds) {
            Optional<SupplierPerformance> perf = performanceRepository.findBySupplierId(supplierId);
            if (perf.isPresent() && perf.get().getOverallScore() != null) {
                if (perf.get().getOverallScore() > bestScore) {
                    bestScore = perf.get().getOverallScore();
                    bestSupplier = perf.get().getSupplier();
                }
            }
        }

        // If no performance data, return first supplier
        if (bestSupplier == null && !supplierIds.isEmpty()) {
            return supplierRepository.findById(supplierIds.get(0)).orElse(null);
        }

        return bestSupplier;
    }

    // === Private Methods ===

    /**
     * Calculate and save performance metrics for a supplier
     */
    private SupplierPerformance calculateAndSavePerformance(Supplier supplier) {
        // Get or create performance record
        SupplierPerformance performance = performanceRepository.findBySupplier(supplier)
                .orElse(SupplierPerformance.builder()
                        .supplier(supplier)
                        .createdAt(LocalDateTime.now())
                        .build());

        // Get orders for this supplier
        List<Order> allOrders = orderRepository.findBySupplierId(supplier.getId());
        List<Order> completedOrders = orderRepository.findCompletedOrdersBySupplierId(supplier.getId());

        // Calculate counters
        int totalOrders = allOrders.size();
        int completedCount = completedOrders.size();

        // Calculate on-time delivery
        int onTimeCount = 0;
        int lateCount = 0;
        double totalLeadTimeDays = 0;
        int leadTimeCount = 0;
        double totalQualityRatingSum = 0;
        int qualityRatingsCount = 0;

        for (Order order : completedOrders) {
            // Check on-time delivery
            if (isOnTimeDelivery(order)) {
                onTimeCount++;
            } else if (order.getActualDeliveryDate() != null && order.getExpectedDeliveryDate() != null) {
                lateCount++;
            }

            // Calculate lead time
            Double leadTime = calculateLeadTimeDays(order);
            if (leadTime != null) {
                totalLeadTimeDays += leadTime;
                leadTimeCount++;
            }

            // Collect quality ratings
            if (order.getQualityRating() != null && order.getQualityRating() >= 1 && order.getQualityRating() <= 5) {
                totalQualityRatingSum += order.getQualityRating();
                qualityRatingsCount++;
            }
        }

        // Update counters
        performance.setTotalOrders(totalOrders);
        performance.setCompletedOrders(completedCount);
        performance.setOnTimeOrders(onTimeCount);
        performance.setLateOrders(lateCount);
        performance.setTotalQualityRatingSum(totalQualityRatingSum);
        performance.setQualityRatingsCount(qualityRatingsCount);

        // Calculate KPIs
        Double onTimeDeliveryRate = calculateOnTimeDeliveryRate(onTimeCount, completedCount);
        Double avgLeadTimeDays = leadTimeCount > 0 ? totalLeadTimeDays / leadTimeCount : null;
        Double qualityRate = calculateQualityRate(totalQualityRatingSum, qualityRatingsCount);
        Double leadTimeScore = calculateLeadTimeScore(avgLeadTimeDays);

        performance.setOnTimeDeliveryRate(onTimeDeliveryRate);
        performance.setAvgLeadTimeDays(avgLeadTimeDays);
        performance.setQualityRate(qualityRate);
        performance.setLeadTimeScore(leadTimeScore);

        // Calculate overall score
        Double overallScore = calculateOverallScore(onTimeDeliveryRate, qualityRate, leadTimeScore);
        performance.setOverallScore(overallScore);

        performance.setLastCalculatedAt(LocalDateTime.now());

        return performanceRepository.save(performance);
    }

    /**
     * Check if an order was delivered on time
     */
    private boolean isOnTimeDelivery(Order order) {
        if (order.getActualDeliveryDate() == null || order.getExpectedDeliveryDate() == null) {
            return false;
        }

        try {
            LocalDate actual = parseDate(order.getActualDeliveryDate());
            LocalDate expected = parseDate(order.getExpectedDeliveryDate());

            return actual != null && expected != null && !actual.isAfter(expected);
        } catch (Exception e) {
            log.debug("Error parsing dates for order {}: {}", order.getId(), e.getMessage());
            return false;
        }
    }

    /**
     * Calculate lead time in days (order date to actual delivery)
     */
    private Double calculateLeadTimeDays(Order order) {
        if (order.getDate() == null || order.getActualDeliveryDate() == null) {
            return null;
        }

        try {
            LocalDate orderDate = parseDate(order.getDate());
            LocalDate deliveryDate = parseDate(order.getActualDeliveryDate());

            if (orderDate != null && deliveryDate != null) {
                long days = ChronoUnit.DAYS.between(orderDate, deliveryDate);
                return days >= 0 ? (double) days : null;
            }
        } catch (Exception e) {
            log.debug("Error calculating lead time for order {}: {}", order.getId(), e.getMessage());
        }

        return null;
    }

    /**
     * Parse date string to LocalDate (handles both date and datetime formats)
     */
    private LocalDate parseDate(String dateString) {
        if (dateString == null || dateString.isBlank()) {
            return null;
        }

        try {
            // Try date-time format first (yyyy-MM-dd HH:mm)
            if (dateString.contains(" ")) {
                return LocalDate.parse(dateString.substring(0, 10), DATE_FORMATTER);
            }
            // Try date format (yyyy-MM-dd)
            return LocalDate.parse(dateString, DATE_FORMATTER);
        } catch (Exception e) {
            log.debug("Could not parse date: {}", dateString);
            return null;
        }
    }

    /**
     * Calculate on-time delivery rate (0-100%)
     */
    private Double calculateOnTimeDeliveryRate(int onTimeCount, int completedCount) {
        if (completedCount == 0) {
            return null;
        }
        return (double) onTimeCount / completedCount * 100;
    }

    /**
     * Calculate quality rate from average rating (0-100%)
     * Formula: (avgRating / 5) * 100
     */
    private Double calculateQualityRate(double totalRatingSum, int ratingsCount) {
        if (ratingsCount == 0) {
            return null;
        }
        double avgRating = totalRatingSum / ratingsCount;
        return (avgRating / 5) * 100;
    }

    /**
     * Calculate lead time score (0-100)
     * Formula: max(0, 100 - (avgLeadTimeDays - 7) * 3)
     * Perfect score (100) for ≤7 days, decreases by 3 per additional day
     */
    private Double calculateLeadTimeScore(Double avgLeadTimeDays) {
        if (avgLeadTimeDays == null) {
            return null;
        }

        double score = 100 - (avgLeadTimeDays - 7) * 3;
        return Math.max(0, Math.min(100, score));
    }

    /**
     * Calculate overall score (0-100)
     * Formula: (onTimeRate * 0.5) + (qualityRate * 0.3) + (leadTimeScore * 0.2)
     */
    private Double calculateOverallScore(Double onTimeDeliveryRate, Double qualityRate, Double leadTimeScore) {
        // Need at least on-time delivery rate to calculate overall score
        if (onTimeDeliveryRate == null) {
            return null;
        }

        double score = 0;
        double totalWeight = 0;

        // On-time delivery rate (weight: 0.5)
        score += onTimeDeliveryRate * 0.5;
        totalWeight += 0.5;

        // Quality rate (weight: 0.3)
        if (qualityRate != null) {
            score += qualityRate * 0.3;
            totalWeight += 0.3;
        }

        // Lead time score (weight: 0.2)
        if (leadTimeScore != null) {
            score += leadTimeScore * 0.2;
            totalWeight += 0.2;
        }

        // Normalize if some components are missing
        if (totalWeight < 1.0 && totalWeight > 0) {
            score = score / totalWeight;
        }

        return Math.round(score * 100) / 100.0; // Round to 2 decimal places
    }
}
