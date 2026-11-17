
package tn.bna.simulateur.config.admin;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import tn.bna.simulateur.entities.admin.ParametreMarge;
import tn.bna.simulateur.entities.admin.ParametreTmm;
import tn.bna.simulateur.repositories.admin.ParametreMargeRepository;
import tn.bna.simulateur.repositories.admin.ParametreTmmRepository;

import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
public class ParametrageInitializer implements CommandLineRunner {

    private final ParametreTmmRepository tmmRepository;
    private final ParametreMargeRepository margeRepository;

    @Override
    public void run(String... args) {

        if (!tmmRepository.existsById(1L)) {
            ParametreTmm tmm = new ParametreTmm();
            tmm.setId(1L);
            tmm.setValeur(new BigDecimal("7.49"));
            tmmRepository.save(tmm);
        }

        // SANS RACHAT
        add("SANS_RACHAT", "Crédit Direct Dépenses Courantes", 36, "2.25");
        add("SANS_RACHAT", "Crédit Direct Aménagement", 60, "2.50");
        add("SANS_RACHAT", "Crédit Direct Acquisition Véhicule Neuf", 84, "2.00");
        add("SANS_RACHAT", "Crédit Direct Acquisition Véhicule Occasion", 60, "2.00");
        add("SANS_RACHAT", "Crédit Direct Aménagement Hypothécaire (Durée ≤ 7 ans)", 84, "2.00");
        add("SANS_RACHAT", "Crédit Direct Aménagement Hypothécaire (Durée > 7 ans et ≤ 12 ans)", 144, "2.25");
        add("SANS_RACHAT", "Crédit Direct Acquisition Logement (Durée ≤ 7 ans)", 84, "2.00");
        add("SANS_RACHAT", "Crédit Direct Acquisition Logement (Durée > 7 ans et ≤ 15 ans)", 180, "2.25");
        add("SANS_RACHAT", "Crédit Direct Construction (Durée ≤ 7 ans)", 84, "2.00");
        add("SANS_RACHAT", "Crédit Direct Construction (Durée > 7 ans et ≤ 15 ans)", 180, "2.25");
        add("SANS_RACHAT", "Crédit Direct Achat Terrain (Durée ≤ 7 ans)", 84, "2.00");
        add("SANS_RACHAT", "Crédit Direct Achat Terrain (Durée > 7 ans et ≤ 15 ans)", 180, "2.25");

        // RACHAT INTERNE
        add("RACHAT_INTERNE", "Crédit Direct Dépenses Courantes", 36, "3.50");
        add("RACHAT_INTERNE", "Crédit Direct Aménagement", 60, "3.75");
        add("RACHAT_INTERNE", "Crédit Direct Acquisition Véhicule Neuf", 84, "3.00");
        add("RACHAT_INTERNE", "Crédit Direct Acquisition Véhicule Occasion", 60, "3.00");
        add("RACHAT_INTERNE", "Crédit Direct Aménagement Hypothécaire (Durée ≤ 7 ans)", 84, "3.00");
        add("RACHAT_INTERNE", "Crédit Direct Aménagement Hypothécaire (Durée > 7 ans et ≤ 12 ans)", 144, "3.25");
        add("RACHAT_INTERNE", "Crédit Direct Acquisition Logement (Durée ≤ 7 ans)", 84, "3.00");
        add("RACHAT_INTERNE", "Crédit Direct Acquisition Logement (Durée > 7 ans et ≤ 15 ans)", 180, "3.25");
        add("RACHAT_INTERNE", "Crédit Direct Construction (Durée ≤ 7 ans)", 84, "3.00");
        add("RACHAT_INTERNE", "Crédit Direct Construction (Durée > 7 ans et ≤ 15 ans)", 180, "3.25");
        add("RACHAT_INTERNE", "Crédit Direct Achat Terrain (Durée ≤ 7 ans)", 84, "3.00");
        add("RACHAT_INTERNE", "Crédit Direct Achat Terrain (Durée > 7 ans et ≤ 15 ans)", 180, "3.25");

        // RACHAT EXTERNE
        add("RACHAT_EXTERNE", "Crédit Direct Dépenses Courantes", 36, "2.25");
        add("RACHAT_EXTERNE", "Crédit Direct Aménagement", 60, "2.50");
    }

    private void add(
            String scenario,
            String typeCredit,
            int maxMois,
            String marge) {

        boolean existe = margeRepository
                .findByScenarioAndTypeCredit(scenario, typeCredit)
                .isPresent();

        if (existe) {
            return;
        }

        ParametreMarge param = new ParametreMarge();
        param.setScenario(scenario);
        param.setTypeCredit(typeCredit);
        param.setMaxMois(maxMois);
        param.setMarge(new BigDecimal(marge));

        margeRepository.save(param);
    }
}
