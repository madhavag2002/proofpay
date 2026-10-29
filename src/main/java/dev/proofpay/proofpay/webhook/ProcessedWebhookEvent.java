package dev.proofpay.proofpay.webhook;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;

import java.time.Instant;

/** Remembers which PayPal events were handled. PayPal retries deliveries, so each must run once. */
@Entity
public class ProcessedWebhookEvent {

    @Id
    @Column(length = 100)
    private String eventId;

    @Column(nullable = false, length = 100)
    private String eventType;

    @Column(nullable = false)
    private Instant receivedAt = Instant.now();

    protected ProcessedWebhookEvent() {
    }

    public ProcessedWebhookEvent(String eventId, String eventType) {
        this.eventId = eventId;
        this.eventType = eventType;
    }

    public String getEventId() { return eventId; }
    public String getEventType() { return eventType; }
    public Instant getReceivedAt() { return receivedAt; }
}
