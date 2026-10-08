package tn.bna.simulateur.agent.dto;

import lombok.Data;

@Data
public class LlmExtraction {
    private String intent;          // SIMULATE | INFO | COMPARE | HELP | DEFAULT
    private String simulateurKey;   // credit-auto, sans-rachat, ...
    private Double salaire;
    private Double retenues;
    private Double montant;
    private Integer dureeMois;
    private String typeProjet;      // auto, immobilier, agricole, ...
    private String messageUtilisateur;
}