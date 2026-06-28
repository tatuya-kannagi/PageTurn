package net.kannagi.pageturn

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

class PageTurnAccessibilityService : AccessibilityService() {

    private var currentPackage: String = ""
    private val prefs by lazy { PrefsManager(this) }

    // ボタンを押し続けている間の連続ページ送り制御
    private val repeatHandler = Handler(Looper.getMainLooper())
    private var heldKey: Int = KeyEvent.KEYCODE_UNKNOWN
    private val repeatRunnable = object : Runnable {
        override fun run() {
            if (heldKey == KeyEvent.KEYCODE_UNKNOWN) return
            val left = heldKey == prefs.getLeftKey()
            Log.d("PageTurn", "service: auto-repeat tap (heldKey=$heldKey left=$left)")
            performTap(left = left)
            repeatHandler.postDelayed(this, REPEAT_INTERVAL_MS)
        }
    }

    companion object {
        // 押し続け検出後、連続送りが始まるまでの待ち時間
        private const val REPEAT_INITIAL_DELAY_MS = 500L
        // 連続送り中のページ送り間隔（E-Inkの再描画を考慮）
        private const val REPEAT_INTERVAL_MS = 400L
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        val info = serviceInfo
        info.flags = info.flags or AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
        serviceInfo = info
        Log.d("PageTurn", "service: connected, flags=0x${info.flags.toString(16)} " +
                "filterKeyEvents=${info.flags and AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS != 0}")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            event.packageName?.toString()?.let {
                if (it != currentPackage) {
                    Log.d("PageTurn", "service: foreground package changed: $currentPackage -> $it")
                    currentPackage = it
                }
            }
        }
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        Log.d("PageTurn", "service: onKeyEvent action=${event.action} keyCode=${event.keyCode} " +
                "(${KeyEvent.keyCodeToString(event.keyCode)}) repeatCount=${event.repeatCount} " +
                "pkg=$currentPackage")

        if (event.keyCode == KeyEvent.KEYCODE_UNKNOWN) {
            Log.d("PageTurn", "service: ignoring KEYCODE_UNKNOWN")
            return false
        }

        val targetApps = prefs.getTargetApps()
        if (currentPackage.isEmpty() || currentPackage !in targetApps) {
            Log.d("PageTurn", "service: pkg not in targets (targets=${targetApps}), pass through")
            return false
        }

        val leftKey = prefs.getLeftKey()
        val rightKey = prefs.getRightKey()
        Log.d("PageTurn", "service: leftKey=$leftKey rightKey=$rightKey")

        if (leftKey == KeyEvent.KEYCODE_UNKNOWN && rightKey == KeyEvent.KEYCODE_UNKNOWN) {
            Log.d("PageTurn", "service: no keys configured, pass through")
            return false
        }

        return when (event.keyCode) {
            leftKey -> {
                handlePageKey(event, left = true)
                true
            }
            rightKey -> {
                handlePageKey(event, left = false)
                true
            }
            else -> {
                Log.d("PageTurn", "service: keyCode=${event.keyCode} not matched, pass through")
                false
            }
        }
    }

    /**
     * ページ送りキーの Down/Up を処理する。
     * Down 時に即座に1回送り、押し続けられている間は一定間隔で連続送りする。
     * Up 時に連続送りを停止する。
     */
    private fun handlePageKey(event: KeyEvent, left: Boolean) {
        when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                // 同じキーの押下中（OS のキーリピート含む repeatCount>0 や
                // E-Ink の二重 Down）は無視し、自前タイマーに任せる
                if (heldKey == event.keyCode) {
                    Log.d("PageTurn", "service: ${if (left) "LEFT" else "RIGHT"} key already held, ignore down")
                    return
                }
                Log.d("PageTurn", "service: ${if (left) "LEFT" else "RIGHT"} key DOWN -> tap + start repeat")
                stopRepeat()
                heldKey = event.keyCode
                performTap(left = left)
                repeatHandler.postDelayed(repeatRunnable, REPEAT_INITIAL_DELAY_MS)
            }
            KeyEvent.ACTION_UP -> {
                Log.d("PageTurn", "service: ${if (left) "LEFT" else "RIGHT"} key UP -> stop repeat")
                if (heldKey == event.keyCode) stopRepeat()
            }
        }
    }

    private fun stopRepeat() {
        repeatHandler.removeCallbacks(repeatRunnable)
        heldKey = KeyEvent.KEYCODE_UNKNOWN
    }

    private fun performTap(left: Boolean) {
        val dm = resources.displayMetrics
        val x = if (left) dm.widthPixels * 0.1f else dm.widthPixels * 0.9f
        val y = dm.heightPixels * 0.5f
        Log.d("PageTurn", "service: performTap left=$left x=$x y=$y " +
                "(screen ${dm.widthPixels}x${dm.heightPixels})")

        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0L, 50L)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, null, null)
    }

    override fun onInterrupt() {
        stopRepeat()
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        stopRepeat()
        return super.onUnbind(intent)
    }
}
