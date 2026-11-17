import re
from datetime import datetime
from typing import Any

from app import AMOUNT_RE, labeled_value, normalize, parse_amount


def text_field(text: str, labels: list[str]) -> dict[str, Any] | None:
    raw = labeled_value(text, labels)
    if raw is None or not raw.strip():
        return None
    normalized = " ".join(raw.split())
    return {"rawValue": raw, "normalizedValue": normalized}


def amount_field(text: str, labels: list[str]) -> dict[str, Any] | None:
    lines = text.splitlines()
    normalized_labels = [normalize(label) for label in labels]
    for index, line in enumerate(lines):
        normalized_line = normalize(line)
        if not any(label in normalized_line for label in normalized_labels):
            continue
        for candidate_line in lines[index:index + 3]:
            normalized_candidate = normalize(candidate_line)
            if "date" in normalized_candidate or "periode" in normalized_candidate:
                continue
            if re.search(r"\b\d{1,2}[./-]\d{1,2}[./-]\d{2,4}\b", candidate_line):
                continue
            for raw in reversed(AMOUNT_RE.findall(candidate_line)):
                value = parse_amount(raw)
                if value is not None:
                    return {"rawValue": raw, "normalizedValue": value}
    return None


def date_field(text: str, labels: list[str]) -> dict[str, Any] | None:
    raw = labeled_value(text, labels)
    if raw is None:
        return None
    match = re.search(r"(?<!\d)(\d{1,2}[./-]\d{1,2}[./-]\d{2,4}|\d{4}-\d{2}-\d{2})(?!\d)", raw)
    if match is None:
        return None
    candidate = match.group(1)
    formats = ("%d/%m/%Y", "%d-%m-%Y", "%d.%m.%Y", "%Y-%m-%d",
               "%d/%m/%y", "%d-%m-%y", "%d.%m.%y")
    for date_format in formats:
        try:
            parsed = datetime.strptime(candidate, date_format).date()
            return {"rawValue": candidate, "normalizedValue": parsed.isoformat()}
        except ValueError:
            continue
    return {"rawValue": candidate, "normalizedValue": None}


def identity_fields(text: str) -> dict[str, dict[str, Any] | None]:
    return {
        "nom": text_field(text, ["nom", "name", "last name", "اللقب"]),
        "prenom": text_field(text, ["prénom", "prenom", "first name", "الاسم الشخصي", "الاسم"]),
        "dateNaissance": date_field(text, ["date de naissance", "date naissance", "birth date", "تاريخ الميلاد"]),
    }


def null_fields(names: list[str]) -> dict[str, None]:
    return {name: None for name in names}


def normalized_text(value: str) -> str:
    return " ".join(normalize(value).split())
