package com.example.prodqapi.mrp;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface MrpOrderSuggestionGroupRepository extends JpaRepository<MrpOrderSuggestionGroup, Integer> {

    /**
     * Find all pending suggestion groups ordered by priority
     */
    @Query("SELECT g FROM MrpOrderSuggestionGroup g " +
           "LEFT JOIN FETCH g.supplier " +
           "WHERE g.status = 'PENDING' " +
           "ORDER BY g.highestPriority ASC, g.earliestNeedDate ASC NULLS LAST")
    List<MrpOrderSuggestionGroup> findPendingSuggestionsOrderedByPriority();

    /**
     * Find suggestion groups by status
     */
    List<MrpOrderSuggestionGroup> findByStatusOrderByHighestPriorityAscCreatedAtDesc(SuggestionStatus status);

    /**
     * Find suggestion groups by supplier
     */
    List<MrpOrderSuggestionGroup> findBySupplierIdAndStatus(Integer supplierId, SuggestionStatus status);

    /**
     * Find suggestion groups by resource type
     */
    List<MrpOrderSuggestionGroup> findByResourceTypeAndStatus(ResourceType resourceType, SuggestionStatus status);

    /**
     * Count pending suggestions by priority (for dashboard)
     */
    long countByHighestPriorityAndStatus(MrpPriority priority, SuggestionStatus status);

    /**
     * Find suggestion group with analyses loaded
     */
    @Query("SELECT g FROM MrpOrderSuggestionGroup g " +
           "LEFT JOIN FETCH g.analyses " +
           "LEFT JOIN FETCH g.supplier " +
           "WHERE g.id = :id")
    MrpOrderSuggestionGroup findByIdWithAnalyses(@Param("id") Integer id);

    /**
     * Delete old dismissed suggestions (cleanup)
     */
    @Modifying
    @Query("DELETE FROM MrpOrderSuggestionGroup g WHERE g.status = 'DISMISSED' AND g.dismissedAt < :beforeDate")
    int deleteDismissedBefore(@Param("beforeDate") LocalDateTime beforeDate);

    /**
     * Mark all pending suggestions as dismissed before running new analysis
     */
    @Modifying
    @Query("UPDATE MrpOrderSuggestionGroup g SET g.status = 'DISMISSED', g.dismissedAt = :now, " +
           "g.dismissedReason = 'Replaced by new analysis' " +
           "WHERE g.status = 'PENDING'")
    int dismissAllPending(@Param("now") LocalDateTime now);
}
