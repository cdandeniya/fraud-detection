package com.cdandeniya.fraud.model;

/**
 * The three outcomes the pipeline can give a transaction.
 * APPROVE = looks fine, REVIEW = a human should look, DECLINE = block it.
 */
public enum Verdict {
    APPROVE,
    REVIEW,
    DECLINE
}
