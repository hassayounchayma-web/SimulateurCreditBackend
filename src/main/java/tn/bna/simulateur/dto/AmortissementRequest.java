package tn.bna.simulateur.dto;

import lombok.Data;
import java.util.List;

@Data
public class AmortissementRequest {
    private double montant;
    private double tauxAnnuel; // ex: 10.08
    private int dureeMois;
}