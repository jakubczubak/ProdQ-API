package com.example.prodqapi.FileImage;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FileImageRepository extends JpaRepository<FileImage, Long> {
}
