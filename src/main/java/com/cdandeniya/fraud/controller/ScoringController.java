package com.cdandeniya.fraud.controller;

import com.cdandeniya.fraud.dto.DecisionResponse;
import com.cdandeniya.fraud.dto.TransactionRequest;
import com.cdandeniya.fraud.service.ScoringService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ScoringController {

    private final ScoringService scoringService;

    public ScoringController(ScoringService scoringService) {
        this.scoringService = scoringService;
    }

    @PostMapping("/score")
    public DecisionResponse score(@Valid @RequestBody TransactionRequest request) {
        return scoringService.score(request);
    }
}
