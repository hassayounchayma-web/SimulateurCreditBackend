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


    @PostMapping("/credit-perso")
    public SimulationResult creditPerso(@RequestBody Map<String, Object> request) {
        return service.simulateCreditPerso(request);
    }
    @PostMapping("/credit-auto")
    public SimulationResult creditAuto(@RequestBody Map<String, Object> request) {
        return service.simulateCreditAuto(request);
    }
    @PostMapping("/credit-immo")
    public SimulationResult creditImmo(@RequestBody Map<String, Object> request) {
        return service.simulateCreditImmo(request);
    }
    @PostMapping("/twensa")
    public SimulationResult twensa(@RequestBody Map<String, Object> request) {
        return service.simulateTwensa(request);
    }

    @PostMapping("/premier-logement")
    public SimulationResult premierLogement(@RequestBody Map<String, Object> request) {
        return service.simulatePremierLogement(request);
    }
    @PostMapping("/creation-projets")
    public SimulationResult creationProjets(@RequestBody Map<String, Object> request) {
        return service.simulateCreationProjets(request);
    }
    @PostMapping("/extension-developpement")
    public SimulationResult extensionDeveloppement(@RequestBody Map<String, Object> request) {
        return service.simulateExtensionDeveloppement(request);
    }
    @PostMapping("/caution-bna")
    public SimulationResult cautionBna(@RequestBody Map<String, Object> request) {
        return service.simulateCautionBna(request);
    }
    @PostMapping("/besoins-exploitation")
    public SimulationResult besoinsExploitation(@RequestBody Map<String, Object> request) {
        return service.simulateBesoinsExploitation(request);
    }
    @PostMapping("/credits-campagne")
    public SimulationResult creditsCampagne(@RequestBody Map<String, Object> request) {
        return service.simulateCreditsCampagne(request);
    }

    @PostMapping("/credits-investissement")
    public SimulationResult creditsInvestissement(@RequestBody Map<String, Object> request) {
        return service.simulateCreditsInvestissement(request);
    }
    @PostMapping("/quroodh-mawsimiya")
    public SimulationResult quroodhMawsimiya(@RequestBody Map<String, Object> request) {
        return service.simulateCreditsCampagne(request); // même logique
    }
    @PostMapping("/quroodh-istithmar")
    public SimulationResult quroodhIstithmar(@RequestBody Map<String, Object> request) {
        return service.simulateCreditsInvestissement(request);
    }
    @PostMapping("/credits-supervises")
    public SimulationResult creditsSupervises(@RequestBody Map<String, Object> request) {
        return service.simulateCreditsSupervises(request);
    }
    @PostMapping("/quroodh-muattara")
    public SimulationResult quroodhMuattara(@RequestBody Map<String, Object> request) {
        return service.simulateCreditsSupervises(request);
    }
    @PostMapping("/chaines-valeur")
    public SimulationResult chainesValeur(@RequestBody Map<String, Object> request) {
        return service.simulateChainesValeur(request);
    }
    @PostMapping("/credit-jinene")
    public SimulationResult creditJinene(@RequestBody Map<String, Object> request) {
        return service.simulateCreditJinene(request);
    }
    @PostMapping("/pro-tresorerie")
    public SimulationResult proTresorerie(@RequestBody Map<String, Object> request) {
        return service.simulateProTresorerie(request);
    }
    @PostMapping("/pro-stocks")
    public SimulationResult proStocks(@RequestBody Map<String, Object> request) {
        return service.simulateProStocks(request);
    }

    @PostMapping("/pro-marches-tn")
    public SimulationResult proMarchesTn(@RequestBody Map<String, Object> request) {
        return service.simulateProMarchesTn(request);
    }

    @PostMapping("/pro-export")
    public SimulationResult proExport(@RequestBody Map<String, Object> request) {
        return service.simulateProExport(request);
    }

    @PostMapping("/pro-investissement")
    public SimulationResult proInvestissement(@RequestBody Map<String, Object> request) {
        return service.simulateProInvestissement(request);
    }

    @PostMapping("/pro-cautions")
    public SimulationResult proCautions(@RequestBody Map<String, Object> request) {
        return service.simulateProCautions(request);
    }
}