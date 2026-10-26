package dev.proofpay.proofpay.order;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<SellerOrder, Long> {

    Optional<SellerOrder> findByPaypalInvoiceId(String paypalInvoiceId);

    Optional<SellerOrder> findByPaymentId(String paymentId);

    List<SellerOrder> findAllByOrderByCreatedAtDesc();
}
