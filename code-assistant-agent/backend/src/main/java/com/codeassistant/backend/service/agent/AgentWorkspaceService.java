package com.codeassistant.backend.service.agent;

import com.codeassistant.backend.dto.agent.AgentDashboardResponse;
import com.codeassistant.backend.dto.agent.AgentHistoryItem;
import com.codeassistant.backend.dto.agent.AgentResponse;
import com.codeassistant.backend.dto.agent.AgentSessionStateResponse;

import java.util.List;

public interface AgentWorkspaceService {

    AgentDashboardResponse getDashboard();

    List<AgentHistoryItem> getHistory(Long userId, String sessionId);

    AgentSessionStateResponse getSessionState(Long userId, String sessionId);

    void saveExecution(Long userId, String sessionId, String requestType, String prompt, String code, AgentResponse response);

    void saveFeedback(Long historyId, String feedback);

    void togglePinned(Long historyId, boolean pinned);
}
