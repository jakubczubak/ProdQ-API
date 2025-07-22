package com.example.infraboxapi;

import com.example.infraboxapi.FileContentMigration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/migration")
public class MigrationController {

    private final FileContentMigration migration;

    @Autowired
    public MigrationController(FileContentMigration migration) {
        this.migration = migration;
    }

    @GetMapping("/migrate-files-to-disk")
    public ResponseEntity<String> runMigrationToDisk() {
        try {
            migration.migrateFileContentToDisk();
            return ResponseEntity.ok("Migration to disk completed successfully");
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Migration to disk failed: " + e.getMessage());
        }
    }

    @GetMapping("/migrate-files-from-disk")
    public ResponseEntity<String> runMigrationFromDisk() {
        try {
            migration.migrateFileContentFromDisk();
            return ResponseEntity.ok("Migration from disk completed successfully");
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Migration from disk failed: " + e.getMessage());
        }
    }
}