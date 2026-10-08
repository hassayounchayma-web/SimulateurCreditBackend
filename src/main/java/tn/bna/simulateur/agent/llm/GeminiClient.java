package tn.bna.simulateur.agent.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import tn.bna.simulateur.agent.dto.LlmExtraction;

import java.util.*;

@Component
@Slf4j
public class GeminiClient {

    @Value("${agent.llm.enabled:false}")
    private boolean enabled;

    @Value("${agent.gemini.api-key:}")
    private String apiKey;

    @Value("${agent.gemini.model:gemini-2.0-flash}")
    private String model;

    @Value("${agent.gemini.url:https://generativelanguage.googleapis.com/v1beta/models}")
    private String baseUrl;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper mapper = new ObjectMapper();

    private static final String SYSTEM_PROMPT = """
        Tu es l'assistant crédit de la Banque Nationale Agricole (BNA - Tunisie).
        Tu aides à orienter vers le bon simulateur et à extraire les données numériques.

        RÈGLES STRICTES :
        1. N'invente JAMAIS de taux, plafonds ou mensualités.
        2. Extrais uniquement les champs présents dans le message.
        3. Réponds UNIQUEMENT en JSON valide, sans markdown.

        Simulateurs possibles (simulateurKey) :
        credit-h24, credit-perso, credit-auto, credit-immo, twensa, premier-logement,
        creation-projets, extension-developpement, besoins-exploitation, caution-bna,
        credits-campagne, credits-investissement, credits-supervises, chaines-valeur, credit-jinene,
        pro-tresorerie, pro-stocks, pro-marches-tn, pro-export, pro-investissement, pro-cautions,
        sans-rachat, rachat-interne, rachat-externe

        Format JSON exact :
        {
          "intent": "SIMULATE|INFO|COMPARE|HELP|DEFAULT",
          "simulateurKey": "credit-auto ou null",
          "salaire": null,
          "retenues": null,
          "montant": null,
          "dureeMois": null,
          "typeProjet": "auto|immobilier|agricole|entreprise|rachat|null",
          "messageUtilisateur": "phrase courte d'aide en français"
        }
        """;

    public boolean isAvailable() {
        return enabled && apiKey != null && !apiKey.isBlank();
    }

    public LlmExtraction extract(String userMessage) {
        if (!isAvailable()) {
            return null;
        }

        try {
            String url = baseUrl + "/" + model + ":generateContent?key=" + apiKey;

            Map<String, Object> body = new HashMap<>();
            body.put("contents", List.of(
                    Map.of("role", "user", "parts", List.of(
                            Map.of("text", SYSTEM_PROMPT + "\n\nMessage utilisateur :\n" + userMessage)
                    ))
            ));
            body.put("generationConfig", Map.of(
                    "temperature", 0.2,
                    "responseMimeType", "application/json"
            ));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    String.class
            );

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                log.warn("Gemini HTTP {}", response.getStatusCode());
                return null;
            }

            JsonNode root = mapper.readTree(response.getBody());
            String text = root.path("candidates").path(0)
                    .path("content").path("parts").path(0)
                    .path("text").asText(null);

            if (text == null || text.isBlank()) {
                return null;
            }

            // Nettoyage si le modèle renvoie des fences ```json
            text = text.replaceAll("(?s)```json\\s*", "")
                    .replaceAll("(?s)```\\s*", "")
                    .trim();

            return mapper.readValue(text, LlmExtraction.class);

        } catch (Exception e) {
            log.error("Erreur Gemini: {}", e.getMessage());
            return null;
        }
    }
}