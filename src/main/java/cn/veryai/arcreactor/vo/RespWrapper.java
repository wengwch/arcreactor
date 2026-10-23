package cn.veryai.arcreactor.vo;

import java.util.List;
import lombok.Data;

@Data
public class RespWrapper<T, E> {
  private int code;
  private String msg;
  private T result;
  private List<E> errors;

  public static <R> RespWrapper<R, Object> success(R result) {
    RespWrapper<R, Object> respWrapper = new RespWrapper<>();
    respWrapper.setCode(200);
    respWrapper.setResult(result);
    return respWrapper;
  }

  public static RespWrapper<Object, Object> failed(int code, String msg) {
    RespWrapper<Object, Object> respWrapper = new RespWrapper<>();
    respWrapper.setCode(code);
    respWrapper.setMsg(msg);
    return respWrapper;
  }

  public static <T> RespWrapper<Object, T> failed(int code, List<T> errors) {
    RespWrapper<Object, T> respWrapper = new RespWrapper<>();
    respWrapper.setCode(code);
    respWrapper.setErrors(errors);
    return respWrapper;
  }

  public static <T> RespWrapper<Object, T> failed(int code, String msg, List<T> errors) {
    RespWrapper<Object, T> respWrapper = failed(code, errors);
    respWrapper.setMsg(msg);
    return respWrapper;
  }

  public static <R, T> RespWrapper<R, T> failed(R result, int code, List<T> errors) {
    RespWrapper<R, T> respWrapper = new RespWrapper<>();
    respWrapper.setCode(code);
    respWrapper.setResult(result);
    respWrapper.setErrors(errors);
    return respWrapper;
  }
}
