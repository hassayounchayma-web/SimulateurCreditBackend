from typing import Any

from .common import amount_field, date_field, null_fields, text_field


def extract_works_quote(text: str) -> dict[str, dict[str, Any] | None]:
    fields = null_fields(["entreprise", "beneficiaire", "natureTravaux", "montantTotal", "date", "reference"])
    fields["entreprise"] = text_field(text, ["entreprise", "entrepreneur", "société"])
    fields["beneficiaire"] = text_field(text, ["bénéficiaire", "client", "maître d'ouvrage"])
    fields["natureTravaux"] = text_field(text, ["nature des travaux", "désignation", "objet"])
    fields["montantTotal"] = amount_field(text, ["montant total", "total ttc", "total à payer"])
    fields["date"] = date_field(text, ["date", "date du devis"])
    fields["reference"] = text_field(text, ["référence", "numéro du devis"])
    return fields


def extract_sale_promise(text: str) -> dict[str, dict[str, Any] | None]:
    fields = null_fields(["acheteur", "vendeur", "bien", "localisation", "prix", "date", "marque", "modele", "immatriculation"])
    fields["acheteur"] = text_field(text, ["acheteur", "acquéreur"])
    fields["vendeur"] = text_field(text, ["vendeur", "cédant", "propriétaire"])
    fields["bien"] = text_field(text, ["bien vendu", "désignation du bien", "objet de la vente"])
    fields["localisation"] = text_field(text, ["localisation", "adresse du bien", "situé à"])
    fields["prix"] = amount_field(text, ["prix de vente", "prix", "montant de la vente"])
    fields["date"] = date_field(text, ["date", "fait le", "date de signature"])
    fields["marque"] = text_field(text, ["marque", "constructeur"])
    fields["modele"] = text_field(text, ["modèle", "modele"])
    fields["immatriculation"] = text_field(text, ["immatriculation", "numéro d'immatriculation", "matricule"])
    return fields


def extract_land_title(text: str) -> dict[str, dict[str, Any] | None]:
    fields = null_fields(["proprietaire", "referenceFonciere", "localisation", "date"])
    fields["proprietaire"] = text_field(text, ["propriétaire", "titulaire", "inscrit au nom de"])
    fields["referenceFonciere"] = text_field(text, ["titre foncier", "référence foncière", "numéro du titre"])
    fields["localisation"] = text_field(text, ["localisation", "situation", "adresse"])
    fields["date"] = date_field(text, ["date", "date d'inscription"])
    return fields


def extract_property_proof(text: str) -> dict[str, dict[str, Any] | None]:
    fields = null_fields(["proprietaire", "referenceFonciere", "localisation", "bien", "date"])
    fields["proprietaire"] = text_field(text, ["propriétaire déclaré", "propriétaire", "titulaire", "inscrit au nom de"])
    fields["referenceFonciere"] = text_field(text, ["référence foncière fictive", "référence foncière", "numéro du titre", "titre foncier"])
    fields["localisation"] = text_field(text, ["localisation", "situation", "adresse du bien", "situé à"])
    fields["bien"] = text_field(text, ["désignation du bien", "désignation du terrain", "nature du bien"])
    fields["date"] = date_field(text, ["date", "date d'inscription"])
    return fields


def extract_notarial_deed(text: str) -> dict[str, dict[str, Any] | None]:
    fields = null_fields(["parties", "notaire", "bien", "references", "date"])
    fields["parties"] = text_field(text, ["parties", "entre", "comparants"])
    fields["notaire"] = text_field(text, ["notaire", "maître", "étude notariale"])
    fields["bien"] = text_field(text, ["désignation du bien", "objet", "immeuble"])
    fields["references"] = text_field(text, ["références", "titre foncier", "numéro d'acte"])
    fields["date"] = date_field(text, ["date", "fait le", "date de signature"])
    return fields


def extract_building_permit(text: str) -> dict[str, dict[str, Any] | None]:
    fields = null_fields(["beneficiaire", "localisation", "reference", "date", "objet"])
    fields["beneficiaire"] = text_field(text, ["bénéficiaire", "demandeur"])
    fields["localisation"] = text_field(text, ["localisation", "adresse", "sis à"])
    fields["reference"] = text_field(text, ["référence du permis", "numéro du permis", "référence"])
    fields["date"] = date_field(text, ["date de délivrance", "délivré le", "date"])
    fields["objet"] = text_field(text, ["nature du projet", "objet du permis", "nature des travaux", "construction de"])
    return fields


def extract_architect_quote(text: str) -> dict[str, dict[str, Any] | None]:
    fields = null_fields(["architecte", "beneficiaire", "projet", "montant", "date"])
    fields["architecte"] = text_field(text, ["architecte", "architecte conseil", "bureau d'architecture"])
    fields["beneficiaire"] = text_field(text, ["bénéficiaire", "client", "maître d'ouvrage"])
    fields["projet"] = text_field(text, ["projet", "objet de la mission", "description"])
    fields["montant"] = amount_field(text, ["montant total", "honoraires", "total ttc"])
    fields["date"] = date_field(text, ["date", "date du devis"])
    return fields


def extract_property_appraisal(text: str) -> dict[str, dict[str, Any] | None]:
    fields = null_fields(["expert", "bien", "localisation", "valeurEstimee", "dateExpertise"])
    fields["expert"] = text_field(text, ["expert", "expertise réalisée par", "organisme"])
    fields["bien"] = text_field(text, ["désignation du bien", "nature du bien", "bien expertisé"])
    fields["localisation"] = text_field(text, ["localisation", "adresse du bien", "situé à"])
    fields["valeurEstimee"] = amount_field(text, ["valeur estimée", "valeur vénale", "valeur du bien"])
    fields["dateExpertise"] = date_field(text, ["date de l'expertise", "date expertise", "date"])
    return fields
