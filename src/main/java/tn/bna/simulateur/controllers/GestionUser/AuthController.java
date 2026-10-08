// src/main/java/tn/bna/simulateur/controllers/GestionUser/AuthController.java
package tn.bna.simulateur.controllers.GestionUser;  // ← Vérifier CE package

import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import lombok.RequiredArgsConstructor;
import tn.bna.simulateur.dto.LoginRequest;
import tn.bna.simulateur.dto.RegisterRequest;
import tn.bna.simulateur.services.GestionUser.AuthService;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "http://localhost:4300")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @GetMapping("/test")
    public ResponseEntity<String> test() {
        System.out.println("✅ Endpoint /api/auth/test appelé !");
        return ResponseEntity.ok("✅ Backend fonctionne !");
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest request) {
        authService.register(request);
        return ResponseEntity.ok(Map.of(
                "message", "Inscription réussie !"
        ));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        System.out.println("📝 [BACKEND] Requête login reçue: " + request.getUsername());
        try {
            return ResponseEntity.ok(authService.login(request));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}