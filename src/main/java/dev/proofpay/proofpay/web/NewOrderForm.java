package dev.proofpay.proofpay.web;

import dev.proofpay.proofpay.ai.ChatImage;
import dev.proofpay.proofpay.ai.ChatInput;
import dev.proofpay.proofpay.order.ChatSource;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** The New order page: who the buyer is, and the chat in one of three forms. */
public class NewOrderForm {

    static final long MAX_EXPORT_BYTES = 2 * 1024 * 1024;
    private static final Set<String> IMAGE_TYPES = Set.of("image/png", "image/jpeg", "image/webp");

    @NotBlank(message = "Enter the buyer's name")
    @Size(max = 120)
    private String buyerName;

    @NotBlank(message = "Enter the buyer's PayPal email, so the invoice reaches them")
    @Email(message = "That email address does not look right")
    @Size(max = 254)
    private String buyerEmail;

    @NotBlank(message = "Choose a currency")
    private String currency = "USD";

    private ChatSource source = ChatSource.PASTE;

    @Size(max = 20000, message = "Paste at most 20,000 characters; keep the messages about this order")
    private String chatText;

    private MultipartFile exportFile;

    private List<MultipartFile> screenshots = new ArrayList<>();

    /** Turns the chosen tab into ChatInput. Throws IllegalArgumentException with a message for the seller. */
    public ChatInput toChatInput() throws IOException {
        return switch (source) {
            case PASTE -> ChatInput.pasted(chatText);
            case WHATSAPP_EXPORT -> {
                if (exportFile == null || exportFile.isEmpty()) {
                    throw new IllegalArgumentException("Choose the exported .txt file");
                }
                if (exportFile.getSize() > MAX_EXPORT_BYTES) {
                    throw new IllegalArgumentException("The export file must be under 2 MB");
                }
                yield ChatInput.whatsappExport(new String(exportFile.getBytes(), StandardCharsets.UTF_8));
            }
            case SCREENSHOTS -> {
                List<ChatImage> images = new ArrayList<>();
                for (MultipartFile file : screenshots) {
                    if (file == null || file.isEmpty()) {
                        continue;
                    }
                    if (images.size() == ChatInput.MAX_IMAGES) {
                        throw new IllegalArgumentException("Upload at most 3 screenshots");
                    }
                    images.add(new ChatImage(mediaType(file), file.getBytes()));
                }
                yield ChatInput.screenshots(images);
            }
        };
    }

    private static String mediaType(MultipartFile file) {
        String type = file.getContentType();
        return type != null && IMAGE_TYPES.contains(type) ? type : ChatImage.mediaTypeFor(file.getOriginalFilename());
    }

    public String getBuyerName() { return buyerName; }
    public void setBuyerName(String buyerName) { this.buyerName = buyerName; }
    public String getBuyerEmail() { return buyerEmail; }
    public void setBuyerEmail(String buyerEmail) { this.buyerEmail = buyerEmail; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public ChatSource getSource() { return source; }
    public void setSource(ChatSource source) { this.source = source; }
    public String getChatText() { return chatText; }
    public void setChatText(String chatText) { this.chatText = chatText; }
    public MultipartFile getExportFile() { return exportFile; }
    public void setExportFile(MultipartFile exportFile) { this.exportFile = exportFile; }
    public List<MultipartFile> getScreenshots() { return screenshots; }
    public void setScreenshots(List<MultipartFile> screenshots) { this.screenshots = screenshots; }
}
