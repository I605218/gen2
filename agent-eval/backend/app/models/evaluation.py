from sqlalchemy import Column, Integer, String, Text, DateTime, Float, Enum, JSON
from sqlalchemy.sql import func
from app.db.database import Base
import enum


class TaskStatus(str, enum.Enum):
    pending = "pending"
    running = "running"
    completed = "completed"
    failed = "failed"


class EvaluationTask(Base):
    """评测任务表"""
    __tablename__ = "evaluation_tasks"

    id = Column(Integer, primary_key=True, index=True, autoincrement=True)
    name = Column(String(128), nullable=False, comment="任务名称")
    description = Column(Text, nullable=True, comment="任务描述")
    agent_url = Column(String(512), nullable=False, comment="Agent API 地址")
    agent_version = Column(String(64), nullable=True, comment="Agent 版本标识")
    dataset_id = Column(Integer, nullable=False, comment="关联数据集 ID")
    metrics = Column(JSON, nullable=False, comment="选择的评测指标列表")
    status = Column(
        Enum(TaskStatus), default=TaskStatus.pending, nullable=False, comment="任务状态"
    )
    result_summary = Column(JSON, nullable=True, comment="评测结果汇总")
    error_message = Column(Text, nullable=True, comment="失败时的错误信息")
    created_at = Column(DateTime, server_default=func.now(), comment="创建时间")
    updated_at = Column(
        DateTime, server_default=func.now(), onupdate=func.now(), comment="更新时间"
    )
    started_at = Column(DateTime, nullable=True, comment="开始执行时间")
    finished_at = Column(DateTime, nullable=True, comment="完成时间")


class EvaluationRecord(Base):
    """单条评测记录表（每个测试用例的结果）"""
    __tablename__ = "evaluation_records"

    id = Column(Integer, primary_key=True, index=True, autoincrement=True)
    task_id = Column(Integer, nullable=False, index=True, comment="所属任务 ID")
    input = Column(Text, nullable=False, comment="输入问题")
    expected_output = Column(Text, nullable=True, comment="预期答案（可选）")
    actual_output = Column(Text, nullable=True, comment="Agent 实际输出")
    steps = Column(JSON, nullable=True, comment="Agent 中间步骤记录")
    latency_ms = Column(Integer, nullable=True, comment="响应时间（毫秒）")
    token_count = Column(Integer, nullable=True, comment="Token 消耗")
    tool_calls = Column(JSON, nullable=True, comment="工具调用记录")
    metric_scores = Column(JSON, nullable=True, comment="各指标得分")
    created_at = Column(DateTime, server_default=func.now())
