package com.cdandeniya.fraud.rules;

import com.cdandeniya.fraud.model.Transaction;

/**
 * A single fraud check. Every rule is a Spring @Component, so the engine just
 * asks Spring for the whole list of rules - adding a new one needs no wiring.
 */
public interface Rule {

    RuleResult evaluate(Transaction transaction);
}
