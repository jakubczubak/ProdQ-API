package com.example.prodqapi.materialReservation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MaterialReservationRepository extends JpaRepository<MaterialReservation, Integer> {

    Optional<MaterialReservation> findByProductionQueueItemId(Integer productionQueueItemId);

    List<MaterialReservation> findByMaterialIdAndStatus(Integer materialId, ReservationStatus status);

    @Query("SELECT COALESCE(SUM(COALESCE(mr.reservedQuantity, 0.0) + COALESCE(mr.reservedLength, 0.0)), 0.0) FROM MaterialReservation mr " +
           "WHERE mr.material.id = :materialId " +
           "AND mr.status = :status " +
           "AND (:excludeReservationId IS NULL OR mr.id != :excludeReservationId)")
    Double sumReservedQuantity(
        @Param("materialId") Integer materialId,
        @Param("status") ReservationStatus status,
        @Param("excludeReservationId") Integer excludeReservationId
    );

    @Query("SELECT mr FROM MaterialReservation mr " +
           "LEFT JOIN FETCH mr.material m " +
           "LEFT JOIN FETCH m.materialPriceHistoryList " +
           "LEFT JOIN FETCH mr.customMaterialType " +
           "LEFT JOIN FETCH mr.productionQueueItem pqi " +
           "WHERE mr.material.id = :materialId " +
           "AND mr.status = 'RESERVED'")
    List<MaterialReservation> findReservationsWithDetailsForMaterial(@Param("materialId") Integer materialId);
}