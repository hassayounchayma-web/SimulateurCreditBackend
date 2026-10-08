package tn.bna.simulateur.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AmortissementLigne {
    private int mois;
    private double mensualite;
    private double interets;
    private double capital;
    private double restantDu;
}