package tn.bna.simulateur.entities;

import jakarta.validation.constraints.*;
import lombok.Data;
@Data
public class SimulationRachatExterneRequest {
    @NotNull
    @Positive
    private double salaireMensuelMoyen;
    @PositiveOrZero private double cinquantePourcentLoyers;
    @PositiveOrZero
    private double retenuesSedNonRachete;
    @Positive private double capitalRestantExterneARacheter;
    @NotBlank
    private String typeCredit;
    @Min(1) private int dureeMois;
    @Positive private double montantCreditDemande;
}