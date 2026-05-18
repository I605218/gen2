from pydantic import BaseModel, Field
from typing import Optional, List, Any
from datetime import datetime


class DatasetItem(BaseModel):
    input: str = Field(..., description="测试问题")
    expected_output: Optional[str] = Field(None, description="预期答案（可选，用于对比评估）")
    expected_tools: Optional[List[str]] = Field(None, description="预期调用的工具列表（可选）")


class DatasetCreate(BaseModel):
    name: str = Field(..., max_length=128)
    description: Optional[str] = None
    items: List[DatasetItem]


class DatasetUpdate(BaseModel):
    name: Optional[str] = None
    description: Optional[str] = None
    items: Optional[List[DatasetItem]] = None


class DatasetOut(BaseModel):
    id: int
    name: str
    description: Optional[str]
    items: Any
    created_at: datetime
    updated_at: datetime

    class Config:
        from_attributes = True
