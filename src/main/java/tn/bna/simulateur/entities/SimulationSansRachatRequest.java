package tn.bna.simulateur.entities;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class SimulationSansRachatRequest {

    @NotNull
    @Positive
    private Double salaireMensuelMoyen;

    @PositiveOrZero
    private double cinquantePourcentLoyers;

    @PositiveOrZero
    private double retenuesSed;

    @PositiveOrZero
    private double retenuesCreditsBna;

    @NotBlank
    private String typeCredit;

    @Min(1)
    private int dureeMois;

    @PositiveOrZero
    private Double montantCreditDemande;
}