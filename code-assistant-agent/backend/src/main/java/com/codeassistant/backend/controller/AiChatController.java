package com.codeassistant.backend.controller;

import com.codeassistant.backend.dto.ai.AiChatRequest;
import com.codeassistant.backend.dto.ai.AiChatResponse;
import com.codeassistant.backend.dto.ai.AiConnectionTestResponse;
import com.codeassistant.backend.service.ai.AiChatService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai")
public class AiChatController {

    private final AiChatService aiChatService;

    public AiChatController(AiChatService aiChatService) {
        this.aiChatService = aiChatService;
    }

    @GetMapping("/test")
    public AiConnectionTestResponse testConnection() {
        return aiChatService.testConnection();
    }

    @PostMapping("/chat")
    public AiChatResponse chat(@Valid @RequestBody AiChatRequest request) {
        return aiChatService.chat(request.message(), request.systemPrompt());
    }
}
