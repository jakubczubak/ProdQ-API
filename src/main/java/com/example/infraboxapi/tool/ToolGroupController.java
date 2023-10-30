package com.example.infraboxapi.tool;

import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tool_group/")
@CrossOrigin(origins = "http://localhost:3000")
@AllArgsConstructor
public class ToolGroupController {

    private final ToolGroupService toolGroupService;

    @GetMapping("/get")
    public ResponseEntity<List<ToolGroup>> getToolGroups(){
        try{
            return ResponseEntity.ok(toolGroupService.getToolGroups());
        }catch (Exception e){
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    @PutMapping("/update")
    public ResponseEntity<String> updateToolGroup(@RequestBody ToolGroupDTO toolGroupDTO){
        try{
            toolGroupService.updateToolGroup(toolGroupDTO);
            return ResponseEntity.ok("Tool Group updated");
        }catch (Exception e){
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error updating tool group: " + e.getMessage());
        }
    }

    @PostMapping("/create")
    public ResponseEntity<String> createToolGroup(@RequestBody ToolGroupDTO toolGroupDTO){
        try{
            toolGroupService.createToolGroup(toolGroupDTO);
            return ResponseEntity.ok("Tool Group created");
        }catch (Exception e){
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error creating tool group: " + e.getMessage());
        }
    }

    @DeleteMapping("/delete/{id}")
    public ResponseEntity<String> deleteToolGroup(@PathVariable Integer id){
        try{
            toolGroupService.deleteToolGroup(id);
            return ResponseEntity.ok("Tool Group deleted");
        }catch (Exception e){
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error deleting tool group: " + e.getMessage());
        }
    }

    @GetMapping("/get/{id}")
    public ResponseEntity<ToolGroup> getToolGroup(@PathVariable Integer id){
        try{
            return ResponseEntity.ok(toolGroupService.getToolGroup(id));
        }catch (Exception e){
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }
}
