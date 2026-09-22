from __future__ import annotations

import io
import json
import os
import random
import secrets
import math
import html
import threading
from functools import lru_cache
import httpx
from datetime import datetime, timedelta, timezone
from pathlib import Path
from typing import Any, Literal

from fastapi import Depends, FastAPI, Header, HTTPException, Query, Request
from fastapi.responses import FileResponse, HTMLResponse, Response
from fastapi.staticfiles import StaticFiles
from pydantic import BaseModel, Field, model_validator
from reportlab.lib import colors
from reportlab.lib.pagesizes import A4, landscape
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.pdfgen import canvas
from sqlalchemy import Boolean, DateTime, Float, ForeignKey, Integer, String, Text, UniqueConstraint, create_engine, func, select, text
from sqlalchemy.orm import DeclarativeBase, Mapped, Session, mapped_column, relationship, sessionmaker

ROOT = Path(__file__).resolve().parent
DATA = ROOT / "data"
DATA.mkdir(exist_ok=True)
DB_URL = os.getenv("MODULE5_DB_URL", f"sqlite:///{(DATA / 'module5.db').as_posix()}")

# 试卷总分统一上限：整份试卷分值合计不可超过 100 分
PAPER_TOTAL_LIMIT = 100
# 资源中心（模块一）考试题库与本地题型的映射
QTYPE_TO_PLATFORM = {"single": 1, "multi": 2, "judge": 3, "essay": 4, "case": 5, "blank": 6, "short": 7}
QTYPE_FROM_PLATFORM = {v: k for k, v in QTYPE_TO_PLATFORM.items()}
DIFF_TO_PLATFORM = {"easy": 1, "medium": 2, "hard": 3}
DIFF_FROM_PLATFORM = {v: k for k, v in DIFF_TO_PLATFORM.items()}
PLATFORM_PAGE_SIZE = 200
PLATFORM_MAX_PAGES = 10



class Base(DeclarativeBase):
    pass


def now() -> datetime:
    return datetime.now(timezone.utc).replace(tzinfo=None)


class User(Base):
    __tablename__ = "users"
    id: Mapped[int] = mapped_column(primary_key=True)
    name: Mapped[str] = mapped_column(String(80))
    role: Mapped[str] = mapped_column(String(20), index=True)


class Question(Base):
    """试题。

    组卷使用的题目来自模块一资源中心的考试题库，本地保存一份镜像快照
    （platform_id 指向平台试题 ID），供排考快照、阅卷、错题本使用。
    """
    __tablename__ = "questions"
    id: Mapped[int] = mapped_column(primary_key=True)
    platform_id: Mapped[int | None] = mapped_column(Integer, index=True)
    q_type: Mapped[str] = mapped_column(String(20), index=True)
    stem: Mapped[str] = mapped_column(Text)
    options: Mapped[str | None] = mapped_column(Text)
    answer: Mapped[str] = mapped_column(Text)
    analysis: Mapped[str | None] = mapped_column(Text)
    tags: Mapped[str] = mapped_column(Text, default="{}")
    difficulty: Mapped[str] = mapped_column(String(10), default="medium")
    status: Mapped[str] = mapped_column(String(20), default="draft", index=True)
    creator_id: Mapped[int] = mapped_column(ForeignKey("users.id"))
    created_at: Mapped[datetime] = mapped_column(DateTime, default=now)
    updated_at: Mapped[datetime] = mapped_column(DateTime, default=now, onupdate=now)


class QuestionReview(Base):
    __tablename__ = "question_reviews"
    id: Mapped[int] = mapped_column(primary_key=True)
    question_id: Mapped[int] = mapped_column(ForeignKey("questions.id"), index=True)
    action: Mapped[str] = mapped_column(String(10))
    opinion: Mapped[str | None] = mapped_column(Text)
    operator_id: Mapped[int] = mapped_column(ForeignKey("users.id"))
    created_at: Mapped[datetime] = mapped_column(DateTime, default=now)


class Paper(Base):
    __tablename__ = "papers"
    id: Mapped[int] = mapped_column(primary_key=True)
    name: Mapped[str] = mapped_column(String(200))
    course_id: Mapped[int | None] = mapped_column(Integer)
    exam_type: Mapped[str] = mapped_column(String(20), default="course_exam")
    status: Mapped[str] = mapped_column(String(20), default="draft")
    total_score: Mapped[float] = mapped_column(Float, default=0)
    pass_score: Mapped[float] = mapped_column(Float, default=60)
    duration_min: Mapped[int] = mapped_column(Integer, default=60)
    retry_limit: Mapped[int] = mapped_column(Integer, default=0)
    anti_switch: Mapped[bool] = mapped_column(Boolean, default=True)
    auto_rule: Mapped[str | None] = mapped_column(Text)
    cert_enabled: Mapped[bool] = mapped_column(Boolean, default=True)
    cert_type: Mapped[str] = mapped_column(String(20), default="graduate")
    course_name: Mapped[str] = mapped_column(String(200), default="")
    # 适用工种（选项来自资源中心标签管理的工种）与考试通过发放的工种资格证
    work_types: Mapped[str | None] = mapped_column(Text, default="[]")
    cert_names: Mapped[str | None] = mapped_column(Text, default="[]")
    created_by: Mapped[int] = mapped_column(ForeignKey("users.id"))
    created_at: Mapped[datetime] = mapped_column(DateTime, default=now)
    questions: Mapped[list[PaperQuestion]] = relationship(back_populates="paper", cascade="all, delete-orphan")


class PaperQuestion(Base):
    __tablename__ = "paper_questions"
    __table_args__ = (UniqueConstraint("paper_id", "question_id"),)
    id: Mapped[int] = mapped_column(primary_key=True)
    paper_id: Mapped[int] = mapped_column(ForeignKey("papers.id"), index=True)
    question_id: Mapped[int] = mapped_column(ForeignKey("questions.id"))
    sort_no: Mapped[int] = mapped_column(Integer)
    score: Mapped[float] = mapped_column(Float)
    paper: Mapped[Paper] = relationship(back_populates="questions")


class ExamSchedule(Base):
    __tablename__ = "exam_schedules"
    id: Mapped[int] = mapped_column(primary_key=True)
    name: Mapped[str] = mapped_column(String(200))
    paper_id: Mapped[int] = mapped_column(ForeignKey("papers.id"))
    start_at: Mapped[datetime] = mapped_column(DateTime)
    end_at: Mapped[datetime] = mapped_column(DateTime)
    scope_type: Mapped[str] = mapped_column(String(10), default="roster")
    scope_detail: Mapped[str] = mapped_column(Text, default="{}")
    status: Mapped[str] = mapped_column(String(20), default="draft")
    created_by: Mapped[int] = mapped_column(ForeignKey("users.id"))
    created_at: Mapped[datetime] = mapped_column(DateTime, default=now)


class ExamInstanceQuestion(Base):
    __tablename__ = "exam_instance_questions"
    __table_args__ = (UniqueConstraint("schedule_id", "question_id"),)
    id: Mapped[int] = mapped_column(primary_key=True)
    schedule_id: Mapped[int] = mapped_column(ForeignKey("exam_schedules.id"), index=True)
    question_id: Mapped[int] = mapped_column(Integer)
    q_type: Mapped[str] = mapped_column(String(20))
    stem: Mapped[str] = mapped_column(Text)
    options: Mapped[str | None] = mapped_column(Text)
    answer: Mapped[str] = mapped_column(Text)
    analysis: Mapped[str | None] = mapped_column(Text)
    sort_no: Mapped[int] = mapped_column(Integer)
    score: Mapped[float] = mapped_column(Float)


class LearnerExamTask(Base):
    __tablename__ = "learner_exam_tasks"
    __table_args__ = (UniqueConstraint("schedule_id", "user_id"),)
    id: Mapped[int] = mapped_column(primary_key=True)
    schedule_id: Mapped[int] = mapped_column(ForeignKey("exam_schedules.id"), index=True)
    user_id: Mapped[int] = mapped_column(ForeignKey("users.id"), index=True)
    status: Mapped[str] = mapped_column(String(20), default="not_open")
    allow_start_at: Mapped[datetime] = mapped_column(DateTime)
    attempt_used: Mapped[int] = mapped_column(Integer, default=0)


class ExamAttempt(Base):
    __tablename__ = "exam_attempts"
    id: Mapped[int] = mapped_column(primary_key=True)
    task_id: Mapped[int] = mapped_column(ForeignKey("learner_exam_tasks.id"), index=True)
    user_id: Mapped[int] = mapped_column(ForeignKey("users.id"), index=True)
    schedule_id: Mapped[int] = mapped_column(ForeignKey("exam_schedules.id"))
    started_at: Mapped[datetime] = mapped_column(DateTime, default=now)
    deadline_at: Mapped[datetime] = mapped_column(DateTime)
    submitted_at: Mapped[datetime | None] = mapped_column(DateTime)
    submit_mode: Mapped[str | None] = mapped_column(String(10))
    objective_score: Mapped[float] = mapped_column(Float, default=0)
    subjective_score: Mapped[float] = mapped_column(Float, default=0)
    total_score: Mapped[float] = mapped_column(Float, default=0)
    is_pass: Mapped[bool | None] = mapped_column(Boolean)
    switch_count: Mapped[int] = mapped_column(Integer, default=0)
    status: Mapped[str] = mapped_column(String(20), default="in_progress", index=True)
    graded_by: Mapped[int | None] = mapped_column(Integer)
    graded_at: Mapped[datetime | None] = mapped_column(DateTime)


class ExamAnswer(Base):
    __tablename__ = "exam_answers"
    __table_args__ = (UniqueConstraint("attempt_id", "question_id"),)
    id: Mapped[int] = mapped_column(primary_key=True)
    attempt_id: Mapped[int] = mapped_column(ForeignKey("exam_attempts.id"), index=True)
    question_id: Mapped[int] = mapped_column(Integer)
    user_answer: Mapped[str] = mapped_column(Text, default="null")
    is_correct: Mapped[bool | None] = mapped_column(Boolean)
    score_awarded: Mapped[float | None] = mapped_column(Float)
    judge_comment: Mapped[str | None] = mapped_column(Text)


class WrongQuestion(Base):
    __tablename__ = "wrong_questions"
    __table_args__ = (UniqueConstraint("user_id", "question_id"),)
    id: Mapped[int] = mapped_column(primary_key=True)
    user_id: Mapped[int] = mapped_column(ForeignKey("users.id"), index=True)
    question_id: Mapped[int] = mapped_column(Integer)
    course_id: Mapped[int | None] = mapped_column(Integer)
    q_type: Mapped[str] = mapped_column(String(20))
    source_attempt_id: Mapped[int] = mapped_column(Integer)
    mastered: Mapped[bool] = mapped_column(Boolean, default=False)
    wrong_at: Mapped[datetime] = mapped_column(DateTime, default=now)
    mastered_at: Mapped[datetime | None] = mapped_column(DateTime)


class Certificate(Base):
    __tablename__ = "certificates"
    __table_args__ = (UniqueConstraint("attempt_id"),)
    id: Mapped[int] = mapped_column(primary_key=True)
    cert_no: Mapped[str] = mapped_column(String(60), unique=True, index=True)
    user_id: Mapped[int] = mapped_column(ForeignKey("users.id"), index=True)
    attempt_id: Mapped[int] = mapped_column(ForeignKey("exam_attempts.id"))
    cert_type: Mapped[str] = mapped_column(String(20))
    title: Mapped[str] = mapped_column(String(200))
    course_id: Mapped[int | None] = mapped_column(Integer)
    score: Mapped[float] = mapped_column(Float)
    issued_at: Mapped[datetime] = mapped_column(DateTime, default=now)
    status: Mapped[str] = mapped_column(String(20), default="valid")
    # 企业模板快照字段（老库由 ensure_columns 补列）
    course_name: Mapped[str] = mapped_column(String(200), default="")
    course_category: Mapped[str] = mapped_column(String(200), default="")
    business_tag: Mapped[str] = mapped_column(String(200), default="")
    study_hours: Mapped[str] = mapped_column(String(50), default="")
    work_type_name: Mapped[str] = mapped_column(String(100), default="")
    skill_level: Mapped[str] = mapped_column(String(50), default="")
    relate_courses: Mapped[str] = mapped_column(String(500), default="")
    paper_name: Mapped[str] = mapped_column(String(200), default="")


CERT_TYPE_NAMES = {"graduate": "结业证书", "competence": "岗位能力认证证书", "joint": "联合认证", "worktype": "工种资格证书"}


class CertIssuer(Base):
    __tablename__ = "cert_issuers"
    id: Mapped[int] = mapped_column(primary_key=True)
    name: Mapped[str] = mapped_column(String(100))
    api_url: Mapped[str] = mapped_column(String(300), default="")
    api_key: Mapped[str] = mapped_column(String(200), default="")
    cert_type_map: Mapped[str] = mapped_column(String(100), default="joint")
    issue_on_pass: Mapped[bool] = mapped_column(Boolean, default=True)
    enabled: Mapped[bool] = mapped_column(Boolean, default=True)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=now)


class CertSyncLog(Base):
    __tablename__ = "cert_sync_logs"
    __table_args__ = (UniqueConstraint("cert_id", "issuer_id"),)
    id: Mapped[int] = mapped_column(primary_key=True)
    cert_id: Mapped[int] = mapped_column(ForeignKey("certificates.id"))
    issuer_id: Mapped[int] = mapped_column(ForeignKey("cert_issuers.id"))
    status: Mapped[str] = mapped_column(String(20))
    ext_cert_no: Mapped[str | None] = mapped_column(String(100))
    request_body: Mapped[str] = mapped_column(Text)
    response: Mapped[str] = mapped_column(Text)
    synced_at: Mapped[datetime] = mapped_column(DateTime, default=now)


class Actor(BaseModel):
    id: int
    role: str
    name: str = ""
    tenant_id: int
    super_admin: bool = False
    token: str = ""

PLATFORM_URL = os.getenv("PLATFORM_URL", "http://gateway:48080/admin-api").rstrip("/")

def platform_get(path, token, tenant_id):
    try:
        r = httpx.get(PLATFORM_URL + path, headers={"Authorization": token, "tenant-id": str(tenant_id)}, timeout=10)
        payload = r.json()
    except (httpx.HTTPError, ValueError):
        raise HTTPException(503, "平台身份服务暂时不可用")
    if r.status_code != 200 or payload.get("code") != 0:
        raise HTTPException(401 if payload.get("code") == 401 or r.status_code == 401 else 403, "登录已过期或没有访问权限")
    return payload.get("data")


def platform_request(method, path, token, tenant_id, body: dict | None = None):
    """调用平台的写接口（新增用 POST、更新用 PUT）。"""
    try:
        r = httpx.request(method, PLATFORM_URL + path, json=body if body is not None else {}, headers={"Authorization": token, "tenant-id": str(tenant_id)}, timeout=15)
        payload = r.json()
    except (httpx.HTTPError, ValueError):
        raise HTTPException(503, "平台服务暂时不可用")
    if r.status_code != 200 or payload.get("code") != 0:
        raise HTTPException(422, payload.get("msg") or payload.get("message") or "平台接口调用失败")
    return payload.get("data")


def platform_post(path, token, tenant_id, body: dict | None = None):
    """调用平台的 POST 写接口（创建/停用试题等）。"""
    return platform_request("POST", path, token, tenant_id, body)


def platform_put(path, token, tenant_id, body: dict | None = None):
    """调用平台的 PUT 写接口（更新试题）。"""
    return platform_request("PUT", path, token, tenant_id, body)


def current_user(authorization: str = Header(""), tenant_id: int = Header(...)):
    if not authorization.startswith("Bearer ") or len(authorization)<10:
        raise HTTPException(401, "请先登录平台")
    if tenant_id <= 0: raise HTTPException(403, "企业信息无效")
    info = platform_get("/system/auth/get-permission-info", authorization, tenant_id)
    user = info.get("user") or {}
    if not user.get("id"): raise HTTPException(401, "无法确认用户身份")
    if user.get("tenantId") is not None and int(user["tenantId"]) != tenant_id:
        raise HTTPException(403, "用户不属于当前企业")
    roles, permissions = set(info.get("roles") or []), set(info.get("permissions") or [])
    super_admin = "super_admin" in roles or "*:*:*" in permissions
    role = "learner"
    if super_admin or "training:exam:manage" in permissions or ("tenant_admin" in roles and "training:exam:query" in permissions): role = "admin"
    elif "training:exam:review" in permissions: role = "reviewer"
    elif "training:exam:author" in permissions: role = "teacher"
    elif "training:exam:query" in permissions: role = "viewer"
    return Actor(id=user["id"], name=user.get("nickname") or user.get("username") or str(user["id"]), role=role, tenant_id=tenant_id, super_admin=super_admin, token=authorization)

def ensure_columns(engine, table: str, columns: dict[str, str]):
    """SQLite 轻量迁移：为历史库补充新增列（create_all 不会改已有表）。"""
    with engine.connect() as conn:
        existing = {row[1] for row in conn.execute(text(f"PRAGMA table_info({table})"))}
        for name, ddl in columns.items():
            if name not in existing:
                conn.execute(text(f"ALTER TABLE {table} ADD COLUMN {ddl}"))
        conn.commit()


@lru_cache(maxsize=256)
def tenant_engine(tenant_id):
    eng=create_engine(f"sqlite:///{(DATA / ('tenant-' + str(tenant_id) + '.db')).as_posix()}", connect_args={"check_same_thread": False, "timeout":30})
    Base.metadata.create_all(eng)
    ensure_columns(eng, "questions", {"platform_id": "platform_id INTEGER"})
    ensure_columns(eng, "papers", {"work_types": "work_types TEXT DEFAULT '[]'", "cert_names": "cert_names TEXT DEFAULT '[]'"})
    # 证书企业模板快照字段（发证时存快照，课程改名后老证书保持原样）
    ensure_columns(eng, "certificates", {
        "course_name": "course_name VARCHAR(200) DEFAULT ''",
        "course_category": "course_category VARCHAR(200) DEFAULT ''",
        "business_tag": "business_tag VARCHAR(200) DEFAULT ''",
        "study_hours": "study_hours VARCHAR(50) DEFAULT ''",
        "work_type_name": "work_type_name VARCHAR(100) DEFAULT ''",
        "skill_level": "skill_level VARCHAR(50) DEFAULT ''",
        "relate_courses": "relate_courses VARCHAR(500) DEFAULT ''",
        "paper_name": "paper_name VARCHAR(200) DEFAULT ''",
    })
    return eng

def db_session(actor: Actor = Depends(current_user)):
    with Session(tenant_engine(actor.tenant_id), expire_on_commit=False) as db:
        user=db.get(User,actor.id)
        if user is None:
            db.add(User(id=actor.id,name=actor.name,role=actor.role)); db.commit()
        elif user.name != actor.name or user.role != actor.role:
            user.name,user.role=actor.name,actor.role; db.commit()
        yield db


def require(*roles: str):
    def dep(actor: Actor = Depends(current_user)) -> Actor:
        if actor.role not in roles and "learner" not in roles:
            raise HTTPException(403, "无权执行此操作")
        return actor
    return dep


def ok(data: Any = None, message: str = "操作成功"):
    return {"code": 0, "message": message, "data": data}


def parse(value: str | None, fallback: Any = None):
    try:
        return json.loads(value) if value is not None else fallback
    except json.JSONDecodeError:
        return fallback


def options_from_platform(raw) -> list[dict[str, str]]:
    rows = raw if isinstance(raw, list) else (parse(raw, []) if isinstance(raw, str) else [])
    result = []
    for item in rows:
        if not isinstance(item, dict):
            continue
        key = str(item.get("key") or "").strip()
        if key:
            result.append({"key": key, "text": str(item.get("text") or "")})
    return result


def platform_question_view(item: dict, include_answer=False) -> dict:
    """把资源中心（模块一）考试题库的试题转换为本地视图结构。"""
    qtype = QTYPE_FROM_PLATFORM.get(int(item.get("qtype") or 1), "single")
    raw_answer = (item.get("answer") or "").strip()
    raw_options = item.get("optionsJson")
    if qtype in {"single", "multi"}:
        options = options_from_platform(raw_options)
        valid = {o["key"] for o in options}
        keys = [k for k in [x.strip() for x in raw_answer.replace("，", ",").split(",")] if k in valid]
        if not keys:  # 兼容答案缺失、仅标记 isCorrect 的历史数据
            rows = raw_options if isinstance(raw_options, list) else parse(raw_options, [])
            keys = [str(o.get("key")) for o in rows if isinstance(o, dict) and o.get("isCorrect") and str(o.get("key")) in valid]
        answer, usable = keys, bool(keys) and len(options) >= 2
    elif qtype == "judge":
        options = None
        answer = [not raw_answer.startswith("错")] if raw_answer else []
        usable = bool(raw_answer)
    else:
        options = None
        answer = {"points": raw_answer}
        usable = bool(raw_answer)
    tag_names = [x.strip() for x in (item.get("tagNames") or "").replace("，", ",").split(",") if x.strip()]
    data = {
        "id": item.get("id"),
        "platform_id": item.get("id"),
        "q_type": qtype,
        "category_id": item.get("categoryId"),
        "stem": item.get("stem") or "",
        "options": options,
        "tags": {"tag_names": tag_names},
        "difficulty": DIFF_FROM_PLATFORM.get(int(item.get("difficulty") or 2), "medium"),
        "status": "enabled" if int(item.get("status") or 1) == 1 else "disabled",
        "analysis": item.get("analysis") or "",
        "usable": usable,
        "source": "platform",
    }
    if include_answer:
        data["answer"] = answer
    return data


def platform_question_page(actor, qtype: int | None = None, difficulty: int | None = None, status: int | None = None) -> list[dict]:
    """分页拉取资源中心考试题库（返回已转换的视图列表）。"""
    rows: list[dict] = []
    page = 1
    while page <= PLATFORM_MAX_PAGES:
        path = f"/training/question/page?pageNo={page}&pageSize={PLATFORM_PAGE_SIZE}"
        if qtype:
            path += f"&qtype={qtype}"
        if difficulty:
            path += f"&difficulty={difficulty}"
        if status:
            path += f"&status={status}"
        data = platform_get(path, actor.token, actor.tenant_id) or {}
        batch = data.get("list") or []
        rows += [platform_question_view(x, True) for x in batch if isinstance(x, dict)]
        if len(batch) < PLATFORM_PAGE_SIZE or page * PLATFORM_PAGE_SIZE >= int(data.get("total") or 0):
            break
        page += 1
    return rows


def platform_question_total(actor) -> int:
    try:
        data = platform_get("/training/question/page?pageNo=1&pageSize=1", actor.token, actor.tenant_id) or {}
        return int(data.get("total") or 0)
    except HTTPException:
        return 0


def platform_tag_options(actor, dimension: str | None = None) -> list[dict]:
    path = "/training/resource-tag/list" + (f"?dimension={dimension}" if dimension else "")
    try:
        rows = platform_get(path, actor.token, actor.tenant_id) or []
    except HTTPException:
        return []
    return [{"id": x.get("id"), "name": str(x.get("name") or "")} for x in rows if isinstance(x, dict)]


def resolve_tag_ids(actor, tags: dict | None) -> list[int]:
    """把标签名称解析为平台标签 ID（匹配不到的忽略）。"""
    names: list[str] = []
    for value in (tags or {}).values():
        if isinstance(value, list):
            names += [str(x).strip() for x in value if str(x).strip()]
    if not names:
        return []
    lookup = {t["name"]: t["id"] for t in platform_tag_options(actor) if t["name"]}
    return [lookup[name] for name in names if name in lookup]


def platform_category_options(actor) -> list[dict]:
    """资源分类（question 类型）扁平化选项：[{id, name, path}]，供题库搜索「按所属分类」使用。"""
    path = "/training/resource-category/tree?type=question"
    try:
        tree = platform_get(path, actor.token, actor.tenant_id) or []
    except HTTPException:
        return []
    result: list[dict] = []

    def walk(nodes, chain):
        for node in nodes or []:
            if not isinstance(node, dict):
                continue
            name = str(node.get("name") or "").strip()
            if node.get("id") is not None:
                result.append({
                    "id": node["id"],
                    "name": name,
                    "path": " / ".join([*chain, name] if name else chain),
                })
            walk(node.get("children"), [*chain, name] if name else chain)

    walk(tree, [])
    return result


def question_payload(body: QuestionIn, actor) -> dict:
    """把本地试题模型转换为平台（资源中心考试题库）创建/更新接口的入参。"""
    payload = {
        "qtype": QTYPE_TO_PLATFORM.get(body.q_type, 1),
        "stem": body.stem,
        "analysis": body.analysis or "",
        "difficulty": DIFF_TO_PLATFORM.get(body.difficulty, 2),
    }
    if body.q_type in {"single", "multi"}:
        chosen = [str(x) for x in body.answer] if isinstance(body.answer, list) else []
        payload["optionsJson"] = json.dumps(
            [{"key": o["key"], "text": o.get("text", ""), "isCorrect": o["key"] in chosen} for o in (body.options or [])],
            ensure_ascii=False,
        )
        payload["answer"] = ",".join(chosen)
    elif body.q_type == "judge":
        payload["answer"] = "对" if (isinstance(body.answer, list) and body.answer and bool(body.answer[0])) else "错"
    else:
        payload["answer"] = (body.answer or {}).get("points", "") if isinstance(body.answer, dict) else str(body.answer or "")
    tag_ids = resolve_tag_ids(actor, body.tags)
    if tag_ids:
        payload["tagIds"] = tag_ids
    return payload


def sync_platform_question(db: Session, actor, platform_qid: int) -> Question:
    """把平台试题同步为本地镜像，供组卷、排考快照、阅卷、错题本使用。"""
    qid = int(platform_qid)
    item = platform_get(f"/training/question/get?id={qid}", actor.token, actor.tenant_id)
    if not item:
        raise HTTPException(404, "资源中心考试题库中不存在该试题")
    view = platform_question_view(item, True)
    if view["status"] != "enabled":
        raise HTTPException(422, "该试题在资源中心考试题库中已停用，不能用于组卷")
    if not view["usable"]:
        raise HTTPException(422, "该试题缺少有效答案，不能用于组卷，请先在资源中心补全")
    values = {
        "q_type": view["q_type"],
        "stem": view["stem"],
        "options": json.dumps(view["options"], ensure_ascii=False) if view["options"] is not None else None,
        "answer": json.dumps(view["answer"], ensure_ascii=False),
        "analysis": view["analysis"],
        "tags": json.dumps(view["tags"], ensure_ascii=False),
        "difficulty": view["difficulty"],
        "status": "enabled",
    }
    row = db.scalar(select(Question).where(Question.platform_id == qid))
    if row is None:
        row = Question(platform_id=qid, creator_id=actor.id, **values)
        db.add(row)
    else:
        for key, value in values.items():
            setattr(row, key, value)
    db.flush()
    return row


def paper_total(paper: Paper) -> float:
    return round(sum(q.score for q in paper.questions), 2)


class QuestionIn(BaseModel):
    q_type: Literal["single", "multi", "judge", "essay", "case"]
    stem: str = Field(min_length=2)
    options: list[dict[str, str]] | None = None
    answer: Any
    analysis: str | None = None
    tags: dict[str, list[Any]] = Field(default_factory=dict)
    difficulty: Literal["easy", "medium", "hard"] = "medium"

    @model_validator(mode="after")
    def validate_answer(self):
        if self.q_type == "single" and (not isinstance(self.answer, list) or len(self.answer) != 1):
            raise ValueError("单选题答案必须且只能包含一个选项")
        if self.q_type == "multi" and (not isinstance(self.answer, list) or len(self.answer) < 2):
            raise ValueError("多选题答案至少包含两个选项")
        if self.q_type == "judge" and (not isinstance(self.answer, list) or len(self.answer) != 1 or not isinstance(self.answer[0], bool)):
            raise ValueError("判断题答案必须是布尔值数组")
        if self.q_type in {"essay", "case"} and (not isinstance(self.answer, dict) or not self.answer.get("points")):
            raise ValueError("主观题必须提供评分要点")
        if self.q_type in {"single", "multi"}:
            opts=self.options or []
            keys=[x.get("key") for x in opts]
            if len(opts)<2 or len(keys)!=len(set(keys)) or any(not k for k in keys) or any(not x.get("text", "").strip() for x in opts):
                raise ValueError("选择题须提供至少两个不重复且非空的选项")
            if any(not isinstance(a,str) or a not in keys for a in self.answer) or len(set(self.answer))!=len(self.answer):
                raise ValueError("答案必须对应不重复的有效选项")
        return self


class PaperIn(BaseModel):
    name: str = Field(min_length=2, max_length=200)
    course_name: str = ""
    cert_type: Literal["graduate", "competence", "joint"] = "graduate"
    course_id: int | None = None
    exam_type: Literal["course_exam", "mock", "cert", "enterprise", "qualification"] = "course_exam"
    pass_score: float = Field(60, gt=0, allow_inf_nan=False)
    duration_min: int = Field(60, gt=0)
    retry_limit: int = Field(0, ge=0)
    anti_switch: bool = True
    cert_enabled: bool = True
    # 适用工种（可选，选项来自资源中心标签管理的工种）
    work_types: list[str] = Field(default_factory=list)
    # 考试通过发放的工种资格证（可选，选项来自工种资格证表）
    cert_names: list[str] = Field(default_factory=list)


class AutoPaperIn(PaperIn):
    sections: list[dict[str, Any]]
    tags: dict[str, list[Any]] = Field(default_factory=dict)
    difficulty: str | None = None


class ScheduleIn(BaseModel):
    name: str
    paper_id: int
    start_at: datetime
    end_at: datetime
    scope_type: Literal["roster", "class"] = "roster"
    class_id: int | None = None
    duration_min: int = Field(60, gt=0, le=1440)
    user_ids: list[int] = Field(default_factory=list)


class AnswerIn(BaseModel):
    question_id: int
    user_answer: Any


class GradeIn(BaseModel):
    answers: list[dict[str, Any]]


app = FastAPI(title="模块五 · 考试与认证", version="1.0.0")

import logging as _logging
_logger = _logging.getLogger("uvicorn.error")


@app.exception_handler(Exception)
async def _unhandled_exception_handler(request: Request, exc: Exception):
    """兜底：把任何未处理异常（含请求体）打进容器日志，便于定位前端提交问题。"""
    _logger.error("[unhandled] %s %s  body=%s", request.method, request.url.path, getattr(exc, "body", None) or "<see traceback>")
    import traceback as _tb
    _logger.error("".join(_tb.format_exception(exc)))
    from fastapi.responses import JSONResponse
    return JSONResponse(status_code=500, content={"detail": "服务内部错误，请稍后重试"})


def question_view(q: Question, include_answer=False):
    data = {"id": q.id, "platform_id": q.platform_id, "q_type": q.q_type, "stem": q.stem, "options": parse(q.options), "tags": parse(q.tags, {}), "difficulty": q.difficulty, "status": q.status, "creator_id": q.creator_id, "analysis": q.analysis, "source": "platform" if q.platform_id else "local"}
    if include_answer:
        data["answer"] = parse(q.answer)
    return data


@app.get("/api/dashboard")
def dashboard(db: Session = Depends(db_session), actor: Actor = Depends(require("admin", "teacher", "reviewer", "viewer"))):
    counts = {}
    for name, model in [("papers", Paper), ("schedules", ExamSchedule), ("attempts", ExamAttempt), ("certificates", Certificate)]:
        counts[name] = db.scalar(select(func.count()).select_from(model))
    # 题库统一来自资源中心（模块一）的考试题库
    counts["questions"] = platform_question_total(actor)
    counts["pending_grading"] = db.scalar(select(func.count()).select_from(ExamAttempt).where(ExamAttempt.status == "submitted"))
    return ok(counts)


@app.get("/api/questions")
def list_questions(status: str | None = None, q_type: str | None = None, keyword: str | None = None, difficulty: str | None = None, db: Session = Depends(db_session), actor: Actor = Depends(require("admin", "teacher", "reviewer", "viewer"))):
    """题库查询：直接查询资源中心（模块一）的考试题库，只读。"""
    platform_status = 1 if status == "enabled" else (2 if status == "disabled" else None)
    rows = platform_question_page(
        actor,
        qtype=QTYPE_TO_PLATFORM.get(q_type) if q_type else None,
        difficulty=DIFF_TO_PLATFORM.get(difficulty) if difficulty else None,
        status=platform_status,
    )
    if keyword:
        lowered = keyword.lower()
        rows = [r for r in rows if lowered in (r["stem"] + json.dumps(r["tags"], ensure_ascii=False)).lower()]
    # 附加分类路径（供「选题与分值」按所属分类搜索与列表展示）
    category_map = {c["id"]: c for c in platform_category_options(actor)}
    for row in rows:
        cid = row.get("category_id")
        row["category_name"] = category_map[cid]["path"] if cid in category_map else ""
    return ok(rows)


@app.get("/api/question-categories")
def question_category_options(actor: Actor = Depends(current_user)):
    """资源分类（question 类型）选项，供试卷「选题与分值」按所属分类搜索使用。"""
    return ok(platform_category_options(actor))


@app.post("/api/questions")
def create_question(body: QuestionIn, db: Session = Depends(db_session), actor: Actor = Depends(require("admin", "teacher"))):
    """新增试题：写入资源中心考试题库，可在本页与模块一资源中心的考试题库中查到。"""
    qid = platform_post("/training/question/create", actor.token, actor.tenant_id, question_payload(body, actor))
    return ok({"id": qid, "platform_id": qid}, "试题已保存至资源中心考试题库")


@app.post("/api/questions/{qid}/disable")
def disable_question(qid: int, db: Session = Depends(db_session), actor: Actor = Depends(require("admin", "teacher"))):
    """停用试题：同步停用资源中心考试题库中的对应试题。"""
    q = db.scalar(select(Question).where(Question.platform_id == qid))
    if not q:
        raise HTTPException(404, "本地不存在该试题镜像")
    platform_post(f"/training/question/status?id={qid}&action=DISABLE", actor.token, actor.tenant_id)
    q.status = "disabled"; db.commit(); return ok(message="试题已停用，既有考试快照不受影响")


def platform_course_name(actor, course_id: int | None) -> str:
    """按课程 ID 从课程管理取课程名称。"""
    if not course_id:
        return ""
    try:
        item = platform_get(f"/training/course/get?id={course_id}", actor.token, actor.tenant_id)
    except HTTPException:
        item = None
    if isinstance(item, dict):
        return str(item.get("title") or item.get("name") or "")
    return ""


def name_list(values: list[str] | None) -> list[str]:
    """去重去空白的名称列表（工种/资格证可能存在同名项）。"""
    result = []
    for value in values or []:
        text = str(value or "").strip()
        if text and text not in result:
            result.append(text)
    return result


def paper_view(p: Paper):
    return {"id": p.id, "name": p.name, "exam_type": p.exam_type, "status": p.status, "total_score": p.total_score, "pass_score": p.pass_score, "duration_min": p.duration_min, "question_count": len(p.questions), "retry_limit": p.retry_limit, "anti_switch": p.anti_switch, "cert_enabled": p.cert_enabled, "cert_type": p.cert_type, "course_name": p.course_name, "course_id": p.course_id, "work_types": parse(p.work_types, []), "cert_names": parse(p.cert_names, []), "score_limit": PAPER_TOTAL_LIMIT}


@app.get("/api/papers")
def list_papers(db: Session = Depends(db_session), actor: Actor = Depends(require("admin", "teacher", "reviewer", "viewer"))):
    return ok([paper_view(p) for p in db.scalars(select(Paper).order_by(Paper.id.desc()))])


@app.post("/api/papers")
def create_paper(body: PaperIn, db: Session = Depends(db_session), actor: Actor = Depends(require("admin", "teacher"))):
    data = body.model_dump()
    data["work_types"] = json.dumps(name_list(body.work_types), ensure_ascii=False)
    data["cert_names"] = json.dumps(name_list(body.cert_names), ensure_ascii=False)
    if not data.get("course_name") and body.course_id:
        data["course_name"] = platform_course_name(actor, body.course_id)
    p = Paper(**data, created_by=actor.id); db.add(p); db.commit(); return ok({"id": p.id}, "试卷草稿已创建")


@app.post("/api/papers/{pid}/questions")
def add_paper_questions(pid: int, items: list[dict[str, Any]], db: Session = Depends(db_session), actor: Actor = Depends(require("admin", "teacher"))):
    p = db.get(Paper, pid)
    if not p or p.status != "draft": raise HTTPException(409, "仅草稿试卷可编辑")
    existing = {x.question_id for x in p.questions}
    for item in items:
        if not item.get("question_id") or not math.isfinite(float(item.get("score", 0))) or float(item["score"]) <= 0:
            raise HTTPException(422, "分值必须大于零")
        q = sync_platform_question(db, actor, item["question_id"])
        if q.id in existing: raise HTTPException(409, f"试题 {q.platform_id or q.id} 重复")
        p.questions.append(PaperQuestion(question_id=q.id, sort_no=len(p.questions)+1, score=float(item["score"])))
        existing.add(q.id)
    db.flush()
    total = paper_total(p)
    if total > PAPER_TOTAL_LIMIT: raise HTTPException(422, f"试卷总分不能超过 {PAPER_TOTAL_LIMIT} 分，当前合计 {total:g} 分")
    p.total_score = total; db.commit(); return ok({"id": p.id, "total_score": total, "score_limit": PAPER_TOTAL_LIMIT})


def tag_match(qtags: dict, wanted: dict) -> bool:
    return all(not values or set(map(str, qtags.get(key, []))) & set(map(str, values)) for key, values in wanted.items())


@app.post("/api/papers/auto-generate")
def auto_paper(body: AutoPaperIn, db: Session = Depends(db_session), actor: Actor = Depends(require("admin", "teacher"))):
    selected: list[tuple[dict, float]] = []
    shortages = []
    if not body.sections: raise HTTPException(422,"至少选择一种题型")
    wanted = [str(x).strip() for x in (body.tags or {}).get("work_types", []) if str(x).strip()]
    pool_by_type: dict[str, list[dict]] = {}
    seen = set()
    for section in body.sections:
        qt=section.get("q_type")
        if qt not in QTYPE_TO_PLATFORM or qt in seen or int(section.get("count",0))<=0 or not math.isfinite(float(section.get("score_each",0))) or float(section.get("score_each",0))<=0: raise HTTPException(422,"抽题规则不合法：题型不可重复，数量和分值必须大于零")
        seen.add(qt)
        if qt not in pool_by_type:
            pool_by_type[qt] = platform_question_page(actor, qtype=QTYPE_TO_PLATFORM[qt])
        pool = [row for row in pool_by_type[qt]
                if row["status"] == "enabled" and row["usable"]
                and (not body.difficulty or row["difficulty"] == body.difficulty)
                and (not wanted or set(row["tags"].get("tag_names", [])) & set(wanted))]
        count = int(section["count"])
        if len(pool) < count: shortages.append({"q_type": qt, "need": count, "available": len(pool), "short": count-len(pool)})
        else: selected.extend((row, float(section["score_each"])) for row in random.sample(pool, count))
    if shortages:
        type_names = {"single": "单选题", "multi": "多选题", "judge": "判断题", "essay": "实操问答", "case": "案例分析题", "blank": "填空题", "short": "简答题"}
        scope = f"工种「{'、'.join(wanted)}」范围内" if wanted else ""
        detail_txt = "；".join(
            f"{type_names.get(s['q_type'], s['q_type'])}需 {s['need']} 题、可用 {s['available']} 题"
            for s in shortages)
        hint = ("所选工种下暂无已打该工种标签的试题——请先在资源中心「题库管理」为试题打上对应工种标签，或清除工种范围后重试。"
                if wanted else "请调低各题型数量，或先在资源中心补充题库。")
        raise HTTPException(422, detail={"message": f"随机组卷失败：{scope}题库数量不足（{detail_txt}）。{hint}", "shortages": shortages})
    total = round(sum(score for _, score in selected), 2)
    # 分值整数化：每题 = floor(100/N)，余数分摊到前几题，保证总分恰为 100 且无小数分
    n = len(selected)
    if n:
        base, rem = divmod(100, n)
        selected = [(row, base + (1 if i < rem else 0)) for i, (row, _) in enumerate(selected)]
        total = 100.0
    if total > PAPER_TOTAL_LIMIT: raise HTTPException(422, f"试卷总分不能超过 {PAPER_TOTAL_LIMIT} 分，当前规则合计 {total:g} 分")
    if body.pass_score > total: raise HTTPException(422, "及格分不能高于总分")
    data = body.model_dump(exclude={"sections", "tags", "difficulty"})
    data["work_types"] = json.dumps(body.work_types, ensure_ascii=False)
    data["cert_names"] = json.dumps(body.cert_names, ensure_ascii=False)
    if not data.get("course_name") and body.course_id:
        data["course_name"] = platform_course_name(actor, body.course_id)
    p = Paper(**data, total_score=total, auto_rule=body.model_dump_json(), created_by=actor.id)
    db.add(p); db.flush()
    for i, (row, score) in enumerate(selected, 1):
        q = sync_platform_question(db, actor, row["platform_id"])
        db.add(PaperQuestion(paper_id=p.id, question_id=q.id, sort_no=i, score=score))
    db.flush()
    real_total = paper_total(p)
    if real_total > PAPER_TOTAL_LIMIT: raise HTTPException(422, f"试卷总分不能超过 {PAPER_TOTAL_LIMIT} 分，当前合计 {real_total:g} 分")
    p.total_score = real_total
    db.commit(); return ok({"id": p.id, "total_score": real_total, "question_count": len(selected), "score_limit": PAPER_TOTAL_LIMIT}, "自动组卷完成")


@app.post("/api/papers/{pid}/publish")
def publish_paper(pid: int, db: Session = Depends(db_session), actor: Actor = Depends(require("admin", "teacher"))):
    p = db.get(Paper, pid)
    if not p or not p.questions: raise HTTPException(422, "试卷不能为空")
    if any(db.get(Question,x.question_id).status != "enabled" for x in p.questions): raise HTTPException(422,"试卷包含已停用题目，请先在题库查询中移除")
    total = paper_total(p)
    if total > PAPER_TOTAL_LIMIT: raise HTTPException(422, f"试卷总分不能超过 {PAPER_TOTAL_LIMIT} 分，当前合计 {total:g} 分")
    p.total_score = total
    if p.pass_score > total: raise HTTPException(422, "及格分不能高于总分")
    p.status = "published"; db.commit(); return ok(message="试卷已发布")


@app.get("/api/admin/exam-schedules")
def list_schedules(db: Session = Depends(db_session), actor: Actor = Depends(require("admin", "teacher", "reviewer"))):
    rows = []
    for s in db.scalars(select(ExamSchedule).order_by(ExamSchedule.id.desc())):
        tasks = list(db.scalars(select(LearnerExamTask).where(LearnerExamTask.schedule_id == s.id)))
        rows.append({"id": s.id, "name": s.name, "paper_id": s.paper_id, "start_at": s.start_at, "end_at": s.end_at, "status": s.status, "scope_type": s.scope_type, "participants": len(tasks), "finished": sum(t.status == "finished" for t in tasks)})
    return ok(rows)


@app.post("/api/admin/exam-schedules")
def create_schedule(body: ScheduleIn, db: Session = Depends(db_session), actor: Actor = Depends(require("admin"))):
    p = db.get(Paper, body.paper_id)
    if not p or p.status != "published": raise HTTPException(422, "只能选择已发布试卷")
    if body.end_at <= body.start_at: raise HTTPException(422, "结束时间必须晚于开始时间")
    members=sync_members(db,actor)
    ids=body.user_ids
    if body.scope_type == "class":
        group=db.get(TrainingClass,body.class_id)
        if not group: raise HTTPException(422,"请选择培训班")
        ids=parse(group.user_ids,[])
    if not ids or set(ids)-{x["id"] for x in members}: raise HTTPException(422,"参考人员必须是当前企业的有效用户")
    s = ExamSchedule(name=body.name, paper_id=p.id, start_at=utc_naive(body.start_at), end_at=utc_naive(body.end_at), scope_type=body.scope_type, scope_detail=json.dumps({"user_ids": sorted(set(ids)), "class_id":body.class_id, "duration_min":body.duration_min, "retry_limit":p.retry_limit, "pass_score":p.pass_score}), created_by=actor.id)
    db.add(s); db.commit(); return ok({"id": s.id}, "考试计划草稿已创建")


@app.post("/api/admin/exam-schedules/{sid}/publish")
def publish_schedule(sid: int, db: Session = Depends(db_session), actor: Actor = Depends(require("admin"))):
    s = db.get(ExamSchedule, sid)
    if not s: raise HTTPException(404, "计划不存在")
    p = db.get(Paper, s.paper_id)
    if s.status == "draft":
        if any(db.get(Question,x.question_id).status != "enabled" for x in p.questions): raise HTTPException(422,"试卷中有题目已停用，请先在题库查询中移除")
        for pq in sorted(p.questions, key=lambda x: x.sort_no):
            q = db.get(Question, pq.question_id)
            db.add(ExamInstanceQuestion(schedule_id=s.id, question_id=q.id, q_type=q.q_type, stem=q.stem, options=q.options, answer=q.answer, analysis=q.analysis, sort_no=pq.sort_no, score=pq.score))
        detail = parse(s.scope_detail, {})
        user_ids = detail.get("user_ids", [])
        for uid in set(user_ids):
            if db.get(User, uid): db.add(LearnerExamTask(schedule_id=s.id, user_id=uid, allow_start_at=s.start_at))
        s.status = "scheduled"; db.commit()
    return ok({"schedule_id": s.id}, "排考已发布并锁定快照")


def task_state(task: LearnerExamTask, schedule: ExamSchedule) -> str:
    moment = now()
    if task.status in {"doing", "finished", "missed", "absent"}: return task.status
    if moment > schedule.end_at: return "missed"
    if moment >= schedule.start_at: return "waiting"
    return "not_open"


@app.get("/api/my/exams")
def my_exams(db: Session = Depends(db_session), actor: Actor = Depends(require("learner"))):
    rows = []
    for t in db.scalars(select(LearnerExamTask).where(LearnerExamTask.user_id == actor.id).order_by(LearnerExamTask.id.desc())):
        s = db.get(ExamSchedule, t.schedule_id); p = db.get(Paper, s.paper_id); state = task_state(t, s)
        if t.status != state: t.status = state
        attempt = db.scalar(select(ExamAttempt).where(ExamAttempt.task_id == t.id).order_by(ExamAttempt.id.desc()))
        rows.append({"id": t.id, "name": s.name, "paper": p.name, "status": state, "start_at": s.start_at, "end_at": s.end_at, "duration_min": parse(s.scope_detail,{}).get("duration_min",p.duration_min), "attempt_used": t.attempt_used, "retry_limit": p.retry_limit, "attempt_id": attempt.id if attempt else None, "result_status": attempt.status if attempt else None, "score": attempt.total_score if attempt and attempt.status == "graded" else None})
    db.commit(); return ok(rows)


def learner_question(iq: ExamInstanceQuestion):
    return {"id": iq.question_id, "q_type": iq.q_type, "stem": iq.stem, "options": parse(iq.options), "sort_no": iq.sort_no, "score": iq.score}


@app.post("/api/my/exams/{task_id}/start")
def start_exam(task_id: int, db: Session = Depends(db_session), actor: Actor = Depends(require("learner"))):
    t = db.get(LearnerExamTask, task_id)
    if not t or t.user_id != actor.id: raise HTTPException(404, "考试任务不存在")
    s = db.get(ExamSchedule, t.schedule_id); p = db.get(Paper, s.paper_id); moment = now()
    active = db.scalar(select(ExamAttempt).where(ExamAttempt.task_id == t.id, ExamAttempt.status == "in_progress"))
    if active and moment >= active.deadline_at:
        finalize(db,active,"auto"); db.commit(); active=None
    if active:
        items = list(db.scalars(select(ExamInstanceQuestion).where(ExamInstanceQuestion.schedule_id == s.id).order_by(ExamInstanceQuestion.sort_no)))
        return ok({"attempt_id": active.id, "deadline_at": active.deadline_at, "anti_switch": p.anti_switch, "switch_count": active.switch_count, "server_now":now(), "answers":{str(x.question_id):parse(x.user_answer) for x in db.scalars(select(ExamAnswer).where(ExamAnswer.attempt_id==active.id))}, "questions": [learner_question(x) for x in items]})
    if not (s.start_at <= moment <= s.end_at): raise HTTPException(409, "当前不在可进入考试的时间窗口")
    if db.scalar(select(ExamAttempt).where(ExamAttempt.task_id==t.id,ExamAttempt.status=="submitted")): raise HTTPException(409,"上次答卷尚待阅卷，暂不能重考")
    if t.attempt_used >= p.retry_limit + 1: raise HTTPException(409, "考试次数已用完")
    deadline = min(moment + timedelta(minutes=parse(s.scope_detail,{}).get("duration_min",p.duration_min)), s.end_at)
    attempt = ExamAttempt(task_id=t.id, user_id=actor.id, schedule_id=s.id, deadline_at=deadline)
    db.add(attempt); t.attempt_used += 1; t.status = "doing"; db.commit()
    items = list(db.scalars(select(ExamInstanceQuestion).where(ExamInstanceQuestion.schedule_id == s.id).order_by(ExamInstanceQuestion.sort_no)))
    return ok({"attempt_id": attempt.id, "deadline_at": deadline, "anti_switch": p.anti_switch, "switch_count": 0, "server_now":now(), "answers":{}, "questions": [learner_question(x) for x in items]}, "考试已开始")


@app.post("/api/my/exams/attempts/{aid}/answers")
def save_answer(aid: int, body: AnswerIn, db: Session = Depends(db_session), actor: Actor = Depends(require("learner"))):
    a = db.get(ExamAttempt, aid)
    if not a or a.user_id != actor.id or a.status != "in_progress": raise HTTPException(409, "答卷不可编辑")
    if now() >= a.deadline_at:
        finalize(db,a,"auto"); db.commit(); raise HTTPException(409,"考试已到时，答卷已自动提交")
    iq = db.scalar(select(ExamInstanceQuestion).where(ExamInstanceQuestion.schedule_id == a.schedule_id, ExamInstanceQuestion.question_id == body.question_id))
    if not iq: raise HTTPException(422, "题目不属于本次考试")
    validate_given(iq,body.user_answer)
    row = db.scalar(select(ExamAnswer).where(ExamAnswer.attempt_id == aid, ExamAnswer.question_id == body.question_id))
    if not row: row = ExamAnswer(attempt_id=aid, question_id=body.question_id); db.add(row)
    row.user_answer = json.dumps(body.user_answer, ensure_ascii=False); db.commit(); return ok(message="答案已保存")


@app.post("/api/my/exams/attempts/{aid}/switch")
def record_switch(aid: int, db: Session = Depends(db_session), actor: Actor = Depends(require("learner"))):
    a = db.get(ExamAttempt, aid)
    if not a or a.user_id != actor.id or a.status != "in_progress": raise HTTPException(409, "答卷状态异常")
    p=db.get(Paper,db.get(ExamSchedule,a.schedule_id).paper_id)
    if not p.anti_switch: return ok({"switch_count":a.switch_count})
    a.switch_count += 1; db.commit(); return ok({"switch_count": a.switch_count}, "切屏已留痕")


def finalize(db: Session, a: ExamAttempt, mode: str):
    if a.status != "in_progress": return
    items = list(db.scalars(select(ExamInstanceQuestion).where(ExamInstanceQuestion.schedule_id == a.schedule_id)))
    answer_map = {x.question_id: x for x in db.scalars(select(ExamAnswer).where(ExamAnswer.attempt_id == a.id))}
    objective = 0.0; has_subjective = False
    for iq in items:
        ans = answer_map.get(iq.question_id)
        if not ans:
            ans = ExamAnswer(attempt_id=a.id, question_id=iq.question_id, user_answer="null"); db.add(ans)
        if iq.q_type in {"single", "multi", "judge"}:
            given = parse(ans.user_answer)
            expected = parse(iq.answer)
            if iq.q_type == "multi": given, expected = sorted(given or []), sorted(expected or [])
            ans.is_correct = given == expected; ans.score_awarded = iq.score if ans.is_correct else 0; objective += ans.score_awarded
        else: has_subjective = True
    a.objective_score = objective; a.submitted_at = now(); a.submit_mode = mode; a.status = "submitted" if has_subjective else "graded"
    if not has_subjective:
        a.total_score = objective; complete_attempt(db, a)


@app.post("/api/my/exams/attempts/{aid}/submit")
def submit_exam(aid: int, db: Session = Depends(db_session), actor: Actor = Depends(require("learner"))):
    a = db.get(ExamAttempt, aid)
    if not a or a.user_id != actor.id: raise HTTPException(404, "答卷不存在")
    finalize(db, a, "auto" if now() >= a.deadline_at else "manual"); db.commit()
    return ok({"status": a.status, "objective_score": a.objective_score}, "交卷成功" if a.status == "graded" else "已交卷，主观题批阅中")


@app.get("/api/my/exams/attempts/{aid}/result")
def exam_result(aid: int, db: Session = Depends(db_session), actor: Actor = Depends(require("learner"))):
    a = db.get(ExamAttempt, aid)
    if not a or a.user_id != actor.id: raise HTTPException(404, "答卷不存在")
    base = {"id": a.id, "status": a.status, "objective_score": a.objective_score, "subjective_score": a.subjective_score, "total_score": a.total_score, "is_pass": a.is_pass, "switch_count": a.switch_count}
    if a.status == "graded":
        base["answers"] = [{"question_id": x.question_id, "is_correct": x.is_correct, "score_awarded": x.score_awarded, "comment": x.judge_comment} for x in db.scalars(select(ExamAnswer).where(ExamAnswer.attempt_id == aid))]
    return ok(base)


@app.get("/api/admin/grading")
def grading_list(db: Session = Depends(db_session), actor: Actor = Depends(require("admin", "teacher"))):
    rows=[]
    for a in db.scalars(select(ExamAttempt).where(ExamAttempt.status == "submitted").order_by(ExamAttempt.id)):
        user=db.get(User,a.user_id); schedule=db.get(ExamSchedule,a.schedule_id); rows.append({"id":a.id,"learner":user.name,"exam":schedule.name,"objective_score":a.objective_score,"submitted_at":a.submitted_at})
    return ok(rows)


@app.get("/api/admin/grading/{aid}")
def grading_detail(aid: int, db: Session = Depends(db_session), actor: Actor = Depends(require("admin", "teacher"))):
    a=db.get(ExamAttempt,aid)
    if not a: raise HTTPException(404,"答卷不存在")
    amap={x.question_id:x for x in db.scalars(select(ExamAnswer).where(ExamAnswer.attempt_id==aid))}
    items=[]
    for iq in db.scalars(select(ExamInstanceQuestion).where(ExamInstanceQuestion.schedule_id==a.schedule_id).order_by(ExamInstanceQuestion.sort_no)):
        ans=amap.get(iq.question_id); items.append({**learner_question(iq),"learner_answer":parse(ans.user_answer) if ans else None,"reference":parse(iq.answer) if iq.q_type in {"essay","case"} else None,"score_awarded":ans.score_awarded if ans else None})
    return ok({"attempt_id":aid,"questions":items})


@app.post("/api/admin/grading/{aid}/grade")
def grade_exam(aid: int, body: GradeIn, db: Session = Depends(db_session), actor: Actor = Depends(require("admin", "teacher"))):
    a=db.get(ExamAttempt,aid)
    if not a or a.status not in {"submitted","graded"}: raise HTTPException(409,"答卷不可批阅")
    if a.status=="graded": return ok({"total_score":a.total_score,"is_pass":a.is_pass},"答卷已批阅")
    score=0.0
    for item in body.answers:
        iq=db.scalar(select(ExamInstanceQuestion).where(ExamInstanceQuestion.schedule_id==a.schedule_id,ExamInstanceQuestion.question_id==int(item["question_id"])))
        if not iq or iq.q_type not in {"essay","case"}: continue
        value=float(item["score"])
        if not math.isfinite(value) or value<0 or value>iq.score: raise HTTPException(422,f"题目 {iq.question_id} 分数越界")
        ans=db.scalar(select(ExamAnswer).where(ExamAnswer.attempt_id==aid,ExamAnswer.question_id==iq.question_id)); ans.score_awarded=value; ans.judge_comment=item.get("comment"); score+=value
    subjects=list(db.scalars(select(ExamInstanceQuestion).where(ExamInstanceQuestion.schedule_id==a.schedule_id,ExamInstanceQuestion.q_type.in_(["essay","case"]))))
    graded_ids={x.question_id for x in db.scalars(select(ExamAnswer).where(ExamAnswer.attempt_id==aid,ExamAnswer.score_awarded.is_not(None)))}
    if any(x.question_id not in graded_ids for x in subjects): raise HTTPException(422,"仍有主观题未评分")
    a.subjective_score=sum((db.scalar(select(ExamAnswer.score_awarded).where(ExamAnswer.attempt_id==aid,ExamAnswer.question_id==x.question_id)) or 0) for x in subjects); a.total_score=a.objective_score+a.subjective_score; a.graded_by=actor.id; a.graded_at=now(); a.status="graded"; complete_attempt(db,a); db.commit()
    return ok({"total_score":a.total_score,"is_pass":a.is_pass},"阅卷完成")


def complete_attempt(db: Session, a: ExamAttempt):
    s=db.get(ExamSchedule,a.schedule_id); p=db.get(Paper,s.paper_id); task=db.get(LearnerExamTask,a.task_id)
    a.is_pass=a.total_score>=p.pass_score; task.status="finished"
    iq_map={x.question_id:x for x in db.scalars(select(ExamInstanceQuestion).where(ExamInstanceQuestion.schedule_id==a.schedule_id))}
    for ans in db.scalars(select(ExamAnswer).where(ExamAnswer.attempt_id==a.id)):
        iq=iq_map[ans.question_id]; wrong=(iq.q_type in {"single","multi","judge"} and not ans.is_correct) or (iq.q_type in {"essay","case"} and (ans.score_awarded or 0)<iq.score*.6)
        if wrong:
            row=db.scalar(select(WrongQuestion).where(WrongQuestion.user_id==a.user_id,WrongQuestion.question_id==iq.question_id))
            if not row: row=WrongQuestion(user_id=a.user_id,question_id=iq.question_id,course_id=p.course_id,q_type=iq.q_type,source_attempt_id=a.id); db.add(row)
            row.mastered=False; row.mastered_at=None; row.wrong_at=now(); row.source_attempt_id=a.id
    if a.is_pass and p.cert_enabled: issue_certificate(db,a,p)


def issue_certificate(db: Session,a: ExamAttempt,p: Paper):
    if db.scalar(select(Certificate).where(Certificate.attempt_id==a.id)): return
    cert_type=p.cert_type
    code={"graduate":"CT","competence":"COMP","joint":"JOINT","worktype":"QL"}[cert_type]
    prefix=f"{code}-{now():%Y%m%d%H%M%S}-{a.user_id}-"
    latest=db.scalar(select(func.count()).select_from(Certificate).where(Certificate.cert_no.like(prefix+"%"))) or 0
    cert=Certificate(cert_no=f"{prefix}{secrets.token_hex(4).upper()}",user_id=a.user_id,attempt_id=a.id,cert_type=cert_type,title=p.name.replace("考试","认证证书"),course_id=p.course_id,score=a.total_score)
    # 企业模板快照：结业证书存课程信息；工种资格证书存工种/试卷信息（细节数据渲染时按需从平台补齐）
    if hasattr(cert,"course_name"):
        if cert_type in ("graduate","competence"):
            cert.course_name=p.course_name or (str(p.course_id) if p.course_id else "")
            cert.paper_name=p.name
        elif cert_type=="worktype":
            wt=parse(p.work_types,[])
            cert.work_type_name=", ".join(wt) if wt else ""
            cert.paper_name=p.name
            cert.relate_courses=p.course_name or ""
    db.add(cert); db.flush()
    if cert_type=="joint":
        for issuer in db.scalars(select(CertIssuer).where(CertIssuer.enabled==True,CertIssuer.issue_on_pass==True)):
            ext=None; payload={"cert_no":cert.cert_no,"user_id":cert.user_id,"score":cert.score}
            db.add(CertSyncLog(cert_id=cert.id,issuer_id=issuer.id,status="pending",ext_cert_no=ext,request_body=json.dumps(payload),response=json.dumps({"message":"等待管理员向已配置合作机构同步"},ensure_ascii=False)))


@app.get("/api/my/wrong-questions")
def wrong_book(q_type:str|None=None,course:str|None=None,db:Session=Depends(db_session),actor:Actor=Depends(require("learner"))):
    stmt=select(WrongQuestion).where(WrongQuestion.user_id==actor.id,WrongQuestion.mastered==False)
    if q_type: stmt=stmt.where(WrongQuestion.q_type==q_type)
    rows=[]
    for w in db.scalars(stmt):
        a=db.get(ExamAttempt,w.source_attempt_id); iq=db.scalar(select(ExamInstanceQuestion).where(ExamInstanceQuestion.schedule_id==a.schedule_id,ExamInstanceQuestion.question_id==w.question_id)); answer=parse(iq.answer)
        if iq.q_type in {"essay","case"}: answer={"points":answer.get("points")} if isinstance(answer,dict) else None
        paper=db.get(Paper,db.get(ExamSchedule,a.schedule_id).paper_id)
        if course and course.lower() not in paper.course_name.lower(): continue
        rows.append({"course_name":paper.course_name,"options":parse(iq.options),"id":w.id,"q_type":w.q_type,"stem":iq.stem,"correct_answer":answer,"analysis":iq.analysis,"wrong_at":w.wrong_at})
    return ok(rows)


@app.post("/api/my/wrong-questions/{wid}/mastered")
def master_wrong(wid:int,db:Session=Depends(db_session),actor:Actor=Depends(require("learner"))):
    w=db.get(WrongQuestion,wid)
    if not w or w.user_id!=actor.id: raise HTTPException(404,"错题不存在")
    w.mastered=True; w.mastered_at=now(); db.commit(); return ok(message="已标记掌握")


def cert_dict(c:Certificate,db:Session):
    user=db.get(User,c.user_id)
    return {"id":c.id,"cert_no":c.cert_no,"title":c.title,"cert_type":c.cert_type,"learner":user.name,"score":c.score,"issued_at":c.issued_at,"status":c.status,
            "course_name":c.course_name,"course_category":c.course_category,"business_tag":c.business_tag,"study_hours":c.study_hours,
            "work_type_name":c.work_type_name,"skill_level":c.skill_level,"relate_courses":c.relate_courses,"paper_name":c.paper_name}


@app.get("/api/my/certs")
def my_certs(db:Session=Depends(db_session),actor:Actor=Depends(require("learner"))): return ok([cert_dict(c,db) for c in db.scalars(select(Certificate).where(Certificate.user_id==actor.id).order_by(Certificate.id.desc()))])


@app.get("/api/my/certs/{cid}/preview",response_class=HTMLResponse)
def cert_preview(cid:int,db:Session=Depends(db_session),actor:Actor=Depends(current_user)):
    c=db.get(Certificate,cid)
    if not c or (actor.role not in {"admin","teacher","reviewer","viewer"} and c.user_id!=actor.id): raise HTTPException(404,"证书不存在")
    d=cert_dict(c,db); d={k:html.escape(v) if isinstance(v,str) else v for k,v in d.items()}; return f'''<!doctype html><meta charset="utf-8"><style>body{{margin:0;background:#edf1f6;font-family:serif}}.cert{{width:900px;height:600px;margin:30px auto;padding:70px;box-sizing:border-box;background:#fffdf8;border:18px double #173f5f;text-align:center;color:#173f5f}}h1{{font-size:54px;letter-spacing:12px}}h2{{font-size:28px}}p{{font-size:20px;line-height:2}}.seal{{display:inline-grid;place-items:center;width:105px;height:105px;border:4px solid #b4322a;border-radius:50%;color:#b4322a;transform:rotate(-9deg)}}small{{display:block;margin-top:25px}}</style><div class="cert"><h1>认证证书</h1><h2>{d['title']}</h2><p>兹证明 <b>{d['learner']}</b> 已完成规定考核，成绩 <b>{d['score']:g}</b> 分，特发此证。</p><div class="seal">云培训<br>学院</div><small>证书编号：{d['cert_no']}　签发日期：{d['issued_at']:%Y-%m-%d}　状态：{d['status']}</small></div>'''


@app.get("/api/internal/exam-status")
def internal_status(user_id:int,course_id:int|None=None,db:Session=Depends(db_session),actor:Actor=Depends(require("admin","teacher","reviewer"))):
    attempts=list(db.scalars(select(ExamAttempt).where(ExamAttempt.user_id==user_id).order_by(ExamAttempt.id.desc())))
    return ok({"user_id":user_id,"status":"passed" if any(x.is_pass for x in attempts) else (attempts[0].status if attempts else "not_started"),"attempts":len(attempts)})


# Integration endpoints and background deadline finalization.
from integration import install
install(globals())
