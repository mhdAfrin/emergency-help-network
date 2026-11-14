package lk.emergency.client.ui;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import lk.emergency.common.Envelope;

/** One owner for terminal input and synchronized output; socket reading never waits for typing. */
public final class ConsoleIO {
  private final BufferedReader input =
      new BufferedReader(new InputStreamReader(System.in, java.nio.charset.StandardCharsets.UTF_8));
  private volatile String prompt = "";
  private final boolean color =
      System.console() != null
          && "1".equals(System.getenv("EHN_COLOR"))
          && System.getenv("NO_COLOR") == null;

  public synchronized void print(String text) {
    System.out.println(text);
  }

  public synchronized void heading(String text) {
    print("\n==============================================================");
    print(color ? "\u001b[36;1m" + text + "\u001b[0m" : text);
    print("--------------------------------------------------------------");
  }

  public String ask(String question) throws IOException {
    synchronized (this) {
      prompt = question;
      System.out.print(question);
      System.out.flush();
    }
    String line = input.readLine();
    prompt = "";
    if (line == null) throw new EOFException();
    return line;
  }

  public String password() throws IOException {
    Console console = System.console();
    if (console == null) return ask("Password (visible in this terminal): ");
    char[] value = console.readPassword("Password: ");
    if (value == null) throw new EOFException();
    try {
      return new String(value);
    } finally {
      Arrays.fill(value, '\0');
    }
  }

  public synchronized void result(JsonNode value) {
    for (String line : RecordPrinter.lines(value)) print(line);
  }

  public synchronized void event(Envelope event) {
    print("\n[UPDATE " + time(Instant.now().toString()) + "] " + RecordPrinter.words(event.type()));
    result(event.payload());
    if (!prompt.isEmpty()) System.out.print(prompt);
    System.out.flush();
  }

  public static String time(String value) {
    try {
      return Instant.parse(value)
          .atZone(ZoneId.of("Asia/Colombo"))
          .format(DateTimeFormatter.ofPattern("dd MMM uuuu HH:mm:ss"));
    } catch (Exception ignored) {
      return value;
    }
  }
}
