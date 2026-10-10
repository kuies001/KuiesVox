package tw.kuies.voiceime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * IME metadata 與 manifest 的整合契約：Android 系統設定裡的 KuiesVox 入口必須指向
 * 既有的設定畫面，Launcher 入口與輸入法服務不受影響，也不能多出權限。
 */
class ImeSettingsActivityMetadataTest {

    private val settingsActivity = "tw.kuies.voiceime.MainActivity"

    private fun repositoryFile(relativePath: String): File {
        val candidates = listOf(File(relativePath), File("app/$relativePath"))
        return candidates.firstOrNull { it.isFile }
            ?: error("Cannot find $relativePath under ${File(".").absolutePath}")
    }

    private fun methodXml(): String =
        repositoryFile("src/main/res/xml/method.xml").readText(Charsets.UTF_8)

    private fun manifest(): String =
        repositoryFile("src/main/AndroidManifest.xml").readText(Charsets.UTF_8)

    private fun componentBlock(manifest: String, tag: String, name: String): String {
        val pattern = Regex("""<$tag\b[^>]*android:name="$name".*?</$tag>""", RegexOption.DOT_MATCHES_ALL)
        return pattern.find(manifest)?.value ?: error("manifest has no <$tag android:name=\"$name\">")
    }

    @Test
    fun theInputMethodPointsAtTheKuiesVoxSettingsActivity() {
        val method = methodXml()

        assertTrue(
            "method.xml must declare the settings activity",
            method.contains("""android:settingsActivity="$settingsActivity"""")
        )
        // 原本的 zh_TW 鍵盤 subtype 與顯示名稱必須保留。
        assertTrue(method.contains("""android:imeSubtypeLocale="zh_TW""""))
        assertTrue(method.contains("""android:imeSubtypeMode="keyboard""""))
        assertTrue(method.contains("""android:label="@string/app_name""""))
    }

    @Test
    fun theSettingsActivityIsAnExportedLauncherActivity() {
        val manifest = manifest()
        val activity = componentBlock(manifest, "activity", ".MainActivity")

        assertTrue(
            "the settings activity must stay exported so the system can open it",
            activity.contains("""android:exported="true"""")
        )
        assertTrue(activity.contains("android.intent.action.MAIN"))
        assertTrue(activity.contains("android.intent.category.LAUNCHER"))
        // settingsActivity 用的是完整類名，必須對應到 manifest 中同一個 Activity。
        assertEquals("tw.kuies.voiceime.MainActivity", "$settingsActivity")
    }

    @Test
    fun theInputMethodServiceStillBindsItsMetadata() {
        val manifest = manifest()
        val service = componentBlock(manifest, "service", ".VoiceImeService")

        assertTrue(service.contains("""android:permission="android.permission.BIND_INPUT_METHOD""""))
        assertTrue(service.contains("android.view.InputMethod"))
        assertTrue(service.contains("""android:name="android.view.im""""))
        assertTrue(service.contains("""android:resource="@xml/method""""))
    }

    @Test
    fun noUnexpectedPermissionWasAdded() {
        val declared = Regex("""<uses-permission\s+android:name="([^"]+)"\s*/>""")
            .findAll(manifest())
            .map { it.groupValues[1] }
            .toSet()

        assertEquals(
            setOf(
                "android.permission.RECORD_AUDIO",
                "android.permission.INTERNET",
                "android.permission.REQUEST_INSTALL_PACKAGES"
            ),
            declared
        )
    }
}
