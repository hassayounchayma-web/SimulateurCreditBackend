package tn.bna.simulateur.entities;

import lombok.Data;

@Data
public class SimulationResult {

    private Double totalRevenuNetMoyen;
    private Double tauxEndettement;
    private Double gf;
    private Double plafondEndettementMensuel;
    private Double totalRetenues;
    private Double availableMensualite;
    private Double montantMaxCredit;
    private Double mensualite;
    private Double totalEndettementMensuel;
    private Double tauxEndettementCalcule;

    // Champs spécifiques Rachat
    private Double reliquatCapaciteEffectif;
    private Double mensualiteNouveau;
    private Double reliquatCreditApresRachat;
    private Double totalRetenuesApres;
    private Double totalRetenuesAvant;
    private Double montantNouveauCredit;
    private Double tauxEndettementMaxAutorise;
    private Boolean amelioration;

    // ✅ Nouveau champ pour Crédit H24 / Crédit Perso
    private Integer duree;

    private String error;

    // Factory method pour les erreurs
    public static SimulationResult error(String message) {
        SimulationResult result = new SimulationResult();
        result.setError(message);
        return result;
    }
}