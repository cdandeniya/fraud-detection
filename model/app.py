"""
FastAPI wrapper that serves the fraud model over HTTP.

The Java scoring service POSTs a transaction's features here and gets back a fraud
probability. Keeping the model behind a small service means Python owns training and
serving while Java owns the pipeline - a clean language boundary.
"""

from fastapi import FastAPI
from pydantic import BaseModel

from train import ensure_model

app = FastAPI(title="fraud-model")
model = ensure_model()


class Features(BaseModel):
    amount: float
    velocity: float


class Prediction(BaseModel):
    probability: float


@app.get("/health")
def health():
    return {"status": "ok"}


@app.post("/predict", response_model=Prediction)
def predict(features: Features):
    prob = float(model.predict_proba([[features.amount, features.velocity]])[0][1])
    return Prediction(probability=prob)
