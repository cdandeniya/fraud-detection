package com.cdandeniya.fraud.ml;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Talks to the Python model service over HTTP.
 *
 * On purpose it "fails open": if the model service is slow or down, we log it and return
 * 0 so the pipeline keeps running on the rules alone. A fraud check that takes the whole
 * system down with it would be worse than one that occasionally misses the model's opinion.
 */
@Component
public class ModelClient {

    private static final Logger log = LoggerFactory.getLogger(ModelClient.class);

    private final RestClient restClient;

    public ModelClient(@Value("${fraud.model.url}") String baseUrl, RestClient.Builder builder) {
        this.restClient = builder.baseUrl(baseUrl).build();
    }

    public double fraudProbability(double amount, double velocity) {
        try {
            PredictResponse response = restClient.post()
                    .uri("/predict")
                    .body(new PredictRequest(amount, velocity))
                    .retrieve()
                    .body(PredictResponse.class);
            return response == null ? 0.0 : response.getProbability();
        } catch (Exception e) {
            log.warn("model service unavailable, failing open (score without model): {}", e.getMessage());
            return 0.0;
        }
    }
}
