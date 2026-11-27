package cn.veryai.arcreactor.config;

import cn.veryai.arcreactor.actor.PekkoRuntime;
import java.time.Duration;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class AppConfig {

  @Bean
  public RestClient restClient(RestClient.Builder builder, ClientHttpRequestFactoryBuilder<?> requestFactoryBuilder,
      HttpClientSettings httpSettings, PekkoRuntime runtime) {
    Duration requestTimeout = runtime.getConfig().getAskTimeout();
    return builder.requestFactory(requestFactoryBuilder.build(
        httpSettings.withTimeouts(requestTimeout, requestTimeout))).build();
  }

}
