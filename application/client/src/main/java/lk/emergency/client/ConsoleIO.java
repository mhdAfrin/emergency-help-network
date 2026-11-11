package lk.emergency.client;

import java.io.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import lk.emergency.common.Envelope;

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
      print("Password input is visible in this console; use fictional credentials.");
      return ask("Password: ");
    }
    char[] chars = console.readPassword("Password: ");
    if (chars == null) throw new EOFException();
    try {
      return new String(chars);
    } finally {
      java.util.Arrays.fill(chars, '\0');
    }
  }

  public synchronized void event(Envelope event) {
    String time =
        ZonedDateTime.now(ZoneId.of("Asia/Colombo"))
            .format(DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss z"));
    System.out.println("\n[" + time + "] " + event.type() + ": " + event.payload());
    if (!prompt.isEmpty()) System.out.print(prompt);
    System.out.flush();
  }
}
