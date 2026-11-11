// APK size report: what takes the room in the release APK, by part and by library.
//
// Runs after assembleRelease (so in CI on every pull request) and prints a table to the build log,
// and to the run's Summary page when on GitHub Actions. Also writes
// app/build/reports/apk-size.md.
//
// Two views:
//  - Files in the APK (compressed, as downloaded): code (dex), native libraries per CPU type,
//    resources, assets.
//  - Code by library (uncompressed dex bytes), from the Android SDK's apkanalyzer with the R8
//    mapping, so obfuscated names are read back. Skipped if apkanalyzer isn't installed.

import java.io.ByteArrayOutputStream
import java.util.zip.ZipFile
import javax.inject.Inject

abstract class ApkSizeReport @Inject constructor(private val exec: ExecOperations) : DefaultTask() {
    @get:Internal abstract val apkDir: DirectoryProperty
    @get:Internal abstract val mappingFile: RegularFileProperty
    @get:Internal abstract val reportFile: RegularFileProperty
    /** Android SDK folder; empty to look in ANDROID_HOME / ANDROID_SDK_ROOT. */
    @get:Internal abstract val sdkDir: Property<String>

    @TaskAction
    fun report() {
        val apk = apkDir.get().asFile.listFiles { f -> f.extension == "apk" }?.maxByOrNull { it.lastModified() }
        if (apk == null) {
            logger.lifecycle("APK size report: no release APK found")
            return
        }
        val md = StringBuilder()
        md.appendLine("## APK size: ${apk.name}")
        md.appendLine()
        md.appendLine("**Total: ${mb(apk.length())}** (the file as installed from an APK; Play's per-device download is smaller, see native libraries)")
        md.appendLine()
        md.append(filesSection(apk))
        md.append(codeSection(apk))
        val text = md.toString()
        reportFile.get().asFile.apply { parentFile.mkdirs(); writeText(text) }
        logger.lifecycle(text)
        System.getenv("GITHUB_STEP_SUMMARY")?.takeIf { it.isNotBlank() }?.let { java.io.File(it).appendText(text + "\n") }
    }

    private fun filesSection(apk: java.io.File): String {
        val parts = LinkedHashMap<String, Long>()
        val native = HashMap<String, Long>()   // library name -> bytes over all CPU types
        val perAbi = HashMap<String, Long>()   // CPU type -> bytes
        ZipFile(apk).use { zip ->
            zip.entries().asSequence().filter { !it.isDirectory }.forEach { e ->
                val size = e.compressedSize.coerceAtLeast(0)
                val name = e.name
                val part = when {
                    name.endsWith(".dex") -> "Code (dex)"
                    name.startsWith("lib/") -> {
                        val bits = name.split('/')
                        perAbi.merge(bits.getOrElse(1) { "?" }, size, Long::plus)
                        native.merge(bits.last(), size, Long::plus)
                        "Native libraries (all CPU types)"
                    }
                    name == "resources.arsc" -> "Resource table (strings, ids)"
                    name.startsWith("res/") -> "Resources (layouts, images)"
                    name.startsWith("assets/") -> "Assets"
                    name.startsWith("META-INF/") -> "Signature and metadata"
                    else -> "Other"
                }
                parts.merge(part, size, Long::plus)
            }
        }
        val sb = StringBuilder()
        sb.appendLine("### Files in the APK (compressed)")
        sb.appendLine()
        sb.appendLine("| Part | Size | Share |")
        sb.appendLine("|---|---:|---:|")
        val total = parts.values.sum().coerceAtLeast(1)
        parts.entries.sortedByDescending { it.value }.forEach { (k, v) -> sb.appendLine("| $k | ${kb(v)} | ${pct(v, total)} |") }
        sb.appendLine()
        if (native.isNotEmpty()) {
            sb.appendLine("**Native libraries**, all CPU types together (Play sends each phone only its own, roughly a quarter):")
            sb.appendLine()
            sb.appendLine("| Library | Size |")
            sb.appendLine("|---|---:|")
            native.entries.sortedByDescending { it.value }.forEach { (k, v) -> sb.appendLine("| `$k` | ${kb(v)} |") }
            sb.appendLine()
            sb.appendLine("By CPU type: " + perAbi.entries.sortedByDescending { it.value }.joinToString(", ") { "${it.key} ${kb(it.value)}" })
            sb.appendLine()
        }
        return sb.toString()
    }

    private fun codeSection(apk: java.io.File): String {
        val analyzer = findApkAnalyzer() ?: return "_Code by library: skipped (apkanalyzer not found)._\n"
        val args = mutableListOf(analyzer.absolutePath, "dex", "packages", "--defined-only")
        mappingFile.orNull?.asFile?.takeIf { it.exists() }?.let { args += listOf("--proguard-mappings", it.absolutePath) }
        args += apk.absolutePath
        val out = ByteArrayOutputStream()
        val result = exec.exec {
            commandLine(args)
            standardOutput = out
            errorOutput = ByteArrayOutputStream()
            isIgnoreExitValue = true
        }
        if (result.exitValue != 0) return "_Code by library: skipped (apkanalyzer failed)._\n"
        return codeTable(out.toString())
    }

    private fun findApkAnalyzer(): java.io.File? {
        val roots = listOfNotNull(sdkDir.orNull?.takeIf { it.isNotBlank() }, System.getenv("ANDROID_HOME"), System.getenv("ANDROID_SDK_ROOT"))
        for (root in roots) {
            val tools = java.io.File(root, "cmdline-tools")
            val candidates = listOf(java.io.File(tools, "latest/bin/apkanalyzer")) +
                (tools.listFiles()?.map { java.io.File(it, "bin/apkanalyzer") } ?: emptyList()) +
                java.io.File(root, "tools/bin/apkanalyzer")
            candidates.firstOrNull { it.canExecute() }?.let { return it }
        }
        return null
    }

    companion object {
        /** Package prefixes to the library they belong to, most specific first. */
        val GROUPS = listOf(
            "com.example.btcwidget" to "Block's own code",
            "androidx.media3.exoplayer.hls" to "Video: HLS streaming (media3-exoplayer-hls)",
            "androidx.media3.ui" to "Video: player view (media3-ui)",
            "androidx.media3" to "Video: ExoPlayer (media3)",
            "coil3.gif" to "GIFs (coil-gif)",
            "coil3" to "Images (Coil)",
            "com.google.mlkit" to "ML Kit (translation)",
            "com.google.android.gms" to "Google Play services (ML Kit, QR scanner)",
            "com.google.android.datatransport" to "Google Play services (ML Kit, QR scanner)",
            "com.google.firebase" to "Google Play services (ML Kit, QR scanner)",
            "com.google.zxing" to "QR codes (ZXing)",
            "fr.acinq" to "Signatures (secp256k1)",
            "okhttp3" to "Network (OkHttp, Okio)",
            "okio" to "Network (OkHttp, Okio)",
            "androidx.compose.material3" to "Compose: Material 3",
            "androidx.compose.material" to "Compose: Material icons",
            "androidx.compose" to "Compose: UI, foundation, runtime",
            "androidx.navigation3" to "Navigation 3",
            "androidx.work" to "WorkManager",
            "sh.calvin.reorderable" to "Reorderable lists",
            "kotlinx" to "Kotlin libraries (coroutines, serialization)",
            "kotlin" to "Kotlin standard library",
            "androidx" to "AndroidX (other)",
            "com.google" to "Google (other)",
        )

        /**
         * Sums apkanalyzer's `dex packages` output into [GROUPS] by class. Rows look like
         * "C d <defined methods> <referenced methods> <bytes> <class name>". Package rows ("P")
         * include their sub-packages, so only class rows (leaves) are added up.
         */
        fun codeTable(output: String): String {
            val sizes = HashMap<String, Long>()
            output.lineSequence().map { it.trim().split(Regex("\\s+")) }
                .filter { it.size >= 6 && it[0] == "C" && it[1] == "d" }
                .forEach { p ->
                    val bytes = p[4].toLongOrNull() ?: return@forEach
                    val name = p[5]
                    val group = GROUPS.firstOrNull { (prefix, _) -> name.startsWith("$prefix.") }?.second
                        ?: "Other (${name.substringBefore('.')})"
                    sizes.merge(group, bytes, Long::plus)
                }
            if (sizes.isEmpty()) return "_Code by library: apkanalyzer gave no packages._\n"
            val total = sizes.values.sum().coerceAtLeast(1)
            val sb = StringBuilder()
            sb.appendLine("### Code by library (dex bytes, before compression)")
            sb.appendLine()
            sb.appendLine("| Library | Size | Share |")
            sb.appendLine("|---|---:|---:|")
            sizes.entries.sortedByDescending { it.value }.forEach { (k, v) -> sb.appendLine("| $k | ${kb(v)} | ${pct(v, total)} |") }
            sb.appendLine()
            return sb.toString()
        }

        fun kb(bytes: Long) = if (bytes >= 1024 * 1024) "%.2f MB".format(bytes / 1048576.0) else "%,d KB".format((bytes + 512) / 1024)
        fun mb(bytes: Long) = "%.2f MB".format(bytes / 1048576.0)
        fun pct(part: Long, total: Long) = "%.1f%%".format(part * 100.0 / total)
    }
}

val apkSizeReport = tasks.register<ApkSizeReport>("apkSizeReport") {
    group = "reporting"
    description = "Prints what takes the room in the release APK."
    apkDir.set(layout.buildDirectory.dir("outputs/apk/release"))
    mappingFile.set(layout.buildDirectory.file("outputs/mapping/release/mapping.txt"))
    reportFile.set(layout.buildDirectory.file("reports/apk-size.md"))
    // Android Studio writes the SDK folder to local.properties; CI sets ANDROID_HOME instead.
    sdkDir.convention(providers.fileContents(rootProject.layout.projectDirectory.file("local.properties")).asText.map { text ->
        text.lineSequence().firstOrNull { it.trim().startsWith("sdk.dir=") }?.substringAfter('=')?.trim()?.replace("\\:", ":")?.replace("\\\\", "\\").orEmpty()
    }.orElse(""))
}

tasks.matching { it.name == "assembleRelease" }.configureEach { finalizedBy(apkSizeReport) }
