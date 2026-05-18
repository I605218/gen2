package com.codeassistant.backend.service.agent;

import com.codeassistant.backend.dto.agent.AgentConversationMessageItem;
import com.codeassistant.backend.dto.agent.AgentConversationSessionItem;
import com.codeassistant.backend.dto.agent.AgentConversationTurnRequest;
import com.codeassistant.backend.dto.agent.AgentConversationThreadResponse;
import com.codeassistant.backend.dto.agent.AgentResponse;

import java.util.List;

public interface ConversationContextService {

    List<AgentConversationSessionItem> listSessions(Long userId, String guestSessionId);

    AgentConversationSessionItem createSession(Long userId, String guestSessionId, String title);

    AgentConversationSessionItem renameSession(Long userId, String guestSessionId, String sessionId, String title);

    AgentConversationSessionItem getSession(Long userId, String guestSessionId, String sessionId);

    List<AgentConversationMessageItem> listMessages(Long userId, String sessionId);

    AgentConversationThreadResponse chat(Long userId, AgentConversationTurnRequest request);

    AgentConversationThreadResponse continueChat(Long userId, AgentConversationTurnRequest request);

    boolean deleteSession(Long userId, String guestSessionId, String sessionId);

    AgentResponse buildAssistantReply(Long userId, AgentConversationTurnRequest request);
}
