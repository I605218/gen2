from fastapi import APIRouter, Depends, HTTPException, BackgroundTasks
from sqlalchemy.orm import Session
from typing import List

from app.db.database import get_db
from app.models.evaluation import EvaluationTask, EvaluationRecord, TaskStatus
from app.schemas.evaluation import (
    EvaluationTaskCreate,
    EvaluationTaskUpdate,
    EvaluationTaskOut,
    EvaluationRecordOut,
)
from app.services.evaluator import run_evaluation

router = APIRouter(prefix="/tasks", tags=["评测任务"])


@router.get("", response_model=List[EvaluationTaskOut])
def list_tasks(db: Session = Depends(get_db)):
    return db.query(EvaluationTask).order_by(EvaluationTask.created_at.desc()).all()


@router.post("", response_model=EvaluationTaskOut, status_code=201)
def create_task(payload: EvaluationTaskCreate, db: Session = Depends(get_db)):
    task = EvaluationTask(**payload.model_dump())
    db.add(task)
    db.commit()
    db.refresh(task)
    return task


@router.get("/{task_id}", response_model=EvaluationTaskOut)
def get_task(task_id: int, db: Session = Depends(get_db)):
    task = db.query(EvaluationTask).filter(EvaluationTask.id == task_id).first()
    if not task:
        raise HTTPException(status_code=404, detail="任务不存在")
    return task


@router.put("/{task_id}", response_model=EvaluationTaskOut)
def update_task(task_id: int, payload: EvaluationTaskUpdate, db: Session = Depends(get_db)):
    task = db.query(EvaluationTask).filter(EvaluationTask.id == task_id).first()
    if not task:
        raise HTTPException(status_code=404, detail="任务不存在")
    if task.status == TaskStatus.running:
        raise HTTPException(status_code=400, detail="任务运行中，无法修改")
    for field, value in payload.model_dump(exclude_none=True).items():
        setattr(task, field, value)
    db.commit()
    db.refresh(task)
    return task


@router.delete("/{task_id}", status_code=204)
def delete_task(task_id: int, db: Session = Depends(get_db)):
    task = db.query(EvaluationTask).filter(EvaluationTask.id == task_id).first()
    if not task:
        raise HTTPException(status_code=404, detail="任务不存在")
    if task.status == TaskStatus.running:
        raise HTTPException(status_code=400, detail="任务运行中，无法删除")
    db.query(EvaluationRecord).filter(EvaluationRecord.task_id == task_id).delete()
    db.delete(task)
    db.commit()


@router.post("/{task_id}/run", response_model=EvaluationTaskOut)
def start_task(
    task_id: int,
    background_tasks: BackgroundTasks,
    db: Session = Depends(get_db),
):
    task = db.query(EvaluationTask).filter(EvaluationTask.id == task_id).first()
    if not task:
        raise HTTPException(status_code=404, detail="任务不存在")
    if task.status == TaskStatus.running:
        raise HTTPException(status_code=400, detail="任务已在运行中")

    # 重置状态
    task.status = TaskStatus.pending
    task.error_message = None
    task.result_summary = None
    db.commit()

    # 后台执行，传入新 db session
    from app.db.database import SessionLocal
    bg_db = SessionLocal()

    async def _run():
        try:
            await run_evaluation(task_id, bg_db)
        finally:
            bg_db.close()

    background_tasks.add_task(_run)
    db.refresh(task)
    return task


@router.get("/{task_id}/records", response_model=List[EvaluationRecordOut])
def get_records(task_id: int, db: Session = Depends(get_db)):
    return (
        db.query(EvaluationRecord)
        .filter(EvaluationRecord.task_id == task_id)
        .order_by(EvaluationRecord.id)
        .all()
    )


@router.get("/compare/summary")
def compare_tasks(task_ids: str, db: Session = Depends(get_db)):
    """对比多个任务的汇总结果，task_ids 以逗号分隔，如 1,2,3"""
    ids = [int(i) for i in task_ids.split(",") if i.strip().isdigit()]
    tasks = db.query(EvaluationTask).filter(EvaluationTask.id.in_(ids)).all()
    return [
        {
            "id": t.id,
            "name": t.name,
            "agent_version": t.agent_version,
            "status": t.status,
            "result_summary": t.result_summary,
            "finished_at": t.finished_at,
        }
        for t in tasks
    ]
