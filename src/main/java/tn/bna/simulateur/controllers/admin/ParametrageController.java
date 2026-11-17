
package tn.bna.simulateur.controllers.admin;

import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import tn.bna.simulateur.dto.admin.ParametrageResponse;
import tn.bna.simulateur.dto.admin.UpdateMargeRequest;
import tn.bna.simulateur.dto.admin.UpdateTmmRequest;

import tn.bna.simulateur.entities.admin.ParametreMarge;
import tn.bna.simulateur.entities.admin.ParametreTmm;

import tn.bna.simulateur.services.admin.ParametrageService;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ParametrageController {

    private final ParametrageService parametrageService;


    @GetMapping("/parametrage")
    public ResponseEntity<ParametrageResponse> getParametrage() {

        ParametrageResponse response =
                parametrageService.getParametrage();

        return ResponseEntity.ok(response);
    }

    @GetMapping("/parametrage/tmm")
    public ResponseEntity<ParametreTmm> getTmm() {

        ParametreTmm tmm = parametrageService.getTmm();

        return ResponseEntity.ok(tmm);
    }

    @GetMapping("/parametrage/marges")
    public ResponseEntity<List<ParametreMarge>> getMarges(
            @RequestParam(required = false) String scenario) {

        List<ParametreMarge> marges =
                parametrageService.getMarges(scenario);

        return ResponseEntity.ok(marges);
    }

    // ==================================================
    // 4. MODIFIER LE TMM - ADMIN UNIQUEMENT
    // ==================================================

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/admin/parametrage/tmm")
    public ResponseEntity<ParametreTmm> modifierTmm(
            @RequestBody UpdateTmmRequest request) {

        ParametreTmm tmmModifie =
                parametrageService.modifierTmm(
                        request.getValeur()
                );

        return ResponseEntity.ok(tmmModifie);
    }

    // ==================================================
    // 5. MODIFIER UNE MARGE - ADMIN UNIQUEMENT
    // ==================================================

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/admin/parametrage/marges/{id}")
    public ResponseEntity<ParametreMarge> modifierMarge(
            @PathVariable Long id,
            @RequestBody UpdateMargeRequest request) {

        ParametreMarge margeModifiee =
                parametrageService.modifierMarge(
                        id,
                        request.getMarge()
                );

        return ResponseEntity.ok(margeModifiee);
    }
}
