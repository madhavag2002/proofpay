package dev.proofpay.proofpay.web;

import dev.proofpay.proofpay.config.AppProperties;
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
            return "a dispute";
        }
        return switch (code) {
            case "MERCHANDISE_OR_SERVICE_NOT_AS_DESCRIBED" -> "Item not as described";
            case "MERCHANDISE_OR_SERVICE_NOT_RECEIVED" -> "Item not received";
            case "INCORRECT_AMOUNT" -> "Incorrect amount";
            default -> "Other dispute";
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
