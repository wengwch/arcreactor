package cn.veryai.arcreactor.config;

import cn.veryai.arcreactor.vo.RespWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

@Slf4j
@RestControllerAdvice(basePackages = {"cn.veryai.arcreactor.web"})
public class GlobalResponseHandler implements ResponseBodyAdvice<Object> {

  public static final Object EMPTY = new Object();

  @Override
  public boolean supports(
      MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
    if (ResponseEntity.class.isAssignableFrom(returnType.getParameterType())) {
      return false;
    }
    return JacksonJsonHttpMessageConverter.class.isAssignableFrom(converterType);
  }

  @Override
  public final Object beforeBodyWrite(
      Object body,
      MethodParameter returnType,
      MediaType contentType,
      Class<? extends HttpMessageConverter<?>> converterType,
      ServerHttpRequest request,
      ServerHttpResponse response) {

    if (body == null) {
      body = EMPTY;
    }
    if (body instanceof RespWrapper) {
      return body;
    } else {
      return RespWrapper.success(body);
    }
  }
}
