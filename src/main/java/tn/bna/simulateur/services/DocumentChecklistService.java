package tn.bna.simulateur.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tn.bna.simulateur.dto.dossier.ChecklistResponse;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.LocalDate;
import java.util.*;

@Service
public class DocumentChecklistService {
    private final ObjectMapper objectMapper;
    private final BigDecimal expertiseSeuil;
    private final LocalDate dateReferenceVehicule;
    private final Integer titreRecentJours;
    private final boolean attestationAnnuelleObligatoire;
    private final boolean attestationTravailObligatoire;
    private final BigDecimal montantReferenceFallback;

    public DocumentChecklistService(
            ObjectMapper objectMapper,
            @Value("${app.dossier.checklist.expertise-seuil-montant:}") String expertiseSeuil,
            @Value("${app.dossier.checklist.vehicule-date-reference:}") String dateReferenceVehicule,
            @Value("${app.dossier.checklist.titre-recent-jours:}") String titreRecentJours,
            @Value("${app.dossier.checklist.attestation-annuelle-obligatoire:false}") boolean attestationAnnuelleObligatoire,
            @Value("${app.dossier.checklist.attestation-travail-obligatoire:false}") boolean attestationTravailObligatoire,
            @Value("${app.dossier.checklist.montant-reference-fallback:0}") BigDecimal montantReferenceFallback
    ) {
        this.objectMapper = objectMapper;
        this.expertiseSeuil = parseAmount(expertiseSeuil);
        this.dateReferenceVehicule = parseDate(dateReferenceVehicule);
        this.titreRecentJours = parsePositiveInt(titreRecentJours);
        this.attestationAnnuelleObligatoire = attestationAnnuelleObligatoire;
        this.attestationTravailObligatoire = attestationTravailObligatoire;
        this.montantReferenceFallback = montantReferenceFallback == null ? BigDecimal.ZERO : montantReferenceFallback;
    }

    public ChecklistResponse generate(
            String simulationJson,
            String typeVendeur,
            Boolean revenusSupplementairesDeclares,
            Collection<String> typesDeposes
    ) {
        Map<String, Object> root = parseSimulation(simulationJson);
        Map<String, Object> simulation = nestedSimulation(root);
        String produit = firstNonBlank(asText(simulation.get("typeCredit")), asText(root.get("typeCredit")));
        String scenario = firstNonBlank(asText(simulation.get("scenario")), asText(root.get("typeSimulateur")));
        Product product = identifyProduct(produit);
        String seller = normalizeSeller(typeVendeur);
        boolean sellerRequired = product == Product.LOGEMENT || product == Product.TERRAIN;
        boolean incomeDeclared = Boolean.TRUE.equals(revenusSupplementairesDeclares);
        BigDecimal requestedAmount = decimal(simulation.get("montantDemande"));
        if (requestedAmount == null) requestedAmount = decimal(root.get("montantDemande"));
        if (requestedAmount == null) requestedAmount = montantReferenceFallback;

        Set<String> uploaded = new HashSet<>();
        if (typesDeposes != null) typesDeposes.stream().filter(Objects::nonNull)
                .map(String::trim).filter(s -> !s.isBlank()).forEach(uploaded::add);

        List<ChecklistResponse.Rule> pending = new ArrayList<>();
        List<ChecklistResponse.Item> items = new ArrayList<>();
        List<ChecklistResponse.AlternativeGroup> groups = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        addCommonItems(items, uploaded, incomeDeclared, pending);
        addProductItems(product, seller, requestedAmount, items, groups, uploaded, pending);

        if (sellerRequired && seller == null) {
            missing.add("Type de vendeur (PROMOTEUR_AGREE ou PARTICULIER)");
        }
        for (ChecklistResponse.Item item : items) {
            if (item.actif() && item.obligatoire() && !item.depose() && !item.genereAutomatiquement()) {
                missing.add(item.libelle());
            }
        }
        for (ChecklistResponse.AlternativeGroup group : groups) {
            if (group.obligatoire() && !group.satisfait()) missing.add(group.libelle());
        }

        if (sellerRequired && seller != null && !Set.of("PROMOTEUR_AGREE", "PARTICULIER").contains(seller)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Le type de vendeur doit être PROMOTEUR_AGREE ou PARTICULIER.");
        }

        return new ChecklistResponse(
                produit, product.code, scenario, seller, sellerRequired, incomeDeclared,
                missing.isEmpty(), List.copyOf(items), List.copyOf(groups), List.copyOf(missing), List.copyOf(pending)
        );
    }

    public ChecklistResponse generate(String simulationJson, String typeVendeur, Boolean revenusSupplementairesDeclares) {
        return generate(simulationJson, typeVendeur, revenusSupplementairesDeclares, List.of());
    }

    public LocalDate getDateReferenceVehicule() {
        return dateReferenceVehicule;
    }

    public Integer getTitreRecentJours() {
        return titreRecentJours;
    }

    private void addCommonItems(List<ChecklistResponse.Item> items, Set<String> uploaded,
                                boolean incomeDeclared, List<ChecklistResponse.Rule> pending) {
        add(items, uploaded, "CIN", "Carte d'identité nationale", "Copie lisible recto-verso si disponible.", true, true, null, "CONFIRMED", false);
        add(items, uploaded, "BULLETIN_SALAIRE", "Dernier bulletin de salaire", "Le bulletin le plus récent.", true, true, null, "CONFIRMED", false);
        add(items, uploaded, "RELEVE_BANCAIRE", "Relevé bancaire", "Document facultatif.", false, true, null, "CONFIRMED", false);
        addConfiguredAttestation(items, uploaded, pending, "ATTESTATION_SALAIRE_ANNUELLE", "Attestation annuelle de salaire", attestationAnnuelleObligatoire,
                "Obligation en attente de confirmation officielle.");
        addConfiguredAttestation(items, uploaded, pending, "ATTESTATION_TRAVAIL", "Attestation de travail", attestationTravailObligatoire,
                "Obligation en attente de confirmation officielle.");
        if (incomeDeclared) {
            add(items, uploaded, "JUSTIFICATIF_AUTRES_REVENUS", "Justificatif d'autres revenus",
                    "À fournir car des revenus supplémentaires ont été déclarés.", true, true,
                    "Revenus supplémentaires déclarés", "CONFIRMED", false);
        } else {
            items.add(new ChecklistResponse.Item("JUSTIFICATIF_AUTRES_REVENUS", "Justificatif d'autres revenus",
                    "Non demandé en l'absence de revenus supplémentaires déclarés.", "CONDITIONNEL_NON_APPLICABLE",
                    false, false, uploaded.contains("JUSTIFICATIF_AUTRES_REVENUS"), false,
                    "Revenus supplémentaires déclarés", "CONFIRMED"));
        }
    }

    private void addConfiguredAttestation(List<ChecklistResponse.Item> items, Set<String> uploaded,
                                         List<ChecklistResponse.Rule> pending, String code, String label,
                                         boolean required, String pendingDescription) {
        String ruleStatus = required ? "CONFIRMED" : "DISABLED";
        boolean active = required;
        String status = required ? (uploaded.contains(code) ? "PRESENT" : "MANQUANT") : "EN_ATTENTE_CONFIRMATION";
        items.add(new ChecklistResponse.Item(code, label, required ? "Pièce configurée comme obligatoire." : pendingDescription,
                status, required, active, uploaded.contains(code), false, null, ruleStatus));
        if (!required) pending.add(new ChecklistResponse.Rule(code + "_OBLIGATION", "PENDING_CONFIRMATION", pendingDescription));
    }

    private void addProductItems(Product product, String seller, BigDecimal amount,
                                 List<ChecklistResponse.Item> items,
                                 List<ChecklistResponse.AlternativeGroup> groups,
                                 Set<String> uploaded, List<ChecklistResponse.Rule> pending) {
        switch (product) {
            case DEPENSES -> add(items, uploaded, "DEMANDE_CREDIT", "Demande de crédit",
                    "La demande est créée automatiquement avec le dossier.", true, true, null, "CONFIRMED", true);
            case AMENAGEMENT -> addRequired(items, uploaded, "DEVIS_TRAVAUX");
            case VEHICULE_NEUF -> addRequired(items, uploaded, "FACTURE_PRO_FORMA");
            case VEHICULE_OCCASION -> {
                addRequired(items, uploaded, "PROMESSE_VENTE");
                addRequired(items, uploaded, "CARTE_GRISE");
                pending.add(new ChecklistResponse.Rule("DATE_REFERENCE_VEHICULE", "CONFIRMED",
                        "Calcul de l'âge du véhicule selon la date de référence configurée : " + dateReferenceVehicule + "."));
            }
            case AMENAGEMENT_HYPOTHECAIRE -> {
                addRequired(items, uploaded, "DEVIS_TRAVAUX");
                addRequired(items, uploaded, "JUSTIFICATIF_PROPRIETE");
                addConditionalExpertise(items, uploaded, amount, pending, "EXPERTISE_HYPOTHECAIRE_P5");
            }
            case LOGEMENT -> {
                addRequired(items, uploaded, "PROMESSE_VENTE");
                if ("PROMOTEUR_AGREE".equals(seller)) {
                    addRequired(items, uploaded, "JUSTIFICATIF_PROPRIETE");
                    pending.add(new ChecklistResponse.Rule("AGREMENT_PROMOTEUR", "PENDING_CONFIRMATION",
                            "L'agrément du promoteur doit être vérifié manuellement par un administrateur."));
                } else if ("PARTICULIER".equals(seller)) {
                    addRequired(items, uploaded, "EXPERTISE_IMMOBILIERE");
                    addAlternative(groups, uploaded, "TITRE_OU_ACTE", "Titre foncier individuel récent ou acte notarié",
                            List.of("TITRE_FONCIER", "ACTE_NOTARIE"));
                    addTitleRecencyRule(pending);
                }
            }
            case CONSTRUCTION -> {
                addRequired(items, uploaded, "DEVIS_ARCHITECTE");
                addRequired(items, uploaded, "PERMIS_BATIR");
                addRequired(items, uploaded, "JUSTIFICATIF_PROPRIETE");
                addConditionalExpertise(items, uploaded, amount, pending, "EXPERTISE_CONSTRUCTION_P7");
            }
            case TERRAIN -> {
                if ("PROMOTEUR_AGREE".equals(seller)) {
                    addRequired(items, uploaded, "JUSTIFICATIF_PROPRIETE");
                    pending.add(new ChecklistResponse.Rule("AGREMENT_PROMOTEUR", "PENDING_CONFIRMATION",
                            "L'agrément du promoteur doit être vérifié manuellement par un administrateur."));
                } else if ("PARTICULIER".equals(seller)) {
                    boolean recencyConfigured = titreRecentJours != null;
                    add(items, uploaded, "TITRE_FONCIER", "Titre foncier récent",
                            recencyConfigured ? "Le délai configuré sera contrôlé après extraction OCR." : "La durée de validité du caractère récent reste à confirmer.",
                            true, true, null, recencyConfigured ? "CONFIRMED" : "PENDING_CONFIRMATION", false);
                    addRequired(items, uploaded, "EXPERTISE_IMMOBILIERE");
                    addTitleRecencyRule(pending);
                }
            }
        }
    }

    private void addConditionalExpertise(List<ChecklistResponse.Item> items, Set<String> uploaded,
                                         BigDecimal amount, List<ChecklistResponse.Rule> pending, String ruleCode) {
        if (expertiseSeuil != null && expertiseSeuil.signum() > 0) {
            boolean required = amount.compareTo(expertiseSeuil) >= 0;
            if (required) addRequired(items, uploaded, "EXPERTISE_IMMOBILIERE");
            else items.add(new ChecklistResponse.Item("EXPERTISE_IMMOBILIERE", "Expertise immobilière",
                    "Le seuil configuré n'est pas atteint.", "CONDITIONNEL_NON_APPLICABLE", false, false,
                    uploaded.contains("EXPERTISE_IMMOBILIERE"), false,
                    "Montant demandé ≥ " + expertiseSeuil, "CONFIRMED"));
            pending.add(new ChecklistResponse.Rule(ruleCode, "CONFIRMED", "Seuil d'expertise configuré : " + expertiseSeuil));
        } else {
            items.add(new ChecklistResponse.Item("EXPERTISE_IMMOBILIERE", "Expertise immobilière",
                    "Le seuil officiel de déclenchement n'est pas confirmé ; ce document n'est pas obligatoire.",
                    "EN_ATTENTE_CONFIRMATION", false, false, uploaded.contains("EXPERTISE_IMMOBILIERE"), false,
                    "Seuil de montant à confirmer", "DISABLED"));
            pending.add(new ChecklistResponse.Rule(ruleCode, "PENDING_CONFIRMATION",
                    "Aucun seuil d'expertise n'est configuré ; l'expertise ne bloque pas la complétude."));
            pending.add(new ChecklistResponse.Rule(ruleCode + "_OBLIGATION", "DISABLED",
                    "L'expertise reste désactivée tant que le seuil officiel n'est pas confirmé."));
        }
    }

    private void addTitleRecencyRule(List<ChecklistResponse.Rule> pending) {
        if (titreRecentJours == null) {
            pending.add(new ChecklistResponse.Rule("RECENCE_TITRE_FONCIER", "PENDING_CONFIRMATION",
                    "La durée définissant un titre foncier récent reste à confirmer."));
        } else {
            pending.add(new ChecklistResponse.Rule("RECENCE_TITRE_FONCIER", "CONFIRMED",
                    "Un titre de moins de " + titreRecentJours + " jours est considéré récent."));
        }
    }

    private void addRequired(List<ChecklistResponse.Item> items, Set<String> uploaded, String code) {
        add(items, uploaded, code, label(code), "Document obligatoire selon le produit sélectionné.", true, true, null, "CONFIRMED", false);
    }

    private void add(List<ChecklistResponse.Item> items, Set<String> uploaded, String code, String label,
                     String description, boolean required, boolean active, String condition, String rule, boolean generated) {
        boolean present = generated || uploaded.contains(code);
        String status = generated ? "AUTO_GENEREE" : present ? "PRESENT" : required ? "MANQUANT" : "FACULTATIF";
        items.add(new ChecklistResponse.Item(code, label, description, status, required, active, present, generated, condition, rule));
    }

    private void addAlternative(List<ChecklistResponse.AlternativeGroup> groups, Set<String> uploaded,
                                String code, String label, List<String> alternatives) {
        List<String> present = alternatives.stream().filter(uploaded::contains).toList();
        groups.add(new ChecklistResponse.AlternativeGroup(code, label, true, !present.isEmpty(),
                alternatives, present));
    }

    private Map<String, Object> parseSimulation(String json) {
        if (json == null || json.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La simulation associée est manquante.");
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() { });
        } catch (JsonProcessingException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Simulation invalide.");
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> nestedSimulation(Map<String, Object> root) {
        Object nested = root.get("simulation");
        return nested instanceof Map<?, ?> map ? (Map<String, Object>) map : root;
    }

    private Product identifyProduct(String label) {
        String normalized = normalize(label);
        if (normalized.contains("DEPENSES COURANTES")) return Product.DEPENSES;
        if (normalized.contains("AMENAGEMENT HYPOTHECAIRE")) return Product.AMENAGEMENT_HYPOTHECAIRE;
        if (normalized.contains("ACQUISITION VEHICULE NEUF")) return Product.VEHICULE_NEUF;
        if (normalized.contains("ACQUISITION VEHICULE OCCASION")) return Product.VEHICULE_OCCASION;
        if (normalized.contains("ACQUISITION LOGEMENT")) return Product.LOGEMENT;
        if (normalized.contains("ACHAT TERRAIN")) return Product.TERRAIN;
        if (normalized.contains("CONSTRUCTION")) return Product.CONSTRUCTION;
        if (normalized.contains("AMENAGEMENT")) return Product.AMENAGEMENT;
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Produit de crédit inconnu ou non pris en charge : " + (label == null ? "(absent)" : label));
    }

    private String normalizeSeller(String seller) {
        if (seller == null || seller.isBlank()) return null;
        String normalized = normalize(seller).replace(' ', '_');
        if (normalized.equals("PROMOTEUR_AGREE")) return "PROMOTEUR_AGREE";
        if (normalized.equals("PARTICULIER")) return "PARTICULIER";
        return normalized;
    }

    private String label(String code) {
        return switch (code) {
            case "CIN" -> "Carte d'identité nationale";
            case "BULLETIN_SALAIRE" -> "Dernier bulletin de salaire";
            case "RELEVE_BANCAIRE" -> "Relevé bancaire";
            case "JUSTIFICATIF_PROPRIETE" -> "Justificatif de propriété";
            case "DEVIS_TRAVAUX" -> "Devis des travaux";
            case "FACTURE_PRO_FORMA" -> "Facture pro forma";
            case "PROMESSE_VENTE" -> "Promesse de vente";
            case "CARTE_GRISE" -> "Carte grise";
            case "TITRE_FONCIER" -> "Titre foncier";
            case "ACTE_NOTARIE" -> "Acte notarié";
            case "PERMIS_BATIR" -> "Permis de bâtir";
            case "DEVIS_ARCHITECTE" -> "Devis d'architecte";
            case "EXPERTISE_IMMOBILIERE" -> "Expertise immobilière";
            default -> code;
        };
    }

    private String asText(Object value) { return value == null ? null : value.toString(); }
    private String firstNonBlank(String first, String second) {
        return first != null && !first.isBlank() ? first : second;
    }
    private String normalize(String value) {
        if (value == null) return "";
        return Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9 ]", " ").replaceAll("\\s+", " ").trim();
    }
    private BigDecimal parseAmount(String value) {
        try { return value == null || value.isBlank() ? null : new BigDecimal(value.trim()); }
        catch (NumberFormatException e) { return null; }
    }
    private LocalDate parseDate(String value) {
        try { return value == null || value.isBlank() ? LocalDate.now() : LocalDate.parse(value.trim()); }
        catch (RuntimeException e) { return LocalDate.now(); }
    }
    private Integer parsePositiveInt(String value) {
        try {
            int parsed = Integer.parseInt(value.trim());
            return parsed > 0 ? parsed : null;
        } catch (RuntimeException e) { return null; }
    }
    private BigDecimal decimal(Object value) {
        if (value instanceof Number number) return BigDecimal.valueOf(number.doubleValue());
        if (value == null) return null;
        try { return new BigDecimal(value.toString().replace(',', '.')); }
        catch (NumberFormatException e) { return null; }
    }

    private enum Product {
        DEPENSES("P1_DEPENSES"), AMENAGEMENT("P2_AMENAGEMENT"), VEHICULE_NEUF("P3_VEHICULE_NEUF"),
        VEHICULE_OCCASION("P4_VEHICULE_OCCASION"), AMENAGEMENT_HYPOTHECAIRE("P5_AMENAGEMENT_HYPOTHECAIRE"),
        LOGEMENT("P6_LOGEMENT"), CONSTRUCTION("P7_CONSTRUCTION"), TERRAIN("P8_TERRAIN");
        private final String code;
        Product(String code) { this.code = code; }
    }
}
