import unittest

from app import (
    detect_document_type,
    extract_birth_date,
    extract_cin,
    extract_fields,
    extract_net_salary,
    labeled_value,
    parse_amount,
)
from extractors.registry import EXTRACTORS, extraction_status


class ExtractorRegressionTests(unittest.TestCase):
    def test_extracts_labeled_grouped_cin(self):
        self.assertEqual(extract_cin("N° CIN : 12 345 678"), "12345678")

    def test_extracts_cin_on_the_next_line(self):
        self.assertEqual(extract_cin("Carte nationale d'identité\n01234567"), "01234567")

    def test_normalizes_arabic_cin_digits(self):
        self.assertEqual(extract_cin("رقم بطاقة التعريف: ١٢٣٤٥٦٧٨"), "12345678")

    def test_does_not_use_an_unlabeled_eight_digit_value(self):
        self.assertIsNone(extract_cin("Téléphone : 12345678\nMatricule : 87654321"))

    def test_does_not_use_birth_date_after_cin_label(self):
        text = "CIN :\nDate de naissance :\n15/04/1995\nTéléphone :\n12345678"
        self.assertIsNone(extract_cin(text))

    def test_does_not_read_dashed_date_as_cin(self):
        self.assertIsNone(extract_cin("CIN : 15-04-1995"))

    def test_extracts_values_when_labels_are_on_previous_lines(self):
        self.assertEqual(labeled_value("Nom :\nBEN YOUSSEF", ["nom"]), "BEN YOUSSEF")

    def test_reads_birth_date_from_next_line(self):
        self.assertEqual(extract_birth_date("Date de naissance :\n15/04/1995"), "15/04/1995")

    def test_salary_extractor_ignores_document_year(self):
        text = (
            "Date d'émission : 31/10/2026\n"
            "NET À PAYER\n"
            "Salaire net mensuel\n"
            "DOCUMENT DE DÉMONSTRATION - SALAIRE NET : 2 000,000 TND"
        )
        self.assertEqual(extract_net_salary(text), 2000.0)

    def test_payslip_extractor_returns_raw_and_normalized_salary(self):
        text = (
            "BULLETIN DE SALAIRE\nNom :\nBEN YOUSSEF\nPrénom :\nSamira\n"
            "Période :\nOctobre 2026\nNET À PAYER\n2 000,000 TND"
        )
        fields = extract_fields(text, "BULLETIN_SALAIRE")
        self.assertEqual(fields["nom"]["normalizedValue"], "BEN YOUSSEF")
        self.assertEqual(fields["salaireNet"]["rawValue"], "2 000,000")
        self.assertEqual(fields["salaireNet"]["normalizedValue"], 2000.0)

    def test_parses_tunisian_three_digit_millimes_as_decimal_amount(self):
        self.assertEqual(parse_amount("600,000"), 600.0)
        self.assertEqual(parse_amount("2 600,000"), 2600.0)

    def test_supported_types_have_separate_extractors(self):
        self.assertEqual(len(EXTRACTORS), 17)
        self.assertIn("EXPERTISE_IMMOBILIERE", EXTRACTORS)
        self.assertIn("CARTE_GRISE", EXTRACTORS)
        self.assertIn("JUSTIFICATIF_PROPRIETE", EXTRACTORS)

    def test_specific_classification(self):
        self.assertEqual(detect_document_type("FACTURE PRO FORMA"), "FACTURE_PRO_FORMA")
        self.assertEqual(detect_document_type("PROMESSE DE VENTE"), "PROMESSE_VENTE")
        self.assertEqual(detect_document_type("ATTESTATION DE TRAVAIL"), "ATTESTATION_TRAVAIL")
        self.assertEqual(detect_document_type("JUSTIFICATIF DE PROPRIETE"), "JUSTIFICATIF_PROPRIETE")

    def test_classifies_titles_split_on_multiple_ocr_lines(self):
        self.assertEqual(detect_document_type("DOSSIER\nBULLETIN\nSALAIRE\nPériode"), "BULLETIN_SALAIRE")
        self.assertEqual(detect_document_type("DEVIS\nD'ARCHITECTE\nBénéficiaire"), "DEVIS_ARCHITECTE")
        self.assertEqual(detect_document_type("PERMIS\nBÂTIR\nBénéficiaire"), "PERMIS_BATIR")

    def test_property_heading_wins_over_titre_foncier_in_disclaimer(self):
        text = "propriété\nJUSTIFICATIF\nPropriétaire déclaré\nAmina\nCe document ne remplace pas un titre foncier"
        self.assertEqual(detect_document_type(text), "JUSTIFICATIF_PROPRIETE")

    def test_extracts_tunisian_cin_when_arabic_title_and_number_are_separate(self):
        text = "14516999\nالجمهورية التونسية\nالتعريف الوطنية\nبطاقة\nاللقب\nحسيون"
        self.assertEqual(extract_cin(text), "14516999")

    def test_reads_split_caption_values_without_consuming_the_next_caption(self):
        text = "Bénéficiaire\nCHAYMA HASSAYOUN\nCIN\n14516999\nPériode :\n: octobre 2026"
        self.assertEqual(labeled_value(text, ["bénéficiaire"]), "CHAYMA HASSAYOUN")
        self.assertEqual(labeled_value(text, ["période"]), "octobre 2026")

    def test_reads_arabic_visual_order_fields(self):
        self.assertEqual(labeled_value("حسيون\nاللقب\nشماء\nالاسم\nميلاد", ["اللقب"]), "حسيون")
        self.assertEqual(labeled_value("حسيون\nاللقب\nشماء\nالاسم\nميلاد", ["الاسم"]), "شماء")

    def test_property_proof_extractor_reads_owner_and_reference(self):
        fields = extract_fields(
            "JUSTIFICATIF DE PROPRIETE\nPropriétaire :\nAmina Ben Salem\nRéférence foncière :\nTF-2026-18",
            "JUSTIFICATIF_PROPRIETE",
        )
        self.assertEqual(fields["proprietaire"]["normalizedValue"], "Amina Ben Salem")
        self.assertEqual(fields["referenceFonciere"]["normalizedValue"], "TF-2026-18")

    def test_vehicle_sale_promise_extracts_make_and_model_for_comparison(self):
        fields = extract_fields(
            "PROMESSE DE VENTE\nAcheteur : Sami\nVendeur : Ali\nMarque : Renault\nModèle : Clio\nPrix : 20 000,000",
            "PROMESSE_VENTE",
        )
        self.assertEqual(fields["marque"]["normalizedValue"], "Renault")
        self.assertEqual(fields["modele"]["normalizedValue"], "Clio")

    def test_missing_essential_fields_are_partial(self):
        self.assertEqual(extraction_status("CIN", {"cin": None, "nom": None, "prenom": None}), "EXTRACTION_INCOMPLETE")
        self.assertEqual(extraction_status("CIN", {"cin": {"normalizedValue": "12345678"}, "nom": None, "prenom": None}), "PARTIAL_SUCCESS")


if __name__ == "__main__":
    unittest.main()
