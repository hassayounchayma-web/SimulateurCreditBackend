package tn.bna.simulateur.entities;
import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class SimulationRachatInterneRequest {
    @NotNull
    @Positive
    private double salaireMensuelMoyen;
    @PositiveOrZero private double cinquantePourcentLoyers;
    @PositiveOrZero
    private double retenuesSed;
    @PositiveOrZero private double retenuesCreditsBnaNonRachete;
    @Positive private double capitalRestantARacheter;
    @NotBlank
    private String typeCredit;
    @Min(1) private int dureeMois;
    @Positive private double montantCreditDemande;
}
