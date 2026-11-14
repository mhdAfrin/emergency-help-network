package lk.emergency.client.ui;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;

/** Only presentation lives here. Role-specific privacy remains enforced by the server. */
public final class RecordPrinter {
  private RecordPrinter() {}

  private static final Map<String, String> LABELS =
      Map.ofEntries(
          Map.entry("id", "Reference"),
          Map.entry("username", "Username"),
          Map.entry("displayName", "Name"),
          Map.entry("role", "Role"),
          Map.entry("name", "Team"),
          Map.entry("serviceType", "Service"),
          Map.entry("availability", "Availability"),
          Map.entry("presence", "Connection"),
          Map.entry("state", "Status"),
          Map.entry("priority", "Priority"),
          Map.entry("category", "Category"),
          Map.entry("address", "Location"),
          Map.entry("description", "Description"),
          Map.entry("incidentId", "Incident"),
          Map.entry("teamId", "Team reference"),
          Map.entry("createdAt", "Created"),
          Map.entry("updatedAt", "Updated"),
          Map.entry("closedAt", "Closed"),
          Map.entry("publicResolutionReason", "Resolution"),
          Map.entry("publicMessage", "Update"),
          Map.entry("privateReason", "Your private note"),
          Map.entry("ownPrivateNote", "Your private note"),
          Map.entry("internalNote", "Operator note"),
          Map.entry("message", "Message"));

  public static List<String> lines(JsonNode value) {
    List<String> output = new ArrayList<>();
    append(value, output);
    return output;
  }

  private static void append(JsonNode value, List<String> out) {
    if (value == null || value.isNull()) return;
    if (value.isArray()) {
      if (value.isEmpty()) out.add("  Nothing to show yet.");
      for (JsonNode item : value) append(item, out);
      return;
    }
    if (!value.isObject()) {
      out.add("  " + value.asText());
      return;
    }
    String heading =
        value.has("username")
            ? "ACCOUNT"
            : value.has("category")
                ? "INCIDENT"
                : value.has("availability")
                    ? "TEAM"
                    : value.has("teamId")
                        ? "ASSIGNMENT"
                        : value.has("publicMessage") ? "TIMELINE" : "RESULT";
    out.add(
        "  +-- "
            + heading
            + (value.has("id") && !heading.equals("TIMELINE")
                ? " #" + value.path("id").asText()
                : "")
            + " --");

    value
        .fields()
        .forEachRemaining(
            field -> {
              String key = field.getKey();
              JsonNode child = field.getValue();
              if (child.isNull() || key.equals("id")) return;
              if (LABELS.containsKey(key) && child.isValueNode()) {
                String text = key.endsWith("At") ? ConsoleIO.time(child.asText()) : child.asText();
                if (Set.of(
                        "state",
                        "role",
                        "priority",
                        "category",
                        "serviceType",
                        "availability",
                        "presence")
                    .contains(key)) text = words(text);
                addWrapped(out, String.format("  | %-16s : ", LABELS.get(key)), text);
              } else if (Set.of(
                      "incident", "assignment", "assignments", "timeline", "operationalNotes")
                  .contains(key)) {
                out.add("  " + words(key) + ":");
                append(child, out);
              }
            });
    out.add("  +------------------------------------------------------------");
  }

  private static void addWrapped(List<String> out, String prefix, String value) {
    String[] paragraphs = value.replace("\r", "").split("\n", -1);
    for (String paragraph : paragraphs) {
      String remaining = paragraph;
      while (remaining.length() > 52) {
        int cut = remaining.lastIndexOf(' ', 52);
        if (cut < 1) cut = 52;
        out.add(prefix + remaining.substring(0, cut));
        remaining = remaining.substring(cut).stripLeading();
      }
      out.add(prefix + remaining);
    }
  }

  public static String words(String text) {
    String s =
        text.replaceAll("([a-z])([A-Z])", "$1 $2").replace('_', ' ').toLowerCase(Locale.ROOT);
    return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
  }
}
