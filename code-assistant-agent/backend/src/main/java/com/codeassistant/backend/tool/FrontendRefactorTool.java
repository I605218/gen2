package com.codeassistant.backend.tool;

import com.codeassistant.backend.dto.agent.AgentRequest;
import com.codeassistant.backend.model.agent.AgentTaskType;
import com.codeassistant.backend.model.agent.AgentToolResult;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class FrontendRefactorTool implements AgentTool {

    @Override
    public String name() {
        return "frontend-refactor-tool";
    }

    @Override
    public boolean supports(AgentTaskType taskType) {
        return taskType == AgentTaskType.CODE_REVIEW || taskType == AgentTaskType.GENERAL_CHAT;
    }

    @Override
    public AgentToolResult execute(AgentRequest request) {
        String code = StringUtils.hasText(request.code()) ? request.code() : request.question();
        String output = "前端重构建议：将 UI、状态、数据请求与工具配置分层处理；高复用区域拆成独立组件；复杂条件渲染抽成计算属性或辅助函数，以提升可维护性和可测试性。";
        return new AgentToolResult(name(), code, output);
    }
}
