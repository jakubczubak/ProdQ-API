package com.example.infraboxapi.material;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


@Repository
public interface MaterialRepository extends JpaRepository<Material, Integer> {

}