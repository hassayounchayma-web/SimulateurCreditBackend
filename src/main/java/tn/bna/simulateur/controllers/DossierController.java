package tn.bna.simulateur.controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import tn.bna.simulateur.entities.Dossier;
import tn.bna.simulateur.services.DossierService;

import java.util.List;

@RestController
@RequestMapping("/api/dossiers")
@RequiredArgsConstructor
public class DossierController {

    private final DossierService dossierService;

    @GetMapping
    public ResponseEntity<List<Dossier>> list(Authentication authentication) {
        return ResponseEntity.ok(dossierService.list(authentication.getName()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Dossier> get(Authentication authentication, @PathVariable Long id) {
        return ResponseEntity.ok(dossierService.get(authentication.getName(), id));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Dossier> create(
            Authentication authentication,
            @RequestParam String simulationJson,
            @RequestParam(defaultValue = "false") boolean consentementTraitementOcr,
            @RequestParam(required = false) String typeVendeur,
            @RequestParam(defaultValue = "false") boolean revenusSupplementairesDeclares,
            @RequestParam(required = false) List<String> typeDocuments,
            @RequestParam(required = false) List<MultipartFile> documents,
            @RequestParam(required = false) MultipartFile cin,
            @RequestParam(required = false) MultipartFile bulletinSalaire,
            @RequestParam(required = false) MultipartFile releveBancaire
    ) {
        return ResponseEntity.ok(dossierService.create(
                authentication.getName(), simulationJson,
                consentementTraitementOcr, typeVendeur, revenusSupplementairesDeclares,
                typeDocuments, documents, cin, bulletinSalaire, releveBancaire
        ));
    }

    @PostMapping(value = "/{id}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Dossier> replaceDocument(
            Authentication authentication,
            @PathVariable Long id,
            @RequestParam String typeDocument,
            @RequestParam(defaultValue = "false") boolean consentementTraitementOcr,
            @RequestParam MultipartFile file
    ) {
        return ResponseEntity.ok(dossierService.replaceDocument(authentication.getName(), id,
                typeDocument, consentementTraitementOcr, file));
    }

    @PostMapping("/{id}/reanalyze")
    public ResponseEntity<Dossier> reanalyze(Authentication authentication, @PathVariable Long id) {
        return ResponseEntity.ok(dossierService.reanalyzeOwnDocuments(authentication.getName(), id));
    }
}
