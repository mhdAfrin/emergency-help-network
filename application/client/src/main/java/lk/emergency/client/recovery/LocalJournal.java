package lk.emergency.client.recovery;

import java.io.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import lk.emergency.client.network.*;
import lk.emergency.common.*;

/**
 * Local fictional-data journal. Writes are atomic and short file locks merge separate client
 * processes.
 */
public final class LocalJournal {
  public record Saved(String owner, Envelope request) {}

  public record Draft(String category, String address, String description) {}

  @FunctionalInterface
  private interface Work<T> {
    T run(Path file) throws IOException;
  }

  private final Path root;
  private static final java.util.concurrent.ConcurrentHashMap<Path, Object> JVM_LOCKS =
      new java.util.concurrent.ConcurrentHashMap<>();
  private static final Set<String> BUSINESS =
      Set.of(
          "CREATE_INCIDENT",
          "SET_PRIORITY",
          "ASSIGN_TEAM",
          "CANCEL_ASSIGNMENT",
          "REJECT_INCIDENT",
          "CLOSE_INCIDENT",
          "SET_TEAM_AVAILABILITY",
          "RESPOND_ASSIGNMENT",
          "UPDATE_ASSIGNMENT_STATUS");

  public LocalJournal(Path base, String endpoint) throws IOException {
    try {
      root =
          base.resolve(
              HexFormat.of()
                  .formatHex(
                      MessageDigest.getInstance("SHA-256")
                          .digest(endpoint.getBytes(java.nio.charset.StandardCharsets.UTF_8))));
      Files.createDirectories(root);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  private synchronized <T> T locked(String owner, Work<T> work) throws IOException {
    String safe;
    try {
      safe = owner != null && owner.matches("[0-9]{5}") ? owner : UUID.fromString(owner).toString();
    } catch (Exception e) {
      throw new IOException("Invalid local owner ID", e);
    }
    Path lock = root.resolve(safe + ".lock");
    synchronized (JVM_LOCKS.computeIfAbsent(lock.toAbsolutePath(), p -> new Object())) {
      try (FileChannel channel =
              FileChannel.open(lock, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
          FileLock ignored = channel.lock()) {
        return work.run(root.resolve(safe + ".json"));
      }
    }
  }

  private List<Saved> read(Path file) throws IOException {
    List<Saved> result = new ArrayList<>();
    if (!Files.exists(file)) return result;
    var tree = Json.MAPPER.readTree(Files.readAllBytes(file));
    if (!tree.isArray())
      throw new IOException("Journal is damaged; preserve it before manual recovery");
    for (var item : tree) result.add(Json.MAPPER.treeToValue(item, Saved.class));
    return result;
  }

  private void write(Path file, Object data) throws IOException {
    Path temp = Files.createTempFile(root, "journal-", ".tmp");
    try {
      Files.write(temp, Json.MAPPER.writeValueAsBytes(data));
      try {
        Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
      } catch (AtomicMoveNotSupportedException e) {
        Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
      }
    } finally {
      Files.deleteIfExists(temp);
    }
  }

  public List<Saved> list(String owner) throws IOException {
    return locked(owner, file -> List.copyOf(read(file)));
  }

  public void put(String owner, Envelope request) throws IOException {
    if (!BUSINESS.contains(request.type()))
      throw new IOException(
          "Only business requests belong in the journal; credentials are never saved");
    locked(
        owner,
        file -> {
          var saved = read(file);
          saved.removeIf(s -> s.request().requestId().equals(request.requestId()));
          if (saved.size() >= 20) throw new IOException("Resolve saved pending actions first");
          saved.add(new Saved(owner, request));
          write(file, saved);
          return null;
        });
  }

  public void remove(String owner, String id) throws IOException {
    locked(
        owner,
        file -> {
          var saved = read(file);
          saved.removeIf(s -> s.request().requestId().equals(id));
          write(file, saved);
          return null;
        });
  }

  private Path draftPath(Path user) {
    return user.resolveSibling(user.getFileName().toString().replace(".json", "-draft.json"));
  }

  public Draft draft(String owner) throws IOException {
    return locked(
        owner,
        file -> {
          Path draft = draftPath(file);
          return Files.exists(draft)
              ? Json.MAPPER.readValue(Files.readAllBytes(draft), Draft.class)
              : null;
        });
  }

  public void draft(String owner, Draft draft) throws IOException {
    locked(
        owner,
        file -> {
          write(draftPath(file), draft);
          return null;
        });
  }

  public void clearDraft(String owner) throws IOException {
    locked(
        owner,
        file -> {
          Files.deleteIfExists(draftPath(file));
          return null;
        });
  }
}
