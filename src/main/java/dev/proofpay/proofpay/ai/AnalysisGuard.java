package dev.proofpay.proofpay.ai;

import dev.proofpay.proofpay.order.RiskLevel;
import dev.proofpay.proofpay.order.RiskType;
import dev.proofpay.proofpay.seller.SellerProfile;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Checks the AI's answer with plain code before the seller sees it:
 * prices must appear in the chat, and delivery risk is judged against the seller's own settings.
 */
final class AnalysisGuard {

    static final int MAX_ITEMS = 30;
    static final int MAX_FLAGS = 10;
    static final int MAX_NOTES = 10;

    private AnalysisGuard() {
    }

    static OrderAnalysis clean(OrderAnalysis a, String chatText, SellerProfile profile, LocalDate today) {
        String evidence = (chatText == null ? "" : chatText) + "\n" + (a.transcript() == null ? "" : a.transcript());
        List<String> questions = new ArrayList<>(a.openQuestions());
        List<OrderAnalysis.Flag> flags = new ArrayList<>(a.riskFlags());
        List<OrderAnalysis.Item> items = new ArrayList<>();
        List<String> unpriced = new ArrayList<>();

        // 1. Prices must appear in the chat.
        for (OrderAnalysis.Item item : a.items()) {
            if (item.name() == null || item.name().isBlank() || items.size() >= MAX_ITEMS) {
                continue;
            }
            String name = item.name().strip();
            BigDecimal price = item.unitPrice();
            if (price != null && price.signum() <= 0) {
                price = null;
            }
            if (price != null && !priceAppearsIn(price, evidence)) {
                questions.add("The AI read a price of " + price.toPlainString() + " for '" + name
                        + "', but that number is not in the chat. Please check it.");
                price = null;
            }
            if (price == null) {
                unpriced.add(name);
            }
            Integer quantity = item.quantity() != null && item.quantity() >= 1 ? item.quantity() : null;
            items.add(new OrderAnalysis.Item(name, item.specs(), quantity, price));
        }

        // 2. A missing price is a fact, not a judgement, so code adds the flag if the AI did not.
        boolean hasPriceFlag = flags.stream().anyMatch(f -> f.type() == RiskType.MISSING_PRICE);
        if (!unpriced.isEmpty() && !hasPriceFlag) {
            flags.add(new OrderAnalysis.Flag(RiskType.MISSING_PRICE, RiskLevel.HIGH,
                    "No agreed price for: " + String.join(", ", unpriced), "INCORRECT_AMOUNT"));
        }

        // 3. Delivery risk comes only from the seller's settings, never from the AI.
        String country = normalizeCountry(a.shipToCountry());
        LocalDate deadline = parseDate(a.deliveryDeadline());
        flags.removeIf(f -> f.type() == RiskType.UNREALISTIC_DELIVERY);
        DeliveryCheck.check(deadline, country, profile, today).ifPresent(flags::add);

        String currency = a.currency() == null ? null : a.currency().strip().toUpperCase(Locale.ROOT);
        if (currency != null && !currency.matches("[A-Z]{3}")) {
            currency = null;
        }

        return new OrderAnalysis(a.transcript(), a.buyerLanguage(), a.englishSummary(), items, currency,
                a.shipTo(), country, a.deliveryBy(), deadline == null ? null : deadline.toString(),
                limit(a.buyerAcceptedNotes(), MAX_NOTES), limit(questions, MAX_NOTES),
                limit(flags, MAX_FLAGS), a.clarificationMessage() == null ? "" : a.clarificationMessage());
    }

    /** True if the number is written in the text: 18 matches "$18" or "18.00", but not "180" or "2018". */
    static boolean priceAppearsIn(BigDecimal price, String text) {
        if (text == null) {
            return false;
        }
        String t = text.replace(",", "");
        List<String> forms = List.of(
                price.stripTrailingZeros().toPlainString(),
                price.setScale(2, RoundingMode.HALF_UP).toPlainString());
        for (String form : forms) {
            Pattern p = Pattern.compile("(?<![\\d.])" + Pattern.quote(form) + "(?!\\d)");
            if (p.matcher(t).find()) {
                return true;
            }
        }
        return false;
    }

    private static String normalizeCountry(String code) {
        if (code == null) {
            return null;
        }
        String c = code.strip().toUpperCase(Locale.ROOT);
        return c.matches("[A-Z]{2}") ? c : null;
    }

    private static LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.strip());
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static <T> List<T> limit(List<T> list, int max) {
        return list.size() <= max ? list : list.subList(0, max);
    }
}
