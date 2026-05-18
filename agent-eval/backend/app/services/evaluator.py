"""
评测执行服务：调用 Agent API，收集结果，计算指标，写入数据库
"""
import asyncio
import time
from datetime import datetime, timezone
from typing import Any

import httpx
from sqlalchemy.orm import Session

from app.models.evaluation import EvaluationTask, EvaluationRecord, TaskStatus
from app.models.dataset import Dataset
from app.services.metrics import compute_explicit_metrics, compute_llm_metrics


async def run_evaluation(task_id: int, db: Session):
    """后台执行评测任务的入口"""
    task: EvaluationTask = db.query(EvaluationTask).filter(EvaluationTask.id == task_id).first()
    if not task:
        return

    task.status = TaskStatus.running
    task.started_at = datetime.now(timezone.utc)
    db.commit()

    try:
        dataset: Dataset = db.query(Dataset).filter(Dataset.id == task.dataset_id).first()
        if not dataset:
            raise ValueError(f"数据集 {task.dataset_id} 不存在")

        items = dataset.items  # list of dicts
        metrics_list = task.metrics

        all_scores: dict[str, list] = {}

        async with httpx.AsyncClient(timeout=180.0) as client:
            for item in items:
                input_text = item.get("input", "")
                expected_output = item.get("expected_output")
                expected_tools = item.get("expected_tools") or []

                # 调用 Agent
                start_ms = time.time()
                try:
                    response = await client.post(
                        task.agent_url,
                        json={"input": input_text},
                    )
                    response.raise_for_status()
                    data: dict = response.json()
                except Exception as e:
                    data = {}
                    actual_output = f"[调用失败] {e}"
                    latency_ms = int((time.time() - start_ms) * 1000)
                else:
                    latency_ms = int((time.time() - start_ms) * 1000)
                    actual_output = data.get("output", "")

                steps = data.get("steps")
                token_count = data.get("tokens")
                tool_calls = data.get("tool_calls") or []

                # 计算显式指标
                explicit = compute_explicit_metrics({
                    "latency_ms": latency_ms,
                    "token_count": token_count,
                    "tool_calls": tool_calls,
                    "expected_tools": expected_tools,
                })

                # 计算模糊指标
                llm_scores = await compute_llm_metrics(
                    input_text, actual_output, metrics_list, expected_output
                )

                metric_scores = {**explicit, **llm_scores}

                # 汇总各指标分数
                for k, v in metric_scores.items():
                    all_scores.setdefault(k, []).append(v)

                record = EvaluationRecord(
                    task_id=task.id,
                    input=input_text,
                    expected_output=expected_output,
                    actual_output=actual_output,
                    steps=steps,
                    latency_ms=latency_ms,
                    token_count=token_count,
                    tool_calls=tool_calls,
                    metric_scores=metric_scores,
                )
                db.add(record)
                db.commit()

        # 计算汇总（各指标均值）
        result_summary = {
            k: round(sum(v) / len(v), 4)
            for k, v in all_scores.items()
            if v
        }
        result_summary["total_cases"] = len(items)

        task.status = TaskStatus.completed
        task.result_summary = result_summary
        task.finished_at = datetime.now(timezone.utc)
        db.commit()

    except Exception as e:
        task.status = TaskStatus.failed
        task.error_message = str(e)
        task.finished_at = datetime.now(timezone.utc)
        db.commit()
        raise
