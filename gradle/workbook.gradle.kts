// 워크북 지원 태스크.
//
//   ./gradlew grade                    answer 작업 공간을 채점
//   ./gradlew grade -Plevel=1          레벨 하나만 채점
//   ./gradlew resetAnswer              skeleton 원본을 answer로 다시 복사(기존 파일은 건너뜀)
//   ./gradlew resetAnswer -Plevel=2 -Pforce
//                                      레벨 2만, 기존 답안을 덮어쓰며 복사(백업을 남긴다)
//
// 구조
//   src/main/kotlin/com/kotlin/workbook/skeleton/   원본. 손대지 않는다.
//   src/main/kotlin/com/kotlin/workbook/answer/     여기서 푼다. 채점 대상.
//
// 채점 근거는 두 가지다.
//   1) TEST   — JUnit XML(build/test-results/test/*.xml)에서 "요구사항 ID로 시작하는" 테스트를
//               찾아 존재하고 통과했는지 본다. answer 패키지의 테스트만 센다.
//   2) SOURCE / SOURCE_ABSENT / SOURCE_BOTH
//               — 지정한 소스 파일이 특정 정규식을 포함하는지(또는 포함하지 않는지) 본다.
//                 "그 문법을 실제로 써서 풀었는가"를 강제하는 용도다.
//
// 루브릭 원본: docs/workbook/rubric.tsv
//
// 주의: Gradle Kotlin DSL 스크립트 안에서는 클래스를 선언하지 않는다(스크립트 컴파일러가
// 람다 안의 로컬 클래스를 코드 생성하지 못한다). 그래서 아래는 List/Pair만 쓴다.

import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

val answerPackagePrefix = "com.kotlin.workbook.answer."
val skeletonPackage = "com.kotlin.workbook.skeleton"
val answerPackage = "com.kotlin.workbook.answer"

// `grade`가 태스크 목록에 있으면 테스트가 깨져도 채점까지 진행한다.
val gradingRun = gradle.startParameter.taskNames.any { it.substringAfterLast(':') == "grade" }
if (gradingRun) {
    tasks.named<Test>("test") { ignoreFailures = true }
}

tasks.register("resetAnswer") {
    group = "workbook"
    description = "skeleton 원본을 answer 작업 공간으로 복사한다 (-Plevel=N, -Pforce)."

    val rootDirFile = layout.projectDirectory.asFile
    val backupDir = layout.buildDirectory.dir("workbook-backup").get().asFile
    val onlyLevel = providers.gradleProperty("level").orNull
    val force = providers.gradleProperty("force").isPresent

    doLast {
        val levels = if (onlyLevel == null) listOf("level1", "level2", "level3") else listOf("level$onlyLevel")
        val roots = listOf("src/main/kotlin/com/kotlin/workbook", "src/test/kotlin/com/kotlin/workbook")
        val stamp = java.time.LocalDateTime.now().toString().replace(":", "-").substringBefore(".")

        val copied = mutableListOf<String>()
        val skipped = mutableListOf<String>()

        for (root in roots) {
            for (level in levels) {
                val fromDir = File(rootDirFile, "$root/skeleton/$level")
                val toDir = File(rootDirFile, "$root/answer/$level")
                if (!fromDir.exists()) continue
                fromDir.listFiles { f: File -> f.extension == "kt" }?.sortedBy { it.name }?.forEach { source ->
                    val target = File(toDir, source.name)
                    val relative = target.relativeTo(rootDirFile).path
                    if (target.exists() && !force) {
                        skipped += relative
                    } else {
                        if (target.exists()) {
                            val backup = File(backupDir, "$stamp/$relative")
                            backup.parentFile.mkdirs()
                            backup.writeText(target.readText())
                        }
                        target.parentFile.mkdirs()
                        target.writeText(source.readText().replace(skeletonPackage, answerPackage))
                        copied += relative
                    }
                }
            }
        }

        println()
        println("복사됨 ${copied.size}개" + if (copied.isEmpty()) "" else ":")
        copied.forEach { println("  + $it") }
        if (skipped.isNotEmpty()) {
            println()
            println("건너뜀 ${skipped.size}개 (이미 파일이 있다. 덮어쓰려면 -Pforce):")
            skipped.forEach { println("  - $it") }
        }
        if (force && copied.isNotEmpty()) {
            println()
            println("덮어쓰기 전 원본 백업: ${File(backupDir, stamp).relativeTo(rootDirFile)}")
        }
    }
}

tasks.register("grade") {
    group = "workbook"
    description = "docs/workbook/rubric.tsv 기준으로 answer 작업 공간을 채점한다."
    dependsOn("test")

    val rubricFile = layout.projectDirectory.file("docs/workbook/rubric.tsv").asFile
    val resultsDir = layout.buildDirectory.dir("test-results/test").get().asFile
    val reportFile = layout.buildDirectory.file("reports/workbook/grade.txt").get().asFile
    val rootDirFile = layout.projectDirectory.asFile
    val onlyLevel = providers.gradleProperty("level").orNull

    doLast {
        require(rubricFile.exists()) { "루브릭 파일이 없다: $rubricFile" }

        // --- 루브릭: [id, level, points, kind, target, description] -------------
        val items: List<List<String>> = rubricFile.readLines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .map { line ->
                val cols = line.split("\t").map(String::trim)
                require(cols.size >= 6) { "루브릭 행의 열이 부족하다(6열 필요): $line" }
                cols
            }
            .filter { onlyLevel == null || it[1] == onlyLevel }

        // --- JUnit 결과: [테스트 이름 to 통과 여부] (answer 패키지만) -----------
        val cases = mutableListOf<Pair<String, Boolean>>()
        resultsDir.listFiles { f: File -> f.name.startsWith("TEST-") && f.extension == "xml" }
            ?.sortedBy { it.name }
            ?.forEach { xml ->
                val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(xml)
                val nodes = doc.getElementsByTagName("testcase")
                for (i in 0 until nodes.length) {
                    val e = nodes.item(i) as Element
                    if (!e.getAttribute("classname").startsWith(answerPackagePrefix)) continue
                    val broken = e.getElementsByTagName("failure").length > 0 ||
                        e.getElementsByTagName("error").length > 0
                    val skipped = e.getElementsByTagName("skipped").length > 0
                    cases += e.getAttribute("name") to (!broken && !skipped)
                }
            }

        // --- 판정 ---------------------------------------------------------------
        val verdicts = mutableListOf<Pair<Boolean, String>>()
        for (item in items) {
            val (id, _, _, kind, target) = item
            when (kind) {
                "TEST" -> {
                    val matched = cases.filter { it.first.trimStart().startsWith(id) }
                    verdicts += when {
                        matched.isEmpty() ->
                            false to "테스트 없음 (테스트 이름이 \"$id\"로 시작해야 한다)"
                        matched.all { it.second } ->
                            true to "테스트 ${matched.size}개 통과"
                        else ->
                            false to ("실패: " + matched.filterNot { it.second }.joinToString { it.first })
                    }
                }

                "SOURCE" -> {
                    val path = target.substringBefore("::")
                    val pattern = target.substringAfter("::", ".")
                    val f = File(rootDirFile, path)
                    verdicts += when {
                        !f.exists() -> false to "파일 없음: $path"
                        !Regex(pattern, RegexOption.MULTILINE).containsMatchIn(f.readText()) ->
                            false to "패턴 미발견: /$pattern/ in $path"
                        else -> true to "패턴 확인"
                    }
                }

                "SOURCE_BOTH" -> {
                    // path::반드시_포함할_패턴::절대_없어야_할_패턴
                    val parts = target.split("::")
                    val f = File(rootDirFile, parts[0])
                    val required = Regex(parts[1], RegexOption.MULTILINE)
                    val forbidden = Regex(parts[2], RegexOption.MULTILINE)
                    val text = if (f.exists()) f.readText() else ""
                    verdicts += when {
                        !f.exists() -> false to "파일 없음: ${parts[0]}"
                        !required.containsMatchIn(text) -> false to "필수 패턴 미발견: /${parts[1]}/"
                        forbidden.containsMatchIn(text) -> false to "금지 패턴 발견: /${parts[2]}/"
                        else -> true to "패턴 확인"
                    }
                }

                "SOURCE_ABSENT" -> {
                    val path = target.substringBefore("::")
                    val pattern = target.substringAfter("::", ".")
                    val f = File(rootDirFile, path)
                    verdicts += when {
                        !f.exists() -> false to "파일 없음: $path"
                        Regex(pattern, RegexOption.MULTILINE).containsMatchIn(f.readText()) ->
                            false to "금지 패턴 발견: /$pattern/ in $path"
                        else -> true to "금지 패턴 없음"
                    }
                }

                else -> verdicts += false to "알 수 없는 채점 종류: $kind"
            }
        }

        // --- 리포트 -------------------------------------------------------------
        val sb = StringBuilder()
        sb.appendLine()
        sb.appendLine("=".repeat(80))
        sb.appendLine("  Kotlin 워크북 채점 결과 (대상: com.kotlin.workbook.answer)")
        sb.appendLine("=".repeat(80))

        for (level in items.map { it[1] }.distinct().sorted()) {
            val rows = items.indices.filter { items[it][1] == level }
            val earned = rows.filter { verdicts[it].first }.sumOf { items[it][2].toInt() }
            val total = rows.sumOf { items[it][2].toInt() }
            sb.appendLine()
            sb.appendLine("[ Level $level ]  $earned / $total 점")
            sb.appendLine("-".repeat(80))
            for (i in rows) {
                val mark = if (verdicts[i].first) "PASS" else "FAIL"
                sb.appendLine("  $mark  ${items[i][0]}  (${items[i][2]}점)  ${items[i][5]}")
                if (!verdicts[i].first) sb.appendLine("        -> ${verdicts[i].second}")
            }
        }

        val earned = items.indices.filter { verdicts[it].first }.sumOf { items[it][2].toInt() }
        val total = items.sumOf { it[2].toInt() }
        val percent = if (total == 0) 0 else earned * 100 / total
        sb.appendLine()
        sb.appendLine("=".repeat(80))
        sb.appendLine("  총점: $earned / $total  ($percent%)")
        sb.appendLine("=".repeat(80))

        reportFile.parentFile.mkdirs()
        reportFile.writeText(sb.toString())
        println(sb.toString())
        println("리포트 저장: ${reportFile.relativeTo(rootDirFile)}")
    }
}
