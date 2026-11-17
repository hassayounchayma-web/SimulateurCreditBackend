package tn.bna.simulateur.dto.dossier;

import java.util.Map;

public record DocumentReviewRequest(String statut, String motif, Map<String, Object> corrections) { }
