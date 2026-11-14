package lk.emergency.client;

import static lk.emergency.common.Types.*;
import static org.junit.jupiter.api.Assertions.*;

import java.io.*;
import java.util.*;
import lk.emergency.common.*;
import lk.emergency.common.Dtos.*;
import org.junit.jupiter.api.Test;

class LiveStateTest {
  UserView user = new UserView(UUID.randomUUID().toString(), "citizen", "Citizen", Role.CITIZEN);

  IncidentView incident(long revision) {
    return new IncidentView(
        "incident",
        user.id(),
        Category.OTHER,
        "Campus",
        "Fiction",
        null,
        null,
        Priority.MEDIUM,
        IncidentState.ACTIVE,
        null,
        "2026-11-13T00:00:00Z",
        "2026-11-13T00:00:00Z",
        null,
        revision);
  }

  TeamView team(Presence presence) {
    return new TeamView("team", "Police", ServiceType.POLICE, Availability.BUSY, 1, presence);
  }

  Envelope event(String type, Object data, String epoch, long sequence) {
    var e = Envelope.event(type, data);
    return new Envelope(
        1, Kind.EVENT, type, null, e.eventId(), e.payload(), null, null, epoch, sequence);
  }

  SnapshotPage snapshot() {
    return new SnapshotPage(
        "snapshot",
        0,
        null,
        user,
        List.of(incident(1)),
        List.of(),
        List.of(team(Presence.ONLINE)),
        "epoch",
        10,
        "now");
  }

  @Test
  void bufferedPostSnapshotEventsWinAndPreSnapshotEventsDoNotUndoPresence() throws Exception {
    LiveState state = new LiveState();
    state.begin();
    state.accept(event("TEAM_UPDATED", team(Presence.OFFLINE), "epoch", 9));
    state.accept(event("INCIDENT_UPDATED", new IncidentUpdate(incident(2), null), "epoch", 11));
    state.finish(List.of(snapshot()));
    assertEquals(2, state.incident("incident").get("revision").asLong());
    assertEquals("ONLINE", state.team("team").get("presence").asText());
    assertTrue(state.accept(event("TEAM_UPDATED", team(Presence.OFFLINE), "epoch", 12)));
    assertEquals("OFFLINE", state.team("team").get("presence").asText());
  }

  @Test
  void staleRevisionsDuplicateSequencesAndOldServerEpochAreIgnored() throws Exception {
    LiveState state = new LiveState();
    state.begin();
    state.finish(List.of(snapshot()));
    state.accept(event("INCIDENT_UPDATED", new IncidentUpdate(incident(3), null), "epoch", 11));
    assertFalse(
        state.accept(
            event("INCIDENT_UPDATED", new IncidentUpdate(incident(2), null), "epoch", 12)));
    assertEquals(3, state.incident("incident").get("revision").asLong());
    assertFalse(
        state.accept(
            event("INCIDENT_UPDATED", new IncidentUpdate(incident(9), null), "epoch", 11)));
    assertFalse(
        state.accept(
            event("INCIDENT_UPDATED", new IncidentUpdate(incident(9), null), "old-server", 100)));
  }

  @Test
  void overflowingBufferOrIncompleteSnapshotRequiresNewSynchronization() throws Exception {
    LiveState state = new LiveState();
    state.begin();
    for (int i = 0; i < 1001; i++)
      state.accept(event("TEAM_UPDATED", team(Presence.ONLINE), "epoch", i));
    assertThrows(IOException.class, () -> state.finish(List.of(snapshot())));
    state.begin();
    var page =
        new SnapshotPage(
            "snapshot", 0, 5, user, List.of(), List.of(), List.of(), "epoch", 0, "now");
    assertThrows(IOException.class, () -> state.finish(List.of(page)));
  }

  @Test
  void snapshotSwapAndResponseUpdatesPreserveNewerRevisions() throws Exception {
    LiveState state = new LiveState();
    state.begin();
    state.finish(List.of(snapshot()));
    state.response(
        Envelope.success(
            Envelope.request("SET_PRIORITY", UUID.randomUUID().toString(), Map.of()), incident(4)));
    state.accept(event("INCIDENT_UPDATED", new IncidentUpdate(incident(2), null), "epoch", 20));
    assertEquals(4, state.incident("incident").get("revision").asLong());
  }
}
