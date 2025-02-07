package com.example.infraboxapi.material;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;

@RestController
@RequestMapping("/reports")
public class MaterialReportController {

    private final String pdfDirectory = System.getProperty("user.dir") + "/material_reports/";

    @GetMapping("/{fileName:.+}")
    public ResponseEntity<Resource> downloadReport(@PathVariable String fileName) {
        File file = new File(pdfDirectory + fileName);

        if (!file.exists()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
        }

        Resource resource = new FileSystemResource(file);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.getName() + "\"")
                .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate")  // Wyłączenie cache
                .header(HttpHeaders.PRAGMA, "no-cache")
                .header(HttpHeaders.EXPIRES, "0")
                .contentLength(file.length())  // Dodanie długości pliku
                .contentType(MediaType.APPLICATION_OCTET_STREAM)  // Wymuszenie pobierania
                .body(resource);
    }
}
