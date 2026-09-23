package cn.veryai.arcreactor.exceptions;

import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class BaseException extends RuntimeException {
  public static final int SUCCESS_CODE = 200;
  public static final int ERROR_CODE = 500;
  public static final int SYS_CODE = 500;
  public static final int UNAUTH_CODE = 401;
  public static final int BAD_REQUEST_CODE = 400;
  public static final int BASE_CODE = 1000;

  private final int code;

  public BaseException(int code, String message) {
    super(message);
    this.code = code;
  }
}
