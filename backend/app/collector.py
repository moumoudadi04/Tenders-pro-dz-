from __future__ import annotations

import hashlib
import re
import time
from datetime import datetime, timezone
from urllib.parse import urljoin, unquote

import requests
from bs4 import BeautifulSoup
from sqlalchemy import select

BASE = "https://www.mounakassatdz.com"
LISTING = BASE + "/annonces"
HEADERS = {
    "User-Agent": "KENDR-PRO-public-index-collector/1.0 (+https://github.com/moumoudadi04/Tenders-pro-dz-)",
    "Accept-Language": "ar,en;q=0.8,fr;q=0.6",
}
WILAYAS = [
    "أدرار", "الشلف", "الأغواط", "أم البواقي", "باتنة", "بجاية", "بسكرة", "بشار",
    "البليدة", "البويرة", "تمنراست", "تبسة", "تلمسان", "تيارت", "تيزي وزو",
    "الجزائر العاصمة", "الجلفة", "جيجل", "سطيف", "سعيدة", "سكيكدة", "سيدي بلعباس",
    "عنابة", "قالمة", "قسنطينة", "المدية", "مستغانم", "المسيلة", "معسكر", "ورقلة",
    "وهران", "البيض", "إليزي", "برج بوعريريج", "بومرداس", "الطارف", "تندوف",
    "تيسمسيلت", "الوادي", "خنشلة", "سوق أهراس", "تيبازة", "ميلة", "عين الدفلى",
    "النعامة", "عين تموشنت", "غرداية", "غليزان", "تيميمون", "برج باجي مختار",
    "بني عباس", "أولاد جلال", "عين صالح", "عين قزام", "تقرت", "جانت", "المغير", "المنيعة",
]


def _kind(text: str) -> str:
    t = text.lower()
    if "استشار" in t or "consultation" in t:
        return "consultation"
    if "مناقصة" in t or "appel d'offre" in t or "appel d’offres" in t:
        return "tender"
    if "منح مؤقت" in t:
        return "award"
    if "مزايد" in t:
        return "auction"
    if "تمديد" in t:
        return "extension"
    if "إلغاء" in t or "عدم جدوى" in t:
        return "cancelled"
    if "إعذار" in t:
        return "notice"
    if "فسخ" in t:
        return "termination"
    return "other"


def _extract_title(anchor_text: str, href: str) -> str:
    text = re.sub(r"\s+", " ", anchor_text).strip()
    # Listing pages may expose the headline but hide the paid subscriber fields.
    text = re.sub(r"^(مناقصات|إستشارات|استشارات|منح مؤقتة|إعلانات عن فسخ|إعلانات عن إعذار|إعلان عن مزايدات|تمديد و تأخير الآجال)\s*[>›»:-]*\s*", "", text)
    text = re.sub(r"\s+(معلن|الولاية|من)\s*:.*$", "", text)
    if len(text) >= 5:
        return text[:490]
    slug = unquote(href.rsplit("/", 1)[-1])
    slug = re.sub(r"^[A-Za-z0-9_-]+-", "", slug)
    return slug.replace("_", " ")[:490] or "إعلان مناقصة"


def _extract_wilaya(text: str) -> str:
    match = re.search(r"الولاية\s*:\s*ولاية\s*([^:|]+)", text)
    if match:
        return match.group(1).strip()[:100]
    for name in WILAYAS:
        if f"ولاية {name}" in text:
            return name
    return ""


def _extract_reference(href: str) -> str:
    slug = unquote(href.rstrip("/").rsplit("/", 1)[-1])
    m = re.match(r"([A-Za-z0-9_-]{4,})-", slug)
    return m.group(1) if m else ""


def _collect_page(session: requests.Session, page: int):
    url = LISTING if page == 1 else f"{LISTING}?page={page}"
    response = session.get(url, headers=HEADERS, timeout=25)
    response.raise_for_status()
    soup = BeautifulSoup(response.text, "html.parser")
    found = []
    seen = set()
    for a in soup.select('a[href*="/details/"]'):
        href = a.get("href", "").strip()
        absolute = urljoin(BASE, href)
        if absolute in seen:
            continue
        seen.add(absolute)
        raw = a.get_text(" ", strip=True)
        if not raw:
            continue
        title = _extract_title(raw, href)
        kind = _kind(raw + " " + title)
        found.append({
            "title": title,
            "kind": kind,
            "wilaya": _extract_wilaya(raw),
            "reference": _extract_reference(href),
            "source_name": "MounakassatDZ",
            "source_url": absolute,
            "status": "unverified",
            "published_at": datetime.now(timezone.utc),
        })
    return found


def collect_public_tenders(session_factory, max_pages: int = 5):
    """Collect only metadata visible without login/subscription; never fetch gated detail text."""
    http = requests.Session()
    records = []
    errors = []
    for page in range(1, max_pages + 1):
        try:
            batch = _collect_page(http, page)
            records.extend(batch)
            if not batch:
                break
        except requests.RequestException as exc:
            errors.append(f"page {page}: {type(exc).__name__}")
            if page == 1:
                raise
            break
        time.sleep(1.0)  # polite crawl interval
    inserted = 0
    updated = 0
    with session_factory() as db:
        from app.main import Tender
        for row in records:
            digest = hashlib.sha256((row["source_url"] + "|" + row["title"]).encode("utf-8")).hexdigest()
            existing = db.scalar(select(Tender).where(
                (Tender.source_url == row["source_url"]) | (Tender.content_hash == digest)
            ))
            if existing:
                # Refresh only fields present in the public listing; do not erase known values.
                existing.title = row["title"] or existing.title
                existing.wilaya = row["wilaya"] or existing.wilaya
                existing.kind = row["kind"] or existing.kind
                existing.reference = row["reference"] or existing.reference
                existing.source_name = row["source_name"]
                existing.content_hash = digest
                updated += 1
            else:
                item = Tender(**row, content_hash=digest)
                db.add(item)
                inserted += 1
        db.commit()
        total = db.query(Tender).count()
    return {
        "source": "MounakassatDZ",
        "pages_checked": min(max_pages, page),
        "records_seen": len(records),
        "inserted": inserted,
        "refreshed": updated,
        "total_in_database": total,
        "errors": errors,
        "collected_at": datetime.now(timezone.utc).isoformat(),
    }
