package com.codeassistant.backend.tool;

import com.codeassistant.backend.dto.agent.AgentRequest;
import com.codeassistant.backend.model.agent.AgentTaskType;
import com.codeassistant.backend.model.agent.AgentToolResult;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class BackendBugFixTool implements AgentTool {

    @Override
    public String name() {
        return "backend-bug-fix-tool";
    }

    @Override
    public boolean supports(AgentTaskType taskType) {
        return taskType == AgentTaskType.ERROR_EXPLANATION || taskType == AgentTaskType.CODE_REVIEW;
    }

    @Override
    public AgentToolResult execute(AgentRequest request) {
        String source = StringUtils.hasText(request.errorMessage()) ? request.errorMessage() : request.question();
        String output = "后端纠错建议：优先排查请求参数校验、空指针、事务边界、SQL 映射、实体字段命名和权限解析逻辑；结合异常栈定位到具体服务层/仓库层代码。";
        return new AgentToolResult(name(), source, output);
    }
}
