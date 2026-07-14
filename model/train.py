"""
Trains a small logistic-regression fraud model and saves it to model.joblib.

The features are deliberately simple so they line up with what the Java service can
cheaply compute for every transaction:
    - amount        : the transaction amount
    - velocity      : how many times the card was used in the last 5 minutes

Real projects would train on something like the Kaggle credit-card fraud dataset; here
I generate synthetic labeled data so the repo is self-contained and reproducible.
"""

import os
import numpy as np
from sklearn.linear_model import LogisticRegression
from sklearn.model_selection import train_test_split
import joblib

MODEL_PATH = os.path.join(os.path.dirname(__file__), "model.joblib")


def _make_dataset(n: int = 20000, seed: int = 42):
    rng = np.random.default_rng(seed)

    # legit transactions: small amounts, low velocity
    legit_n = int(n * 0.97)
    legit_amount = rng.gamma(shape=2.0, scale=40.0, size=legit_n)      # ~$80 avg
    legit_velocity = rng.poisson(lam=1.0, size=legit_n)

    # fraud: larger amounts and/or rapid-fire velocity
    fraud_n = n - legit_n
    fraud_amount = rng.gamma(shape=3.0, scale=1200.0, size=fraud_n)    # big
    fraud_velocity = rng.poisson(lam=8.0, size=fraud_n)

    x = np.vstack([
        np.column_stack([legit_amount, legit_velocity]),
        np.column_stack([fraud_amount, fraud_velocity]),
    ])
    y = np.concatenate([np.zeros(legit_n), np.ones(fraud_n)])
    return x, y


def train():
    x, y = _make_dataset()
    x_train, x_test, y_train, y_test = train_test_split(
        x, y, test_size=0.2, random_state=42, stratify=y)

    # class_weight balances the rare fraud class
    model = LogisticRegression(class_weight="balanced", max_iter=1000)
    model.fit(x_train, y_train)

    acc = model.score(x_test, y_test)
    print(f"trained logistic regression, test accuracy = {acc:.3f}")

    joblib.dump(model, MODEL_PATH)
    print(f"saved model to {MODEL_PATH}")
    return model


def ensure_model():
    """Load the model if it exists, otherwise train and save it first."""
    if os.path.exists(MODEL_PATH):
        return joblib.load(MODEL_PATH)
    return train()


if __name__ == "__main__":
    train()
