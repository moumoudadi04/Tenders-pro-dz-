from datetime import datetime
from typing import Optional
from fastapi import FastAPI, Depends, Query
from fastapi.middleware.cors import CORSMiddleware
from sqlalchemy import create_engine, String, DateTime, select, or_
from sqlalchemy.orm import DeclarativeBase, Mapped, mapped_column, sessionmaker, Session

engine = create_engine("sqlite:///./tenders.db", connect_args={"check_same_thread": False})
SessionLocal = sessionmaker(bind=engine, autoflush=False, autocommit=False)
class Base(DeclarativeBase): pass
class Tender(Base):
    __tablename__ = "tenders"
    id: Mapped[int] = mapped_column(primary_key=True)
    title: Mapped[str] = mapped_column(String(500), index=True)
    organization: Mapped[str] = mapped_column(String(300), default="")
    wilaya: Mapped[str] = mapped_column(String(100), default="")
    kind: Mapped[str] = mapped_column(String(50), default="مناقصة")
    reference: Mapped[str] = mapped_column(String(150), default="")
    published_at: Mapped[datetime] = mapped_column(DateTime, default=datetime.utcnow)

Base.metadata.create_all(engine)
app = FastAPI(title="Tenders-pro-dz API", version="1.0.0")
app.add_middleware(CORSMiddleware, allow_origins=["*"], allow_methods=["*"], allow_headers=["*"])
def db():
    session=SessionLocal()
    try: yield session
    finally: session.close()
@app.get("/health")
def health(): return {"status":"ok","app":"Tenders-pro-dz"}
@app.get("/api/tenders")
def list_tenders(q: Optional[str]=None, wilaya: Optional[str]=None, kind: Optional[str]=None, limit: int=Query(100,ge=1,le=500), session: Session=Depends(db)):
    stmt=select(Tender).order_by(Tender.published_at.desc()).limit(limit)
    if q: stmt=stmt.where(or_(Tender.title.contains(q),Tender.organization.contains(q),Tender.reference.contains(q)))
    if wilaya: stmt=stmt.where(Tender.wilaya==wilaya)
    if kind: stmt=stmt.where(Tender.kind==kind)
    return [{"id":x.id,"title":x.title,"organization":x.organization,"wilaya":x.wilaya,"kind":x.kind,"reference":x.reference,"published_at":x.published_at.isoformat()} for x in session.scalars(stmt)]
@app.post("/api/tenders")
def add_tender(payload: dict, session: Session=Depends(db)):
    title=str(payload.get("title","")).strip()
    if not title: return {"error":"title is required"}
    item=Tender(title=title,organization=str(payload.get("organization","")),wilaya=str(payload.get("wilaya","")),kind=str(payload.get("kind","مناقصة")),reference=str(payload.get("reference","")))
    session.add(item); session.commit(); session.refresh(item)
    return {"id":item.id,"title":item.title}
