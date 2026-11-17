package tn.bna.simulateur.services.admin;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import tn.bna.simulateur.dto.admin.ParametrageResponse;
import tn.bna.simulateur.entities.admin.ParametreMarge;
import tn.bna.simulateur.entities.admin.ParametreTmm;
import tn.bna.simulateur.repositories.admin.ParametreMargeRepository;
import tn.bna.simulateur.repositories.admin.ParametreTmmRepository;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ParametrageService {

    private final ParametreTmmRepository tmmRepository;
    private final ParametreMargeRepository margeRepository;

    public record CreditConfig(int maxMois, double marge) {}

    private void verifierPourcentage(BigDecimal valeur) {
        if (valeur == null
                || valeur.compareTo(BigDecimal.ZERO) < 0
                || valeur.compareTo(new BigDecimal("100")) > 0
                || valeur.scale() > 4) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Taux invalide : valeur entre 0 et 100, "
                            + "avec au maximum 4 décimales"
            );
        }
    }

    public ParametreTmm getTmm() {
        return tmmRepository.findById(1L)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "TMM non initialisé"
                ));
    }

    public double getTmmDecimal() {
        return getTmm().getValeur().doubleValue() / 100.0;
    }

    public List<ParametreMarge> getMarges(String scenario) {
        if (scenario == null || scenario.isBlank()) {
            return margeRepository
                    .findAllByOrderByScenarioAscTypeCreditAsc();
        }

        verifierScenario(scenario);

        return margeRepository
                .findByScenarioOrderByTypeCreditAsc(scenario);
    }

    public ParametrageResponse getParametrage() {
        return new ParametrageResponse(
                getTmm().getValeur(),
                getMarges(null)
        );
    }

    public CreditConfig getCreditConfig(
            String scenario,
            String typeCredit) {

        verifierScenario(scenario);

        if (typeCredit == null || typeCredit.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Type de crédit obligatoire"
            );
        }

        ParametreMarge param = margeRepository
                .findByScenarioAndTypeCredit(scenario, typeCredit)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Aucune marge configurée pour ce produit "
                                + "et ce scénario"
                ));

        return new CreditConfig(
                param.getMaxMois(),
                param.getMarge().doubleValue() / 100.0
        );
    }

    @Transactional
    public ParametreTmm modifierTmm(BigDecimal valeur) {
        verifierPourcentage(valeur);

        ParametreTmm tmm = getTmm();
        tmm.setValeur(valeur);

        return tmmRepository.save(tmm);
    }

    @Transactional
    public ParametreMarge modifierMarge(
            Long id,
            BigDecimal marge) {

        verifierPourcentage(marge);

        ParametreMarge param = margeRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Paramètre de marge introuvable"
                ));

        param.setMarge(marge);

        return margeRepository.save(param);
    }

    private void verifierScenario(String scenario) {
        if (!List.of(
                "SANS_RACHAT",
                "RACHAT_INTERNE",
                "RACHAT_EXTERNE"
        ).contains(scenario)) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Scénario invalide"
            );
        }
    }
}
