package tn.bna.simulateur.controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.bna.simulateur.dto.SaveSimulationRequest;
import tn.bna.simulateur.entities.SimulationHistory;
import tn.bna.simulateur.services.SimulationHistoryService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/history")
@RequiredArgsConstructor
@CrossOrigin(origins = {"http://localhost:4200", "http://localhost:4300"})
public class SimulationHistoryController {

    private final SimulationHistoryService historyService;

    private Long currentUserId(@RequestHeader(value = "X-User-Id", required = false) Long userId) {
        return userId != null ? userId : 1L; // temporaire → remplacer par JWT
    }

    @PostMapping
    public ResponseEntity<SimulationHistory> save(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestBody SaveSimulationRequest request) {
        return ResponseEntity.ok(historyService.save(currentUserId(userId), request));
    }

    @GetMapping
    public ResponseEntity<List<SimulationHistory>> list(
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        return ResponseEntity.ok(historyService.listByUser(currentUserId(userId)));
    }

    @PostMapping("/compare")
    public ResponseEntity<List<SimulationHistory>> compare(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestBody Map<String, List<Long>> body) {
        return ResponseEntity.ok(historyService.compare(currentUserId(userId), body.get("ids")));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @PathVariable Long id) {
        historyService.delete(currentUserId(userId), id);
        return ResponseEntity.noContent().build();
    }
}