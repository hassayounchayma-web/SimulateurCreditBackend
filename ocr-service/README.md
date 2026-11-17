# Microservice OCR PaddleOCR

Service HTTP local utilisé par le backend Spring pour lire les justificatifs de crédit. Il n'envoie pas les documents à Gemini ni à un service cloud. Les scans sont temporairement écrits pendant l'inférence puis supprimés ; les originaux restent dans le stockage local du backend.

## Démarrage avec Docker

Depuis ce dossier :

```powershell
docker compose up -d --build
```

Sous Windows, démarrer Docker Desktop avant cette commande. Le premier traitement télécharge les modèles PaddleOCR et peut prendre plus de temps. Le volume `paddle-models` conserve les modèles pour les lancements suivants. Le service expose `/health` et `POST /api/ocr/extract` sur le port `8090`, lié à `localhost`.

## Démarrage avec Python

Utiliser Python 3.11 et un environnement virtuel. Installer le moteur CPU PaddlePaddle, puis les dépendances :

```powershell
python -m venv .venv
.\.venv\Scripts\Activate.ps1
python -m pip install paddlepaddle==3.2.0
python -m pip install -r requirements.txt
python -m uvicorn app:app --host 127.0.0.1 --port 8090
```

Le backend appelle `http://localhost:8090/api/ocr/extract`. Pour changer cette adresse, définir `PADDLE_OCR_URL` avant le démarrage du backend. Les formats acceptés sont PDF, JPG/JPEG et PNG, 10 Mo maximum par pièce.

## Modèles et extraction

Le service utilise deux pipelines PP-OCRv5 CPU, en français et en arabe. Il renvoie le texte reconnu, un score moyen de reconnaissance et des extractions simples par règles (CIN, nom, prénom, date de naissance, employeur et salaire net). L'agent doit vérifier les valeurs avant toute étude du dossier.
