package lk.emergency.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import lk.emergency.common.*;

public final class ConsoleIO {
  private final BufferedReader input =
      new BufferedReader(new InputStreamReader(System.in, java.nio.charset.StandardCharsets.UTF_8));
  private volatile String prompt = "";

  public synchronized void print(String text) {
    System.out.println(text);
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
    if (console == null) {
      print("Password input is visible here; use fictional credentials.");
      return ask("Password: ");
    }
    char[] chars = console.readPassword("Password: ");
    if (chars == null) throw new EOFException();
    try {
      return new String(chars);
    } finally {
      Arrays.fill(chars, '\0');
    }
  }

  public synchronized void result(JsonNode payload) {
    try {
      JsonNode display = payload.deepCopy();
      localTimes(display);
      System.out.println(Json.MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(display));
    } catch (IOException e) {
      print("Could not display response");
    }
  }

  public synchronized void event(Envelope event) {
    print("\n[" + time(Instant.now().toString()) + "] " + event.type());
    result(event.payload());
    if (!prompt.isEmpty()) System.out.print(prompt);
    System.out.flush();
  }

  public static String time(String value) {
    try {
      return Instant.parse(value)
          .atZone(ZoneId.of("Asia/Colombo"))
          .format(DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss VV"));
    } catch (Exception e) {
      return value;
    }
  }

  private void localTimes(JsonNode node) {
    if (node.isObject()) {
      List<String> names = new ArrayList<>();
      node.fieldNames().forEachRemaining(names::add);
      for (String name : names) {
        JsonNode child = node.get(name);
        if (name.endsWith("At") && child.isTextual())
          ((ObjectNode) node).put(name, time(child.asText()));
        else localTimes(child);
      }
    } else if (node.isArray()) node.forEach(this::localTimes);
  }
}
