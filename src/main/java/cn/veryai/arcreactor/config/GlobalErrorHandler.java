package cn.veryai.arcreactor.config;

import cn.veryai.arcreactor.exceptions.BaseException;
import cn.veryai.arcreactor.i18n.ErrorCode;
import cn.veryai.arcreactor.i18n.MessageService;
import cn.veryai.arcreactor.util.KVRecord;
import cn.veryai.arcreactor.vo.RespWrapper;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RequiredArgsConstructor
@RestControllerAdvice(basePackages = "cn.veryai.arcreactor.web.api")
public class GlobalErrorHandler {
  private final MessageService messageService;

  @ResponseStatus(code = HttpStatus.OK)
  @ExceptionHandler(value = BaseException.class)
  @ResponseBody
  public RespWrapper<Object, Object> handleBaseException(HttpServletRequest req, BaseException t) {
    String message = messageService.getMessage(t.getCode(), t.getArgs());
    log.info(
        "remote host: {}, request uri: {}, msg: {}",
        req.getRemoteHost(),
        req.getRequestURI(),
        message);
    if (t.getCause() != null) {
      log.warn("business error, code: {}, request uri: {}", t.getCode(), req.getRequestURI(), t);
    }
    return RespWrapper.failed(t.getCode(), message);
  }

  @ResponseStatus(code = HttpStatus.BAD_REQUEST)
  @ExceptionHandler(
      value = {
        MethodArgumentNotValidException.class,
        HttpMessageNotReadableException.class,
        BindException.class,
      })
  @ResponseBody
  public RespWrapper<Object, KVRecord> handleMethodArgumentNotValidException(
      HttpServletRequest req, Exception ex) {

    log.info(
        "bad request, remote host: {}, request uri: {}, msg: {}",
        req.getRemoteHost(),
        req.getRequestURI(),
        ex.getMessage());
    List<KVRecord> errors = new ArrayList<>();
    switch (ex) {
      case MethodArgumentNotValidException methodArgumentNotValidException ->
          errors =
              methodArgumentNotValidException.getBindingResult().getFieldErrors().stream()
                  .map(
                      fieldError ->
                          new KVRecord(fieldError.getField(), fieldError.getDefaultMessage()))
                  .collect(Collectors.toList());
      case BindException bindException ->
          errors =
              bindException.getBindingResult().getFieldErrors().stream()
                  .map(
                      fieldError ->
                          new KVRecord(fieldError.getField(), fieldError.getDefaultMessage()))
                  .collect(Collectors.toList());
      default -> {}
    }
    return RespWrapper.failed(
        ErrorCode.BAD_REQUEST.getCode(),
        messageService.getMessage(ErrorCode.BAD_REQUEST.getCode()),
        errors);
  }

  @ResponseStatus(code = HttpStatus.INTERNAL_SERVER_ERROR)
  @ExceptionHandler(value = Throwable.class)
  @ResponseBody
  public RespWrapper<Object, Object> handleException(HttpServletRequest req, Throwable t) {
    log.error(
        "unknown error, remote host: {}, request uri: {}.",
        req.getRemoteHost(),
        req.getRequestURI(),
        t);
    return RespWrapper.failed(
        ErrorCode.INTERNAL_ERROR.getCode(),
        messageService.getMessage(ErrorCode.INTERNAL_ERROR.getCode()));
  }
}
