package tn.bna.simulateur.services.GestionUser;

import tn.bna.simulateur.config.JwtUtil;
import tn.bna.simulateur.dto.AuthResponse;
import tn.bna.simulateur.dto.LoginRequest;
import tn.bna.simulateur.dto.RegisterRequest;
import tn.bna.simulateur.dto.UserResponse;
import tn.bna.simulateur.entities.GestionUser.User;
import tn.bna.simulateur.repositories.GestionUser.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public User register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email déjà utilisé");
        }
        if (userRepository.existsByCin(request.getCin())) {
            throw new RuntimeException("CIN déjà utilisé");
        }

        User user = new User();
        user.setNom(request.getNom());
        user.setPrenom(request.getPrenom());
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setTelephone(request.getTelephone());
        user.setCin(request.getCin());
        user.setRole("CLIENT");

        return userRepository.save(user);
    }

    public Map<String, Object> login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getUsername())
                .or(() -> userRepository.findByCin(request.getUsername()))
                .orElseThrow(() -> new RuntimeException("Utilisateur introuvable"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new RuntimeException("Mot de passe incorrect");
        }

        String subject = user.getEmail() != null ? user.getEmail() : user.getCin();
        String role = user.getRole() != null ? user.getRole() : "USER";

        // ✅ génération du JWT
        String token = jwtUtil.generateToken(subject, role);

        Map<String, Object> body = new HashMap<>();
        body.put("token", token);   // nom exact attendu par le front
        body.put("id", user.getId());
        body.put("email", user.getEmail());
        body.put("role", role);
        body.put("nom", user.getNom());
        body.put("prenom", user.getPrenom());
        return body;
    }
}