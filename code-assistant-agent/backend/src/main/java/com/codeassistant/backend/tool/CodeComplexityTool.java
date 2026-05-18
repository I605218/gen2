package com.codeassistant.backend.tool;

import com.codeassistant.backend.dto.agent.AgentRequest;
import com.codeassistant.backend.model.agent.AgentTaskType;
import com.codeassistant.backend.model.agent.AgentToolResult;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class CodeComplexityTool implements AgentTool {

    @Override
    public String name() {
        return "code-complexity-tool";
    }

    @Override
    public boolean supports(AgentTaskType taskType) {
        return taskType == AgentTaskType.CODE_REVIEW || taskType == AgentTaskType.ALGORITHM_GUIDE;
    }

    @Override
    public AgentToolResult execute(AgentRequest request) {
        String code = StringUtils.hasText(request.code()) ? request.code() : "";
        long lineCount = code.lines().count();
        long loopCount = code.lines()
                .map(String::trim)
                .filter(line -> line.startsWith("for") || line.startsWith("while"))
                .count();
        String summary = "代码共约 " + lineCount + " 行，检测到循环语句 " + loopCount + " 处。若存在多层循环，需关注时间复杂度和可读性。";
        return new AgentToolResult(name(), code, summary);
    }
}
