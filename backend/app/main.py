from __future__ import annotations

import asyncio
import hashlib
import os
from contextlib import asynccontextmanager
from datetime import datetime, timezone
from typing import Optional

from fastapi import FastAPI, Depends, Query
from fastapi.middleware.cors import CORSMiddleware
from sqlalchemy import create_engine, String, DateTime, Text, select, or_, inspect, text
from sqlalchemy.orm import DeclarativeBase, Mapped, mapped_column, sessionmaker, Session

from app.collector import collect_public_tenders

DATABASE_URL = os.getenv("DATABASE_URL", "sqlite:///./tenders.db")\nif DATABASE_URL.startswith("postgres://"):\n    DATABASE_URL = DATABASE_URL.replace("postgres://", "postgresql+psycopg://", 1)\nelif DATABASE_URL.startswith("postgresql://"):\n    DATABASE_URL = DATABASE_URL.replace("postgresql://", "postgresql+psycopg://", 1)
connect_args = {"check_same_thread": False} if DATABASE_URL.startswith("sqlite") else {}
engine = create_engine(DATABASE_URL, connect_args=connect_args, pool_pre_ping=True)
SessionLocal = sessionmaker(bind=engine, autoflush=False, autocommit=False)


class Base(DeclarativeBase):
    pass


class Tender(Base):
    __tablename__ = "tenders"
    id: Mapped[int] = mapped_column(primary_key=True)
    title: Mapped[str] = mapped_column(String(500), index=True)
    organization: Mapped[str] = mapped_column(String(300), default="")
    wilaya: Mapped[str] = mapped_column(String(100), default="")
    kind: Mapped[str] = mapped_column(String(50), default="tender")
    reference: Mapped[str] = mapped_column(String(150), default="")
    published_at: Mapped[datetime] = mapped_column(DateTime, default=lambda: datetime.now(timezone.utc))
    commune: Mapped[str] = mapped_column(String(150), default="")
    sector: Mapped[str] = mapped_column(String(200), default="")
    deadline: Mapped[Optional[str]] = mapped_column(String(40), nullable=True)
    opening_at: Mapped[Optional[str]] = mapped_column(String(40), nullable=True)
    status: Mapped[str] = mapped_column(String(40), default="unverified")
    source_name: Mapped[str] = mapped_column(String(150), default="")
    source_url: Mapped[str] = mapped_column(String(1000), default="")
    spec_url: Mapped[str] = mapped_column(String(1000), default="")
    content_hash: Mapped[str] = mapped_column(String(64), default="", index=True)


Base.metadata.create_all(engine)

# Lightweight additive migration for databases created by earlier app versions.
_existing = {c["name"] for c in inspect(engine).get_columns("tenders")}
_migrations = {
    "commune": "VARCHAR(150) DEFAULT ''",
    "sector": "VARCHAR(200) DEFAULT ''",
    "deadline": "VARCHAR(40)",
    "opening_at": "VARCHAR(40)",
    "status": "VARCHAR(40) DEFAULT 'unverified'",
    "source_name": "VARCHAR(150) DEFAULT ''",
    "source_url": "VARCHAR(1000) DEFAULT ''",
    "spec_url": "VARCHAR(1000) DEFAULT ''",
    "content_hash": "VARCHAR(64) DEFAULT ''",
}
with engine.begin() as conn:
    for name, sql_type in _migrations.items():
        if name not in _existing:
            conn.execute(text(f"ALTER TABLE tenders ADD COLUMN {name} {sql_type}"))


async def _collector_loop():
    # Collect immediately on startup, then every 30 minutes while the service is awake.
    while True:
        try:
            await asyncio.to_thread(collect_public_tenders, SessionLocal)
        except Exception as exc:
            print(f"[collector] cycle failed: {type(exc).__name__}: {exc}", flush=True)
        await asyncio.sleep(30 * 60)


@asynccontextmanager
async def lifespan(app: FastAPI):
    task = asyncio.create_task(_collector_loop())
    yield
    task.cancel()
    try:
        await task
    except asyncio.CancelledError:
        pass


app = FastAPI(title="KENDR PRO / Tenders-pro-dz API", version="1.1.0", lifespan=lifespan)
app.add_middleware(CORSMiddleware, allow_origins=["*"], allow_methods=["*"], allow_headers=["*"])


def db():
    session = SessionLocal()
    try:
        yield session
    finally:
        session.close()


def _as_dict(x: Tender):
    return {
        "id": x.id,
        "title": x.title,
        "organization": x.organization,
        "wilaya": x.wilaya,
        "commune": x.commune,
        "sector": x.sector,
        "kind": x.kind,
        "reference": x.reference,
        "published_at": x.published_at.isoformat() if x.published_at else None,
        "deadline": x.deadline,
        "opening_at": x.opening_at,
        "status": x.status,
        "source": x.source_name,
        "source_name": x.source_name,
        "source_url": x.source_url,
        "spec_url": x.spec_url,
        "is_new": True,
    }


@app.get("/health")
def health():
    return {"status": "ok", "app": "Tenders-pro-dz", "collector": "enabled", "interval_minutes": 30}


@app.get("/api/sources")
def sources():
    return [
        {"name": "MounakassatDZ", "url": "https://www.mounakassatdz.com/annonces",
         "mode": "public_listing_metadata", "enabled": True,
         "note": "يتم جمع بيانات الفهرسة الظاهرة للعموم فقط؛ التفاصيل المحجوبة تتطلب اشتراك الموقع."},
        {"name": "بوابة الصفقات العمومية", "url": "https://marches-publics.gov.dz/",
         "mode": "official_reference", "enabled": False,
         "note": "مصدر رسمي؛ لم يتم تفعيل الجمع الآلي لعدم توفر واجهة عامة موثقة في هذا الإصدار."},
        {"name": "BOMOP / ANEP", "url": "https://bomop.anep.dz/ar/",
         "mode": "official_reference", "enabled": False,
         "note": "الاطلاع على الإعلانات التفصيلية يخضع لاشتراك؛ لا يتم تجاوز تسجيل الدخول أو الحماية."},
        {"name": "BAOSEM", "url": "https://www.baosem.com/",
         "mode": "official_reference", "enabled": False,
         "note": "يضاف موصل عام بعد التحقق من شروط الوصول وطريقة النشر المتاحة."},
    ]


@app.get("/api/tenders")
def list_tenders(
    q: Optional[str] = None,
    wilaya: Optional[str] = None,
    kind: Optional[str] = None,
    status: Optional[str] = None,
    page: int = Query(1, ge=1),
    limit: int = Query(100, ge=1, le=500),
    session: Session = Depends(db),
):
    stmt = select(Tender)
    if q:
        stmt = stmt.where(or_(Tender.title.contains(q), Tender.organization.contains(q),
                              Tender.reference.contains(q), Tender.sector.contains(q)))
    if wilaya:
        stmt = stmt.where(Tender.wilaya.contains(wilaya))
    if kind:
        stmt = stmt.where(Tender.kind == kind)
    if status:
        stmt = stmt.where(Tender.status == status)
    total = len(session.scalars(stmt).all())
    rows = session.scalars(stmt.order_by(Tender.published_at.desc())
                           .offset((page - 1) * limit).limit(limit)).all()
    return {"items": [_as_dict(x) for x in rows], "total": total, "page": page, "page_size": limit}


@app.get("/api/tenders/{tender_id}")
def get_tender(tender_id: int, session: Session = Depends(db)):
    item = session.get(Tender, tender_id)
    if not item:
        from fastapi import HTTPException
        raise HTTPException(status_code=404, detail="Tender not found")
    return _as_dict(item)


@app.post("/api/tenders")
def add_tender(payload: dict, session: Session = Depends(db)):
    title = str(payload.get("title", "")).strip()
    if not title:
        from fastapi import HTTPException
        raise HTTPException(status_code=400, detail="title is required")
    item = Tender(title=title, organization=str(payload.get("organization", "")),
                  wilaya=str(payload.get("wilaya", "")), kind=str(payload.get("kind", "tender")),
                  reference=str(payload.get("reference", "")), source_name=str(payload.get("source_name", "manual")),
                  source_url=str(payload.get("source_url", "")))
    session.add(item)
    session.commit()
    session.refresh(item)
    return {"id": item.id, "title": item.title}


@app.post("/api/collector/run")
def run_collector():
    # Manual trigger for owner testing; normal collection runs automatically in the background.
    result = collect_public_tenders(SessionLocal)
    return result
