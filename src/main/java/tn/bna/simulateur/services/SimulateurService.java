
package tn.bna.simulateur.services;

import org.springframework.stereotype.Service;

import tn.bna.simulateur.entities.SimulationRachatExterneRequest;
import tn.bna.simulateur.entities.SimulationRachatInterneRequest;
import tn.bna.simulateur.entities.SimulationResult;
import tn.bna.simulateur.entities.SimulationSansRachatRequest;

import tn.bna.simulateur.entities.admin.ParametreMarge;
import tn.bna.simulateur.services.admin.ParametrageService;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class SimulateurService {

    // =====================================================
    // PARAMETRAGE DYNAMIQUE
    // =====================================================

    private final ParametrageService parametrageService;

    public SimulateurService(ParametrageService parametrageService) {
        this.parametrageService = parametrageService;
    }

    public record CreditConfig(int maxMois, double marge) {}

    /**
     * Récupère la configuration du crédit depuis MySQL.
     *
     * La marge est stockée en pourcentage :
     * 2.25 signifie 2,25 %.
     *
     * Pour les calculs :
     * 2.25 / 100 = 0.0225.
     */
    private CreditConfig getConfigDynamique(
            String scenario,
            String typeCredit) {

        return parametrageService.getMarges(scenario)
                .stream()
                .filter(param ->
                        param.getTypeCredit().equals(typeCredit)
                )
                .findFirst()
                .map(param -> new CreditConfig(
                        param.getMaxMois(),
                        param.getMarge().doubleValue() / 100.0
                ))
                .orElse(null);
    }

    /**
     * Retourne le TMM sous forme décimale.
     *
     * Exemple : 7.49 % -> 0.0749
     */
    private double getTmmDynamique() {
        return parametrageService.getTmmDecimal();
    }

    // =====================================================
    // FORMULES EXCEL VA / VPM
    // =====================================================

    /** VA(taux/12 ; n ; -mensualité) */
    public static double va(double tauxAnnuel, int n, double mensDispo) {
        if (n <= 0) return 0;
        double r = tauxAnnuel / 12.0;
        if (Math.abs(r) < 1e-12) return mensDispo * n;
        return mensDispo * (1 - Math.pow(1 + r, -n)) / r;
    }

    /** VPM(taux/12 ; n ; -capital) */
    public static double vpm(double tauxAnnuel, int n, double capital) {
        if (n <= 0 || capital <= 0) return 0;
        double r = tauxAnnuel / 12.0;
        if (Math.abs(r) < 1e-12) return capital / n;
        return capital * r * Math.pow(1 + r, n)
                / (Math.pow(1 + r, n) - 1);
    }

    private static double round2(double x) {
        return Math.round(x * 100.0) / 100.0;
    }

    private static double n(Double v) {
        return v != null ? v : 0.0;
    }

    // =====================================================
    // TAUX ENDETTEMENT ET GARDE-FOU
    // SANS RACHAT ET RACHAT INTERNE
    // =====================================================

    private double txEndStandard(double revenu) {
        if (revenu < 1000) return 0.40;
        if (revenu < 2000) return 0.50;
        if (revenu < 3000) return 0.55;
        if (revenu < 4000) return 0.60;
        return 0.65;
    }

    private double gfStandard(double salaire) {
        if (salaire < 1000) return 0.50;
        if (salaire < 2000) return 0.60;
        if (salaire < 3000) return 0.65;
        if (salaire < 4000) return 0.70;
        return 0.75;
    }

    // =====================================================
    // TAUX ENDETTEMENT ET GARDE-FOU
    // RACHAT EXTERNE
    // =====================================================

    private double txEndExterne(double revenu) {
        if (revenu < 2000) return 0.50;
        if (revenu < 3000) return 0.55;
        if (revenu < 4000) return 0.60;
        return 0.65;
    }

    private double gfExterne(double salaire) {
        if (salaire < 2000) return 0.60;
        if (salaire < 3000) return 0.65;
        if (salaire < 4000) return 0.70;
        return 0.75;
    }

    /**
     * Plafond = MIN(revenu × TxEnd ; salaire × GF)
     *
     * revenu = salaire + cinquantePourcentLoyers
     */
    private double plafond(
            double salaire,
            double revenu,
            double tx,
            double gf) {

        return Math.min(revenu * tx, salaire * gf);
    }

    // =====================================================
    // CONSTRUCTION DU RESULTAT
    // =====================================================

    private SimulationResult fillBase(
            double revenu,
            double tx,
            double gf,
            double plaf,
            double reliquat,
            double montantMax,
            int duree,
            double tauxAnnuel) {

        SimulationResult r = new SimulationResult();

        r.setTotalRevenuNetMoyen(round2(revenu));
        r.setTauxEndettement(round2(tx * 100));
        r.setGf(round2(gf * 100));
        r.setPlafondEndettementMensuel(round2(plaf));
        r.setAvailableMensualite(round2(reliquat));
        r.setReliquatCapaciteEffectif(round2(reliquat));
        r.setMontantMaxCredit(round2(montantMax));
        r.setDuree(duree);
        r.setTauxAnnuel(round2(tauxAnnuel * 100));

        return r;
    }

    // =====================================================
    // SCENARIO 1 : SANS RACHAT
    // =====================================================

    public SimulationResult simulateSansRachat(
            double salaire,
            double cinquantePourcentLoyers,
            double retenuesSed,
            double retenuesBna,
            String typeCredit,
            int duree,
            Double montantDemande) {

        CreditConfig cfg = getConfigDynamique(
                "SANS_RACHAT",
                typeCredit
        );

        if (cfg == null)
            return SimulationResult.error("Type de crédit invalide");

        if (duree <= 0 || duree > cfg.maxMois()) {
            return SimulationResult.error(
                    "Durée max autorisée : " + cfg.maxMois() + " mois");
        }

        double revenu = salaire + cinquantePourcentLoyers;
        double tx = txEndStandard(revenu);
        double gf = gfStandard(salaire);
        double plaf = plafond(salaire, revenu, tx, gf);
        double reliquat = Math.max(0, plaf - retenuesSed - retenuesBna);

        // TMM + marge configurés par l'administrateur
        double taux = getTmmDynamique() + cfg.marge();

        double montantMax = va(taux, duree, reliquat);

        SimulationResult r = fillBase(
                revenu, tx, gf, plaf, reliquat,
                montantMax, duree, taux
        );

        r.setMensualite(round2(reliquat));

        if (montantDemande != null && montantDemande > 0) {

            if (montantDemande > montantMax + 0.01) {
                r.setError(
                        "Montant demandé dépasse le maximum ("
                                + round2(montantMax) + " DT)");
                r.setStatut("HORS_LIMITES");

            } else {
                double mens = vpm(taux, duree, montantDemande);
                double total = retenuesSed + retenuesBna + mens;

                r.setMensualite(round2(mens));
                r.setTotalEndettementMensuel(round2(total));
                r.setTotalRetenues(round2(total));
                r.setTauxEndettementCalcule(
                        revenu > 0 ? round2(total / revenu * 100) : 0);
                r.setStatut("ELIGIBLE");
            }

        } else {
            r.setStatut("ELIGIBLE");
        }

        return r;
    }

    // =====================================================
    // SCENARIO 2 : RACHAT INTERNE
    // =====================================================

    public SimulationResult simulateRachatInterne(
            double salaire,
            double cinquantePourcentLoyers,
            double retenuesSed,
            double retenuesBnaNonRachete,
            double capitalRestantARacheter,
            String typeCredit,
            int duree,
            Double montantDemande) {

        CreditConfig cfg = getConfigDynamique(
                "RACHAT_INTERNE",
                typeCredit
        );

        if (cfg == null)
            return SimulationResult.error("Type de crédit invalide");

        if (duree <= 0 || duree > cfg.maxMois()) {
            return SimulationResult.error(
                    "Durée max autorisée : " + cfg.maxMois() + " mois");
        }

        double revenu = salaire + cinquantePourcentLoyers;
        double tx = txEndStandard(revenu);
        double gf = gfStandard(salaire);
        double plaf = plafond(salaire, revenu, tx, gf);

        // Reliquat = plafond - SED - BNA non racheté
        double reliquat = Math.max(
                0, plaf - retenuesSed - retenuesBnaNonRachete
        );

        // TMM + marge configurés par l'administrateur
        double taux = getTmmDynamique() + cfg.marge();

        double montantMax = va(taux, duree, reliquat);

        SimulationResult r = fillBase(
                revenu, tx, gf, plaf, reliquat,
                0, duree, taux
        );

        // Excel : si VA < capital restant dû
        // -> Capacité Insuffisante
        if (montantMax < capitalRestantARacheter) {
            r.setError("Capacité Insuffisante");
            r.setMontantMaxCredit(0.0);
            r.setStatut("CAPACITE_INSUFFISANTE");
            return r;
        }

        r.setMontantMaxCredit(round2(montantMax));
        r.setMensualite(round2(reliquat));

        if (montantDemande != null && montantDemande > 0) {

            if (montantDemande > montantMax + 0.01) {
                r.setError(
                        "Montant demandé dépasse le maximum ("
                                + round2(montantMax) + " DT)");
                r.setStatut("HORS_LIMITES");

            } else {
                double mens = vpm(taux, duree, montantDemande);

                // Reliquat crédit après rachat = demande - capital restant
                double reliquatCredit =
                        montantDemande - capitalRestantARacheter;

                // Total retenues = SED + BNA non racheté + mensualité
                double total =
                        retenuesSed + retenuesBnaNonRachete + mens;

                r.setMensualite(round2(mens));
                r.setReliquatCreditApresRachat(round2(reliquatCredit));
                r.setTotalEndettementMensuel(round2(total));
                r.setTotalRetenues(round2(total));
                r.setTauxEndettementCalcule(
                        revenu > 0 ? round2(total / revenu * 100) : 0);
                r.setStatut("ELIGIBLE");
            }

        } else {
            r.setStatut("ELIGIBLE");
        }

        return r;
    }

    // =====================================================
    // SCENARIO 3 : RACHAT EXTERNE
    // =====================================================

    public SimulationResult simulateRachatExterne(
            double salaire,
            double cinquantePourcentLoyers,
            double retenuesSedNonRachete,
            double capitalRestantARacheter,
            String typeCredit,
            int duree,
            Double montantDemande) {

        CreditConfig cfg = getConfigDynamique(
                "RACHAT_EXTERNE",
                typeCredit
        );

        if (cfg == null) {
            return SimulationResult.error(
                    "Type invalide (externe : Crédit Direct Dépenses Courantes "
                            + "ou Crédit Direct Aménagement)");
        }

        if (duree <= 0 || duree > cfg.maxMois()) {
            return SimulationResult.error(
                    "Durée max autorisée : " + cfg.maxMois() + " mois");
        }

        double revenu = salaire + cinquantePourcentLoyers;
        double tx = txEndExterne(revenu);
        double gf = gfExterne(salaire);
        double plaf = plafond(salaire, revenu, tx, gf);

        double reliquat = Math.max(
                0, plaf - retenuesSedNonRachete
        );

        // TMM + marge configurés par l'administrateur
        double taux = getTmmDynamique() + cfg.marge();

        double montantMax = va(taux, duree, reliquat);

        SimulationResult r = fillBase(
                revenu, tx, gf, plaf, reliquat,
                0, duree, taux
        );

        if (montantMax < capitalRestantARacheter) {
            r.setError("Capacité Insuffisante");
            r.setMontantMaxCredit(0.0);
            r.setStatut("CAPACITE_INSUFFISANTE");
            return r;
        }

        r.setMontantMaxCredit(round2(montantMax));
        r.setMensualite(round2(reliquat));

        if (montantDemande != null && montantDemande > 0) {

            if (montantDemande > montantMax + 0.01) {
                r.setError(
                        "Montant demandé dépasse le maximum ("
                                + round2(montantMax) + " DT)");
                r.setStatut("HORS_LIMITES");

            } else {
                double mens = vpm(taux, duree, montantDemande);
                double reliquatCredit =
                        montantDemande - capitalRestantARacheter;
                double total = retenuesSedNonRachete + mens;

                r.setMensualite(round2(mens));
                r.setReliquatCreditApresRachat(round2(reliquatCredit));
                r.setTotalEndettementMensuel(round2(total));
                r.setTotalRetenues(round2(total));
                r.setTauxEndettementCalcule(
                        revenu > 0 ? round2(total / revenu * 100) : 0);
                r.setStatut("ELIGIBLE");
            }

        } else {
            r.setStatut("ELIGIBLE");
        }

        return r;
    }

    // =====================================================
    // ENTREES DTO (CONTROLLER)
    // =====================================================

    public SimulationResult simulateSansRachat(
            SimulationSansRachatRequest req) {

        Double demande =
                (req.getMontantCreditDemande() != null
                        && req.getMontantCreditDemande() > 0)
                        ? req.getMontantCreditDemande()
                        : null;

        return simulateSansRachat(
                n(req.getSalaireMensuelMoyen()),
                req.getCinquantePourcentLoyers(),
                req.getRetenuesSed(),
                req.getRetenuesCreditsBna(),
                req.getTypeCredit(),
                req.getDureeMois(),
                demande
        );
    }

    public SimulationResult simulateRachatInterne(
            SimulationRachatInterneRequest req) {

        Double demande =
                (req.getMontantCreditDemande() != null
                        && req.getMontantCreditDemande() > 0)
                        ? req.getMontantCreditDemande()
                        : null;

        return simulateRachatInterne(
                n(req.getSalaireMensuelMoyen()),
                req.getCinquantePourcentLoyers(),
                req.getRetenuesSed(),
                req.getRetenuesCreditsBnaNonRachete(),
                req.getCapitalRestantARacheter(),
                req.getTypeCredit(),
                req.getDureeMois(),
                demande
        );
    }

    public SimulationResult simulateRachatExterne(
            SimulationRachatExterneRequest req) {

        Double demande =
                (req.getMontantCreditDemande() != null
                        && req.getMontantCreditDemande() > 0)
                        ? req.getMontantCreditDemande()
                        : null;

        return simulateRachatExterne(
                n(req.getSalaireMensuelMoyen()),
                req.getCinquantePourcentLoyers(),
                req.getRetenuesSed(),
                req.getCapitalRestantARacheter(),
                req.getTypeCredit(),
                req.getDureeMois(),
                demande
        );
    }

    // =====================================================
    // CATALOGUE DYNAMIQUE
    // =====================================================

    public Map<String, CreditConfig> catalogue(String scenario) {

        // Conserve le comportement de l'ancien switch :
        // un scénario inconnu utilise SANS_RACHAT.
        String scenarioEffectif = switch (scenario) {
            case "RACHAT_INTERNE" -> "RACHAT_INTERNE";
            case "RACHAT_EXTERNE" -> "RACHAT_EXTERNE";
            default -> "SANS_RACHAT";
        };

        Map<String, CreditConfig> resultat = new LinkedHashMap<>();

        for (ParametreMarge param :
                parametrageService.getMarges(scenarioEffectif)) {

            resultat.put(
                    param.getTypeCredit(),
                    new CreditConfig(
                            param.getMaxMois(),
                            param.getMarge().doubleValue() / 100.0
                    )
            );
        }

        return resultat;
    }
}
