package lk.emergency.client;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.*;
import java.util.*;
import java.util.concurrent.TimeoutException;
import lk.emergency.common.*;
import lk.emergency.common.Dtos.*;

/**
 * Buffers subscribed events during a frozen snapshot and applies only newer epoch/revision data.
 */
public final class LiveState {
  private final Map<String, JsonNode> incidents = new LinkedHashMap<>(),
      assignments = new LinkedHashMap<>(),
      teams = new LinkedHashMap<>();
  private final List<Envelope> buffer = new ArrayList<>();
  private boolean synchronizing, overflow;
  private String epoch;
  private long sequence;

  public synchronized void begin() {
    synchronizing = true;
    overflow = false;
    buffer.clear();
  }

  public synchronized boolean accept(Envelope event) {
    if (event.sequence() == null || event.epoch() == null) return true;
    if (synchronizing) {
      if (buffer.size() >= 1000) {
        overflow = true;
        return false;
      }
      buffer.add(event);
      return false;
    }
    return apply(event);
  }

  private boolean apply(Envelope e) {
    if (!Objects.equals(epoch, e.epoch()) || e.sequence() <= sequence) return false;
    sequence = e.sequence();
    JsonNode data = e.payload();
    boolean changed = false;
    if (e.type().equals("INCIDENT_CREATED")) changed = put(incidents, data);
    if (e.type().equals("INCIDENT_UPDATED")
        || e.type().equals("ASSIGNMENT_UPDATED")
        || e.type().equals("ASSIGNMENT_OFFERED")) {
      changed |= put(incidents, data.get("incident"));
      changed |= put(assignments, data.get("assignment"));
    }
    if (e.type().equals("TEAM_UPDATED")) changed |= put(teams, data);
    return changed;
  }

  private boolean put(Map<String, JsonNode> map, JsonNode value) {
    if (value == null || value.isNull() || !value.has("id")) return false;
    String id = value.get("id").asText();
    JsonNode old = map.get(id);
    if (old != null && old.path("revision").asLong() > value.path("revision").asLong())
      return false;
    boolean changed = !value.equals(old);
    map.put(id, value.deepCopy());
    while (map.size() > 5000) map.remove(map.keySet().iterator().next());
    return changed;
  }

  public void synchronize(Transport transport, UserView user) throws IOException, TimeoutException {
    begin();
    List<SnapshotPage> pages = new ArrayList<>();
    String id = null;
    int offset = 0;
    do {
      Envelope response = transport.call("GET_SNAPSHOT", new SnapshotQuery(id, offset));
      if (!Boolean.TRUE.equals(response.ok()))
        throw new IOException("Snapshot unavailable: " + response.error().code());
      SnapshotPage page = Validation.payload(response.payload(), SnapshotPage.class);
      if (!page.user().id().equals(user.id())) throw new IOException("Snapshot identity mismatch");
      if (!pages.isEmpty()
          && (!page.snapshotId().equals(id)
              || !page.epoch().equals(pages.getFirst().epoch())
              || page.sequence() != pages.getFirst().sequence()))
        throw new IOException("Snapshot changed during synchronization");
      if (page.offset() != offset) throw new IOException("Snapshot page offset mismatch");
      pages.add(page);
      id = page.snapshotId();
      if (page.nextOffset() == null) break;
      if (page.nextOffset() <= offset) throw new IOException("Invalid snapshot continuation");
      offset = page.nextOffset();
    } while (pages.size() < 1001);
    finish(pages);
  }

  public synchronized void finish(List<SnapshotPage> pages) throws IOException {
    if (overflow) throw new IOException("Event buffer overflow; fetch a fresh snapshot");
    if (pages.isEmpty() || pages.getLast().nextOffset() != null)
      throw new IOException("Incomplete snapshot");
    incidents.clear();
    assignments.clear();
    teams.clear();
    epoch = pages.getFirst().epoch();
    sequence = pages.getFirst().sequence();
    for (var page : pages) {
      page.incidents().forEach(x -> put(incidents, Json.tree(x)));
      page.assignments().forEach(x -> put(assignments, Json.tree(x)));
      page.teams().forEach(x -> put(teams, Json.tree(x)));
    }
    buffer.sort(Comparator.comparingLong(e -> e.sequence() == null ? 0 : e.sequence()));
    for (var e : buffer) apply(e);
    buffer.clear();
    synchronizing = false;
  }

  public synchronized JsonNode incident(String id) {
    return incidents.get(id);
  }

  public synchronized JsonNode assignment(String id) {
    return assignments.get(id);
  }

  public synchronized JsonNode team(String id) {
    return teams.get(id);
  }

  public synchronized int incidentCount() {
    return incidents.size();
  }

  public synchronized void response(Envelope response) {
    if (!Boolean.TRUE.equals(response.ok())) return;
    JsonNode data = response.payload();
    switch (response.type()) {
      case "CREATE_INCIDENT", "SET_PRIORITY", "REJECT_INCIDENT", "CLOSE_INCIDENT" ->
          put(incidents, data);
      case "ASSIGN_TEAM", "CANCEL_ASSIGNMENT", "RESPOND_ASSIGNMENT", "UPDATE_ASSIGNMENT_STATUS" ->
          put(assignments, data);
      case "SET_TEAM_AVAILABILITY" -> put(teams, data);
      default -> {}
    }
  }

  public synchronized List<JsonNode> assignments() {
    return assignments.values().stream().map(JsonNode::deepCopy).map(x -> (JsonNode) x).toList();
  }
}
