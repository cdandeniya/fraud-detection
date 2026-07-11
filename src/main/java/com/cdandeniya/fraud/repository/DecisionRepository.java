package com.cdandeniya.fraud.repository;

import com.cdandeniya.fraud.model.Decision;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DecisionRepository extends JpaRepository<Decision, Long> {
}
