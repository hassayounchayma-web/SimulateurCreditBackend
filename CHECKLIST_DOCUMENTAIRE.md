# Checklist documentaire des dossiers de crédit

## Architecture

- `DocumentChecklistService` centralise les règles à partir du libellé déjà présent dans `simulation.typeCredit`.
- `POST /api/dossiers/checklist` renvoie les pièces attendues, les alternatives, les règles en attente et l'état de complétude.
- `DossierService` vérifie la checklist avant de créer un dossier, enregistre les fichiers et transmet chacun à `PaddleOcrClient`.
- `DossierDocument` conserve chaque version, son résultat OCR, les validations, les corrections et leurs historiques.
- Le statut du dossier reflète la validation documentaire. Il ne décide jamais de l'octroi du crédit.

## Règles intégrées

| Produit existant | Documents spécifiques |
| --- | --- |
| Crédit Direct Dépenses Courantes | Demande créée automatiquement avec le dossier ; aucun justificatif de dépenses ajouté |
| Crédit Direct Aménagement | Devis des travaux |
| Crédit Direct Acquisition Véhicule Neuf | Facture pro forma |
| Crédit Direct Acquisition Véhicule Occasion | Promesse de vente et carte grise ; véhicule de moins de 3 ans |
| Crédit Direct Aménagement Hypothécaire | Devis des travaux et justificatif de propriété ; expertise soumise au seuil configuré |
| Crédit Direct Acquisition Logement, promoteur | Promesse de vente et justificatif de propriété ; agrément à vérifier manuellement |
| Crédit Direct Acquisition Logement, particulier | Promesse de vente, expertise, puis titre foncier récent **ou** acte notarié |
| Crédit Direct Construction | Devis d'architecte, permis de bâtir et justificatif de propriété ; expertise soumise au seuil configuré |
| Crédit Direct Achat Terrain, promoteur | Justificatif de propriété ; agrément à vérifier manuellement |
| Crédit Direct Achat Terrain, particulier | Titre foncier récent et expertise du terrain |

Tous les produits exigent CIN et dernier bulletin de salaire. Le relevé bancaire reste facultatif. Le justificatif d'autres revenus devient exigé uniquement si la déclaration correspondante est cochée. Les attestations de travail et de salaire annuel sont prises en charge par OCR mais restent inactives en attendant confirmation.

Les scénarios `SANS_RACHAT`, `RACHAT_INTERNE` et `RACHAT_EXTERNE` ne changent pas ces règles.

## Configuration

Les propriétés suivantes sont sous `app.dossier.checklist` dans `application.yml` et restent désactivées par défaut lorsqu'une règle officielle manque :

- `DOSSIER_EXPERTISE_SEUIL` : montant à partir duquel l'expertise des produits 5 et 7 devient obligatoire.
- `DOSSIER_TITRE_RECENT_JOURS` : nombre de jours définissant la récence d'un titre foncier.
- `DOSSIER_VEHICULE_DATE_REFERENCE` : date ISO `AAAA-MM-JJ` utilisée pour contrôler l'âge du véhicule ; la date du jour est utilisée si vide.
- `DOSSIER_ATTESTATION_ANNUELLE_OBLIGATOIRE` : `true` uniquement après confirmation.
- `DOSSIER_ATTESTATION_TRAVAIL_OBLIGATOIRE` : `true` uniquement après confirmation.

Aucune source officielle d'agrément des promoteurs n'est intégrée ; la vérification reste manuelle.

## API d'administration

- `GET /api/admin/dossiers` et `GET /api/admin/dossiers/{id}` : consultation de la checklist et de l'historique des pièces.
- `GET /api/admin/dossiers/{id}/documents/{documentId}/file` : téléchargement du fichier original.
- `PATCH /api/admin/dossiers/{id}/documents/{documentId}/review` : statut `VALIDE`, `REJETE` ou `REMPLACEMENT_DEMANDE`, motif et corrections au format JSON.
- `PATCH /api/admin/dossiers/{id}/promoteur-agrement` : vérification manuelle `VERIFIE`, `NON_VERIFIE` ou `A_VERIFIER`, avec une note de contrôle.
- `POST /api/admin/dossiers/{id}/documents/{documentId}/reanalyze` : nouvelle analyse PaddleOCR, l'ancienne analyse reste dans l'historique.
- `POST /api/dossiers/{id}/documents` : remplacement d'une pièce par son propriétaire ; les fichiers précédents ne sont pas écrasés.

Les nouveaux champs et la table d'historique sont créés par Hibernate (`ddl-auto: update`) lors du démarrage du backend.
