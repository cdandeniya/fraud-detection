package com.cdandeniya.fraud.ml;

/** The model service's answer: probability that a transaction is fraud (0..1). */
public class PredictResponse {

    private double probability;

    public double getProbability() {
        return probability;
    }

    public void setProbability(double probability) {
        this.probability = probability;
    }
}
