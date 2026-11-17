package tn.bna.simulateur.controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ContentDisposition;
import org.springframework.web.bind.annotation.*;
import tn.bna.simulateur.entities.SimulationHistory;
import tn.bna.simulateur.entities.GestionUser.User;
import tn.bna.simulateur.services.AdminService;
import tn.bna.simulateur.dto.dossier.DocumentReviewRequest;
import tn.bna.simulateur.dto.dossier.PromoterReviewRequest;
import tn.bna.simulateur.services.DossierService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@CrossOrigin(origins = {"http://localhost:4300", "http://localhost:4200"})
public class AdminController {

    private final AdminService adminService;
    private final DossierService dossierService;

    @GetMapping("/users")
    public ResponseEntity<List<Map<String, Object>>> users() {
        return ResponseEntity.ok(adminService.listUsers());
    }

    @GetMapping("/simulations")
    public ResponseEntity<List<SimulationHistory>> simulations() {
        return ResponseEntity.ok(adminService.listAllSimulations());
    }

    @GetMapping("/simulations/{id}")
    public ResponseEntity<SimulationHistory> simulation(@PathVariable Long id) {
        return ResponseEntity.ok(adminService.getSimulation(id));
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> stats() {
        return ResponseEntity.ok(adminService.stats());
    }

    @GetMapping("/dossiers")
    public ResponseEntity<List<tn.bna.simulateur.entities.Dossier>> dossiers() {
        return ResponseEntity.ok(dossierService.listAllForAdmin());
    }

    @GetMapping("/dossiers/{id}")
    public ResponseEntity<Map<String, Object>> dossier(@PathVariable Long id) {
        return ResponseEntity.ok(dossierService.getAdminDetail(id));
    }

    @GetMapping("/dossiers/{id}/documents/{documentId}/file")
    public ResponseEntity<byte[]> documentFile(@PathVariable Long id, @PathVariable Long documentId) {
        var document = dossierService.getDocumentForAdmin(id, documentId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(document.getTypeMime()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(document.getNomFichier()).build().toString())
                .body(dossierService.loadOriginalDocument(id, documentId));
    }

    @PatchMapping("/dossiers/{id}/documents/{documentId}/review")
    public ResponseEntity<Map<String, Object>> reviewDocument(
            org.springframework.security.core.Authentication authentication,
            @PathVariable Long id,
            @PathVariable Long documentId,
            @RequestBody DocumentReviewRequest request
    ) {
        return ResponseEntity.ok(dossierService.reviewDocument(id, documentId,
                request.statut(), request.motif(), request.corrections(), authentication.getName()));
    }

    @PostMapping("/dossiers/{id}/documents/{documentId}/reanalyze")
    public ResponseEntity<Map<String, Object>> reanalyzeDocument(
            org.springframework.security.core.Authentication authentication,
            @PathVariable Long id,
            @PathVariable Long documentId
    ) {
        return ResponseEntity.ok(dossierService.reanalyzeDocument(id, documentId, authentication.getName()));
    }

    @PatchMapping("/dossiers/{id}/promoteur-agrement")
    public ResponseEntity<tn.bna.simulateur.entities.Dossier> reviewPromoter(
            @PathVariable Long id,
            @RequestBody PromoterReviewRequest request
    ) {
        return ResponseEntity.ok(dossierService.reviewPromoter(id, request.statut(), request.motif()));
    }
}
