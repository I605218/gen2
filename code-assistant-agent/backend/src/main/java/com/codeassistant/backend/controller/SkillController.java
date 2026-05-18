package com.codeassistant.backend.controller;

import com.codeassistant.backend.dto.agent.AgentUserSkillItem;
import com.codeassistant.backend.dto.agent.AgentUserSkillUpsertRequest;
import com.codeassistant.backend.repository.AgentUserSkillRepository;
import com.codeassistant.backend.repository.entity.AgentUserSkillEntity;
import com.codeassistant.backend.dto.auth.AuthSessionUser;
import com.codeassistant.backend.service.auth.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/skills")
public class SkillController {

    private final AuthService authService;
    private final AgentUserSkillRepository skillRepository;

    public SkillController(AuthService authService, AgentUserSkillRepository skillRepository) {
        this.authService = authService;
        this.skillRepository = skillRepository;
    }

    @GetMapping
    public List<AgentUserSkillItem> listSkills(@RequestHeader(value = "Authorization", required = false) String authorization) {
        Long userId = requireUserId(authorization);
        return skillRepository.findByUserId(userId).stream().map(this::toItem).toList();
    }

    @PostMapping
    public AgentUserSkillItem createSkill(@RequestHeader(value = "Authorization", required = false) String authorization,
                                          @Valid @RequestBody AgentUserSkillUpsertRequest request) {
        Long userId = requireUserId(authorization);
        AgentUserSkillEntity entity = new AgentUserSkillEntity();
        entity.setUserId(userId);
        entity.setName(request.name().trim());
        entity.setDescription(normalize(request.description()));
        entity.setContent(request.content().trim());
        entity.setEnabled(request.enabled() == null ? Boolean.TRUE : request.enabled());
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        return toItem(skillRepository.save(entity));
    }

    @PutMapping("/{id}")
    public AgentUserSkillItem updateSkill(@RequestHeader(value = "Authorization", required = false) String authorization,
                                          @PathVariable Long id,
                                          @Valid @RequestBody AgentUserSkillUpsertRequest request) {
        Long userId = requireUserId(authorization);
        AgentUserSkillEntity entity = skillRepository.findByUserIdAndId(userId, id)
                .orElseThrow(() -> new IllegalArgumentException("技能不存在或无权限访问"));
        entity.setName(request.name().trim());
        entity.setDescription(normalize(request.description()));
        entity.setContent(request.content().trim());
        entity.setEnabled(request.enabled() == null ? Boolean.TRUE : request.enabled());
        entity.setUpdatedAt(LocalDateTime.now());
        return toItem(skillRepository.save(entity));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteSkill(@RequestHeader(value = "Authorization", required = false) String authorization,
                                            @PathVariable Long id) {
        Long userId = requireUserId(authorization);
        int affected = skillRepository.deleteByUserIdAndId(userId, id);
        if (affected == 0) {
            throw new IllegalArgumentException("技能不存在或无权限访问");
        }
        return ResponseEntity.noContent().build();
    }

    private Long requireUserId(String authorization) {
        AuthSessionUser user = authService.resolveUser(authorization);
        if (user == null) {
            throw new IllegalArgumentException("请先登录后再管理技能");
        }
        return user.id();
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private AgentUserSkillItem toItem(AgentUserSkillEntity entity) {
        return new AgentUserSkillItem(
                entity.getId(),
                entity.getName(),
                entity.getDescription(),
                entity.getContent(),
                entity.getEnabled(),
                entity.getUpdatedAt()
        );
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleIllegalArgument(IllegalArgumentException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(exception.getMessage());
    }
}
