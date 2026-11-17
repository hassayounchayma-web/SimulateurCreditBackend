package tn.bna.simulateur.entities;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "dossiers_credit")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Dossier {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, unique = true, length = 32)
    private String reference;

    @Column(nullable = false, length = 160)
    private String libelle;

    @Column(nullable = false, length = 40)
    private String statut;

    @Column(length = 40)
    private String typeVendeur;

    @Column(nullable = false)
    @Builder.Default
    private boolean revenusSupplementairesDeclares = false;

    @Column(length = 60)
    private String statutValidationDocumentaire;

    @Column(columnDefinition = "LONGTEXT")
    private String motifValidationDocumentaire;

    @Column(length = 40)
    private String statutVerificationPromoteur;

    @Column(columnDefinition = "LONGTEXT")
    private String motifVerificationPromoteur;

    @Column(columnDefinition = "LONGTEXT")
    private String simulationJson;

    @Column(columnDefinition = "LONGTEXT")
    private String documentsJson;

    @Column(columnDefinition = "LONGTEXT")
    private String checklistJson;

    @Column(columnDefinition = "LONGTEXT")
    private String analyseJson;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
