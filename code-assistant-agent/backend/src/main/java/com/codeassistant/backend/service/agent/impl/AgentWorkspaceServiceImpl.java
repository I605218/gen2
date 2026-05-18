package com.codeassistant.backend.service.agent.impl;

import com.codeassistant.backend.dto.agent.AgentDashboardResponse;
import com.codeassistant.backend.dto.agent.AgentExecutionMeta;
import com.codeassistant.backend.dto.agent.AgentHistoryItem;
import com.codeassistant.backend.dto.agent.AgentResponse;
import com.codeassistant.backend.dto.agent.AgentSessionStateResponse;
import com.codeassistant.backend.dto.agent.AgentStats;
import com.codeassistant.backend.repository.AgentConversationHistoryRepository;
import com.codeassistant.backend.repository.entity.AgentConversationHistoryEntity;
import com.codeassistant.backend.service.agent.AgentWorkspaceService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class AgentWorkspaceServiceImpl implements AgentWorkspaceService {

    private static final String GUEST_SESSION = "guest-session";
    private static final List<String> QUICK_PROMPTS = List.of(
            "这段 Java 代码为什么会空指针？",
            "讲讲快速排序的思想、复杂度和 Java 实现",
            "请围绕哈希表给我生成 3 道基础到进阶练习题",
            "帮我 review 这段代码，指出复杂度和可读性问题"
    );

    private final AgentConversationHistoryRepository repository;
    private final ObjectMapper objectMapper;

    public AgentWorkspaceServiceImpl(AgentConversationHistoryRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Override
    public AgentDashboardResponse getDashboard() {
        List<AgentHistoryItem> history = repository.findRecent(8).stream().map(this::toItem).toList();
        AgentStats stats = new AgentStats(
                Math.max(repository.countDistinctSessions(), repository.countDistinctUsers()),
                repository.countAll(),
                repository.countByTaskType("ERROR_EXPLANATION"),
                repository.countByTaskType("ALGORITHM_GUIDE"),
                repository.countByTaskType("PRACTICE_GENERATION"),
                repository.findHotTaskTypes()
        );
        return new AgentDashboardResponse(history, stats, QUICK_PROMPTS);
    }

    @Override
    public List<AgentHistoryItem> getHistory(Long userId, String sessionId) {
        if (userId != null) {
            return repository.findByUserId(userId, 20).stream().map(this::toItem).toList();
        }
        return repository.findGuestBySessionId(resolveSessionId(sessionId), 20).stream().map(this::toItem).toList();
    }

    @Override
    public AgentSessionStateResponse getSessionState(Long userId, String sessionId) {
        if (userId != null) {
            Optional<AgentConversationHistoryEntity> latest = repository.findLatestByUserId(userId);
            return new AgentSessionStateResponse(
                    "user-" + userId,
                    repository.countByUserId(userId),
                    latest.map(AgentConversationHistoryEntity::getTaskType).orElse(null),
                    latest.map(AgentConversationHistoryEntity::getUserPrompt).orElse(null),
                    latest.map(AgentConversationHistoryEntity::getAnswerContent).orElse(null),
                    repository.existsPinnedByUserId(userId)
            );
        }

        String resolvedSessionId = resolveSessionId(sessionId);
        Optional<AgentConversationHistoryEntity> latest = repository.findLatestGuestBySessionId(resolvedSessionId);
        return new AgentSessionStateResponse(
                resolvedSessionId,
                repository.countGuestBySessionId(resolvedSessionId),
                latest.map(AgentConversationHistoryEntity::getTaskType).orElse(null),
                latest.map(AgentConversationHistoryEntity::getUserPrompt).orElse(null),
                latest.map(AgentConversationHistoryEntity::getAnswerContent).orElse(null),
                repository.existsPinnedGuestBySessionId(resolvedSessionId)
        );
    }

    @Override
    public void saveExecution(Long userId, String sessionId, String requestType, String prompt, String code, AgentResponse response) {
        AgentConversationHistoryEntity entity = new AgentConversationHistoryEntity();
        entity.setUserId(userId);
        entity.setSessionId(resolveSessionId(sessionId));
        entity.setRequestType(requestType);
        entity.setTaskType(response.taskType());
        entity.setUserPrompt(prompt);
        entity.setCodeContent(code);
        entity.setResponseSummary(buildSummary(response));
        entity.setAnswerContent(response.answer());
        entity.setResponsePayload(serializeResponse(response));
        entity.setUserFeedback(null);
        entity.setPinned(false);
        entity.setCreatedAt(LocalDateTime.now());
        repository.save(entity);
    }

    @Override
    public void saveFeedback(Long historyId, String feedback) {
        repository.updateFeedback(historyId, StringUtils.hasText(feedback) ? feedback.trim() : null);
    }

    @Override
    public void togglePinned(Long historyId, boolean pinned) {
        repository.updatePinned(historyId, pinned);
    }

    private AgentHistoryItem toItem(AgentConversationHistoryEntity entity) {
        return new AgentHistoryItem(
                entity.getId(),
                entity.getSessionId(),
                entity.getRequestType(),
                entity.getTaskType(),
                entity.getUserPrompt(),
                entity.getCodeContent(),
                entity.getResponseSummary(),
                entity.getAnswerContent(),
                deserializeResponse(entity.getResponsePayload()),
                entity.getUserFeedback(),
                entity.isPinned(),
                entity.getCreatedAt()
        );
    }

    private String buildSummary(AgentResponse response) {
        AgentExecutionMeta execution = response.execution();
        return response.taskType() + " · 计划 " + execution.planStepCount() + " 步 · 工具 " + execution.toolCallCount() + " 次";
    }

    private String serializeResponse(AgentResponse response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException exception) {
            return null;
        }
    }

    private AgentResponse deserializeResponse(String payload) {
        if (!StringUtils.hasText(payload)) {
            return null;
        }
        try {
            return objectMapper.readValue(payload, AgentResponse.class);
        } catch (JsonProcessingException exception) {
            return null;
        }
    }

    private String resolveSessionId(String sessionId) {
        return StringUtils.hasText(sessionId) ? sessionId : GUEST_SESSION;
    }
}
