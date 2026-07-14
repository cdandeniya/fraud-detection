# Fraud model service

A tiny Python service that serves a logistic-regression fraud model to the Java pipeline.

```bash
pip install -r requirements.txt
python train.py            # writes model.joblib
uvicorn app:app --port 8000
```

Then:

```bash
curl -X POST http://localhost:8000/predict \
  -H "Content-Type: application/json" \
  -d '{"amount": 5000, "velocity": 9}'
# -> {"probability": 0.9x}
```

Features are `amount` and `velocity` (transactions by this card in the last 5 minutes).
Trained on synthetic data (see `train.py`); swap in the Kaggle credit-card fraud dataset
later for something more realistic.
