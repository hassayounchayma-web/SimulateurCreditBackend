package tn.bna.simulateur.controllers;

import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import tn.bna.simulateur.entities.*;
import tn.bna.simulateur.services.SimulateurService;

import java.util.Map;

@RestController
@RequestMapping("/api/simulateur")
@Validated
@CrossOrigin(origins = "http://localhost:4300")
public class SimulateurController {

    @Autowired
    private SimulateurService service;

    @PostMapping("/sans-rachat")
    public ResponseEntity<SimulationResult> sansRachat(@Valid @RequestBody SimulationSansRachatRequest request) {
        SimulationResult result = service.simulateSansRachat(request);
        return result.getError() != null ? ResponseEntity.badRequest().body(result) : ResponseEntity.ok(result);
    }

    @PostMapping("/rachat-externe")
    public ResponseEntity<SimulationResult> rachatExterne(@Valid @RequestBody SimulationRachatExterneRequest request) {
        SimulationResult result = service.simulateRachatExterne(request);
        return result.getError() != null ? ResponseEntity.badRequest().body(result) : ResponseEntity.ok(result);
    }

    @PostMapping("/rachat-interne")
    public ResponseEntity<SimulationResult> rachatInterne(@Valid @RequestBody SimulationRachatInterneRequest request) {
        SimulationResult result = service.simulateRachatInterne(request);
        return result.getError() != null ? ResponseEntity.badRequest().body(result) : ResponseEntity.ok(result);
    }



}