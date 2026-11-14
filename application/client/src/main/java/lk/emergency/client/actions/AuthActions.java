package lk.emergency.client.actions;

import static lk.emergency.common.Types.*;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.*;
import java.util.*;
import java.util.concurrent.TimeoutException;
import lk.emergency.client.network.*;
import lk.emergency.client.recovery.*;
import lk.emergency.client.support.ClientContext;
import lk.emergency.client.ui.*;
import lk.emergency.common.*;
import lk.emergency.common.Dtos.*;

public final class AuthActions extends ActionSupport {
  public AuthActions(ClientContext context) {
    super(context);
  }

  public void handle(String action) throws IOException, TimeoutException {
    switch (action) {
      case "1" ->
          io.result(
              read(
                  "REGISTER_CITIZEN",
                  new Registration(
                      io.ask("Username: "),
                      io.ask("Display name: "),
                      io.password(),
                      io.ask("Contact number (optional): "))));
      case "2" -> {
        live.begin();
        JsonNode response = read("LOGIN", new Login(io.ask("Username: "), io.password()));
        UserView logged = Json.MAPPER.treeToValue(response, UserView.class);
        try {
          boolean synchronizedState = false;
          for (int attempt = 0; attempt < 3; attempt++) {
            try {
              live.synchronize(connection, logged);
              synchronizedState = true;
              break;
            } catch (IOException e) {
              if (attempt == 2) throw e;
            }
          }
          if (!synchronizedState) throw new IOException("Please log in and synchronize again");
        } catch (IOException | TimeoutException failure) {
          try {
            connection.call("LOGOUT", Map.of());
          } catch (Exception ignored) {
          }
          throw failure;
        }
        context.signIn(logged);
        io.print(
            "Welcome, "
                + logged.displayName()
                + ". Your account reference is "
                + logged.id()
                + ".");
        io.print("Login successful. Your current assignments:");
        for (JsonNode assignment : live.assignments())
          io.print(
              assignment.path("serviceType").asText()
                  + ": "
                  + assignment.path("state").asText()
                  + " | "
                  + assignment.path("id").asText());
        int saved = pending.list(context.user().id()).size();
        if (saved > 0)
          io.print(
              saved + " saved uncertain action(s). Open Account / Saved actions to check them.");
      }
      default -> io.print("Choose a displayed action.");
    }
  }
}
