package com.example.infraboxapi.tool;


import com.example.infraboxapi.common.CommonService;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/tool/")
@AllArgsConstructor
public class ToolController {

    private final ToolService toolService;
    private final CommonService commonService;


    @PostMapping("/create")
    public ResponseEntity<String> createTool(@Valid @RequestBody ToolDTO toolDTO, BindingResult bindingResult) {

        if (bindingResult.hasErrors()) {
            return commonService.handleBindingResult(bindingResult);
        }

        try {
            toolService.createTool(toolDTO);
            return ResponseEntity.ok("Tool created");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error creating tool: " + e.getMessage());
        }
    }

    @PutMapping("/update")
    public ResponseEntity<String> updateTool(@Valid @RequestBody ToolDTO toolDTO, BindingResult bindingResult) {

        if (bindingResult.hasErrors()) {
            return commonService.handleBindingResult(bindingResult);
        }

        try {
            toolService.updateTool(toolDTO);
            return ResponseEntity.ok("Tool updated");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error updating tool: " + e.getMessage());
        }
    }

    @DeleteMapping("/delete/{id}")
    public ResponseEntity<String> deleteTool(@PathVariable Integer id) {

        try {
            toolService.deleteTool(id);
            return ResponseEntity.ok("Tool deleted");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error deleting tool: " + e.getMessage());
        }
    }


}
