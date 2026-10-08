package tn.bna.simulateur.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tn.bna.simulateur.dto.SaveSimulationRequest;
import tn.bna.simulateur.entities.SimulationHistory;
import tn.bna.simulateur.repositories.SimulationHistoryRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SimulationHistoryService {

    private final SimulationHistoryRepository repository;

    public SimulationHistory save(Long userId, SaveSimulationRequest req) {
        SimulationHistory h = SimulationHistory.builder()
                .userId(userId)
                .typeSimulateur(req.getTypeSimulateur())
                .libelle(req.getLibelle())
                .inputsJson(req.getInputsJson())
                .resultJson(req.getResultJson())
                .montantDemande(req.getMontantDemande())
                .mensualite(req.getMensualite())
                .montantMax(req.getMontantMax())
                .dureeMois(req.getDureeMois())
                .tauxEndettement(req.getTauxEndettement())
                .build();
        return repository.save(h);
    }

    public List<SimulationHistory> listByUser(Long userId) {
        return repository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    public List<SimulationHistory> compare(Long userId, List<Long> ids) {
        if (ids == null || ids.isEmpty() || ids.size() > 3) {
            throw new IllegalArgumentException("Sélectionnez entre 1 et 3 simulations");
        }
        return repository.findByUserIdAndIdIn(userId, ids);
    }

    public void delete(Long userId, Long id) {
        repository.findById(id).ifPresent(h -> {
            if (h.getUserId().equals(userId)) repository.delete(h);
        });
    }
}