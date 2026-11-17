from typing import Any

from app import extract_net_salary
from .common import amount_field, date_field, identity_fields, null_fields, text_field


def extract_payslip(text: str) -> dict[str, dict[str, Any] | None]:
    fields = null_fields(["cin", "nom", "prenom", "employeur", "periode", "salaireBrut", "salaireNet", "retenues"])
    fields.update(identity_fields(text))
    fields["employeur"] = text_field(text, ["employeur", "employer", "société", "raison sociale", "الشركة"])
    fields["periode"] = text_field(text, ["période", "mois de paie", "bulletin du mois", "période de paie"])
    fields["salaireBrut"] = amount_field(text, ["total brut", "salaire brut", "brut imposable"])
    net = amount_field(text, ["net à payer", "net a payer", "salaire net", "net mensuel"])
    if net is None:
        parsed_net = extract_net_salary(text)
        net = {"rawValue": parsed_net, "normalizedValue": parsed_net} if parsed_net is not None else None
    fields["salaireNet"] = net
    fields["retenues"] = amount_field(text, ["total des retenues", "total retenues", "retenues totales"])
    return fields


def extract_annual_salary_attestation(text: str) -> dict[str, dict[str, Any] | None]:
    fields = null_fields(["nom", "prenom", "cin", "employeur", "salaireAnnuel", "salaireMensuel", "periode", "dateEmission"])
    fields.update(identity_fields(text))
    fields["cin"] = None
    fields["employeur"] = text_field(text, ["employeur", "employer", "société", "raison sociale"])
    fields["salaireAnnuel"] = amount_field(text, ["salaire annuel", "revenu annuel", "rémunération annuelle"])
    fields["salaireMensuel"] = amount_field(text, ["salaire mensuel", "revenu mensuel", "rémunération mensuelle"])
    fields["periode"] = text_field(text, ["période", "année concernée", "année de référence"])
    fields["dateEmission"] = date_field(text, ["date d'émission", "date de délivrance", "fait à"])
    return fields


def extract_work_attestation(text: str) -> dict[str, dict[str, Any] | None]:
    fields = null_fields(["nom", "prenom", "cin", "employeur", "fonction", "anciennete", "typeContrat", "dateEmission"])
    fields.update(identity_fields(text))
    fields["cin"] = None
    fields["employeur"] = text_field(text, ["employeur", "employer", "société", "raison sociale"])
    fields["fonction"] = text_field(text, ["fonction", "poste", "emploi occupé", "profession"])
    fields["anciennete"] = text_field(text, ["ancienneté", "date d'embauche", "depuis le"])
    fields["typeContrat"] = text_field(text, ["type de contrat", "contrat", "nature du contrat"])
    fields["dateEmission"] = date_field(text, ["date d'émission", "date de délivrance", "fait à"])
    return fields


def extract_other_income(text: str) -> dict[str, dict[str, Any] | None]:
    fields = null_fields(["beneficiaire", "natureRevenu", "montant", "periode", "date"])
    fields["beneficiaire"] = text_field(text, ["bénéficiaire", "titulaire", "nom et prénom"])
    fields["natureRevenu"] = text_field(text, ["nature du revenu", "type de revenu", "revenu provenant de"])
    fields["montant"] = amount_field(text, ["montant", "revenu net", "somme versée"])
    fields["periode"] = text_field(text, ["période", "par mois", "annuel", "mensuel"])
    fields["date"] = date_field(text, ["date", "date d'émission", "date de paiement"])
    return fields


def extract_bank_statement(text: str) -> dict[str, dict[str, Any] | None]:
    fields = null_fields(["titulaire", "banque", "numeroCompte", "periode", "solde", "soldeMoyen"])
    fields["titulaire"] = text_field(text, ["titulaire du compte", "titulaire", "nom du client"])
    fields["banque"] = text_field(text, ["banque", "établissement bancaire"])
    fields["numeroCompte"] = text_field(text, ["numéro de compte", "compte n°", "iban"])
    fields["periode"] = text_field(text, ["période", "relevé du", "du ... au"])
    fields["solde"] = amount_field(text, ["solde final", "nouveau solde", "solde disponible"])
    fields["soldeMoyen"] = amount_field(text, ["solde moyen", "moyenne des soldes"])
    return fields
