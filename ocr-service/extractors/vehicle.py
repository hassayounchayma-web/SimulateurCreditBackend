from typing import Any

from .common import amount_field, date_field, null_fields, text_field


def extract_proforma_invoice(text: str) -> dict[str, dict[str, Any] | None]:
    fields = null_fields(["fournisseur", "client", "marque", "modele", "montant", "date", "reference"])
    fields["fournisseur"] = text_field(text, ["fournisseur", "vendeur", "société"])
    fields["client"] = text_field(text, ["client", "acheteur", "destinataire"])
    fields["marque"] = text_field(text, ["marque", "brand"])
    fields["modele"] = text_field(text, ["modèle", "modele", "type véhicule"])
    fields["montant"] = amount_field(text, ["montant total", "total ttc", "prix total", "total"])
    fields["date"] = date_field(text, ["date", "date facture", "date d'émission"])
    fields["reference"] = text_field(text, ["référence", "ref.", "numéro facture"])
    return fields


def extract_vehicle_registration(text: str) -> dict[str, dict[str, Any] | None]:
    fields = null_fields(["immatriculation", "marque", "modele", "datePremiereMiseEnCirculation", "proprietaire"])
    fields["immatriculation"] = text_field(text, ["immatriculation", "numéro d'immatriculation", "matricule"])
    fields["marque"] = text_field(text, ["marque", "constructeur"])
    fields["modele"] = text_field(text, ["modèle", "type"])
    fields["datePremiereMiseEnCirculation"] = date_field(text, ["première mise en circulation", "date de première circulation"])
    fields["proprietaire"] = text_field(text, ["propriétaire", "titulaire"])
    return fields
