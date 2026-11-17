package tn.bna.simulateur.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import tn.bna.simulateur.entities.DossierDocument;

import java.util.List;
import java.util.Optional;

public interface DossierDocumentRepository extends JpaRepository<DossierDocument, Long> {
    List<DossierDocument> findByDossierIdOrderByTypeDocumentAscVersionAsc(Long dossierId);
    List<DossierDocument> findByDossierIdAndTypeDocumentAndActifTrue(Long dossierId, String typeDocument);
    Optional<DossierDocument> findTopByDossierIdAndTypeDocumentOrderByVersionDesc(Long dossierId, String typeDocument);
    Optional<DossierDocument> findByIdAndDossierId(Long id, Long dossierId);
}
