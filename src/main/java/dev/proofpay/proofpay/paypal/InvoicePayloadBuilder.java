package dev.proofpay.proofpay.paypal;

import dev.proofpay.proofpay.order.AgreedTerms;
import dev.proofpay.proofpay.order.OrderItem;
import dev.proofpay.proofpay.order.SellerOrder;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;

/** Builds the JSON body for PayPal's "create draft invoice" call. No HTTP here, so it is easy to test. */
@Component
public class InvoicePayloadBuilder {

    // PayPal Invoicing v2 field limits
    static final int MAX_ITEM_NAME = 200;
    static final int MAX_ITEM_DESCRIPTION = 1000;

    static final String BUYER_NOTE = "Please check the agreed terms below before paying. If anything is not "
            + "what you agreed in our chat, reply to us before payment and we will correct the invoice.";

    private final JsonMapper mapper;

    public InvoicePayloadBuilder(JsonMapper mapper) {
        this.mapper = mapper;
    }

    public ObjectNode build(SellerOrder order, String agreedTerms, String sellerName) {
        ObjectNode root = mapper.createObjectNode();

        ObjectNode detail = root.putObject("detail");
        detail.put("currency_code", order.getCurrency());
        detail.put("note", BUYER_NOTE);
        detail.put("terms_and_conditions", AgreedTerms.truncate(agreedTerms, AgreedTerms.MAX_LENGTH));
        detail.putObject("payment_term").put("term_type", "DUE_ON_RECEIPT");

        if (sellerName != null && !sellerName.isBlank()) {
            root.putObject("invoicer").put("business_name", sellerName);
        }

        ObjectNode billing = root.putArray("primary_recipients").addObject().putObject("billing_info");
        billing.put("email_address", order.getBuyerEmail());
        putName(billing.putObject("name"), order.getBuyerName());

        ArrayNode items = root.putArray("items");
        for (OrderItem item : order.getItems()) {
            if (item.getUnitPrice() == null) {
                throw new IllegalArgumentException("Item '" + item.getName() + "' has no price");
            }
            ObjectNode node = items.addObject();
            node.put("name", AgreedTerms.truncate(item.getName(), MAX_ITEM_NAME));
            if (item.getSpecs() != null && !item.getSpecs().isBlank()) {
                // Agreed specs sit on the invoice line itself, where a PayPal reviewer looks first.
                node.put("description",
                        AgreedTerms.truncate("Agreed: " + item.getSpecs().strip(), MAX_ITEM_DESCRIPTION));
            }
            node.put("quantity", String.valueOf(item.getQuantity()));
            ObjectNode amount = node.putObject("unit_amount");
            amount.put("currency_code", order.getCurrency());
            amount.put("value", formatAmount(item.getUnitPrice(), order.getCurrency()));
            node.put("unit_of_measure", "QUANTITY");
        }
        return root;
    }

    /** Amounts as strings with the currency's own decimals: "18.00" USD, "1200" JPY. */
    static String formatAmount(BigDecimal amount, String currency) {
        int digits = Math.max(Currency.getInstance(currency).getDefaultFractionDigits(), 0);
        return amount.setScale(digits, RoundingMode.HALF_UP).toPlainString();
    }

    private static void putName(ObjectNode name, String fullName) {
        String s = fullName == null ? "" : fullName.strip();
        int space = s.indexOf(' ');
        if (space < 0) {
            name.put("given_name", s);
        } else {
            name.put("given_name", s.substring(0, space));
            name.put("surname", s.substring(space + 1).strip());
        }
    }
}
