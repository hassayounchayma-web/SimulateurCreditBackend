package tn.bna.simulateur.entities;

import lombok.Data;

@Data
public class SimulationResult {
    private Double totalRevenuNetMoyen;
    private Double tauxEndettement;
    private Double gf;
    private Double plafondEndettementMensuel;
    private Double availableMensualite;
    private Double reliquatCapaciteEffectif;
    private Double montantMaxCredit;
    private Double mensualite;
    private Integer duree;
    private Double tauxAnnuel;
    private Double totalEndettementMensuel;
    private Double totalRetenues;
    private Double tauxEndettementCalcule;
    private Double reliquatCreditApresRachat;
    private String statut;
    private String error;

    public static SimulationResult error(String message) {
        SimulationResult r = new SimulationResult();
        r.setError(message);
        r.setStatut("HORS_LIMITES");
        return r;
    }
}