package dev.proofpay.proofpay.agent;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Shrinks PayPal responses before they go to the AI, and finds values inside tool arguments. */
public final class JsonSlimmer {

    private JsonSlimmer() {
    }

    /** Removes "links" everywhere (large and useless to the AI) and cuts long results. */
    public static String slim(JsonMapper mapper, String text, int maxChars) {
        String result = text;
        try {
            JsonNode node = mapper.readTree(text);
            removeLinks(node);
            result = mapper.writeValueAsString(node);
        } catch (JacksonException e) {
            // Not JSON: keep the text as it is.
        }
        return result.length() <= maxChars ? result : result.substring(0, maxChars) + "...(truncated)";
    }

    private static void removeLinks(JsonNode node) {
        if (node instanceof ObjectNode obj) {
            obj.remove("links");
            List<String> names = new ArrayList<>(obj.propertyNames());
            for (String name : names) {
                removeLinks(obj.get(name));
            }
        } else if (node != null && node.isArray()) {
            for (JsonNode child : node) {
                removeLinks(child);
            }
        }
    }

    /** First text value whose key equals keyPart, otherwise contains it. Searches nested objects. */
    public static String findText(JsonNode node, String keyPart) {
        String exact = find(node, keyPart.toLowerCase(Locale.ROOT), true);
        return exact != null ? exact : find(node, keyPart.toLowerCase(Locale.ROOT), false);
    }

    private static String find(JsonNode node, String key, boolean exact) {
        if (node instanceof ObjectNode obj) {
            for (String name : obj.propertyNames()) {
                JsonNode value = obj.get(name);
                String n = name.toLowerCase(Locale.ROOT);
                boolean match = exact ? n.equals(key) : n.contains(key);
                if (match && value.isValueNode() && !value.asString("").isBlank()) {
                    return value.asString("");
                }
            }
            for (String name : obj.propertyNames()) {
                String found = find(obj.get(name), key, exact);
                if (found != null) {
                    return found;
                }
            }
        } else if (node != null && node.isArray()) {
            for (JsonNode child : node) {
                String found = find(child, key, exact);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
}
