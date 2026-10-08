package tn.bna.simulateur.services;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class PythonSimulatorService {

    @Value("${python.executable:python}")
    private String pythonExecutable;

    @Value("${python.script-path:C:/pfe projects/simulateurs_credit.py}")
    private String scriptPath;

    private final ObjectMapper mapper = new ObjectMapper();

    public Map<String, Object> runSimulator(String simulatorType, Map<String, Object> params) {
        try {
            // Construire la commande
            List<String> command = new ArrayList<>();
            command.add(pythonExecutable);
            command.add(scriptPath);
            command.add(simulatorType);

            // Ajouter les paramètres sous forme clé=valeur
            params.forEach((key, value) -> {
                if (value != null) {
                    command.add(key + "=" + value);
                }
            });

            log.info("Exécution du script Python : {}", command);

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);

            Process process = pb.start();

            // Lecture sortie standard (stdout)
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }

            // Lecture erreurs (stderr)
            BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
            StringBuilder errorOutput = new StringBuilder();
            while ((line = errorReader.readLine()) != null) {
                errorOutput.append(line).append("\n");
            }

            int exitCode = process.waitFor();

            if (exitCode != 0) {
                log.error("Script Python terminé avec code d'erreur {} - Message : {}", exitCode, errorOutput.toString().trim());
                return Map.of("error", "Échec du script Python (code " + exitCode + ") : " + errorOutput.toString().trim());
            }

            String jsonOutput = output.toString().trim();
            if (jsonOutput.isEmpty()) {
                log.warn("Sortie Python vide pour simulateur {}", simulatorType);
                return Map.of("error", "Aucune réponse du script Python");
            }

            log.debug("Sortie brute Python : {}", jsonOutput);

            // Parser le JSON
            Map<String, Object> rawResult = mapper.readValue(jsonOutput, new TypeReference<Map<String, Object>>() {});

            // ───────────────────────────────────────────────
            // NORMALISATION DES NOMS DE CHAMPS POUR LE FRONTEND
            // ───────────────────────────────────────────────
            Map<String, Object> normalizedResult = new HashMap<>(rawResult);

            // Pour RACHAT EXTERNE : renommer taux_retenues_final → tauxEndettementCalcule
            if ("rachat-externe".equals(simulatorType)) {
                if (rawResult.containsKey("taux_retenues_final")) {
                    normalizedResult.put("tauxEndettementCalcule", rawResult.get("taux_retenues_final"));
                }
                if (rawResult.containsKey("montant_max_credit")) {
                    normalizedResult.put("montantMaxCredit", rawResult.get("montant_max_credit"));
                }
            }

            // Pour RACHAT INTERNE : renommer taux_endettement_apres → tauxEndettementCalcule
            if ("rachat-interne".equals(simulatorType)) {
                if (rawResult.containsKey("taux_endettement_apres")) {
                    normalizedResult.put("tauxEndettementCalcule", rawResult.get("taux_endettement_apres"));
                }
                if (rawResult.containsKey("montant_nouveau_credit")) {
                    normalizedResult.put("montantMaxCredit", rawResult.get("montant_nouveau_credit"));
                }
            }

            // Pour tous les simulateurs : s'assurer que les champs attendus existent (fallback 0 si absent)
            normalizedResult.putIfAbsent("tauxEndettementCalcule", 0.0);
            normalizedResult.putIfAbsent("montantMaxCredit", 0.0);

            log.info("Résultat normalisé envoyé au frontend : {}", normalizedResult);

            return normalizedResult;

        } catch (Exception e) {
            log.error("Erreur lors de l'exécution ou du parsing du script Python", e);
            return Map.of("error", "Erreur serveur lors de la simulation : " + e.getMessage());
        }
    }
}