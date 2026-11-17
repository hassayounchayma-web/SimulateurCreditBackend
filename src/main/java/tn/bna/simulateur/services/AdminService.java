package tn.bna.simulateur.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tn.bna.simulateur.entities.SimulationHistory;
import tn.bna.simulateur.entities.GestionUser.User;
import tn.bna.simulateur.repositories.GestionUser.UserRepository;
import tn.bna.simulateur.repositories.SimulationHistoryRepository;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminService {

    private final UserRepository userRepository;
    private final SimulationHistoryRepository historyRepository;

    public List<Map<String, Object>> listUsers() {
        return userRepository.findAll().stream().map(u -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", u.getId());
            m.put("nom", u.getNom());
            m.put("prenom", u.getPrenom());
            m.put("email", u.getEmail());
            m.put("cin", u.getCin());
            m.put("telephone", u.getTelephone());
            m.put("role", u.getRole());
            // ne jamais renvoyer password
            return m;
        }).collect(Collectors.toList());
    }

    public List<SimulationHistory> listAllSimulations() {
        return historyRepository.findAllByOrderByCreatedAtDesc();
    }

    public SimulationHistory getSimulation(Long id) {
        return historyRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Simulation introuvable"));
    }

    public Map<String, Object> stats() {
        List<SimulationHistory> all = historyRepository.findAll();
        Map<String, Long> byType = all.stream()
                .collect(Collectors.groupingBy(
                        s -> s.getTypeSimulateur() != null ? s.getTypeSimulateur() : "?",
                        Collectors.counting()));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("totalUsers", userRepository.count());
        m.put("totalSimulations", all.size());
        m.put("byType", byType);
        return m;
    }
}