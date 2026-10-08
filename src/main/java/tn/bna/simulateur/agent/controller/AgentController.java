package tn.bna.simulateur.agent.controller;

import tn.bna.simulateur.agent.dto.ChatRequest;
import tn.bna.simulateur.agent.dto.ChatResponse;
import tn.bna.simulateur.agent.service.AgentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/agent")
@CrossOrigin(origins = "http://localhost:4300")
@RequiredArgsConstructor
public class AgentController {

    private final AgentService agentService;

    @PostMapping("/chat")
    public ResponseEntity<ChatResponse> chat(@RequestBody ChatRequest request) {
        ChatResponse response = agentService.processMessage(request);
        return ResponseEntity.ok(response);
    }
    @DeleteMapping("/session/{userId}")
    public ResponseEntity<Void> clearSession(@PathVariable Long userId) {
        agentService.clearSession(userId);
        return ResponseEntity.noContent().build();
    }
}