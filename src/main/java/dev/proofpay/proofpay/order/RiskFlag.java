package dev.proofpay.proofpay.order;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

/** One dispute risk the AI found in the chat. */
@Embeddable
public class RiskFlag {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private RiskType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private RiskLevel level;

    @Column(length = 500)
    private String detail;

    /** The PayPal dispute reason this risk leads to, e.g. MERCHANDISE_OR_SERVICE_NOT_AS_DESCRIBED. */
    @Column(length = 60)
    private String prevents;

    private boolean resolved;

    protected RiskFlag() {
    }

    public RiskFlag(RiskType type, RiskLevel level, String detail, String prevents) {
        this.type = type;
        this.level = level;
        this.detail = OrderItem.clip(detail, 500);
        this.prevents = prevents;
    }

    /** A copy marked resolved. Items in an embedded list are replaced, not edited. */
    RiskFlag resolve() {
        RiskFlag copy = new RiskFlag(type, level, detail, prevents);
        copy.resolved = true;
        return copy;
    }

    public RiskType getType() { return type; }
    public RiskLevel getLevel() { return level; }
    public String getDetail() { return detail; }
    public String getPrevents() { return prevents; }
    public boolean isResolved() { return resolved; }
}
