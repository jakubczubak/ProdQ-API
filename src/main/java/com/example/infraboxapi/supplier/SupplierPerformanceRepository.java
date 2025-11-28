package com.example.infraboxapi.supplier;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SupplierPerformanceRepository extends JpaRepository<SupplierPerformance, Integer> {

    /**
     * Find performance record by supplier ID
     */
    Optional<SupplierPerformance> findBySupplierId(Integer supplierId);

    /**
     * Find performance record by supplier
     */
    Optional<SupplierPerformance> findBySupplier(Supplier supplier);

    /**
     * Get all supplier performances ordered by overall score descending (best first)
     */
    @Query("SELECT sp FROM SupplierPerformance sp ORDER BY sp.overallScore DESC NULLS LAST")
    List<SupplierPerformance> findAllOrderByOverallScoreDesc();

    /**
     * Get all supplier performances with score above threshold
     */
    @Query("SELECT sp FROM SupplierPerformance sp WHERE sp.overallScore >= :minScore ORDER BY sp.overallScore DESC")
    List<SupplierPerformance> findByOverallScoreGreaterThanEqual(@Param("minScore") Double minScore);

    /**
     * Get top N suppliers by overall score
     */
    @Query("SELECT sp FROM SupplierPerformance sp WHERE sp.overallScore IS NOT NULL ORDER BY sp.overallScore DESC")
    List<SupplierPerformance> findTopSuppliers();

    /**
     * Check if performance record exists for supplier
     */
    boolean existsBySupplierId(Integer supplierId);

    /**
     * Delete performance record by supplier ID
     */
    void deleteBySupplierId(Integer supplierId);
}
