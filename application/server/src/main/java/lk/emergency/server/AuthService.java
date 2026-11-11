package lk.emergency.server;

import static lk.emergency.common.Types.*;

import lk.emergency.common.*;
import lk.emergency.common.Dtos.*;

public final class AuthService {
  private final Database db;
  private final UserRepository users = new UserRepository();
  private final PasswordHasher hasher = new PasswordHasher();
  private final PasswordHasher.Hash dummy = hasher.hash("NonAccountDummyPassword");

  public AuthService(Database db) {
    this.db = db;
  }

  public UserView register(Registration input) {
    String username = Validation.text(input.username(), 3, 40, "Username"),
        name = Validation.text(input.displayName(), 1, 80, "Display name");
    String password = Validation.password(input.password());
    String contact =
        input.contactNumber() == null
            ? null
            : Validation.text(input.contactNumber(), 0, 40, "Contact");
    PasswordHasher.Hash hash = hasher.hash(password);
    return db.transaction(
        c -> {
          if (users.find(c, username) != null)
            throw new DomainException(ErrorCode.VALIDATION_ERROR, "Username already exists");
          return users.insert(c, username, name, contact, Role.CITIZEN, hash);
        });
  }

  public UserView login(Login input) {
    String username, password;
    try {
      username = Validation.text(input.username(), 3, 40, "Username");
      password = Validation.password(input.password());
    } catch (DomainException e) {
      hasher.verify("InvalidCredentialInput", dummy);
      throw new DomainException(ErrorCode.UNAUTHENTICATED, "Invalid username or password");
    }
    UserRepository.Account account = db.call(c -> users.find(c, username));
    boolean valid = hasher.verify(password, account == null ? dummy : account.hash());
    if (account == null || !valid)
      throw new DomainException(ErrorCode.UNAUTHENTICATED, "Invalid username or password");
    return account.user();
  }

  public void seed() {
    String[] names = {
      "operator", "citizen1", "citizen2", "police1", "ambulance1", "fire1", "fire2"
    };
    for (int i = 0; i < names.length; i++) {
      String name = names[i];
      Role role = i == 0 ? Role.OPERATOR : i < 3 ? Role.CITIZEN : Role.RESPONDER;
      if (db.call(c -> users.find(c, name)) == null) {
        PasswordHasher.Hash hash = hasher.hash("DemoPass123!");
        db.transaction(
            c -> {
              if (users.find(c, name) == null) users.insert(c, name, name, null, role, hash);
              return null;
            });
      }
      if (role == Role.RESPONDER) {
        ServiceType service =
            i == 3 ? ServiceType.POLICE : i == 4 ? ServiceType.AMBULANCE : ServiceType.FIRE_RESCUE;
        db.transaction(
            c -> {
              new TeamRepository()
                  .seed(c, users.find(c, name).user().id(), name + " simulated team", service);
              return null;
            });
      }
    }
  }
}
