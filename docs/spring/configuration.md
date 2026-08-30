# Spring 애플리케이션 설정 (Kotlin)

Spring 애플리케이션에서 "설정"이라고 부르는 것 — 빌드 설정, 설정값 바인딩,
`@Value`, 조건부/프로파일 설정, 설정 메타데이터, 테스트용 설정 — 을 Java에서
Kotlin으로 옮길 때 실제로 달라지는 지점만 정리한다.

`application.yml`/`application.properties` 파일 자체와 프로퍼티 우선순위는 언어와
무관하게 동일하다. 달라지는 것은 **그 값을 받는 쪽의 코드**와 **컴파일 설정**이다.

## 1. 빌드 설정 — Kotlin이라서 추가로 필요한 것

Java 프로젝트의 Spring Boot 빌드 파일은 플러그인 두 개(부트 + 의존성 관리)와
스타터 의존성이면 끝난다.

```kotlin
// Java 프로젝트 (build.gradle.kts)
plugins {
    java
    id("org.springframework.boot") version "4.1.1"
    id("io.spring.dependency-management") version "1.1.7"
}

java {
    toolchain { languageVersion = JavaLanguageVersion.of(21) }
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
}
```

Kotlin 프로젝트는 여기에 **컴파일러 플러그인 두 개, 런타임 의존성 두 개, 컴파일러
플래그 두 개**가 더 붙는다. 아래는 start.spring.io가 현재(Spring Boot 4.1.1,
Kotlin 2.3.21) 생성해주는 파일이다.

```kotlin
// Kotlin 프로젝트 (build.gradle.kts)
plugins {
    kotlin("jvm") version "2.3.21"
    kotlin("plugin.spring") version "2.3.21"          // (1)
    id("org.springframework.boot") version "4.1.1"
    id("io.spring.dependency-management") version "1.1.7"
}

java {
    toolchain { languageVersion = JavaLanguageVersion.of(21) }
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.jetbrains.kotlin:kotlin-reflect")             // (2)
    implementation("tools.jackson.module:jackson-module-kotlin")      // (3)
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll(
            "-Xjsr305=strict",                            // (4)
            "-Xannotation-default-target=param-property", // (5)
        )
    }
}
```

**핵심 차이**: Java에서는 "설정"이 런타임 관심사지만, Kotlin에서는 `final` 기본값과
널 안전성 때문에 **컴파일 시점 설정이 먼저 맞아야 스프링이 동작한다**. 아래 (1)~(5)가
그 목록이다.

| 항목 | 하는 일 | 없으면 |
|------|--------|-------|
| (1) `kotlin("plugin.spring")` | `all-open` 래퍼. `@Component`/`@Async`/`@Transactional`/`@Cacheable`/`@SpringBootTest`를 `open`으로 만든다. 메타 애노테이션 덕분에 `@Configuration`/`@Controller`/`@RestController`/`@Service`/`@Repository`도 함께 열린다 | CGLIB 프록시를 만들 수 없어 `@Configuration`·`@Transactional` 등이 깨진다 |
| (2) `kotlin-reflect` | Spring Boot가 요구하는 클래스패스 의존성 (`kotlin-stdlib`와 함께) | 기동 실패 |
| (3) `jackson-module-kotlin` | 클래스패스에 있으면 Boot가 자동 등록한다 | 주 생성자 기반 JSON 역직렬화가 동작하지 않는다 |
| (4) `-Xjsr305=strict` | JSR-305 널 애노테이션이 붙은 Java 선언을 플랫폼 타입이 아니라 실제 nullable/non-null 타입으로 본다 | 스프링 API의 널 정보가 컴파일 시점에 강제되지 않는다 |
| (5) `-Xannotation-default-target=param-property` | Kotlin 2.2가 도입한 애노테이션 타깃 기본 규칙(param → property → field 순)을 미리 적용한다. Spring Boot가 권장 | 경고가 발생하고, 이후 Kotlin 버전에서 기본 동작이 바뀔 때 영향을 받는다 |

JPA를 쓴다면 `kotlin("plugin.jpa")`가 하나 더 붙는다. `no-arg` 플러그인 래퍼로
`@Entity`/`@Embeddable`/`@MappedSuperclass` 클래스에 **합성(synthetic) 무인자
생성자**를 만들어준다 (Java·Kotlin 코드에서 직접 호출할 수는 없고 리플렉션으로만
호출된다 — JPA가 필요로 하는 방식이 정확히 그것이다).

`-parameters`(Kotlin에서는 `-java-parameters`)는 직접 넣지 않아도 된다. Spring Boot
Gradle 플러그인이 Kotlin 플러그인을 감지하면 **모든 `KotlinCompile` 태스크에
`-java-parameters`를 자동으로 추가**하고, Kotlin 버전에 맞춰 `kotlin.version`
의존성 관리도 정렬한다. 이 플래그가 있어야 아래 2절의 생성자 바인딩이 동작한다.

> 널 안전성 애노테이션은 JSR-305에서 **JSpecify**로 이동 중이다. Spring 프로젝트들은
> JSpecify로 널 안전성을 제공하고, Kotlin은 2.1부터 `org.jspecify.annotations`
> 애노테이션을 별도 플래그 없이 strict로 처리한다.

## 2. 설정값 바인딩 — `@ConfigurationProperties`

Java의 기본은 JavaBean 바인딩이다. 무인자 생성자와 getter/setter가 필요하고,
그래서 설정 객체가 가변이 된다.

```java
@ConfigurationProperties("order.payment")
public class PaymentProperties {

    private String apiToken;
    private Duration timeout = Duration.ofSeconds(3);
    private final Retry retry = new Retry();

    // getter / setter 전부 필요

    public static class Retry {
        private int maxAttempts = 3;
        // getter / setter
    }
}
```

Kotlin은 생성자 바인딩을 쓴다. 주 생성자가 하나뿐이면 **별도 애노테이션 없이
자동으로 생성자 바인딩**이 되고, 기본값은 Kotlin 기본 인자로 쓴다.

```kotlin
@ConfigurationProperties("order.payment")
data class PaymentProperties(
    val apiToken: String,
    val timeout: Duration = Duration.ofSeconds(3),
    val retry: Retry = Retry(),
) {
    data class Retry(val maxAttempts: Int = 3)
}
```

**핵심 차이**: getter/setter가 사라지고 설정 객체가 불변(`val`)이 된다. "설정이
없으면 어떤 값인지"가 기본 인자로 코드에 드러나고, non-null 프로퍼티에 값이 없으면
런타임 NPE가 아니라 **기동 실패**로 잡힌다.

### 2-1. 생성자 바인딩은 등록 방식을 가린다

Spring Boot 문서는 이렇게 못 박는다 — "생성자 바인딩을 쓰려면 클래스가
`@EnableConfigurationProperties` 또는 설정 프로퍼티 스캔으로 활성화되어야 한다.
`@Component` 빈, `@Bean` 메서드로 만든 빈, `@Import`로 로드한 빈처럼 일반적인 스프링
메커니즘으로 생성된 빈에는 생성자 바인딩을 쓸 수 없다."

```kotlin
@SpringBootApplication
@ConfigurationPropertiesScan          // 스캔 방식
class OrderApplication

// 또는 개별 등록
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PaymentProperties::class)
class PaymentConfig
```

`@Component`를 붙여 두고 "왜 기본값이 안 먹지"를 찾게 되는 실수는 Java·Kotlin
공통이지만, Kotlin은 불변 `val` 클래스라 JavaBean 바인딩으로 조용히 넘어갈 수도
없다 — setter가 아예 없으니 바인딩이 실패한다.

### 2-2. 기본값이 없는 파라미터는 nullable이어야 한다

`@DefaultValue`는 Java에서는 파라미터에 그냥 붙이면 되지만, Kotlin에서는 사용 지점
타깃 `@param:`을 붙인다. 그리고 Spring Boot 문서가 명시하듯 **Kotlin에서는 기본값이
없는 파라미터를 nullable로 선언해야 한다.**

```java
public Security(String username, String password, @DefaultValue("USER") List<String> roles) {
    this.username = username;
    this.password = password;
    this.roles = roles;
}
```

```kotlin
class Security(
    val username: String?,                              // 기본값이 없으므로 nullable
    val password: String?,
    @param:DefaultValue("USER") val roles: List<String>,
)
```

값을 반드시 받아야 하는 설정이라면 nullable로 두는 대신 non-null로 선언하고
바인딩 실패(기동 실패)로 강제하는 쪽이 낫다. 즉 Kotlin에서 설정 프로퍼티의
타입은 그대로 **"필수인가 / 선택인가"의 선언**이 된다.

### 2-3. 생성자 바인딩을 쓰고 싶지 않다면

Java는 파라미터 생성자에 `@Autowired`를 붙이거나 `private`으로 만들어 opt-out
한다. Kotlin에는 한 가지 방법이 더 있다 — **빈 주 생성자를 선언하면 생성자
바인딩에서 빠진다.**

```kotlin
@ConfigurationProperties("order.payment")
class PaymentProperties {                    // 빈 주 생성자 = JavaBean 바인딩
    lateinit var apiToken: String            // setter가 있어야 하므로 var
    var timeout: Duration = Duration.ofSeconds(3)
}
```

`lateinit var`는 "바인딩 전에는 값이 없다"는 상태를 다시 코드로 들여오는 것이라,
특별한 이유(서드파티 클래스에 `@ConfigurationProperties`를 얹는 등)가 없으면 2절의
`data class`를 쓰는 편이 낫다.

### 2-4. value class는 지원이 제한된다

Spring Boot 문서는 "Java와의 상호운용 한계 때문에 value class 지원은 제한적이며,
특히 **value class의 기본값에 의존하는 것은 설정 프로퍼티 바인딩에서 동작하지
않는다**"고 안내한다. 설정 클래스는 `data class`로 쓴다.

## 3. `@Value` — 문자열 템플릿과 `$` 이스케이프

Java에서는 프로퍼티 플레이스홀더를 그대로 쓴다.

```java
@Value("${order.payment.api-token}")
private String apiToken;
```

Kotlin에서 `$`는 문자열 템플릿의 시작 문자다. 그대로 쓰면 `order`라는 변수를 찾다가
컴파일 에러가 나므로 **`\$`로 이스케이프**해야 한다 (`\$`는 Kotlin이 지원하는
이스케이프 시퀀스다).

```kotlin
@Value("\${order.payment.api-token}")
private lateinit var apiToken: String

// 생성자 주입이면
@Service
class PaymentService(
    @Value("\${order.payment.api-token}") private val apiToken: String,
    @Value("\${order.payment.timeout:3s}") private val timeout: Duration,   // 기본값 문법은 동일
)
```

**핵심 차이**: 플레이스홀더 문법(`${...}`, `${key:default}`)은 같지만 Kotlin 소스에서는
항상 `\$`로 써야 한다. 이 한 글자 때문에 `@Value`가 늘어나는 코드는 가독성이 빠르게
나빠지므로, 값이 두세 개를 넘으면 `@ConfigurationProperties`로 묶는 편이 낫다.

> `@ConfigurationProperties`의 기본값은 `Environment`에 반영되지 않는다. Boot 문서가
> 밝히듯 설정 클래스에서 기본값을 준 프로퍼티라도 사용자가 값을 지정하지 않았다면
> `Environment`에는 존재하지 않으므로, `@Value("\${my.service.enabled}")`처럼 기본값
> 없이 참조하면 실패한다.

## 4. 애노테이션 인자 — 배열과 클래스 리터럴

설정 관련 애노테이션은 유독 `String[]`과 `Class<?>` 인자가 많아서 Kotlin 문법 차이가
바로 드러난다.

```java
@Configuration
@EnableConfigurationProperties(PaymentProperties.class)
@PropertySource("classpath:payment.properties")
@Profile({"dev", "test"})
@ConditionalOnProperty(name = "order.payment.enabled", havingValue = "true")
public class PaymentConfig { }
```

```kotlin
@Configuration
@EnableConfigurationProperties(PaymentProperties::class)          // ::class
@PropertySource("classpath:payment.properties")                   // value 인자는 vararg
@Profile("dev", "test")                                           // 위와 같은 이유
@ConditionalOnProperty(name = ["order.payment.enabled"], havingValue = "true")  // [...]
class PaymentConfig
```

**핵심 차이**: 클래스 리터럴은 `X.class` → `X::class`(KClass, 컴파일러가 Java
`Class`로 변환), 배열 인자는 `{...}` → `[...]`. 다만 애노테이션의 `value` 인자가
배열 타입이면 Kotlin에서 `vararg`가 되므로 `@Profile("dev", "test")`처럼 대괄호 없이
쓸 수 있다. `name`, `havingValue` 같은 **이름 있는 배열 인자에는 `[...]`가
필요하다** — `@ConditionalOnProperty`에서 자주 걸린다.

## 5. 설정값 검증 — `@Validated`와 사용 지점 타깃

Java는 제약 애노테이션을 필드에 그대로 붙인다.

```java
@ConfigurationProperties("order.payment")
@Validated
public class PaymentProperties {

    @NotBlank
    private String apiToken;

    @Valid
    private final Retry retry = new Retry();
    // ...
}
```

Kotlin의 주 생성자 프로퍼티는 파라미터·필드·getter 중 어디에 애노테이션이 붙을지가
갈리므로, Spring 문서는 빈 검증에 **사용 지점 타깃(`@field:`, `@get:`)을 명시**하라고
안내한다.

```kotlin
@ConfigurationProperties("order.payment")
@Validated
data class PaymentProperties(
    @field:NotBlank val apiToken: String,
    @field:Valid val retry: Retry = Retry(),
) {
    data class Retry(
        @field:Min(1) @field:Max(10) val maxAttempts: Int = 3,
    )
}
```

중첩 설정 객체는 Java와 마찬가지로 `@Valid`를 붙여야 검증이 전파된다.

커스텀 `Validator`를 쓸 때 Kotlin 고유의 함정이 하나 있다. Boot 문서는
`configurationPropertiesValidator` 빈의 **`@Bean` 메서드를 `static`으로 선언하라**고
요구하는데(설정 클래스가 너무 이르게 초기화되는 것을 막기 위해서다), Kotlin에는
static이 없다.

```kotlin
@Configuration(proxyBeanMethods = false)
class ValidationConfig {

    companion object {
        @Bean
        @JvmStatic                                     // static 요구사항을 이렇게 만족시킨다
        fun configurationPropertiesValidator(): Validator = PaymentPropertiesValidator()
    }
}
```

**핵심 차이**: 검증 규칙 자체는 동일하고, Kotlin에서는 **애노테이션이 어디에 붙는지**
(`@field:`)와 **static 요구사항**(`companion object` + `@JvmStatic`)만 추가로 신경 쓴다.

## 6. 코드로 하는 설정 — `BeanRegistrar`와 Kotlin DSL

조건에 따라 빈을 다르게 등록해야 할 때 Java는 `BeanRegistrar`를 구현한다
(Spring Framework 7 / Spring Boot 4에서 도입된 프로그래밍 방식 빈 등록이다).

```java
class MyBeanRegistrar implements BeanRegistrar {

    @Override
    public void register(BeanRegistry registry, Environment env) {
        registry.registerBean("foo", Foo.class);
        registry.registerBean("bar", Bar.class, spec -> spec
                .prototype()
                .lazyInit()
                .description("Custom description")
                .supplier(context -> new Bar(context.bean(Foo.class))));
        if (env.matchesProfiles("baz")) {
            registry.registerBean(Baz.class, spec -> spec
                    .supplier(context -> new Baz("Hello World!")));
        }
    }
}
```

Kotlin에는 같은 API 위에 얹은 `BeanRegistrarDsl`이 있다. 옵션이 이름 있는 인자가 되고,
프로파일 분기가 `profile("baz") { }` 블록이 된다.

```kotlin
class MyBeanRegistrar : BeanRegistrarDsl({
    registerBean<Foo>()
    registerBean(
        name = "bar",
        prototype = true,
        lazyInit = true,
        description = "Custom description") {
        Bar(bean<Foo>())          // Bar(bean()) 으로도 쓸 수 있다
    }
    profile("baz") {
        registerBean { Baz("Hello World!") }
    }
})
```

등록 방법은 양쪽 다 동일하다.

```kotlin
@Configuration
@Import(MyBeanRegistrar::class)
class MyConfiguration
```

**핵심 차이**: 람다 하나로 등록 로직을 표현하므로 `if`/`for`/프로파일 분기를 애노테이션
조건(`@ConditionalOnProperty` 등)이 아니라 **그냥 Kotlin 코드**로 쓸 수 있다. 타입은
`registerBean<Foo>()`처럼 reified 타입 파라미터로 넘어간다.

## 7. 설정 메타데이터 — Kotlin에서는 kapt

`spring-boot-configuration-processor`는 `@ConfigurationProperties` 클래스를 읽어
IDE 자동완성용 메타데이터(`spring-configuration-metadata.json`)를 만들어준다. Java는
`annotationProcessor` 설정에 넣으면 끝이지만, 그 설정은 **Java 소스만** 처리한다.
Kotlin 클래스에 대해 메타데이터를 만들려면 Boot 문서가 안내하는 대로 kapt를 쓴다.

```kotlin
plugins {
    kotlin("jvm") version "2.3.21"
    kotlin("plugin.spring") version "2.3.21"
    kotlin("kapt") version "2.3.21"
}

dependencies {
    kapt("org.springframework.boot:spring-boot-configuration-processor")
}
```

Boot 문서는 동시에 한계도 명시한다 — "kapt가 제공하는 모델의 한계 때문에 기본값
감지나 deprecated 항목 감지 같은 일부 기능은 동작하지 않는다."

**핵심 차이**: 메타데이터는 있으면 편한 기능(IDE 자동완성·문서화)이지 애플리케이션
동작에 필요한 것은 아니다. kapt를 빌드에 들이는 비용이 아깝다면 생략해도 바인딩은
그대로 동작한다.

## 8. 테스트에서의 설정

프로퍼티를 테스트에서 덮어쓸 때 4절의 배열 문법 차이가 다시 나온다.

```java
@SpringBootTest(properties = {"order.payment.timeout=1s"})
@ActiveProfiles({"test"})
class PaymentServiceTest { }
```

```kotlin
@SpringBootTest(properties = ["order.payment.timeout=1s"])
@ActiveProfiles("test")                                     // value 인자라 vararg
class PaymentServiceTest
```

컨테이너 포트처럼 **런타임에 정해지는 값**을 프로퍼티로 넣는 `@DynamicPropertySource`는
static 메서드를 요구한다 — "`@DynamicPropertySource`가 붙은 메서드는 `static`이어야
하고 `DynamicPropertyRegistry` 하나를 인자로 받아야 한다". Kotlin에서는
`companion object` + `@JvmStatic`으로 만족시킨다.

```kotlin
@SpringJUnitConfig(/* ... */)
@Testcontainers
class ExampleIntegrationTests {

    companion object {

        @Container
        @JvmStatic
        val redis: GenericContainer<*> =
            GenericContainer("redis:5.0.3-alpine").withExposedPorts(6379)

        @DynamicPropertySource
        @JvmStatic
        fun redisProperties(registry: DynamicPropertyRegistry) {
            registry.add("redis.host", redis::getHost)
            registry.add("redis.port", redis::getFirstMappedPort)
        }
    }
}
```

## 주의사항 요약

| 항목 | 내용 |
|------|------|
| 컴파일러 플러그인 | `kotlin("plugin.spring")` 없으면 `@Configuration`·`@Transactional` 프록시 불가. JPA는 `kotlin("plugin.jpa")` 추가 |
| 클래스패스 | `kotlin-stdlib` + `kotlin-reflect` 필수, JSON을 다루면 `jackson-module-kotlin` |
| 컴파일러 플래그 | `-Xjsr305=strict`, `-Xannotation-default-target=param-property` (Boot 권장). `-java-parameters`는 Boot Gradle 플러그인이 자동 추가 |
| 생성자 바인딩 활성화 | `@EnableConfigurationProperties` 또는 `@ConfigurationPropertiesScan`. `@Component`/`@Bean`/`@Import`로 만든 빈에는 적용되지 않는다 |
| 기본값 없는 파라미터 | Kotlin에서는 nullable로 선언해야 한다 (또는 non-null로 두고 기동 실패로 강제) |
| 생성자 바인딩 opt-out | 빈 주 생성자를 선언하면 JavaBean 바인딩으로 빠진다 |
| value class | 설정 바인딩에서 기본값이 동작하지 않는다 — `data class`를 쓴다 |
| `@Value` | `@Value("\${...}")` — `$`는 문자열 템플릿이라 항상 이스케이프 |
| 애노테이션 배열 인자 | `{...}`가 아니라 `[...]`. 단 `value` 인자는 vararg라 대괄호 불필요 |
| 클래스 인자 | `X.class`가 아니라 `X::class` |
| 검증 | 제약 애노테이션에 `@field:` 사용 지점 타깃, 중첩 객체는 `@field:Valid` |
| 커스텀 Validator | `configurationPropertiesValidator`의 `@Bean` 메서드는 static — `companion object` + `@JvmStatic` |
| 설정 메타데이터 | Kotlin 소스는 `annotationProcessor`가 아니라 kapt로 처리 (기본값·deprecated 감지 제한) |
| `@DynamicPropertySource` | static 요구 — `companion object` + `@JvmStatic` |

## 관련 문서

- [mvc/basics.md](mvc/basics.md)
- [mvc/jpa.md](mvc/jpa.md)
- [testing.md](testing.md)
- [../stdlib/conventions.md](../stdlib/conventions.md)

## 참고 자료

- [Kotlin :: Spring Boot Reference](https://docs.spring.io/spring-boot/reference/features/kotlin.html) — 요구 버전, `kotlin-reflect`, `-Xannotation-default-target=param-property`, kotlin-spring 플러그인, `@ConfigurationProperties` 생성자 바인딩, value class 제한, kapt 설정 프로세서
- [Externalized Configuration :: Spring Boot Reference](https://docs.spring.io/spring-boot/reference/features/external-config.html) — 생성자 바인딩 규칙(`@EnableConfigurationProperties`/스캔, `-parameters`, 빈 주 생성자 opt-out, Kotlin nullable 파라미터, `@param:DefaultValue`), 기본값과 `Environment`, 검증과 `configurationPropertiesValidator`의 static `@Bean`
- [Reacting to Other Plugins :: Spring Boot Gradle Plugin](https://docs.spring.io/spring-boot/gradle-plugin/reacting.html) — Kotlin 플러그인 감지 시 `kotlin.version` 정렬과 `-java-parameters` 자동 추가
- [Annotations :: Spring Framework Kotlin](https://docs.spring.io/spring-framework/reference/languages/kotlin/annotations.html) — nullability = 필수 여부, 빈 검증의 사용 지점 타깃(`@field:`, `@get:`)
- [Programmatic Bean Registration :: Spring Framework](https://docs.spring.io/spring-framework/reference/core/beans/java/programmatic-bean-registration.html) — `BeanRegistrar` / Kotlin `BeanRegistrarDsl`, `profile { }`, `@Import` 등록
- [Bean Registration DSL :: Spring Framework Kotlin](https://docs.spring.io/spring-framework/reference/languages/kotlin/bean-registration-dsl.html)
- [Context Configuration with Dynamic Property Sources :: Spring Framework](https://docs.spring.io/spring-framework/reference/testing/testcontext-framework/ctx-management/dynamic-property-sources.html) — static 요구사항과 Kotlin `companion object` + `@JvmStatic` 예제
- [All-open compiler plugin :: Kotlin](https://kotlinlang.org/docs/all-open-plugin.html) — `kotlin-spring`이 여는 애노테이션 목록
- [No-arg compiler plugin :: Kotlin](https://kotlinlang.org/docs/no-arg-plugin.html) — `kotlin-jpa`와 합성 무인자 생성자
- [Characters :: Kotlin](https://kotlinlang.org/docs/characters.html) — 이스케이프 시퀀스 목록 (`\$` 포함)
- [Annotations :: Kotlin](https://kotlinlang.org/docs/annotations.html) — 배열 인자 `[...]`, `value`의 vararg 처리, `::class` 인자
- [What's new in Kotlin 2.2.0 :: Kotlin](https://kotlinlang.org/docs/whatsnew22.html) — 애노테이션 사용 지점 타깃 기본 규칙 변경과 `-Xannotation-default-target`
- [Calling Java from Kotlin :: Kotlin](https://kotlinlang.org/docs/java-interop.html) — `-Xjsr305=strict`, JSpecify 애노테이션 처리
