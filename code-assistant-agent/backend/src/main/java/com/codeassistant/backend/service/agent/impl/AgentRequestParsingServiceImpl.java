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
    public AgentRequest parse(AgentAutoRequest request, Long userId) {
        AgentTaskType taskType = fallbackTaskType(request.message());
        return new AgentRequest(
                taskType,
                request.message(),
                request.code(),
                null,
                null,
                null,
                null,
                request.enableReflexion(),
                request.selectedTools(),
                userId
        );
    }

    private AgentParseResult parseWithModel(AgentAutoRequest request) {
        List<OpenAiMessage> messages = new ArrayList<>();
        messages.add(new OpenAiMessage("system", buildSystemPrompt()));
        messages.add(new OpenAiMessage("user", buildUserPrompt(request)));
        AiChatResponse response = aiChatService.chatWithMessages(messages, 0.1, 300);
        return parseJsonResult(response.content(), request.message());
    }

    private String buildSystemPrompt() {
        return "你是一个任务解析器。请把用户的自然语言请求解析为结构化 JSON，仅输出 JSON，不要输出解释。"
                + "字段包括：taskType、question、language、errorMessage、knowledgePoint、practiceCount。"
                + "taskType 只能是 GENERAL_CHAT、ERROR_EXPLANATION、CODE_REVIEW、ALGORITHM_GUIDE、PRACTICE_GENERATION 之一。"
                + "分类规则（按优先级排序）："
                + "1. 若用户描述报错、异常、error、exception、崩溃、不知道怎么修复等，归类为 ERROR_EXPLANATION；"
                + "2. 若用户要求审查代码、分析代码质量/安全/潜在问题/重构，或消息中包含代码块（```），归类为 CODE_REVIEW；"
                + "3. 若用户要求讲解算法思路、复杂度、伪代码、数据结构（排序/链表/树/图/动态规划/BFS/DFS/KMP/LRU等），归类为 ALGORITHM_GUIDE；"
                + "4. 若用户主要目标是生成练习题、出题、刷题，归类为 PRACTICE_GENERATION；"
                + "5. 其余情况归类为 GENERAL_CHAT。"
                + "无法确定的字段填 null。";
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

        // 错误诊断：报错/异常类关键词
        if (text.contains("报错") || text.contains("异常") || text.contains("error") || text.contains("exception")
                || text.contains("nullpointer") || text.contains("出错") || text.contains("崩溃") || text.contains("失败")
                || text.contains("问题") && (text.contains("修复") || text.contains("排查") || text.contains("原因"))) {
            return AgentTaskType.ERROR_EXPLANATION;
        }

        // 代码审查：审查/review/安全性相关，或包含代码片段标记
        if (text.contains("审查") || text.contains("review") || text.contains("代码质量") || text.contains("潜在问题")
                || text.contains("安全") || text.contains("重构") || text.contains("优化")
                || text.contains("```") || text.contains("以下代码") || text.contains("这段代码")) {
            return AgentTaskType.CODE_REVIEW;
        }

        // 算法指导：涉及算法讲解、复杂度、伪代码、数据结构等
        if (text.contains("算法") || text.contains("排序") || text.contains("伪代码") || text.contains("讲解")
                || text.contains("思路") || text.contains("时间复杂度") || text.contains("空间复杂度")
                || text.contains("动态规划") || text.contains("二分") || text.contains("bfs") || text.contains("dfs")
                || text.contains("链表") || text.contains("树") || text.contains("图") || text.contains("哈希")
                || text.contains("滑动窗口") || text.contains("双指针") || text.contains("递归") || text.contains("分治")
                || text.contains("kmp") || text.contains("lru") || text.contains("dijkstra") || text.contains("贪心")
                || text.contains("搜索") || text.contains("遍历")) {
            return AgentTaskType.ALGORITHM_GUIDE;
        }

        // 练习题生成：主要目标是出题
        if (text.contains("生成") && (text.contains("练习题") || text.contains("题目") || text.contains("习题"))
                || text.contains("出题") || text.contains("刷题")
                || text.contains("练习题") || text.contains("给出") && text.contains("题")) {
            return AgentTaskType.PRACTICE_GENERATION;
        }

        // 前后端技术关键词兜底到代码审查
        if (text.contains("前端") || text.contains("页面") || text.contains("ui") || text.contains("组件")
                || text.contains("样式") || text.contains("react") || text.contains("vue") || text.contains("html")
                || text.contains("css") || text.contains("表单") || text.contains("渲染") || text.contains("交互")) {
            return AgentTaskType.CODE_REVIEW;
        }
        if (text.contains("后端") || text.contains("api") || text.contains("接口") || text.contains("controller")
                || text.contains("service") || text.contains("repository") || text.contains("数据库")
                || text.contains("sql") || text.contains("jpa") || text.contains("mybatis")
                || text.contains("事务") || text.contains("登录") || text.contains("权限")) {
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
