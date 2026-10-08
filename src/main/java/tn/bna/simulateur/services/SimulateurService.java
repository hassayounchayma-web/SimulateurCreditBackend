package tn.bna.simulateur.services;

import lombok.Data;
import org.springframework.stereotype.Service;
import tn.bna.simulateur.entities.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;

@Service
public class SimulateurService {

    private static final double TMM = 7;//7.08

    // Types de crédit existants
    private static final Map<String, CreditConfig> CREDIT_TYPES = new HashMap<>() {{
        put("Crédit Direct Dépenses Courantes", new CreditConfig(36, 3.5));
        put("Crédit Direct Aménagement", new CreditConfig(60, 3.75));
        put("Crédit Direct Acquisition Véhicule Neuf", new CreditConfig(84, 3.0));
        put("Crédit Direct Acquisition Véhicule Occasion", new CreditConfig(60, 3.0));
        put("Crédit Direct Aménagement Hypothécaire (Durée ≤ 7 ans)", new CreditConfig(84, 3.0));
        put("Crédit Direct Aménagement Hypothécaire (Durée > 7 ans et ≤ 12 ans)", new CreditConfig(144, 3.25));
        put("Crédit Direct Acquisition Logement (Durée ≤ 7 ans)", new CreditConfig(84, 3.0));
        put("Crédit Direct Acquisition Logement (Durée > 7 ans et ≤ 15 ans)", new CreditConfig(180, 3.25));
        put("Crédit Direct Construction (Durée ≤ 7 ans)", new CreditConfig(84, 3.0));
        put("Crédit Direct Construction (Durée > 7 ans et ≤ 15 ans)", new CreditConfig(180, 3.25));
        put("Crédit Direct Achat Terrain (Durée ≤ 7 ans)", new CreditConfig(84, 3.0));
        put("Crédit Direct Achat Terrain (Durée > 7 ans et ≤ 15 ans)", new CreditConfig(180, 3.25));
    }};

    private static final Map<String, CreditConfig> CREDIT_TYPES_RACHAT = new HashMap<>() {{
        put("Crédit Direct Dépenses Courantes", CREDIT_TYPES.get("Crédit Direct Dépenses Courantes"));
        put("Crédit Direct Aménagement", CREDIT_TYPES.get("Crédit Direct Aménagement"));
    }};

    @Data
    static class CreditConfig {
        private final int maxMois;
        private final double marge;
    }

    // ───────────────────────────────────────────────
    // Fonctions auxiliaires communes
    // ───────────────────────────────────────────────

    private double getTauxEndettement(double totalRevenu) {
        if (totalRevenu < 1000) return 0.40;
        else if (totalRevenu < 2000) return 0.50;
        else if (totalRevenu < 3000) return 0.55;
        else if (totalRevenu < 4000) return 0.60;
        else return 0.65;
    }

    private double getGf(double salaireNet) {
        if (salaireNet < 1000) return 0.50;
        else if (salaireNet < 2000) return 0.60;
        else if (salaireNet < 3000) return 0.65;
        else if (salaireNet < 4000) return 0.70;
        else return 0.75;
    }

    private double getTauxEndettementExterne(double totalRevenu) {
        if (totalRevenu < 2000) return 0.50;
        else if (totalRevenu < 3000) return 0.55;
        else if (totalRevenu < 4000) return 0.60;
        else return 0.65;
    }

    private double getGfExterne(double salaireNet) {
        if (salaireNet < 2000) return 0.60;
        else if (salaireNet < 3000) return 0.65;
        else if (salaireNet < 4000) return 0.70;
        else return 0.75;
    }

    private double calculateMensualite(double principal, double tauxAnnuel, int dureeMois) {
        if (dureeMois <= 0 || principal <= 0) return 0.0;
        BigDecimal r = BigDecimal.valueOf(tauxAnnuel / 100.0 / 12.0);
        BigDecimal onePlusR = BigDecimal.ONE.add(r);
        BigDecimal temp = onePlusR.pow(dureeMois);
        BigDecimal numerator = r.multiply(temp);
        BigDecimal denominator = temp.subtract(BigDecimal.ONE);
        return BigDecimal.valueOf(principal)
                .multiply(numerator)
                .divide(denominator, 2, RoundingMode.HALF_UP)
                .doubleValue();
    }

    private double calculateMontantMax(double maxMensualite, double tauxAnnuel, int dureeMois) {
        if (dureeMois <= 0 || maxMensualite <= 0) return 0.0;
        BigDecimal r = BigDecimal.valueOf(tauxAnnuel / 100.0 / 12.0);
        BigDecimal onePlusR = BigDecimal.ONE.add(r);
        BigDecimal temp = onePlusR.pow(dureeMois);
        BigDecimal numerator = temp.subtract(BigDecimal.ONE);
        BigDecimal denominator = r.multiply(temp);
        return BigDecimal.valueOf(maxMensualite)
                .multiply(numerator)
                .divide(denominator, 2, RoundingMode.HALF_UP)
                .doubleValue();
    }

    // ───────────────────────────────────────────────
    // Simulateurs existants (Sans Rachat / Rachat Externe / Rachat Interne)
    // ───────────────────────────────────────────────

    public SimulationResult simulateSansRachat(SimulationSansRachatRequest request) {
        // ... (ton code existant reste inchangé)
        if (!CREDIT_TYPES.containsKey(request.getTypeCredit())) {
            return SimulationResult.error("Type de crédit inconnu : " + request.getTypeCredit());
        }
        CreditConfig config = CREDIT_TYPES.get(request.getTypeCredit());
        double tauxAnnuel = TMM + config.getMarge();

        if (request.getDureeMois() > config.getMaxMois()) {
            return SimulationResult.error("Durée dépasse le maximum pour " + request.getTypeCredit() + " : " + config.getMaxMois() + " mois");
        }

        double salaireNet = request.getSalaireMensuelMoyen();
        double totalRevenuNet = salaireNet + request.getCinquantePourcentLoyers();
        double tauxEndettement = getTauxEndettement(totalRevenuNet);
        double gf = getGf(salaireNet);

        double plafondRevenuTaux = totalRevenuNet * tauxEndettement;
        double plafondSalaireGf = salaireNet * gf;
        double plafondEndettementMensuel = Math.min(plafondRevenuTaux, plafondSalaireGf);

        double totalRetenues = request.getRetenuesSed() + request.getRetenuesCreditsBna();
        double availableMensualite = Math.max(0, plafondEndettementMensuel - totalRetenues);

        if (availableMensualite <= 0) {
            return SimulationResult.error("Pas de capacité d'endettement disponible");
        }

        double montantMaxCredit = calculateMontantMax(availableMensualite, tauxAnnuel, request.getDureeMois());

        SimulationResult result = new SimulationResult();
        result.setTotalRevenuNetMoyen(totalRevenuNet);
        result.setTauxEndettement(tauxEndettement * 100);
        result.setGf(gf * 100);
        result.setPlafondEndettementMensuel(plafondEndettementMensuel);
        result.setTotalRetenues(totalRetenues);
        result.setAvailableMensualite(availableMensualite);
        result.setMontantMaxCredit(Math.round(montantMaxCredit * 100.0) / 100.0);

        if (request.getMontantDemande() != null) {
            double montantDemande = request.getMontantDemande();
            if (montantDemande > montantMaxCredit) {
                result.setError("Montant demandé dépasse le maximum possible");
            } else {
                double mensualite = calculateMensualite(montantDemande, tauxAnnuel, request.getDureeMois());
                double totalEndettementMensuel = totalRetenues + mensualite;
                double tauxEndettementCalcule = totalRevenuNet > 0 ? (totalEndettementMensuel / totalRevenuNet) * 100 : 0;

                result.setMensualite(Math.round(mensualite * 100.0) / 100.0);
                result.setTotalEndettementMensuel(totalEndettementMensuel);
                result.setTauxEndettementCalcule(Math.round(tauxEndettementCalcule * 100.0) / 100.0);
            }
        }
        return result;
    }

    public SimulationResult simulateRachatExterne(SimulationRachatExterneRequest request) {
        // ... (ton code existant reste inchangé)
        // (copie exacte de ton code actuel)
        if (!CREDIT_TYPES_RACHAT.containsKey(request.getTypeCredit())) {
            return SimulationResult.error("Type de crédit invalide pour rachat externe : " + request.getTypeCredit());
        }
        // ... reste identique à ton code
        return null; // remplace par ton code complet
    }

    public SimulationResult simulateRachatInterne(SimulationRachatInterneRequest request) {
        // ... (ton code existant reste inchangé)
        return null; // remplace par ton code complet
    }

    // ───────────────────────────────────────────────
    // NOUVEAUX SIMULATEURS – Financement des Particuliers
    // ───────────────────────────────────────────────


    /**
     * Crédit Perso
     * - Dépenses Courantes →marge 4.5 avec max 240 mois
     * - Aménagement → marge 5 avec  max 240 mois
     *  - Aménagement Hypothécaire → marge 4 avec  max 240 mois
     */
    public SimulationResult simulateCreditPerso(Map<String, Object> request) {
        String typeCredit = (String) request.getOrDefault("typeCredit", "Dépenses Courantes");
        double montantDemande = ((Number) request.getOrDefault("montantDemande", 0)).doubleValue();
        String periodicite = (String) request.getOrDefault("periodicite", "MENSUEL");
        int duree = ((Number) request.getOrDefault("duree", 36)).intValue();
        String datePremiereEcheance = (String) request.getOrDefault("datePremiereEcheance", null);

        String type = typeCredit == null ? "" : typeCredit.trim();

        double marge;
        if (type.equalsIgnoreCase("Aménagement Hypothécaire")
                || type.equalsIgnoreCase("Aménagement Hypothecaire")) {
            marge = 4.0;
        } else if (type.equalsIgnoreCase("Aménagement")) {
            marge = 5.0;
        } else if (type.equalsIgnoreCase("Dépenses Courantes")
                || type.equalsIgnoreCase("Depenses Courantes")) {
            marge = 4.5;
        } else {
            return SimulationResult.error(
                    "Type de crédit invalide. Valeurs : Dépenses Courantes, "
                            + "Aménagement Hypothécaire, Aménagement"
            );
        }

        if (montantDemande <= 0) {
            return SimulationResult.error("Le montant demandé doit être strictement positif");
        }

        String per = periodicite == null ? "MENSUEL" : periodicite.toUpperCase();
        int moisParPeriode = switch (per) {
            case "TRIMESTRIEL" -> 3;
            case "SEMESTRIEL" -> 6;
            case "ANNUEL" -> 12;
            default -> 1;
        };
        int maxPeriodes = 300 / moisParPeriode; // 300, 100, 50, 25

        if (duree <= 0 || duree > maxPeriodes) {
            return SimulationResult.error(
                    "Durée invalide pour cette périodicité. Maximum : " + maxPeriodes + " périodes"
            );
        }

        int totalMois = duree * moisParPeriode;
        double tauxAnnuel = TMM + marge; // TMM global déjà défini dans le service

        double tauxMensuel = (tauxAnnuel / 100.0) / 12.0;
        double mensualite;
        if (Math.abs(tauxMensuel) < 1e-12) {
            mensualite = montantDemande / totalMois;
        } else {
            double pow = Math.pow(1 + tauxMensuel, totalMois);
            mensualite = montantDemande * tauxMensuel * pow / (pow - 1);
        }
        mensualite = Math.round(mensualite * 100.0) / 100.0;
        double echeancePeriode = Math.round(mensualite * moisParPeriode * 100.0) / 100.0;

        String dateDebut = null;
        String dateFin = null;
        if (datePremiereEcheance != null && !datePremiereEcheance.isBlank()) {
            try {
                java.time.LocalDate debut = java.time.LocalDate.parse(datePremiereEcheance.trim());
                java.time.LocalDate fin = debut.plusMonths((long) moisParPeriode * (duree - 1));
                dateDebut = debut.toString();
                dateFin = fin.toString();
            } catch (Exception e) {
                return SimulationResult.error("Date de première échéance invalide (yyyy-MM-dd)");
            }
        }

        String dateDebutFr = dateDebut != null ? formatDateFr(dateDebut) : "—";
        String dateFinFr = dateFin != null ? formatDateFr(dateFin) : "—";
        String message = String.format(
                "Vous avez demandé la somme de %.2f DT. Chaque période, vous allez rembourser : %.2f DT "
                        + "de la date première échéance (%s) à la date fin échéance (%s). "
                        + "Cette simulation est à caractère indicatif sans valeur contractuelle.",
                montantDemande, echeancePeriode, dateDebutFr, dateFinFr
        );

        SimulationResult result = new SimulationResult();
        result.setMontantMaxCredit(Math.round(montantDemande * 100.0) / 100.0);
        result.setMensualite(echeancePeriode);
        result.setDuree(duree);
        result.setTauxEndettement(tauxAnnuel);
        result.setAvailableMensualite(mensualite);
        result.setPlafondEndettementMensuel(TMM);
        result.setGf(marge);

        try {
            result.getClass().getMethod("setMessage", String.class).invoke(result, message);
        } catch (Exception ignored) {
            try {
                result.getClass().getMethod("setLibelle", String.class).invoke(result, message);
            } catch (Exception ignored2) {}
        }
        try {
            result.getClass().getMethod("setDateDebut", String.class).invoke(result, dateDebut);
            result.getClass().getMethod("setDateFin", String.class).invoke(result, dateFin);
        } catch (Exception ignored) {}

        return result;
    }

   
    /**
     * Crédit Immo
     * - Acquisition / Construction / Terrain → max 15 ans (180 mois)
     * - Aménagement → max 12 ans (144 mois)
     * - Financement jusqu’à 80 %
     */
    public SimulationResult simulateCreditImmo(Map<String, Object> request) {
        String typeProjet = (String) request.getOrDefault("typeProjet", "Acquisition logement");
        double montantDemande = ((Number) request.getOrDefault("montantDemande", 0)).doubleValue();
        String periodicite = (String) request.getOrDefault("periodicite", "MENSUEL");
        int duree = ((Number) request.getOrDefault("duree", 120)).intValue(); // nb de périodes
        String datePremiereEcheance = (String) request.getOrDefault("datePremiereEcheance", null);

        String type = typeProjet == null ? "" : typeProjet.trim();
        boolean produitConnu =
                type.equalsIgnoreCase("Construction")
                        || type.equalsIgnoreCase("Achat terrain")
                        || type.equalsIgnoreCase("Acquisition logement")
                        || type.equalsIgnoreCase("Aménagement logement Hypothécaire");

        if (!produitConnu) {
            return SimulationResult.error(
                    "Type de produit invalide. Valeurs : Construction, Achat terrain, "
                            + "Acquisition logement, Aménagement logement Hypothécaire"
            );
        }
        if (montantDemande <= 0) {
            return SimulationResult.error("Le montant demandé doit être strictement positif");
        }

        String per = periodicite == null ? "MENSUEL" : periodicite.toUpperCase();
        int moisParPeriode = switch (per) {
            case "TRIMESTRIEL" -> 3;
            case "SEMESTRIEL" -> 6;
            case "ANNUEL" -> 12;
            default -> 1; // MENSUEL
        };
        int maxPeriodes = 300 / moisParPeriode; // 300, 100, 50, 25

        if (duree <= 0 || duree > maxPeriodes) {
            return SimulationResult.error(
                    "Durée invalide pour cette périodicité. Maximum : " + maxPeriodes + " périodes"
            );
        }

        int totalMois = duree * moisParPeriode; // ≤ 300
        double marge = (totalMois <= 180) ? 4.0 : 2.0;
        double tauxAnnuel = TMM + marge;

        // Mensualité (base mensuelle)
        double tauxMensuel = (tauxAnnuel / 100.0) / 12.0;
        double mensualite;
        if (Math.abs(tauxMensuel) < 1e-12) {
            mensualite = montantDemande / totalMois;
        } else {
            double pow = Math.pow(1 + tauxMensuel, totalMois);
            mensualite = montantDemande * tauxMensuel * pow / (pow - 1);
        }
        mensualite = Math.round(mensualite * 100.0) / 100.0;

        // Échéance par période = mensualité × 1 / 3 / 6 / 12
        double echeancePeriode = Math.round(mensualite * moisParPeriode * 100.0) / 100.0;

        String dateDebut = null;
        String dateFin = null;
        if (datePremiereEcheance != null && !datePremiereEcheance.isBlank()) {
            try {
                java.time.LocalDate debut = java.time.LocalDate.parse(datePremiereEcheance.trim());
                // dernière échéance = 1ère + (duree-1) × moisParPeriode
                java.time.LocalDate fin = debut.plusMonths((long) moisParPeriode * (duree - 1));
                dateDebut = debut.toString();
                dateFin = fin.toString();
            } catch (Exception e) {
                return SimulationResult.error("Date de première échéance invalide (yyyy-MM-dd)");
            }
        }

        String dateDebutFr = dateDebut != null ? formatDateFr(dateDebut) : "—";
        String dateFinFr = dateFin != null ? formatDateFr(dateFin) : "—";
        String message = String.format(
                "Vous avez demandé la somme de %.2f DT. Chaque période, vous allez rembourser : %.2f DT "
                        + "de la date première échéance (%s) à la date fin échéance (%s). "
                        + "Cette simulation est à caractère indicatif sans valeur contractuelle.",
                montantDemande, echeancePeriode, dateDebutFr, dateFinFr
        );

        SimulationResult result = new SimulationResult();
        result.setMontantMaxCredit(Math.round(montantDemande * 100.0) / 100.0);
        result.setMensualite(echeancePeriode);           // échéance PAR PÉRIODE
        result.setDuree(duree);                          // nb de périodes
        result.setTauxEndettement(tauxAnnuel);
        result.setAvailableMensualite(Math.round(mensualite * 100.0) / 100.0); // mensualité pure
        result.setPlafondEndettementMensuel(TMM);
        result.setGf(marge);

        try {
            result.getClass().getMethod("setMessage", String.class).invoke(result, message);
        } catch (Exception ignored) {
            try {
                result.getClass().getMethod("setLibelle", String.class).invoke(result, message);
            } catch (Exception ignored2) {}
        }
        try {
            result.getClass().getMethod("setDateDebut", String.class).invoke(result, dateDebut);
            result.getClass().getMethod("setDateFin", String.class).invoke(result, dateFin);
        } catch (Exception ignored) {}

        return result;
    }

    private String formatDateFr(String iso) {
        String[] p = iso.split("-");
        if (p.length == 3) return p[2] + "-" + p[1] + "-" + p[0];
        return iso;
    }
    /**
     * Crédit Auto
     */
    public SimulationResult simulateCreditAuto(Map<String, Object> request) {
        double salaire = ((Number) request.getOrDefault("salaireMensuelMoyen", 0)).doubleValue();
        double retenues = ((Number) request.getOrDefault("retenues", 0)).doubleValue();
        double prixVehicule = ((Number) request.getOrDefault("prixVehicule", 0)).doubleValue();
        String typeVehicule = (String) request.getOrDefault("typeVehicule", "Neuf 4CV");
        int duree = ((Number) request.getOrDefault("duree", 60)).intValue();
        double montantDemande = request.get("montantDemande") != null
                ? ((Number) request.get("montantDemande")).doubleValue() : 0;

        // Détermination du % de financement et durée max
        double pourcentageFinancement;
        int maxDuree;

        switch (typeVehicule) {
            case "Neuf 4CV":
                pourcentageFinancement = 0.80;
                maxDuree = 84;
                break;
            case "Neuf 5CV+":
                pourcentageFinancement = 0.60;
                maxDuree = 84;
                break;
            case "Occasion":
                pourcentageFinancement = 0.60;
                maxDuree = 60;
                break;
            default:
                return SimulationResult.error("Type de véhicule invalide");
        }

        if (duree > maxDuree || duree <= 0) {
            return SimulationResult.error("Durée maximale autorisée : " + maxDuree + " mois");
        }

        double tauxAnnuel = TMM + 4.5; // marge standard véhicule

        double totalRevenu = salaire;
        double tauxEndettement = getTauxEndettement(totalRevenu);
        double plafondMensuel = totalRevenu * tauxEndettement;
        double available = Math.max(0, plafondMensuel - retenues);

        if (available <= 0) {
            return SimulationResult.error("Pas de capacité d'endettement disponible");
        }

        // Montant max selon capacité + plafond du véhicule
        double montantMaxCapacite = calculateMontantMax(available, tauxAnnuel, duree);
        double montantMaxVehicule = prixVehicule * pourcentageFinancement;
        double montantMax = Math.min(montantMaxCapacite, montantMaxVehicule);

        SimulationResult result = new SimulationResult();
        result.setTotalRevenuNetMoyen(totalRevenu);
        result.setTauxEndettement(tauxEndettement * 100);
        result.setPlafondEndettementMensuel(plafondMensuel);
        result.setMontantMaxCredit(Math.round(montantMax * 100.0) / 100.0);
        result.setDuree(duree);

        if (montantDemande > 0) {
            if (montantDemande > montantMax) {
                result.setError("Montant demandé dépasse le maximum possible");
            } else {
                double mensualite = calculateMensualite(montantDemande, tauxAnnuel, duree);
                result.setMensualite(Math.round(mensualite * 100.0) / 100.0);
                result.setTauxEndettementCalcule(
                        Math.round((mensualite + retenues) / totalRevenu * 10000.0) / 100.0
                );
            }
        }

        return result;
    }
    /**
     * Financement Immobilier TWENSA
     * - Financement max : 80 % du coût
     * - Autofinancement min : 20 %
     * - Durée max : 15 ans (Acquisition/Construction/Terrain) | 12 ans (Aménagement)
     */
    public SimulationResult simulateTwensa(Map<String, Object> request) {
        double salaire = ((Number) request.getOrDefault("salaireMensuelMoyen", 0)).doubleValue();
        double retenues = ((Number) request.getOrDefault("retenues", 0)).doubleValue();
        double coutInvestissement = ((Number) request.getOrDefault("coutInvestissement", 0)).doubleValue();
        String typeProjet = (String) request.getOrDefault("typeProjet", "Acquisition");
        int duree = ((Number) request.getOrDefault("duree", 120)).intValue();
        double montantDemande = request.get("montantDemande") != null
                ? ((Number) request.get("montantDemande")).doubleValue() : 0;

        // Durée max selon type
        int maxDuree = typeProjet.equalsIgnoreCase("Aménagement") ? 144 : 180;
        if (duree > maxDuree || duree <= 0) {
            return SimulationResult.error("Durée maximale autorisée : " + maxDuree + " mois");
        }

        // Taux (TMM + marge préférentielle TWENSA)
        double tauxAnnuel = TMM + 2.00; // taux privilégié

        double totalRevenu = salaire;
        double tauxEndettement = getTauxEndettement(totalRevenu);
        double plafondMensuel = totalRevenu * tauxEndettement;
        double available = Math.max(0, plafondMensuel - retenues);

        if (available <= 0) {
            return SimulationResult.error("Pas de capacité d'endettement disponible");
        }

        // 80 % du coût de l'investissement
        double montantMaxProjet = coutInvestissement * 0.80;
        double montantMaxCapacite = calculateMontantMax(available, tauxAnnuel, duree);
        double montantMax = Math.min(montantMaxProjet, montantMaxCapacite);

        // Autofinancement minimum 20 %
        double autofinancementMin = coutInvestissement * 0.20;

        SimulationResult result = new SimulationResult();
        result.setTotalRevenuNetMoyen(totalRevenu);
        result.setTauxEndettement(tauxEndettement * 100);
        result.setPlafondEndettementMensuel(plafondMensuel);
        result.setMontantMaxCredit(Math.round(montantMax * 100.0) / 100.0);
        result.setDuree(duree);
        // On peut stocker l'autofinancement dans un champ existant ou l'ajouter plus tard
        result.setAvailableMensualite(Math.round(autofinancementMin * 100.0) / 100.0); // temporaire

        if (montantDemande > 0) {
            if (montantDemande > montantMax) {
                result.setError("Montant demandé dépasse le maximum possible (80 % du projet)");
            } else {
                double mensualite = calculateMensualite(montantDemande, tauxAnnuel, duree);
                result.setMensualite(Math.round(mensualite * 100.0) / 100.0);
                result.setTauxEndettementCalcule(
                        Math.round((mensualite + retenues) / totalRevenu * 10000.0) / 100.0
                );
            }
        }
        return result;
    }

    /**
     * Programme Premier Logement
     * - Prix max logement : 220 000 DT HT
     * - Dotation : 20 % du coût (plafond 44 000 DT) – taux 2 % – grâce 5 ans
     * - Crédit : jusqu'à 80 % (plafond 160 000 DT)
     */
    public SimulationResult simulatePremierLogement(Map<String, Object> request) {
        double salaire = ((Number) request.getOrDefault("salaireMensuelMoyen", 0)).doubleValue();
        double retenues = ((Number) request.getOrDefault("retenues", 0)).doubleValue();
        double prixLogement = ((Number) request.getOrDefault("prixLogement", 0)).doubleValue();
        int duree = ((Number) request.getOrDefault("duree", 180)).intValue();
        double montantDemande = request.get("montantDemande") != null
                ? ((Number) request.get("montantDemande")).doubleValue() : 0;

        // Prix max réglementaire
        if (prixLogement > 220000) {
            return SimulationResult.error("Le prix du logement ne doit pas dépasser 220 000 DT HT");
        }

        if (duree <= 0 || duree > 240) { // flexibilité jusqu'à 20 ans possible
            return SimulationResult.error("Durée invalide");
        }

        // Dotation (20 % avec plafond 44 000 DT)
        double dotation = Math.min(prixLogement * 0.20, 44000);

        // Crédit max (80 % avec plafond 160 000 DT)
        double creditMaxReglementaire = Math.min(prixLogement * 0.80, 160000);

        double totalRevenu = salaire;
        double tauxEndettement = getTauxEndettement(totalRevenu);
        double plafondMensuel = totalRevenu * tauxEndettement;
        double available = Math.max(0, plafondMensuel - retenues);

        if (available <= 0) {
            return SimulationResult.error("Pas de capacité d'endettement disponible");
        }

        // Taux pour le crédit principal (TMM + marge)
        double tauxAnnuel = TMM + 2.25;
        double montantMaxCapacite = calculateMontantMax(available, tauxAnnuel, duree);
        double montantMaxCredit = Math.min(creditMaxReglementaire, montantMaxCapacite);

        SimulationResult result = new SimulationResult();
        result.setTotalRevenuNetMoyen(totalRevenu);
        result.setTauxEndettement(tauxEndettement * 100);
        result.setPlafondEndettementMensuel(plafondMensuel);
        result.setMontantMaxCredit(Math.round(montantMaxCredit * 100.0) / 100.0);
        result.setDuree(duree);
        // On stocke la dotation dans availableMensualite temporairement
        result.setAvailableMensualite(Math.round(dotation * 100.0) / 100.0);

        if (montantDemande > 0) {
            if (montantDemande > montantMaxCredit) {
                result.setError("Montant demandé dépasse le maximum possible");
            } else {
                double mensualite = calculateMensualite(montantDemande, tauxAnnuel, duree);
                result.setMensualite(Math.round(mensualite * 100.0) / 100.0);
                result.setTauxEndettementCalcule(
                        Math.round((mensualite + retenues) / totalRevenu * 10000.0) / 100.0
                );
            }
        }
        return result;
    }
    /**
     * Création de nouveaux projets
     */
    public SimulationResult simulateCreationProjets(Map<String, Object> request) {
        double chiffreAffaires = ((Number) request.getOrDefault("chiffreAffaires", 0)).doubleValue();
        double coutInvestissement = ((Number) request.getOrDefault("coutInvestissement", 0)).doubleValue();
        String typeProjet = (String) request.getOrDefault("typeProjet", "Industriel");
        String typeCredit = (String) request.getOrDefault("typeCredit", "Investissement");
        int duree = ((Number) request.getOrDefault("duree", 84)).intValue();
        double montantDemande = request.get("montantDemande") != null
                ? ((Number) request.get("montantDemande")).doubleValue() : 0;

        // Pourcentage de financement
        double pourcentage;
        int maxDuree;

        if (typeCredit.equalsIgnoreCase("Participatif")) {
            pourcentage = 1.0;
            maxDuree = 84; // 7 ans
        } else if (typeProjet.equalsIgnoreCase("Services")) {
            pourcentage = 0.75;
            maxDuree = 144; // 12 ans
        } else {
            pourcentage = 0.70; // Industriel / Commercial
            maxDuree = 144;
        }

        if (duree > maxDuree || duree < 84) {
            return SimulationResult.error("Durée doit être entre 7 et 12 ans (84 à 144 mois)");
        }

        double tauxAnnuel = TMM + 2.75; // marge entreprises

        // Capacité basée sur le chiffre d'affaires (hypothèse simplifiée)
        double capaciteMensuelle = chiffreAffaires * 0.15; // 15 % du CA mensuel approximatif
        double montantMaxCapacite = calculateMontantMax(capaciteMensuelle, tauxAnnuel, duree);
        double montantMaxProjet = coutInvestissement * pourcentage;
        double montantMax = Math.min(montantMaxCapacite, montantMaxProjet);

        SimulationResult result = new SimulationResult();
        result.setTotalRevenuNetMoyen(chiffreAffaires);
        result.setTauxEndettement(15.0);
        result.setMontantMaxCredit(Math.round(montantMax * 100.0) / 100.0);
        result.setDuree(duree);
        result.setAvailableMensualite(Math.round(coutInvestissement * (1 - pourcentage) * 100.0) / 100.0); // apport minimum

        if (montantDemande > 0) {
            if (montantDemande > montantMax) {
                result.setError("Montant demandé dépasse le maximum possible");
            } else {
                double mensualite = calculateMensualite(montantDemande, tauxAnnuel, duree);
                result.setMensualite(Math.round(mensualite * 100.0) / 100.0);
            }
        }
        return result;
    }
    /**
     * 2. Extension et Développement
     * - CMT : jusqu’à 80 %, max 7 ans
     * - Long terme : jusqu’à 70 %, max 12 ans
     */
    public SimulationResult simulateExtensionDeveloppement(Map<String, Object> request) {
        double chiffreAffaires = ((Number) request.getOrDefault("chiffreAffaires", 0)).doubleValue();
        double coutInvestissement = ((Number) request.getOrDefault("coutInvestissement", 0)).doubleValue();
        String typeCredit = (String) request.getOrDefault("typeCredit", "CMT");
        int duree = ((Number) request.getOrDefault("duree", 60)).intValue();
        double montantDemande = request.get("montantDemande") != null
                ? ((Number) request.get("montantDemande")).doubleValue() : 0;

        double pourcentage;
        int maxDuree;

        if ("LongTerme".equalsIgnoreCase(typeCredit)) {
            pourcentage = 0.70;
            maxDuree = 144; // 12 ans
        } else {
            // CMT (Moyen Terme)
            pourcentage = 0.80;
            maxDuree = 84; // 7 ans
        }

        if (duree <= 0 || duree > maxDuree) {
            return SimulationResult.error("Durée maximale autorisée : " + maxDuree + " mois");
        }

        double tauxAnnuel = TMM + 2.50;

        double capaciteMensuelle = (chiffreAffaires / 12.0) * 0.15;
        double montantMaxCapacite = calculateMontantMax(capaciteMensuelle, tauxAnnuel, duree);
        double montantMaxProjet = coutInvestissement * pourcentage;
        double montantMax = Math.min(montantMaxCapacite, montantMaxProjet);

        SimulationResult result = new SimulationResult();
        result.setTotalRevenuNetMoyen(chiffreAffaires);
        result.setTauxEndettement(15.0);
        result.setMontantMaxCredit(Math.round(montantMax * 100.0) / 100.0);
        result.setDuree(duree);
        result.setAvailableMensualite(Math.round(coutInvestissement * (1 - pourcentage) * 100.0) / 100.0);

        if (montantDemande > 0) {
            if (montantDemande > montantMax) {
                result.setError("Montant demandé dépasse le maximum possible");
            } else {
                double mensualite = calculateMensualite(montantDemande, tauxAnnuel, duree);
                result.setMensualite(Math.round(mensualite * 100.0) / 100.0);
            }
        }
        return result;
    }
    /**
     * Caution de la BNA
     * Calcul de la commission de caution selon le type et le montant garanti
     */
    public SimulationResult simulateCautionBna(Map<String, Object> request) {
        double montantMarche = ((Number) request.getOrDefault("montantMarche", 0)).doubleValue();
        String typeCaution = (String) request.getOrDefault("typeCaution", "Provisoire");
        int dureeMois = ((Number) request.getOrDefault("dureeMois", 12)).intValue();
        double montantDemande = request.get("montantDemande") != null
                ? ((Number) request.get("montantDemande")).doubleValue() : 0;

        if (montantMarche <= 0) {
            return SimulationResult.error("Le montant du marché / engagement doit être positif");
        }

        // Pourcentage de couverture + taux de commission annuel selon le type
        double pourcentageCouverture;
        double tauxCommissionAnnuel; // en %

        switch (typeCaution) {
            case "Provisoire":
                pourcentageCouverture = 0.02; // 1 à 3 % typique → on prend 2 %
                tauxCommissionAnnuel = 1.0;
                break;
            case "Definitive":
                pourcentageCouverture = 0.10; // 5 à 10 %
                tauxCommissionAnnuel = 1.5;
                break;
            case "RetenueGarantie":
                pourcentageCouverture = 0.05;
                tauxCommissionAnnuel = 1.2;
                break;
            case "Avance":
                pourcentageCouverture = 0.15; // souvent 10-20 %
                tauxCommissionAnnuel = 1.8;
                break;
            case "Douaniere":
                pourcentageCouverture = 1.0;  // 100 % des droits
                tauxCommissionAnnuel = 1.0;
                break;
            case "Aval":
                pourcentageCouverture = 1.0;
                tauxCommissionAnnuel = 2.0;
                break;
            case "Etranger":
                pourcentageCouverture = 0.10;
                tauxCommissionAnnuel = 2.5;
                break;
            default:
                pourcentageCouverture = 0.10;
                tauxCommissionAnnuel = 1.5;
        }

        // Montant maximum de la caution
        double montantMaxCaution = montantMarche * pourcentageCouverture;

        // Commission totale estimée (prorata temporis)
        double commission = montantMaxCaution * (tauxCommissionAnnuel / 100.0) * (dureeMois / 12.0);

        SimulationResult result = new SimulationResult();
        result.setTotalRevenuNetMoyen(montantMarche);
        result.setTauxEndettement(tauxCommissionAnnuel);
        result.setMontantMaxCredit(Math.round(montantMaxCaution * 100.0) / 100.0);
        result.setDuree(dureeMois);
        result.setMensualite(Math.round(commission * 100.0) / 100.0); // on utilise mensualite pour la commission
        result.setAvailableMensualite(Math.round(pourcentageCouverture * 10000.0) / 100.0); // % couverture

        if (montantDemande > 0) {
            if (montantDemande > montantMaxCaution) {
                result.setError("Montant de caution demandé dépasse le maximum possible");
            } else {
                double commissionDemandee = montantDemande * (tauxCommissionAnnuel / 100.0) * (dureeMois / 12.0);
                result.setMensualite(Math.round(commissionDemandee * 100.0) / 100.0);
                result.setMontantMaxCredit(Math.round(montantDemande * 100.0) / 100.0);
            }
        }

        return result;
    }
    /**
     * 4. Couverture des besoins d’exploitation
     * Types : Saisonnier, Trésorerie, Stock, Export, Marchés
     */
    public SimulationResult simulateBesoinsExploitation(Map<String, Object> request) {
        double chiffreAffaires = ((Number) request.getOrDefault("chiffreAffaires", 0)).doubleValue();
        double montantBase = ((Number) request.getOrDefault("montantBase", 0)).doubleValue(); // stock, factures, achats...
        String typeCredit = (String) request.getOrDefault("typeCredit", "FaciliteCaisse");
        int duree = ((Number) request.getOrDefault("duree", 12)).intValue();
        double montantDemande = request.get("montantDemande") != null
                ? ((Number) request.get("montantDemande")).doubleValue() : 0;

        double pourcentage;
        int maxDuree;
        double tauxAnnuel = TMM + 3.0; // marge court terme plus élevée

        switch (typeCredit) {
            case "CreditCampagne":
            case "AvanceMarchandises":
                pourcentage = 0.80;
                maxDuree = 12;
                break;
            case "AvanceAttestation":
            case "AvanceFacture":
                pourcentage = 0.80;
                maxDuree = 12;
                break;
            case "PrefinancementMarche":
                pourcentage = 0.10;
                maxDuree = 12;
                break;
            case "FaciliteCaisse":
                // 15 jours à 1 mois de CA → on prend 1 mois
                pourcentage = 1.0 / 12.0;
                maxDuree = 12;
                montantBase = chiffreAffaires; // base = CA annuel
                break;
            case "FinancementStock":
                // max 3 mois d’achats
                pourcentage = 0.25; // approximation 3/12
                maxDuree = 12;
                break;
            case "PrefinancementExport":
                pourcentage = 0.30;
                maxDuree = 12;
                break;
            case "MCNE":
            case "EscompteExport":
                pourcentage = 0.70; // estimation
                maxDuree = 12;
                break;
            default:
                pourcentage = 0.50;
                maxDuree = 12;
        }

        if (duree <= 0 || duree > maxDuree) {
            return SimulationResult.error("Durée maximale autorisée : " + maxDuree + " mois");
        }

        double montantMax = montantBase * pourcentage;

        // Capacité simplifiée
        double capaciteMensuelle = (chiffreAffaires / 12.0) * 0.20;
        double montantMaxCapacite = calculateMontantMax(capaciteMensuelle, tauxAnnuel, duree);
        montantMax = Math.min(montantMax, montantMaxCapacite);

        SimulationResult result = new SimulationResult();
        result.setTotalRevenuNetMoyen(chiffreAffaires);
        result.setTauxEndettement(20.0);
        result.setMontantMaxCredit(Math.round(montantMax * 100.0) / 100.0);
        result.setDuree(duree);
        result.setAvailableMensualite(Math.round(pourcentage * 10000.0) / 100.0); // % financement

        if (montantDemande > 0) {
            if (montantDemande > montantMax) {
                result.setError("Montant demandé dépasse le maximum possible");
            } else {
                double mensualite = calculateMensualite(montantDemande, tauxAnnuel, duree);
                result.setMensualite(Math.round(mensualite * 100.0) / 100.0);
            }
        }
        return result;
    }
    /**
     * Crédits de Campagne
     */
    public SimulationResult simulateCreditsCampagne(Map<String, Object> request) {
        double revenuAnnuel = ((Number) request.getOrDefault("revenuAnnuel", 0)).doubleValue();
        double nombreUnites = ((Number) request.getOrDefault("nombreUnites", 0)).doubleValue();
        double baremeUnitaire = ((Number) request.getOrDefault("baremeUnitaire", 0)).doubleValue();
        int duree = ((Number) request.getOrDefault("duree", 12)).intValue();
        double montantDemande = request.get("montantDemande") != null
                ? ((Number) request.get("montantDemande")).doubleValue() : 0;

        if (duree <= 0 || duree > 18) {
            return SimulationResult.error("Durée maximale recommandée : 12 à 18 mois");
        }

        double tauxAnnuel = TMM + 2.0;
        double montantMaxBareme = nombreUnites * baremeUnitaire;
        double capaciteMensuelle = (revenuAnnuel / 12.0) * 0.40;
        double montantMaxCapacite = calculateMontantMax(capaciteMensuelle, tauxAnnuel, duree);
        double montantMax = Math.min(montantMaxBareme > 0 ? montantMaxBareme : montantMaxCapacite, montantMaxCapacite);

        SimulationResult result = new SimulationResult();
        result.setTotalRevenuNetMoyen(revenuAnnuel);
        result.setTauxEndettement(40.0);
        result.setMontantMaxCredit(Math.round(montantMax * 100.0) / 100.0);
        result.setDuree(duree);
        result.setAvailableMensualite(Math.round(montantMaxBareme * 100.0) / 100.0);

        if (montantDemande > 0) {
            if (montantDemande > montantMax) {
                result.setError("Montant demandé dépasse le maximum possible");
            } else {
                double mensualite = calculateMensualite(montantDemande, tauxAnnuel, duree);
                result.setMensualite(Math.round(mensualite * 100.0) / 100.0);
            }
        }
        return result;
    }

    /**
     * Crédits d'Investissement Agricole
     */
    public SimulationResult simulateCreditsInvestissement(Map<String, Object> request) {
        double revenuAnnuel = ((Number) request.getOrDefault("revenuAnnuel", 0)).doubleValue();
        double coutInvestissement = ((Number) request.getOrDefault("coutInvestissement", 0)).doubleValue();
        String typeInvestissement = (String) request.getOrDefault("typeInvestissement", "Infrastructure");
        int duree = ((Number) request.getOrDefault("duree", 84)).intValue();
        double montantDemande = request.get("montantDemande") != null
                ? ((Number) request.get("montantDemande")).doubleValue() : 0;

        double pourcentageCredit = 0.70;
        int maxDuree = 84;
        int minDuree = 36;

        switch (typeInvestissement) {
            case "Forage":
            case "Puits":
            case "Plantations":
            case "Barques":
            case "Aquaculture":
                maxDuree = 144;
                minDuree = 84;
                break;
            case "Genisses":
                maxDuree = 36;
                minDuree = 36;
                break;
            default:
                maxDuree = 84;
                minDuree = 36;
        }

        if (duree < minDuree || duree > maxDuree) {
            return SimulationResult.error("Durée autorisée : " + minDuree + " à " + maxDuree + " mois");
        }

        double tauxAnnuel = TMM + 2.25;
        double montantMaxProjet = coutInvestissement * pourcentageCredit;
        double capaciteMensuelle = (revenuAnnuel / 12.0) * 0.35;
        double montantMaxCapacite = calculateMontantMax(capaciteMensuelle, tauxAnnuel, duree);
        double montantMax = Math.min(montantMaxProjet, montantMaxCapacite);

        SimulationResult result = new SimulationResult();
        result.setTotalRevenuNetMoyen(revenuAnnuel);
        result.setTauxEndettement(35.0);
        result.setMontantMaxCredit(Math.round(montantMax * 100.0) / 100.0);
        result.setDuree(duree);
        result.setAvailableMensualite(Math.round(coutInvestissement * 0.30 * 100.0) / 100.0);

        if (montantDemande > 0) {
            if (montantDemande > montantMax) {
                result.setError("Montant demandé dépasse le maximum possible (70 % du projet)");
            } else {
                double mensualite = calculateMensualite(montantDemande, tauxAnnuel, duree);
                result.setMensualite(Math.round(mensualite * 100.0) / 100.0);
            }
        }
        return result;
    }
    /**
     * Crédits Supervisés (Campagne ou Investissement)
     * Bénéficiaires : agriculteurs / pêcheurs catégories A et B
     * Instruction CRDA + financement BNA
     */
    public SimulationResult simulateCreditsSupervises(Map<String, Object> request) {
        double revenuAnnuel = ((Number) request.getOrDefault("revenuAnnuel", 0)).doubleValue();
        double coutOuBesoin = ((Number) request.getOrDefault("coutOuBesoin", 0)).doubleValue();
        String typeCredit = (String) request.getOrDefault("typeCredit", "Campagne");
        String categorie = (String) request.getOrDefault("categorie", "A");
        int duree = ((Number) request.getOrDefault("duree", 12)).intValue();
        double montantDemande = request.get("montantDemande") != null
                ? ((Number) request.get("montantDemande")).doubleValue() : 0;

        double pourcentage;
        int maxDuree;
        double tauxAnnuel;

        if ("Investissement".equalsIgnoreCase(typeCredit)) {
            pourcentage = 0.70;          // 60-70 % typique investissement
            maxDuree = 144;              // moyen / long terme jusqu'à 12 ans
            tauxAnnuel = TMM + 2.0;      // taux préférentiel supervisé
            if (duree < 36 || duree > maxDuree) {
                return SimulationResult.error("Durée autorisée pour Investissement : 36 à 144 mois");
            }
        } else {
            // Campagne supervisée
            pourcentage = 0.80;
            maxDuree = 18;
            tauxAnnuel = TMM + 1.75;
            if (duree <= 0 || duree > maxDuree) {
                return SimulationResult.error("Durée autorisée pour Campagne : 1 à 18 mois");
            }
        }

        // Capacité un peu plus souple (accompagnement CRDA + BNA)
        double tauxCapacite = "A".equalsIgnoreCase(categorie) ? 0.45 : 0.40;
        double capaciteMensuelle = (revenuAnnuel / 12.0) * tauxCapacite;
        double montantMaxCapacite = calculateMontantMax(capaciteMensuelle, tauxAnnuel, duree);
        double montantMaxProjet = coutOuBesoin * pourcentage;
        double montantMax = Math.min(montantMaxCapacite, montantMaxProjet > 0 ? montantMaxProjet : montantMaxCapacite);

        SimulationResult result = new SimulationResult();
        result.setTotalRevenuNetMoyen(revenuAnnuel);
        result.setTauxEndettement(tauxCapacite * 100);
        result.setMontantMaxCredit(Math.round(montantMax * 100.0) / 100.0);
        result.setDuree(duree);
        result.setAvailableMensualite(Math.round(coutOuBesoin * (1 - pourcentage) * 100.0) / 100.0);

        if (montantDemande > 0) {
            if (montantDemande > montantMax) {
                result.setError("Montant demandé dépasse le maximum possible");
            } else {
                double mensualite = calculateMensualite(montantDemande, tauxAnnuel, duree);
                result.setMensualite(Math.round(mensualite * 100.0) / 100.0);
            }
        }
        return result;
    }
    /**
     * Chaînes de Valeur Agricoles – Tripartite ou Interface
     */
    public SimulationResult simulateChainesValeur(Map<String, Object> request) {
        double besoinTotal = ((Number) request.getOrDefault("besoinTotal", 0)).doubleValue();
        double caAgroIndustriel = ((Number) request.getOrDefault("caAgroIndustriel", 0)).doubleValue();
        int nombreAgriculteurs = ((Number) request.getOrDefault("nombreAgriculteurs", 1)).intValue();
        String typeFinancement = (String) request.getOrDefault("typeFinancement", "Tripartite");
        String filiere = (String) request.getOrDefault("filiere", "Cereales");
        int duree = ((Number) request.getOrDefault("duree", 12)).intValue();
        double montantDemande = request.get("montantDemande") != null
                ? ((Number) request.get("montantDemande")).doubleValue() : 0;

        // Court terme d'exploitation en général
        if (duree <= 0 || duree > 24) {
            return SimulationResult.error("Durée recommandée : 1 à 24 mois (cycle d'exploitation)");
        }

        double tauxAnnuel = TMM + 2.25;
        double pourcentage;

        if ("Interface".equalsIgnoreCase(typeFinancement)) {
            // Crédit à l'agro-industriel pour redistribution
            pourcentage = 0.80;
        } else {
            // Tripartite : crédit agriculteurs cautionné par agro-industriel
            pourcentage = 0.85;
        }

        // Capacité liée au CA de l'agro-industriel (caution / remboursement)
        double capaciteMensuelle = (caAgroIndustriel / 12.0) * 0.25;
        double montantMaxCapacite = calculateMontantMax(capaciteMensuelle, tauxAnnuel, duree);
        double montantMaxBesoin = besoinTotal * pourcentage;
        double montantMax = Math.min(montantMaxCapacite, montantMaxBesoin > 0 ? montantMaxBesoin : montantMaxCapacite);

        SimulationResult result = new SimulationResult();
        result.setTotalRevenuNetMoyen(caAgroIndustriel);
        result.setTauxEndettement(25.0);
        result.setMontantMaxCredit(Math.round(montantMax * 100.0) / 100.0);
        result.setDuree(duree);
        result.setAvailableMensualite((double) nombreAgriculteurs); // nb agriculteurs
        result.setGf(Math.round(pourcentage * 10000.0) / 100.0);   // % financement

        if (montantDemande > 0) {
            if (montantDemande > montantMax) {
                result.setError("Montant demandé dépasse le maximum possible");
            } else {
                double mensualite = calculateMensualite(montantDemande, tauxAnnuel, duree);
                result.setMensualite(Math.round(mensualite * 100.0) / 100.0);
            }
        }
        return result;
    }
    /**
     * Crédit d'Investissement Agricole JINENE
     * Montant = 3 × solde min 12 mois  OU  4 × solde min 24 mois
     * Autofinancement min 30 % | Durée max 15 ans
     */
    public SimulationResult simulateCreditJinene(Map<String, Object> request) {
        double soldeMin12 = ((Number) request.getOrDefault("soldeMin12", 0)).doubleValue();
        double soldeMin24 = ((Number) request.getOrDefault("soldeMin24", 0)).doubleValue();
        double coutProjet = ((Number) request.getOrDefault("coutProjet", 0)).doubleValue();
        String baseCalcul = (String) request.getOrDefault("baseCalcul", "12"); // "12" ou "24"
        int duree = ((Number) request.getOrDefault("duree", 120)).intValue();
        double montantDemande = request.get("montantDemande") != null
                ? ((Number) request.get("montantDemande")).doubleValue() : 0;

        if (duree <= 0 || duree > 180) {
            return SimulationResult.error("Durée maximale : 15 ans (180 mois)");
        }

        // Éligibilité préférentielle
        boolean eligiblePref = soldeMin12 >= 20000;

        double montantMaxCompte;
        if ("24".equals(baseCalcul) && soldeMin24 > 0) {
            montantMaxCompte = soldeMin24 * 4;
        } else {
            montantMaxCompte = soldeMin12 * 3;
        }

        // Plafond projet : 70 % (autofinancement 30 %)
        double montantMaxProjet = coutProjet * 0.70;
        double montantMax = Math.min(montantMaxCompte, montantMaxProjet);

        double tauxAnnuel = eligiblePref ? (TMM + 1.50) : (TMM + 2.50);

        SimulationResult result = new SimulationResult();
        result.setTotalRevenuNetMoyen(soldeMin12);
        result.setTauxEndettement(eligiblePref ? 1.0 : 0.0); // flag éligibilité
        result.setMontantMaxCredit(Math.round(montantMax * 100.0) / 100.0);
        result.setDuree(duree);
        result.setAvailableMensualite(Math.round(coutProjet * 0.30 * 100.0) / 100.0); // apport 30 %
        result.setGf(Math.round(montantMaxCompte * 100.0) / 100.0); // plafond lié au compte

        if (montantDemande > 0) {
            if (montantDemande > montantMax) {
                result.setError("Montant demandé dépasse le maximum possible");
            } else {
                double mensualite = calculateMensualite(montantDemande, tauxAnnuel, duree);
                result.setMensualite(Math.round(mensualite * 100.0) / 100.0);
            }
        }
        return result;
    }
    public SimulationResult simulateProTresorerie(Map<String, Object> request) {
        double chiffreAffaires = ((Number) request.getOrDefault("chiffreAffaires", 0)).doubleValue();
        double montantBase = ((Number) request.getOrDefault("montantBase", 0)).doubleValue();
        String typeCredit = (String) request.getOrDefault("typeCredit", "FaciliteCaisse");
        int duree = ((Number) request.getOrDefault("duree", 12)).intValue();
        double montantDemande = request.get("montantDemande") != null
                ? ((Number) request.get("montantDemande")).doubleValue() : 0;

        double pourcentage;
        int maxDuree = 12;
        double tauxAnnuel = TMM + 3.0;

        switch (typeCredit) {
            case "FaciliteCaisse":
                pourcentage = 1.0 / 12.0;
                montantBase = chiffreAffaires;
                break;
            case "Decouvert":
                pourcentage = 1.0;
                break;
            case "AvanceDepots":
                pourcentage = 0.90;
                break;
            case "EscompteCommercial":
                pourcentage = 0.70;
                break;
            default:
                pourcentage = 0.50;
        }

        if (duree <= 0 || duree > maxDuree) {
            return SimulationResult.error("Durée maximale : 12 mois");
        }

        double montantMax = montantBase * pourcentage;
        double capaciteMensuelle = (chiffreAffaires / 12.0) * 0.20;
        double montantMaxCapacite = calculateMontantMax(capaciteMensuelle, tauxAnnuel, duree);
        montantMax = Math.min(montantMax, montantMaxCapacite);

        SimulationResult result = new SimulationResult();
        result.setTotalRevenuNetMoyen(chiffreAffaires);
        result.setTauxEndettement(20.0);
        result.setMontantMaxCredit(Math.round(montantMax * 100.0) / 100.0);
        result.setDuree(duree);
        result.setAvailableMensualite(Math.round(pourcentage * 10000.0) / 100.0);

        if (montantDemande > 0) {
            if (montantDemande > montantMax) {
                result.setError("Montant demandé dépasse le maximum possible");
            } else {
                double mensualite = calculateMensualite(montantDemande, tauxAnnuel, duree);
                result.setMensualite(Math.round(mensualite * 100.0) / 100.0);
            }
        }
        return result;
    }
    /** Stocks & activités saisonnières */
    public SimulationResult simulateProStocks(Map<String, Object> request) {
        double chiffreAffaires = ((Number) request.getOrDefault("chiffreAffaires", 0)).doubleValue();
        double montantBase = ((Number) request.getOrDefault("montantBase", 0)).doubleValue();
        String typeCredit = (String) request.getOrDefault("typeCredit", "FinancementStock");
        int duree = ((Number) request.getOrDefault("duree", 12)).intValue();
        double montantDemande = request.get("montantDemande") != null
                ? ((Number) request.get("montantDemande")).doubleValue() : 0;

        double pourcentage;
        int maxDuree = 12;
        double tauxAnnuel = TMM + 3.0;

        switch (typeCredit) {
            case "FinancementStock":
                pourcentage = 0.25; // ~3 mois d'achats
                break;
            case "CreditCampagne":
            case "AvanceMarchandises":
                pourcentage = 0.80;
                break;
            case "DemarrageHuile":
                pourcentage = 1.0; // limité à capacité stockage (montantBase)
                break;
            default:
                pourcentage = 0.50;
        }

        if (duree <= 0 || duree > maxDuree) {
            return SimulationResult.error("Durée maximale : 12 mois");
        }

        double montantMax = montantBase * pourcentage;
        double capacite = (chiffreAffaires / 12.0) * 0.20;
        montantMax = Math.min(montantMax, calculateMontantMax(capacite, tauxAnnuel, duree));

        return buildProResult(chiffreAffaires, montantMax, pourcentage, duree, montantDemande, tauxAnnuel);
    }

    /** Marchés en Tunisie */
    public SimulationResult simulateProMarchesTn(Map<String, Object> request) {
        double chiffreAffaires = ((Number) request.getOrDefault("chiffreAffaires", 0)).doubleValue();
        double montantBase = ((Number) request.getOrDefault("montantBase", 0)).doubleValue(); // décompte / marché / factures
        String typeCredit = (String) request.getOrDefault("typeCredit", "AvanceAttestation");
        int duree = ((Number) request.getOrDefault("duree", 12)).intValue();
        double montantDemande = request.get("montantDemande") != null
                ? ((Number) request.get("montantDemande")).doubleValue() : 0;

        double pourcentage;
        switch (typeCredit) {
            case "PrefinancementMarche":
                pourcentage = 0.10;
                break;
            case "AvanceAttestation":
            case "AvanceFacture":
            default:
                pourcentage = 0.80;
        }

        if (duree <= 0 || duree > 12) {
            return SimulationResult.error("Durée maximale : 12 mois");
        }

        double tauxAnnuel = TMM + 3.0;
        double montantMax = montantBase * pourcentage;
        double capacite = (chiffreAffaires / 12.0) * 0.20;
        montantMax = Math.min(montantMax, calculateMontantMax(capacite, tauxAnnuel, duree));

        return buildProResult(chiffreAffaires, montantMax, pourcentage, duree, montantDemande, tauxAnnuel);
    }

    /** Marchés à l'export */
    public SimulationResult simulateProExport(Map<String, Object> request) {
        double chiffreAffaires = ((Number) request.getOrDefault("chiffreAffaires", 0)).doubleValue();
        double montantBase = ((Number) request.getOrDefault("montantBase", 0)).doubleValue(); // export prévisionnel / créances
        String typeCredit = (String) request.getOrDefault("typeCredit", "PrefinancementExport");
        int duree = ((Number) request.getOrDefault("duree", 12)).intValue();
        double montantDemande = request.get("montantDemande") != null
                ? ((Number) request.get("montantDemande")).doubleValue() : 0;

        double pourcentage;
        switch (typeCredit) {
            case "PrefinancementExport":
                pourcentage = 0.30;
                break;
            case "MCNE":
            case "EscompteExport":
            default:
                pourcentage = 0.70;
        }

        if (duree <= 0 || duree > 12) {
            return SimulationResult.error("Durée maximale : 12 mois");
        }

        double tauxAnnuel = TMM + 3.0;
        double montantMax = montantBase * pourcentage;
        double capacite = (chiffreAffaires / 12.0) * 0.20;
        montantMax = Math.min(montantMax, calculateMontantMax(capacite, tauxAnnuel, duree));

        return buildProResult(chiffreAffaires, montantMax, pourcentage, duree, montantDemande, tauxAnnuel);
    }

    /** Investissements professionnels */
    public SimulationResult simulateProInvestissement(Map<String, Object> request) {
        double chiffreAffaires = ((Number) request.getOrDefault("chiffreAffaires", 0)).doubleValue();
        double coutInvestissement = ((Number) request.getOrDefault("coutInvestissement", 0)).doubleValue();
        String typeCredit = (String) request.getOrDefault("typeCredit", "Investissement");
        String typeProjet = (String) request.getOrDefault("typeProjet", "Industriel");
        int duree = ((Number) request.getOrDefault("duree", 84)).intValue();
        double montantDemande = request.get("montantDemande") != null
                ? ((Number) request.get("montantDemande")).doubleValue() : 0;

        double pourcentage;
        int maxDuree;
        int minDuree = 36;

        if ("Participatif".equalsIgnoreCase(typeCredit)) {
            pourcentage = 1.0;
            maxDuree = 84;
            minDuree = 84;
        } else if ("FONAPRAM".equalsIgnoreCase(typeCredit)) {
            pourcentage = 0.68; // ~32% dotation
            maxDuree = 84;
            if (coutInvestissement > 100000) {
                return SimulationResult.error("FONAPRAM : coût max 100 000 DT");
            }
        } else {
            pourcentage = "Services".equalsIgnoreCase(typeProjet) ? 0.75 : 0.70;
            maxDuree = 144;
            minDuree = 84;
        }

        if (duree < minDuree || duree > maxDuree) {
            return SimulationResult.error("Durée autorisée : " + minDuree + " à " + maxDuree + " mois");
        }

        double tauxAnnuel = TMM + 2.75;
        double montantMaxProjet = coutInvestissement * pourcentage;
        double capacite = (chiffreAffaires / 12.0) * 0.15;
        double montantMax = Math.min(montantMaxProjet, calculateMontantMax(capacite, tauxAnnuel, duree));

        SimulationResult result = new SimulationResult();
        result.setTotalRevenuNetMoyen(chiffreAffaires);
        result.setTauxEndettement(15.0);
        result.setMontantMaxCredit(Math.round(montantMax * 100.0) / 100.0);
        result.setDuree(duree);
        result.setAvailableMensualite(Math.round(coutInvestissement * (1 - pourcentage) * 100.0) / 100.0);

        if (montantDemande > 0) {
            if (montantDemande > montantMax) {
                result.setError("Montant demandé dépasse le maximum possible");
            } else {
                result.setMensualite(Math.round(calculateMensualite(montantDemande, tauxAnnuel, duree) * 100.0) / 100.0);
            }
        }
        return result;
    }

    /** Cautions & avals (même logique que Caution BNA) */
    public SimulationResult simulateProCautions(Map<String, Object> request) {
        return simulateCautionBna(request); // réutilise ta méthode existante
    }

    /** Helper commun court terme */
    private SimulationResult buildProResult(double ca, double montantMax, double pourcentage,
                                            int duree, double montantDemande, double tauxAnnuel) {
        SimulationResult result = new SimulationResult();
        result.setTotalRevenuNetMoyen(ca);
        result.setTauxEndettement(20.0);
        result.setMontantMaxCredit(Math.round(montantMax * 100.0) / 100.0);
        result.setDuree(duree);
        result.setAvailableMensualite(Math.round(pourcentage * 10000.0) / 100.0);

        if (montantDemande > 0) {
            if (montantDemande > montantMax) {
                result.setError("Montant demandé dépasse le maximum possible");
            } else {
                result.setMensualite(Math.round(calculateMensualite(montantDemande, tauxAnnuel, duree) * 100.0) / 100.0);
            }
        }
        return result;
    }
}