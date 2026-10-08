package tn.bna.simulateur.entities;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "simulation_history")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SimulationHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, length = 80)
    private String typeSimulateur; // credit-auto, sans-rachat, etc.

    @Column(nullable = false, length = 120)
    private String libelle; // "Crédit Auto - Neuf 4CV"

    @Column(columnDefinition = "TEXT")
    private String inputsJson; // formulaire saisi

    @Column(columnDefinition = "TEXT")
    private String resultJson; // résultat API

    private Double montantDemande;
    private Double mensualite;
    private Double montantMax;
    private Integer dureeMois;
    private Double tauxEndettement;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}