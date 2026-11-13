
package lk.emergency.common;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import org.junit.jupiter.api.Test;

import lk.emergency.common.Dtos.IncidentInput;
import lk.emergency.common.Types.Category;

class ValidationTest {

  @Test
  void acceptsAllIncidentCategories() {
    for (Category category : Category.values()) {
      assertNotNull(category);
    }

    assertEquals(5, Category.values().length);
  }

  @Test
  void rejectsEmptyRequiredText() {
    assertThrows(
      DomainException.class,
      () -> Validation.text("", 1, 200, "Address")
    );

    assertThrows(
      DomainException.class,
      () -> Validation.text("   ", 1, 200, "Address")
    );
  }

  @Test
  void rejectsTextExceedingMaximumLength() {
    assertThrows(
      DomainException.class,
      () -> Validation.text("A".repeat(201), 1, 200, "Address")
    );

    assertThrows(
      DomainException.class,
      () -> Validation.text("A".repeat(1001), 1, 1000, "Description")
    );
  }

  @Test
  void acceptsMaximumLengthText() {
    assertEquals(
      200,
      Validation.text("A".repeat(200), 1, 200, "Address").length()
    );

    assertEquals(
      1000,
      Validation.text("A".repeat(1000), 1, 1000, "Description").length()
    );
  }

  @Test
  void trimsWhitespace() {
    assertEquals(
      "Colombo",
      Validation.text("  Colombo  ", 1, 200, "Address")
    );
  }

  @Test
  void acceptsUnicodeText() {
    String sinhala = "ගිනි අනතුරක් සිදුවී ඇත";
    String tamil = "தீ விபத்து ஏற்பட்டுள்ளது";

    assertEquals(
      sinhala,
      Validation.text(sinhala, 1, 1000, "Description")
    );

    assertEquals(
      tamil,
      Validation.text(tamil, 1, 1000, "Description")
    );

    assertEquals(
      "🚒",
      Validation.text("🚒", 1, 10, "Description")
    );
  }

  @Test
  void rejectsInvalidPasswords() {
    assertThrows(
      DomainException.class,
      () -> Validation.password("1234567")
    );

    assertThrows(
      DomainException.class,
      () -> Validation.password("A".repeat(129))
    );
  }

  @Test
  void acceptsValidPasswordBoundaries() {
    assertEquals("12345678", Validation.password("12345678"));
    assertEquals("A".repeat(128), Validation.password("A".repeat(128)));
  }

  @Test
  void acceptsValidIncidentPayload() {
    IncidentInput input = Validation.payload(
      Json.tree(Map.of(
        "category", "FIRE",
        "address", "Colombo",
        "description", "Smoke detected",
        "latitude", 6.9271,
        "longitude", 79.8612
      )),
      IncidentInput.class
    );

    assertEquals(Category.FIRE, input.category());
    assertEquals("Colombo", input.address());
  }

  @Test
  void rejectsUnknownIncidentFields() {
    assertThrows(
      DomainException.class,
      () -> Validation.payload(
        Json.tree(Map.of(
          "category", "FIRE",
          "address", "Colombo",
          "description", "Smoke detected",
          "isAdmin", true
        )),
        IncidentInput.class
      )
    );
  }

  @Test
  void rejectsInvalidCategory() {
    assertThrows(
      DomainException.class,
      () -> Validation.payload(
        Json.tree(Map.of(
          "category", "INVALID_CATEGORY",
          "address", "Colombo",
          "description", "Test"
        )),
        IncidentInput.class
      )
    );
  }

  @Test
  void validatesEmptyPayload() {
    assertDoesNotThrow(
      () -> Validation.empty(Json.tree(Map.of()))
    );

    assertThrows(
      DomainException.class,
      () -> Validation.empty(Json.tree(Map.of("extra", "value")))
    );
  }
}
