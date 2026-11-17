from typing import Any, Callable

from .classification import detect_document_type
from .identity import extract_cin_document, extract_credit_application
from .income import (
    extract_annual_salary_attestation,
    extract_bank_statement,
    extract_other_income,
    extract_payslip,
    extract_work_attestation,
)
from .property import (
    extract_architect_quote,
    extract_building_permit,
    extract_land_title,
    extract_notarial_deed,
    extract_property_proof,
    extract_property_appraisal,
    extract_sale_promise,
    extract_works_quote,
)
from .vehicle import extract_proforma_invoice, extract_vehicle_registration


Extractor = Callable[[str], dict[str, dict[str, Any] | None]]

EXTRACTORS: dict[str, Extractor] = {
    "CIN": extract_cin_document,
    "BULLETIN_SALAIRE": extract_payslip,
    "ATTESTATION_SALAIRE_ANNUELLE": extract_annual_salary_attestation,
    "ATTESTATION_TRAVAIL": extract_work_attestation,
    "JUSTIFICATIF_AUTRES_REVENUS": extract_other_income,
    "RELEVE_BANCAIRE": extract_bank_statement,
    "DEMANDE_CREDIT": extract_credit_application,
    "DEVIS_TRAVAUX": extract_works_quote,
    "FACTURE_PRO_FORMA": extract_proforma_invoice,
    "PROMESSE_VENTE": extract_sale_promise,
    "CARTE_GRISE": extract_vehicle_registration,
    "TITRE_FONCIER": extract_land_title,
    "JUSTIFICATIF_PROPRIETE": extract_property_proof,
    "ACTE_NOTARIE": extract_notarial_deed,
    "PERMIS_BATIR": extract_building_permit,
    "DEVIS_ARCHITECTE": extract_architect_quote,
    "EXPERTISE_IMMOBILIERE": extract_property_appraisal,
}

REQUIRED_FIELDS: dict[str, tuple[str, ...]] = {
    "CIN": ("cin", "nom", "prenom"),
    "BULLETIN_SALAIRE": ("nom", "prenom", "periode", "salaireNet"),
    "ATTESTATION_SALAIRE_ANNUELLE": ("nom", "prenom", "employeur"),
    "ATTESTATION_TRAVAIL": ("nom", "prenom", "employeur"),
    "JUSTIFICATIF_AUTRES_REVENUS": ("beneficiaire", "natureRevenu", "montant"),
    "RELEVE_BANCAIRE": ("titulaire", "periode"),
    "DEMANDE_CREDIT": ("nom", "prenom", "montantDemande"),
    "DEVIS_TRAVAUX": ("entreprise", "montantTotal"),
    "FACTURE_PRO_FORMA": ("fournisseur", "montant"),
    "PROMESSE_VENTE": ("acheteur", "vendeur", "prix"),
    "CARTE_GRISE": ("immatriculation", "marque", "datePremiereMiseEnCirculation"),
    "TITRE_FONCIER": ("proprietaire", "referenceFonciere"),
    "JUSTIFICATIF_PROPRIETE": ("proprietaire",),
    "ACTE_NOTARIE": ("parties", "date"),
    "PERMIS_BATIR": ("beneficiaire", "reference"),
    "DEVIS_ARCHITECTE": ("architecte", "projet", "montant"),
    "EXPERTISE_IMMOBILIERE": ("expert", "bien", "valeurEstimee"),
}


def extract_fields(text: str, document_type: str) -> dict[str, dict[str, Any] | None]:
    extractor = EXTRACTORS.get(document_type)
    if extractor is None:
        return {}
    return extractor(text)


def extraction_status(document_type: str, fields: dict[str, dict[str, Any] | None]) -> str:
    required = REQUIRED_FIELDS.get(document_type, ())
    present = sum(fields.get(name) is not None for name in required)
    if not required:
        return "EXTRACTION_INCOMPLETE"
    if present == len(required):
        return "SUCCESS"
    if present:
        return "PARTIAL_SUCCESS"
    return "EXTRACTION_INCOMPLETE"
