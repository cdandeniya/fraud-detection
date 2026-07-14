package com.cdandeniya.fraud.ml;

/** Features we send to the model service. Keep in sync with the Python side. */
public record PredictRequest(double amount, double velocity) {
}
