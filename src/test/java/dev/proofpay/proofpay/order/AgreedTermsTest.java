package dev.proofpay.proofpay.order;

import dev.proofpay.proofpay.paypal.InvoicePayloadBuilder;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgreedTermsTest {

    private final InvoicePayloadBuilder builder = new InvoicePayloadBuilder(JsonMapper.builder().build());

    private SellerOrder order() {
        SellerOrder o = new SellerOrder("Emily Carter", "emily@example.com", "msg", "USD");
        o.recordAiDraft("en", "s",
                List.of(new OrderItem("Marble coaster", "Round, 10 cm, blue inlay", 4, new BigDecimal("18")),
                        new OrderItem("Marble tray", "30 x 20 cm", 1, new BigDecimal("65.5"))),
                "Columbus, Ohio", "Before 15 Dec", List.of("Handmade variation is fine"), List.of());
        return o;
    }

    private RiskFlag highRisk() {
        return new RiskFlag(RiskType.VAGUE_SPEC, RiskLevel.HIGH, "Coaster size not stated",
                "MERCHANDISE_OR_SERVICE_NOT_AS_DESCRIBED");
    }

    @Test
    void termsListEveryAgreedDetail() {
        String terms = AgreedTerms.render(order(), LocalDate.of(2026, 10, 27));
        assertThat(terms)
                .startsWith("Terms agreed with Emily Carter via chat on 27 Oct 2026:")
                .contains("1. 4 x Marble coaster - Round, 10 cm, blue inlay")
                .contains("2. 1 x Marble tray - 30 x 20 cm")
                .contains("Ship to: Columbus, Ohio")
                .contains("Delivery: Before 15 Dec")
                .contains("Buyer accepted in chat: Handmade variation is fine")
                .endsWith("Paying this invoice confirms acceptance of these terms.");
    }

    @Test
    void sellerResolvingAFlagClearsTheHighRisk() {
        SellerOrder o = order();
        o.recordRiskCheck(ChatSource.PASTE, List.of(highRisk()), "Could you confirm the size is 10 cm?");
        assertThat(o.hasOpenHighRisk()).isTrue();

        o.resolveFlag(0);

        assertThat(o.hasOpenHighRisk()).isFalse();
        assertThat(o.getRiskFlags().get(0).isResolved()).isTrue();
        assertThat(o.getClarificationMessage()).isEqualTo("Could you confirm the size is 10 cm?");
    }

    @Test
    void resolvingAMissingFlagFails() {
        SellerOrder o = order();
        o.recordRiskCheck(ChatSource.PASTE, List.of(highRisk()), "msg");
        assertThatThrownBy(() -> o.resolveFlag(5)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void payloadCarriesTermsSpecsAmountsAndBuyerNote() {
        JsonNode p = builder.build(order(), "TERMS", "Agra Marble");
        assertThat(p.at("/detail/terms_and_conditions").asString()).isEqualTo("TERMS");
        assertThat(p.at("/detail/note").asString()).startsWith("Please check the agreed terms below");
        assertThat(p.at("/detail/currency_code").asString()).isEqualTo("USD");
        assertThat(p.at("/items/0/description").asString()).isEqualTo("Agreed: Round, 10 cm, blue inlay");
        assertThat(p.at("/items/0/quantity").asString()).isEqualTo("4");
        assertThat(p.at("/items/0/unit_amount/value").asString()).isEqualTo("18.00");
        assertThat(p.at("/items/1/unit_amount/value").asString()).isEqualTo("65.50");
        assertThat(p.at("/primary_recipients/0/billing_info/email_address").asString()).isEqualTo("emily@example.com");
        assertThat(p.at("/primary_recipients/0/billing_info/name/surname").asString()).isEqualTo("Carter");
    }

    @Test
    void refusesItemWithoutPrice() {
        SellerOrder o = new SellerOrder("Ana", "ana@example.com", "msg", "EUR");
        o.recordAiDraft("en", "s", List.of(new OrderItem("Vase", null, 1, null)), null, null, List.of(), List.of());
        assertThatThrownBy(() -> builder.build(o, "t", "s")).hasMessageContaining("Vase");
    }

    @Test
    void cannotChangeDraftAfterInvoiceSent() {
        SellerOrder o = order();
        o.recordRiskCheck(ChatSource.PASTE, List.of(highRisk()), "msg");
        o.markInvoiceSent("terms", "INV2-TEST", null);
        assertThatThrownBy(() -> o.resolveFlag(0)).isInstanceOf(IllegalStateException.class);
    }
}
