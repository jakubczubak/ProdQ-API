package com.example.prodqapi.accessorie;


import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AccessorieReposotory extends JpaRepository<Accessorie, Integer> {
}
