package tn.bna.simulateur.entities;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "dossiers_documents")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DossierDocument {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long dossierId;

    @Column(nullable = false, length = 64)
    private String typeDocument;

    @Column(nullable = false)
    private Integer version;

    @Column(nullable = false, length = 240)
    private String storageKey;

    @Column(nullable = false, length = 120)
    private String nomFichier;

    @Column(nullable = false, length = 120)
    private String typeMime;

    @Column(nullable = false)
    private Long tailleOctets;

    @Column(nullable = false)
    private boolean actif;

    @Column(nullable = false, length = 40)
    @Builder.Default
    private String statutValidation = "EN_ATTENTE";

    @Column(columnDefinition = "LONGTEXT")
    private String resultatOcrJson;

    @Column(columnDefinition = "LONGTEXT")
    private String correctionsAdminJson;

    @Column(columnDefinition = "LONGTEXT")
    private String historiqueCorrectionsAdminJson;

    @Column(columnDefinition = "LONGTEXT")
    private String historiqueOcrJson;

    @Column(columnDefinition = "LONGTEXT")
    private String motifAdmin;

    @Column(length = 120)
    private String validePar;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
