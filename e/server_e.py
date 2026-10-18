"""Optional HTTP service that adds a Claude comparison to D's local candidates."""

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

    return {
        "status": "configured" if os.getenv("ANTHROPIC_API_KEY") else "offline_only",
        "model": MODEL_NAME,
    }


@app.post("/api/compare")
def compare(request: CompareRequest):
    text, status = get_comparison_result(request.candidates, request.conditions, request.raw_text)
    return {
        "task_id": request.task_id,
        "base_version": request.base_version,
        "comparison_text": text,
        "status": status,
    }
