package tn.bna.simulateur.agent.dto;

import lombok.Data;

@Data
public class ChatResponse {
    private String message;
    private String suggestedAction;  // "NAVIGATE", "SHOW_CONTEXT", "SHOW_HELP", "LISTEN"
    private String actionData;      // URL de redirection
    private SimulateurInfo simulateurInfo;
}