package com.example.infraboxapi.productionQueueItem;

import com.example.infraboxapi.common.CommonService;
import jakarta.validation.Valid;
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
        return ResponseEntity.ok(machineService.getAvailableLocations());
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