package com.codeassistant.backend.tool;

import com.codeassistant.backend.dto.agent.AgentRequest;
import com.codeassistant.backend.model.agent.AgentTaskType;
import com.codeassistant.backend.model.agent.AgentToolResult;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class FrontendBugFixTool implements AgentTool {

    @Override
    public String name() {
        return "frontend-bug-fix-tool";
    }

    @Override
    public boolean supports(AgentTaskType taskType) {
        return taskType == AgentTaskType.ERROR_EXPLANATION || taskType == AgentTaskType.CODE_REVIEW;
    }

    @Override
    public AgentToolResult execute(AgentRequest request) {
        String source = StringUtils.hasText(request.errorMessage()) ? request.errorMessage() : request.question();
        String output = "前端纠错建议：重点检查模板绑定、响应式状态、事件冒泡、条件渲染、样式优先级以及异步请求后的状态更新；常见问题包括变量未定义、props 传递错误和 DOM 布局溢出。";
        return new AgentToolResult(name(), source, output);
    }
}
