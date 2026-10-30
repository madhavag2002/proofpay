package dev.proofpay.proofpay.agent;

/** One line in the assistant page: the seller, the AI, or an action ProofPay took. */
public record ChatLine(Kind kind, String text) {

    public enum Kind { USER, ASSISTANT, ACTION }

    public boolean isUser() { return kind == Kind.USER; }
    public boolean isAssistant() { return kind == Kind.ASSISTANT; }
    public boolean isAction() { return kind == Kind.ACTION; }
}
