package com.codeassistant.backend.tool;

import com.codeassistant.backend.dto.agent.AgentRequest;
import com.codeassistant.backend.model.agent.AgentTaskType;
import com.codeassistant.backend.model.agent.AgentToolResult;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class FrontendCodeGenerationTool implements AgentTool {

    @Override
    public String name() {
        return "frontend-code-generation-tool";
    }

    @Override
    public boolean supports(AgentTaskType taskType) {
        return taskType == AgentTaskType.GENERAL_CHAT || taskType == AgentTaskType.CODE_REVIEW;
    }

    @Override
    public AgentToolResult execute(AgentRequest request) {
        String question = StringUtils.hasText(request.question()) ? request.question() : "";
        String output = "前端实现建议：先定义页面结构、组件职责和状态管理，再补充样式与交互。可优先使用 Vue 组合式 API / React Hooks 组织逻辑，并拆分出输入区、结果区、提示区等模块。";
        return new AgentToolResult(name(), question, output);
    }
}
