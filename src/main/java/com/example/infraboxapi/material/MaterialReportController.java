package com.example.infraboxapi.material;

import com.example.infraboxapi.FilePDF.FilePDF;
import com.example.infraboxapi.FilePDF.FilePDFService;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/material_reports")
public class MaterialReportController {

    private final FilePDFService filePDFService;

    public MaterialReportController(FilePDFService filePDFService) {
        this.filePDFService = filePDFService;
    }

    @GetMapping("/{id}")
    public ResponseEntity<Resource> downloadReport(@PathVariable Long id) {
        FilePDF filePDF = filePDFService.findById(id)
                .orElse(null);

        if (filePDF == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
        }

        Resource resource = new ByteArrayResource(filePDF.getPdfData());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filePDF.getName() + "\"")
                .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate")
                .header(HttpHeaders.PRAGMA, "no-cache")
                .header(HttpHeaders.EXPIRES, "0")
                .contentLength(filePDF.getPdfData().length)
                .contentType(MediaType.APPLICATION_PDF)
                .body(resource);
    }
}