package cn.veryai.arcreactor.exceptions;

import cn.veryai.arcreactor.i18n.ErrorCode;
import lombok.Getter;

/** Carries a message code and arguments; the HTTP boundary resolves the request's locale. */
@Getter
public class BaseException extends RuntimeException {
  private final int code;
  private final Object[] args;

  public BaseException(ErrorCode errorCode, Object... args) {
    this(errorCode, null, args);
  }

  public BaseException(ErrorCode errorCode, Throwable cause, Object... args) {
    super(Integer.toString(errorCode.getCode()), cause);
    this.code = errorCode.getCode();
    this.args = args == null ? new Object[0] : args.clone();
  }

  public Object[] getArgs() {
    return args.clone();
  }
}
