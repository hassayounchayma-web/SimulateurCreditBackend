package tn.bna.simulateur.dto;

import lombok.Data;

@Data
public class SaveSimulationRequest {
    private String typeSimulateur;
    private String libelle;
    private String inputsJson;
    private String resultJson;
    private Double montantDemande;
    private Double mensualite;
    private Double montantMax;
    private Integer dureeMois;
    private Double tauxEndettement;
}