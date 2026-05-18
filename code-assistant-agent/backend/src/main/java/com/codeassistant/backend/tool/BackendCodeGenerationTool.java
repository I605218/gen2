package com.codeassistant.backend.tool;

import com.codeassistant.backend.dto.agent.AgentRequest;
import com.codeassistant.backend.model.agent.AgentTaskType;
import com.codeassistant.backend.model.agent.AgentToolResult;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class BackendCodeGenerationTool implements AgentTool {

    @Override
    public String name() {
        return "backend-code-generation-tool";
    }

    @Override
    public boolean supports(AgentTaskType taskType) {
        return taskType == AgentTaskType.GENERAL_CHAT || taskType == AgentTaskType.CODE_REVIEW;
    }

    @Override
    public AgentToolResult execute(AgentRequest request) {
        String question = StringUtils.hasText(request.question()) ? request.question() : "";
        String output = "后端实现建议：先定义 API 契约，再分层实现 Controller、Service、Repository 与 DTO。优先保证参数校验、异常处理、事务边界和日志可观测性。";
        return new AgentToolResult(name(), question, output);
    }
}
