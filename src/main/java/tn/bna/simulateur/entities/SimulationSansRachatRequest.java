package tn.bna.simulateur.entities;

import jakarta.validation.constraints.*;
import lombok.Data;
@Data
public class SimulationSansRachatRequest {
    @NotNull(message = "Le salaire mensuel moyen est obligatoire")
    @Positive(message = "Le salaire doit être positif")
    private double salaireMensuelMoyen;

    @PositiveOrZero
    private double cinquantePourcentLoyers;

    @PositiveOrZero
    private double retenuesSed;

    @PositiveOrZero
    private double retenuesCreditsBna;

    @NotBlank(message = "Le type de crédit est obligatoire")
    private String typeCredit;

    @Min(value = 1, message = "La durée doit être d'au moins 1 mois")
    private int dureeMois;

    @PositiveOrZero
    private Double montantDemande; // peut être null
}