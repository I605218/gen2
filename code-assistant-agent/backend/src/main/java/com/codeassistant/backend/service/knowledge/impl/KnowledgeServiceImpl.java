package com.codeassistant.backend.service.knowledge.impl;

import com.codeassistant.backend.dto.knowledge.KnowledgeDocumentItem;
import com.codeassistant.backend.dto.knowledge.KnowledgeSearchResponse;
import com.codeassistant.backend.dto.knowledge.KnowledgeSearchResultItem;
import com.codeassistant.backend.dto.knowledge.KnowledgeUpsertRequest;
import com.codeassistant.backend.repository.KnowledgeChunkRepository;
import com.codeassistant.backend.repository.KnowledgeDocumentRepository;
import com.codeassistant.backend.repository.entity.KnowledgeChunkEntity;
import com.codeassistant.backend.repository.entity.KnowledgeDocumentEntity;
import com.codeassistant.backend.service.knowledge.KnowledgeService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class KnowledgeServiceImpl implements KnowledgeService {

    private static final int DEFAULT_CHUNK_SIZE = 900;
    private static final int DEFAULT_OVERLAP = 180;

    private final KnowledgeDocumentRepository documentRepository;
    private final KnowledgeChunkRepository chunkRepository;

    public KnowledgeServiceImpl(KnowledgeDocumentRepository documentRepository,
                                KnowledgeChunkRepository chunkRepository) {
        this.documentRepository = documentRepository;
        this.chunkRepository = chunkRepository;
    }

    @Override
    public List<KnowledgeDocumentItem> listDocuments(Long userId) {
        return documentRepository.findByUserId(userId).stream().map(this::toItem).toList();
    }

    @Override
    public KnowledgeDocumentItem getDocument(Long userId, Long id) {
        return documentRepository.findByUserIdAndId(userId, id).map(this::toItem)
                .orElseThrow(() -> new IllegalArgumentException("知识文档不存在或无权限访问"));
    }

    @Override
    public KnowledgeDocumentItem upsertDocument(Long userId, Long id, KnowledgeUpsertRequest request) {
        KnowledgeDocumentEntity entity = id == null
                ? new KnowledgeDocumentEntity()
                : documentRepository.findByUserIdAndId(userId, id).orElseThrow(() -> new IllegalArgumentException("知识文档不存在或无权限访问"));
        entity.setUserId(userId);
        entity.setTitle(request.title().trim());
        entity.setSourceName(normalize(request.sourceName()));
        entity.setSourceType(normalize(request.sourceType()));
        entity.setTags(normalize(request.tags()));
        entity.setSummary(normalize(request.summary()));
        entity.setAliases(joinList(request.aliases()));
        entity.setCategories(joinList(request.categories()));
        entity.setReferences(joinList(request.references()));
        entity.setContent(StringUtils.hasText(request.content()) ? request.content().trim() : "");
        entity.setTotalChars(entity.getContent().length());
        entity.setEnabled(request.enabled() == null || request.enabled());
        entity.setUpdatedAt(LocalDateTime.now());
        if (entity.getCreatedAt() == null) {
            entity.setCreatedAt(LocalDateTime.now());
        }
        KnowledgeDocumentEntity saved = documentRepository.save(entity);
        rebuildChunks(saved);
        return toItem(saved);
    }

    @Override
    public void deleteDocument(Long userId, Long id) {
        KnowledgeDocumentEntity entity = documentRepository.findByUserIdAndId(userId, id)
                .orElseThrow(() -> new IllegalArgumentException("知识文档不存在或无权限访问"));
        chunkRepository.deleteByUserIdAndDocumentId(userId, entity.getId());
        documentRepository.delete(entity);
    }

    @Override
    public KnowledgeSearchResponse search(Long userId, String query, int limit) {
        int realLimit = Math.max(1, Math.min(limit, 12));
        if (!StringUtils.hasText(query)) {
            return new KnowledgeSearchResponse(query, List.of());
        }
        Set<String> queryTokens = tokenize(query);
        List<KnowledgeSearchResultItem> results = chunkRepository.findSearchCandidates(userId).stream()
                .filter(chunk -> Boolean.TRUE.equals(findDocumentEnabled(userId, chunk.getDocumentId())))
                .map(chunk -> scoreChunk(chunk, queryTokens, query))
                .filter(item -> item.score() > 0)
                .sorted(Comparator.comparingDouble(KnowledgeSearchResultItem::score).reversed())
                .limit(realLimit)
                .toList();
        return new KnowledgeSearchResponse(query, results);
    }

    @Override
    public List<KnowledgeDocumentItem> importSamples(Long userId) {
        List<KnowledgeUpsertRequest> samples = List.of(
                new KnowledgeUpsertRequest(
                        "RAG 知识增强设计",
                        "项目文档",
                        "markdown",
                        "RAG,检索增强,引用,知识库",
                        "RAG 流程：问题识别 -> 检索 -> 排序 -> 注入 prompt -> 回答并标注来源。回答中应保留引用编号，并在结果区展示来源文档、章节、命中片段。",
                        true,
                        "围绕项目 Agent 构建外部知识增强能力的设计文档",
                        List.of("RAG", "知识增强", "知识库动态更新"),
                        List.of("前端", "后端", "答辩加分项"),
                        List.of("外部知识源", "知识引用", "动态刷新")
                ),
                new KnowledgeUpsertRequest(
                        "Java 后端排错手册",
                        "课程笔记",
                        "txt",
                        "后端,调试,异常,数据库,接口",
                        "后端排错建议从 Controller、Service、Repository、数据库连接、事务边界、空指针、集合越界和权限拦截器逐层排查。",
                        true,
                        "常见后端故障定位清单",
                        List.of("异常处理", "SQL", "事务", "登录鉴权"),
                        List.of("后端", "Java", "Spring Boot"),
                        List.of("Controller", "Service", "Repository")
                )
        );
        List<KnowledgeDocumentItem> created = new ArrayList<>();
        for (KnowledgeUpsertRequest sample : samples) {
            created.add(upsertDocument(userId, null, sample));
        }
        return created;
    }

    private Boolean findDocumentEnabled(Long userId, Long documentId) {
        return documentRepository.findByUserIdAndId(userId, documentId)
                .map(KnowledgeDocumentEntity::getEnabled)
                .orElse(Boolean.FALSE);
    }

    private void rebuildChunks(KnowledgeDocumentEntity document) {
        chunkRepository.deleteByUserIdAndDocumentId(document.getUserId(), document.getId());
        List<ChunkWindow> windows = split(document.getContent());
        List<KnowledgeChunkEntity> chunks = new ArrayList<>();
        for (int i = 0; i < windows.size(); i++) {
            ChunkWindow window = windows.get(i);
            KnowledgeChunkEntity chunk = new KnowledgeChunkEntity();
            chunk.setDocumentId(document.getId());
            chunk.setUserId(document.getUserId());
            chunk.setChunkIndex(i + 1);
            chunk.setStartOffset(window.startOffset());
            chunk.setEndOffset(window.endOffset());
            chunk.setTitle(document.getTitle());
            chunk.setContent(window.content());
            chunk.setKeywords(buildKeywords(window.content(), document));
            chunk.setSourceName(document.getSourceName());
            chunk.setSourceType(document.getSourceType());
            chunk.setTags(document.getTags());
            chunk.setSummary(document.getSummary());
            chunk.setReferences(document.getReferences());
            chunk.setKeywordScore(0.0);
            chunk.setLengthScore(lengthScore(window.content()));
            chunk.setFinalScore(0.0);
            chunk.setCreatedAt(LocalDateTime.now());
            chunk.setUpdatedAt(LocalDateTime.now());
            chunks.add(chunk);
        }
        chunkRepository.saveAll(chunks);
    }

    private List<ChunkWindow> split(String content) {
        List<ChunkWindow> result = new ArrayList<>();
        if (!StringUtils.hasText(content)) {
            return result;
        }
        int len = content.length();
        int start = 0;
        while (start < len) {
            int end = Math.min(len, start + DEFAULT_CHUNK_SIZE);
            if (end < len) {
                int newline = content.lastIndexOf('\n', end);
                if (newline > start + 250) {
                    end = newline + 1;
                }
            }
            String chunk = content.substring(start, end).trim();
            if (StringUtils.hasText(chunk)) {
                result.add(new ChunkWindow(start, end, chunk));
            }
            if (end >= len) break;
            start = Math.max(0, end - DEFAULT_OVERLAP);
        }
        return result;
    }

    private String buildKeywords(String chunk, KnowledgeDocumentEntity document) {
        Set<String> tokens = new HashSet<>(tokenize(chunk));
        tokens.addAll(tokenize(document.getTitle()));
        tokens.addAll(tokenize(document.getTags()));
        tokens.addAll(tokenize(document.getAliases()));
        tokens.addAll(tokenize(document.getCategories()));
        tokens.addAll(tokenize(document.getSummary()));
        tokens.addAll(tokenize(document.getReferences()));
        return tokens.stream().limit(60).collect(Collectors.joining(","));
    }

    private Set<String> tokenize(String text) {
        Set<String> tokens = new HashSet<>();
        if (!StringUtils.hasText(text)) return tokens;
        for (String token : text.toLowerCase(Locale.ROOT).split("[\\s,，。.!！?？;；:：()（）\\[\\]{}<>/\\\\|\\-]+")) {
            if (token.length() >= 2) tokens.add(token);
        }
        return tokens;
    }

    private KnowledgeSearchResultItem scoreChunk(KnowledgeChunkEntity chunk, Set<String> queryTokens, String query) {
        Set<String> chunkTokens = tokenize(chunk.getKeywords());
        if (chunkTokens.isEmpty()) {
            chunkTokens = tokenize(chunk.getContent());
        }
        long hitCount = queryTokens.stream().filter(chunkTokens::contains).count();
        double keywordScore = queryTokens.isEmpty() ? 0.0 : (double) hitCount / queryTokens.size();
        double semanticScore = semanticBoost(chunk, query);
        double lengthScore = chunk.getLengthScore() == null ? lengthScore(chunk.getContent()) : chunk.getLengthScore();
        double finalScore = 0.62 * keywordScore + 0.18 * semanticScore + 0.20 * lengthScore;
        chunk.setKeywordScore(keywordScore);
        chunk.setFinalScore(finalScore);
        return new KnowledgeSearchResultItem(
                chunk.getDocumentId(),
                chunk.getId(),
                chunk.getChunkIndex(),
                chunk.getTitle(),
                chunk.getSourceName(),
                chunk.getSourceType(),
                chunk.getTags(),
                chunk.getSummary(),
                chunk.getReferences(),
                chunk.getContent(),
                chunk.getKeywords(),
                keywordScore,
                lengthScore,
                finalScore,
                chunk.getStartOffset(),
                chunk.getEndOffset()
        );
    }

    private double semanticBoost(KnowledgeChunkEntity chunk, String query) {
        String combined = String.join(" ",
                safe(chunk.getTitle()),
                safe(chunk.getSummary()),
                safe(chunk.getTags()),
                safe(chunk.getKeywords()),
                safe(chunk.getContent())
        ).toLowerCase(Locale.ROOT);
        String lowerQuery = safe(query).toLowerCase(Locale.ROOT);
        int hits = 0;
        for (String token : tokenize(query)) {
            if (combined.contains(token)) hits++;
        }
        return lowerQuery.isEmpty() ? 0.0 : Math.min(1.0, hits / (double) Math.max(1, tokenize(query).size()));
    }

    private double lengthScore(String content) {
        if (!StringUtils.hasText(content)) return 0.0;
        int len = content.length();
        if (len < 120) return 0.35;
        if (len < 500) return 0.75;
        return 1.0;
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String joinList(List<String> values) {
        if (values == null || values.isEmpty()) return null;
        return values.stream().filter(StringUtils::hasText).map(String::trim).distinct().collect(Collectors.joining(" | "));
    }

    private List<String> splitList(String value) {
        if (!StringUtils.hasText(value)) return List.of();
        return List.of(value.split("\\s*\\|\\s*"));
    }

    private KnowledgeDocumentItem toItem(KnowledgeDocumentEntity entity) {
        Integer chunkCount = documentRepository.countChunks(entity.getUserId(), entity.getId());
        return new KnowledgeDocumentItem(
                entity.getId(),
                entity.getTitle(),
                entity.getSourceName(),
                entity.getSourceType(),
                entity.getTags(),
                entity.getSummary(),
                splitList(entity.getAliases()),
                splitList(entity.getCategories()),
                splitList(entity.getReferences()),
                entity.getEnabled(),
                chunkCount == null ? 0 : chunkCount,
                entity.getTotalChars(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private record ChunkWindow(int startOffset, int endOffset, String content) {}
}
