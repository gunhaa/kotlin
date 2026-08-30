# Kotlin과 Java의 핵심 컨벤션 차이

Java Spring MVC/WebFlux 경험이 있는 개발자가 Kotlin 코드를 읽을 때 가장 먼저
부딪히는 관용구(idiom) 차이를 정리한다. 실행 가능한 예제는
`src/main/kotlin/com/kotlin/stdlib/KotlinVsJavaConventions.kt`에 있고, 각 절 제목은
그 파일의 섹션 번호와 1:1로 대응한다.

```bash
./gradlew compileKotlin   # 컴파일만 확인
```

## 1. Null 안전성

Java는 모든 참조 타입이 기본적으로 null이 될 수 있고, 그 사실이 타입에
드러나지 않는다. NPE는 런타임에야 발견된다.

```java
String nickname = findNicknameById("u1");     // null일 수도 있다는 걸 코드만 봐선 모른다
int length = (nickname != null) ? nickname.length() : 0;

User user = findUserById("u1");
System.out.println(user.getName());           // NPE 가능성이 타입 시그니처에 없다
```

Kotlin은 "null이 될 수 있는지"를 타입 자체(`String` vs `String?`)에 넣는다.
컴파일러가 null 가능 타입의 멤버 접근을 막기 때문에, null 처리를 빼먹으면
컴파일이 안 된다.

```kotlin
val nickname: String? = findNicknameById("u1")
val length = nickname?.length ?: 0            // null이면 0, 아니면 length

val user: User? = findUserById("u1")
println(user!!.name)                           // "여기선 null 아님을 내가 보장" (틀리면 NPE)
```

**핵심 차이**: Java의 `Optional<T>`는 라이브러리 차원의 관례일 뿐 강제되지
않지만, Kotlin의 `?`는 타입 시스템 차원에서 강제된다.

## 2. 데이터 클래스 vs POJO/record

Java 16 이전에는 불변 값 객체 하나를 만들려고 필드·생성자·getter·
`equals`/`hashCode`/`toString`을 손으로 작성하거나 Lombok `@Data`에
의존해야 했다.

```java
// Java 16+ record — Kotlin data class와 가장 가까운 대응
record User(String id, String name, String email) {}
```

```kotlin
data class User(val id: String, val name: String, val email: String)
```

record가 나오면서 격차는 많이 줄었지만, data class가 여전히 더 주는 것들이 있다.

| 기능 | Java record | Kotlin data class |
|------|-------------|--------------------|
| 불변, `equals`/`hashCode`/`toString` 자동 생성 | ✅ | ✅ |
| 일부 필드만 바꾼 복사본 (`copy(name = "Gun")`) | ❌ (직접 생성자 재호출 필요) | ✅ |
| 구조 분해 선언 (`val (id, name) = user`) | ❌ | ✅ |
| `var` 프로퍼티 허용 (가변 데이터 객체) | ❌ (record는 항상 불변) | ✅ (권장하진 않음) |

## 3. 불변성이 기본값

Java는 가변이 기본값이고, 불변으로 만들려면 매번 명시해야 한다.

```java
List<String> list = new ArrayList<>();        // 기본이 가변
final String name = "a";                        // 불변으로 하려면 매번 final
List<String> readonly = List.of("a", "b", "c"); // 불변 컬렉션은 별도로 명시
```

Kotlin은 반대로, "일단 불변(`val`)으로 쓰고 필요할 때만 가변(`var`)"이
관용구다. 컬렉션도 `listOf()`(읽기 전용)가 기본이고 `mutableListOf()`는
의도적으로 골라야 한다.

```kotlin
val name = "a"                                  // 기본이 불변
val readonly = listOf("a", "b", "c")            // 기본이 읽기 전용
val mutable = mutableListOf("a", "b")           // 가변이 필요할 때만 명시
```

**핵심 차이**: 코드 리뷰에서 "이거 왜 불변이 아니에요?"라고 묻는 방향이
Kotlin에서는 정반대로 "이거 왜 굳이 `var`/`mutableListOf`예요?"가 된다.

## 4. Named & Default Arguments

Java에는 기본값 파라미터가 없어서, 선택적 파라미터를 표현하려면 오버로딩을
계단식으로 쌓거나 Builder 패턴을 쓴다.

```java
String createUser(String name) { return createUser(name, 20, true); }
String createUser(String name, int age) { return createUser(name, age, true); }
String createUser(String name, int age, boolean isActive) { ... }
```

Kotlin은 파라미터 하나에 기본값을 주고, 호출부에서 필요한 인자만 이름으로
지정한다 — 오버로딩도, 빌더도 필요 없다.

```kotlin
fun createUser(name: String, age: Int = 20, isActive: Boolean = true) = ...

createUser("Gunhaa")
createUser("Gunhaa", isActive = false)  // age는 기본값 그대로, isActive만 지정
```

## 5. 확장 함수 vs 정적 유틸리티 클래스

Java는 이미 있는 클래스(`String` 등)에 메서드를 추가할 수 없어서, 정적
유틸 클래스에 대상 객체를 인자로 넘기는 형태가 된다.

```java
class StringUtils {
    static String truncate(String s, int maxLength) { ... }
}
StringUtils.truncate(str, 10);   // str이 어색하게 "인자"로 들어감
```

Kotlin의 확장 함수는 마치 그 타입에 원래 있던 메서드처럼 호출한다
(컴파일 결과물은 결국 정적 메서드라 런타임 비용 차이는 없다).

```kotlin
fun String.truncate(maxLength: Int): String = ...

str.truncate(10)                 // str이 수신자(receiver)로 자연스럽게 붙음
```

## 6. when 식(expression) + 스마트 캐스트

Java 16+는 패턴 매칭 `instanceof`로 캐스팅 보일러플레이트를 줄였고,
Java 21의 switch 패턴 매칭은 식으로도 쓸 수 있어 아래 Kotlin 코드와
꽤 비슷해졌다.

```java
// Java 21
String describe(Object value) {
    return switch (value) {
        case String s -> "문자열, 길이=" + s.length();
        case Integer i -> "정수, 값=" + i;
        default -> "알 수 없는 타입";
    };
}
```

```kotlin
fun describe(value: Any): String = when (value) {
    is String -> "문자열, 길이=${value.length}"  // 스마트 캐스트: 캐스팅 없이 바로 사용
    is Int -> "정수, 값=$value"
    else -> "알 수 없는 타입"
}
```

**핵심 차이**: 문법은 수렴했지만, Kotlin은 `when`이 처음부터(2016년) 식이었고
스마트 캐스트도 `is` 검사 하나로 끝난다. Java는 Java 21(2023년)부터야
비슷한 표현력을 갖췄고, 여전히 `instanceof`만 쓰는 구코드가 훨씬 많다.

## 7. Sealed class/interface

```java
// Java 17+ sealed + Java 21 switch 패턴 매칭
sealed interface PaymentResult permits Success, Failure {}
record Success(String transactionId) implements PaymentResult {}
record Failure(String reason) implements PaymentResult {}

String handle(PaymentResult r) {
    return switch (r) {
        case Success s -> "성공: " + s.transactionId();
        case Failure f -> "실패: " + f.reason();
    }; // default 없이도 컴파일러가 exhaustiveness 검사
}
```

```kotlin
sealed interface PaymentResult
data class Success(val transactionId: String) : PaymentResult
data class Failure(val reason: String) : PaymentResult

fun handle(result: PaymentResult): String = when (result) {
    is Success -> "성공: ${result.transactionId}"
    is Failure -> "실패: ${result.reason}"
    // else 없어도 컴파일러가 모든 하위 타입을 처리했는지 검사해준다
}
```

**핵심 차이**: 기능은 거의 동등해졌지만 Kotlin은 이 패턴을 언어 초기부터
표준 관용구로 써왔다 (enum 대신 sealed class로 "닫힌 타입 집합 + 각 케이스가
다른 데이터를 가짐"을 표현하는 것이 매우 흔하다).

## 8. Checked Exception이 없음

```java
public String readConfig(String path) throws IOException { ... }
// 호출부는 반드시 catch 하거나 throws를 전파해야 한다 (컴파일러가 강제)
```

```kotlin
fun readConfig(path: String): String { ... }
// 모든 예외가 unchecked. throws 선언도, 강제 catch도 없다.
// (Kotlin에서 Java의 checked exception 메서드를 호출할 때도 try-catch가 강제되지 않는다.)
```

**핵심 차이**: Java의 checked exception은 "실패할 수 있음을 시그니처에
남긴다"는 장점이 있지만 실무에서는 무의미한 `throws Exception` 전파나
빈 `catch` 블록으로 이어지는 경우가 많다. Kotlin은 아예 이 메커니즘을
없애고, 실패 가능성을 표현하고 싶으면 `Result<T>`나 sealed class(위 7번
참고)를 명시적으로 쓰도록 유도한다.

## 그 밖에 자주 마주치는 차이 (요약)

| 항목 | Java | Kotlin |
|------|------|--------|
| 파일 구성 | 반드시 클래스 안에 코드를 넣어야 함 | 최상위 함수/프로퍼티를 파일에 바로 작성 가능 |
| 기본 접근 제어자 | package-private | public |
| 문자열 조합 | `"a=" + a + ", b=" + b` / `String.format(...)` | `"a=$a, b=$b"` (문자열 템플릿) |
| 정적 멤버 | `static` 키워드 | `companion object { }` |
| 세미콜론 | 필수 | 선택 (관용적으로 생략) |
| 비동기/동시성 | `Thread`/`ExecutorService`/`CompletableFuture` | 코루틴 — 별도 문서 [../coroutine/basics.md](../coroutine/basics.md) 참고 |
