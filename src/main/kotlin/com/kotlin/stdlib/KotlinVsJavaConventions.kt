package com.kotlin.stdlib

/**
 * Kotlin과 Java의 핵심적인 컨벤션 차이를 실행 가능한 코드로 정리한 예제.
 * 각 함수는 Kotlin 방식으로 작성하고, 바로 위 주석에 "Java였다면 이렇게 썼을 것"을 비교했다.
 * 문서 버전: docs/kotlin-vs-java-conventions.md
 */
fun main() {
    nullSafetyExample()
    dataClassExample()
    immutabilityExample()
    namedAndDefaultArgsExample()
    extensionFunctionExample()
    whenExpressionExample()
    sealedClassExample()
    checkedExceptionExample()
}

// ── 1. Null 안전성 ───────────────────────────────────────────────
//
// Java: String nickname = findNicknameById("u1"); // null일 수도 있다는 걸 코드만 봐선 모른다
//       int length = (nickname != null) ? nickname.length() : 0;
//       User user = findUserById("u1");
//       System.out.println(user.getName()); // NPE 가능성이 타입에 드러나지 않는다
//
// Kotlin: 타입 자체에 "null이 될 수 있는지"가 포함된다 (String vs String?).
//         컴파일러가 null 처리를 하지 않으면 컴파일을 막아준다.
fun nullSafetyExample() {
    println("== 1. Null 안전성 ==")
    val nickname: String? = findNicknameById("u1")
    val length = nickname?.length ?: 0 // null이면 0, 아니면 length
    println("length=$length")

    val user: User? = findUserById("u1")
    println(user!!.name) // "여기선 null이 아님을 내가 보장한다"는 명시적 선언 (틀리면 NPE)
}

fun findNicknameById(id: String): String? = if (id == "u1") null else "nick-$id"
fun findUserById(id: String): User? = User(id, "user-$id", "$id@example.com")

// ── 2. 데이터 클래스 vs POJO/record ─────────────────────────────
//
// Java (record 이전): 필드 + 생성자 + getter + equals/hashCode/toString을
//                     전부 손으로 작성하거나 Lombok @Data에 의존해야 했다.
// Java 16+ record: record User(String id, String name, String email) {}
//                  Kotlin data class와 상당히 비슷해졌다 (불변, equals/hashCode/toString 자동).
// 그래도 Kotlin data class가 더 주는 것: copy(), 구조 분해 선언(destructuring), var 프로퍼티도 허용.
data class User(val id: String, val name: String, val email: String)

fun dataClassExample() {
    println("\n== 2. 데이터 클래스 ==")
    val user = User("u1", "Gunhaa", "gunhaa@example.com")
    val renamed = user.copy(name = "Gun") // record엔 없는 기능 — 일부 필드만 바꾼 복사본
    val (id, name, _) = user // 구조 분해 선언
    println("$user -> $renamed / 구조분해: id=$id, name=$name")
}

// ── 3. 불변성이 기본값 ───────────────────────────────────────────
//
// Java: List<String> list = new ArrayList<>(); // 기본이 가변, 불변으로 하려면 List.of(...) 명시
//       final String name = "a"; // 불변으로 하려면 매번 final을 붙여야 한다
//
// Kotlin: val(불변)이 기본 습관, var(가변)은 필요할 때만 명시. listOf()가 기본, 가변이 필요하면 mutableListOf().
fun immutabilityExample() {
    println("\n== 3. 불변성 기본값 ==")
    val readonly = listOf("a", "b", "c") // Java: List.of("a", "b", "c") — 수정 시도 시 예외
    val mutable = mutableListOf("a", "b") // Java: new ArrayList<>(List.of("a", "b"))
    mutable.add("c")
    println("readonly=$readonly, mutable=$mutable")
}

// ── 4. Named & Default Arguments ────────────────────────────────
//
// Java: 기본값/오버로딩이 없어서 아래처럼 계단식으로 오버로딩을 만들거나 Builder 패턴을 쓴다.
//   String createUser(String name) { return createUser(name, 20, true); }
//   String createUser(String name, int age) { return createUser(name, age, true); }
//   String createUser(String name, int age, boolean isActive) { ... }
//
// Kotlin: 파라미터 하나에 기본값을 주고, 호출부에서 필요한 것만 이름으로 지정한다.
fun namedAndDefaultArgsExample() {
    println("\n== 4. Named & Default Arguments ==")
    println(createUser("Gunhaa"))
    println(createUser("Gunhaa", isActive = false)) // age는 기본값 사용, isActive만 이름으로 지정
}

fun createUser(name: String, age: Int = 20, isActive: Boolean = true) =
    "User(name=$name, age=$age, isActive=$isActive)"

// ── 5. 확장 함수 vs 정적 유틸리티 클래스 ─────────────────────────
//
// Java: 기존 클래스(String 등)에 메서드를 못 붙이므로 정적 유틸 클래스를 만든다.
//   class StringUtils {
//       static String truncate(String s, int maxLength) { ... }
//   }
//   StringUtils.truncate(str, 10); // str이 어색하게 인자로 들어간다
//
// Kotlin: 기존 타입에 메서드를 붙인 것처럼 호출할 수 있다 (내부적으론 정적 메서드로 컴파일됨).
fun String.truncate(maxLength: Int): String =
    if (length <= maxLength) this else take(maxLength) + "..."

fun extensionFunctionExample() {
    println("\n== 5. 확장 함수 ==")
    val long = "Kotlin coroutines are great"
    println(long.truncate(10)) // StringUtils.truncate(long, 10) 대신 long.truncate(10)
}

// ── 6. when 식(expression) + 스마트 캐스트 ───────────────────────
//
// Java 16+: if (value instanceof String s) { ... } else if (value instanceof Integer i) { ... }
//           패턴 매칭 instanceof 덕분에 캐스팅은 줄었지만, if-else 문(statement)이라
//           변수에 바로 대입하려면 각 분기에서 따로 대입해야 한다.
// Java 21 switch 패턴 매칭: switch (value) { case String s -> ...; case Integer i -> ...; }
//           식(expression)으로도 쓸 수 있게 되어 Kotlin when과 꽤 비슷해졌다.
//
// Kotlin: when은 처음부터 식이라 변수에 바로 대입 가능, is로 검사한 분기 안에서는
//         캐스팅 없이 바로 그 타입처럼 사용할 수 있다 (스마트 캐스트).
fun describe(value: Any): String = when (value) {
    is String -> "문자열, 길이=${value.length}" // value가 여기서 자동으로 String 취급됨
    is Int -> "정수, 값=$value"
    else -> "알 수 없는 타입"
}

fun whenExpressionExample() {
    println("\n== 6. when 식 + 스마트 캐스트 ==")
    println(describe("hello"))
    println(describe(42))
}

// ── 7. Sealed class/interface ───────────────────────────────────
//
// Java 17+ sealed interface + Java 21 switch 패턴 매칭으로 거의 동등하게 가능해졌다.
//   sealed interface PaymentResult permits Success, Failure {}
//   record Success(String transactionId) implements PaymentResult {}
//   record Failure(String reason) implements PaymentResult {}
//   String handle(PaymentResult r) {
//       return switch (r) {
//           case Success s -> "성공: " + s.transactionId();
//           case Failure f -> "실패: " + f.reason();
//       }; // 컴파일러가 모든 케이스를 다뤘는지 검사(exhaustiveness)
//   }
//
// Kotlin은 이 기능을 훨씬 먼저(2016년, Kotlin 1.0)부터 언어에 자연스럽게 갖고 있었다.
sealed interface PaymentResult
data class Success(val transactionId: String) : PaymentResult
data class Failure(val reason: String) : PaymentResult

fun handle(result: PaymentResult): String = when (result) {
    is Success -> "성공: ${result.transactionId}"
    is Failure -> "실패: ${result.reason}"
    // else 분기가 없어도 컴파일된다 — sealed의 하위 타입을 컴파일러가 전부 알고 있기 때문.
}

fun sealedClassExample() {
    println("\n== 7. Sealed class ==")
    println(handle(Success("tx-123")))
    println(handle(Failure("잔액 부족")))
}

// ── 8. Checked Exception 없음 ────────────────────────────────────
//
// Java: public String readConfig(String path) throws IOException { ... }
//       호출부는 반드시 catch 하거나 throws를 전파해야 한다 (컴파일러가 강제).
//
// Kotlin: 모든 예외가 unchecked다. throws 선언도, 강제 catch도 없다.
//         (Kotlin에서 Java의 checked exception 메서드를 호출할 때도 try-catch가 강제되지 않는다.)
fun readConfig(path: String): String =
    java.io.File(path).takeIf { it.exists() }?.readText() ?: "설정 없음: $path"

fun checkedExceptionExample() {
    println("\n== 8. Checked Exception 없음 ==")
    println(readConfig("/no/such/file.conf"))
}
