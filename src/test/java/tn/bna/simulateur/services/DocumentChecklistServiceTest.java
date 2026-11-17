package tn.bna.simulateur.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tn.bna.simulateur.dto.dossier.ChecklistResponse;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DocumentChecklistServiceTest {
    private DocumentChecklistService service;
    private final List<String> common = List.of("CIN", "BULLETIN_SALAIRE");

    @BeforeEach
    void setUp() {
        service = new DocumentChecklistService(new ObjectMapper(), "", "", "", false, false, BigDecimal.ZERO);
    }

    @Test
    void expensesRequireNoAdditionalUploadAndBankIsOptional() {
        ChecklistResponse result = checklist("Crédit Direct Dépenses Courantes", null, common, "SANS_RACHAT");
        assertTrue(result.complete());
        assertEquals("AUTO_GENEREE", item(result, "DEMANDE_CREDIT").statut());
        assertFalse(item(result, "RELEVE_BANCAIRE").obligatoire());
        assertTrue(checklist("Crédit Direct Dépenses Courantes", null,
                append(common, "RELEVE_BANCAIRE"), "SANS_RACHAT").complete());
    }

    @Test
    void allExistingScenariosUseTheSameDocumentRules() {
        for (String scenario : List.of("SANS_RACHAT", "RACHAT_INTERNE", "RACHAT_EXTERNE")) {
            ChecklistResponse result = checklist("Crédit Direct Dépenses Courantes", null, common, scenario);
            assertTrue(result.complete(), scenario);
            assertTrue(result.documents().stream().noneMatch(item -> item.code().contains("SOLDE")
                    || item.code().contains("AMORTISSEMENT") || item.code().contains("ANCIEN_CONTRAT")));
        }
    }

    @Test
    void renovationRequiresWorksQuote() {
        assertFalse(checklist("Crédit Direct Aménagement", null, common, "SANS_RACHAT").complete());
        assertTrue(checklist("Crédit Direct Aménagement", null,
                append(common, "DEVIS_TRAVAUX"), "RACHAT_INTERNE").complete());
    }

    @Test
    void newVehicleRequiresProforma() {
        assertFalse(checklist("Crédit Direct Acquisition Véhicule Neuf", null, common, "SANS_RACHAT").complete());
        assertTrue(checklist("Crédit Direct Acquisition Véhicule Neuf", null,
                append(common, "FACTURE_PRO_FORMA"), "RACHAT_EXTERNE").complete());
    }

    @Test
    void usedVehicleRequiresPromiseAndRegistration() {
        assertFalse(checklist("Crédit Direct Acquisition Véhicule Occasion", null, common, "SANS_RACHAT").complete());
        assertTrue(checklist("Crédit Direct Acquisition Véhicule Occasion", null,
                append(common, "PROMESSE_VENTE", "CARTE_GRISE"), "RACHAT_INTERNE").complete());
    }

    @Test
    void mortgageRenovationKeepsUnknownAppraisalThresholdDisabled() {
        String product = "Crédit Direct Aménagement Hypothécaire (Durée ≤ 7 ans)";
        ChecklistResponse result = checklist(product, null,
                append(common, "DEVIS_TRAVAUX", "JUSTIFICATIF_PROPRIETE"), "SANS_RACHAT");
        assertTrue(result.complete());
        assertEquals("EN_ATTENTE_CONFIRMATION", item(result, "EXPERTISE_IMMOBILIERE").statut());
        assertFalse(item(result, "EXPERTISE_IMMOBILIERE").obligatoire());
    }

    @Test
    void configuredAppraisalThresholdActivatesExpertiseRule() {
        DocumentChecklistService configured = new DocumentChecklistService(
                new ObjectMapper(), "40000", "", "", false, false, BigDecimal.ZERO);
        String product = "Crédit Direct Aménagement Hypothécaire (Durée ≤ 7 ans)";
        ChecklistResponse missing = configured.generate(simulation(product, "SANS_RACHAT"), null, false,
                append(common, "DEVIS_TRAVAUX", "JUSTIFICATIF_PROPRIETE"));
        assertFalse(missing.complete());
        assertTrue(missing.documentsManquants().contains("Expertise immobilière"));
        ChecklistResponse present = configured.generate(simulation(product, "SANS_RACHAT"), null, false,
                append(common, "DEVIS_TRAVAUX", "JUSTIFICATIF_PROPRIETE", "EXPERTISE_IMMOBILIERE"));
        assertTrue(present.complete());
    }

    @Test
    void housingPromoterRequiresPromiseAndPropertyProof() {
        ChecklistResponse result = checklist("Crédit Direct Acquisition Logement (Durée ≤ 7 ans)", "PROMOTEUR_AGREE",
                append(common, "PROMESSE_VENTE", "JUSTIFICATIF_PROPRIETE"), "SANS_RACHAT");
        assertTrue(result.complete());
        assertTrue(result.reglesEnAttente().stream().anyMatch(rule -> rule.code().equals("AGREMENT_PROMOTEUR")));
    }

    @Test
    void housingPrivateRequiresExpertiseAndOneAlternativeOnly() {
        ChecklistResponse withoutAlternative = checklist("Crédit Direct Acquisition Logement (Durée > 7 ans et ≤ 15 ans)",
                "PARTICULIER", append(common, "PROMESSE_VENTE", "EXPERTISE_IMMOBILIERE"), "SANS_RACHAT");
        assertFalse(withoutAlternative.complete());
        ChecklistResponse withTitle = checklist("Crédit Direct Acquisition Logement (Durée > 7 ans et ≤ 15 ans)",
                "PARTICULIER", append(common, "PROMESSE_VENTE", "EXPERTISE_IMMOBILIERE", "TITRE_FONCIER"), "RACHAT_EXTERNE");
        assertTrue(withTitle.complete());
        assertTrue(withTitle.groupesAlternatifs().get(0).satisfait());
        ChecklistResponse withBoth = checklist("Crédit Direct Acquisition Logement (Durée ≤ 7 ans)", "PARTICULIER",
                append(common, "PROMESSE_VENTE", "EXPERTISE_IMMOBILIERE", "TITRE_FONCIER", "ACTE_NOTARIE"), "SANS_RACHAT");
        assertTrue(withBoth.complete());
    }

    @Test
    void constructionRequiresArchitectQuotePermitAndPropertyButNotUnconfirmedAppraisal() {
        ChecklistResponse result = checklist("Crédit Direct Construction (Durée ≤ 7 ans)", null,
                append(common, "DEVIS_ARCHITECTE", "PERMIS_BATIR", "JUSTIFICATIF_PROPRIETE"), "RACHAT_INTERNE");
        assertTrue(result.complete());
        assertFalse(item(result, "EXPERTISE_IMMOBILIERE").obligatoire());
    }

    @Test
    void landPromoterRequiresPropertyProof() {
        assertTrue(checklist("Crédit Direct Achat Terrain (Durée ≤ 7 ans)", "PROMOTEUR_AGREE",
                append(common, "JUSTIFICATIF_PROPRIETE"), "SANS_RACHAT").complete());
    }

    @Test
    void landPrivateRequiresTitleAndAppraisal() {
        ChecklistResponse missing = checklist("Crédit Direct Achat Terrain (Durée ≤ 7 ans)", "PARTICULIER",
                append(common, "TITRE_FONCIER"), "SANS_RACHAT");
        assertFalse(missing.complete());
        ChecklistResponse complete = checklist("Crédit Direct Achat Terrain (Durée ≤ 7 ans)", "PARTICULIER",
                append(common, "TITRE_FONCIER", "EXPERTISE_IMMOBILIERE"), "SANS_RACHAT");
        assertTrue(complete.complete());
        assertEquals("PENDING_CONFIRMATION", item(complete, "TITRE_FONCIER").regle());
    }

    @Test
    void missingSellerTypeAndAdditionalIncomeAreHandledConditionally() {
        ChecklistResponse missingSeller = checklist("Crédit Direct Achat Terrain (Durée ≤ 7 ans)", null, common, "SANS_RACHAT");
        assertFalse(missingSeller.complete());
        assertTrue(missingSeller.typeVendeurRequis());

        ChecklistResponse noIncome = service.generate(simulation("Crédit Direct Dépenses Courantes", "SANS_RACHAT"), null, false, common);
        assertTrue(noIncome.complete());
        assertFalse(item(noIncome, "JUSTIFICATIF_AUTRES_REVENUS").actif());

        ChecklistResponse extraIncome = service.generate(simulation("Crédit Direct Dépenses Courantes", "SANS_RACHAT"), null, true, common);
        assertFalse(extraIncome.complete());
        assertTrue(item(extraIncome, "JUSTIFICATIF_AUTRES_REVENUS").obligatoire());
    }

    @Test
    void pendingAttestationsAndBankNeverBlockCompleteness() {
        ChecklistResponse result = checklist("Crédit Direct Dépenses Courantes", null, common, "SANS_RACHAT");
        assertTrue(result.complete());
        assertEquals("EN_ATTENTE_CONFIRMATION", item(result, "ATTESTATION_TRAVAIL").statut());
        assertFalse(item(result, "ATTESTATION_TRAVAIL").obligatoire());
        assertEquals("DISABLED", item(result, "ATTESTATION_TRAVAIL").regle());
        assertFalse(item(result, "RELEVE_BANCAIRE").obligatoire());
    }

    private ChecklistResponse checklist(String product, String seller, List<String> uploaded, String scenario) {
        return service.generate(simulation(product, scenario), seller, false, uploaded);
    }

    private String simulation(String product, String scenario) {
        return "{\"libelle\":\"" + product + "\",\"typeSimulateur\":\"" + scenario +
                "\",\"simulation\":{\"typeCredit\":\"" + product + "\",\"scenario\":\"" + scenario +
                "\",\"montantDemande\":50000}}";
    }

    private ChecklistResponse.Item item(ChecklistResponse result, String code) {
        return result.documents().stream().filter(document -> document.code().equals(code)).findFirst().orElseThrow();
    }

    private List<String> append(List<String> source, String... extra) {
        java.util.ArrayList<String> result = new java.util.ArrayList<>(source);
        result.addAll(List.of(extra));
        return result;
    }
}
