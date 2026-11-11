package lk.emergency.common;

import lk.emergency.common.Types.ErrorCode;

public final class DomainException extends RuntimeException {
  private final ErrorCode code;

  public DomainException(ErrorCode code, String message) {
    super(message);
    this.code = code;
  }

  public ErrorCode code() {
    return code;
  }
}
