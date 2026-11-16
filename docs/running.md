# 应用启动

使用 JDK 21 或以上版本。应用使用 Pekko 的 `virtual-thread-executor`，应用 JVM 需要以下参数，
以便 Pekko 通过反射读取虚拟线程默认调度器的负载指标：

```text
--add-opens=java.base/java.lang=ALL-UNNAMED
```

## IntelliJ IDEA

在 `Run → Edit Configurations → ArcreactorApplication → Modify options → Add VM options`
中添加上述参数，再重新启动应用。该参数属于应用的 VM options；IDE 自身的 JVM 参数和
应用的 Program arguments 不控制这一权限。

## Maven

`pom.xml` 已在 `spring-boot-maven-plugin` 的 `jvmArguments` 中配置该参数，使用以下方式启动时自动生效：

```shell
./mvnw spring-boot:run
```

Spring Boot Maven 插件会创建应用 JVM；只设置 Maven 自身的 JVM 参数不能替代应用的
`jvmArguments`。参见 [Spring Boot 启动参数说明](https://docs.spring.io/spring-boot/maven-plugin/run.html)。

## 可执行 JAR

直接启动 JAR 时，参数放在 `-jar` 前面：

```shell
java --add-opens=java.base/java.lang=ALL-UNNAMED -jar target/arcreactor-0.0.1-SNAPSHOT.jar
```

## 虚拟线程负载指标告警

`Failed to get the default scheduler of virtual thread` 表示 Pekko 无法访问默认调度器。
Pekko 1.7.0 会捕获该异常并输出告警，虚拟线程执行器仍会创建。
当前 `virtual-thread-dispatcher` 配置为普通 `Dispatcher`，告警中出现 `BalancingDispatcher`
不表示应用配置了该类型。补齐上述 VM options 后重新启动即可开放所需的反射访问。
