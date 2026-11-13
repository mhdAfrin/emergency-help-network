import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.zip.*;

/**
 * Source export excludes Git, local tools/data and Maven output; runtime ZIP includes only built
 * JARs and instructions.
 */
public class CreateSubmission {
  static void file(ZipOutputStream zip, String name, Path path) throws IOException {
    ZipEntry entry = new ZipEntry(name.replace('\\', '/'));
    entry.setTime(0);
    zip.putNextEntry(entry);
    Files.copy(path, zip);
    zip.closeEntry();
  }

  static void text(ZipOutputStream zip, String name, String text) throws IOException {
    ZipEntry entry = new ZipEntry(name);
    entry.setTime(0);
    zip.putNextEntry(entry);
    zip.write(text.getBytes(StandardCharsets.UTF_8));
    zip.closeEntry();
  }

  public static void main(String[] args) throws Exception {
    Path root = Path.of(args.length == 0 ? ".." : args[0]).toAbsolutePath().normalize(),
        output = root.resolve("application/.local/submission");
    Files.createDirectories(output);
    List<Path> files = new ArrayList<>();
    Files.walkFileTree(
        root,
        new SimpleFileVisitor<Path>() {
          @Override
          public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) {
            String name = directory.getFileName().toString();
            return !directory.equals(root) && Set.of(".git", ".local", "target").contains(name)
                ? FileVisitResult.SKIP_SUBTREE
                : FileVisitResult.CONTINUE;
          }

          @Override
          public FileVisitResult visitFile(Path path, BasicFileAttributes attributes) {
            String name = path.getFileName().toString();
            if (attributes.isRegularFile()
                && !Files.isSymbolicLink(path)
                && !name.equals("server.properties")
                && !name.endsWith(".db")
                && !name.endsWith(".db-wal")
                && !name.endsWith(".db-shm")) files.add(path);
            return FileVisitResult.CONTINUE;
          }
        });
    files.sort(Comparator.comparing(p -> root.relativize(p).toString()));
    Path source = output.resolve("emergency-help-network-source.zip");
    try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(source))) {
      for (Path path : files) file(zip, root.relativize(path).toString(), path);
    }
    Path server = root.resolve("application/server/target/server-1.0.0-SNAPSHOT.jar"),
        client = root.resolve("application/client/target/client-1.0.0-SNAPSHOT.jar");
    if (!Files.exists(server) || !Files.exists(client))
      throw new IllegalStateException("Build before packaging runnable JARs");
    Path runtime = output.resolve("emergency-help-network-runtime.zip");
    try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(runtime))) {
      file(zip, "server.jar", server);
      file(zip, "client.jar", client);
      file(
          zip,
          "config/server.properties.example",
          root.resolve("application/config/server.properties.example"));
      text(
          zip,
          "START-HERE.txt",
          "Emergency Help Network — University of Ruhuna / EC5205\n"
              + "Academic demonstration — no real emergency dispatch.\n\n"
              + "Requires Java 21 installed on PATH. Extract this ZIP into a writable folder.\n"
              + "From that folder run:\n"
              + "  java -jar server.jar --seed\n"
              + "  java -jar server.jar\n"
              + "In separate terminals in the same folder run:\n"
              + "  java -jar client.jar\n\n"
              + "Default host 127.0.0.1, port 5050. For LAN bind 0.0.0.0 and use the server"
              + " laptop's LAN IP as client destination. Use a permitted trusted LAN only; TCP is"
              + " unencrypted.\n"
              + "Demo users: operator, citizen1, citizen2, police1, ambulance1, fire1, fire2.\n"
              + "Demo password: DemoPass123!\n"
              + "Copy config/server.properties.example to config/server.properties to customize.\n"
              + "Persistent DB: .local/data/emergency.db. Client journals: .local/client/. Never"
              + " erase these to retry an uncertain action.\n"
              + "Log in again after reconnect; menu 90 reconciles saved original request IDs.\n"
              + "Use the source ZIP for protocol/design/test evidence and Eclipse instructions.\n");
    }
    System.out.println(
        "SOURCE: "
            + source
            + " ("
            + files.size()
            + " files; no Git/local tools/databases/build output)");
    System.out.println("RUNTIME: " + runtime + " (Java 21 server/client JARs and instructions)");
  }
}
