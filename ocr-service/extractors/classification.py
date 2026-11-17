"""Document-type detection resilient to OCR line ordering.

PaddleOCR can emit a title as several independent lines (for example
``BULLETIN`` then ``SALAIRE``).  Looking for one literal sentence in the full
text therefore caused valid documents to be marked as unknown.  The heading is
also more reliable than a word occurring in a disclaimer at the end of a page.
"""

import re

from app import normalize


# type, exact title variants, token groups that may be split over OCR lines
DOCUMENT_PROFILES = (
    ("RELEVE_BANCAIRE", ("releve bancaire", "releve de compte", "account statement"), (("releve", "bancaire"),)),
    ("BULLETIN_SALAIRE", ("bulletin de salaire", "bulletin salaire", "bulletin de paie", "fiche de paie"), (("bulletin", "salaire"),)),
    ("ATTESTATION_SALAIRE_ANNUELLE", ("attestation annuelle de salaire", "attestation de salaire annuel"), (("attestation", "salaire", "annuel"),)),
    ("ATTESTATION_TRAVAIL", ("attestation de travail", "certificat de travail"), (("attestation", "travail"),)),
    ("JUSTIFICATIF_AUTRES_REVENUS", ("justificatif de revenus", "attestation de revenu"), (("justificatif", "revenus"),)),
    ("FACTURE_PRO_FORMA", ("facture pro forma", "facture proforma"), (("facture", "pro", "forma"),)),
    ("PROMESSE_VENTE", ("promesse de vente", "compromis de vente"), (("promesse", "vente"),)),
    ("CARTE_GRISE", ("carte grise", "certificat d immatriculation"), (("carte", "grise"),)),
    # This profile intentionally precedes TITRE_FONCIER. A property proof can
    # mention a "titre foncier" in an explanatory paragraph.
    ("JUSTIFICATIF_PROPRIETE", ("justificatif de propriete", "preuve de propriete", "attestation de propriete"), (("justificatif", "propriete"),)),
    ("TITRE_FONCIER", ("titre foncier", "conservation fonciere", "propriete fonciere"), (("titre", "foncier"),)),
    ("ACTE_NOTARIE", ("acte notarie", "acte authentique", "etude notariale"), (("acte", "notarie"),)),
    ("PERMIS_BATIR", ("permis de batir", "permis batir", "permis de construire"), (("permis", "batir"),)),
    ("DEVIS_ARCHITECTE", ("devis d architecte", "devis architecte", "honoraires d architecte"), (("devis", "architecte"),)),
    ("EXPERTISE_IMMOBILIERE", ("expertise immobiliere", "rapport d expertise immobiliere"), (("expertise", "immobiliere"),)),
    ("DEVIS_TRAVAUX", ("devis de travaux", "devis travaux"), (("devis", "travaux"),)),
    ("DEMANDE_CREDIT", ("demande de credit", "demande de financement"), (("demande", "credit"),)),
    ("CIN", ("carte nationale d identite", "carte d identite", "identite nationale"),
     (("carte", "identite"), ("بطاقة", "التعريف"), ("الجمهورية", "التونسية", "بطاقة"))),
)


def _words(value: str) -> str:
    # Retain Arabic characters as they are needed for Tunisian CIN detection.
    return re.sub(r"[^a-z0-9\u0600-\u06ff]+", " ", normalize(value)).strip()


def _contains_all(scope: str, terms: tuple[str, ...]) -> bool:
    padded = f" {scope} "
    return all(f" {term} " in padded for term in terms)


def _score_heading(scope: str, phrases: tuple[str, ...], groups: tuple[tuple[str, ...], ...]) -> int:
    score = 0
    for phrase in phrases:
        phrase_words = _words(phrase)
        if phrase_words and phrase_words in scope:
            score = max(score, 100 + len(phrase_words.split()))
    for group in groups:
        normalized_group = tuple(_words(term) for term in group)
        if _contains_all(scope, normalized_group):
            # A complete title token group in the heading is stronger evidence
            # than a literal phrase found in a later explanatory sentence.
            score = max(score, 110 + len(group))
    return score


def detect_document_type(text: str) -> str:
    lines = [line for line in text.splitlines() if line.strip()]
    # The title occurs at the beginning of all supported documents. Do not let
    # a disclaimer lower in the document override it.
    heading = _words(" ".join(lines[:36]))
    candidates = [
        (score, -index, document_type)
        for index, (document_type, phrases, groups) in enumerate(DOCUMENT_PROFILES)
        if (score := _score_heading(heading, phrases, groups))
    ]
    if candidates:
        return max(candidates)[2]

    # Fallback for an OCR result where no top area was returned. Literal
    # phrases are safe here; token groups are deliberately not used.
    full_text = _words(text)
    for document_type, phrases, _ in DOCUMENT_PROFILES:
        if any(_words(phrase) in full_text for phrase in phrases):
            return document_type
    return "INCONNU"
