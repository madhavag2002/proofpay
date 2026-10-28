package dev.proofpay.proofpay.order;

import dev.proofpay.proofpay.ai.ChatInput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Walks the vague chat through the whole workflow, playing the seller's part.
 * Works with any mix of mock and live AI / PayPal. Runs only with the "orderspike" profile.
 */
@Component
@Profile("orderspike")
class OrderSpike implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(OrderSpike.class);

    private final OrderService service;
    private final String buyerEmail;

    OrderSpike(OrderService service, @Value("${SANDBOX_BUYER_EMAIL:emily.buyer@example.com}") String buyerEmail) {
        this.service = service;
        this.buyerEmail = buyerEmail.isBlank() ? "emily.buyer@example.com" : buyerEmail;
    }

    @Override
    public void run(String... args) throws IOException {
        String chat = new ClassPathResource("samples/vague.txt").getContentAsString(StandardCharsets.UTF_8);

        // 1. Chat in
        SellerOrder order = service.createFromChat("Emily Carter", buyerEmail, "USD", ChatInput.pasted(chat));
        long id = order.getId();
        log.info("1. Drafted order {} with {} items and {} risk flags", id, order.getItems().size(), order.getRiskFlags().size());
        log.info("   Missing: {}", ApprovalCheck.of(order).missing());

        // 2. Try to send straight away: must be refused
        log.info("2. Send with gaps -> {}", service.approveAndSend(id, null, false).outcome());

        // 3. The seller fills the gaps (as if the buyer answered the clarification)
        List<OrderItem> filled = new ArrayList<>();
        for (OrderItem item : order.getItems()) {
            filled.add(new OrderItem(item.getName(),
                    item.getSpecs() == null ? "As confirmed with the buyer" : item.getSpecs(),
                    item.getQuantity() == null ? 4 : item.getQuantity(),
                    item.getUnitPrice() == null ? new BigDecimal("65") : item.getUnitPrice()));
        }
        DraftEdit edit = new DraftEdit(filled, "Columbus, Ohio, USA", "Buyer agreed to a later date: by Dec 1",
                List.of("Slight pattern variation is fine because each piece is handmade"));
        log.info("3. After filling gaps, missing: {}", service.saveDraft(id, edit).missing());

        // 4. Send while HIGH risks are still open: must ask for confirmation
        log.info("4. Send with open HIGH risk -> {}", service.approveAndSend(id, edit, false).outcome());

        // 5. The seller marks every risk handled, then sends
        for (int i = 0; i < service.load(id).getRiskFlags().size(); i++) {
            service.resolveFlag(id, i);
        }
        SendResult result = service.approveAndSend(id, edit, false);
        log.info("5. Send after handling risks -> {}: {}", result.outcome(), result.message());

        SellerOrder sent = service.load(id);
        log.info("   Invoice {} | pay page {}\n{}", sent.getPaypalInvoiceId(), sent.getInvoiceUrl(), sent.getAgreedTerms());

        // 6. A second click must not send another invoice
        log.info("6. Send again -> {}", service.approveAndSend(id, edit, false).outcome());

        // 7. Payment (in Step 9 this comes from PayPal's webhook), delivered twice like a real retry
        service.markPaidByInvoice(sent.getPaypalInvoiceId(), "SPIKE-PAYMENT");
        service.markPaidByInvoice(sent.getPaypalInvoiceId(), "SPIKE-PAYMENT");
        log.info("7. Status after payment: {}", service.load(id).getStatus());
    }
}
