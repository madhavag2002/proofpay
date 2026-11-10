package dev.proofpay.proofpay.web;

import dev.proofpay.proofpay.config.AppProperties;
import dev.proofpay.proofpay.dispute.CourierStatus;
import dev.proofpay.proofpay.dispute.Recommendation;
import dev.proofpay.proofpay.order.ChatSource;
import dev.proofpay.proofpay.order.RiskType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Currency;
import java.util.Locale;

/** Display helpers for templates, used as ${@fmt.money(...)}, ${@fmt.date(...)} and so on. */
@Component("fmt")
public class Formats {

    private final DateTimeFormatter dateTime;

    public Formats(AppProperties props) {
        this.dateTime = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.ENGLISH)
                .withZone(ZoneId.of(props.zoneOrDefault()));
    }

    public String date(Instant instant) {
        return instant == null ? "" : dateTime.format(instant);
    }

    public String money(BigDecimal amount, String currency) {
        if (amount == null) {
            return "Not complete";
        }
        try {
            Currency c = Currency.getInstance(currency);
            NumberFormat f = NumberFormat.getCurrencyInstance(Locale.US);
            f.setCurrency(c);
            f.setMinimumFractionDigits(c.getDefaultFractionDigits());
            f.setMaximumFractionDigits(c.getDefaultFractionDigits());
            return f.format(amount);
        } catch (IllegalArgumentException e) {
            return amount.toPlainString() + " " + currency;
        }
    }

    public String riskLabel(RiskType type) {
        if (type == null) {
            return "Risk";
        }
        return switch (type) {
            case VAGUE_SPEC -> "Vague detail";
            case VARIATION_NOT_ACKNOWLEDGED -> "Handmade variation not accepted";
            case UNREALISTIC_DELIVERY -> "Delivery deadline";
            case UNCONFIRMED_REQUEST -> "Not clearly agreed";
            case MISSING_PRICE -> "Missing price";
            case OTHER -> "Other risk";
        };
    }

    /** PayPal dispute reason code to plain words. */
    public String disputeLabel(String code) {
        if (code == null) {
            return "Dispute";
        }
        return switch (code) {
            case "MERCHANDISE_OR_SERVICE_NOT_AS_DESCRIBED" -> "Item not as described";
            case "MERCHANDISE_OR_SERVICE_NOT_RECEIVED" -> "Item not received";
            case "INCORRECT_AMOUNT" -> "Incorrect amount";
            case "UNAUTHORISED" -> "Unauthorised payment";
            case "DUPLICATE_TRANSACTION" -> "Duplicate payment";
            case "PAYMENT_BY_OTHER_MEANS" -> "Paid another way";
            case "CREDIT_NOT_PROCESSED" -> "Refund not received";
            case "CANCELED_RECURRING_BILLING" -> "Cancelled subscription";
            default -> "Other dispute";
        };
    }

    /** PayPal evidence type to plain words. */
    public String evidenceLabel(String code) {
        if (code == null) {
            return "";
        }
        return switch (code) {
            case "PROOF_OF_FULFILLMENT" -> "Proof of shipping";
            case "PROOF_OF_DELIVERY_SIGNATURE" -> "Signed proof of delivery";
            case "PROOF_OF_REFUND" -> "Proof of refund (ProofPay never sends this)";
            case "OTHER" -> "Other documents or notes";
            default -> code;
        };
    }

    public String recommendationLabel(Recommendation r) {
        if (r == null) {
            return "";
        }
        return switch (r) {
            case FIGHT -> "Fight it with the evidence";
            case REPLY_WITH_UPDATE -> "Reply with an update";
            case PARTIAL_REFUND -> "Offer a partial refund";
            case ACCEPT -> "Accept the claim";
        };
    }

    public String courierLabel(CourierStatus s) {
        if (s == null) {
            return "Unknown";
        }
        return switch (s) {
            case UNKNOWN -> "Unknown";
            case IN_TRANSIT -> "In transit";
            case DELIVERED -> "Delivered";
            case RETURNED -> "Returned to sender";
        };
    }

    public String disputeStatusNote(String status) {
        if (status == null) {
            return "";
        }
        return switch (status) {
            case "WAITING_FOR_SELLER_RESPONSE" -> "PayPal is waiting for your response.";
            case "UNDER_REVIEW" -> "PayPal is reviewing. Neither side can respond right now.";
            case "WAITING_FOR_BUYER_RESPONSE" -> "PayPal is waiting for the buyer.";
            case "RESOLVED" -> "This dispute is closed.";
            default -> "Status: " + status;
        };
    }

    public String sourceLabel(ChatSource source) {
        if (source == null) {
            return "";
        }
        return switch (source) {
            case PASTE -> "Pasted chat";
            case SCREENSHOTS -> "Read from screenshots";
            case WHATSAPP_EXPORT -> "WhatsApp export";
        };
    }
}
