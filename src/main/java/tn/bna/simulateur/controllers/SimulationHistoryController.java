package tn.bna.simulateur.controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.bna.simulateur.dto.AmortissementLigne;
import tn.bna.simulateur.dto.AmortissementRequest;
import tn.bna.simulateur.dto.SaveSimulationRequest;
import tn.bna.simulateur.entities.SimulationHistory;
import tn.bna.simulateur.services.AmortissementService;
import tn.bna.simulateur.services.SimulationHistoryService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/history")
@RequiredArgsConstructor
public class SimulationHistoryController {

    private final SimulationHistoryService historyService;
    private final AmortissementService amortissementService;

    // En prod: récupérer userId depuis le JWT
    private Long currentUserId(@RequestHeader(value = "X-User-Id", required = false) Long userId) {
        return userId != null ? userId : 1L; // temporaire si pas encore extrait du token
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

    @PostMapping("/amortissement")
    public ResponseEntity<List<AmortissementLigne>> amortissement(@RequestBody AmortissementRequest req) {
        return ResponseEntity.ok(
                amortissementService.generer(req.getMontant(), req.getTauxAnnuel(), req.getDureeMois())
        );
    }
}