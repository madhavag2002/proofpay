package dev.proofpay.proofpay.ai;

import dev.proofpay.proofpay.order.ChatSource;

import java.util.List;

/** The chat as the seller gave it: pasted text, a WhatsApp export, or screenshots. */
public record ChatInput(ChatSource source, String text, List<ChatImage> images) {

    /** Same limit as SellerOrder.buyerMessage. Long chats keep their most recent part. */
    public static final int MAX_TEXT = 8000;
    public static final int MAX_IMAGES = 3;

    public ChatInput {
        images = images == null ? List.of() : List.copyOf(images);
        text = text == null ? null : keepRecent(text.strip());
        if (source == ChatSource.SCREENSHOTS) {
            if (images.isEmpty() || images.size() > MAX_IMAGES) {
                throw new IllegalArgumentException("Upload 1 to 3 screenshots");
            }
        } else if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("The chat is empty");
        }
    }

    public static ChatInput pasted(String text) {
        return new ChatInput(ChatSource.PASTE, text, List.of());
    }

    public static ChatInput whatsappExport(String fileContent) {
        return new ChatInput(ChatSource.WHATSAPP_EXPORT, WhatsAppExportReader.clean(fileContent), List.of());
    }

    public static ChatInput screenshots(List<ChatImage> images) {
        return new ChatInput(ChatSource.SCREENSHOTS, null, images);
    }

    private static String keepRecent(String s) {
        if (s.length() <= MAX_TEXT) {
            return s;
        }
        String tail = s.substring(s.length() - MAX_TEXT);
        int firstBreak = tail.indexOf('\n');
        return firstBreak > 0 ? tail.substring(firstBreak + 1) : tail;
    }
}
