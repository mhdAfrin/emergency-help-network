package lk.emergency.server;

import java.security.*;
import java.util.Arrays;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public final class PasswordHasher {
  public static final int ITERATIONS = 600_000;

  public record Hash(byte[] value, byte[] salt, int iterations) {}

  public Hash hash(String password) {
    byte[] salt = new byte[16];
    new SecureRandom().nextBytes(salt);
    return new Hash(derive(password, salt, ITERATIONS), salt, ITERATIONS);
  }

  public boolean verify(String password, Hash hash) {
    return MessageDigest.isEqual(hash.value(), derive(password, hash.salt(), hash.iterations()));
  }

  private byte[] derive(String password, byte[] salt, int iterations) {
    char[] chars = password.toCharArray();
    PBEKeySpec spec = new PBEKeySpec(chars, salt, iterations, 256);
    try {
      return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("PBKDF2 unavailable", e);
    } finally {
      spec.clearPassword();
      Arrays.fill(chars, '\0');
    }
  }
}
