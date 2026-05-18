package com.codeassistant.backend.service.agent.impl;

import com.codeassistant.backend.dto.agent.AgentConversationMessageItem;
import com.codeassistant.backend.dto.agent.AgentConversationSessionItem;
import com.codeassistant.backend.dto.agent.AgentConversationThreadResponse;
import com.codeassistant.backend.dto.agent.AgentConversationTurnRequest;
import com.codeassistant.backend.dto.agent.AgentRequest;
import com.codeassistant.backend.dto.agent.AgentResponse;
import com.codeassistant.backend.model.agent.AgentTaskType;
import com.codeassistant.backend.dto.knowledge.KnowledgeSearchResponse;
import com.codeassistant.backend.dto.knowledge.KnowledgeSearchResultItem;
import com.codeassistant.backend.repository.AgentConversationHistoryRepository;
import com.codeassistant.backend.repository.AgentConversationMessageRepository;
import com.codeassistant.backend.repository.AgentConversationSessionRepository;
import com.codeassistant.backend.repository.AgentUserSkillRepository;
import com.codeassistant.backend.service.knowledge.KnowledgeService;
import com.codeassistant.backend.repository.entity.AgentConversationHistoryEntity;
import com.codeassistant.backend.repository.entity.AgentConversationMessageEntity;
import com.codeassistant.backend.repository.entity.AgentConversationSessionEntity;
import com.codeassistant.backend.service.agent.ConversationContextService;
import com.codeassistant.backend.service.agent.CodeAssistantAgentService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
public class ConversationContextServiceImpl implements ConversationContextService {

    private static final int WINDOW_SIZE = 12;
    private static final int SUMMARY_TRIGGER_SIZE = 10;

    private final AgentConversationSessionRepository sessionRepository;
    private final AgentConversationHistoryRepository historyRepository;
    private final AgentConversationMessageRepository messageRepository;
    private final AgentUserSkillRepository skillRepository;
    private final com.codeassistant.backend.service.knowledge.KnowledgeService knowledgeService;
    private final CodeAssistantAgentService agentService;

    public ConversationContextServiceImpl(AgentConversationSessionRepository sessionRepository,
                                          AgentConversationHistoryRepository historyRepository,
                                          AgentConversationMessageRepository messageRepository,
                                          AgentUserSkillRepository skillRepository,
                                          com.codeassistant.backend.service.knowledge.KnowledgeService knowledgeService,
                                          CodeAssistantAgentService agentService) {
        this.sessionRepository = sessionRepository;
        this.historyRepository = historyRepository;
        this.messageRepository = messageRepository;
        this.skillRepository = skillRepository;
        this.knowledgeService = knowledgeService;
        this.agentService = agentService;
    }

    @Override
    public List<AgentConversationSessionItem> listSessions(Long userId, String guestSessionId) {
        if (userId != null) {
            return sessionRepository.findByUserId(userId).stream()
                    .map(this::toItem)
                    .collect(java.util.stream.Collectors.toMap(
                            AgentConversationSessionItem::sessionId,
                            item -> item,
                            (left, right) -> left.updatedAt() != null && right.updatedAt() != null && right.updatedAt().isAfter(left.updatedAt()) ? right : left,
                            java.util.LinkedHashMap::new
                    ))
                    .values()
                    .stream()
                    .toList();
        }
        return sessionRepository.findGuestBySessionId(resolveSessionId(guestSessionId)).map(this::toItem).stream().toList();
    }

    @Override
    public AgentConversationSessionItem createSession(Long userId, String guestSessionId, String title) {
        String resolved = resolveSessionId(guestSessionId);
        AgentConversationSessionEntity existing = userId != null
                ? sessionRepository.findByUserIdAndSessionId(userId, resolved).orElse(null)
                : sessionRepository.findGuestBySessionId(resolved).orElse(null);
        if (existing != null) {
            if (StringUtils.hasText(title) && !title.trim().equals(existing.getTitle())) {
                existing.setTitle(title.trim());
                existing.setUpdatedAt(LocalDateTime.now());
                return toItem(sessionRepository.save(existing));
            }
            return toItem(existing);
        }

        AgentConversationSessionEntity entity = new AgentConversationSessionEntity();
        entity.setUserId(userId);
        entity.setSessionId(isSessionIdDuplicated(userId, resolved) ? generateSessionId() : resolved);
        entity.setTitle(StringUtils.hasText(title) ? title.trim() : "新对话");
        entity.setSummary("");
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        return toItem(sessionRepository.save(entity));
    }

    @Override
    public AgentConversationSessionItem renameSession(Long userId, String guestSessionId, String sessionId, String title) {
        AgentConversationSessionEntity entity = getOrCreateSession(userId, guestSessionId, sessionId);
        entity.setTitle(StringUtils.hasText(title) ? title.trim() : entity.getTitle());
        entity.setUpdatedAt(LocalDateTime.now());
        return toItem(sessionRepository.save(entity));
    }

    @Override
    public AgentConversationSessionItem getSession(Long userId, String guestSessionId, String sessionId) {
        return toItem(getOrCreateSession(userId, guestSessionId, sessionId));
    }

    @Override
    public List<AgentConversationMessageItem> listMessages(Long userId, String sessionId) {
        String resolved = resolveSessionId(sessionId);
        return messageRepository.findBySessionId(resolved).stream().map(this::toMessageItem).toList();
    }

    @Override
    public AgentConversationThreadResponse chat(Long userId, AgentConversationTurnRequest request) {
        AgentResponse response = buildAssistantReply(userId, request);
        AgentConversationSessionEntity session = getOrCreateSession(userId, request.sessionId(), request.sessionId());
        List<AgentConversationMessageItem> messages = listMessages(userId, session.getSessionId());
        AgentConversationSessionItem conversation = toItem(session);
        return new AgentConversationThreadResponse(conversation, messages, response);
    }

    @Override
    public AgentConversationThreadResponse continueChat(Long userId, AgentConversationTurnRequest request) {
        return chat(userId, request);
    }

    @Override
    public AgentResponse buildAssistantReply(Long userId, AgentConversationTurnRequest request) {
        AgentConversationSessionEntity session = getOrCreateSession(userId, request.sessionId(), request.sessionId());
        List<AgentConversationMessageEntity> history = messageRepository.findBySessionId(session.getSessionId());
        int nextTurn = Optional.ofNullable(messageRepository.findMaxTurnIndex(session.getSessionId())).orElse(0) + 1;

        persistMessage(userId, session.getSessionId(), nextTurn, "user", request.message(), request.code(), request.summary(), null);

        String prompt = buildContextPrompt(
                userId,
                session,
                history,
                request.message(),
                request.code(),
                Boolean.TRUE.equals(request.enableCrossConversationKnowledge()),
                request.crossConversationShareMode(),
                request.crossConversationShareLimit(),
                request.selectedSkillIds()
        );
        prompt = appendRagKnowledgeIfNeeded(userId, prompt, request.message());
        AgentRequest agentRequest = new AgentRequest(
                AgentTaskType.GENERAL_CHAT,
                prompt,
                request.code(),
                null,
                null,
                null,
                null,
                true,
                request.selectedTools()
        );
        AgentResponse response = agentService.execute(agentRequest);

        persistMessage(userId, session.getSessionId(), nextTurn, "assistant", response.answer(), null, response.taskType(), response);
        persistTurn(userId, session.getSessionId(), request.message(), request.code(), response);
        updateSessionSummary(session, request.summary(), response.answer());
        return response;
    }

    @Override
    public boolean deleteSession(Long userId, String guestSessionId, String sessionId) {
        String resolved = resolveSessionId(sessionId);
        if (userId != null) {
            return sessionRepository.findByUserIdAndSessionId(userId, resolved)
                    .map(session -> {
                        messageRepository.deleteBySessionId(session.getSessionId());
                        sessionRepository.deleteBySessionId(session.getSessionId());
                        return true;
                    })
                    .orElse(false);
        }
        return sessionRepository.findGuestBySessionId(resolved)
                .map(session -> {
                    messageRepository.deleteBySessionId(session.getSessionId());
                    sessionRepository.deleteBySessionId(session.getSessionId());
                    return true;
                })
                .orElse(false);
    }

    private void persistMessage(Long userId, String sessionId, int turnIndex, String role, String content, String codeContent, String taskType, AgentResponse response) {
        AgentConversationMessageEntity entity = new AgentConversationMessageEntity();
        entity.setUserId(userId);
        entity.setSessionId(sessionId);
        entity.setConversationId(null);
        entity.setRole(role);
        entity.setTurnIndex(turnIndex);
        entity.setContent(content);
        entity.setCodeContent(codeContent);
        entity.setTaskType(taskType);
        entity.setCreatedAt(LocalDateTime.now());
        messageRepository.save(entity);
    }

    private AgentConversationSessionEntity getOrCreateSession(Long userId, String guestSessionId, String sessionId) {
        String resolved = StringUtils.hasText(sessionId) ? sessionId : resolveSessionId(guestSessionId);
        if (userId != null) {
            return sessionRepository.findByUserIdAndSessionId(userId, resolved).orElseGet(() -> createSessionEntity(userId, resolved));
        }
        return sessionRepository.findGuestBySessionId(resolved).orElseGet(() -> createSessionEntity(null, resolved));
    }

    private AgentConversationSessionEntity createSessionEntity(Long userId, String sessionId) {
        AgentConversationSessionEntity entity = new AgentConversationSessionEntity();
        entity.setUserId(userId);
        entity.setSessionId(sessionId);
        entity.setTitle("新对话");
        entity.setSummary("");
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        return sessionRepository.save(entity);
    }

    private String buildContextPrompt(Long userId,
                                      AgentConversationSessionEntity session,
                                      List<AgentConversationMessageEntity> history,
                                      String message,
                                      String code,
                                      boolean enableCrossConversationKnowledge,
                                      String crossConversationShareMode,
                                      Integer crossConversationShareLimit,
                                      List<Long> selectedSkillIds) {
        List<AgentConversationMessageEntity> recent = history.stream()
                .sorted(Comparator.comparing(AgentConversationMessageEntity::getTurnIndex).thenComparing(AgentConversationMessageEntity::getCreatedAt))
                .skip(Math.max(0, history.size() - WINDOW_SIZE))
                .toList();
        StringBuilder builder = new StringBuilder();
        if (StringUtils.hasText(session.getSummary())) {
            builder.append("【会话摘要】\n").append(session.getSummary()).append("\n\n");
        }
        builder.append("【最近消息】\n");
        recent.forEach(item -> builder.append(item.getRole()).append(": ").append(item.getContent()).append("\n\n"));
        if (enableCrossConversationKnowledge) {
            appendCrossConversationKnowledge(builder, userId, session.getSessionId(), message, crossConversationShareMode, crossConversationShareLimit);
        }
        appendSelectedSkills(builder, userId, selectedSkillIds);
        builder.append("【当前输入】\n").append(message).append("\n");
        if (StringUtils.hasText(code)) {
            builder.append("\n【代码】\n").append(code).append("\n");
        }
        return builder.toString();
    }

    private void appendCrossConversationKnowledge(StringBuilder builder,
                                                  Long userId,
                                                  String currentSessionId,
                                                  String currentMessage,
                                                  String crossConversationShareMode,
                                                  Integer crossConversationShareLimit) {
        if (userId == null) {
            return;
        }
        int limit = Math.max(1, Math.min(Optional.ofNullable(crossConversationShareLimit).orElse(3), 8));
        String mode = StringUtils.hasText(crossConversationShareMode) ? crossConversationShareMode.trim().toUpperCase() : "BALANCED";

        List<AgentConversationSessionEntity> sessions = sessionRepository.findByUserId(userId).stream()
                .filter(item -> !item.getSessionId().equals(currentSessionId))
                .filter(item -> StringUtils.hasText(item.getSummary()))
                .map(item -> new ScoredConversation(item, scoreConversation(item, currentMessage, mode)))
                .sorted(Comparator.comparingDouble(ScoredConversation::score).reversed())
                .limit(limit)
                .map(ScoredConversation::session)
                .toList();
        if (sessions.isEmpty()) {
            return;
        }

        builder.append("【跨对话知识共享（仅供参考，冲突时以当前会话事实为准）】\n");
        builder.append("- 共享模式：").append(mode).append("\n");
        for (AgentConversationSessionEntity item : sessions) {
            builder.append("- ")
                    .append(StringUtils.hasText(item.getTitle()) ? item.getTitle() : item.getSessionId())
                    .append("（最近更新：")
                    .append(item.getUpdatedAt() == null ? "未知" : item.getUpdatedAt().toLocalDate())
                    .append("）: ")
                    .append(item.getSummary(), 0, Math.min(180, item.getSummary().length()))
                    .append("\n");
        }
        builder.append("\n");
    }

    private double scoreConversation(AgentConversationSessionEntity item, String currentMessage, String mode) {
        double recencyScore = recencyScore(item.getUpdatedAt());
        double lexicalScore = lexicalSimilarity(item.getSummary(), currentMessage);
        double summaryQuality = summaryQuality(item.getSummary());

        return switch (mode) {
            case "RECENT_FIRST" -> 0.7 * recencyScore + 0.2 * lexicalScore + 0.1 * summaryQuality;
            case "RELEVANCE_FIRST" -> 0.2 * recencyScore + 0.7 * lexicalScore + 0.1 * summaryQuality;
            default -> 0.45 * recencyScore + 0.45 * lexicalScore + 0.1 * summaryQuality;
        };
    }

    private double recencyScore(LocalDateTime updatedAt) {
        if (updatedAt == null) return 0.0;
        long hours = Math.max(1, Duration.between(updatedAt, LocalDateTime.now()).toHours());
        return 1.0 / (1.0 + (hours / 24.0));
    }

    private double lexicalSimilarity(String summary, String currentMessage) {
        if (!StringUtils.hasText(summary) || !StringUtils.hasText(currentMessage)) return 0.0;
        String left = summary.toLowerCase();
        String right = currentMessage.toLowerCase();
        List<String> tokens = new ArrayList<>();
        for (String token : right.split("[\\s,，。.!！?？;；:：()（）\\[\\]{}]+")) {
            if (token.length() >= 2) tokens.add(token);
        }
        if (tokens.isEmpty()) return 0.0;
        long hits = tokens.stream().filter(left::contains).count();
        return Math.min(1.0, hits * 1.0 / tokens.size());
    }

    private double summaryQuality(String summary) {
        if (!StringUtils.hasText(summary)) return 0.0;
        int len = summary.trim().length();
        if (len < 20) return 0.2;
        if (len < 60) return 0.5;
        if (len < 160) return 0.8;
        return 1.0;
    }

    private void appendSelectedSkills(StringBuilder builder, Long userId, List<Long> selectedSkillIds) {
        if (userId == null || selectedSkillIds == null || selectedSkillIds.isEmpty()) {
            return;
        }
        List<String> lines = selectedSkillIds.stream()
                .distinct()
                .limit(8)
                .map(id -> skillRepository.findByUserIdAndId(userId, id).orElse(null))
                .filter(item -> item != null && Boolean.TRUE.equals(item.getEnabled()) && StringUtils.hasText(item.getContent()))
                .map(item -> "- " + item.getName() + "：" + item.getContent().substring(0, Math.min(220, item.getContent().length())))
                .toList();
        if (lines.isEmpty()) {
            return;
        }
        builder.append("【用户技能（仅作约束和偏好参考，不覆盖当前问题事实）】\n");
        lines.forEach(line -> builder.append(line).append("\n"));
        builder.append("\n");
    }

    private String appendRagKnowledgeIfNeeded(Long userId, String prompt, String message) {
        if (userId == null || !StringUtils.hasText(message)) {
            return prompt;
        }
        KnowledgeSearchResponse response = knowledgeService.search(userId, message, 4);
        if (response.results() == null || response.results().isEmpty()) {
            return prompt;
        }
        StringBuilder builder = new StringBuilder(prompt);
        builder.append("\n【外部知识库参考（请优先参考与当前问题最相关的片段，并在回答中保留出处编号）】\n");
        int index = 1;
        for (KnowledgeSearchResultItem item : response.results()) {
            builder.append("[").append(index++).append("] ")
                    .append(StringUtils.hasText(item.title()) ? item.title() : "未命名文档")
                    .append(" - ")
                    .append(StringUtils.hasText(item.content()) ? item.content().substring(0, Math.min(260, item.content().length())) : "")
                    .append("\n");
        }
        return builder.toString();
    }

    private record ScoredConversation(AgentConversationSessionEntity session, double score) {}

    private void persistTurn(Long userId, String sessionId, String message, String code, AgentResponse response) {
        AgentConversationHistoryEntity entity = new AgentConversationHistoryEntity();
        entity.setUserId(userId);
        entity.setSessionId(sessionId);
        entity.setRequestType("multi-turn-chat");
        entity.setTaskType(response.taskType());
        entity.setUserPrompt(message);
        entity.setCodeContent(code);
        entity.setResponseSummary(response.answer() == null ? "" : response.answer().substring(0, Math.min(80, response.answer().length())));
        entity.setAnswerContent(response.answer());
        entity.setResponsePayload(null);
        entity.setPinned(false);
        entity.setCreatedAt(LocalDateTime.now());
        historyRepository.save(entity);
    }

    private void updateSessionSummary(AgentConversationSessionEntity session, String incomingSummary, String latestAnswer) {
        StringBuilder summary = new StringBuilder();
        if (StringUtils.hasText(session.getSummary())) {
            summary.append(session.getSummary()).append("\n");
        }
        if (StringUtils.hasText(incomingSummary)) {
            summary.append(incomingSummary).append("\n");
        }
        if (StringUtils.hasText(latestAnswer)) {
            summary.append("最新回复：").append(latestAnswer.substring(0, Math.min(120, latestAnswer.length())));
        }
        session.setSummary(summary.toString().trim());
        session.setUpdatedAt(LocalDateTime.now());
        if (historyRepository.countBySessionId(session.getSessionId()) >= SUMMARY_TRIGGER_SIZE) {
            session.setSummary(compactSummary(session.getSummary(), latestAnswer));
        }
        sessionRepository.save(session);
    }

    private String compactSummary(String summary, String latestAnswer) {
        String base = StringUtils.hasText(summary) ? summary : "";
        String tail = StringUtils.hasText(latestAnswer) ? latestAnswer.substring(0, Math.min(100, latestAnswer.length())) : "";
        return (base + "\n[压缩摘要] " + tail).trim();
    }

    private AgentConversationSessionItem toItem(AgentConversationSessionEntity entity) {
        long messageCount = messageRepository.findBySessionId(entity.getSessionId()).size();
        AgentConversationHistoryEntity latest = historyRepository.findLatestBySessionId(entity.getSessionId()).orElse(null);
        return new AgentConversationSessionItem(
                entity.getId(),
                entity.getSessionId(),
                entity.getTitle(),
                entity.getSummary(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                messageCount,
                latest != null ? latest.getTaskType() : null,
                latest != null ? latest.getUserPrompt() : null,
                latest != null ? latest.getAnswerContent() : null,
                historyRepository.existsPinnedBySessionId(entity.getSessionId())
        );
    }

    private AgentConversationMessageItem toMessageItem(AgentConversationMessageEntity entity) {
        return new AgentConversationMessageItem(
                entity.getId(),
                entity.getConversationId(),
                entity.getSessionId(),
                entity.getRole(),
                entity.getTurnIndex(),
                entity.getContent(),
                entity.getCodeContent(),
                entity.getTaskType(),
                entity.getCreatedAt()
        );
    }

    private boolean isSessionIdDuplicated(Long userId, String sessionId) {
        if (userId != null) {
            return sessionRepository.findByUserIdAndSessionId(userId, sessionId).isPresent();
        }
        return sessionRepository.findGuestBySessionId(sessionId).isPresent();
    }

    private String generateSessionId() {
        return "session-" + java.util.UUID.randomUUID();
    }

    private String resolveSessionId(String sessionId) {
        return StringUtils.hasText(sessionId) ? sessionId : "guest-session";
    }
}
