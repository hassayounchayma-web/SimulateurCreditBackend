package tn.bna.simulateur.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import tn.bna.simulateur.entities.SimulationHistory;
import java.util.List;

public interface SimulationHistoryRepository extends JpaRepository<SimulationHistory, Long> {
    List<SimulationHistory> findByUserIdOrderByCreatedAtDesc(Long userId);
    List<SimulationHistory> findByUserIdAndIdIn(Long userId, List<Long> ids);
}