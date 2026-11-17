package tn.bna.simulateur.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import tn.bna.simulateur.entities.Dossier;
import tn.bna.simulateur.entities.DossierDocument;
import tn.bna.simulateur.entities.GestionUser.User;
import tn.bna.simulateur.dto.dossier.ChecklistResponse;
import tn.bna.simulateur.repositories.DossierRepository;
import tn.bna.simulateur.repositories.DossierDocumentRepository;
import tn.bna.simulateur.repositories.GestionUser.UserRepository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class DossierService {

    private static final long MAX_FILE_SIZE = 10L * 1024 * 1024;
    private static final Set<String> SUPPORTED_DOCUMENT_TYPES = Set.of("CIN", "BULLETIN_SALAIRE", "RELEVE_BANCAIRE",
            "ATTESTATION_SALAIRE_ANNUELLE", "ATTESTATION_TRAVAIL", "JUSTIFICATIF_AUTRES_REVENUS",
            "DEMANDE_CREDIT", "DEVIS_TRAVAUX", "FACTURE_PRO_FORMA", "PROMESSE_VENTE", "CARTE_GRISE",
            "JUSTIFICATIF_PROPRIETE", "TITRE_FONCIER", "ACTE_NOTARIE", "PERMIS_BATIR",
            "DEVIS_ARCHITECTE", "EXPERTISE_IMMOBILIERE");

    private final DossierRepository dossierRepository;
    private final DossierDocumentRepository dossierDocumentRepository;
    private final UserRepository userRepository;
    private final PaddleOcrClient ocrService;
    private final DocumentChecklistService checklistService;
    private final ObjectMapper objectMapper;

    @Value("${app.dossier.storage-directory:./data/dossiers}")
    private String storageDirectory;

    public List<Dossier> list(String username) {
        User user = findUser(username);
        return dossierRepository.findByUserIdOrderByCreatedAtDesc(user.getId());
    }

    public Dossier get(String username, Long id) {
        User user = findUser(username);
        return dossierRepository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Dossier introuvable"));
    }

    public Dossier replaceDocument(String username, Long dossierId, String typeDocument,
                                   boolean consentementTraitementOcr, MultipartFile file) {
        if (!consentementTraitementOcr) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le consentement au traitement OCR est requis.");
        }
        User user = findUser(username);
        Dossier dossier = dossierRepository.findByIdAndUserId(dossierId, user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Dossier introuvable"));
        String normalizedType = normalizeDocumentType(typeDocument);
        if (file == null || file.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le fichier est manquant.");
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Chaque document doit faire 10 Mo maximum.");
        }

        List<DossierDocument> history = dossierDocumentRepository.findByDossierIdOrderByTypeDocumentAscVersionAsc(dossierId);
        boolean replaceAlternative = Set.of("TITRE_FONCIER", "ACTE_NOTARIE").contains(normalizedType);
        List<String> activeTypes = history.stream().filter(DossierDocument::isActif)
                .map(DossierDocument::getTypeDocument)
                .filter(type -> !type.equals(normalizedType)
                        && !(replaceAlternative && Set.of("TITRE_FONCIER", "ACTE_NOTARIE").contains(type)))
                .toList();
        List<String> typesWithReplacement = new ArrayList<>(activeTypes);
        typesWithReplacement.add(normalizedType);
        ChecklistResponse checklist = checklistService.generate(dossier.getSimulationJson(), dossier.getTypeVendeur(),
                dossier.isRevenusSupplementairesDeclares(), typesWithReplacement);
        if (!checklist.complete()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Ce remplacement ne satisfait pas la checklist : " + String.join(", ", checklist.documentsManquants()));
        }

        try {
            String extension = validateAndGetExtension(file);
            byte[] bytes = file.getBytes();
            Path dossierPath = Paths.get(storageDirectory).toAbsolutePath().normalize()
                    .resolve(user.getId().toString()).resolve(dossierId.toString());
            Files.createDirectories(dossierPath);
            String storedName = UUID.randomUUID() + extension;
            Files.write(dossierPath.resolve(storedName), bytes, StandardOpenOption.CREATE_NEW);
            Map<String, Object> extraction = ocrService.extract(normalizedType, bytes, mimeType(extension));
            if (replaceAlternative) {
                history.stream().filter(DossierDocument::isActif)
                        .filter(old -> Set.of("TITRE_FONCIER", "ACTE_NOTARIE").contains(old.getTypeDocument()))
                        .forEach(old -> {
                            old.setActif(false);
                            dossierDocumentRepository.save(old);
                        });
            }
            DossierDocument stored = persistDocumentVersion(dossierId,
                    new UploadedDocument(normalizedType, file, extension, bytes), storedName, extraction, bytes.length);

            List<Map<String, Object>> allDocuments = dossierDocumentRepository
                    .findByDossierIdOrderByTypeDocumentAscVersionAsc(dossierId).stream().map(this::toDocumentMap).toList();
            List<Map<String, Object>> currentDocuments = allDocuments.stream()
                    .filter(document -> Boolean.TRUE.equals(document.get("actif"))).toList();
            Map<String, Object> analysis = analyze(parseObject(dossier.getSimulationJson(), "Simulation invalide."),
                    currentDocuments, user);
            dossier.setDocumentsJson(writeJson(allDocuments));
            dossier.setChecklistJson(writeJson(checklist));
            dossier.setAnalyseJson(writeJson(analysis));
            dossier.setStatut("EN_ATTENTE_VALIDATION");
            dossier.setStatutValidationDocumentaire("EN_ATTENTE");
            dossier.setMotifValidationDocumentaire(null);
            return dossierRepository.save(dossier);
        } catch (IOException e) {
            log.error("Impossible de remplacer une pièce du dossier {}.", dossierId, e);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le document n'a pas pu être enregistré.");
        }
    }

    public List<Dossier> listAllForAdmin() {
        return dossierRepository.findAllByOrderByCreatedAtDesc();
    }

    public Dossier getForAdmin(Long id) {
        return dossierRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Dossier introuvable"));
    }

    public DossierDocument getDocumentForAdmin(Long dossierId, Long documentId) {
        return dossierDocumentRepository.findByIdAndDossierId(documentId, dossierId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document introuvable"));
    }

    public Map<String, Object> reviewDocument(Long dossierId, Long documentId, String statut,
                                               String motif, Map<String, Object> corrections, String admin) {
        Set<String> allowed = Set.of("VALIDE", "REJETE", "REMPLACEMENT_DEMANDE");
        if (!allowed.contains(statut)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Statut documentaire invalide.");
        if (("REJETE".equals(statut) || "REMPLACEMENT_DEMANDE".equals(statut))
                && (motif == null || motif.isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Un motif est obligatoire pour rejeter ou remplacer une pièce.");
        }
        Dossier dossier = getForAdmin(dossierId);
        DossierDocument document = getDocumentForAdmin(dossierId, documentId);
        document.setStatutValidation(statut);
        document.setMotifAdmin(motif);
        document.setValidePar(admin);
        if (corrections != null && !corrections.isEmpty()) {
            document.setCorrectionsAdminJson(writeJson(corrections));
        }
        List<Object> history = parseArray(document.getHistoriqueCorrectionsAdminJson());
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("date", java.time.LocalDateTime.now().toString());
        entry.put("admin", admin);
        entry.put("statut", statut);
        entry.put("motif", motif);
        entry.put("corrections", corrections == null ? Map.of() : corrections);
        history.add(entry);
        document.setHistoriqueCorrectionsAdminJson(writeJson(history));
        dossierDocumentRepository.save(document);

        List<Map<String, Object>> allDocumentMaps = dossierDocumentRepository
                .findByDossierIdOrderByTypeDocumentAscVersionAsc(dossierId).stream().map(this::toDocumentMap).toList();
        List<Map<String, Object>> currentDocumentMaps = allDocumentMaps.stream()
                .filter(item -> Boolean.TRUE.equals(item.get("actif"))).toList();
        User applicant = findUserById(dossier.getUserId());
        dossier.setAnalyseJson(writeJson(analyze(
                parseObject(dossier.getSimulationJson(), "Simulation invalide."), currentDocumentMaps, applicant)));

        List<DossierDocument> activeDocuments = dossierDocumentRepository
                .findByDossierIdOrderByTypeDocumentAscVersionAsc(dossierId).stream().filter(DossierDocument::isActif).toList();
        String dossierStatus = resolveDocumentStatus(dossier, activeDocuments);
        dossier.setStatutValidationDocumentaire(dossierStatus);
        dossier.setStatut(dossierStatus);
        dossier.setMotifValidationDocumentaire(motif);
        dossier.setDocumentsJson(writeJson(allDocumentMaps));
        dossierRepository.save(dossier);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("dossier", dossier);
        result.put("document", toDocumentMap(document));
        result.put("note", "La validation documentaire ne constitue pas une décision d'octroi du crédit.");
        return result;
    }

    public Map<String, Object> getAdminDetail(Long dossierId) {
        Dossier dossier = getForAdmin(dossierId);
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("dossier", dossier);
        detail.put("documents", dossierDocumentRepository
                .findByDossierIdOrderByTypeDocumentAscVersionAsc(dossierId).stream().map(this::toDocumentMap).toList());
        detail.put("checklist", parseObjectOrEmpty(dossier.getChecklistJson()));
        detail.put("analyse", parseObjectOrEmpty(dossier.getAnalyseJson()));
        detail.put("note", "La validation documentaire ne constitue pas une décision d'octroi du crédit.");
        return detail;
    }

    public Dossier reviewPromoter(Long dossierId, String statut, String motif) {
        if (!Set.of("VERIFIE", "NON_VERIFIE", "A_VERIFIER").contains(statut)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Statut de vérification du promoteur invalide.");
        }
        if (!"A_VERIFIER".equals(statut) && (motif == null || motif.isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Ajoutez une note de vérification ou le motif du résultat.");
        }
        Dossier dossier = getForAdmin(dossierId);
        if (!"PROMOTEUR_AGREE".equals(dossier.getTypeVendeur())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Ce dossier ne déclare pas un vendeur promoteur agréé.");
        }
        dossier.setStatutVerificationPromoteur(statut);
        dossier.setMotifVerificationPromoteur(motif);
        List<DossierDocument> activeDocuments = dossierDocumentRepository
                .findByDossierIdOrderByTypeDocumentAscVersionAsc(dossierId).stream().filter(DossierDocument::isActif).toList();
        String status = resolveDocumentStatus(dossier, activeDocuments);
        dossier.setStatut(status);
        dossier.setStatutValidationDocumentaire(status);
        return dossierRepository.save(dossier);
    }

    public Map<String, Object> reanalyzeDocument(Long dossierId, Long documentId, String admin) {
        Dossier dossier = getForAdmin(dossierId);
        DossierDocument document = getDocumentForAdmin(dossierId, documentId);
        byte[] bytes = loadOriginalDocument(dossierId, documentId);
        Map<String, Object> previous = parseObjectOrEmpty(document.getResultatOcrJson());
        List<Object> ocrHistory = parseArray(document.getHistoriqueOcrJson());
        Map<String, Object> historyEntry = new LinkedHashMap<>();
        historyEntry.put("date", java.time.LocalDateTime.now().toString());
        historyEntry.put("admin", admin);
        historyEntry.put("resultat", previous);
        ocrHistory.add(historyEntry);
        document.setHistoriqueOcrJson(writeJson(ocrHistory));
        document.setResultatOcrJson(writeJson(ocrService.extract(document.getTypeDocument(), bytes, document.getTypeMime())));
        document.setStatutValidation("EN_ATTENTE");
        document.setValidePar(null);
        dossierDocumentRepository.save(document);

        List<Map<String, Object>> allDocuments = dossierDocumentRepository
                .findByDossierIdOrderByTypeDocumentAscVersionAsc(dossierId).stream().map(this::toDocumentMap).toList();
        List<Map<String, Object>> currentDocuments = allDocuments.stream()
                .filter(entry -> Boolean.TRUE.equals(entry.get("actif"))).toList();
        User user = findUserById(dossier.getUserId());
        Map<String, Object> simulation = parseObject(dossier.getSimulationJson(), "Simulation invalide.");
        dossier.setDocumentsJson(writeJson(allDocuments));
        dossier.setAnalyseJson(writeJson(analyze(simulation, currentDocuments, user)));
        dossier.setStatut("EN_ATTENTE_VALIDATION");
        dossier.setStatutValidationDocumentaire("EN_ATTENTE");
        dossierRepository.save(dossier);
        return getAdminDetail(dossierId);
    }

    /** Re-run OCR on the active documents of the authenticated applicant. */
    public Dossier reanalyzeOwnDocuments(String username, Long dossierId) {
        User user = findUser(username);
        Dossier dossier = dossierRepository.findByIdAndUserId(dossierId, user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Dossier introuvable"));
        List<DossierDocument> activeDocuments = dossierDocumentRepository
                .findByDossierIdOrderByTypeDocumentAscVersionAsc(dossierId).stream()
                .filter(DossierDocument::isActif).toList();
        if (activeDocuments.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Aucun document actif à analyser.");
        }
        for (DossierDocument document : activeDocuments) {
            byte[] bytes = loadOriginalDocument(dossierId, document.getId());
            List<Object> history = parseArray(document.getHistoriqueOcrJson());
            history.add(Map.of(
                    "date", java.time.LocalDateTime.now().toString(),
                    "acteur", "CLIENT",
                    "resultat", parseObjectOrEmpty(document.getResultatOcrJson())
            ));
            document.setHistoriqueOcrJson(writeJson(history));
            document.setResultatOcrJson(writeJson(ocrService.extract(
                    document.getTypeDocument(), bytes, document.getTypeMime())));
            document.setStatutValidation("EN_ATTENTE");
            document.setValidePar(null);
            document.setMotifAdmin(null);
            dossierDocumentRepository.save(document);
        }
        List<Map<String, Object>> allDocuments = dossierDocumentRepository
                .findByDossierIdOrderByTypeDocumentAscVersionAsc(dossierId).stream().map(this::toDocumentMap).toList();
        List<Map<String, Object>> currentDocuments = allDocuments.stream()
                .filter(entry -> Boolean.TRUE.equals(entry.get("actif"))).toList();
        dossier.setDocumentsJson(writeJson(allDocuments));
        dossier.setAnalyseJson(writeJson(analyze(
                parseObject(dossier.getSimulationJson(), "Simulation invalide."), currentDocuments, user)));
        dossier.setStatut("EN_ATTENTE_VALIDATION");
        dossier.setStatutValidationDocumentaire("EN_ATTENTE");
        dossier.setMotifValidationDocumentaire(null);
        return dossierRepository.save(dossier);
    }

    public byte[] loadOriginalDocument(Long dossierId, Long documentId) {
        DossierDocument document = getDocumentForAdmin(dossierId, documentId);
        try {
            Path path = Paths.get(storageDirectory).toAbsolutePath().normalize()
                    .resolve(findUserById(getForAdmin(dossierId).getUserId()).getId().toString())
                    .resolve(dossierId.toString()).resolve(document.getStorageKey()).normalize();
            Path root = Paths.get(storageDirectory).toAbsolutePath().normalize();
            if (!path.startsWith(root) || !Files.isRegularFile(path)) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Fichier introuvable.");
            }
            return Files.readAllBytes(path);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Fichier introuvable.");
        }
    }

    public Dossier create(
            String username,
            String simulationJson,
            boolean consentementTraitementOcr,
            String typeVendeur,
            boolean revenusSupplementairesDeclares,
            List<String> typeDocuments,
            List<MultipartFile> fichiers,
            MultipartFile cin,
            MultipartFile bulletinSalaire,
            MultipartFile releveBancaire
    ) {
        if (!consentementTraitementOcr) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Le consentement au traitement OCR est requis.");
        }
        if (simulationJson == null || simulationJson.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La simulation associée est manquante.");
        }

        Map<String, Object> simulation = parseObject(simulationJson, "Simulation invalide.");
        List<UploadedDocument> documents = new ArrayList<>();
        if (fichiers != null && !fichiers.isEmpty()) {
            if (typeDocuments == null || typeDocuments.size() != fichiers.size()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Chaque fichier doit être associé à un type de document.");
            }
            for (int i = 0; i < fichiers.size(); i++) addIfPresent(documents, typeDocuments.get(i), fichiers.get(i));
        }
        addIfPresent(documents, "CIN", cin);
        addIfPresent(documents, "BULLETIN_SALAIRE", bulletinSalaire);
        addIfPresent(documents, "RELEVE_BANCAIRE", releveBancaire);

        try {
            List<UploadedDocument> validatedDocuments = new ArrayList<>();
            for (UploadedDocument document : documents) {
                String extension = validateAndGetExtension(document.file());
                validatedDocuments.add(new UploadedDocument(
                        document.type(), document.file(), extension, document.file().getBytes()
                ));
            }
            documents = validatedDocuments;
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Un document joint n'a pas pu être lu.");
        }

        ChecklistResponse checklist = checklistService.generate(
                simulationJson, typeVendeur, revenusSupplementairesDeclares,
                documents.stream().map(UploadedDocument::type).toList());
        if (!checklist.complete()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Le dossier est incomplet : " + String.join(", ", checklist.documentsManquants()));
        }

        User user = findUser(username);
        Dossier dossier = dossierRepository.save(Dossier.builder()
                .userId(user.getId())
                .reference("DOS-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase(Locale.ROOT))
                .libelle(resolveLibelle(simulation))
                .statut("EN_ATTENTE_VALIDATION")
                .statutValidationDocumentaire("EN_ATTENTE")
                .typeVendeur(checklist.typeVendeur())
                .statutVerificationPromoteur("PROMOTEUR_AGREE".equals(checklist.typeVendeur()) ? "A_VERIFIER" : null)
                .revenusSupplementairesDeclares(revenusSupplementairesDeclares)
                .simulationJson(simulationJson)
                .createdAt(java.time.LocalDateTime.now())
                .build());

        List<Map<String, Object>> documentResults = new ArrayList<>();
        try {
            Path dossierPath = Paths.get(storageDirectory)
                    .toAbsolutePath().normalize()
                    .resolve(user.getId().toString())
                    .resolve(dossier.getId().toString());
            Files.createDirectories(dossierPath);

            for (UploadedDocument document : documents) {
                byte[] bytes = document.bytes();
                String storedName = UUID.randomUUID() + document.extension();
                Files.write(dossierPath.resolve(storedName), bytes, StandardOpenOption.CREATE_NEW);

                Map<String, Object> extraction = ocrService.extract(
                        document.type(), bytes, mimeType(document.extension())
                );
                DossierDocument storedDocument = persistDocumentVersion(dossier.getId(), document, storedName, extraction, bytes.length);
                documentResults.add(toDocumentMap(storedDocument));
            }
        } catch (IOException e) {
            log.error("Impossible d'enregistrer les pièces du dossier {}.", dossier.getId(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Impossible d'enregistrer les documents joints.");
        }

        Map<String, Object> analysis = analyze(simulation, documentResults, user);
        dossier.setDocumentsJson(writeJson(documentResults));
        dossier.setChecklistJson(writeJson(checklist));
        dossier.setAnalyseJson(writeJson(analysis));
        return dossierRepository.save(dossier);
    }

    private String resolveDocumentStatus(Dossier dossier, List<DossierDocument> activeDocuments) {
        if (activeDocuments.isEmpty()) return "EN_ATTENTE_VALIDATION";
        if (activeDocuments.stream().anyMatch(doc -> "REJETE".equals(doc.getStatutValidation())
                || "REMPLACEMENT_DEMANDE".equals(doc.getStatutValidation()))) return "A_COMPLETER";
        if (!activeDocuments.stream().allMatch(doc -> "VALIDE".equals(doc.getStatutValidation()))) return "EN_ATTENTE_VALIDATION";
        if ("PROMOTEUR_AGREE".equals(dossier.getTypeVendeur())) {
            String promoterStatus = dossier.getStatutVerificationPromoteur();
            if (!"VERIFIE".equals(promoterStatus)) {
                return "NON_VERIFIE".equals(promoterStatus) ? "AGREMENT_A_VERIFIER_MANUELLEMENT"
                        : "EN_ATTENTE_VERIFICATION_AGREMENT";
            }
        }
        return "DOCUMENTS_VALIDES";
    }

    private DossierDocument persistDocumentVersion(Long dossierId, UploadedDocument document, String storedName,
                                                   Map<String, Object> extraction, long size) {
        List<DossierDocument> existing = dossierDocumentRepository
                .findByDossierIdAndTypeDocumentAndActifTrue(dossierId, document.type());
        existing.forEach(previous -> {
            previous.setActif(false);
            dossierDocumentRepository.save(previous);
        });
        int version = dossierDocumentRepository.findTopByDossierIdAndTypeDocumentOrderByVersionDesc(dossierId, document.type())
                .map(previous -> previous.getVersion() + 1).orElse(1);
        return dossierDocumentRepository.save(DossierDocument.builder()
                .dossierId(dossierId)
                .typeDocument(document.type())
                .version(version)
                .storageKey(storedName)
                .nomFichier(safeOriginalName(document.file().getOriginalFilename()))
                .typeMime(mimeType(document.extension()))
                .tailleOctets(size)
                .actif(true)
                .statutValidation("EN_ATTENTE")
                .resultatOcrJson(writeJson(extraction))
                .createdAt(java.time.LocalDateTime.now())
                .build());
    }

    private Map<String, Object> toDocumentMap(DossierDocument document) {
        Map<String, Object> result = parseObject(document.getResultatOcrJson(), "Résultat OCR invalide.");
        Object ocrFields = result.get("champs");
        Map<String, Object> effectiveFields = new LinkedHashMap<>();
        if (ocrFields instanceof Map<?, ?> fields) fields.forEach((key, value) -> effectiveFields.put(String.valueOf(key), value));
        Map<String, Object> corrections = parseObjectOrEmpty(document.getCorrectionsAdminJson());
        if (!corrections.isEmpty()) {
            result.put("champsOCR", effectiveFields);
            effectiveFields.putAll(corrections);
            result.put("champs", effectiveFields);
        }
        result.put("id", document.getId());
        result.put("type", document.getTypeDocument());
        result.put("storageKey", document.getStorageKey());
        result.put("nomFichier", document.getNomFichier());
        result.put("typeMime", document.getTypeMime());
        result.put("tailleOctets", document.getTailleOctets());
        result.put("version", document.getVersion());
        result.put("actif", document.isActif());
        result.put("statutValidation", document.getStatutValidation());
        result.put("motifAdmin", document.getMotifAdmin());
        result.put("validePar", document.getValidePar());
        result.put("correctionsAdmin", corrections);
        result.put("historiqueCorrectionsAdmin", parseArray(document.getHistoriqueCorrectionsAdminJson()));
        result.put("historiqueOcr", parseArray(document.getHistoriqueOcrJson()));
        return result;
    }

    private Map<String, Object> parseObjectOrEmpty(String json) {
        if (json == null || json.isBlank()) return new LinkedHashMap<>();
        return parseObject(json, "Données documentaires invalides.");
    }

    private List<Object> parseArray(String json) {
        if (json == null || json.isBlank()) return new ArrayList<>();
        try { return objectMapper.readValue(json, new TypeReference<>() { }); }
        catch (JsonProcessingException e) { return new ArrayList<>(); }
    }

    private String normalizeDocumentType(String type) {
        if (type == null || type.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Type de document manquant.");
        }
        String normalized = type.trim().toUpperCase(Locale.ROOT);
        if (!SUPPORTED_DOCUMENT_TYPES.contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Type de document non pris en charge : " + normalized);
        }
        return normalized;
    }

    private User findUserById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Utilisateur introuvable"));
    }

    private User findUser(String username) {
        return userRepository.findByEmail(username)
                .or(() -> userRepository.findByCin(username))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                        "Utilisateur de la session introuvable."));
    }

    private void addIfPresent(List<UploadedDocument> documents, String type, MultipartFile file) {
        if (file != null && !file.isEmpty()) {
            if (file.getSize() > MAX_FILE_SIZE) {
                throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,
                        "Chaque document doit faire 10 Mo maximum.");
            }
            documents.add(new UploadedDocument(normalizeDocumentType(type), file));
        }
    }

    private String validateAndGetExtension(MultipartFile file) throws IOException {
        String original = safeOriginalName(file.getOriginalFilename()).toLowerCase(Locale.ROOT);
        byte[] header = file.getBytes();
        if (original.endsWith(".pdf") && startsWith(header, new byte[]{'%', 'P', 'D', 'F', '-'})) {
            return ".pdf";
        }
        if (original.endsWith(".png") && startsWith(header,
                new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A})) {
            return ".png";
        }
        if ((original.endsWith(".jpg") || original.endsWith(".jpeg")) &&
                header.length >= 3 && (header[0] & 0xFF) == 0xFF &&
                (header[1] & 0xFF) == 0xD8 && (header[2] & 0xFF) == 0xFF) {
            return ".jpg";
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Format non pris en charge. Utilisez un PDF, un JPG ou un PNG valide.");
    }

    private boolean startsWith(byte[] bytes, byte[] prefix) {
        if (bytes.length < prefix.length) return false;
        for (int i = 0; i < prefix.length; i++) {
            if (bytes[i] != prefix[i]) return false;
        }
        return true;
    }

    private String mimeType(String extension) {
        return switch (extension) {
            case ".pdf" -> "application/pdf";
            case ".png" -> "image/png";
            default -> "image/jpeg";
        };
    }

    private String safeOriginalName(String name) {
        if (name == null || name.isBlank()) return "document";
        String leaf = name.replace('\\', '/');
        leaf = leaf.substring(leaf.lastIndexOf('/') + 1).replaceAll("[^a-zA-Z0-9._ -]", "_");
        return leaf.length() > 120 ? leaf.substring(0, 120) : leaf;
    }

    private String resolveLibelle(Map<String, Object> simulation) {
        Object libelle = simulation.get("libelle");
        if (libelle != null && !libelle.toString().isBlank()) return libelle.toString();
        Object scenario = simulation.get("scenarioLabel");
        return scenario == null ? "Dossier de crédit" : "Dossier — " + scenario;
    }

    Map<String, Object> analyze(
            Map<String, Object> simulation,
            List<Map<String, Object>> documents,
            User applicant
    ) {
        List<String> alerts = new ArrayList<>();
        Set<String> documentTypes = new HashSet<>();
        documents.forEach(document -> documentTypes.add(String.valueOf(document.get("type"))));

        if (!documentTypes.contains("CIN")) alerts.add("Pièce d'identité manquante.");
        if (!documentTypes.contains("BULLETIN_SALAIRE")) alerts.add("Bulletin de salaire manquant.");

        boolean ocrFailed = documents.stream().anyMatch(document -> !Boolean.TRUE.equals(document.get("ocrDisponible")));
        if (ocrFailed) alerts.add("La lecture automatique d'au moins un document n'a pas abouti.");

        List<String> cinValues = new ArrayList<>();
        String normalizedRegisteredCin = applicant.getCin() == null ? "" : applicant.getCin().replaceAll("\\D", "");
        String applicantName = normalizePersonName(applicant.getNom());
        String applicantFirstName = normalizePersonName(applicant.getPrenom());
        Double salaryDetected = null;
        Map<String, Map<?, ?>> fieldsByType = new HashMap<>();
        for (Map<String, Object> document : documents) {
            Object documentAlerts = document.get("alertes");
            if (documentAlerts instanceof Collection<?> collection) {
                for (Object alert : collection) {
                    if (alert != null && !alert.toString().isBlank()) {
                        alerts.add(String.valueOf(document.get("type")) + " : " + alert);
                    }
                }
            }
            Object confidence = document.get("confiance");
            if (!(confidence instanceof Number n) || n.doubleValue() < 0.55) {
                alerts.add("Qualité de lecture faible pour " + document.get("type") + ".");
            }
            Object recognizedType = document.get("typeDocument");
            if (recognizedType != null && !recognizedType.toString().equalsIgnoreCase(String.valueOf(document.get("type")))) {
                alerts.add("Le type de pièce n'a pas été confirmé pour " + document.get("type") + ".");
            }
            Object fieldsValue = document.get("champs");
            if (fieldsValue instanceof Map<?, ?> fields) {
                fieldsByType.put(String.valueOf(document.get("type")), fields);
                if ("CIN".equals(document.get("type")) &&
                        (fields.get("cin") == null || fields.get("cin").toString().isBlank())) {
                    alerts.add("Le numéro de CIN n'a pas pu être lu automatiquement.");
                }
                Object cin = fields.get("cin");
                if (cin != null && !cin.toString().isBlank()) cinValues.add(cin.toString().replaceAll("\\D", ""));
                if ("BULLETIN_SALAIRE".equals(document.get("type"))) {
                    salaryDetected = toDouble(fields.get("salaireNet"));
                    if (salaryDetected == null) {
                        alerts.add("Le salaire net n'a pas pu être lu sur le bulletin.");
                    }
                }
                // Compare a document only with the fields it actually owns.
                // The old unconditional calls produced false alerts for a
                // pro-forma invoice and a sale promise that were never uploaded.
                switch (String.valueOf(document.get("type"))) {
                    case "CIN" -> compareApplicantName(fields, "CIN", applicantName, applicantFirstName, alerts);
                    case "BULLETIN_SALAIRE" -> compareApplicantName(fields, "BULLETIN_SALAIRE",
                            applicantName, applicantFirstName, alerts);
                    case "FACTURE_PRO_FORMA" -> compareApplicantName(fields, "FACTURE_PRO_FORMA",
                            applicantName, applicantFirstName, alerts);
                    case "PROMESSE_VENTE" -> compareApplicantName(fields, "PROMESSE_VENTE",
                            applicantName, applicantFirstName, alerts);
                    default -> { }
                }
            } else {
                alerts.add("Aucun champ exploitable n'a été extrait de " + document.get("type") + ".");
            }
        }

        compareField(fieldsByType.get("PROMESSE_VENTE"), "marque", fieldsByType.get("CARTE_GRISE"), "marque",
                "La marque du véhicule diffère entre la promesse de vente et la carte grise.", alerts);
        compareField(fieldsByType.get("PROMESSE_VENTE"), "modele", fieldsByType.get("CARTE_GRISE"), "modele",
                "Le modèle du véhicule diffère entre la promesse de vente et la carte grise.", alerts);
        compareField(fieldsByType.get("PROMESSE_VENTE"), "vendeur", fieldsByType.get("CARTE_GRISE"), "proprietaire",
                "Le vendeur de la promesse ne correspond pas au propriétaire de la carte grise.", alerts);
        compareField(fieldsByType.get("PROMESSE_VENTE"), "vendeur", fieldsByType.get("TITRE_FONCIER"), "proprietaire",
                "Le vendeur de la promesse ne correspond pas au propriétaire du titre foncier.", alerts);
        compareField(fieldsByType.get("PROMESSE_VENTE"), "vendeur", fieldsByType.get("JUSTIFICATIF_PROPRIETE"), "proprietaire",
                "Le vendeur de la promesse ne correspond pas au propriétaire déclaré.", alerts);
        compareField(fieldsByType.get("CIN"), "nom", fieldsByType.get("BULLETIN_SALAIRE"), "nom",
                "Le nom diffère entre la CIN et le bulletin de salaire ; vérification manuelle requise.", alerts);
        compareField(fieldsByType.get("CIN"), "prenom", fieldsByType.get("BULLETIN_SALAIRE"), "prenom",
                "Le prénom diffère entre la CIN et le bulletin de salaire ; vérification manuelle requise.", alerts);
        compareField(fieldsByType.get("PROMESSE_VENTE"), "bien", fieldsByType.get("TITRE_FONCIER"), "bien",
                "La désignation du bien diffère entre la promesse et le titre foncier.", alerts);
        compareField(fieldsByType.get("PROMESSE_VENTE"), "localisation", fieldsByType.get("TITRE_FONCIER"), "localisation",
                "La localisation du bien diffère entre la promesse et le titre foncier.", alerts);
        compareField(fieldsByType.get("PROMESSE_VENTE"), "bien", fieldsByType.get("JUSTIFICATIF_PROPRIETE"), "bien",
                "La désignation du bien diffère entre la promesse et le justificatif de propriété.", alerts);
        compareField(fieldsByType.get("PROMESSE_VENTE"), "localisation", fieldsByType.get("JUSTIFICATIF_PROPRIETE"), "localisation",
                "La localisation diffère entre la promesse et le justificatif de propriété.", alerts);
        compareField(fieldsByType.get("PROMESSE_VENTE"), "localisation", fieldsByType.get("EXPERTISE_IMMOBILIERE"), "localisation",
                "La localisation diffère entre la promesse et l'expertise.", alerts);
        if (checklistService.getTitreRecentJours() != null) {
            Map<?, ?> titleFields = fieldsByType.get("TITRE_FONCIER");
            if (titleFields != null) {
                java.time.LocalDate titleDate = parseDate(extractedValue(titleFields.get("date")));
                if (titleDate == null) {
                    alerts.add("La date du titre foncier n'a pas pu être lue pour vérifier sa récence.");
                } else if (titleDate.plusDays(checklistService.getTitreRecentJours()).isBefore(java.time.LocalDate.now())) {
                    alerts.add("Le titre foncier dépasse la durée de récence configurée.");
                }
            }
        }
        Map<?, ?> vehicleRegistration = fieldsByType.get("CARTE_GRISE");
        if (vehicleRegistration != null) {
            java.time.LocalDate firstDate = parseDate(extractedValue(vehicleRegistration.get("datePremiereMiseEnCirculation")));
            if (firstDate != null) {
                java.time.LocalDate referenceDate = checklistService.getDateReferenceVehicule();
                if (firstDate.isAfter(referenceDate)) {
                    alerts.add("La date de première mise en circulation est postérieure à la date de référence.");
                } else if (!firstDate.plusYears(3).isAfter(referenceDate)) {
                    alerts.add("Le véhicule ne respecte pas la condition d'âge : il doit avoir moins de 3 ans.");
                }
            }
        }

        if (cinValues.stream().distinct().count() > 1) {
            alerts.add("Les numéros d'identité relevés dans les pièces ne concordent pas.");
        }
        if (!normalizedRegisteredCin.isBlank() && cinValues.stream().anyMatch(cin -> !cin.isBlank()) &&
                cinValues.stream().filter(cin -> !cin.isBlank())
                        .anyMatch(cin -> !normalizedRegisteredCin.equals(cin))) {
            alerts.add("Le numéro de CIN lu ne correspond pas à celui du compte client.");
        }

        Double salaryDeclared = declaredSalary(simulation);
        if (salaryDeclared != null && salaryDetected != null && salaryDeclared > 0 &&
                Math.abs(salaryDetected - salaryDeclared) / salaryDeclared > 0.20) {
            alerts.add("Le salaire lu sur le bulletin diffère de plus de 20 % du montant déclaré.");
        }

        String status;
        if (!documentTypes.contains("CIN") || !documentTypes.contains("BULLETIN_SALAIRE")) {
            status = "OCR_INCOMPLET";
        } else if (ocrFailed || !alerts.isEmpty()) {
            status = "OCR_A_VERIFIER";
        } else {
            status = "PREANALYSE_TERMINEE";
        }

        Map<String, Object> analysis = new LinkedHashMap<>();
        analysis.put("statut", status);
        analysis.put("message", status.equals("PREANALYSE_TERMINEE")
                ? "La préanalyse est terminée. Un administrateur doit vérifier les documents avant toute décision."
                : "Vérifiez les éléments signalés ; l'administrateur réalisera la validation documentaire finale.");
        analysis.put("pointsVerification", alerts);
        analysis.put("salaireDeclare", salaryDeclared);
        analysis.put("salaireDetecte", salaryDetected);
        analysis.put("note", "La préanalyse OCR est indicative. La validation documentaire ne constitue pas une décision d'octroi de crédit.");
        return analysis;
    }

    private void compareApplicantName(Map<?, ?> fields, String type, String expectedLastName,
                                      String expectedFirstName, List<String> alerts) {
        if (fields == null) return;
        String lastName = normalizePersonName(extractedValue(fields.get("nom")));
        String firstName = normalizePersonName(extractedValue(fields.get("prenom")));
        if (!expectedLastName.isBlank() && !lastName.isBlank() && !namesMatch(expectedLastName, lastName)) {
            alerts.add("Le nom lu sur " + type + " diffère du nom déclaré dans le compte ; vérification manuelle requise.");
        }
        if (!expectedFirstName.isBlank() && !firstName.isBlank() && !namesMatch(expectedFirstName, firstName)) {
            alerts.add("Le prénom lu sur " + type + " diffère du prénom déclaré dans le compte ; vérification manuelle requise.");
        }
        String buyer = normalizePersonName(firstExtractedValue(fields, "acheteur", "client", "beneficiaire"));
        String applicantFullName = (expectedLastName + " " + expectedFirstName).trim();
        if (!applicantFullName.isBlank() && !buyer.isBlank() && !namesMatch(applicantFullName, buyer)) {
            alerts.add("L'acheteur indiqué sur la pièce ne correspond pas clairement au demandeur ; vérification manuelle requise.");
        }
    }

    private String firstExtractedValue(Map<?, ?> fields, String... names) {
        for (String name : names) {
            String value = extractedValue(fields.get(name));
            if (!value.isBlank()) return value;
        }
        return "";
    }

    private void compareField(Map<?, ?> first, String firstField, Map<?, ?> second, String secondField,
                              String message, List<String> alerts) {
        if (first == null || second == null) return;
        String a = normalizePersonName(extractedValue(first.get(firstField)));
        String b = normalizePersonName(extractedValue(second.get(secondField)));
        if (!a.isBlank() && !b.isBlank() && !namesMatch(a, b)) alerts.add(message);
    }

    private String extractedValue(Object field) {
        if (field instanceof Map<?, ?> value) {
            Object normalized = value.get("normalizedValue");
            return normalized == null ? "" : normalized.toString();
        }
        return field == null ? "" : field.toString();
    }

    private String normalizePersonName(String value) {
        if (value == null) return "";
        return java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9 ]", " ").replaceAll("\\s+", " ").trim();
    }

    private boolean namesMatch(String expected, String actual) {
        return expected.equals(actual) || expected.contains(actual) || actual.contains(expected);
    }

    private java.time.LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) return null;
        for (java.time.format.DateTimeFormatter formatter : List.of(
                java.time.format.DateTimeFormatter.ISO_LOCAL_DATE,
                java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"),
                java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy"),
                java.time.format.DateTimeFormatter.ofPattern("d/M/yyyy"))) {
            try { return java.time.LocalDate.parse(value.trim(), formatter); }
            catch (java.time.format.DateTimeParseException ignored) { }
        }
        return null;
    }

    private Double declaredSalary(Map<String, Object> simulation) {
        Object nested = simulation.get("simulation");
        if (nested instanceof Map<?, ?> fields) return toDouble(fields.get("salaireMensuelMoyen"));
        return toDouble(simulation.get("salaireMensuelMoyen"));
    }

    private Double toDouble(Object value) {
        if (value instanceof Number number) return number.doubleValue();
        if (value == null) return null;
        try {
            return Double.parseDouble(value.toString().replaceAll("[^0-9,.-]", "").replace(',', '.'));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Map<String, Object> parseObject(String json, String message) {
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (JsonProcessingException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Impossible de sauvegarder le résultat OCR.", e);
        }
    }

    private record UploadedDocument(String type, MultipartFile file, String extension, byte[] bytes) {
        private UploadedDocument(String type, MultipartFile file) {
            this(type, file, null, null);
        }
    }
}
