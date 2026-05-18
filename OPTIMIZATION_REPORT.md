# Code Assistant Agent — 迭代三优化报告

> **迭代版本**: 迭代三（Iteration 3）
> **基准测试集**: `agent-eval/datasets/code-assistant-eval-datasets.json`（6 个数据集，48 条用例）
> **报告日期**: 2026-05-18

---

## 一、测试结果分析

### 1.1 测试批次概览

| 任务名 | 总用例数 | 成功率 | 主要问题 |
|--------|----------|--------|----------|
| test（通用问答） | 20 | ~50% | 大量调用失败（latency=60s 超时） |
| tool（工具调用） | 10 | ~100% | task_completion 90%，latency_score 0% |
| 666（综合） | 6 | ~100% | latency_score 24.9%，整体质量较好 |
| 演示 | 10 | ~0% | 500 Internal Server Error 全部失败 |
| 第4批次 | 46+ | ~部分 | 大量 Server error 500/502 |

### 1.2 核心问题识别

通过逐条分析测试截图，共归纳出 **5 类根本问题**：

---

#### 问题一：超时导致大规模调用失败（最严重）

**现象**：大量用例 `latency = 60000ms`，结果显示「调用失败」，所有指标均为 0%。

**根因**：`shouldRefine` 方法在 `toolResults` 为空时**无条件返回 true**，导致每次 Agent 执行都强制触发两轮 LLM 调用（`generateAnswer` + `refineAnswer`）。而当评测平台未传 `selectedTools` 时，`runTools` 返回空列表，使 `toolResults.isEmpty() = true`，从而必然进入第二轮调用。两轮调用叠加后总耗时超过评测平台的 60s 超时上限。

```java
// 修改前：toolResults 为空时必然触发 reflexion
private boolean shouldRefine(...) {
    if (Boolean.TRUE.equals(request.enableReflexion())) return true;
    if (toolResults.isEmpty()) return true;  // ← 这里是根因
    return reflection.contains("重规划") || reflection.contains("进一步补强");
}
```

---

#### 问题二：工具从不被自动调用，tool_correctness 偏低

**现象**：评测数据集中大量用例设置了 `expected_tools`（如 `error-keyword-tool`、`code-complexity-tool`、`learning-resource-tool`），但实测中工具调用率偏低，部分批次 `tool_correctness = 0%`。

**根因**：`runTools` 方法完全依赖请求中的 `selectedTools` 字段。评测平台通过适配器调用 `/api/agent/auto-execute` 时**不携带** `selectedTools`，导致该字段为空，工具一个都不会被调用。

```java
// 修改前：没有 selectedTools 直接返回空列表
private List<AgentToolResult> runTools(AgentRequest request) {
    List<String> selectedTools = request.selectedTools();
    if (selectedTools == null || selectedTools.isEmpty()) {
        return List.of();  // ← 评测时必然走这里
    }
    ...
}
```

---

#### 问题三：latency_score 全部为 0%

**现象**：所有成功用例的 `latency_score = 0%`，包括单次成功、回答质量很高的用例。

**根因**：
1. 问题一导致的双倍调用使响应时间普遍超过 120 秒，远超评测平台的延迟阈值。
2. 即便单次调用，当前 `max-concurrent-requests = 3` 在并发请求时会立即抛出 `AiBusyException`，导致失败；排队等待则进一步增加延迟。

---

#### 问题四：Server error 500/502 大规模出现

**现象**：第4批次、演示批次中，约 60% 的用例返回 `Server error '500 Internal Server Error'` 或 `'502 Bad Gateway'`。

**根因**：
1. `max-concurrent-requests = 3` 过低，评测平台并发发起请求时信号量耗尽，`tryAcquire()` **立即**返回 false 并抛 `AiBusyException`（HTTP 429），评测平台将 429 记为失败。
2. 部分 502 源于上游 AI 接口（ModelScope）在高并发下的限流响应，后端未做重试直接抛出。

---

#### 问题五：任务类型识别不准确

**现象**：部分用例（如算法讲解、代码审查类）未被正确分类，导致没有选择最匹配的工具，回答结构也不符合对应任务类型的模板要求。

**根因**：`fallbackTaskType` 规则集覆盖不足，缺少以下关键词：
- 算法类：`动态规划`、`bfs`、`dfs`、`链表`、`树`、`图`、`kmp`、`lru`、`滑动窗口`、`分治` 等
- 代码审查类：`以下代码`、`这段代码`、` ``` ` 等代码块标识
- 错误诊断类：`不知道怎么修`、`崩溃` 等

---

## 二、优化方案与实施

### 2.1 修复一：消除无条件 Reflexion 触发（解决超时问题）

**修改文件**：`CodeAssistantAgentServiceImpl.java`

**修改逻辑**：将 `shouldRefine` 中 `toolResults.isEmpty()` 条件**移除**，改为仅在用户显式开启 `enableReflexion=true` 时触发二次修正。当 `toolResults` 为空（无工具结果）时，Agent 依然能给出完整回答，无需强制第二轮 LLM 调用。

```java
// 修改后：不再因工具为空而触发
private boolean shouldRefine(AgentRequest request, String reflection, List<AgentToolResult> toolResults) {
    if (Boolean.TRUE.equals(request.enableReflexion())) {
        return true;
    }
    return reflection.contains("重规划") || reflection.contains("进一步补强");
}
```

**预期效果**：常规请求从 2 次 LLM 调用降为 1 次，响应时间减少约 50%，超时失败率大幅降低。

---

### 2.2 修复二：工具自动调用（解决 tool_correctness 低问题）

**修改文件**：`CodeAssistantAgentServiceImpl.java`（`runTools` 和 `requiresToolObservation`）

**修改逻辑**：当请求中未携带 `selectedTools` 时，改为**根据任务类型自动筛选**所有 `supports(taskType)` 为 true 的工具并执行（最多 3 个）。

```java
// 修改后：无 selectedTools 时自动按任务类型选择工具
private List<AgentToolResult> runTools(AgentRequest request) {
    List<String> selectedTools = request.selectedTools();
    if (selectedTools == null || selectedTools.isEmpty()) {
        return agentTools.stream()
                .filter(tool -> tool.supports(request.taskType()))
                .limit(3)
                .map(tool -> tool.execute(request))
                .toList();
    }
    // 有显式指定时：在支持该任务类型的工具中再按名称过滤
    return agentTools.stream()
            .filter(tool -> tool.supports(request.taskType()))
            .filter(tool -> selectedTools.contains(tool.name()))
            .limit(3)
            .map(tool -> tool.execute(request))
            .toList();
}
```

**各任务类型的自动工具映射**：

| 任务类型 | 自动调用工具 |
|----------|-------------|
| `ERROR_EXPLANATION` | `error-keyword-tool`、`backend-bug-fix-tool` |
| `CODE_REVIEW` | `error-keyword-tool`、`code-complexity-tool`、`backend-bug-fix-tool` |
| `ALGORITHM_GUIDE` | `code-complexity-tool`、`learning-resource-tool` |
| `PRACTICE_GENERATION` | `learning-resource-tool` |
| `GENERAL_CHAT` | `learning-resource-tool`、`frontend-code-generation-tool` |

**预期效果**：评测集中设有 `expected_tools` 的用例工具调用率从约 0% 提升至接近 100%，`tool_correctness` 指标显著改善。

---

### 2.3 修复三：动态调整 maxTokens（解决输出截断问题）

**修改文件**：`CodeAssistantAgentServiceImpl.java`

**修改逻辑**：引入 `resolveMaxTokens(taskType)` 方法，根据任务类型动态分配 token 上限。算法讲解和练习题生成需要更长输出，代码审查次之，通用聊天最少。

```java
private int resolveMaxTokens(AgentTaskType taskType) {
    return switch (taskType) {
        case ALGORITHM_GUIDE, PRACTICE_GENERATION -> 4096;
        case CODE_REVIEW -> 3200;
        default -> 2600;
    };
}
```

**修改前后对比**：

| 任务类型 | 修改前（固定） | 修改后（动态） |
|----------|---------------|---------------|
| `ALGORITHM_GUIDE` | 2600 tokens | **4096 tokens** |
| `PRACTICE_GENERATION` | 2600 tokens | **4096 tokens** |
| `CODE_REVIEW` | 2600 tokens | **3200 tokens** |
| `GENERAL_CHAT` / `ERROR_EXPLANATION` | 2600 tokens | 2600 tokens（不变） |

**预期效果**：算法讲解、练习题生成类用例回答完整性提升，`task_completion` 和 `answer_relevancy` 进一步改善。

---

### 2.4 修复四：提高并发限制 + 等待重试（解决 429/500 问题）

**修改文件**：`application.yml`、`OpenAiCompatibleChatService.java`

**修改一**：默认并发数从 3 提升至 8。

```yaml
# 修改前
max-concurrent-requests: ${AI_API_MAX_CONCURRENT_REQUESTS:3}
# 修改后
max-concurrent-requests: ${AI_API_MAX_CONCURRENT_REQUESTS:8}
```

**修改二**：信号量获取策略从「立即失败」改为「最长等待 30 秒」，避免评测批量调用时因信号量瞬间耗尽而立即抛出 429。

```java
// 修改前：立即失败
if (!concurrencyLimiter.tryAcquire()) {
    throw new AiBusyException(BUSY_MESSAGE);
}

// 修改后：等待最多 30 秒
boolean acquired = concurrencyLimiter.tryAcquire(30, TimeUnit.SECONDS);
if (!acquired) {
    throw new AiBusyException(BUSY_MESSAGE);
}
```

**预期效果**：评测批量并发场景下的 429 错误率大幅降低，Server error 比例显著减少。

---

### 2.5 修复五：完善任务类型识别规则

**修改文件**：`AgentRequestParsingServiceImpl.java`（`buildSystemPrompt` 和 `fallbackTaskType`）

**新增关键词覆盖**：

| 任务类型 | 新增关键词 |
|----------|-----------|
| `ERROR_EXPLANATION` | `nullpointer`、`出错`、`崩溃`、`修复`+`原因` 组合 |
| `CODE_REVIEW` | `以下代码`、`这段代码`、` ``` `（代码块）、`安全`、`重构` |
| `ALGORITHM_GUIDE` | `动态规划`、`bfs`、`dfs`、`链表`、`树`、`图`、`哈希`、`滑动窗口`、`双指针`、`递归`、`分治`、`kmp`、`lru`、`dijkstra`、`贪心`、`搜索`、`遍历`、`二分` |
| `PRACTICE_GENERATION` | `生成`+`练习题` 组合、`出题` |

**优先级调整**：将算法类关键词的优先级**提前到代码审查之前**，避免含有「审查」「质量」等词的算法题被误分为 CODE_REVIEW。错误诊断的优先级也调整到最高，避免含有报错信息的用例被误分。

**System Prompt 优化**：将原本一段式的描述改为**编号优先级规则列表**，使模型更稳定地按优先级顺序判断任务类型。

---

## 三、修改文件汇总

| 文件路径 | 修改类型 | 修改摘要 |
|---------|---------|---------|
| `backend/.../CodeAssistantAgentServiceImpl.java` | 核心逻辑 | 工具自动调用、Reflexion 策略、动态 maxTokens |
| `backend/.../AgentRequestParsingServiceImpl.java` | 解析逻辑 | fallback 规则完善、system prompt 优化 |
| `backend/.../OpenAiCompatibleChatService.java` | 基础设施 | 信号量等待策略：立即失败→等待30s |
| `backend/src/main/resources/application.yml` | 配置 | max-concurrent-requests: 3 → 8 |

---

## 四、预期指标改善

| 评测指标 | 修改前（典型值） | 修改后（预期） | 改善原因 |
|---------|---------------|--------------|---------|
| `task_completion` | 0%（失败批次）/ 80-100%（成功批次） | **稳定 90%+** | 超时失败率降低，更多用例能正常完成 |
| `answer_relevancy` | 0%（失败批次）/ 90-100%（成功批次） | **稳定 90%+** | 同上，任务类型识别更准确 |
| `tool_correctness` | 0-100%（不稳定） | **90%+** | 工具自动调用确保 expected_tools 被覆盖 |
| `safety` | 80-100% | **保持 95%+** | 未影响安全性相关逻辑 |
| `latency` | 120,000-180,000ms | **60,000-130,000ms** | 单次调用替代双倍调用 |
| `latency_score` | 0% | **有改善** | 响应时间缩短，部分用例可能达标 |
| **整体成功率** | ~50%（含超时失败） | **90%+** | 超时、并发失败大幅减少 |

---

## 五、测试结果中已良好的方面（保留）

以下是测试中已经表现良好、本次迭代**不做破坏性修改**的部分：

- **Safety 指标**：成功用例的 safety 基本在 96-100%，说明内容安全策略有效。
- **Answer Relevancy**：成功用例中基本 90-100%，说明 Agent 的回答质量和相关性本身是达标的。
- **多框架执行链路**：Plan-and-Execute + ReAct-style + Reflexion 的分阶段结构正确，`stageTraces` 数据完整。
- **任务专用 Prompt 模板**：`ALGORITHM_GUIDE` 的「基本思想→复杂度→伪代码→代码实现→例题」模板结构清晰，输出质量高。
- **多轮对话**：对话上下文管理、会话摘要压缩、RAG 知识注入等功能稳定。

---

## 六、后续可继续优化的方向

1. **Reflexion 智能判断**：当前 Reflexion 仅在显式开启时触发。后续可通过 LLM 对初步回答进行自动评分（1-5分），分数低于阈值时再触发二次修正，实现更智能的质量把控。

2. **工具结果质量增强**：现有三个工具（`ErrorKeywordTool`、`CodeComplexityTool`、`LearningResourceTool`）返回的是静态规则文本。后续可引入真实的代码静态分析或外部知识库检索，提升工具观察的信息含量。

3. **Latency Score 专项优化**：考虑对简单问题（如 GENERAL_CHAT 不含代码）引入轻量级直接回答路径，跳过完整的 Plan-Action-Reflect 链路，将响应时间压缩至 30 秒以内。

4. **错误重试机制**：当 AI 接口返回 502/503 时，加入指数退避重试（最多 2 次），减少上游不稳定导致的测试失败。

5. **流式输出**：为 `/api/agent/auto-execute` 添加 SSE 流式响应，使前端可以边生成边展示，改善用户感知延迟。

---

*本报告由迭代三代码审查与优化过程自动生成，最终修改以代码提交为准。*
