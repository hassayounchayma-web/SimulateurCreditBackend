package tn.bna.simulateur.agent.service;

import tn.bna.simulateur.agent.dto.ChatRequest;
import tn.bna.simulateur.agent.dto.ChatResponse;
import tn.bna.simulateur.agent.dto.SimulateurInfo;
import tn.bna.simulateur.agent.dto.LlmExtraction;
import tn.bna.simulateur.agent.llm.GeminiClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class AgentService {

    // ─── PATTERNS ───
    private static final String NUM = "(\\d{1,3}(?:[\\s.,]\\d{3})+|\\d+[.,]?\\d*)";
    private static final String DEVISE = "\\s*(DT|dt|dinar|dinars|TND|tnd)?";

    private static final Pattern SALAIRE_PATTERN = Pattern.compile(
            "(?:(?:je\\s+)?(?:gagne|perçois|touche)|(?:salaire|revenu|revenus|salaire\\s+mensuel|revenu\\s+mensuel))\\s*(?:mensuel|net|brut)?\\s*(?:est\\s*)?(?:de\\s*)?" + NUM + DEVISE,
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern SALAIRE_PATTERN_2 = Pattern.compile(
            NUM + DEVISE + "\\s*(?:de\\s*)?(?:salaire|revenu|par\\s+mois|mensuel|par\\s+moi)",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern RETENUES_PATTERN = Pattern.compile(
            "(?:retenues?|charges?|crédits?\\s+en\\s+cours|mensualités?\\s+en\\s+cours)\\s*(?:est\\s*)?(?:de\\s*|d['’]?\\s*)?" + NUM + DEVISE,
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern RETENUES_PATTERN_2 = Pattern.compile(
            NUM + DEVISE + "\\s*(?:de\\s*)?(?:retenues?|charges?|crédits?\\s+en\\s+cours)",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern PRIX_VOITURE_PATTERN = Pattern.compile(
            "(?:prix|co[uû]t|valeur|montant)?\\s*(?:de\\s+)?(?:la\\s+|le\\s+)?(?:voiture|v[eé]hicule|auto|automobile)\\s*(?:neuve|neuf|d['’]?occasion|occasion)?\\s*(?:est\\s*)?(?:de\\s*|à\\s*|a\\s*)?" + NUM + DEVISE,
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern VOITURE_PATTERN = Pattern.compile(
            "(?:voiture|v[eé]hicule|auto|automobile)\\s*(?:neuve|neuf|d['’]?occasion|occasion)?\\s*(?:de\\s*|à\\s*|a\\s*)?" + NUM + DEVISE,
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern PRIX_IMMO_PATTERN = Pattern.compile(
            "(?:prix|co[uû]t|valeur|montant)?\\s*(?:de\\s+)?(?:la\\s+|le\\s+)?(?:maison|appartement|logement|bien\\s+immobilier|immeuble|terrain)\\s*(?:est\\s*)?(?:de\\s*|à\\s*|a\\s*)?" + NUM + DEVISE,
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern MAISON_PATTERN = Pattern.compile(
            "(?:maison|appartement|logement|bien\\s+immobilier|immeuble|terrain)\\s*(?:de\\s*|à\\s*)?" + NUM + DEVISE,
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern MONTANT_PATTERN = Pattern.compile(
            "(?:montant|cr[eé]dit|pr[eê]t|financement|demande|achat|projet|investissement)\\s*(?:demand[eé]|du\\s+cr[eé]dit|du\\s+projet)?\\s*(?:est\\s*)?(?:de\\s*|à\\s*)?" + NUM + DEVISE,
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern MONTANT_SIMPLE_PATTERN = Pattern.compile(NUM + DEVISE, Pattern.CASE_INSENSITIVE);
    private static final Pattern DUREE_PATTERN = Pattern.compile(
            "(?:dur[eé]e\\s*(?:de\\s*)?|sur\\s+|pour\\s+(?:une\\s+)?(?:dur[eé]e\\s*(?:de\\s*)?)?)?(\\d+)\\s*(mois|an|ann[eé]e|ans)\\b",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern CHIFFRE_AFFAIRES_PATTERN = Pattern.compile(
            "(?:chiffre\\s+d['’]?affaires|\\bCA\\b)\\s*(?:annuel|pr[eé]visionnel)?\\s*(?:est\\s*)?(?:de\\s*)?" + NUM + DEVISE,
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern REVENU_ANNUEL_PATTERN = Pattern.compile(
            "(?:revenu\\s+annuel|revenus\\s+annuels)\\s*(?:est\\s*)?(?:de\\s*)?" + NUM + DEVISE,
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern CAPITAL_RESTANT_PATTERN = Pattern.compile(
            "(?:capital\\s+restant|solde\\s+restant|cr[eé]dit\\s+restant|reste\\s+[àa]\\s+payer)\\s*(?:est\\s*)?(?:de\\s*|d['’]?)?" + NUM + DEVISE,
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern SOLDE_MIN_PATTERN = Pattern.compile(
            "(?:solde\\s+min(?:imum)?|solde\\s+moyen|moyenne\\s+des\\s+soldes)\\s*(?:sur\\s+\\d+\\s*mois)?\\s*(?:est\\s*)?(?:de\\s*)?" + NUM + DEVISE,
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern LOYERS_PATTERN = Pattern.compile(
            "(?:loyers?|loyer\\s+mensuel)\\s*(?:domicili[eé]s?)?\\s*(?:est\\s*)?(?:de\\s*)?" + NUM + DEVISE,
            Pattern.CASE_INSENSITIVE
    );

    private static final List<String> DETAILS_KEYWORDS = Arrays.asList(
            "détail", "details", "caractéristique", "caractéristiques",
            "conditions", "condition", "document", "documents", "papier", "papiers",
            "info", "informations", "renseignement", "renseignements",
            "qu'est-ce que", "c'est quoi", "explique", "explique-moi",
            "comment ça marche", "comment fonctionne"
    );

    private static final double TMM = 7.08;

    private final Map<String, SimulateurInfo> simulateursMap = new LinkedHashMap<>();
    private final Map<String, List<String>> intentKeywords = new HashMap<>();
    private final Map<String, String> routeMap = new HashMap<>();
    private final Map<String, String> nomMap = new HashMap<>();
    private final Map<String, String> descriptionMap = new HashMap<>();
    private final Map<String, String[]> champsMap = new HashMap<>();
    private final Map<String, CreditDetails> creditDetailsMap = new HashMap<>();
    private final Map<Long, UserContext> sessions = new ConcurrentHashMap<>();

    private final GeminiClient geminiClient;

    private static class UserContext {
        Double salaire;
        Double retenues;
        Double montantDemande;
        Integer dureeMois;
        String typeProjet;
        Double revenusLocatifs;
        Double chiffreAffaires;
        Double loyers;
        Double capitalRestant;
        Double revenuAnnuel;
        Double soldeMin;
        String typeCredit;
        String typeVehicule;
        String typeProjetImmobilier;
        String lastSimulateurKey;
        String currentRoute;
        String lastUserMessage;
        long updatedAt = System.currentTimeMillis();

        boolean hasData() {
            return salaire != null || retenues != null || montantDemande != null
                    || dureeMois != null || chiffreAffaires != null
                    || revenuAnnuel != null || capitalRestant != null || soldeMin != null;
        }

        boolean isComplete() {
            return salaire != null && dureeMois != null && montantDemande != null;
        }
    }

    private static class CreditDetails {
        String nom, description, montantMax, duree, taux, conditions, avantages;
        String[] documents;

        CreditDetails(String nom, String description, String montantMax, String duree,
                      String taux, String conditions, String avantages, String[] documents) {
            this.nom = nom;
            this.description = description;
            this.montantMax = montantMax;
            this.duree = duree;
            this.taux = taux;
            this.conditions = conditions;
            this.avantages = avantages;
            this.documents = documents;
        }
    }

    @Autowired
    public AgentService(GeminiClient geminiClient) {
        this.geminiClient = geminiClient;
        initMaps();
        initCreditDetails();
        initSimulateurs();
        initKeywords();
    }

    // ═══════════════════════════════════════════
    // API
    // ═══════════════════════════════════════════
    public ChatResponse processMessage(ChatRequest request) {
        String raw = request.getMessage() == null ? "" : request.getMessage().trim();
        String message = raw.toLowerCase();
        Long userId = request.getUserId() != null ? request.getUserId() : 0L;
        String currentRoute = request.getCurrentRoute();

        ChatResponse response = new ChatResponse();

        UserContext session = getOrCreateSession(userId);
        applyPageContext(session, currentRoute);
        session.lastUserMessage = raw;

        LlmExtraction llm = null;
        try {
            llm = geminiClient.extract(buildLlmPromptWithMemory(raw, session));
        } catch (Exception ignored) {
        }

        UserContext extracted = extractContext(raw);
        mergeIntoSession(session, extracted);
        mergeLlmIntoContext(session, llm);

        System.out.println("SESSION => salaire=" + session.salaire
                + " retenues=" + session.retenues
                + " montant=" + session.montantDemande
                + " duree=" + session.dureeMois
                + " type=" + session.typeProjet);

        // Gemini → simulateur clair
        if (llm != null && llm.getSimulateurKey() != null
                && !llm.getSimulateurKey().isBlank()
                && !"null".equalsIgnoreCase(llm.getSimulateurKey())
                && routeMap.containsKey(llm.getSimulateurKey())
                && !session.isComplete()) {
            session.lastSimulateurKey = llm.getSimulateurKey();
            return buildNavigateResponse(session, llm.getSimulateurKey(), llm.getMessageUtilisateur());
        }

        // Analyse capacité + recommandations
        if (session.isComplete() || (session.montantDemande != null && session.salaire != null
                && (session.typeProjet != null || session.lastSimulateurKey != null || session.dureeMois != null))) {

            String key = resolveProductKey(session);
            session.lastSimulateurKey = key;

            double montantMax = computeMontantMax(session, key);
            double demande = session.montantDemande != null ? session.montantDemande : 0;

            if (demande > 0 && montantMax > 0 && demande > montantMax * 1.02) {
                ChatResponse reco = new ChatResponse();
                reco.setMessage(buildRecommendations(session, key, montantMax));
                reco.setSuggestedAction("NAVIGATE");
                reco.setActionData(routeMap.get(key));
                reco.setSimulateurInfo(buildSimulateurInfo(key));
                return reco;
            }

            if (session.isComplete() || (demande > 0 && session.salaire != null)) {
                String intro = "J'ai bien relevé vos informations.\n"
                        + "✅ Capacité estimée : **~" + String.format("%.0f", montantMax) + " DT**\n"
                        + (demande > 0
                        ? "📌 Montant demandé : **" + String.format("%.0f", demande) + " DT** — dans les clous.\n"
                        : "");
                return buildNavigateResponse(session, key, intro);
            }
        }

        // Contexte de page
        if (session.lastSimulateurKey != null && routeMap.containsKey(session.lastSimulateurKey)) {
            if (message.matches(".*(simule|calcul|mensualité|capacité|ok|d'accord|continue).*")
                    || (session.hasData() && message.length() < 40)) {
                return buildNavigateResponse(session, session.lastSimulateurKey,
                        "Je me base sur le simulateur de la page en cours et vos données mémorisées.");
            }
        }

        if (isDetailsRequest(message)) {
            return handleDetailsRequest(message);
        }

        if (session.hasData() && !session.isComplete()) {
            return askMissingFields(session);
        }

        String intent = classifyIntent(message);
        switch (intent) {
            case "simulation" -> handleSimulationIntent(message, response);
            case "context" -> {
                if (session.hasData()) return askMissingFields(session);
                handleContextIntent(response);
            }
            case "project" -> handleProjectIntent(message, response);
            case "info" -> handleInfoIntent(message, response);
            case "help" -> handleHelpIntent(response);
            default -> {
                if (session.hasData()) {
                    response.setMessage("🧠 **Je me souviens de votre dossier :**\n"
                            + summarizeContext(session)
                            + "\nComplétez ou indiquez le type de crédit souhaité.");
                    response.setSuggestedAction("ASK_MORE");
                } else {
                    handleDefaultIntent(response);
                }
            }
        }
        return response;
    }

    public void clearSession(Long userId) {
        sessions.remove(userId != null ? userId : 0L);
    }

    private String resolveProductKey(UserContext session) {
        if (session.lastSimulateurKey != null && routeMap.containsKey(session.lastSimulateurKey)) {
            return session.lastSimulateurKey;
        }
        if ("auto".equals(session.typeProjet)) return "credit-auto";
        if ("immobilier".equals(session.typeProjet)) return "credit-immo";
        if ("agricole".equals(session.typeProjet)) return "credits-campagne";
        if ("entreprise".equals(session.typeProjet)) return "creation-projets";
        if ("rachat".equals(session.typeProjet)) return "rachat-interne";
        return "credit-auto";
    }

    // ═══════════════════════════════════════════
    // CAPACITÉ + RECOMMANDATIONS
    // ═══════════════════════════════════════════
    private double resolveTauxAnnuel(String key, Integer dureeMois) {
        if (key == null) key = "credit-auto";
        return switch (key) {
            case "credit-h24" -> TMM + 2.50;
            case "credit-perso" -> TMM + 2.25;
            case "credit-auto" -> TMM + 3.00;
            case "credit-immo", "twensa" -> (dureeMois != null && dureeMois > 84) ? TMM + 3.25 : TMM + 3.00;
            case "premier-logement" -> TMM + 2.25;
            case "sans-rachat", "rachat-interne", "rachat-externe" -> TMM + 2.50;
            case "credits-campagne" -> TMM + 1.75;
            case "credit-jinene" -> TMM + 1.50;
            default -> TMM + 2.50;
        };
    }

    private int resolveMaxDuree(String key, String typeVehicule) {
        if (key == null) return 84;
        return switch (key) {
            case "credit-h24" -> 36;
            case "credit-perso" -> 60;
            case "credit-auto" -> "occasion".equalsIgnoreCase(typeVehicule) ? 60 : 84;
            case "credit-immo", "twensa", "premier-logement" -> 180;
            case "rachat-externe" -> 60;
            case "credits-campagne" -> 18;
            default -> 84;
        };
    }

    private double computeMontantMax(UserContext session, String key) {
        double salaire = session.salaire != null ? session.salaire : 0;
        double retenues = session.retenues != null ? session.retenues : 0;
        int duree = session.dureeMois != null ? session.dureeMois : 60;
        double totalRevenu = salaire + (session.loyers != null ? session.loyers * 0.5 : 0);
        if (totalRevenu <= 0) return 0;

        double tauxEnd = getTauxEndettement(totalRevenu);
        double available = Math.max(0, totalRevenu * tauxEnd - retenues);
        if (available <= 0) return 0;

        return calculateMontantMax(available, resolveTauxAnnuel(key, duree), duree);
    }

    private double calculateMontantMax(double maxMensualite, double tauxAnnuel, int dureeMois) {
        if (dureeMois <= 0 || maxMensualite <= 0) return 0.0;
        BigDecimal r = BigDecimal.valueOf(tauxAnnuel / 100.0 / 12.0);
        if (r.compareTo(BigDecimal.ZERO) == 0) return maxMensualite * dureeMois;
        BigDecimal temp = BigDecimal.ONE.add(r).pow(dureeMois);
        return BigDecimal.valueOf(maxMensualite)
                .multiply(temp.subtract(BigDecimal.ONE))
                .divide(r.multiply(temp), 2, RoundingMode.HALF_UP)
                .doubleValue();
    }

    private double calculateMensualite(double principal, double tauxAnnuel, int dureeMois) {
        if (dureeMois <= 0 || principal <= 0) return 0.0;
        BigDecimal r = BigDecimal.valueOf(tauxAnnuel / 100.0 / 12.0);
        BigDecimal temp = BigDecimal.ONE.add(r).pow(dureeMois);
        return BigDecimal.valueOf(principal)
                .multiply(r.multiply(temp))
                .divide(temp.subtract(BigDecimal.ONE), 2, RoundingMode.HALF_UP)
                .doubleValue();
    }

    private String buildRecommendations(UserContext session, String key, double montantMax) {
        double demande = session.montantDemande != null ? session.montantDemande : 0;
        double retenues = session.retenues != null ? session.retenues : 0;
        int duree = session.dureeMois != null ? session.dureeMois : 60;
        int maxDuree = resolveMaxDuree(key, session.typeVehicule);
        double tauxAnnuel = resolveTauxAnnuel(key, duree);

        StringBuilder sb = new StringBuilder();
        sb.append("⚠️ **Le montant demandé dépasse votre capacité estimée.**\n\n");
        sb.append("📊 **Analyse :**\n");
        sb.append(" • Montant demandé : **").append(String.format("%.0f", demande)).append(" DT**\n");
        sb.append(" • Montant max estimé : **").append(String.format("%.0f", montantMax)).append(" DT**\n");
        sb.append(" • Écart : **").append(String.format("%.0f", demande - montantMax)).append(" DT**\n\n");
        sb.append(summarizeContext(session)).append("\n");
        sb.append("💡 **Recommandations pour obtenir ce crédit :**\n\n");

        // 1) Allonger la durée
        if (duree < maxDuree) {
            UserContext tmp = copyContext(session);
            boolean found = false;
            for (int d = duree + 6; d <= maxDuree; d += 6) {
                tmp.dureeMois = d;
                double maxLongue = computeMontantMax(tmp, key);
                if (maxLongue >= demande) {
                    double mens = calculateMensualite(demande, resolveTauxAnnuel(key, d), d);
                    sb.append("1️⃣ **Allonger la durée à ").append(d).append(" mois**\n");
                    sb.append("   → Mensualité estimée : ~").append(String.format("%.0f", mens)).append(" DT\n");
                    sb.append("   → Capacité max : ~").append(String.format("%.0f", maxLongue)).append(" DT\n\n");
                    found = true;
                    break;
                }
            }
            if (!found) {
                tmp.dureeMois = maxDuree;
                double maxLongue = computeMontantMax(tmp, key);
                sb.append("1️⃣ **Allonger la durée au maximum (").append(maxDuree).append(" mois)**\n");
                sb.append("   → Capacité max : ~").append(String.format("%.0f", maxLongue)).append(" DT\n\n");
            }
        }

        // 2) Réduire le montant
        if (montantMax > 0) {
            double mens = calculateMensualite(montantMax, tauxAnnuel, duree);
            sb.append("2️⃣ **Réduire le montant à ~").append(String.format("%.0f", montantMax)).append(" DT**\n");
            sb.append("   → Mensualité estimée : ~").append(String.format("%.0f", mens)).append(" DT\n");
            sb.append("   → Apport / reste à charge : ~")
                    .append(String.format("%.0f", Math.max(0, demande - montantMax))).append(" DT\n\n");
        }

        // 3) Baisser retenues
        if (retenues > 0) {
            UserContext tmp = copyContext(session);
            tmp.retenues = Math.max(0, retenues * 0.5);
            double max2 = computeMontantMax(tmp, key);
            sb.append("3️⃣ **Réduire les retenues / crédits en cours**\n");
            sb.append("   → Avec retenues à ~").append(String.format("%.0f", tmp.retenues))
                    .append(" DT, capacité ≈ ").append(String.format("%.0f", max2)).append(" DT\n\n");
        }

        // 4) Apport
        if ("auto".equals(session.typeProjet) || "immobilier".equals(session.typeProjet) || key.contains("immo") || key.contains("auto")) {
            double apport = Math.max(0, demande - montantMax);
            double pct = demande > 0 ? (apport / demande) * 100 : 0;
            sb.append("4️⃣ **Augmenter l'apport personnel**\n");
            sb.append("   → Apport suggéré : ~").append(String.format("%.0f", apport))
                    .append(" DT (").append(String.format("%.0f", pct)).append(" %)\n\n");
        }

        // 5) Alternatives produit
        if ("auto".equals(session.typeProjet) && demande <= 20000) {
            sb.append("5️⃣ **Alternative : Crédit H24** (besoin ≤ 20 000 DT, réponse rapide)\n\n");
        }
        if ("immobilier".equals(session.typeProjet)) {
            sb.append("5️⃣ **Regardez aussi TWENSA / Premier Logement** selon votre profil\n\n");
        }

        sb.append("📌 Simulation **indicative** — offre définitive en agence BNA.\n");
        sb.append("🔗 Utilisez le bouton pour affiner dans le simulateur.");
        return sb.toString();
    }

    private UserContext copyContext(UserContext s) {
        UserContext c = new UserContext();
        c.salaire = s.salaire;
        c.retenues = s.retenues;
        c.montantDemande = s.montantDemande;
        c.dureeMois = s.dureeMois;
        c.loyers = s.loyers;
        c.typeProjet = s.typeProjet;
        c.typeVehicule = s.typeVehicule;
        c.lastSimulateurKey = s.lastSimulateurKey;
        return c;
    }

    // ═══════════════════════════════════════════
    // SESSION
    // ═══════════════════════════════════════════
    private UserContext getOrCreateSession(Long userId) {
        return sessions.computeIfAbsent(userId != null ? userId : 0L, k -> new UserContext());
    }

    private void applyPageContext(UserContext ctx, String currentRoute) {
        if (currentRoute == null || currentRoute.isBlank()) return;
        ctx.currentRoute = currentRoute;
        if (currentRoute.contains("credit-auto")) ctx.lastSimulateurKey = "credit-auto";
        else if (currentRoute.contains("credit-h24")) ctx.lastSimulateurKey = "credit-h24";
        else if (currentRoute.contains("credit-perso")) ctx.lastSimulateurKey = "credit-perso";
        else if (currentRoute.contains("credit-immo")) ctx.lastSimulateurKey = "credit-immo";
        else if (currentRoute.contains("twensa")) ctx.lastSimulateurKey = "twensa";
        else if (currentRoute.contains("premier-logement")) ctx.lastSimulateurKey = "premier-logement";
        else if (currentRoute.contains("sans-rachat")) ctx.lastSimulateurKey = "sans-rachat";
        else if (currentRoute.contains("rachat-interne")) ctx.lastSimulateurKey = "rachat-interne";
        else if (currentRoute.contains("rachat-externe")) ctx.lastSimulateurKey = "rachat-externe";
        else if (currentRoute.contains("credits-campagne")) ctx.lastSimulateurKey = "credits-campagne";
        else if (currentRoute.contains("credits-investissement")) ctx.lastSimulateurKey = "credits-investissement";
        else if (currentRoute.contains("credit-jinene") || currentRoute.contains("jinene")) ctx.lastSimulateurKey = "credit-jinene";
        else if (currentRoute.contains("pro-tresorerie")) ctx.lastSimulateurKey = "pro-tresorerie";
        else if (currentRoute.contains("creation-projets")) ctx.lastSimulateurKey = "creation-projets";
        else if (currentRoute.contains("caution-bna")) ctx.lastSimulateurKey = "caution-bna";
    }

    private void mergeIntoSession(UserContext session, UserContext extracted) {
        if (extracted.salaire != null) session.salaire = extracted.salaire;
        if (extracted.retenues != null) session.retenues = extracted.retenues;
        if (extracted.montantDemande != null) session.montantDemande = extracted.montantDemande;
        if (extracted.dureeMois != null) session.dureeMois = extracted.dureeMois;
        if (extracted.typeProjet != null) session.typeProjet = extracted.typeProjet;
        if (extracted.chiffreAffaires != null) session.chiffreAffaires = extracted.chiffreAffaires;
        if (extracted.revenuAnnuel != null) session.revenuAnnuel = extracted.revenuAnnuel;
        if (extracted.capitalRestant != null) session.capitalRestant = extracted.capitalRestant;
        if (extracted.soldeMin != null) session.soldeMin = extracted.soldeMin;
        if (extracted.typeVehicule != null) session.typeVehicule = extracted.typeVehicule;
        if (extracted.typeCredit != null) session.typeCredit = extracted.typeCredit;
        if (extracted.loyers != null) session.loyers = extracted.loyers;
        session.updatedAt = System.currentTimeMillis();
    }

    private void mergeLlmIntoContext(UserContext ctx, LlmExtraction llm) {
        if (llm == null) return;
        if (llm.getSalaire() != null) ctx.salaire = llm.getSalaire();
        if (llm.getRetenues() != null) ctx.retenues = llm.getRetenues();
        if (llm.getMontant() != null) ctx.montantDemande = llm.getMontant();
        if (llm.getDureeMois() != null) ctx.dureeMois = llm.getDureeMois();
        if (llm.getTypeProjet() != null && !llm.getTypeProjet().isBlank()
                && !"null".equalsIgnoreCase(llm.getTypeProjet())) {
            ctx.typeProjet = llm.getTypeProjet();
        }
        if (llm.getSimulateurKey() != null && !llm.getSimulateurKey().isBlank()
                && !"null".equalsIgnoreCase(llm.getSimulateurKey())) {
            ctx.typeCredit = llm.getSimulateurKey();
            ctx.lastSimulateurKey = llm.getSimulateurKey();
        }
    }

    private String buildLlmPromptWithMemory(String userMessage, UserContext session) {
        StringBuilder sb = new StringBuilder("Contexte mémorisé BNA :\n");
        if (session.salaire != null) sb.append("- salaire=").append(session.salaire).append("\n");
        if (session.retenues != null) sb.append("- retenues=").append(session.retenues).append("\n");
        if (session.montantDemande != null) sb.append("- montant=").append(session.montantDemande).append("\n");
        if (session.dureeMois != null) sb.append("- dureeMois=").append(session.dureeMois).append("\n");
        if (session.typeProjet != null) sb.append("- typeProjet=").append(session.typeProjet).append("\n");
        if (session.lastSimulateurKey != null) sb.append("- simulateurPage=").append(session.lastSimulateurKey).append("\n");
        sb.append("\nMessage :\n").append(userMessage);
        return sb.toString();
    }

    // ═══════════════════════════════════════════
    // EXTRACTION
    // ═══════════════════════════════════════════
    private UserContext extractContext(String text) {
        UserContext ctx = new UserContext();
        String lower = text.toLowerCase();

        ctx.salaire = firstDouble(SALAIRE_PATTERN, text);
        if (ctx.salaire == null) ctx.salaire = firstDouble(SALAIRE_PATTERN_2, text);

        ctx.retenues = firstDouble(RETENUES_PATTERN, text);
        if (ctx.retenues == null) ctx.retenues = firstDouble(RETENUES_PATTERN_2, text);

        ctx.loyers = firstDouble(LOYERS_PATTERN, text);
        ctx.chiffreAffaires = firstDouble(CHIFFRE_AFFAIRES_PATTERN, text);
        ctx.revenuAnnuel = firstDouble(REVENU_ANNUEL_PATTERN, text);
        ctx.capitalRestant = firstDouble(CAPITAL_RESTANT_PATTERN, text);
        ctx.soldeMin = firstDouble(SOLDE_MIN_PATTERN, text);

        Double montant = firstDouble(PRIX_VOITURE_PATTERN, text);
        if (montant == null) montant = firstDouble(VOITURE_PATTERN, text);
        if (montant == null) montant = firstDouble(PRIX_IMMO_PATTERN, text);
        if (montant == null) montant = firstDouble(MAISON_PATTERN, text);
        if (montant == null) montant = firstDouble(MONTANT_PATTERN, text);
        if (montant == null) {
            Matcher m = MONTANT_SIMPLE_PATTERN.matcher(text);
            Double best = null;
            while (m.find()) {
                Double v = parseNumber(m.group(1));
                if (v != null && v >= 5000) {
                    if (best == null || v > best) best = v;
                }
            }
            montant = best;
        }
        ctx.montantDemande = montant;

        Matcher dm = DUREE_PATTERN.matcher(text);
        if (dm.find()) {
            int n = Integer.parseInt(dm.group(1));
            String unit = dm.group(2).toLowerCase();
            ctx.dureeMois = unit.startsWith("an") ? n * 12 : n;
        }

        if (lower.matches(".*(voiture|v[eé]hicule|\\bauto\\b|automobile|4cv|5cv).*")) {
            ctx.typeProjet = "auto";
            if (lower.contains("occasion")) ctx.typeVehicule = "occasion";
            else if (lower.contains("neuve") || lower.contains("neuf")) ctx.typeVehicule = "neuf";
        } else if (lower.matches(".*(maison|appartement|logement|immobilier|terrain|immeuble).*")) {
            ctx.typeProjet = "immobilier";
        } else if (lower.matches(".*(agricole|agriculteur|campagne|plantation|jinene).*")) {
            ctx.typeProjet = "agricole";
        } else if (lower.matches(".*(entreprise|cr[eé]ation|commerce).*")) {
            ctx.typeProjet = "entreprise";
        } else if (lower.contains("rachat")) {
            ctx.typeProjet = "rachat";
        }
        return ctx;
    }

    private Double parseNumber(String raw) {
        if (raw == null) return null;
        try {
            String n = raw.trim().replace(" ", "").replace("\u00A0", "").replace(",", ".");
            if (n.chars().filter(ch -> ch == '.').count() > 1) n = n.replace(".", "");
            return Double.parseDouble(n);
        } catch (Exception e) {
            return null;
        }
    }

    private Double firstDouble(Pattern p, String text) {
        Matcher m = p.matcher(text);
        return m.find() ? parseNumber(m.group(1)) : null;
    }

    // ═══════════════════════════════════════════
    // INIT
    // ═══════════════════════════════════════════
    private void initMaps() {
        routeMap.put("credit-h24", "/dashboard/financement-particuliers/credit-h24");
        routeMap.put("credit-perso", "/dashboard/financement-particuliers/credit-perso");
        routeMap.put("credit-auto", "/dashboard/financement-particuliers/credit-auto");
        routeMap.put("credit-immo", "/dashboard/financement-particuliers/credit-immo");
        routeMap.put("twensa", "/dashboard/financement-particuliers/twensa");
        routeMap.put("premier-logement", "/dashboard/financement-particuliers/premier-logement");
        routeMap.put("creation-projets", "/dashboard/financement-entreprises/creation-projets");
        routeMap.put("extension-developpement", "/dashboard/financement-entreprises/extension-developpement");
        routeMap.put("besoins-exploitation", "/dashboard/financement-entreprises/besoins-exploitation");
        routeMap.put("caution-bna", "/dashboard/financement-entreprises/caution-bna");
        routeMap.put("credits-campagne", "/dashboard/financement-agricole/credits-campagne");
        routeMap.put("credits-investissement", "/dashboard/financement-agricole/credits-investissement");
        routeMap.put("credits-supervises", "/dashboard/financement-agricole/credits-supervises");
        routeMap.put("chaines-valeur", "/dashboard/financement-agricole/chaines-valeur");
        routeMap.put("credit-jinene", "/dashboard/financement-agricole/credit-jinene");
        routeMap.put("professions-liberales", "/dashboard/financement-professionnels/professions-liberales");
        routeMap.put("commercants-artisans", "/dashboard/financement-professionnels/commercants-artisans");
        routeMap.put("pro-tresorerie", "/dashboard/financement-professionnels/pro-tresorerie");
        routeMap.put("pro-stocks", "/dashboard/financement-professionnels/pro-stocks");
        routeMap.put("pro-marches-tn", "/dashboard/financement-professionnels/pro-marches-tn");
        routeMap.put("pro-export", "/dashboard/financement-professionnels/pro-export");
        routeMap.put("pro-investissement", "/dashboard/financement-professionnels/pro-investissement");
        routeMap.put("pro-cautions", "/dashboard/financement-professionnels/pro-cautions");
        routeMap.put("sans-rachat", "/dashboard/simulateurs-internes/sans-rachat");
        routeMap.put("rachat-interne", "/dashboard/simulateurs-internes/rachat-interne");
        routeMap.put("rachat-externe", "/dashboard/simulateurs-internes/rachat-externe");

        nomMap.put("credit-h24", "Crédit H24");
        nomMap.put("credit-perso", "Crédit Personnel");
        nomMap.put("credit-auto", "Crédit Auto");
        nomMap.put("credit-immo", "Crédit Immobilier");
        nomMap.put("twensa", "TWENSA Immobilier");
        nomMap.put("premier-logement", "Premier Logement");
        nomMap.put("creation-projets", "Création Projets");
        nomMap.put("extension-developpement", "Extension & Développement");
        nomMap.put("besoins-exploitation", "Besoins Exploitation");
        nomMap.put("caution-bna", "Caution BNA");
        nomMap.put("credits-campagne", "Crédits Campagne");
        nomMap.put("credits-investissement", "Crédits Investissement Agricole");
        nomMap.put("credits-supervises", "Crédits Supervisés");
        nomMap.put("chaines-valeur", "Chaînes de Valeur");
        nomMap.put("credit-jinene", "Crédit JINENE");
        nomMap.put("professions-liberales", "Professions Libérales");
        nomMap.put("commercants-artisans", "Commerçants & Artisans");
        nomMap.put("pro-tresorerie", "PRO Trésorerie");
        nomMap.put("pro-stocks", "PRO Stocks");
        nomMap.put("pro-marches-tn", "PRO Marchés Tunisie");
        nomMap.put("pro-export", "PRO Export");
        nomMap.put("pro-investissement", "PRO Investissement");
        nomMap.put("pro-cautions", "PRO Cautions");
        nomMap.put("sans-rachat", "Simulateur Sans Rachat");
        nomMap.put("rachat-interne", "Simulateur Rachat Interne");
        nomMap.put("rachat-externe", "Simulateur Rachat Externe");

        descriptionMap.put("credit-h24", "Crédit express jusqu'à 20 000 DT sur 36 mois");
        descriptionMap.put("credit-perso", "Dépenses courantes ou aménagement (36-60 mois)");
        descriptionMap.put("credit-auto", "Véhicule neuf (84 mois) ou occasion (60 mois)");
        descriptionMap.put("credit-immo", "Acquisition, construction, terrain (7-15 ans)");
        descriptionMap.put("twensa", "Immobilier taux préférentiel TMM+2.00%");
        descriptionMap.put("premier-logement", "Primo-accédant, dotation 20%");
        descriptionMap.put("creation-projets", "Création d'entreprise (7-12 ans)");
        descriptionMap.put("extension-developpement", "Extension CMT / long terme");
        descriptionMap.put("besoins-exploitation", "Stock, trésorerie, export");
        descriptionMap.put("caution-bna", "Cautions et avals");
        descriptionMap.put("credits-campagne", "Campagne agricole court terme");
        descriptionMap.put("credits-investissement", "Investissement agricole");
        descriptionMap.put("credits-supervises", "Crédits supervisés CRDA");
        descriptionMap.put("chaines-valeur", "Tripartite / Interface");
        descriptionMap.put("credit-jinene", "3× ou 4× solde minimum");
        descriptionMap.put("professions-liberales", "Financement professions libérales");
        descriptionMap.put("commercants-artisans", "Financement commerçants & artisans");
        descriptionMap.put("pro-tresorerie", "Facilité de caisse, découvert, escompte");
        descriptionMap.put("pro-stocks", "Stocks et activités saisonnières");
        descriptionMap.put("pro-marches-tn", "Marchés en Tunisie");
        descriptionMap.put("pro-export", "Marchés à l'export");
        descriptionMap.put("pro-investissement", "Investissements professionnels");
        descriptionMap.put("pro-cautions", "Cautions & avals professionnels");
        descriptionMap.put("sans-rachat", "Nouveau crédit BNA sans rachat");
        descriptionMap.put("rachat-interne", "Regroupement crédits BNA");
        descriptionMap.put("rachat-externe", "Rachat crédit concurrent");

        champsMap.put("credit-h24", new String[]{"salaire", "retenues", "montant"});
        champsMap.put("credit-perso", new String[]{"salaire", "retenues", "type", "durée"});
        champsMap.put("credit-auto", new String[]{"salaire", "prix véhicule", "type", "durée"});
        champsMap.put("credit-immo", new String[]{"salaire", "coût projet", "type", "durée"});
        champsMap.put("twensa", new String[]{"salaire", "coût", "type projet"});
        champsMap.put("premier-logement", new String[]{"salaire", "prix logement", "durée"});
        champsMap.put("sans-rachat", new String[]{"salaire", "loyers", "retenues", "type", "durée"});
        champsMap.put("rachat-interne", new String[]{"capital BNA", "retenues", "durée", "montant"});
        champsMap.put("rachat-externe", new String[]{"capital externe", "retenues", "durée", "montant"});
        champsMap.put("pro-tresorerie", new String[]{"CA", "type crédit"});
    }

    private void initCreditDetails() {
        creditDetailsMap.put("credit-h24", new CreditDetails("Crédit H24", "Express sans frais", "20 000 DT", "36 mois", "TMM+2.50%", "Salaire ≥ 1200 DT", "Réponse 24h", new String[]{"CIN", "3 bulletins"}));
        creditDetailsMap.put("credit-auto", new CreditDetails("Crédit Auto", "Neuf ou occasion", "80%/60%", "60-84 mois", "TMM+3.00%", "Salaire ≥ 1200 DT", "Flexible", new String[]{"CIN", "Facture proforma"}));
        creditDetailsMap.put("credit-immo", new CreditDetails("Crédit Immo", "Acquisition/construction", "80%", "84-180 mois", "TMM+3.00%", "Apport 20%", "Hypothèque", new String[]{"CIN", "Offre de vente"}));
        creditDetailsMap.put("credit-perso", new CreditDetails("Crédit Perso", "Dépenses / aménagement", "Selon capacité", "36-60 mois", "TMM+2.25%", "Salaire ≥ 1000 DT", "Souple", new String[]{"CIN", "3 bulletins"}));
        creditDetailsMap.put("sans-rachat", new CreditDetails("Sans Rachat", "Crédit direct BNA", "Selon capacité", "36-180 mois", "TMM+marge", "Selon produit", "12 types", new String[]{"CIN", "SED"}));
        creditDetailsMap.put("rachat-interne", new CreditDetails("Rachat Interne", "Regroupement BNA", "Selon capacité", "Selon produit", "TMM+marge", "Capital BNA", "Un seul crédit", new String[]{"Tableau amortissement"}));
        creditDetailsMap.put("rachat-externe", new CreditDetails("Rachat Externe", "Rachat concurrent", "Selon capacité", "36-60 mois", "TMM+marge", "2 types", "Dépenses/Aménagement", new String[]{"Attestation solde"}));
    }

    private void initSimulateurs() {
        for (String key : routeMap.keySet()) {
            simulateursMap.put(key, buildSimulateurInfo(key));
        }
    }

    private void initKeywords() {
        intentKeywords.put("simulation", Arrays.asList("simuler", "simulation", "calculer", "mensualité", "capacité"));
        intentKeywords.put("context", Arrays.asList("je gagne", "mon salaire", "mes revenus", "retenues"));
        intentKeywords.put("project", Arrays.asList("projet", "acheter", "financer", "maison", "voiture", "entreprise"));
        intentKeywords.put("info", Arrays.asList("taux", "durée", "plafond", "condition", "document"));
        intentKeywords.put("help", Arrays.asList("aide", "help", "comment", "que faire", "conseil"));
    }

    private SimulateurInfo buildSimulateurInfo(String key) {
        return new SimulateurInfo(
                nomMap.getOrDefault(key, key),
                routeMap.getOrDefault(key, "/dashboard/categories"),
                descriptionMap.getOrDefault(key, "Simulateur BNA"),
                champsMap.getOrDefault(key, new String[]{})
        );
    }

    // ═══════════════════════════════════════════
    // RÉPONSES
    // ═══════════════════════════════════════════
    private ChatResponse buildNavigateResponse(UserContext session, String key, String intro) {
        ChatResponse response = new ChatResponse();
        SimulateurInfo info = buildSimulateurInfo(key);
        StringBuilder sb = new StringBuilder();
        if (intro != null && !intro.isBlank()) sb.append(intro).append("\n\n");
        sb.append(generateSimulationResponse(info));
        if (session.hasData()) sb.append("\n\n").append(summarizeContext(session));
        sb.append("\n\n⚠️ *Simulation indicative – offre définitive en agence BNA.*");
        response.setMessage(sb.toString());
        response.setSuggestedAction("NAVIGATE");
        response.setActionData(info.getRoute());
        response.setSimulateurInfo(info);
        return response;
    }

    private boolean isDetailsRequest(String message) {
        return DETAILS_KEYWORDS.stream().anyMatch(message::contains);
    }

    private ChatResponse handleDetailsRequest(String message) {
        ChatResponse response = new ChatResponse();
        String key = detectCreditType(message);
        if (key != null && creditDetailsMap.containsKey(key)) {
            CreditDetails d = creditDetailsMap.get(key);
            StringBuilder sb = new StringBuilder();
            sb.append("📋 **").append(d.nom).append("**\n\n").append(d.description).append("\n\n");
            sb.append("💰 Max : ").append(d.montantMax).append("\n");
            sb.append("📅 Durée : ").append(d.duree).append("\n");
            sb.append("📈 Taux : ").append(d.taux).append("\n");
            sb.append("✅ Conditions : ").append(d.conditions).append("\n\n");
            sb.append("📄 Documents :\n");
            for (String doc : d.documents) sb.append(" • ").append(doc).append("\n");
            if (routeMap.containsKey(key)) {
                response.setSuggestedAction("NAVIGATE");
                response.setActionData(routeMap.get(key));
                response.setSimulateurInfo(buildSimulateurInfo(key));
            }
            response.setMessage(sb.toString());
        } else {
            response.setMessage("📌 Précisez le crédit : H24, perso, auto, immo, rachat…");
            response.setSuggestedAction("SHOW_INFO");
        }
        return response;
    }

    private String detectCreditType(String message) {
        if (message.contains("h24")) return "credit-h24";
        if (message.contains("personnel") || message.contains("perso")) return "credit-perso";
        if (message.contains("auto") || message.contains("voiture") || message.contains("véhicule")) return "credit-auto";
        if (message.contains("immobilier") || message.contains("immo") || message.contains("maison")) return "credit-immo";
        if (message.contains("twensa")) return "twensa";
        if (message.contains("premier logement") || message.contains("primo")) return "premier-logement";
        if (message.contains("jinene")) return "credit-jinene";
        if (message.contains("campagne")) return "credits-campagne";
        if (message.contains("rachat interne")) return "rachat-interne";
        if (message.contains("rachat externe")) return "rachat-externe";
        if (message.contains("sans rachat")) return "sans-rachat";
        if (message.contains("caution")) return "caution-bna";
        if (message.contains("trésorerie")) return "pro-tresorerie";
        return null;
    }

    private ChatResponse askMissingFields(UserContext context) {
        ChatResponse response = new ChatResponse();
        StringBuilder sb = new StringBuilder("📊 **Données mémorisées :**\n");
        if (context.salaire != null) sb.append(" • Salaire : ").append(String.format("%.0f", context.salaire)).append(" DT\n");
        if (context.montantDemande != null) sb.append(" • Montant : ").append(String.format("%.0f", context.montantDemande)).append(" DT\n");
        if (context.dureeMois != null) sb.append(" • Durée : ").append(context.dureeMois).append(" mois\n");
        if (context.retenues != null) sb.append(" • Retenues : ").append(String.format("%.0f", context.retenues)).append(" DT\n");
        if (context.typeProjet != null) sb.append(" • Projet : ").append(context.typeProjet).append("\n");
        sb.append("\n📋 **Il me manque :**\n");
        if (context.salaire == null) sb.append(" ❌ Salaire mensuel\n");
        if (context.montantDemande == null) sb.append(" ❌ Montant du projet\n");
        if (context.dureeMois == null) sb.append(" ❌ Durée souhaitée\n");
        sb.append("\n💡 Ex. : \"Je gagne 3500 DT, retenues 400, voiture 45000 DT sur 60 mois\"");
        response.setMessage(sb.toString());
        response.setSuggestedAction("ASK_MORE");
        return response;
    }

    private String summarizeContext(UserContext c) {
        StringBuilder sb = new StringBuilder("📋 **Votre dossier :**\n");
        if (c.salaire != null) sb.append(" • Salaire : ").append(String.format("%.0f", c.salaire)).append(" DT\n");
        if (c.retenues != null) sb.append(" • Retenues : ").append(String.format("%.0f", c.retenues)).append(" DT\n");
        if (c.montantDemande != null) sb.append(" • Montant : ").append(String.format("%.0f", c.montantDemande)).append(" DT\n");
        if (c.dureeMois != null) sb.append(" • Durée : ").append(c.dureeMois).append(" mois\n");
        if (c.typeProjet != null) sb.append(" • Projet : ").append(c.typeProjet).append("\n");
        if (c.typeVehicule != null) sb.append(" • Véhicule : ").append(c.typeVehicule).append("\n");
        return sb.toString();
    }

    private void handleSimulationIntent(String message, ChatResponse response) {
        SimulateurInfo sim = findBestSimulateur(message);
        if (sim != null) {
            response.setMessage(generateSimulationResponse(sim));
            response.setSuggestedAction("NAVIGATE");
            response.setActionData(sim.getRoute());
            response.setSimulateurInfo(sim);
        } else {
            response.setMessage("🔍 Donnez salaire, montant, durée.\nEx. : \"Je gagne 3500 DT, voiture 45000 DT sur 60 mois\"");
            response.setSuggestedAction("SHOW_OPTIONS");
        }
    }

    private void handleContextIntent(ChatResponse response) {
        response.setMessage("📊 Indiquez salaire, retenues, montant et durée de votre projet.");
        response.setSuggestedAction("SHOW_CONTEXT");
    }

    private void handleProjectIntent(String message, ChatResponse response) {
        if (message.contains("voiture") || message.contains("auto") || message.contains("véhicule")) {
            response.setMessage("🚗 **Crédit Auto** — neuf 84 mois ou occasion 60 mois.");
            response.setSuggestedAction("NAVIGATE");
            response.setActionData(routeMap.get("credit-auto"));
            response.setSimulateurInfo(buildSimulateurInfo("credit-auto"));
        } else if (message.contains("maison") || message.contains("immobilier")) {
            response.setMessage("🏠 Options : Crédit Immo, TWENSA, Premier Logement.");
            response.setSuggestedAction("SHOW_PROJECT");
        } else if (message.contains("agricole") || message.contains("campagne")) {
            response.setMessage("🌾 Options : Campagne, Investissement, Supervisés, JINENE.");
            response.setSuggestedAction("SHOW_PROJECT");
        } else if (message.contains("entreprise") || message.contains("création")) {
            response.setMessage("🏢 Options : Création projets, Extension, PRO Trésorerie.");
            response.setSuggestedAction("SHOW_PROJECT");
        } else {
            response.setMessage("Précisez : immobilier, auto, agricole, entreprise ou rachat.");
            response.setSuggestedAction("SHOW_PROJECT");
        }
    }

    private void handleInfoIntent(String message, ChatResponse response) {
        String key = detectCreditType(message);
        if (key != null) {
            ChatResponse d = handleDetailsRequest(message);
            response.setMessage(d.getMessage());
            response.setSuggestedAction(d.getSuggestedAction());
            response.setActionData(d.getActionData());
            response.setSimulateurInfo(d.getSimulateurInfo());
            return;
        }
        response.setMessage("Je renseigne sur taux, durées, plafonds, documents.\nEx. : \"Détails du crédit auto\"");
        response.setSuggestedAction("SHOW_INFO");
    }

    private void handleHelpIntent(ChatResponse response) {
        response.setMessage("🤖 Je mémorise votre dossier, calcule votre capacité et vous conseille si le montant est trop élevé.");
        response.setSuggestedAction("SHOW_HELP");
    }

    private void handleDefaultIntent(ChatResponse response) {
        response.setMessage("💬 Donnez salaire, projet et durée — ou dites \"détails crédit auto\".");
        response.setSuggestedAction("LISTEN");
    }

    private SimulateurInfo findBestSimulateur(String message) {
        Map<String, List<String>> map = new LinkedHashMap<>();
        map.put("credit-h24", List.of("h24", "express"));
        map.put("credit-perso", List.of("personnel", "perso"));
        map.put("credit-auto", List.of("voiture", "auto", "véhicule"));
        map.put("credit-immo", List.of("immobilier", "immo", "maison"));
        map.put("twensa", List.of("twensa"));
        map.put("rachat-interne", List.of("rachat interne"));
        map.put("rachat-externe", List.of("rachat externe"));
        map.put("sans-rachat", List.of("sans rachat"));
        map.put("pro-tresorerie", List.of("trésorerie"));
        map.put("credits-campagne", List.of("campagne", "agricole"));
        map.put("credit-jinene", List.of("jinene"));
        map.put("caution-bna", List.of("caution"));
        for (var e : map.entrySet()) {
            for (String k : e.getValue()) {
                if (message.contains(k)) return buildSimulateurInfo(e.getKey());
            }
        }
        return null;
    }

    private String generateSimulationResponse(SimulateurInfo s) {
        StringBuilder sb = new StringBuilder();
        sb.append("✅ **").append(s.getNom()).append("**\n\n");
        sb.append("📌 ").append(s.getDescription()).append("\n\n");
        if (s.getChampsRequis() != null && s.getChampsRequis().length > 0) {
            sb.append("📋 **Champs à renseigner :**\n");
            for (String c : s.getChampsRequis()) sb.append(" • ").append(c).append("\n");
            sb.append("\n");
        }
        sb.append("🔗 Cliquez sur le bouton pour ouvrir le simulateur.");
        return sb.toString();
    }

    private String classifyIntent(String message) {
        for (var e : intentKeywords.entrySet()) {
            for (String k : e.getValue()) {
                if (message.contains(k)) return e.getKey();
            }
        }
        return "default";
    }

    private double getTauxEndettement(double totalRevenu) {
        if (totalRevenu < 1000) return 0.40;
        if (totalRevenu < 2000) return 0.50;
        if (totalRevenu < 3000) return 0.55;
        if (totalRevenu < 4000) return 0.60;
        return 0.65;
    }
}