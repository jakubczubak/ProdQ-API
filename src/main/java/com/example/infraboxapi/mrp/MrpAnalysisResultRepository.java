package com.example.infraboxapi.mrp;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface MrpAnalysisResultRepository extends JpaRepository<MrpAnalysisResult, Integer> {

    /**
     * Find all active (non-resolved) analyses
     */
    List<MrpAnalysisResult> findByStatusNot(MrpAnalysisStatus status);

    /**
     * Find analyses by priority
     */
    List<MrpAnalysisResult> findByPriorityOrderByAnalyzedAtDesc(MrpPriority priority);

    /**
     * Find analyses by resource type
     */
    List<MrpAnalysisResult> findByResourceTypeOrderByPriorityAscAnalyzedAtDesc(ResourceType resourceType);

    /**
     * Find analyses by status
     */
    List<MrpAnalysisResult> findByStatusOrderByPriorityAscAnalyzedAtDesc(MrpAnalysisStatus status);

    /**
     * Find active analyses ordered by priority
     */
    @Query("SELECT a FROM MrpAnalysisResult a " +
           "WHERE a.status IN ('PENDING', 'DRAFT_CREATED') " +
           "ORDER BY a.priority ASC, a.earliestNeedDate ASC NULLS LAST")
    List<MrpAnalysisResult> findActiveAnalysesOrderedByPriority();

    /**
     * Find analyses for a specific resource
     */
    List<MrpAnalysisResult> findByResourceTypeAndResourceIdOrderByAnalyzedAtDesc(
            ResourceType resourceType, Integer resourceId);

    /**
     * Count analyses by priority (for dashboard)
     */
    long countByPriorityAndStatusNot(MrpPriority priority, MrpAnalysisStatus excludeStatus);

    /**
     * Find analyses without suggestion group (orphans)
     */
    List<MrpAnalysisResult> findBySuggestionGroupIsNullAndStatus(MrpAnalysisStatus status);

    /**
     * Delete old resolved analyses (cleanup)
     */
    @Modifying
    @Query("DELETE FROM MrpAnalysisResult a WHERE a.status = 'RESOLVED' AND a.resolvedAt < :beforeDate")
    int deleteResolvedBefore(@Param("beforeDate") LocalDateTime beforeDate);

    /**
     * Mark analyses as resolved for a specific resource
     */
    @Modifying
    @Query("UPDATE MrpAnalysisResult a SET a.status = 'RESOLVED', a.resolvedAt = :resolvedAt " +
           "WHERE a.resourceType = :resourceType AND a.resourceId = :resourceId " +
           "AND a.status IN ('PENDING', 'DRAFT_CREATED')")
    int resolveByResource(
            @Param("resourceType") ResourceType resourceType,
            @Param("resourceId") Integer resourceId,
            @Param("resolvedAt") LocalDateTime resolvedAt);
}
