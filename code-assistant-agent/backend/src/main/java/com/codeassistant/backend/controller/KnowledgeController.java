package com.codeassistant.backend.controller;

import com.codeassistant.backend.dto.auth.AuthSessionUser;
import com.codeassistant.backend.dto.knowledge.KnowledgeDocumentItem;
import com.codeassistant.backend.dto.knowledge.KnowledgeSearchResponse;
import com.codeassistant.backend.dto.knowledge.KnowledgeUpsertRequest;
import com.codeassistant.backend.service.auth.AuthService;
import com.codeassistant.backend.service.knowledge.KnowledgeService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PatchMapping;

import java.util.List;

@RestController
@RequestMapping("/api/knowledge")
public class KnowledgeController {

    private final AuthService authService;
    private final KnowledgeService knowledgeService;

    public KnowledgeController(AuthService authService, KnowledgeService knowledgeService) {
        this.authService = authService;
        this.knowledgeService = knowledgeService;
    }

    @GetMapping
    public List<KnowledgeDocumentItem> list(@RequestHeader(value = "Authorization", required = false) String authorizationHeader) {
        return knowledgeService.listDocuments(requireUserId(authorizationHeader));
    }

    @PostMapping
    public KnowledgeDocumentItem create(@RequestHeader(value = "Authorization", required = false) String authorizationHeader,
                                        @Valid @RequestBody KnowledgeUpsertRequest request) {
        return knowledgeService.upsertDocument(requireUserId(authorizationHeader), null, request);
    }

    @PutMapping("/{id}")
    public KnowledgeDocumentItem update(@RequestHeader(value = "Authorization", required = false) String authorizationHeader,
                                        @PathVariable Long id,
                                        @Valid @RequestBody KnowledgeUpsertRequest request) {
        return knowledgeService.upsertDocument(requireUserId(authorizationHeader), id, request);
    }

    @GetMapping("/{id}")
    public KnowledgeDocumentItem get(@RequestHeader(value = "Authorization", required = false) String authorizationHeader,
                                     @PathVariable Long id) {
        return knowledgeService.getDocument(requireUserId(authorizationHeader), id);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@RequestHeader(value = "Authorization", required = false) String authorizationHeader,
                                       @PathVariable Long id) {
        knowledgeService.deleteDocument(requireUserId(authorizationHeader), id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/import-samples")
    public List<KnowledgeDocumentItem> importSamples(@RequestHeader(value = "Authorization", required = false) String authorizationHeader) {
        return knowledgeService.importSamples(requireUserId(authorizationHeader));
    }

    @GetMapping("/search")
    public KnowledgeSearchResponse search(@RequestHeader(value = "Authorization", required = false) String authorizationHeader,
                                          @RequestParam String query,
                                          @RequestParam(defaultValue = "5") int limit) {
        return knowledgeService.search(requireUserId(authorizationHeader), query, limit);
    }

    private Long requireUserId(String authorizationHeader) {
        AuthSessionUser user = authService.resolveUser(authorizationHeader);
        if (user == null) {
            throw new IllegalArgumentException("请先登录后再使用知识库功能");
        }
        return user.id();
    }

    @org.springframework.web.bind.annotation.ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleIllegalArgument(IllegalArgumentException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(exception.getMessage());
    }
}
