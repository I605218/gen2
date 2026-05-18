package com.codeassistant.backend.service.ai.impl;

import com.codeassistant.backend.config.AiApiProperties;
import com.codeassistant.backend.dto.ai.AiChatResponse;
import com.codeassistant.backend.dto.ai.AiConnectionTestResponse;
import com.codeassistant.backend.dto.ai.AiMessagesChatRequest;
import com.codeassistant.backend.dto.ai.OpenAiChatCompletionResponse;
import com.codeassistant.backend.dto.ai.OpenAiMessage;
import com.codeassistant.backend.exception.AiBusyException;
import com.codeassistant.backend.exception.AiClientException;
import com.codeassistant.backend.service.ai.AiChatService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;

@Service
public class OpenAiCompatibleChatService implements AiChatService {

    private static final String DEFAULT_SYSTEM_PROMPT = "你是一个面向初学者的编程学习与代码助手，请用清晰、准确、循序渐进的方式回答问题。";
    private static final String TEST_PROMPT = "请仅回复：连接成功";
    private static final String BUSY_MESSAGE = "当前 AI 请求较多，请稍后再试";
    private static final int MAX_CONTINUE_ROUNDS = 2;

    private final RestClient restClient;
    private final AiApiProperties properties;
    private final Semaphore concurrencyLimiter;

    public OpenAiCompatibleChatService(RestClient aiRestClient, AiApiProperties properties) {
        this.restClient = aiRestClient;
        this.properties = properties;
        this.concurrencyLimiter = new Semaphore(Math.max(1, properties.getMaxConcurrentRequests()));
    }

    @Override
    public AiChatResponse chat(String message, String systemPrompt) {
        List<OpenAiMessage> messages = new ArrayList<>();
        messages.add(new OpenAiMessage("system", StringUtils.hasText(systemPrompt) ? systemPrompt : DEFAULT_SYSTEM_PROMPT));
        messages.add(new OpenAiMessage("user", message));
        return chatWithMessages(messages, properties.getTemperature(), properties.getMaxTokens());
    }

    @Override
    public AiChatResponse chatWithMessages(List<OpenAiMessage> messages, Double temperature, Integer maxTokens) {
        OpenAiChatCompletionResponse response = sendChatCompletion(messages, temperature, maxTokens);
        String content = extractContent(response);
        String model = response.model();
        int continueRounds = 0;
        List<OpenAiMessage> contextMessages = new ArrayList<>(messages);

        while (continueRounds < MAX_CONTINUE_ROUNDS && isPossiblyTruncated(response, content)) {
            String continuation = continueAnswer(contextMessages, content, temperature, maxTokens);
            if (!StringUtils.hasText(continuation) || continuation.trim().equals(content.trim())) {
                break;
            }
            content = appendContinuation(content, continuation);
            contextMessages = new ArrayList<>(messages);
            contextMessages.add(new OpenAiMessage("assistant", content));
            continueRounds++;
            response = new OpenAiChatCompletionResponse(model, List.of(new OpenAiChatCompletionResponse.Choice(0, new OpenAiMessage("assistant", content), "length")));
        }

        return new AiChatResponse(model, content);
    }

    @Override
    public AiConnectionTestResponse testConnection() {
        List<OpenAiMessage> messages = List.of(
                new OpenAiMessage("system", "你是一个接口连通性测试助手。"),
                new OpenAiMessage("user", TEST_PROMPT)
        );
        OpenAiChatCompletionResponse response = sendChatCompletion(messages, properties.getTemperature(), properties.getMaxTokens());
        String content = extractContent(response);
        return new AiConnectionTestResponse(true, response.model(), "AI 服务连接成功", content);
    }

    private OpenAiChatCompletionResponse sendChatCompletion(List<OpenAiMessage> messages,
                                                            Double temperature,
                                                            Integer maxTokens) {
        if (!concurrencyLimiter.tryAcquire()) {
            throw new AiBusyException(BUSY_MESSAGE);
        }

        try {
            AiMessagesChatRequest request = new AiMessagesChatRequest(
                    properties.getModel(),
                    messages,
                    temperature,
                    maxTokens
            );

            OpenAiChatCompletionResponse response = restClient.post()
                    .uri("/chat/completions")
                    .body(request)
                    .retrieve()
                    .body(OpenAiChatCompletionResponse.class);

            if (response == null) {
                throw new AiClientException("AI 接口未返回有效响应");
            }

            return response;
        } catch (RestClientException exception) {
            throw new AiClientException("调用 AI 接口失败，请检查 base URL、API Key、模型名称或网络连接", exception);
        } finally {
            concurrencyLimiter.release();
        }
    }

    private boolean isPossiblyTruncated(OpenAiChatCompletionResponse response, String content) {
        if (response.choices() == null || response.choices().isEmpty()) {
            return false;
        }
        String finishReason = response.choices().get(0).finishReason();
        if (finishReason != null && finishReason.equalsIgnoreCase("length")) {
            return true;
        }
        if (!StringUtils.hasText(content)) {
            return false;
        }
        String trimmed = content.trim();
        char lastChar = trimmed.charAt(trimmed.length() - 1);
        return lastChar == ':' || lastChar == '：' || lastChar == ',' || lastChar == '，' || lastChar == '(' || lastChar == '（' || lastChar == '【' || lastChar == '“' || lastChar == '"' || lastChar == '-' || lastChar == '、';
    }

    private String continueAnswer(List<OpenAiMessage> messages,
                                  String partialContent,
                                  Double temperature,
                                  Integer maxTokens) {
        List<OpenAiMessage> followUpMessages = new ArrayList<>(messages);
        followUpMessages.add(new OpenAiMessage("assistant", partialContent));
        followUpMessages.add(new OpenAiMessage("user", "上面的回答被截断了，请在不重复已有内容的前提下继续完整回答，直接从未完成处接着写完。"));
        OpenAiChatCompletionResponse response = sendChatCompletion(followUpMessages, temperature, maxTokens);
        return extractContent(response);
    }

    private String appendContinuation(String content, String continuation) {
        String left = StringUtils.hasText(content) ? content.trim() : "";
        String right = StringUtils.hasText(continuation) ? continuation.trim() : "";
        if (!StringUtils.hasText(left)) {
            return right;
        }
        if (!StringUtils.hasText(right)) {
            return left;
        }
        if (right.startsWith(left)) {
            return right;
        }
        if (left.endsWith(right)) {
            return left;
        }
        return left + "\n\n" + right;
    }

    private String extractContent(OpenAiChatCompletionResponse response) {
        if (response.choices() == null || response.choices().isEmpty() || response.choices().get(0).message() == null) {
            throw new AiClientException("AI 接口返回内容为空");
        }
        String content = response.choices().get(0).message().content();
        if (!StringUtils.hasText(content)) {
            throw new AiClientException("AI 接口返回内容为空");
        }
        return content;
    }
}
