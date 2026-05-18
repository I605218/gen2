from fastapi import APIRouter
from app.services.metrics import AVAILABLE_METRICS

router = APIRouter(prefix="/metrics", tags=["评测指标"])


@router.get("")
def list_metrics():
    """返回所有可用的评测指标"""
    return [
        {"key": k, "label": v}
        for k, v in AVAILABLE_METRICS.items()
    ]
