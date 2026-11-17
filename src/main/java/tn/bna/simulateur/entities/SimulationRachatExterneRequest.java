package tn.bna.simulateur.entities;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class SimulationRachatExterneRequest {

    @NotNull
    @Positive
    private Double salaireMensuelMoyen;

    @PositiveOrZero
    private double cinquantePourcentLoyers;

    /** Retenues crédits bancaires non rachetés (SED) */
    @PositiveOrZero
    private double retenuesSed;

    @PositiveOrZero
    private double capitalRestantARacheter;

    @NotBlank
    private String typeCredit;

    @Min(1)
    private int dureeMois;

    @PositiveOrZero
    private Double montantCreditDemande;
}