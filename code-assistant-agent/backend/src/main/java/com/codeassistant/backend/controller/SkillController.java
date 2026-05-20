package com.codeassistant.backend.controller;

import com.codeassistant.backend.dto.agent.AgentUserSkillItem;
import com.codeassistant.backend.dto.agent.AgentUserSkillUpsertRequest;
import com.codeassistant.backend.repository.AgentUserSkillRepository;
import com.codeassistant.backend.repository.entity.AgentUserSkillEntity;
import com.codeassistant.backend.dto.auth.AuthSessionUser;
import com.codeassistant.backend.service.auth.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/skills")
public class SkillController {

    private final AuthService authService;
    private final AgentUserSkillRepository skillRepository;

    private static final List<AgentUserSkillUpsertRequest> SAMPLE_SKILLS = List.of(
        new AgentUserSkillUpsertRequest(
            "回答格式规范",
            "控制 Agent 回答的结构与风格",
            "回答时请遵循以下格式规范：\n" +
            "1. 先给出结论或核心答案，再展开解释；\n" +
            "2. 代码示例必须包含注释，说明关键逻辑；\n" +
            "3. 算法类回答需包含时间复杂度和空间复杂度分析；\n" +
            "4. 回答结构清晰，使用标题和分点列表，避免大段无结构文字；\n" +
            "5. 如有多种方案，先推荐最佳方案，再列出备选。",
            true
        ),
        new AgentUserSkillUpsertRequest(
            "编程语言偏好",
            "指定代码示例优先使用的语言",
            "代码示例优先使用 Java 或 Python：\n" +
            "- 后端逻辑、数据结构、算法题：优先用 Java（符合课程技术栈）；\n" +
            "- 数据处理、脚本类、快速原型：优先用 Python；\n" +
            "- 前端相关：使用 JavaScript / TypeScript；\n" +
            "- 若用户指定了语言，以用户要求为准；\n" +
            "- 所有代码示例保证可运行，变量命名使用英文驼峰或下划线风格。",
            true
        ),
        new AgentUserSkillUpsertRequest(
            "错误诊断思路",
            "引导 Agent 按分层思路排查 bug",
            "排查后端错误时，按以下顺序逐层检查：\n" +
            "1. Controller 层：请求参数是否正确、路由是否匹配、权限拦截器是否放行；\n" +
            "2. Service 层：业务逻辑是否有空指针、集合越界、类型转换异常；\n" +
            "3. Repository 层：SQL 语句是否正确、参数绑定是否对齐；\n" +
            "4. 数据库层：连接配置、事务边界、字段类型是否匹配；\n" +
            "5. 给出具体的修复建议和示例代码，不只说【可能是】，要给出确定性结论。",
            true
        ),
        new AgentUserSkillUpsertRequest(
            "算法讲解模板",
            "要求算法讲解包含完整的五步结构",
            "讲解算法时，必须包含以下五个部分，缺一不可：\n" +
            "1. 【基本思想】用自然语言描述算法核心逻辑，不超过 3 句话；\n" +
            "2. 【时间/空间复杂度】给出最好、平均、最坏三种情况；\n" +
            "3. 【伪代码】用结构化伪代码描述步骤，不依赖特定语言语法；\n" +
            "4. 【代码实现】给出完整可运行的代码，包含注释；\n" +
            "5. 【例题练习】给出 1-2 道典型例题，标注难度和对应场景。",
            true
        ),
        new AgentUserSkillUpsertRequest(
            "代码审查重点",
            "指定代码审查时重点关注的维度",
            "审查代码时，按以下优先级检查：\n" +
            "1. 安全性：是否有 SQL 注入、XSS、未校验的用户输入、权限漏洞；\n" +
            "2. 异常处理：是否有未捕获的异常、是否对 null 做了防御；\n" +
            "3. 边界条件：空集合、空字符串、超大数值、并发竞态是否处理；\n" +
            "4. 性能：是否有 N+1 查询、不必要的循环嵌套、大对象创建；\n" +
            "5. 可读性：命名是否清晰、方法是否单一职责、逻辑是否可测试；\n" +
            "每个问题给出具体行号（如有）和修复建议。",
            true
        ),
        new AgentUserSkillUpsertRequest(
            "练习题生成规范",
            "控制练习题的格式和难度分布",
            "生成练习题时，遵循以下规范：\n" +
            "1. 每套题目包含：填空题 2 道、选择题 2 道、编程题 1-2 道；\n" +
            "2. 难度分布：基础 40%、中等 40%、进阶 20%；\n" +
            "3. 每道题附带答案和解析，编程题附带参考代码；\n" +
            "4. 题目场景尽量贴近实际开发（如：实现一个 LRU 缓存、修复一段有 bug 的代码）；\n" +
            "5. 避免纯记忆型题目，优先考察理解和应用能力。",
            true
        ),
        new AgentUserSkillUpsertRequest(
            "简洁回答模式",
            "要求 Agent 给出简短精炼的回答",
            "启用简洁模式：\n" +
            "- 回答控制在 300 字以内；\n" +
            "- 只给出核心结论，不展开背景介绍；\n" +
            "- 代码示例不超过 20 行；\n" +
            "- 不使用多级标题，只用简单分点；\n" +
            "- 适用场景：快速确认概念、简单语法问题、不需要深度解释的情况。",
            true
        ),
        new AgentUserSkillUpsertRequest(
            "Spring Boot 开发规范",
            "针对 Spring Boot 项目的编码约束",
            "在 Spring Boot 项目中，遵循以下规范：\n" +
            "1. 分层架构：Controller 只做参数校验和路由，业务逻辑放 Service，数据访问放 Repository；\n" +
            "2. 统一响应：使用 ResponseEntity 或统一的 ApiResponse 包装返回值；\n" +
            "3. 异常处理：使用 @ExceptionHandler 或 @ControllerAdvice 集中处理，不在 Controller 里 try-catch；\n" +
            "4. 事务：@Transactional 加在 Service 层，不加在 Controller 和 Repository；\n" +
            "5. 参数校验：使用 @Valid + javax/jakarta 注解校验入参，不在业务代码里手动 if 判断；\n" +
            "6. 命名：接口路径用 kebab-case，Java 类名用 PascalCase，方法和变量用 camelCase。",
            true
        )
    );

    public SkillController(AuthService authService, AgentUserSkillRepository skillRepository) {
        this.authService = authService;
        this.skillRepository = skillRepository;
    }

    @GetMapping
    public List<AgentUserSkillItem> listSkills(@RequestHeader(value = "Authorization", required = false) String authorization) {
        Long userId = requireUserId(authorization);
        return skillRepository.findByUserId(userId).stream().map(this::toItem).toList();
    }

    @PostMapping
    public AgentUserSkillItem createSkill(@RequestHeader(value = "Authorization", required = false) String authorization,
                                          @Valid @RequestBody AgentUserSkillUpsertRequest request) {
        Long userId = requireUserId(authorization);
        AgentUserSkillEntity entity = new AgentUserSkillEntity();
        entity.setUserId(userId);
        entity.setName(request.name().trim());
        entity.setDescription(normalize(request.description()));
        entity.setContent(request.content().trim());
        entity.setEnabled(request.enabled() == null ? Boolean.TRUE : request.enabled());
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        return toItem(skillRepository.save(entity));
    }

    @PutMapping("/{id}")
    public AgentUserSkillItem updateSkill(@RequestHeader(value = "Authorization", required = false) String authorization,
                                          @PathVariable Long id,
                                          @Valid @RequestBody AgentUserSkillUpsertRequest request) {
        Long userId = requireUserId(authorization);
        AgentUserSkillEntity entity = skillRepository.findByUserIdAndId(userId, id)
                .orElseThrow(() -> new IllegalArgumentException("技能不存在或无权限访问"));
        entity.setName(request.name().trim());
        entity.setDescription(normalize(request.description()));
        entity.setContent(request.content().trim());
        entity.setEnabled(request.enabled() == null ? Boolean.TRUE : request.enabled());
        entity.setUpdatedAt(LocalDateTime.now());
        return toItem(skillRepository.save(entity));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteSkill(@RequestHeader(value = "Authorization", required = false) String authorization,
                                            @PathVariable Long id) {
        Long userId = requireUserId(authorization);
        int affected = skillRepository.deleteByUserIdAndId(userId, id);
        if (affected == 0) {
            throw new IllegalArgumentException("技能不存在或无权限访问");
        }
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/import-samples")
    public List<AgentUserSkillItem> importSamples(@RequestHeader(value = "Authorization", required = false) String authorization) {
        Long userId = requireUserId(authorization);
        List<String> existingNames = skillRepository.findByUserId(userId).stream()
                .map(AgentUserSkillEntity::getName)
                .toList();
        return SAMPLE_SKILLS.stream()
                .filter(sample -> !existingNames.contains(sample.name()))
                .map(sample -> {
                    AgentUserSkillEntity entity = new AgentUserSkillEntity();
                    entity.setUserId(userId);
                    entity.setName(sample.name());
                    entity.setDescription(normalize(sample.description()));
                    entity.setContent(sample.content().trim());
                    entity.setEnabled(Boolean.TRUE);
                    entity.setCreatedAt(LocalDateTime.now());
                    entity.setUpdatedAt(LocalDateTime.now());
                    return toItem(skillRepository.save(entity));
                })
                .toList();
    }

    private Long requireUserId(String authorization) {
        AuthSessionUser user = authService.resolveUser(authorization);
        if (user == null) {
            throw new IllegalArgumentException("请先登录后再管理技能");
        }
        return user.id();
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private AgentUserSkillItem toItem(AgentUserSkillEntity entity) {
        return new AgentUserSkillItem(
                entity.getId(),
                entity.getName(),
                entity.getDescription(),
                entity.getContent(),
                entity.getEnabled(),
                entity.getUpdatedAt()
        );
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleIllegalArgument(IllegalArgumentException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(exception.getMessage());
    }
}
