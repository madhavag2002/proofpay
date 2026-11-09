package dev.proofpay.proofpay.dispute;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DisputeCaseRepository extends JpaRepository<DisputeCase, Long> {

    Optional<DisputeCase> findByDisputeId(String disputeId);

    List<DisputeCase> findAllByOrderByCreatedAtDesc();
}
