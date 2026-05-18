"""
Code-Assistant-Agent 协议适配器

将 agent-eval 平台的通用协议转换为 code-assistant-agent 的格式，
同时将 Agent 响应转换回 eval 平台期望的格式。

eval 平台 → 适配器: POST / {"input": "..."}
适配器 → Agent:    POST /api/agent/auto-execute {"message": "...", ...}
Agent → 适配器:    AgentResponse (含 answer, tools, planSteps 等)
适配器 → eval 平台: {"output": "...", "steps": [...], "tokens": ..., "tool_calls": [...]}

用法:
    python code-assistant-adapter.py --agent-url http://localhost:8080 --port 8001
"""
import argparse
import json
import time
import sys
from http.server import HTTPServer, BaseHTTPRequestHandler

import urllib.request
import urllib.error


AGENT_URL = "http://localhost:8080"


def call_agent(input_text: str) -> dict:
    """调用 code-assistant-agent 的 auto-execute 接口"""
    payload = {
        "message": input_text,
        "enableReflexion": True,
    }
    req = urllib.request.Request(
        f"{AGENT_URL}/api/agent/auto-execute",
        data=json.dumps(payload).encode("utf-8"),
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    with urllib.request.urlopen(req, timeout=180) as resp:
        return json.loads(resp.read().decode("utf-8"))


def convert_response(agent_resp: dict) -> dict:
    """将 AgentResponse 转换为 eval 平台期望的格式"""
    # 构造 steps（中间步骤）
    steps = []

    for step in agent_resp.get("planSteps", []):
        steps.append({
            "type": "plan",
            "step": step.get("stepNumber"),
            "goal": step.get("goal"),
            "framework": step.get("framework"),
            "status": step.get("status"),
        })

    for rs in agent_resp.get("reasoningSteps", []):
        steps.append({"type": "reasoning", "content": rs})

    for trace in agent_resp.get("actionTraces", []):
        steps.append({
            "type": "action",
            "thought": trace.get("thought"),
            "action": trace.get("action"),
            "observation": trace.get("observation"),
        })

    if agent_resp.get("reflection"):
        steps.append({"type": "reflection", "content": agent_resp["reflection"]})

    # 构造 tool_calls
    tool_calls = []
    for t in agent_resp.get("tools", []):
        tool_calls.append({
            "name": t.get("name"),
            "input": t.get("input"),
            "output": t.get("output"),
        })

    # Token 估算（Agent 未返回精确值，粗略估算）
    answer = agent_resp.get("answer", "")
    estimated_tokens = len(answer)  # 中英文混合，粗略按字符数估算

    return {
        "output": answer,
        "steps": steps,
        "tokens": estimated_tokens,
        "tool_calls": tool_calls,
    }


class AdapterHandler(BaseHTTPRequestHandler):
    def do_POST(self):
        start = time.time()
        try:
            content_length = int(self.headers.get("Content-Length", 0))
            body = self.rfile.read(content_length)
            request_data = json.loads(body.decode("utf-8"))

            input_text = request_data.get("input", "")
            if not input_text:
                self._send_error(400, "缺少 input 字段")
                return

            print(f"[{time.strftime('%H:%M:%S')}] 收到请求: {input_text[:80]}...")

            agent_resp = call_agent(input_text)
            converted = convert_response(agent_resp)

            elapsed = int((time.time() - start) * 1000)
            print(f"[{time.strftime('%H:%M:%S')}] 完成 ({elapsed}ms) — output 长度: {len(converted['output'])}")

            self._send_json(200, converted)

        except urllib.error.HTTPError as e:
            body = e.read().decode("utf-8", errors="replace")
            print(f"[{time.strftime('%H:%M:%S')}] Agent 错误: HTTP {e.code} — {body[:200]}")
            self._send_json(502, {
                "output": f"[Agent 返回错误] HTTP {e.code}",
                "steps": [],
                "tokens": 0,
                "tool_calls": [],
            })
        except Exception as e:
            elapsed = int((time.time() - start) * 1000)
            print(f"[{time.strftime('%H:%M:%S')}] 异常 ({elapsed}ms): {e}")
            self._send_json(500, {
                "output": f"[适配器异常] {e}",
                "steps": [],
                "tokens": 0,
                "tool_calls": [],
            })

    def do_GET(self):
        """健康检查"""
        self._send_json(200, {"status": "ok", "agent": AGENT_URL})

    def _send_json(self, status: int, data: dict):
        body = json.dumps(data, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def _send_error(self, status: int, message: str):
        self._send_json(status, {"output": message, "steps": [], "tokens": 0, "tool_calls": []})

    def log_message(self, format, *args):
        pass  # 关闭默认日志，使用自定义日志


def main():
    global AGENT_URL

    parser = argparse.ArgumentParser(description="Code-Assistant-Agent 协议适配器")
    parser.add_argument(
        "--agent-url",
        default="http://localhost:8080",
        help="code-assistant-agent 后端地址 (默认: http://localhost:8080)",
    )
    parser.add_argument(
        "--port",
        type=int,
        default=8001,
        help="适配器监听端口 (默认: 8001)",
    )
    args = parser.parse_args()

    AGENT_URL = args.agent_url.rstrip("/")

    print(f"适配器启动")
    print(f"  监听端口: {args.port}")
    print(f"  转发到:   {AGENT_URL}/api/agent/auto-execute")
    print(f"  eval 平台 agent_url 填写: http://localhost:{args.port}")
    print()

    server = HTTPServer(("0.0.0.0", args.port), AdapterHandler)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\n适配器已停止")
        server.shutdown()


if __name__ == "__main__":
    main()
