package com.example.infraboxapi.productionQueueItem;

import com.example.infraboxapi.common.CommonService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/machine")
public class MachineController {

    private static final Logger logger = LoggerFactory.getLogger(MachineController.class);

    private final MachineService machineService;
    private final CommonService commonService;

    public MachineController(MachineService machineService, CommonService commonService) {
        this.machineService = machineService;
        this.commonService = commonService;
    }

    @PostMapping(value = "/add", consumes = {"multipart/form-data"})
    public ResponseEntity<?> addMachine(
            @Valid @ModelAttribute MachineRequest request,
            @RequestParam(value = "image", required = false) MultipartFile image,
            BindingResult bindingResult) throws IOException {
        if (bindingResult.hasErrors()) {
            String errorMessage = commonService.handleBindingResult(bindingResult).getBody();
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                    new ErrorResponse(errorMessage)
            );
        }

        try {
            Machine machine = machineService.createMachine(request, image);
            return ResponseEntity.ok(machine);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                    new ErrorResponse(e.getMessage())
            );
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<Machine> getMachine(@PathVariable Integer id) {
        return machineService.findById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping
    public ResponseEntity<List<Machine>> getAllMachines() {
        return ResponseEntity.ok(machineService.findAll());
    }

    @PutMapping(value = "/{id}", consumes = {"multipart/form-data"})
    public ResponseEntity<?> updateMachine(
            @PathVariable Integer id,
            @Valid @ModelAttribute MachineRequest request,
            @RequestParam(value = "image", required = false) MultipartFile image,
            BindingResult bindingResult) throws IOException {
        if (bindingResult.hasErrors()) {
            String errorMessage = commonService.handleBindingResult(bindingResult).getBody();
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                    new ErrorResponse(errorMessage)
            );
        }

        try {
            Machine updatedMachine = machineService.updateMachine(id, request, image);
            return ResponseEntity.ok(updatedMachine);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                    new ErrorResponse(e.getMessage())
            );
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteMachine(@PathVariable Integer id) {
        machineService.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/image")
    public ResponseEntity<byte[]> getMachineImage(@PathVariable Integer id) {
        return machineService.findById(id)
                .filter(machine -> machine.getImage() != null)
                .map(machine -> ResponseEntity.ok()
                        .header("Content-Disposition", "inline; filename=" + machine.getImage().getName())
                        .contentType(MediaType.parseMediaType(machine.getImage().getType()))
                        .body(machine.getImage().getImageData()))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/available-locations")
    public ResponseEntity<List<String>> getAvailableLocations() {
        try {
            List<String> locations = machineService.getAvailableLocations();
            return ResponseEntity.ok(locations);
        } catch (RuntimeException e) {
            logger.error("Error retrieving available locations", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(List.of("Error fetching locations: " + e.getMessage()));
        }
    }

    @GetMapping("/directory-structure-hash")
    public ResponseEntity<String> getDirectoryStructureHash() {
        try {
            return ResponseEntity.ok(machineService.getDirectoryStructureHash());
        } catch (RuntimeException e) {
            logger.error("Error computing directory structure hash", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error computing directory structure hash: " + e.getMessage());
        }
    }

    @GetMapping("/{id}/download-programs")
    public ResponseEntity<byte[]> downloadMachinePrograms(@PathVariable Integer id) throws IOException {
        return machineService.downloadMachinePrograms(id);
    }

    // Klasa pomocnicza do formatowania odpowiedzi błędu
    private static class ErrorResponse {
        private String message;

        public ErrorResponse(String message) {
            this.message = message;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }
    }
}