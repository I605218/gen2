package com.codeassistant.backend.tool;

import com.codeassistant.backend.dto.agent.AgentRequest;
import com.codeassistant.backend.model.agent.AgentTaskType;
import com.codeassistant.backend.model.agent.AgentToolResult;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class ErrorKeywordTool implements AgentTool {

    @Override
    public String name() {
        return "error-keyword-tool";
    }

    @Override
    public boolean supports(AgentTaskType taskType) {
        return taskType == AgentTaskType.ERROR_EXPLANATION || taskType == AgentTaskType.CODE_REVIEW;
    }

    @Override
    public AgentToolResult execute(AgentRequest request) {
        String source = StringUtils.hasText(request.errorMessage()) ? request.errorMessage() : request.question();
        String normalized = source == null ? "" : source.toLowerCase();

        String summary;
        if (normalized.contains("nullpointer") || normalized.contains("null pointer") || normalized.contains("空指针")) {
            summary = "检测到空指针相关错误，优先检查对象是否为 null、是否已初始化、方法返回值是否为空。";
        } else if (normalized.contains("indexoutofbounds") || normalized.contains("越界")) {
            summary = "检测到数组或列表越界问题，建议检查循环边界、下标起始值和集合长度。";
        } else if (normalized.contains("syntax") || normalized.contains("语法")) {
            summary = "检测到语法错误，优先检查括号、分号、缩进和关键字拼写。";
        } else {
            summary = "未命中特定错误模式，建议结合代码上下文、报错位置和变量状态继续分析。";
        }

        return new AgentToolResult(name(), source, summary);
    }
}
