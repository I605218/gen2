from pydantic import BaseModel, Field
from typing import Optional, List, Any
from datetime import datetime
from app.models.evaluation import TaskStatus


class EvaluationTaskCreate(BaseModel):
    name: str = Field(..., max_length=128)
    description: Optional[str] = None
    agent_url: str
    agent_version: Optional[str] = None
    dataset_id: int
    metrics: List[str] = Field(..., description="选择的评测指标，如 ['latency', 'task_completion', 'tool_correctness']")


class EvaluationTaskUpdate(BaseModel):
    name: Optional[str] = None
    description: Optional[str] = None
    agent_url: Optional[str] = None
    agent_version: Optional[str] = None
    dataset_id: Optional[int] = None
    metrics: Optional[List[str]] = None


class EvaluationTaskOut(BaseModel):
    id: int
    name: str
    description: Optional[str]
    agent_url: str
    agent_version: Optional[str]
    dataset_id: int
    metrics: List[str]
    status: TaskStatus
    result_summary: Optional[Any]
    error_message: Optional[str]
    created_at: datetime
    updated_at: datetime
    started_at: Optional[datetime]
    finished_at: Optional[datetime]

    class Config:
        from_attributes = True


class EvaluationRecordOut(BaseModel):
    id: int
    task_id: int
    input: str
    expected_output: Optional[str]
    actual_output: Optional[str]
    steps: Optional[Any]
    latency_ms: Optional[int]
    token_count: Optional[int]
    tool_calls: Optional[Any]
    metric_scores: Optional[Any]
    created_at: datetime

    class Config:
        from_attributes = True
