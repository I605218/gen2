package com.codeassistant.backend.tool;

import com.codeassistant.backend.dto.agent.AgentRequest;
import com.codeassistant.backend.model.agent.AgentTaskType;
import com.codeassistant.backend.model.agent.AgentToolResult;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class LearningResourceTool implements AgentTool {

    @Override
    public String name() {
        return "learning-resource-tool";
    }

    @Override
    public boolean supports(AgentTaskType taskType) {
        return taskType == AgentTaskType.ALGORITHM_GUIDE
                || taskType == AgentTaskType.PRACTICE_GENERATION
                || taskType == AgentTaskType.GENERAL_CHAT;
    }

    @Override
    public AgentToolResult execute(AgentRequest request) {
        String topic = StringUtils.hasText(request.knowledgePoint()) ? request.knowledgePoint() : request.question();
        String resourceSummary = "建议外部学习路径：1) 查阅官方文档或语言教程；2) 搜索同主题的基础例题；3) 用 1-2 个最小样例验证理解；4) 记录易错点并复盘。主题：" + topic;
        return new AgentToolResult(name(), topic, resourceSummary);
    }
}
