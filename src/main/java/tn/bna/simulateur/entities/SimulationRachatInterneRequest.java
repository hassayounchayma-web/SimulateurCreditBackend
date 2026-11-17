package tn.bna.simulateur.entities;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class SimulationRachatInterneRequest {

    @NotNull
    @Positive
    private Double salaireMensuelMoyen;

    /** 50 % des loyers domiciliés — saisi directement */
    @PositiveOrZero
    private double cinquantePourcentLoyers;

    @PositiveOrZero
    private double retenuesSed;

    @PositiveOrZero
    private double retenuesCreditsBnaNonRachete;

    @PositiveOrZero
    private double capitalRestantARacheter;

    @NotBlank
    private String typeCredit;

    @Min(1)
    private int dureeMois;

    /** Optionnel : 0 ou absent = calcul du max seulement */
    @PositiveOrZero
    private Double montantCreditDemande;
}