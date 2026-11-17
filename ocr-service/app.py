import re
import os
import tempfile
import threading
import unicodedata
from pathlib import Path
from typing import Any

from fastapi import FastAPI, File, Form, HTTPException, UploadFile

app = FastAPI(title="PaddleOCR Credit Documents", version="1.0.0")

MAX_FILE_SIZE = 10 * 1024 * 1024
SUPPORTED_TYPES = {
    "CIN", "BULLETIN_SALAIRE", "ATTESTATION_SALAIRE_ANNUELLE",
    "ATTESTATION_TRAVAIL", "JUSTIFICATIF_AUTRES_REVENUS", "DEMANDE_CREDIT",
    "DEVIS_TRAVAUX", "FACTURE_PRO_FORMA", "PROMESSE_VENTE", "CARTE_GRISE",
    "TITRE_FONCIER", "JUSTIFICATIF_PROPRIETE", "ACTE_NOTARIE", "PERMIS_BATIR", "DEVIS_ARCHITECTE",
    "EXPERTISE_IMMOBILIERE", "RELEVE_BANCAIRE",
}
CONFIDENCE_AUTO = float(os.getenv("OCR_CONFIDENCE_AUTO", "0.90"))
CONFIDENCE_REVIEW = float(os.getenv("OCR_CONFIDENCE_REVIEW", "0.70"))
ENGINE_LOCK = threading.Lock()
INFERENCE_LOCK = threading.Lock()
ENGINES: dict[str, Any] | None = None


def get_engines() -> dict[str, Any]:
    """Load the French and Arabic PP-OCRv5 pipelines once, on first use."""
    global ENGINES
    if ENGINES is None:
        with ENGINE_LOCK:
            if ENGINES is None:
                from paddleocr import PaddleOCR

                shared_options = {
                    "ocr_version": "PP-OCRv5",
                    "device": "cpu",
                    "use_doc_orientation_classify": False,
                    "use_doc_unwarping": False,
                    "use_textline_orientation": False,
                }
                ENGINES = {
                    "fr": PaddleOCR(lang="fr", **shared_options),
                    "ar": PaddleOCR(lang="ar", **shared_options),
                }
    return ENGINES


@app.get("/health")
def health() -> dict[str, Any]:
    return {
        "status": "ok",
        "moteur": "PaddleOCR PP-OCRv5",
        "langues": ["fr", "ar"],
        "modelesCharges": ENGINES is not None,
    }


@app.post("/api/ocr/extract")
def extract_document(
    file: UploadFile = File(...),
    typeDocument: str = Form(...),
) -> dict[str, Any]:
    expected_type = typeDocument.strip().upper()
    if expected_type not in SUPPORTED_TYPES:
        raise HTTPException(status_code=400, detail="Type de document non pris en charge.")

    suffix = Path(file.filename or "document").suffix.lower()
    if suffix not in {".pdf", ".jpg", ".jpeg", ".png"}:
        raise HTTPException(status_code=415, detail="Formats acceptés : PDF, JPG ou PNG.")

    content = file.file.read(MAX_FILE_SIZE + 1)
    if not content:
        raise HTTPException(status_code=400, detail="Le document est vide.")
    if len(content) > MAX_FILE_SIZE:
        raise HTTPException(status_code=413, detail="Le document dépasse 10 Mo.")
    if not valid_signature(suffix, content):
        raise HTTPException(status_code=415, detail="Le contenu ne correspond pas au format annoncé.")

    with tempfile.NamedTemporaryFile(suffix=suffix, delete=False) as temporary_file:
        temporary_file.write(content)
        temporary_path = Path(temporary_file.name)

    try:
        lines_by_text: dict[str, tuple[str, float]] = {}
        pages_by_number: dict[int, dict[str, Any]] = {}
        with INFERENCE_LOCK:
            for language, engine in get_engines().items():
                fallback_page = 1
                try:
                    for prediction in engine.predict(str(temporary_path)):
                        result = prediction_payload(prediction)
                        raw_page_index = result.get("page_index")
                        page_number = int(raw_page_index) + 1 if raw_page_index is not None else fallback_page
                        fallback_page = page_number + 1
                        page = pages_by_number.setdefault(page_number, {
                            "page": page_number,
                            "source": "PADDLEOCR",
                            "regions": {},
                            "erreur": None,
                        })
                        texts = result.get("rec_texts")
                        scores = result.get("rec_scores")
                        polygons = result.get("rec_polys")
                        if texts is None:
                            texts = []
                        if scores is None:
                            scores = []
                        if polygons is None:
                            polygons = []
                        for index, raw_text in enumerate(texts):
                            text = str(raw_text).strip()
                            if not text:
                                continue
                            score = safe_score(scores[index] if index < len(scores) else 0.0)
                            key = normalize(text)
                            region = {
                                "texte": text,
                                "confiance": score,
                                "boite": json_safe(polygons[index]) if index < len(polygons) else None,
                                "langue": language,
                            }
                            existing = page["regions"].get(key)
                            if existing is None or score > existing["confiance"]:
                                page["regions"][key] = region
                            if key not in lines_by_text or score > lines_by_text[key][1]:
                                lines_by_text[key] = (text, score)
                except Exception:
                    # Keep the successful language/page results if one pipeline fails.
                    page = pages_by_number.setdefault(fallback_page, {
                        "page": fallback_page,
                        "source": "PADDLEOCR",
                        "regions": {},
                        "erreur": None,
                    })
                    page["erreur"] = f"Échec de la lecture en langue {language}."

        extracted_lines = list(lines_by_text.values())
        extracted_text = "\n".join(text for text, _ in extracted_lines)[:12000]
        confidence = (
            sum(score for _, score in extracted_lines) / len(extracted_lines)
            if extracted_lines
            else 0.0
        )
        field_details = extract_fields(extracted_text, expected_type)
        from extractors.registry import REQUIRED_FIELDS, extraction_status

        pages = []
        for page_number in sorted(pages_by_number):
            page = pages_by_number[page_number]
            regions = list(page["regions"].values())
            page_text = "\n".join(region["texte"] for region in regions)
            pages.append({
                "page": page_number,
                "source": page["source"],
                "texte": page_text,
                "regions": regions,
                "erreur": page["erreur"],
            })

        for field_name, details in field_details.items():
            if details is None:
                continue
            details.update(field_source(field_name, details, pages))
            score = details.get("confiance")
            details["validation"] = (
                "A_CONFIRMER" if isinstance(score, (float, int)) and score >= CONFIDENCE_AUTO
                else "REVISION_MANUELLE"
            )
        fields = {
            name: details.get("normalizedValue") if details is not None else None
            for name, details in field_details.items()
        }
        detected_type = detect_document_type(extracted_text)
        if not extracted_text:
            type_status = "TYPE_INDETERMINE"
            ocr_status = "DOCUMENT_ILLISIBLE"
        elif detected_type == "INCONNU":
            type_status = "TYPE_INDETERMINE"
            ocr_status = "TYPE_INDETERMINE"
        elif detected_type != expected_type:
            type_status = "TYPE_INCORRECT"
            ocr_status = "TYPE_INCORRECT"
        else:
            type_status = "TYPE_CORRECT"
            ocr_status = extraction_status(expected_type, field_details)

        alerts: list[str] = []
        if not extracted_text:
            alerts.append("Aucun texte n'a pu être reconnu sur ce document.")
        if type_status == "TYPE_INCORRECT":
            alerts.append(f"Type détecté {detected_type}, type attendu {expected_type}.")
        elif type_status == "TYPE_INDETERMINE":
            alerts.append("Le type du document n'a pas pu être déterminé automatiquement.")
        if confidence < CONFIDENCE_REVIEW:
            alerts.append("Confiance OCR inférieure à 70 % : révision manuelle obligatoire.")
        elif confidence < CONFIDENCE_AUTO:
            alerts.append("Confiance OCR entre 70 % et 90 % : vérification recommandée.")
        for page in pages:
            if page["erreur"]:
                alerts.append(page["erreur"])
        required = REQUIRED_FIELDS.get(expected_type, ())
        missing = [name for name in required if fields.get(name) is None]
        if missing and type_status == "TYPE_CORRECT":
            alerts.append("Champs essentiels absents : " + ", ".join(missing) + ".")

        return {
            "ocrDisponible": True,
            "moteur": "PaddleOCR PP-OCRv5",
            "langues": ["fr", "ar"],
            "typeDocument": detected_type,
            "typeDocumentAttendu": expected_type,
            "statutType": type_status,
            "statutOCR": ocr_status,
            "champs": fields,
            "champsDetail": field_details,
            "confiance": round(confidence, 4),
            "alertes": alerts,
            "texteExtrait": extracted_text,
            "pages": pages,
        }
    except HTTPException:
        raise
    except Exception as exc:
        # Avoid logging document contents or paths: uploaded records contain personal data.
        raise HTTPException(status_code=503, detail="La lecture PaddleOCR a échoué.") from exc
    finally:
        temporary_path.unlink(missing_ok=True)


def valid_signature(suffix: str, content: bytes) -> bool:
    if suffix == ".pdf":
        return content.startswith(b"%PDF-")
    if suffix == ".png":
        return content.startswith(b"\x89PNG\r\n\x1a\n")
    return len(content) >= 3 and content[:3] == b"\xff\xd8\xff"


def prediction_payload(prediction: Any) -> dict[str, Any]:
    payload = getattr(prediction, "json", None)
    if callable(payload):
        payload = payload()
    if not isinstance(payload, dict):
        return {}
    nested = payload.get("res")
    return nested if isinstance(nested, dict) else payload


def json_safe(value: Any) -> Any:
    if hasattr(value, "tolist"):
        return json_safe(value.tolist())
    if isinstance(value, dict):
        return {str(key): json_safe(item) for key, item in value.items()}
    if isinstance(value, (list, tuple)):
        return [json_safe(item) for item in value]
    if isinstance(value, (str, int, float, bool)) or value is None:
        return value
    return str(value)


def field_source(
    field_name: str,
    details: dict[str, Any],
    pages: list[dict[str, Any]],
) -> dict[str, Any]:
    raw_value = details.get("rawValue")
    normalized_value = details.get("normalizedValue")
    raw_text = str(raw_value).strip() if raw_value is not None else ""
    normalized_text = normalize(raw_text) if raw_text else ""
    target_digits = re.sub(r"\D", "", normalized_text)
    target_number = float(normalized_value) if isinstance(normalized_value, (int, float)) else None

    for page in pages:
        for region in page["regions"]:
            region_text = str(region.get("texte", ""))
            region_normalized = normalize(region_text)
            found = bool(normalized_text and normalized_text in region_normalized)
            if field_name == "cin" and target_digits:
                found = any(
                    re.sub(r"\D", "", candidate) == target_digits
                    for candidate in CIN_NUMBER_RE.findall(region_text)
                )
            elif not found and target_number is not None:
                parsed = parse_amount(region_text)
                found = parsed is not None and abs(parsed - target_number) < 0.005
            if found:
                return {
                    "confiance": safe_score(region.get("confiance")),
                    "page": page["page"],
                    "boite": region.get("boite"),
                    "source": region.get("langue", "PADDLEOCR"),
                }

    return {"confiance": None, "page": None, "boite": None, "source": "PADDLEOCR"}


def safe_score(value: Any) -> float:
    try:
        return max(0.0, min(1.0, float(value)))
    except (TypeError, ValueError):
        return 0.0


def normalize(value: str) -> str:
    digits = str.maketrans("٠١٢٣٤٥٦٧٨٩۰۱۲۳۴۵۶۷۸۹", "01234567890123456789")
    value = value.translate(digits)
    folded = unicodedata.normalize("NFKD", value.casefold())
    return "".join(char for char in folded if not unicodedata.combining(char))


def labeled_value(text: str, labels: list[str]) -> str | None:
    lines = text.splitlines()
    # Captions commonly used by the supported templates. This prevents a
    # missing value from accidentally consuming the following field caption.
    known_labels = {
        normalize(label) for label in (
            "nom", "nom et prénom", "prenom", "prénom", "name", "last name", "first name",
            "date de naissance", "employeur", "cin", "n cin", "numéro cin", "numero cin",
            "bénéficiaire", "beneficiaire", "titulaire", "banque", "compte", "période", "periode",
            "référence", "reference", "localisation", "adresse", "propriétaire", "proprietaire",
            "architecte", "projet", "date", "montant", "objet", "qualité", "qualite",
            "marque", "modèle", "modele", "vendeur", "acheteur", "client", "fonction",
            "matricule", "identifiant client", "devise", "nature du bien", "référence cadastrale",
            "reference cadastrale", "titre foncier", "référence foncière", "reference fonciere",
            "اللقب", "الاسم", "الاسم الشخصي", "تاريخ الميلاد", "الشركة",
        )
    }
    # These words extend a caption rather than form its value in the test
    # templates ("Nom et prénom", "Propriétaire déclaré", ...).
    follow_caption_extensions = ("et prenom", "declare", "declared", "fonciere fictive", "foncier fictif")
    # These are other, more specific captions. Do not consume their next line
    # while looking for a generic label such as "référence" or "employeur".
    skip_caption_extensions = ("fictif", "fictive", "de test", "du permis", "cadastrale", "et compte",
                               "du beneficiaire", "de l employeur", "devis")

    def is_caption_extension(value: str, extensions: tuple[str, ...]) -> bool:
        return any(re.match(rf"^{re.escape(extension)}(?:\W|$)", value) for extension in extensions)

    def is_next_field(line: str) -> bool:
        normalized_line = normalize(line.strip())
        return any(
            normalized_line == label
            or normalized_line.startswith(label + " :")
            or normalized_line.startswith(label + ":")
            or normalized_line.startswith(label + " -")
            for label in known_labels
        )

    def following_value(index: int) -> str | None:
        for next_line in lines[index + 1:index + 4]:
            candidate = next_line.strip()
            if not candidate:
                continue
            if is_next_field(candidate):
                return None
            return re.sub(r"^[:：\-]\s*", "", candidate)[:120]
        return None

    for index, line in enumerate(lines):
        clean_line = line.strip()
        normalized_line = normalize(clean_line)
        # A specific caption must take precedence over a generic one, e.g.
        # "référence du permis" before "référence".
        for label in sorted(labels, key=lambda item: len(normalize(item)), reverse=True):
            normalized_label = normalize(label)
            # PaddleOCR can return Arabic fields in visual order: value, then
            # caption. Prefer the adjacent Arabic value in that situation.
            if normalized_line == normalized_label and normalized_label in {"اللقب", "الاسم", "الاسم الشخصي"}:
                for previous in reversed(lines[max(0, index - 2):index]):
                    candidate = previous.strip()
                    if candidate and not is_next_field(candidate) and re.search(r"[\u0600-\u06ff]", candidate):
                        return candidate[:120]
            label_only = normalized_line.rstrip(" :：-").strip() == normalized_label
            if label_only:
                value = following_value(index)
                if value:
                    return value
                continue
            match = re.match(rf"^{re.escape(normalized_label)}(?:\s*[:：\-]\s*|\s+)(.+)$", normalized_line)
            if not match:
                continue

            value = match.group(1).strip()
            if value and is_caption_extension(value, skip_caption_extensions):
                continue
            if value and not is_caption_extension(value, follow_caption_extensions):
                original_match = re.match(
                    rf"^\s*{re.escape(label)}(?:\s*[:：\-]\s*|\s+)(.*?)\s*$",
                    clean_line,
                    re.IGNORECASE,
                )
                if original_match and original_match.group(1).strip():
                    return original_match.group(1).strip()[:120]
                return value[:120]
            following = following_value(index)
            if following:
                return following
    return None


def extract_fields(text: str, document_type: str) -> dict[str, dict[str, Any] | None]:
    # Import lazily so extractors can reuse the shared text utilities above
    # without creating an import cycle during FastAPI startup.
    from extractors.registry import extract_fields as extract_by_type

    return extract_by_type(text, document_type)


CIN_LABEL_RE = re.compile(
    r"(?:\bcin\b|carte\s+(?:nationale\s+d['’ ]identite|d['’ ]identite)|"
    r"identite\s+nationale|بطاقة\s*(?:تعريف|التعريف)|"
    r"رقم\s*(?:بطاقة\s*)?(?:تعريف|التعريف|الهوية))",
    re.IGNORECASE,
)
CIN_NUMBER_RE = re.compile(r"(?<!\d)(\d(?:[ \t-]?\d){7})(?!\d)")
DATE_RE = re.compile(r"(?<!\d)\d{1,2}[./-]\d{1,2}[./-]\d{2,4}(?!\d)")


def cin_candidate(fragment: str) -> str | None:
    date_spans = [match.span() for match in DATE_RE.finditer(fragment)]
    for match in CIN_NUMBER_RE.finditer(fragment):
        if any(match.start() < end and match.end() > start for start, end in date_spans):
            continue
        digits = re.sub(r"\D", "", match.group(1))
        if len(digits) == 8:
            return digits
    return None


def extract_cin(text: str) -> str | None:
    """Return a CIN only when OCR text ties the eight-digit value to an ID label.

    Unlabelled eight-digit strings are deliberately ignored: payroll references,
    telephone numbers and dates can otherwise be shown as the client's CIN.
    """
    lines = [normalize(line) for line in text.splitlines()]
    for index, line in enumerate(lines):
        label = CIN_LABEL_RE.search(line)
        if not label:
            continue

        # French layouts normally place the number after the label. Arabic OCR
        # can return the visual number first, so accept that order on the same line.
        value = cin_candidate(line[label.end():])
        if value:
            return value
        prefix = line[:label.start()].strip()
        if prefix and re.fullmatch(r"[\d \t-]+", prefix):
            value = cin_candidate(prefix)
            if value:
                return value

        # OCR often puts the caption and number on adjacent lines. Do not look
        # past another text field, where a phone or birth date could be mistaken.
        if index + 1 < len(lines):
            next_line = lines[index + 1].strip()
            if re.fullmatch(r"[\d \t-]+", next_line):
                value = cin_candidate(next_line)
                if value:
                    return value

    # The front of a Tunisian CIN is often emitted in visual (rather than
    # reading) order: its number is separate from the Arabic title. In that
    # specific identity-card context, a single eight-digit line is safe.
    normalized_text = "\n".join(lines)
    identity_context = (
        ("الجمهورية التونسية" in normalized_text and "بطاقة" in normalized_text)
        or ("بطاقة" in normalized_text and "التعريف" in normalized_text)
        or "carte nationale d identite" in normalized_text
    )
    if identity_context:
        candidates = []
        for line in lines:
            candidate = cin_candidate(line)
            if candidate and candidate not in candidates:
                candidates.append(candidate)
        if len(candidates) == 1:
            return candidates[0]
    return None


AMOUNT_RE = re.compile(
    r"(?<!\d)(\d{1,3}(?:[ \u00a0.]\d{3})+(?:,\d{1,3})?|\d+(?:[,.]\d{1,3})?)(?!\d)"
)


def parse_amount(value: str) -> float | None:
    value = normalize(value)
    clean = re.sub(r"[^\d,.]", "", value)
    if not clean:
        return None

    if "." in clean and "," in clean:
        decimal_separator = "." if clean.rfind(".") > clean.rfind(",") else ","
        grouping_separator = "," if decimal_separator == "." else "."
        clean = clean.replace(grouping_separator, "").replace(decimal_separator, ".")
    elif "," in clean:
        if clean.count(",") > 1:
            clean = clean.replace(",", "")
        else:
            # Tunisian payslips commonly use a comma followed by three millimes
            # (for example 600,000 TND = 600 dinars), even when the integer part
            # has only three digits.
            clean = clean.replace(",", ".")
    elif "." in clean:
        if clean.count(".") > 1:
            clean = clean.replace(".", "")
        elif len(clean.rsplit(".", 1)[1]) == 3 and len(clean.split(".", 1)[0]) <= 3:
            clean = clean.replace(".", "")

    try:
        amount = float(clean)
        return amount if 0 < amount < 1_000_000_000 else None
    except ValueError:
        return None


def extract_net_salary(text: str) -> float | None:
    lines = text.splitlines()
    primary_terms = (
        "net a payer", "net payable", "remuneration nette",
        "الاجر الصافي", "صافي الاجر",
    )
    secondary_terms = ("salaire net", "net mensuel")

    def amount_on_line(line: str) -> float | None:
        normalized_line = normalize(line)
        # Date fragments such as 31/10/2026 can otherwise be mistaken for a salary.
        if re.search(r"\b\d{1,2}[./-]\d{1,2}[./-]\d{2,4}\b", line) or any(
            marker in normalized_line for marker in ("date", "periode", "period")
        ):
            return None
        for raw_amount in reversed(AMOUNT_RE.findall(line)):
            parsed = parse_amount(raw_amount)
            if parsed is not None:
                return parsed
        return None

    normalized_lines = [normalize(line) for line in lines]

    # Prefer an amount written on the same line as a net-pay label, even if
    # the OCR returns another empty label nearby.
    for terms in (primary_terms, secondary_terms):
        for index, line in enumerate(lines):
            if any(normalize(term) in normalized_lines[index] for term in terms):
                parsed = amount_on_line(line)
                if parsed is not None:
                    return parsed

    # Some payslips place the label and its amount on separate OCR lines.
    # Search only a small window and skip intervening labels and date lines.
    for terms in (primary_terms, secondary_terms):
        for index, normalized_line in enumerate(normalized_lines):
            if not any(normalize(term) in normalized_line for term in terms):
                continue
            for next_line in lines[index + 1:index + 4]:
                parsed = amount_on_line(next_line)
                if parsed is not None:
                    return parsed
    return None


def extract_birth_date(text: str) -> str | None:
    lines = text.splitlines()
    for index, line in enumerate(lines):
        normalized_line = normalize(line)
        if any(term in normalized_line for term in ("naissance", "date de naissance", "born", "تاريخ الولادة", "تاريخ الميلاد")):
            for candidate in lines[index:index + 3]:
                match = re.search(r"\b(\d{1,2}[./-]\d{1,2}[./-]\d{2,4})\b", candidate)
                if match:
                    return match.group(1)
    return None


def detect_document_type(text: str) -> str:
    from extractors.classification import detect_document_type as classify

    return classify(text)
