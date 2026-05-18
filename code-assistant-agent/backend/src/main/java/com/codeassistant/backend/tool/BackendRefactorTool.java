package com.codeassistant.backend.tool;

import com.codeassistant.backend.dto.agent.AgentRequest;
import com.codeassistant.backend.model.agent.AgentTaskType;
import com.codeassistant.backend.model.agent.AgentToolResult;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class BackendRefactorTool implements AgentTool {

    @Override
    public String name() {
        return "backend-refactor-tool";
    }

    @Override
    public boolean supports(AgentTaskType taskType) {
        return taskType == AgentTaskType.CODE_REVIEW || taskType == AgentTaskType.GENERAL_CHAT;
    }

    @Override
    public AgentToolResult execute(AgentRequest request) {
        String source = StringUtils.hasText(request.code()) ? request.code() : request.question();
        String output = "后端重构建议：按 Controller / Service / Repository / DTO 分层抽象，提取公共校验与异常处理，统一响应结构，并将复杂流程拆成更小的领域方法。";
        return new AgentToolResult(name(), source, output);
    }
}
