package com.codeassistant.backend.service.agent.impl;

import com.codeassistant.backend.dto.agent.AgentAutoRequest;
import com.codeassistant.backend.dto.agent.AgentParseResult;
import com.codeassistant.backend.dto.agent.AgentRequest;
import com.codeassistant.backend.dto.ai.AiChatResponse;
import com.codeassistant.backend.dto.ai.OpenAiMessage;
import com.codeassistant.backend.model.agent.AgentTaskType;
import com.codeassistant.backend.service.agent.AgentRequestParsingService;
import com.codeassistant.backend.service.ai.AiChatService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class AgentRequestParsingServiceImpl implements AgentRequestParsingService {

    private static final Pattern JSON_PATTERN = Pattern.compile("\\{[\\s\\S]*}");

    private final AiChatService aiChatService;

    public AgentRequestParsingServiceImpl(AiChatService aiChatService) {
        this.aiChatService = aiChatService;
    }

    @Override
    public AgentRequest parse(AgentAutoRequest request) {
        AgentParseResult parsed = parseWithModel(request);
        return new AgentRequest(
                parsed.taskType() == null ? fallbackTaskType(request.message()) : parsed.taskType(),
                StringUtils.hasText(parsed.question()) ? parsed.question() : request.message(),
                request.code(),
                normalize(parsed.language()),
                normalize(parsed.errorMessage()),
                normalize(parsed.knowledgePoint()),
                parsed.practiceCount(),
                request.enableReflexion(),
                request.selectedTools()
        );
    }

    private AgentParseResult parseWithModel(AgentAutoRequest request) {
        List<OpenAiMessage> messages = new ArrayList<>();
        messages.add(new OpenAiMessage("system", buildSystemPrompt()));
        messages.add(new OpenAiMessage("user", buildUserPrompt(request)));
        AiChatResponse response = aiChatService.chatWithMessages(messages, 0.1, 600);
        return parseJsonResult(response.content(), request.message());
    }

    private String buildSystemPrompt() {
        return "你是一个任务解析器。请把用户的自然语言请求解析为结构化 JSON，仅输出 JSON，不要输出解释。字段包括：taskType、question、language、errorMessage、knowledgePoint、practiceCount。taskType 只能是 GENERAL_CHAT、ERROR_EXPLANATION、CODE_REVIEW、ALGORITHM_GUIDE、PRACTICE_GENERATION 之一。若用户明确提到前端/页面/UI/组件/样式/React/Vue/HTML/CSS/JS/TS/表单/渲染/交互/路由/状态，优先识别为 CODE_REVIEW，并在 question 中保留用户原意。若用户明确提到后端/API/接口/Controller/Service/Repository/数据库/SQL/JPA/MyBatis/事务/登录/权限，优先识别为 CODE_REVIEW。若用户同时要求讲解算法思路、复杂度、伪代码、代码实现，即使提到了例题，也优先归类为 ALGORITHM_GUIDE。只有当用户主要目标是出题、练习、刷题时，才归类为 PRACTICE_GENERATION。无法确定的字段填 null。";
    }

    private String buildUserPrompt(AgentAutoRequest request) {
        StringBuilder builder = new StringBuilder();
        builder.append("请解析以下用户请求：\n");
        builder.append("message: ").append(request.message()).append("\n");
        if (StringUtils.hasText(request.code())) {
            builder.append("code:\n").append(request.code()).append("\n");
        }
        builder.append("\n请严格输出如下 JSON 结构：\n");
        builder.append("{\n");
        builder.append("  \"taskType\": \"ERROR_EXPLANATION\",\n");
        builder.append("  \"question\": \"...\",\n");
        builder.append("  \"language\": \"Java\",\n");
        builder.append("  \"errorMessage\": \"NullPointerException\",\n");
        builder.append("  \"knowledgePoint\": null,\n");
        builder.append("  \"practiceCount\": null\n");
        builder.append("}\n");
        return builder.toString();
    }

    private AgentParseResult parseJsonResult(String content, String fallbackQuestion) {
        Matcher matcher = JSON_PATTERN.matcher(content);
        String json = matcher.find() ? matcher.group() : content;

        AgentTaskType taskType = parseTaskType(extractString(json, "taskType"));
        String question = extractString(json, "question");
        String language = extractString(json, "language");
        String errorMessage = extractString(json, "errorMessage");
        String knowledgePoint = extractString(json, "knowledgePoint");
        Integer practiceCount = extractInteger(json, "practiceCount");

        return new AgentParseResult(
                taskType == null ? fallbackTaskType(fallbackQuestion) : taskType,
                StringUtils.hasText(question) ? question : fallbackQuestion,
                language,
                errorMessage,
                knowledgePoint,
                practiceCount
        );
    }

    private AgentTaskType fallbackTaskType(String message) {
        String text = message == null ? "" : message.toLowerCase();
        if (text.contains("报错") || text.contains("异常") || text.contains("error") || text.contains("exception")) {
            return AgentTaskType.ERROR_EXPLANATION;
        }
        if (text.contains("前端") || text.contains("页面") || text.contains("ui") || text.contains("组件") || text.contains("样式") || text.contains("react") || text.contains("vue") || text.contains("html") || text.contains("css") || text.contains("js") || text.contains("ts") || text.contains("表单") || text.contains("渲染") || text.contains("交互") || text.contains("路由") || text.contains("状态")) {
            return AgentTaskType.CODE_REVIEW;
        }
        if (text.contains("后端") || text.contains("api") || text.contains("接口") || text.contains("controller") || text.contains("service") || text.contains("repository") || text.contains("数据库") || text.contains("sql") || text.contains("jpa") || text.contains("mybatis") || text.contains("事务") || text.contains("登录") || text.contains("权限")) {
            return AgentTaskType.CODE_REVIEW;
        }
        if (text.contains("算法") || text.contains("排序") || text.contains("伪代码") || text.contains("讲解") || text.contains("思路") || text.contains("时间复杂度")) {
            return AgentTaskType.ALGORITHM_GUIDE;
        }
        if (text.contains("练习") || text.contains("题目") || text.contains("刷题") || text.contains("例题") || text.contains("学习资源")) {
            return AgentTaskType.PRACTICE_GENERATION;
        }
        if (text.contains("前端") || text.contains("页面") || text.contains("ui") || text.contains("组件") || text.contains("样式") || text.contains("react") || text.contains("vue") || text.contains("html") || text.contains("css") || text.contains("前台")) {
            return AgentTaskType.CODE_REVIEW;
        }
        if (text.contains("后端") || text.contains("接口") || text.contains("controller") || text.contains("service") || text.contains("repository") || text.contains("数据库") || text.contains("sql") || text.contains("事务") || text.contains("权限") || text.contains("登录")) {
            return AgentTaskType.CODE_REVIEW;
        }
        if (text.contains("优化") || text.contains("审查") || text.contains("review")) {
            return AgentTaskType.CODE_REVIEW;
        }
        return AgentTaskType.GENERAL_CHAT;
    }

    private AgentTaskType parseTaskType(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return AgentTaskType.valueOf(value.trim());
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private String extractString(String json, String field) {
        Pattern pattern = Pattern.compile("\\\"" + field + "\\\"\\s*:\\s*(null|\\\"(.*?)\\\")", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(json);
        if (!matcher.find()) {
            return null;
        }
        if ("null".equals(matcher.group(1))) {
            return null;
        }
        return matcher.group(2) == null ? null : matcher.group(2).trim();
    }

    private Integer extractInteger(String json, String field) {
        Pattern pattern = Pattern.compile("\\\"" + field + "\\\"\\s*:\\s*(null|\\d+)");
        Matcher matcher = pattern.matcher(json);
        if (!matcher.find()) {
            return null;
        }
        String value = matcher.group(1);
        return "null".equals(value) ? null : Integer.parseInt(value);
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value : null;
    }
}
