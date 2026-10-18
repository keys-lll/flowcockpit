"""FlowCockpit E：可選的雲端比較服務。沒有 API Key 或斷網時安全回傳 local_only。"""

from typing import Any

from fastapi import FastAPI
from pydantic import BaseModel, Field

from cloud_enhance import MODEL_NAME, get_comparison_result


class CompareRequest(BaseModel):
    task_id: str
    base_version: int = Field(ge=0)
    candidates: list[dict[str, Any]]
    conditions: dict[str, Any]
    raw_text: str | None = None


app = FastAPI(title="FlowCockpit Cloud Enhancement Module E")


@app.get("/health")
def health():
    import os
    key_present = bool(os.getenv("ANTHROPIC_API_KEY"))
    return {
        "status": "configured" if key_present else "offline_only",
        "model": MODEL_NAME,
    }


@app.post("/api/compare")
def compare(request: CompareRequest):
    text, status = get_comparison_result(request.candidates, request.conditions, request.raw_text)
    if status == "offline_only":
        status = "local_only"
    return {
        "task_id": request.task_id,
        "base_version": request.base_version,
        "comparison_text": text,
        "status": status,
    }
