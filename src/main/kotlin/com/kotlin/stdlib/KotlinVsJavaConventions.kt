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
    scopeFunctionsExample()
    rangeAndLoopExample()
    objectAndCompanionExample()
    destructuringExample()
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

// ── 9. 스코프 함수 (let, apply, also, run, with) ─────────────────
//
// Java에는 직접적인 대응이 없다. null 체크 후 처리는 if 블록으로,
// 객체 설정은 세터를 여러 줄 호출하거나 Builder 패턴으로 표현한다.
//
// Java:
//   User user = findUserById("u1");
//   String upper;
//   if (user != null) {
//       upper = user.getName().toUpperCase();
//   } else {
//       upper = null;
//   }
//
//   StringBuilder sb = new StringBuilder();
//   sb.append("a");
//   sb.append("b");
//   String built = sb.toString(); // 세터 체이닝을 위해 변수를 계속 다시 참조해야 함
//
// Kotlin: let은 널 체크 + 결과 변환에, apply는 객체 설정(빌더 패턴)에 쓴다.
// let/also는 인자를 it으로 받고, run/apply/with는 수신자를 this로 받는다.
// let/run/with는 람다의 마지막 식을 반환하고, apply/also는 객체 자신을 반환한다.
fun scopeFunctionsExample() {
    println("\n== 9. 스코프 함수 (let, apply, also, run, with) ==")

    val upper = findUserById("u1")?.let { it.name.uppercase() } // null이면 upper도 null
    println("let: $upper")

    val built = StringBuilder().apply {
        append("a")
        append("b")
    }.toString() // apply는 StringBuilder 자신을 반환하므로 바로 체이닝 가능
    println("apply: $built")

    val logged = fetchCount().also { println("also: 조회된 값 = $it") } // also는 부가 효과만, 값은 그대로 통과
    println("also 통과값: $logged")
}

fun fetchCount(): Int = 42

// ── 10. 범위(Range)와 for 반복문 ─────────────────────────────────
//
// Java: 인덱스 변수를 직접 초기화·조건·증감식으로 관리해야 한다.
//
// Java:
//   for (int i = 1; i <= 5; i++) System.out.print(i);       // 12345
//   for (int i = 5; i >= 1; i--) System.out.print(i);       // 54321
//   for (int i = 0; i <= 8; i += 2) System.out.print(i);    // 02468
//
// Kotlin: 범위(1..5)와 진행(step, downTo)으로 의도를 그대로 코드로 표현한다.
fun rangeAndLoopExample() {
    println("\n== 10. 범위(Range)와 for 반복문 ==")
    for (i in 1..5) print(i)        // 12345
    println()
    for (i in 5 downTo 1) print(i)  // 54321
    println()
    for (i in 0..8 step 2) print(i) // 02468
    println()
}

// ── 11. object 선언 / companion object ───────────────────────────
//
// Java의 싱글턴은 private 생성자 + static 필드/메서드로 직접 구현해야 한다.
//
// Java:
//   public final class DataProviderManager {
//       private static final DataProviderManager INSTANCE = new DataProviderManager();
//       private final List<String> providers = new ArrayList<>();
//       private DataProviderManager() {}
//       public static DataProviderManager getInstance() { return INSTANCE; }
//       public void register(String provider) { providers.add(provider); }
//   }
//   DataProviderManager.getInstance().register("x");
//
//   class User {
//       private final String name;
//       private User(String name) { this.name = name; }
//       static User create(String name) { return new User(name); } // static 팩토리 메서드
//   }
//   User.create("John");
//
// Kotlin: object 선언 하나로 스레드 안전한 싱글턴이 만들어진다 (첫 접근 시 지연 초기화).
// companion object는 클래스 안에 정의되어 Java의 static 팩토리 메서드 자리를 대신한다
// (겉보기엔 static 같지만 실제로는 companion object라는 객체의 인스턴스 멤버다).
object DataProviderManager {
    private val providers = mutableListOf<String>()
    fun register(provider: String) = providers.add(provider)
    fun all(): List<String> = providers
}

class Account private constructor(val name: String) {
    companion object {
        fun create(name: String) = Account(name)
    }
}

fun objectAndCompanionExample() {
    println("\n== 11. object 선언 / companion object ==")
    DataProviderManager.register("provider-a")
    println("object 싱글턴: ${DataProviderManager.all()}")

    val account = Account.create("Gunhaa") // Account.create(...) — Java의 static 팩토리 메서드 호출과 겉모습이 같다
    println("companion object 팩토리: ${account.name}")
}

// ── 12. 구조 분해 선언 (destructuring) ───────────────────────────
//
// Java: Map.Entry에서 key/value를 각각 꺼내려면 entry.getKey()/getValue()를 호출해야 한다.
//
// Java:
//   for (Map.Entry<String, Integer> entry : scores.entrySet()) {
//       String name = entry.getKey();
//       Integer score = entry.getValue();
//       System.out.println(name + "=" + score);
//   }
//
// Kotlin: data class나 Map.Entry처럼 componentN() 함수를 제공하는 타입은
// val (a, b) = obj 형태로 한 번에 여러 변수로 풀어낼 수 있다.
fun destructuringExample() {
    println("\n== 12. 구조 분해 선언 ==")
    val scores = mapOf("Gunhaa" to 90, "Kim" to 85)
    for ((name, score) in scores) {
        println("$name=$score")
    }

    val (id, name, _) = User("u2", "Kim", "kim@example.com") // data class의 componentN() 활용, email은 _로 건너뜀
    println("destructuring: id=$id, name=$name")
}
