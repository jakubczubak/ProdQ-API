package com.example.infraboxapi.productionQueueItem;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MachineRepository extends JpaRepository<Machine, Integer> {
    List<Machine> findByProgramPath(String programPath);
    Optional<Machine> findByQueueFilePath(String queueFilePath);
    boolean existsByMachineName(String machineName);
}