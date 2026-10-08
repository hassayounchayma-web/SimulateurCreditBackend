package tn.bna.simulateur.agent.dto;

import lombok.Data;

@Data
public class ChatRequest {
    private String message;
    private Long userId;
    private String currentRoute; // ex: /dashboard/financement-particuliers/credit-auto
}