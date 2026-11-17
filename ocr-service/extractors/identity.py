from typing import Any

from app import extract_cin
from .common import date_field, identity_fields, null_fields, text_field


def extract_cin_document(text: str) -> dict[str, dict[str, Any] | None]:
    fields = null_fields(["cin", "nom", "prenom", "dateNaissance", "dateExpiration"])
    number = extract_cin(text)
    fields["cin"] = {"rawValue": number, "normalizedValue": number} if number else None
    fields.update(identity_fields(text))
    fields["dateExpiration"] = date_field(text, ["date d'expiration", "date expiration", "expire le", "validité jusqu'au"])
    return fields


def extract_credit_application(text: str) -> dict[str, dict[str, Any] | None]:
    fields = null_fields(["cin", "nom", "prenom", "dateNaissance", "montantDemande", "objetCredit", "reference"])
    fields.update(identity_fields(text))
    number = extract_cin(text)
    fields["cin"] = {"rawValue": number, "normalizedValue": number} if number else None
    fields["montantDemande"] = text_field(text, ["montant demandé", "montant du crédit", "montant sollicité"])
    fields["objetCredit"] = text_field(text, ["objet du crédit", "objet de la demande", "type de crédit"])
    fields["reference"] = text_field(text, ["référence", "reference dossier", "numéro de demande"])
    return fields
