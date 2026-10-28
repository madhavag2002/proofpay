package dev.proofpay.proofpay.ai;

import java.util.List;
import java.util.stream.Collectors;

/** Cleans a WhatsApp "Export chat" .txt file down to the messages that matter. */
public final class WhatsAppExportReader {

    private static final List<String> NOISE = List.of(
            "<media omitted>", "image omitted", "video omitted", "sticker omitted", "audio omitted",
            "end-to-end encrypted", "this message was deleted", "you deleted this message");

    private WhatsAppExportReader() {
    }

    public static String clean(String raw) {
        if (raw == null) {
            return "";
        }
        String text = raw.replace("﻿", "")      // byte-order mark
                         .replace("\u200E", "")      // invisible marks WhatsApp adds on iPhone
                         .replace("\r\n", "\n");
        return text.lines()
                .map(String::strip)
                .filter(line -> !line.isEmpty())
                .filter(line -> NOISE.stream().noneMatch(n -> line.toLowerCase().contains(n)))
                .collect(Collectors.joining("\n"));
    }
}
