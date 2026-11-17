package tn.bna.simulateur.controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import tn.bna.simulateur.dto.dossier.ChecklistRequest;
import tn.bna.simulateur.dto.dossier.ChecklistResponse;
import tn.bna.simulateur.services.DocumentChecklistService;

@RestController
@RequestMapping("/api/dossiers")
@RequiredArgsConstructor
public class DocumentChecklistController {
    private final DocumentChecklistService checklistService;

    @PostMapping("/checklist")
    public ResponseEntity<ChecklistResponse> checklist(Authentication authentication,
                                                         @RequestBody ChecklistRequest request) {
        return ResponseEntity.ok(checklistService.generate(
                request.simulationJson(), request.typeVendeur(), request.revenusSupplementairesDeclares()));
    }
}
