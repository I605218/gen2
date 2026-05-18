# Agent 评估平台

面向 LLM Agent 的通用评估平台，支持评测任务管理、多种评估指标计算、结果可视化与多任务对比分析。

---

## 项目结构

```
agent-eval/
├── backend/        # FastAPI 后端
│   ├── app/
│   │   ├── api/            # 路由：tasks, datasets, metrics
│   │   ├── db/             # 数据库连接
│   │   ├── models/         # SQLAlchemy 模型（自动建表）
│   │   ├── schemas/        # Pydantic 请求/响应模型
│   │   ├── services/       # 评估执行逻辑 + 指标计算
│   │   ├── config.py       # 配置（读取 .env）
│   │   └── main.py         # 应用入口
│   ├── requirements.txt
│   └── .env.example
└── frontend/       # Vue 3 前端
    ├── src/
    │   ├── api/            # HTTP 请求封装
    │   ├── router/         # Vue Router 路由
    │   └── views/          # 页面组件
    └── .env.example
```

---

## 环境准备

### 数据库（MySQL）

在 MySQL 中手动创建一个空数据库（表由程序启动时自动创建）：

```sql
CREATE DATABASE agent_eval CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

---

## 后端启动

### 1. 安装 Python 依赖

> 建议 Python 3.11+，推荐使用虚拟环境

```bash
cd backend
python -m venv venv

# Windows
venv\Scripts\activate
# macOS/Linux
source venv/bin/activate

pip install -r requirements.txt
```

### 2. 配置环境变量

复制 `.env.example` 为 `.env`，填入你的 MySQL 密码和 OpenAI API Key：

```bash
cp .env.example .env
```

`.env` 内容示例：

```
DB_HOST=localhost
DB_PORT=3306
DB_USER=root
DB_PASSWORD=你的MySQL密码
DB_NAME=agent_eval

OPENAI_API_KEY=sk-xxx
OPENAI_BASE_URL=https://api.openai.com/v1
JUDGE_MODEL=gpt-4o-mini
```

> `OPENAI_API_KEY` 仅在使用模糊指标（task_completion、answer_relevancy、safety）时才需要。
> 如果只使用显式指标（latency、token_count、tool_correctness），可留空。

### 3. 启动后端

```bash
uvicorn app.main:app --reload --host 0.0.0.0 --port 8000
```

启动后访问 http://localhost:8000/docs 可查看 API 文档。

---

## 前端启动

```bash
cd frontend

# 安装依赖
npm install

# 配置环境变量（默认已指向 localhost:8000，一般不需要改）
cp .env.example .env

# 启动开发服务器
npm run dev
```

启动后访问 http://localhost:5173

---

## 数据集

预置数据集位于 `datasets/` 目录，覆盖编程问答、错误诊断、代码审查、算法指导、练习题生成等场景，共 6 个数据集 48 条测试用例。

### 导入数据集

确保后端已启动，运行导入脚本：

```bash
cd datasets
python import_datasets.py --base-url http://localhost:8000
```

### 数据集文件结构

```
datasets/
├── code-assistant-eval-datasets.json   # 数据集定义（JSON）
└── import_datasets.py                  # API 导入脚本
```

JSON 中每条用例结构：

```json
{
  "input": "用户问题（必填）",
  "expected_output": "预期答案（可选，用于对比评估）",
  "expected_tools": ["tool-name"]       // 预期调用的工具列表（可选）
}
```

---

## 适配器

当被评测的 Agent 接口协议与平台不兼容时，可通过适配器桥接。

### Code-Assistant-Agent 适配器

`adapters/code-assistant-adapter.py` 将平台通用协议转换为 code-assistant-agent 的格式。

**协议转换：**

```
eval 平台 → 适配器: POST / {"input": "..."}
适配器 → Agent:    POST /api/agent/auto-execute {"message": "...", "enableReflexion": true}
Agent → 适配器:    AgentResponse (含 answer, tools, planSteps 等)
适配器 → eval 平台: {"output": "...", "steps": [...], "tokens": 150, "tool_calls": [...]}
```

**启动适配器：**

```bash
cd adapters
python code-assistant-adapter.py --agent-url http://localhost:8080 --port 8001
```

| 参数 | 默认值 | 说明 |
|------|--------|------|
| `--agent-url` | `http://localhost:8080` | code-assistant-agent 后端地址 |
| `--port` | `8001` | 适配器监听端口 |

启动后，创建评测任务时 **Agent URL** 填写 `http://localhost:8001`。

---

## 使用流程

1. **导入数据集**：运行 `datasets/import_datasets.py` 或在前端手动创建
2. **启动适配器**（如需要）：运行 `adapters/code-assistant-adapter.py`
3. **创建评测任务**：进入「评测任务」，填入 Agent URL、选择数据集和评测指标
4. **运行任务**：点击「运行」，平台后台调用 Agent API 并计算指标
5. **查看结果**：点击「结果」查看单次评测详情和雷达图
6. **对比分析**：进入「对比分析」，选择多个已完成的任务进行柱状图对比

---

## Agent 接入协议

被评测的 Agent 需要提供一个 HTTP 接口，满足以下格式：

**请求**
```
POST <agent_url>
Content-Type: application/json

{"input": "用户的问题"}
```

**响应**
```json
{
  "output": "Agent 的回答",
  "steps": [
    {"type": "think", "content": "我需要先查找..."},
    {"type": "tool_call", "name": "search", "input": "...", "output": "..."}
  ],
  "tokens": 150,
  "tool_calls": [
    {"name": "search", "input": {"query": "..."}, "output": "..."}
  ]
}
```

> `steps`、`tokens`、`tool_calls` 均为可选字段，不提供时跳过对应指标计算。

---

## 支持的评测指标

| 指标 | 类型 | 说明 |
|------|------|------|
| `latency` | 显式 | 响应时间（毫秒） |
| `token_count` | 显式 | Token 消耗数量 |
| `tool_correctness` | 显式 | 工具调用正确率（需数据集填写预期工具） |
| `task_completion` | 模糊（LLM-as-a-Judge） | 任务完成率 |
| `answer_relevancy` | 模糊（LLM-as-a-Judge） | 答案相关性 |
| `safety` | 模糊（LLM-as-a-Judge） | 安全性 |
