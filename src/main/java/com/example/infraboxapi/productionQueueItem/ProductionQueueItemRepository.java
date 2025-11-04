package com.example.infraboxapi.productionQueueItem;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface ProductionQueueItemRepository extends JpaRepository<ProductionQueueItem, Integer> {

    // --- POCZĄTEK ZMIANY ---
    // Dodajemy DISTINCT, aby uniknąć duplikowania wierszy rodzica, gdy ma wiele dzieci.
    // Pobieramy również rezerwację materiału wraz z zagnieżdżonymi danymi
    @Query("SELECT DISTINCT p FROM ProductionQueueItem p " +
           "LEFT JOIN FETCH p.files f " +
           "LEFT JOIN FETCH p.materialReservation mr " +
           "LEFT JOIN FETCH mr.material m " +
           "LEFT JOIN FETCH m.materialGroup mg " +
           "LEFT JOIN FETCH mg.materialType mt " +
           "LEFT JOIN FETCH mr.customMaterialType cmt " +
           "WHERE p.queueType = :queueType")
    Page<ProductionQueueItem> findByQueueTypeWithFiles(@Param("queueType") String queueType, Pageable pageable);
    // --- KONIEC ZMIANY ---

    @Query("SELECT COALESCE(MAX(p.order), -1) FROM ProductionQueueItem p WHERE p.queueType = :queueType")
    Integer findMaxOrderByQueueType(String queueType);

    // --- POCZĄTEK ZMIANY ---
    // Tutaj również dodajemy DISTINCT z tego samego powodu.
    @Query("SELECT DISTINCT p FROM ProductionQueueItem p LEFT JOIN FETCH p.files f WHERE p.id = :id ORDER BY f.order ASC")
    Optional<ProductionQueueItem> findByIdWithFiles(@Param("id") Integer id);

    // Query dla generatora kolejki - pobiera programy z plikami i rezerwacją materiału
    @Query("SELECT DISTINCT p FROM ProductionQueueItem p " +
           "LEFT JOIN FETCH p.files f " +
           "LEFT JOIN FETCH p.materialReservation mr " +
           "LEFT JOIN FETCH mr.material m " +
           "LEFT JOIN FETCH m.materialGroup mg " +
           "LEFT JOIN FETCH mg.materialType mt " +
           "LEFT JOIN FETCH mr.customMaterialType cmt " +
           "WHERE p.queueType = :queueType")
    List<ProductionQueueItem> findByQueueTypeWithFilesAndMaterial(@Param("queueType") String queueType);
    // --- KONIEC ZMIANY ---

    @Query("SELECT DISTINCT f.fileName FROM ProductionQueueItem p JOIN p.files f WHERE p.orderName = :orderName AND p.partName = :partName")
    Set<String> findFileNamesByOrderNameAndPartName(String orderName, String partName);

    // Dodajemy tę metodę, aby serwis nadal miał dostęp do prostego zapytania bez złączania plików.
    Page<ProductionQueueItem> findByQueueType(String queueType, Pageable pageable);
}