package tn.bna.simulateur.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import tn.bna.simulateur.entities.Dossier;

import java.util.List;
import java.util.Optional;

public interface DossierRepository extends JpaRepository<Dossier, Long> {
    List<Dossier> findByUserIdOrderByCreatedAtDesc(Long userId);
    List<Dossier> findAllByOrderByCreatedAtDesc();
    Optional<Dossier> findByIdAndUserId(Long id, Long userId);
}
