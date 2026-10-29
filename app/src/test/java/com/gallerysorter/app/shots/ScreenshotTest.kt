package com.gallerysorter.app.shots

import android.Manifest
import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Insets
import android.os.Looper
import android.view.WindowInsets
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.down
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.moveBy
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.up
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ApplicationProvider
import com.gallerysorter.app.MainActivity
import com.gallerysorter.app.MainViewModel
import org.junit.After
import org.junit.Assume
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.runners.MethodSorters
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.rules.TestName
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.Duration

/**
 * Renders the real app (MainActivity + MainViewModel + MediaRepo) on the JVM with Robolectric against a
 * fake MediaStore provider that serves generated demo landscapes. Run on demand:
 * `./gradlew :app:testDebugUnitTest -Pscreenshots --tests "*ScreenshotTest*"`; PNGs land in app/build/screenshots.
 * Tests whose name starts with "firstRun" start without photo permission.
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h800dp-port-xxhdpi")
class ScreenshotTest {

    private val testName = TestName()

    private val setup = object : ExternalResource() {
        override fun before() {
            Assume.assumeTrue("run with -Pscreenshots", System.getProperty("screenshots") != null)
            val app = ApplicationProvider.getApplicationContext<Application>()
            if (!testName.methodName.contains("firstRun")) {
                shadowOf(app).grantPermissions(Manifest.permission.READ_MEDIA_IMAGES)
            }
            app.getSharedPreferences("session", 0).edit().clear().commit()
            Robolectric.setupContentProvider(FakeMedia::class.java, "media")
            // hardware bitmaps cannot be drawn into the software canvas used for the screenshots
            coil.Coil.setImageLoader(coil.ImageLoader.Builder(app).allowHardware(false).build())
        }
    }
    private val compose = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val chain: RuleChain = RuleChain.outerRule(testName).around(setup).around(compose)

    private val outDir = File("build/screenshots").also { it.mkdirs() }
    private val framesDir = File("build/frames").also { it.mkdirs() }

    @Before
    fun manualClock() {
        compose.mainClock.autoAdvance = false
        compose.runOnUiThread {
            val insets = WindowInsets.Builder()
                .setInsets(WindowInsets.Type.systemBars(), Insets.of(0, 78, 0, 66))
                .build()
            compose.activity.window.decorView.dispatchApplyWindowInsets(insets)
        }
    }

    @After
    fun tearDown() {
        // end the activity so its ViewModel (and any delayed save job) is cleared before the next test
        compose.runOnUiThread { compose.activity.finish() }
        advance(1000)
        Thread.sleep(600) // let any queued SharedPreferences write land before clearing
        ApplicationProvider.getApplicationContext<Application>()
            .getSharedPreferences("session", 0).edit().clear().commit()
    }

    private val vm: MainViewModel get() = ViewModelProvider(compose.activity)[MainViewModel::class.java]

    private fun advance(ms: Long) {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))
        compose.mainClock.advanceTimeBy(ms)
    }

    private fun settle(rounds: Int = 40) {
        repeat(rounds) {
            Thread.sleep(60)
            advance(60)
        }
    }

    private fun draw(): Bitmap {
        val view = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        compose.runOnUiThread { view.draw(Canvas(bitmap)) }
        return bitmap
    }

    private fun save(bitmap: Bitmap, file: File) =
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }

    /** Wait for photos to load, let the image cross-fades finish, then capture. */
    private fun shot(name: String) {
        settle(60)
        draw() // first frame starts the cross-fade clocks
        advance(600)
        draw()
        advance(100)
        save(draw(), File(outDir, "$name.png"))
    }

    private fun decideMany(keepCount: Int, deleteCount: Int) {
        var keeps = 0
        var deletes = 0
        var i = 0
        while (keeps < keepCount || deletes < deleteCount) {
            val id = vm.ui.currentId ?: break
            val mark = deletes < deleteCount && (i % 5 == 2 || keeps >= keepCount)
            vm.decide(id, !mark)
            if (mark) deletes++ else keeps++
            i++
        }
        settle(10)
    }

    private fun drag(dx: Float) {
        compose.onRoot().performTouchInput { down(center) }
        repeat(6) {
            compose.onRoot().performTouchInput { moveBy(Offset(dx / 6f, 0f)) }
            advance(32)
        }
        repeat(4) { advance(32) }
    }

    // ---- shots ----------------------------------------------------------------------------

    @Test
    fun t01_home() = shot("01-home")

    @Test
    fun t02_swipeLeft() {
        vm.startSession(1L, "Camera")
        settle(20)
        drag(-250f)
        shot("02-swipe-left")
    }

    @Test
    fun t03_swipeRight() {
        vm.startSession(1L, "Camera")
        settle(20)
        drag(250f)
        shot("03-swipe-right")
    }

    @Test
    fun t04_progress() {
        vm.startSession(4L, "Downloads")
        settle(10)
        decideMany(84, 28)
        shot("04-progress")
    }

    @Test
    fun t05_review() {
        vm.startSession(4L, "Downloads")
        settle(10)
        decideMany(84, 28)
        vm.back()
        shot("05-review")
    }

    @Test
    fun t06_marked() {
        vm.startSession(4L, "Downloads")
        settle(10)
        decideMany(84, 28)
        vm.back()
        vm.openMarked()
        shot("06-marked")
    }

    @Test
    fun t07_recentlyDeleted() {
        vm.openTrash()
        settle(10)
        vm.toggleSelected(FakeMedia.TRASH_BASE)
        vm.toggleSelected(FakeMedia.TRASH_BASE + 2)
        vm.toggleSelected(FakeMedia.TRASH_BASE + 5)
        shot("07-recently-deleted")
    }

    @Test
    fun t08_firstRunWelcome() = shot("00-welcome")

    @Test
    fun t09_firstRunPermission() {
        compose.onNodeWithText("Get Started").performClick()
        shot("09-permission")
    }

    // ---- frames for the demo animation -------------------------------------------------------

    private fun frame(n: Int) {
        advance(33)
        save(draw(), File(framesDir, "f%04d.png".format(n)))
    }

    @Test
    fun t10_demoFrames() {
        vm.startSession(1L, "Camera")
        settle(30)
        draw(); advance(700); draw()
        var n = 0
        repeat(8) { frame(n++) }
        for (dx in listOf(340f, -340f, 340f, 340f, -340f)) {
            compose.onRoot().performTouchInput { down(center) }
            val steps = 14
            repeat(steps) {
                compose.onRoot().performTouchInput { moveBy(Offset(dx / steps, 0f)) }
                frame(n++)
            }
            repeat(4) { frame(n++) }
            compose.onRoot().performTouchInput { up() }
            repeat(14) { frame(n++) }
            repeat(10) { advance(33); draw() }
            repeat(6) { frame(n++) }
        }
    }
}
