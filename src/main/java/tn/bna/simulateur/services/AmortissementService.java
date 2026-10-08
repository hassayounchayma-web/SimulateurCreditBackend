package tn.bna.simulateur.services;

import org.springframework.stereotype.Service;
import tn.bna.simulateur.dto.AmortissementLigne;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Service
public class AmortissementService {

    public List<AmortissementLigne> generer(double montant, double tauxAnnuel, int dureeMois) {
        List<AmortissementLigne> lignes = new ArrayList<>();
        if (montant <= 0 || dureeMois <= 0) return lignes;

        double tauxMensuel = tauxAnnuel / 100.0 / 12.0;
        double mensualite;

        if (tauxMensuel == 0) {
            mensualite = montant / dureeMois;
        } else {
            double pow = Math.pow(1 + tauxMensuel, dureeMois);
            mensualite = montant * tauxMensuel * pow / (pow - 1);
        }

        double restant = montant;
        for (int m = 1; m <= dureeMois; m++) {
            double interets = restant * tauxMensuel;
            double capital = mensualite - interets;
            if (m == dureeMois) {
                capital = restant;
                mensualite = capital + interets;
            }
            restant = Math.max(0, restant - capital);

            lignes.add(new AmortissementLigne(
                    m,
                    round(mensualite),
                    round(interets),
                    round(capital),
                    round(restant)
            ));
        }
        return lignes;
    }

    private double round(double v) {
        return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}