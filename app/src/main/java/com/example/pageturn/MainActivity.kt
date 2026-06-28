package net.kannagi.pageturn

import android.content.Intent
import android.util.Log
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import net.kannagi.pageturn.ui.theme.Background
import net.kannagi.pageturn.ui.theme.BorderSoft
import net.kannagi.pageturn.ui.theme.PageTurnTheme
import net.kannagi.pageturn.ui.theme.Primary
import net.kannagi.pageturn.ui.theme.PrimaryDark
import net.kannagi.pageturn.ui.theme.PrimaryTint
import net.kannagi.pageturn.ui.theme.StatusOn
import net.kannagi.pageturn.ui.theme.Surface
import net.kannagi.pageturn.ui.theme.TextMuted
import net.kannagi.pageturn.ui.theme.TextPrimary
import net.kannagi.pageturn.ui.theme.Warning
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class Screen { Main, KeyWizard, AppSelection }
enum class WizardStep { Left, Right, Done }

data class AppInfo(val name: String, val packageName: String, val icon: Drawable)

class MainActivity : ComponentActivity() {
    internal var keyEventCallback: ((KeyEvent) -> Boolean)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PageTurnTheme {
                PageTurnApp()
            }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        Log.d("PageTurn", "dispatchKeyEvent: action=${event.action} keyCode=${event.keyCode} " +
                "(${KeyEvent.keyCodeToString(event.keyCode)}) repeatCount=${event.repeatCount} " +
                "callbackSet=${keyEventCallback != null}")
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            keyEventCallback?.let { return it(event) }
        }
        return super.dispatchKeyEvent(event)
    }
}

fun isAccessibilityServiceEnabled(context: android.content.Context): Boolean {
    val serviceName = "${context.packageName}/${PageTurnAccessibilityService::class.java.canonicalName}"
    val enabledServices = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ) ?: return false
    val splitter = TextUtils.SimpleStringSplitter(':')
    splitter.setString(enabledServices)
    while (splitter.hasNext()) {
        if (splitter.next().equals(serviceName, ignoreCase = true)) return true
    }
    return false
}

fun keyDisplayName(keyCode: Int): String =
    if (keyCode == KeyEvent.KEYCODE_UNKNOWN) "未設定"
    else KeyEvent.keyCodeToString(keyCode).removePrefix("KEYCODE_")

// ─── Navigation ──────────────────────────────────────────────────────────────

@Composable
fun PageTurnApp() {
    var screen by remember { mutableStateOf(Screen.Main) }
    when (screen) {
        Screen.Main -> MainScreen(
            onConfigureKeys = { screen = Screen.KeyWizard },
            onSelectApps = { screen = Screen.AppSelection }
        )
        Screen.KeyWizard -> KeyWizardScreen(onDone = { screen = Screen.Main })
        Screen.AppSelection -> AppSelectionScreen(onDone = { screen = Screen.Main })
    }
}

// ─── Shared components ───────────────────────────────────────────────────────

@Composable
private fun SectionLabel(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.labelMedium.copy(
            color = Primary,
            fontSize = 13.sp,
            letterSpacing = 2.sp,
        )
    )
}

@Composable
private fun KeyCapChip(text: String) {
    if (text == "未設定") {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium.copy(color = TextMuted)
        )
        return
    }
    Box(
        modifier = Modifier
            .border(2.dp, Primary, RoundedCornerShape(7.dp))
            .background(PrimaryTint, RoundedCornerShape(7.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            text.uppercase(),
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                color = PrimaryDark,
                letterSpacing = 0.5.sp,
            )
        )
    }
}

// Static (no animation) — E-Ink avoids continuous redraws.
@Composable
private fun StatusDot(enabled: Boolean) {
    val color = if (enabled) StatusOn else Warning
    Box(
        Modifier
            .size(14.dp)
            .background(color, CircleShape)
    )
}

@Composable
private fun SectionCard(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(2.dp, BorderSoft, RoundedCornerShape(14.dp))
            .background(Surface, RoundedCornerShape(14.dp))
    ) {
        content()
    }
}

@Composable
private fun CardDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(2.dp)
            .background(BorderSoft)
    )
}

@Composable
private fun CardRow(
    label: String,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 20.dp, vertical = 18.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge.copy(
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
            )
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            content()
            if (onClick != null) {
                Text(
                    "›",
                    style = TextStyle(fontSize = 26.sp, color = Primary, fontWeight = FontWeight.Bold)
                )
            }
        }
    }
}

// ─── Main screen ─────────────────────────────────────────────────────────────

@Composable
fun MainScreen(onConfigureKeys: () -> Unit, onSelectApps: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { PrefsManager(context) }
    val lifecycleOwner = LocalLifecycleOwner.current

    var serviceEnabled by remember { mutableStateOf(isAccessibilityServiceEnabled(context)) }
    var leftKey by remember { mutableStateOf(prefs.getLeftKey()) }
    var rightKey by remember { mutableStateOf(prefs.getRightKey()) }
    var targetApps by remember { mutableStateOf(prefs.getTargetApps()) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                serviceEnabled = isAccessibilityServiceEnabled(context)
                leftKey = prefs.getLeftKey()
                rightKey = prefs.getRightKey()
                targetApps = prefs.getTargetApps()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(containerColor = Background) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
        ) {
            Spacer(Modifier.height(48.dp))

            // ── App header
            Text(
                "PageTurn",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 42.sp,
                    lineHeight = 48.sp,
                    letterSpacing = 0.sp,
                    color = TextPrimary,
                )
            )
            Spacer(Modifier.height(10.dp))
            Box(
                Modifier
                    .size(48.dp, 4.dp)
                    .background(Primary, RoundedCornerShape(2.dp))
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "ハードウェアキー ページ送り",
                style = MaterialTheme.typography.bodyLarge.copy(color = TextMuted)
            )

            Spacer(Modifier.height(40.dp))

            // ── SERVICE
            SectionLabel("SERVICE")
            Spacer(Modifier.height(12.dp))
            SectionCard {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        StatusDot(serviceEnabled)
                        Text(
                            if (serviceEnabled) "有効" else "無効",
                            style = TextStyle(
                                fontWeight = FontWeight.Bold,
                                fontSize = 22.sp,
                                color = if (serviceEnabled) StatusOn else Warning,
                            )
                        )
                    }
                    if (!serviceEnabled) {
                        TextButton(
                            onClick = {
                                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                            },
                            colors = ButtonDefaults.textButtonColors(contentColor = Primary)
                        ) {
                            Text(
                                "設定を開く",
                                style = MaterialTheme.typography.labelMedium.copy(fontSize = 15.sp)
                            )
                        }
                    }
                }
                if (!serviceEnabled) {
                    CardDivider()
                    Text(
                        "アクセシビリティサービスを有効にすることで\nハードウェアキー操作が使用可能になります",
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = TextMuted,
                            lineHeight = 22.sp,
                        )
                    )
                }
            }

            Spacer(Modifier.height(28.dp))

            // ── KEY BINDINGS
            SectionLabel("KEY BINDINGS")
            Spacer(Modifier.height(12.dp))
            SectionCard {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(36.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            "← LEFT",
                            style = MaterialTheme.typography.labelSmall.copy(color = TextMuted, fontSize = 13.sp)
                        )
                        KeyCapChip(keyDisplayName(leftKey))
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            "RIGHT →",
                            style = MaterialTheme.typography.labelSmall.copy(color = TextMuted, fontSize = 13.sp)
                        )
                        KeyCapChip(keyDisplayName(rightKey))
                    }
                }
                CardDivider()
                CardRow(label = "入力キーの指定", onClick = onConfigureKeys)
            }

            Spacer(Modifier.height(28.dp))

            // ── TARGET APPS
            SectionLabel("TARGET APPS")
            Spacer(Modifier.height(12.dp))
            SectionCard {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        targetApps.size.toString(),
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 44.sp,
                            color = if (targetApps.isNotEmpty()) Primary else TextMuted,
                        )
                    )
                    Text(
                        if (targetApps.isEmpty()) "アプリ未選択" else "個のアプリを選択中",
                        style = MaterialTheme.typography.bodyLarge.copy(color = TextMuted)
                    )
                }
                CardDivider()
                CardRow(label = "アプリの指定", onClick = onSelectApps)
            }

            Spacer(Modifier.height(56.dp))
        }
    }
}

// ─── Key wizard ──────────────────────────────────────────────────────────────

@Composable
fun KeyWizardScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val activity = context as MainActivity
    val view = LocalView.current
    val prefs = remember { PrefsManager(context) }

    var step by remember { mutableStateOf(WizardStep.Left) }
    var leftKeyCode by remember { mutableStateOf(KeyEvent.KEYCODE_UNKNOWN) }
    val cancelFocusRequester = remember { FocusRequester() }

    DisposableEffect(Unit) {
        activity.keyEventCallback = { event ->
            Log.d("PageTurn", "wizard callback: step=$step keyCode=${event.keyCode} " +
                    "(${KeyEvent.keyCodeToString(event.keyCode)}) repeatCount=${event.repeatCount}")
            if (event.keyCode == KeyEvent.KEYCODE_UNKNOWN) {
                Log.d("PageTurn", "wizard: ignoring KEYCODE_UNKNOWN")
            } else {
                when (step) {
                    WizardStep.Left -> {
                        Log.d("PageTurn", "wizard: Left step -> recording leftKey=${event.keyCode}, advancing to Right")
                        leftKeyCode = event.keyCode
                        step = WizardStep.Right
                    }
                    WizardStep.Right -> {
                        if (event.keyCode == leftKeyCode) {
                            Log.d("PageTurn", "wizard: Right step -> same key as left ($leftKeyCode), ignoring")
                        } else {
                            Log.d("PageTurn", "wizard: Right step -> saving left=$leftKeyCode right=${event.keyCode}, Done")
                            prefs.setLeftKey(leftKeyCode)
                            prefs.setRightKey(event.keyCode)
                            step = WizardStep.Done
                        }
                    }
                    WizardStep.Done -> {
                        Log.d("PageTurn", "wizard: callback fired after Done (ignored)")
                    }
                }
            }
            true
        }
        onDispose {
            Log.d("PageTurn", "wizard: callback cleared")
            activity.keyEventCallback = null
        }
    }

    LaunchedEffect(step) {
        if (step == WizardStep.Done) onDone()
    }

    // E-Ink/touch-mode fix: the device starts in touch mode, and in touch mode the
    // framework consumes the first navigation key (DPAD_LEFT/RIGHT — what the page
    // buttons emit) in an early input stage to leave touch mode and assign focus,
    // BEFORE it ever reaches Activity.dispatchKeyEvent. requestFocusFromTouch() leaves
    // touch mode immediately (ensureTouchMode(false)), so the very first real key
    // press is delivered to the wizard's key callback instead of being swallowed.
    LaunchedEffect(Unit) {
        if (view.isInTouchMode) {
            view.requestFocusFromTouch()
        }
        cancelFocusRequester.requestFocus()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
    ) {
        // Static large step number as background watermark
        val ghostNum = if (step == WizardStep.Left) "1" else "2"
        Text(
            ghostNum,
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = (-40).dp),
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 300.sp,
                lineHeight = 300.sp,
                color = Primary.copy(alpha = 0.10f),
            )
        )

        // Step indicator pills (static)
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 72.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                Modifier
                    .size(40.dp, 5.dp)
                    .background(
                        if (step == WizardStep.Left) Primary else BorderSoft,
                        RoundedCornerShape(3.dp)
                    )
            )
            Box(
                Modifier
                    .size(40.dp, 5.dp)
                    .background(
                        if (step == WizardStep.Right) Primary else BorderSoft,
                        RoundedCornerShape(3.dp)
                    )
            )
        }

        // Center content (static)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                when (step) {
                    WizardStep.Left -> "左ページ移動\nボタンを押してください"
                    WizardStep.Right -> "右ページ移動\nボタンを押してください"
                    WizardStep.Done -> ""
                },
                style = TextStyle(
                    fontFamily = FontFamily.Default,
                    fontWeight = FontWeight.Bold,
                    fontSize = 30.sp,
                    lineHeight = 44.sp,
                    color = TextPrimary,
                    textAlign = TextAlign.Center,
                )
            )
            Spacer(Modifier.height(16.dp))
            Text(
                when (step) {
                    WizardStep.Left -> "STEP 1 of 2"
                    WizardStep.Right -> "STEP 2 of 2"
                    WizardStep.Done -> ""
                },
                style = MaterialTheme.typography.labelMedium.copy(color = TextMuted, fontSize = 14.sp)
            )
        }

        // Cancel — bottom
        TextButton(
            onClick = onDone,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 48.dp)
                .focusRequester(cancelFocusRequester),
            colors = ButtonDefaults.textButtonColors(contentColor = TextMuted)
        ) {
            Text(
                "キャンセル",
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 15.sp)
            )
        }
    }
}

// ─── App selection ───────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSelectionScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { PrefsManager(context) }
    var apps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    var selectedPackages by remember { mutableStateOf(prefs.getTargetApps()) }
    var loading by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }

    val filteredApps = remember(apps, searchQuery) {
        if (searchQuery.isBlank()) apps
        else apps.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) {
            val intent = Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
            val pm = context.packageManager
            pm.queryIntentActivities(intent, 0)
                .map { info ->
                    val pkg = info.activityInfo.packageName
                    val icon = try {
                        pm.getApplicationIcon(pkg)
                    } catch (_: Exception) {
                        pm.defaultActivityIcon
                    }
                    AppInfo(
                        name = info.loadLabel(pm).toString(),
                        packageName = pkg,
                        icon = icon,
                    )
                }
                .distinctBy { it.packageName }
                .sortedBy { it.name }
        }
        loading = false
    }

    Scaffold(
        containerColor = Background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "対象アプリの指定",
                            style = MaterialTheme.typography.titleMedium.copy(color = TextPrimary)
                        )
                        Text(
                            "${selectedPackages.size} 個選択中",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = if (selectedPackages.isNotEmpty()) Primary else TextMuted,
                                fontSize = 13.sp,
                            )
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Background),
                actions = {
                    TextButton(
                        onClick = {
                            prefs.setTargetApps(selectedPackages)
                            onDone()
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = Primary)
                    ) {
                        Text(
                            "保存",
                            style = MaterialTheme.typography.labelMedium.copy(fontSize = 16.sp)
                        )
                    }
                }
            )
        }
    ) { padding ->
        if (loading) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Background),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = Primary,
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(44.dp)
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 100.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .background(Background)
                    .padding(padding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Search bar — full width
                item(span = { GridItemSpan(maxLineSpan) }) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 4.dp),
                        textStyle = MaterialTheme.typography.bodyLarge,
                        placeholder = {
                            Text(
                                "アプリを検索",
                                style = MaterialTheme.typography.bodyLarge.copy(color = TextMuted)
                            )
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Primary,
                            unfocusedBorderColor = BorderSoft,
                            cursorColor = Primary,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedContainerColor = Surface,
                            unfocusedContainerColor = Surface,
                            focusedPlaceholderColor = TextMuted,
                            unfocusedPlaceholderColor = TextMuted,
                        )
                    )
                }

                // No-results message
                if (filteredApps.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "「$searchQuery」に一致するアプリがありません",
                                style = MaterialTheme.typography.bodyLarge.copy(color = TextMuted),
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }

                // App grid
                items(filteredApps, key = { it.packageName }) { app ->
                    val checked = app.packageName in selectedPackages
                    AppGridItem(
                        app = app,
                        checked = checked,
                        onClick = {
                            selectedPackages = if (checked) {
                                selectedPackages - app.packageName
                            } else {
                                selectedPackages + app.packageName
                            }
                        }
                    )
                }

                item(span = { GridItemSpan(maxLineSpan) }) {
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
private fun AppGridItem(app: AppInfo, checked: Boolean, onClick: () -> Unit) {
    val iconBitmap = remember(app.packageName) {
        app.icon.toBitmap(128, 128).asImageBitmap()
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(150.dp)
            .clip(RoundedCornerShape(14.dp))
            .border(
                width = if (checked) 3.dp else 2.dp,
                color = if (checked) Primary else BorderSoft,
                shape = RoundedCornerShape(14.dp),
            )
            .background(
                if (checked) PrimaryTint else Surface,
                RoundedCornerShape(14.dp),
            )
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box {
            Image(
                bitmap = iconBitmap,
                contentDescription = null,
                modifier = Modifier.size(58.dp),
                contentScale = ContentScale.Fit,
            )
            if (checked) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 6.dp, y = (-6).dp)
                        .size(24.dp)
                        .background(Primary, CircleShape)
                        .border(2.dp, PrimaryTint, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "✓",
                        style = TextStyle(
                            fontSize = 13.sp,
                            color = Surface,
                            fontWeight = FontWeight.ExtraBold,
                        )
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        // Fixed-height label area so every cell is the same height
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
            Text(
                text = app.name,
                style = TextStyle(
                    fontWeight = if (checked) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 14.sp,
                    lineHeight = 18.sp,
                    color = if (checked) PrimaryDark else TextPrimary,
                    textAlign = TextAlign.Center,
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
