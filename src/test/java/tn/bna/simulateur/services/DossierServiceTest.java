package tn.bna.simulateur.services;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import tn.bna.simulateur.entities.Dossier;
import tn.bna.simulateur.entities.DossierDocument;
import tn.bna.simulateur.entities.GestionUser.User;
import tn.bna.simulateur.repositories.DossierDocumentRepository;
import tn.bna.simulateur.repositories.DossierRepository;
import tn.bna.simulateur.repositories.GestionUser.UserRepository;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DossierServiceTest {
    @TempDir Path storage;

    private DossierRepository dossiers;
    private DossierDocumentRepository documents;
    private UserRepository users;
    private StubPaddleOcrClient ocr;
    private ObjectMapper mapper;
    private DossierService service;
    private User applicant;

    @BeforeEach
    void setUp() {
        dossiers = mock(DossierRepository.class);
        documents = mock(DossierDocumentRepository.class);
        users = mock(UserRepository.class);
        ocr = new StubPaddleOcrClient();
        mapper = new ObjectMapper();
        DocumentChecklistService checklist = new DocumentChecklistService(mapper, "", "", "", false, false, null);
        service = new DossierService(dossiers, documents, users, ocr, checklist, mapper);
        ReflectionTestUtils.setField(service, "storageDirectory", storage.toString());
        applicant = new User();
        applicant.setId(21L);
        applicant.setEmail("client@example.test");
        applicant.setCin("12345678");
        applicant.setNom("Ben Salem");
        applicant.setPrenom("Amina");
        when(users.findByEmail("client@example.test")).thenReturn(Optional.of(applicant));
    }

    @Test
    void OCRIdentityMismatchIsFlaggedForReviewAndDoesNotRejectDossier() throws Exception {
        ocr.set("CIN", extraction("CIN", Map.of("cin", "87654321", "nom", "Autre Nom", "prenom", "Autre Prenom")));
        ocr.set("BULLETIN_SALAIRE", extraction("BULLETIN_SALAIRE",
                Map.of("nom", "Autre Nom", "prenom", "Autre Prenom", "salaireNet", 2000.0)));
        when(dossiers.save(any(Dossier.class))).thenAnswer(invocation -> {
            Dossier dossier = invocation.getArgument(0);
            if (dossier.getId() == null) dossier.setId(44L);
            return dossier;
        });
        when(documents.findByDossierIdAndTypeDocumentAndActifTrue(anyLong(), anyString())).thenReturn(List.of());
        when(documents.findTopByDossierIdAndTypeDocumentOrderByVersionDesc(anyLong(), anyString())).thenReturn(Optional.empty());
        when(documents.save(any(DossierDocument.class))).thenAnswer(invocation -> {
            DossierDocument document = invocation.getArgument(0);
            document.setId(document.getId() == null ? 1L : document.getId());
            return document;
        });

        String simulation = "{\"libelle\":\"SANS_RACHAT — Crédit Direct Dépenses Courantes\",\"typeSimulateur\":\"SANS_RACHAT\",\"simulation\":{\"typeCredit\":\"Crédit Direct Dépenses Courantes\",\"scenario\":\"SANS_RACHAT\",\"salaireMensuelMoyen\":2000}}";
        Dossier created = service.create("client@example.test", simulation, true, null, false,
                List.of("CIN", "BULLETIN_SALAIRE"), List.of(pdf("cin.pdf"), pdf("salaire.pdf")), null, null, null);

        assertEquals("EN_ATTENTE_VALIDATION", created.getStatut());
        Map<String, Object> analysis = mapper.readValue(created.getAnalyseJson(), new TypeReference<>() { });
        assertEquals("OCR_A_VERIFIER", analysis.get("statut"));
        assertTrue(((List<?>) analysis.get("pointsVerification")).stream().anyMatch(
                alert -> alert.toString().contains("ne correspond pas") || alert.toString().contains("diffère")));
    }

    @Test
    void replacingDocumentKeepsPreviousFileAndOCRAsAnInactiveVersion() throws Exception {
        Dossier dossier = Dossier.builder().id(8L).userId(applicant.getId()).reference("DOS-8")
                .libelle("Aménagement").statut("EN_ATTENTE_VALIDATION").simulationJson(
                        "{\"typeSimulateur\":\"SANS_RACHAT\",\"simulation\":{\"typeCredit\":\"Crédit Direct Aménagement\",\"scenario\":\"SANS_RACHAT\",\"montantDemande\":10000,\"salaireMensuelMoyen\":2000}}")
                .revenusSupplementairesDeclares(false).build();
        when(dossiers.findByIdAndUserId(8L, applicant.getId())).thenReturn(Optional.of(dossier));
        List<DossierDocument> rows = new ArrayList<>(List.of(
                stored(1L, 8L, "CIN", 1, true, extraction("CIN", Map.of("cin", "12345678"))),
                stored(2L, 8L, "BULLETIN_SALAIRE", 1, true, extraction("BULLETIN_SALAIRE", Map.of("salaireNet", 2000.0))),
                stored(3L, 8L, "DEVIS_TRAVAUX", 1, true, extraction("DEVIS_TRAVAUX", Map.of("montantTotal", 8000.0)))));
        when(documents.findByDossierIdOrderByTypeDocumentAscVersionAsc(8L)).thenAnswer(invocation -> new ArrayList<>(rows));
        when(documents.findByDossierIdAndTypeDocumentAndActifTrue(8L, "DEVIS_TRAVAUX"))
                .thenAnswer(invocation -> rows.stream().filter(DossierDocument::isActif)
                        .filter(row -> row.getTypeDocument().equals("DEVIS_TRAVAUX")).toList());
        when(documents.findTopByDossierIdAndTypeDocumentOrderByVersionDesc(8L, "DEVIS_TRAVAUX"))
                .thenReturn(Optional.of(rows.get(2)));
        AtomicLong ids = new AtomicLong(3);
        when(documents.save(any(DossierDocument.class))).thenAnswer(invocation -> {
            DossierDocument value = invocation.getArgument(0);
            if (value.getId() == null) value.setId(ids.incrementAndGet());
            if (rows.stream().noneMatch(row -> Objects.equals(row.getId(), value.getId()))) rows.add(value);
            return value;
        });
        ocr.set("DEVIS_TRAVAUX", extraction("DEVIS_TRAVAUX", Map.of("entreprise", "Bâtir", "montantTotal", 9000.0)));
        when(dossiers.save(any(Dossier.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Dossier updated = service.replaceDocument("client@example.test", 8L, "DEVIS_TRAVAUX", true, pdf("devis-nouveau.pdf"));

        assertEquals("EN_ATTENTE_VALIDATION", updated.getStatut());
        assertEquals(2, rows.stream().filter(row -> row.getTypeDocument().equals("DEVIS_TRAVAUX")).count());
        DossierDocument old = rows.stream().filter(row -> row.getId().equals(3L)).findFirst().orElseThrow();
        DossierDocument latest = rows.stream().filter(row -> row.getTypeDocument().equals("DEVIS_TRAVAUX") && row.isActif()).findFirst().orElseThrow();
        assertFalse(old.isActif());
        assertEquals(1, old.getVersion());
        assertEquals(2, latest.getVersion());
        assertTrue(old.getResultatOcrJson().contains("8000.0"));
        assertTrue(latest.getResultatOcrJson().contains("9000.0"));
    }

    @Test
    void wrongDocumentTypeAndVehicleAgeAreWarningsForManualReview() {
        List<Map<String, Object>> uploaded = List.of(
                ocrDocument("CIN", "CIN", Map.of("cin", "12345678", "nom", "Ben Salem", "prenom", "Amina")),
                ocrDocument("BULLETIN_SALAIRE", "BULLETIN_SALAIRE",
                        Map.of("nom", "Ben Salem", "prenom", "Amina", "salaireNet", 2000.0)),
                ocrDocument("PROMESSE_VENTE", "PROMESSE_VENTE",
                        Map.of("acheteur", "Amina Ben Salem", "vendeur", "Ali", "marque", "Renault", "modele", "Clio")),
                ocrDocument("CARTE_GRISE", "CARTE_GRISE",
                        Map.of("proprietaire", "Ali", "marque", "Peugeot", "modele", "208", "datePremiereMiseEnCirculation", "2020-01-01")),
                ocrDocument("DEVIS_TRAVAUX", "CARTE_GRISE", Map.of("montantTotal", 8000.0)));

        Map<String, Object> result = service.analyze(Map.of("simulation", Map.of("salaireMensuelMoyen", 2000)), uploaded, applicant);
        List<?> alerts = (List<?>) result.get("pointsVerification");

        assertEquals("OCR_A_VERIFIER", result.get("statut"));
        assertTrue(alerts.stream().anyMatch(alert -> alert.toString().contains("type de pièce n'a pas été confirmé")));
        assertTrue(alerts.stream().anyMatch(alert -> alert.toString().contains("marque du véhicule diffère")));
        assertTrue(alerts.stream().anyMatch(alert -> alert.toString().contains("moins de 3 ans")));
    }

    @Test
    void applicantComparisonDoesNotInventChecksForDocumentsNotUploaded() {
        List<Map<String, Object>> uploaded = List.of(
                ocrDocument("CIN", "CIN", Map.of("cin", "12345678", "nom", "Autre Nom", "prenom", "Autre Prenom")),
                ocrDocument("BULLETIN_SALAIRE", "BULLETIN_SALAIRE",
                        Map.of("nom", "Autre Nom", "prenom", "Autre Prenom", "salaireNet", 2000.0)));

        Map<String, Object> result = service.analyze(
                Map.of("simulation", Map.of("salaireMensuelMoyen", 2000)), uploaded, applicant);
        List<?> alerts = (List<?>) result.get("pointsVerification");

        assertTrue(alerts.stream().anyMatch(alert -> alert.toString().contains("BULLETIN_SALAIRE")));
        assertFalse(alerts.stream().anyMatch(alert -> alert.toString().contains("FACTURE_PRO_FORMA")));
        assertFalse(alerts.stream().anyMatch(alert -> alert.toString().contains("PROMESSE_VENTE")));
    }

    @Test
    void promoterDeclarationNeedsSeparateManualVerification() {
        Dossier dossier = Dossier.builder().id(10L).userId(applicant.getId()).reference("DOS-10")
                .libelle("Logement promoteur").typeVendeur("PROMOTEUR_AGREE")
                .statutVerificationPromoteur("A_VERIFIER").statut("EN_ATTENTE_VALIDATION").build();
        when(dossiers.findById(10L)).thenReturn(Optional.of(dossier));
        DossierDocument document = DossierDocument.builder().id(2L).dossierId(10L).typeDocument("CIN")
                .version(1).storageKey("cin.pdf").nomFichier("cin.pdf").typeMime("application/pdf")
                .tailleOctets(10L).actif(true).statutValidation("VALIDE").build();
        when(documents.findByDossierIdOrderByTypeDocumentAscVersionAsc(10L)).thenReturn(List.of(document));
        when(dossiers.save(any(Dossier.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Dossier pending = service.reviewPromoter(10L, "A_VERIFIER", null);
        assertEquals("EN_ATTENTE_VERIFICATION_AGREMENT", pending.getStatut());

        Dossier verified = service.reviewPromoter(10L, "VERIFIE", "Vérification manuelle enregistrée");
        assertEquals("DOCUMENTS_VALIDES", verified.getStatut());
    }

    private MockMultipartFile pdf(String name) {
        return new MockMultipartFile("documents", name, "application/pdf", "%PDF-1.4\nOCR test\n".getBytes());
    }

    private DossierDocument stored(Long id, Long dossierId, String type, int version, boolean active,
                                   Map<String, Object> result) throws Exception {
        return DossierDocument.builder().id(id).dossierId(dossierId).typeDocument(type).version(version)
                .storageKey(id + ".pdf").nomFichier(type + ".pdf").typeMime("application/pdf").tailleOctets(20L)
                .actif(active).statutValidation("EN_ATTENTE").resultatOcrJson(mapper.writeValueAsString(result))
                .createdAt(java.time.LocalDateTime.now()).build();
    }

    private Map<String, Object> extraction(String type, Map<String, Object> fields) {
        return Map.of("ocrDisponible", true, "typeDocument", type, "statutType", "TYPE_CORRECT", "champs", fields,
                "confiance", 0.99, "alertes", List.of(), "texteExtrait", "document de test");
    }

    private Map<String, Object> ocrDocument(String expectedType, String detectedType, Map<String, Object> fields) {
        return Map.of("type", expectedType, "typeDocument", detectedType, "ocrDisponible", true,
                "confiance", 0.99, "champs", fields, "alertes", List.of());
    }

    private static class StubPaddleOcrClient extends PaddleOcrClient {
        private final Map<String, Map<String, Object>> responses = new HashMap<>();
        void set(String type, Map<String, Object> response) { responses.put(type, response); }
        @Override public Map<String, Object> extract(String typeDocument, byte[] bytes, String mimeType) {
            return responses.getOrDefault(typeDocument, Map.of("ocrDisponible", false, "typeDocument", "INCONNU",
                    "champs", Map.of(), "confiance", 0.0, "alertes", List.of("OCR test non configuré")));
        }
    }
}
