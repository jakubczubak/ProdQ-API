package com.example.infraboxapi.productionQueueItem;

import com.example.infraboxapi.common.CommonService;
import jakarta.validation.Valid;
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
    public ResponseEntity<Machine> addMachine(
            @Valid @ModelAttribute MachineRequest request,
            @RequestParam(value = "image", required = false) MultipartFile image,
            BindingResult bindingResult) throws IOException {
        if (bindingResult.hasErrors()) {
            ResponseEntity<String> errorResponse = commonService.handleBindingResult(bindingResult);
            return ResponseEntity.status(errorResponse.getStatusCode()).body(null);
        }

        Machine machine = machineService.createMachine(request, image);
        return ResponseEntity.ok(machine);
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
    public ResponseEntity<Machine> updateMachine(
            @PathVariable Integer id,
            @Valid @ModelAttribute MachineRequest request,
            @RequestParam(value = "image", required = false) MultipartFile image,
            BindingResult bindingResult) throws IOException {
        if (bindingResult.hasErrors()) {
            ResponseEntity<String> errorResponse = commonService.handleBindingResult(bindingResult);
            return ResponseEntity.status(errorResponse.getStatusCode()).body(null);
        }

        Machine updatedMachine = machineService.updateMachine(id, request, image);
        return ResponseEntity.ok(updatedMachine);
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
}