from sqlalchemy import Column, Integer, String, Text, DateTime, JSON
from sqlalchemy.sql import func
from app.db.database import Base


class Dataset(Base):
    """数据集表"""
    __tablename__ = "datasets"

    id = Column(Integer, primary_key=True, index=True, autoincrement=True)
    name = Column(String(128), nullable=False, comment="数据集名称")
    description = Column(Text, nullable=True, comment="数据集描述")
    items = Column(JSON, nullable=False, comment="测试用例列表")
    created_at = Column(DateTime, server_default=func.now())
    updated_at = Column(DateTime, server_default=func.now(), onupdate=func.now())
