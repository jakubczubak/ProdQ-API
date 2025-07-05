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

        @GetMapping("/migrate-files")
    public ResponseEntity<String> runMigration() {
        try {
            migration.migrateFileContentToDisk();
            return ResponseEntity.ok("Migration completed successfully");
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Migration failed: " + e.getMessage());
        }
    }
}