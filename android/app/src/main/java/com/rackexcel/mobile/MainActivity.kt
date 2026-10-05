package com.rackexcel.mobile

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rackexcel.mobile.excel.ImageOutcome
import com.rackexcel.mobile.excel.ImageProcessingState
import com.rackexcel.mobile.excel.ReviewPolicy
import com.rackexcel.mobile.excel.TaskEfficiencyMetrics
import com.rackexcel.mobile.alarm.AlarmIdentityField
import com.rackexcel.mobile.alarm.AlarmMatchMode
import com.rackexcel.mobile.alarm.AlarmMatchResult
import com.rackexcel.mobile.alarm.AlarmRecord
import com.rackexcel.mobile.alarm.AlarmReviewDecision
import com.rackexcel.mobile.alarm.AlarmReviewNavigationPolicy
import com.rackexcel.mobile.alarm.AlarmSeverity
import com.rackexcel.mobile.alarm.AlarmSourceMode
import com.rackexcel.mobile.alarm.AlarmStatus
import com.rackexcel.mobile.alarm.AlarmUiEffect
import com.rackexcel.mobile.alarm.RackAlarmHighlight
import com.rackexcel.mobile.alarm.NetworkAuthMode
import com.rackexcel.mobile.alarm.NetworkGatewayConfig
import com.rackexcel.mobile.alarm.NetworkGatewayFieldMapping
import com.rackexcel.mobile.image.ImageQualityLevel
import com.rackexcel.mobile.storage.TaskHistoryItem
import com.rackexcel.mobile.storage.TaskReviewSnapshot
import com.rackexcel.mobile.storage.ModelProfile
import com.rackexcel.mobile.storage.PromptTemplate
import com.rackexcel.mobile.model.Device
import com.rackexcel.mobile.model.DeviceTypePolicy
import com.rackexcel.mobile.model.Rack
import com.rackexcel.mobile.receiver.ReceiverQrScannerDialog
import com.rackexcel.mobile.ui.DesktopTransferPhase
import com.rackexcel.mobile.ui.DesktopTransferUiState
import com.rackexcel.mobile.ui.EngineBanner
import com.rackexcel.mobile.ui.EngineBannerPhase
import com.rackexcel.mobile.ui.ExportPhase
import com.rackexcel.mobile.ui.RackExcelUiState
import com.rackexcel.mobile.ui.RackExcelViewModel
import com.rackexcel.mobile.ui.RecognitionScrollPolicy
import com.rackexcel.mobile.ui.RecognitionScrollTarget
import com.rackexcel.mobile.ui.SelectedImage
import com.rackexcel.mobile.ui.TaskPolicy
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val Navy = Color(0xFF0A2842)
private val Ink = Color(0xFF17344C)
private val Muted = Color(0xFF667789)
private val Blue = Color(0xFF1768C2)
private val Cyan = Color(0xFF0B8D82)
private val Canvas = Color(0xFFF4F6F8)
private val SurfaceTint = Color(0xFFEAF0F5)
private val Success = Color(0xFF0E7A60)
private val Warning = Color(0xFFA56600)
private val Danger = Color(0xFFB64045)
private val Line = Color(0xFFD9E2E9)

private const val RACK_TOTAL_U = 47

private val YunshuTypography = Typography(
    headlineSmall = TextStyle(fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 19.sp, lineHeight = 25.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 13.sp, lineHeight = 19.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 15.sp, fontWeight = FontWeight.Medium),
)

private enum class AppPage(val label: String, val icon: ImageVector) {
    WORKBENCH("工作台", Icons.Outlined.Dashboard),
    RECOGNITION("智能识别", Icons.Outlined.CameraAlt),
    TASKS("任务中心", Icons.Outlined.CheckCircle),
    SETTINGS("设置", Icons.Outlined.Settings),
}

private enum class SettingsView(val title: String) {
    HUB("设置"),
    COMPUTER("已连接电脑"),
    MODEL("模型配置"),
    PROMPT("识别规则"),
    FILE("文件交付"),
    ALARM("告警联动"),
}

private enum class TaskFilter(val label: String) {
    ALL("全部"),
    ACTIVE("处理中"),
    REVIEW("待复核"),
    RISK("风险线索"),
    DONE("已完成"),
}

private data class FullscreenImage(
    val id: String,
    val uri: Uri,
    val name: String,
)

private data class FullscreenImageGallery(
    val images: List<FullscreenImage>,
    val initialPage: Int = 0,
)

private const val FULL_GALLERY_INDICATOR_LIMIT = 8
private const val GALLERY_INDICATOR_WINDOW_SIZE = 5

internal fun galleryIndicatorPages(totalPages: Int, currentPage: Int): List<Int> {
    if (totalPages <= 0) return emptyList()
    if (totalPages <= FULL_GALLERY_INDICATOR_LIMIT) return (0 until totalPages).toList()

    val clampedCurrentPage = currentPage.coerceIn(0, totalPages - 1)
    val firstPage = (clampedCurrentPage - GALLERY_INDICATOR_WINDOW_SIZE / 2)
        .coerceIn(0, totalPages - GALLERY_INDICATOR_WINDOW_SIZE)
    return (firstPage until firstPage + GALLERY_INDICATOR_WINDOW_SIZE).toList()
}

private object StartupAnimationGate {
    private const val PREFERENCES = "yunshu_startup"
    private const val KEY_HAS_SEEN_FULL_STARTUP = "has_seen_full_startup"
    // Keep the brand handoff consistent for first launch and later cold starts.
    // The in-process session still prevents replay when the activity returns from background.
    private const val FULL_STARTUP_MILLIS = 3_000L
    private const val COMPACT_STARTUP_MILLIS = 3_000L

    private data class Session(val startedAtMillis: Long, val durationMillis: Long)

    private var session: Session? = null

    @Synchronized
    fun remainingMillis(context: Context): Long {
        val now = SystemClock.elapsedRealtime()
        session?.let { active ->
            return (active.durationMillis - (now - active.startedAtMillis)).coerceAtLeast(0L)
        }
        val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        val isFirstStartup = !preferences.getBoolean(KEY_HAS_SEEN_FULL_STARTUP, false)
        val durationMillis = if (isFirstStartup) FULL_STARTUP_MILLIS else COMPACT_STARTUP_MILLIS
        if (isFirstStartup) {
            preferences.edit().putBoolean(KEY_HAS_SEEN_FULL_STARTUP, true).apply()
        }
        session = Session(startedAtMillis = now, durationMillis = durationMillis)
        return durationMillis
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            YunshuTheme {
                YunshuStartupShell {
                    RackExcelApp()
                }
            }
        }
    }
}

// Every role is assigned: any role left to the Material 3 default leaks purple into
// FilterChip, AlertDialog, Switch and OutlinedTextField, which read the container roles.
private val YunshuColorScheme = lightColorScheme(
    primary = Blue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE9F7),
    onPrimaryContainer = Navy,
    inversePrimary = Color(0xFF9CC6EE),
    secondary = Cyan,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDCEFEC),
    onSecondaryContainer = Color(0xFF06463F),
    tertiary = Navy,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFDCE4EC),
    onTertiaryContainer = Navy,
    background = Canvas,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    surfaceVariant = SurfaceTint,
    onSurfaceVariant = Muted,
    surfaceTint = Blue,
    inverseSurface = Navy,
    inverseOnSurface = Canvas,
    error = Danger,
    onError = Color.White,
    errorContainer = Color(0xFFFBE4E5),
    onErrorContainer = Color(0xFF6E1F23),
    outline = Color(0xFF8CA0B0),
    outlineVariant = Line,
    scrim = Color(0xFF000000),
    surfaceBright = Color.White,
    surfaceDim = Color(0xFFDDE4EA),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFAFBFC),
    surfaceContainer = Canvas,
    surfaceContainerHigh = SurfaceTint,
    surfaceContainerHighest = Color(0xFFE2EAF1),
)

@Composable
private fun YunshuTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = YunshuColorScheme,
        typography = YunshuTypography,
        content = content,
    )
}

@Composable
private fun YunshuStartupShell(content: @Composable () -> Unit) {
    val applicationContext = LocalContext.current.applicationContext
    val startupMillis = remember(applicationContext) { StartupAnimationGate.remainingMillis(applicationContext) }
    var showStartupSplash by remember { mutableStateOf(startupMillis > 0L) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            // A return from Home/Recents must resume the current workspace directly.
            if (event == Lifecycle.Event.ON_STOP) {
                showStartupSplash = false
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    Box(Modifier.fillMaxSize()) {
        content()
        if (showStartupSplash) {
            StartupSplash(
                durationMillis = startupMillis,
                onFinished = { showStartupSplash = false },
            )
        }
    }
}

@Composable
private fun rememberMotionEnabled(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            ) > 0f
        }.getOrDefault(true)
    }
}

@Composable
private fun AlarmEffectDispatcher(effect: AlarmUiEffect?) {
    val context = LocalContext.current
    LaunchedEffect(effect?.sequence) {
        val current = effect ?: return@LaunchedEffect
        if (current.playSound) {
            runCatching {
                val tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 82)
                tone.startTone(ToneGenerator.TONE_PROP_BEEP2, 220)
                delay(260L)
                tone.release()
            }
        }
        if (current.vibrate) {
            runCatching {
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                vibrator?.vibrate(VibrationEffect.createOneShot(120L, VibrationEffect.DEFAULT_AMPLITUDE))
            }
        }
    }
}

/**
 * Material 3's lambda-based progress does not interpolate: driving it straight from state
 * makes progress jump one image at a time. Every progress bar goes through here instead.
 */
@Composable
private fun animatedProgress(target: Float, label: String): Float {
    val motionEnabled = rememberMotionEnabled()
    val value by animateFloatAsState(
        targetValue = target.coerceIn(0f, 1f),
        animationSpec = tween(
            durationMillis = if (motionEnabled) 450 else 0,
            easing = FastOutSlowInEasing,
        ),
        label = label,
    )
    return value
}

@Composable
fun RackExcelApp(viewModel: RackExcelViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    var currentPage by rememberSaveable { mutableStateOf(AppPage.WORKBENCH) }
    var aboutOpen by rememberSaveable { mutableStateOf(false) }
    var settingsViewName by rememberSaveable { mutableStateOf(SettingsView.HUB.name) }
    val settingsView = SettingsView.entries.firstOrNull { it.name == settingsViewName } ?: SettingsView.HUB
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    var activeReviewImageId by rememberSaveable { mutableStateOf<String?>(null) }
    var historyDetailTaskId by rememberSaveable { mutableStateOf<String?>(null) }
    var historyReviewImageId by rememberSaveable { mutableStateOf<String?>(null) }
    var fullscreenGallery by remember { mutableStateOf<FullscreenImageGallery?>(null) }
    var showReceiverScanner by rememberSaveable { mutableStateOf(false) }
    var alarmCenterOpen by rememberSaveable { mutableStateOf(false) }
    var activeAlarmId by rememberSaveable { mutableStateOf<String?>(null) }
    val activeReviewOutcome = state.imageOutcomes.firstOrNull { it.imageId == activeReviewImageId }
    val activeHistoryTask = state.taskHistory.firstOrNull { it.taskId == historyDetailTaskId }
    val activeHistoryReview = activeHistoryTask?.reviewItems?.firstOrNull { it.imageId == historyReviewImageId }
    val activeAlarm = state.alarms.firstOrNull { it.alarmId == activeAlarmId }
    val activeAlarmMatch = state.alarmMatches.firstOrNull { it.alarm.alarmId == activeAlarmId }
    AlarmEffectDispatcher(state.alarmEffect)
    val openSingleImage: (Uri, String) -> Unit = { uri, name ->
        fullscreenGallery = FullscreenImageGallery(
            images = listOf(FullscreenImage(id = uri.toString(), uri = uri, name = name)),
        )
    }
    val openImageGallery: (List<SelectedImage>, Int) -> Unit = { images, initialPage ->
        fullscreenGallery = FullscreenImageGallery(
            images = images.map { image -> FullscreenImage(id = image.id, uri = image.uri, name = image.name) },
            initialPage = initialPage.coerceIn(0, images.lastIndex.coerceAtLeast(0)),
        )
    }
    val openReview: (ImageOutcome) -> Unit = { outcome ->
        if (outcome.rack != null) {
            viewModel.beginReview()
            activeReviewImageId = outcome.imageId
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val uri = pendingCameraUri
        if (success && uri != null) {
            viewModel.addImages(listOf(SelectedImage(uri, "拍摄_" + (state.images.size + 1) + ".jpg")))
        }
        pendingCameraUri = null
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            val file = File.createTempFile("yunshu_rack_", ".jpg", context.cacheDir)
            val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
            pendingCameraUri = uri
            cameraLauncher.launch(uri)
        }
    }
    val receiverScannerPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            showReceiverScanner = true
        } else {
            viewModel.reportDesktopScannerPermissionDenied()
        }
    }
    val pickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(20)) { uris ->
        viewModel.addImages(
            uris.mapIndexed { index, uri ->
                SelectedImage(uri, uri.lastPathSegment?.substringAfterLast('/') ?: "图片_" + (index + 1))
            },
        )
    }
    val alarmJsonPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::importMockAlarmJson)
    }
    val takePhoto = {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            val file = File.createTempFile("yunshu_rack_", ".jpg", context.cacheDir)
            val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
            pendingCameraUri = uri
            cameraLauncher.launch(uri)
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }
    val startReceiverScan = {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            showReceiverScanner = true
        } else {
            receiverScannerPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }
    val sectionTitle = when {
        aboutOpen -> "关于云枢智维"
        activeAlarm != null -> "告警现场复核"
        alarmCenterOpen -> "告警联动"
        activeReviewOutcome != null -> "现场复核"
        activeHistoryReview != null -> "历史复核"
        activeHistoryTask != null -> "任务详情"
        currentPage == AppPage.SETTINGS -> settingsView.title
        else -> currentPage.label
    }
    val navigateBack: (() -> Unit)? = when {
        aboutOpen -> ({ aboutOpen = false })
        activeAlarm != null -> ({ activeAlarmId = null })
        alarmCenterOpen -> ({ alarmCenterOpen = false })
        activeHistoryReview != null -> ({ historyReviewImageId = null })
        activeHistoryTask != null -> ({ historyDetailTaskId = null })
        activeReviewOutcome != null -> ({ activeReviewImageId = null })
        currentPage == AppPage.SETTINGS && settingsView != SettingsView.HUB -> ({
            settingsViewName = SettingsView.HUB.name
        })
        else -> null
    }
    val isDetailPage = navigateBack != null

    BackHandler(enabled = isDetailPage) {
        navigateBack?.invoke()
    }

    Scaffold(
        topBar = {
            BrandTopBar(
                sectionTitle = sectionTitle,
                banner = state.engineBanner,
                isDetail = isDetailPage,
                onBack = navigateBack,
                alarmCount = activeAlarmCount(state),
                onOpenAlarms = if (!isDetailPage && state.racks.isNotEmpty() && activeAlarmCount(state) > 0) {
                    {
                        alarmCenterOpen = true
                        activeAlarmId = null
                    }
                } else null,
            )
        },
        bottomBar = {
            if (!isDetailPage) {
                NavigationBar(
                    modifier = Modifier.height(72.dp),
                    containerColor = Color.White,
                    tonalElevation = 0.dp,
                ) {
                    AppPage.entries.forEach { page ->
                        NavigationBarItem(
                            selected = page == currentPage,
                            onClick = {
                                currentPage = page
                                settingsViewName = SettingsView.HUB.name
                            },
                            icon = { Icon(page.icon, contentDescription = page.label, modifier = Modifier.size(20.dp)) },
                            label = { Text(page.label, style = MaterialTheme.typography.labelSmall, maxLines = 1) },
                            alwaysShowLabel = true,
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Blue,
                                selectedTextColor = Blue,
                                indicatorColor = SurfaceTint,
                            ),
                        )
                    }
                }
            }
        },
        containerColor = Canvas,
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
            if (aboutOpen) {
                AboutPage(state = state, onBack = { aboutOpen = false })
            } else if (activeAlarm != null) {
                AlarmReviewPage(
                    alarm = activeAlarm,
                    match = activeAlarmMatch,
                    racks = state.racks,
                    reviewer = state.alarmSettings.reviewer,
                    error = state.error,
                    onDecision = { decision, note ->
                        viewModel.reviewAlarm(activeAlarm.alarmId, decision, note)
                    },
                    onNext = {
                        val next = nextUnresolvedAlarmId(state, activeAlarm.alarmId)
                        if (next == null) {
                            activeAlarmId = null
                        } else {
                            activeAlarmId = next
                        }
                    },
                    hasNext = nextUnresolvedAlarmId(state, activeAlarm.alarmId) != null,
                    onBack = { activeAlarmId = null },
                )
            } else if (alarmCenterOpen) {
                AlarmCenterPage(
                    state = state,
                    onOpenAlarm = { alarmId -> activeAlarmId = alarmId },
                    onSeedDemo = viewModel::seedDemoAlarms,
                    onInject = viewModel::injectDemoAlarm,
                    onRefresh = viewModel::refreshAlarms,
                    onGoRecognition = {
                        alarmCenterOpen = false
                        viewModel.beginNewTask()
                        currentPage = AppPage.RECOGNITION
                    },
                )
            } else if (activeReviewOutcome != null) {
                RackReviewPage(
                    outcome = activeReviewOutcome,
                    imageUri = activeReviewOutcome.reviewUri(state),
                    alarmHighlights = activeReviewOutcome.rack?.let { rackAlarmHighlights(state, it) }.orEmpty(),
                    onOpenImage = openSingleImage,
                    onUpdateDevice = viewModel::updateReviewedDevice,
                    onConfirm = {
                        viewModel.confirmReview(activeReviewOutcome.imageId)
                        activeReviewImageId = null
                    },
                    onReidentify = {
                        viewModel.retryImage(activeReviewOutcome.imageId)
                        activeReviewImageId = null
                    },
                    onBack = { activeReviewImageId = null },
                )
            } else if (activeHistoryReview != null) {
                HistoryReviewPage(
                    item = activeHistoryReview,
                    onOpenImage = openSingleImage,
                    onBack = { historyReviewImageId = null },
                )
            } else if (activeHistoryTask != null) {
                HistoryTaskDetailPage(
                    item = activeHistoryTask,
                    onOpen = { viewModel.openHistory(activeHistoryTask) },
                    onShare = { viewModel.shareHistory(activeHistoryTask) },
                    onSendToDesktop = { viewModel.sendHistoryToDesktop(activeHistoryTask) },
                    connectedComputerName = state.connectedComputer?.receiverName,
                    desktopTransfer = state.desktopTransfer,
                    alarmAudits = activeHistoryTask.alarmRecords.ifEmpty { state.alarmHistory[activeHistoryTask.taskId].orEmpty() },
                    feedbackStatus = historyDeliveryStatus(state, activeHistoryTask),
                    feedbackError = historyDeliveryError(state, activeHistoryTask),
                    onPauseDesktopTransfer = viewModel::pauseDesktopTransfer,
                    onOpenReview = { review -> historyReviewImageId = review.imageId },
                    onResumeForReview = {
                        val reviewImage = activeHistoryTask.reviewItems.firstOrNull { it.rack != null }
                        viewModel.resumeHistoryForReview(activeHistoryTask)
                        historyDetailTaskId = null
                        currentPage = AppPage.RECOGNITION
                        activeReviewImageId = reviewImage?.imageId
                    },
                    onBack = { historyDetailTaskId = null },
                )
            } else {
                Crossfade(
                    targetState = currentPage,
                    // Root navigation is an immediate workspace switch; animated
                    // crossfade makes field taps feel delayed on compact phones.
                    animationSpec = tween(durationMillis = 0),
                    label = "rootPageTransition",
                ) { page ->
                    when (page) {
                    AppPage.WORKBENCH -> WorkbenchPage(
                        state = state,
                        onNewTask = {
                            viewModel.beginNewTask()
                            currentPage = AppPage.RECOGNITION
                        },
                        onContinue = { currentPage = AppPage.RECOGNITION },
                        onOpenReview = { cabinetId ->
                            state.imageOutcomes.firstOrNull { it.cabinetId == cabinetId }?.let { outcome ->
                                viewModel.beginReview()
                                activeReviewImageId = outcome.imageId
                            }
                        },
                        onOpenAlarms = {
                            alarmCenterOpen = true
                            activeAlarmId = null
                        },
                        onInjectAlarm = viewModel::injectDemoAlarm,
                        onSave = viewModel::saveResult,
                        onShare = viewModel::shareResult,
                        onOpen = viewModel::openResult,
                        onSendToDesktop = viewModel::sendResultToDesktop,
                        onPauseDesktopTransfer = viewModel::pauseDesktopTransfer,
                    )

                    AppPage.RECOGNITION -> RecognitionPage(
                        state = state,
                        onRoomNameChange = viewModel::setRoomName,
                        onTakePhoto = takePhoto,
                        onPickImages = { pickerLauncher.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly)) },
                        onRemoveImage = viewModel::removeImage,
                        onOpenImage = openImageGallery,
                        onConcurrencyChange = viewModel::setConcurrency,
                        onGenerate = viewModel::generate,
                        onConfirmNoIssues = viewModel::confirmNoIssuesAndGenerate,
                        onStartNewTask = viewModel::beginNewTask,
                        onOpenReview = openReview,
                        onSave = viewModel::saveResult,
                        onShare = viewModel::shareResult,
                        onOpen = viewModel::openResult,
                        onSendToDesktop = viewModel::sendResultToDesktop,
                        onPauseDesktopTransfer = viewModel::pauseDesktopTransfer,
                    )

                    AppPage.TASKS -> TaskCenterPage(
                        state = state,
                        onRetryFailed = viewModel::retryFailedImages,
                        onRetryImage = viewModel::retryImage,
                        onOpenReview = { outcome ->
                            openReview(outcome)
                        },
                        onClearHistory = viewModel::clearTaskHistory,
                        onDeleteHistory = viewModel::deleteTaskHistory,
                        onOpenHistory = viewModel::openHistory,
                        onShareHistory = viewModel::shareHistory,
                        onOpenHistoryDetail = { item -> historyDetailTaskId = item.taskId },
                    )

                    AppPage.SETTINGS -> SettingsPage(
                        state = state,
                        view = settingsView,
                        onOpenView = { settingsViewName = it.name },
                        onModelProfileActivate = viewModel::activateModelProfile,
                        onCreateModelProfile = viewModel::createModelProfile,
                        onDeleteModelProfile = viewModel::deleteActiveModelProfile,
                        onModelProfileNameChange = viewModel::setModelProfileName,
                        onUrlChange = viewModel::setUrl,
                        onModelChange = viewModel::setModel,
                        onKeyChange = viewModel::setApiKey,
                        onSaveConfig = viewModel::saveConfig,
                        onTestConnection = viewModel::testConnection,
                        onActivatePrompt = viewModel::activatePrompt,
                        onPromptNameChange = viewModel::setPromptEditorName,
                        onPromptVersionChange = viewModel::setPromptEditorVersion,
                        onPromptContentChange = viewModel::setPromptEditorContent,
                        onSavePrompt = viewModel::savePromptTemplate,
                        onDuplicatePrompt = viewModel::duplicateActivePrompt,
                        onResetPrompt = viewModel::resetPromptEditor,
                        onDeletePrompt = viewModel::deleteActivePrompt,
                        onPrefixChange = viewModel::setFilePrefix,
                        onAutoSaveChange = viewModel::setAutoSave,
                        onRetainImagesChange = viewModel::setRetainOriginalImages,
                        onSaveSettings = viewModel::saveAppSettings,
                        onStartComputerScan = startReceiverScan,
                        onPairComputer = viewModel::pairDesktopReceiver,
                        onCheckComputer = viewModel::checkConnectedComputer,
                        onDisconnectComputer = viewModel::disconnectComputer,
                        onAlarmSourceModeChange = viewModel::setAlarmSourceMode,
                        onAlarmDemoModeChange = viewModel::setAlarmDemoMode,
                        onAlarmSoundChange = viewModel::setAlarmSoundEnabled,
                        onAlarmVibrationChange = viewModel::setAlarmVibrationEnabled,
                        onAlarmReviewerChange = viewModel::setAlarmReviewer,
                        onAlarmMatchFieldChange = viewModel::setAlarmMatchField,
                        onAlarmMatchModeChange = viewModel::setAlarmMatchMode,
                        onGatewayConfigChange = viewModel::setNetworkGateway,
                        onGatewaySave = viewModel::saveNetworkGatewayConfig,
                        onGatewayTest = viewModel::testNetworkGateway,
                        onImportAlarmJson = {
                            alarmJsonPicker.launch(arrayOf("application/json", "text/plain", "*/*"))
                        },
                        onOpenAbout = { aboutOpen = true },
                    )
                    }
                }
            }
            fullscreenGallery?.let { gallery ->
                FullscreenImageGalleryDialog(gallery = gallery, onDismiss = { fullscreenGallery = null })
            }
            if (showReceiverScanner) {
                ReceiverQrScannerDialog(
                    onScanned = { pairingUri ->
                        showReceiverScanner = false
                        viewModel.pairDesktopReceiver(pairingUri)
                    },
                    onDismiss = { showReceiverScanner = false },
                )
            }
            }
        }
    }

@Composable
private fun StartupSplash(durationMillis: Long, onFinished: () -> Unit) {
    var stage by remember { mutableStateOf(0) }
    val motionEnabled = rememberMotionEnabled()
    val positioningCopy = listOf(
        "让每一张机柜现场照片",
        "成为可复核的资产台账",
    )

    // This is intentionally a fixed three-second storyboard. The main workspace is composed
    // underneath from frame one; only the visual handoff is delayed.
    LaunchedEffect(durationMillis, motionEnabled) {
        if (!motionEnabled) {
            stage = 7
            delay(durationMillis)
            onFinished()
            return@LaunchedEffect
        }
        stage = 1 // 0ms: logo
        delay(350L)
        stage = 2 // 350ms: brand name
        delay(450L)
        stage = 3 // 800ms: positioning copy
        delay(200L)
        stage = 4 // 1000ms: U-rail lights from bottom to top
        delay(700L)
        stage = 5 // 1700ms: readiness checks
        delay(900L)
        stage = 6 // 2600ms: logo travels to the top-bar anchor
        delay(200L)
        stage = 7 // 2800ms: remaining content fades out
        delay(200L)
        onFinished()
    }

    val logoAlpha by animateFloatAsState(
        targetValue = if (stage >= 1) 1f else 0f,
        animationSpec = if (motionEnabled) {
            spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMediumLow)
        } else {
            tween(durationMillis = 0)
        },
        label = "startupLogoAlpha",
    )
    val logoScale by animateFloatAsState(
        targetValue = if (stage >= 1) 1f else 0.90f,
        animationSpec = if (motionEnabled) {
            spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMediumLow)
        } else {
            tween(durationMillis = 0)
        },
        label = "startupLogoScale",
    )
    val brandReveal by animateFloatAsState(
        targetValue = if (stage >= 2) 1f else 0f,
        animationSpec = tween(durationMillis = if (motionEnabled) 750 else 0, easing = LinearOutSlowInEasing),
        label = "startupBrandReveal",
    )
    val subtitleReveal by animateFloatAsState(
        targetValue = if (stage >= 3) 1f else 0f,
        animationSpec = tween(durationMillis = if (motionEnabled) 700 else 0, easing = LinearOutSlowInEasing),
        label = "startupSubtitleReveal",
    )
    val railProgress by animateFloatAsState(
        targetValue = if (stage >= 4) 1f else 0f,
        animationSpec = tween(durationMillis = if (motionEnabled) 1_300 else 0, easing = FastOutSlowInEasing),
        label = "startupRailProgress",
    )
    val handoffProgress by animateFloatAsState(
        // With Android's "Remove animations" switch on, retain a complete static splash instead
        // of placing only the logo at the top-bar destination for the whole startup interval.
        targetValue = if (stage >= 6 && motionEnabled) 1f else 0f,
        animationSpec = tween(durationMillis = if (motionEnabled) 200 else 0, easing = FastOutSlowInEasing),
        label = "startupLogoHandoff",
    )
    val exitAlpha by animateFloatAsState(
        targetValue = if (stage >= 7 && motionEnabled) 0f else 1f,
        animationSpec = tween(durationMillis = if (motionEnabled) 200 else 0),
        label = "startupHandoffAlpha",
    )

    Surface(
        modifier = Modifier.fillMaxSize().graphicsLayer(alpha = exitAlpha),
        color = Color.White,
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp),
            contentAlignment = Alignment.Center,
        ) {
            val density = LocalDensity.current
            // The static bar beneath the splash already owns the real destination. Moving the
            // last splash logo to that anchor before fading provides a dependable shared-element
            // handoff without coupling this overlay to Scaffold internals.
            // The content box has 28dp horizontal padding and the central row is offset -28dp.
            // Account for both so the overlay logo resolves near the actual top-bar mark rather
            // than merely toward the top-left quadrant.
            val handoffX = with(density) { -(maxWidth.toPx() / 2f + 28.dp.toPx() - 42.dp.toPx()) }
            val handoffY = with(density) { -maxHeight.toPx() / 2f + 62.dp.toPx() }
            val supportingAlpha = 1f - handoffProgress
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Line.copy(alpha = 0.9f)),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(y = (-28).dp),
                contentAlignment = Alignment.Center,
            ) {
                // The rails decorate the edges only. Keeping them outside the central content
                // layout prevents their width from shifting the logo, copy, or readiness rows.
                Box(Modifier.align(Alignment.CenterStart)) {
                    StartupRackRail(progress = railProgress, alpha = supportingAlpha)
                }
                Box(Modifier.align(Alignment.CenterEnd)) {
                    StartupRackRail(progress = railProgress, alpha = supportingAlpha)
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .align(Alignment.Center)
                        // Give every element the same visual axis.  Without an explicit
                        // width the fixed-width slogan/status children can be measured from
                        // their left edge, which makes them look offset from the logo on
                        // high-density phones.
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                ) {
                    BrandLogoMark(
                        modifier = Modifier
                            .size(92.dp)
                            .graphicsLayer(
                                alpha = logoAlpha,
                                scaleX = logoScale * (1f - handoffProgress * 0.65f),
                                scaleY = logoScale * (1f - handoffProgress * 0.65f),
                                translationX = handoffX * handoffProgress,
                                translationY = handoffY * handoffProgress,
                            ),
                        contentDescription = "云枢智维标识",
                    )
                    Spacer(Modifier.height(14.dp))
                    StartupBrandName(reveal = brandReveal, alpha = supportingAlpha)
                    Spacer(Modifier.height(8.dp))
                    StartupPositioningCopy(
                        lines = positioningCopy,
                        reveal = subtitleReveal,
                        alpha = supportingAlpha,
                    )
                    StartupReadinessStatus(
                        stage = stage,
                        motionEnabled = motionEnabled,
                        modifier = Modifier
                            .padding(top = 24.dp)
                            .graphicsLayer(alpha = supportingAlpha),
                    )
                }
            }
        }
    }
}

@Composable
private fun StartupBrandName(reveal: Float, alpha: Float) {
    Text(
        characterRevealText("云枢智维", reveal, Navy.copy(alpha = alpha)),
        modifier = Modifier.fillMaxWidth(),
        style = MaterialTheme.typography.headlineSmall,
        textAlign = TextAlign.Center,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
    )
}

@Composable
private fun StartupPositioningCopy(lines: List<String>, reveal: Float, alpha: Float) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.width(240.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            // Center each line on the same axis as the logo.  A left-aligned fixed-width
            // block makes the shorter second line pull the whole slogan visually left.
            lines.forEach { line ->
                Text(
                    characterRevealText(line, reveal, Muted.copy(alpha = alpha)),
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                )
            }
        }
    }
}

private fun characterRevealText(text: String, reveal: Float, color: Color) = buildAnnotatedString {
    // A single laid-out Text keeps Chinese wrapping and punctuation rules stable. Span alpha is
    // driven by one value, so the visual "one character at a time" reveal has no per-character
    // compose animation overhead.
    text.forEachIndexed { index, character ->
        val progress = (reveal.coerceIn(0f, 1f) * text.length - index).coerceIn(0f, 1f)
        val eased = FastOutSlowInEasing.transform(progress)
        withStyle(SpanStyle(color = color.copy(alpha = color.alpha * eased))) {
            append(character)
        }
    }
}

@Composable
private fun StartupReadinessStatus(
    stage: Int,
    motionEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val readinessItems = listOf("模型配置已加载", "局域网通道已检查", "识别规则已就绪")
    var visibleCount by remember { mutableStateOf(0) }
    LaunchedEffect(stage, motionEnabled) {
        if (!motionEnabled) {
            visibleCount = readinessItems.size
        } else if (stage >= 5 && visibleCount == 0) {
            readinessItems.indices.forEach { index ->
                if (index > 0) delay(140L)
                visibleCount = index + 1
            }
        }
    }
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            // Size to the widest visible row, then center the complete content group.  A
            // broad fixed width leaves a large trailing blank area and makes the group appear
            // left-shifted even when its container is technically centered.
            modifier = Modifier.width(IntrinsicSize.Max),
            verticalArrangement = Arrangement.spacedBy(7.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            readinessItems.forEachIndexed { index, item ->
                AnimatedVisibility(
                    visible = visibleCount > index,
                    enter = fadeIn(tween(if (motionEnabled) 180 else 0)) +
                        slideInVertically(tween(if (motionEnabled) 180 else 0)) { it / 2 },
                    exit = fadeOut(tween(if (motionEnabled) 120 else 0)),
                ) {
                    Row(
                        modifier = Modifier.width(IntrinsicSize.Max),
                        horizontalArrangement = Arrangement.Start,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Keep every checkmark on one vertical axis even when labels have
                        // different widths.
                        Box(
                            modifier = Modifier.width(20.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Outlined.CheckCircle,
                                contentDescription = null,
                                tint = Cyan,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                        Spacer(Modifier.width(6.dp))
                        Text(
                            item,
                            style = MaterialTheme.typography.labelSmall,
                            color = Muted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StartupRackRail(progress: Float, alpha: Float = 1f) {
    Column(
        modifier = Modifier.width(15.dp).graphicsLayer(alpha = alpha),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        repeat(9) { index ->
            // Column is rendered top-to-bottom, so the activation index is reversed to make the
            // U-rail illuminate from the physical bottom upward.
            val segmentProgress = (progress.coerceIn(0f, 1f) * 9f - (8 - index)).coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(lerp(Line, Cyan, segmentProgress)),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BrandTopBar(
    sectionTitle: String,
    banner: EngineBanner,
    isDetail: Boolean,
    onBack: (() -> Unit)? = null,
    alarmCount: Int = 0,
    onOpenAlarms: (() -> Unit)? = null,
) {
    TopAppBar(
        modifier = Modifier.height(if (isDetail) 60.dp else 64.dp),
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Navy,
            navigationIconContentColor = Color.White,
            titleContentColor = Color.White,
            actionIconContentColor = Color.White,
        ),
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Outlined.ArrowBack, contentDescription = "返回", tint = Color.White)
                }
            }
        },
        title = {
            if (isDetail) {
                Text(
                    sectionTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BrandLogoMark(
                        modifier = Modifier.size(28.dp),
                        contentDescription = "云枢智维标识",
                    )
                    Spacer(Modifier.width(8.dp))
                    // Root pages already expose their section through the bottom navigation.
                    // A single-line brand lockup remains readable when Android uses large text.
                    Text(
                        "云枢智维",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        },
        actions = {
            if (alarmCount > 0 && onOpenAlarms != null) {
                IconButton(onClick = onOpenAlarms) {
                    Icon(
                        Icons.Outlined.Notifications,
                        contentDescription = "当前告警 $alarmCount 条",
                        tint = Danger,
                    )
                }
                Text(
                    "告警 $alarmCount",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    maxLines = 1,
                )
                Spacer(Modifier.width(4.dp))
            }
            CompactEngineStatus(banner)
            Spacer(Modifier.width(10.dp))
        },
    )
}

@Composable
private fun BrandLogoMark(
    modifier: Modifier = Modifier,
    contentDescription: String?,
) {
    Image(
        painter = painterResource(id = R.drawable.ic_yunshu_logo_mark),
        contentDescription = contentDescription,
        contentScale = ContentScale.Fit,
        modifier = modifier,
    )
}

@Composable
private fun CompactEngineStatus(banner: EngineBanner) {
    val (label, color) = when (banner.phase) {
        EngineBannerPhase.CHECKING -> "引擎自检" to Blue
        EngineBannerPhase.READY -> "引擎待命" to Success
        EngineBannerPhase.FAILED -> "链路异常" to Danger
        EngineBannerPhase.IDLE -> "尚未检测" to Muted
    }
    Row(
        modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(RoundedCornerShape(99.dp))
                .background(color),
        )
        Spacer(Modifier.width(5.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.92f), maxLines = 1)
    }
}

private fun activeAlarmCount(state: RackExcelUiState): Int =
    state.alarms.count { it.status == AlarmStatus.UNHANDLED }

private fun nextUnresolvedAlarmId(state: RackExcelUiState, currentAlarmId: String): String? =
    AlarmReviewNavigationPolicy.nextUnresolvedAlarmId(state.alarms, currentAlarmId)

private fun alarmSeverityRank(severity: AlarmSeverity): Int = when (severity) {
    AlarmSeverity.CRITICAL -> 0
    AlarmSeverity.WARNING -> 1
    AlarmSeverity.INFO -> 2
}

private fun alarmSeverityColor(severity: AlarmSeverity): Color = when (severity) {
    AlarmSeverity.CRITICAL -> Danger
    AlarmSeverity.WARNING -> Warning
    AlarmSeverity.INFO -> Success
}

private fun rackAlarmHighlights(
    state: RackExcelUiState,
    rack: Rack,
): List<RackAlarmHighlight> = state.alarmMatches
    .asSequence()
    .filterIsInstance<AlarmMatchResult.Matched>()
    .filter { it.alarm.status == AlarmStatus.UNHANDLED && it.coordinate.cabinetId == rack.cabinetId }
    .groupBy { it.coordinate.deviceIndex }
    .values
    .mapNotNull { matches ->
        val first = matches.minWithOrNull(compareBy<AlarmMatchResult.Matched> { alarmSeverityRank(it.alarm.severity) }.thenByDescending { it.alarm.occurredAtMillis }) ?: return@mapNotNull null
        RackAlarmHighlight(
            alarmId = first.alarm.alarmId,
            severity = first.alarm.severity,
            coordinate = first.coordinate,
            count = matches.size,
        )
    }
    .toList()

@Composable
private fun WorkbenchPage(
    state: RackExcelUiState,
    onNewTask: () -> Unit,
    onContinue: () -> Unit,
    onOpenReview: (String) -> Unit,
    onOpenAlarms: () -> Unit,
    onInjectAlarm: () -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit,
    onOpen: () -> Unit,
    onSendToDesktop: () -> Unit,
    onPauseDesktopTransfer: () -> Unit,
) {
    val effective = TaskPolicy.effectiveConcurrency(state.concurrency, state.images.size)
    val pendingReview = state.imageOutcomes.count(ReviewPolicy::requiresManualReview)
    val hasDelivery = state.resultName != null || state.taskHistory.any { it.resultName != null }
    val firstReviewRack = state.racks.firstOrNull { rack ->
        rack.uncertain.isNotEmpty() || rack.riskCandidates.isNotEmpty()
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("现场工作台", style = MaterialTheme.typography.labelMedium, color = Blue)
                    Spacer(Modifier.height(3.dp))
                    Text("开始一次机柜采集", style = MaterialTheme.typography.titleLarge, color = Navy)
                    Spacer(Modifier.height(3.dp))
                    Text("拍照或上传完整原图，自动生成可复核的三表 Excel。", style = MaterialTheme.typography.bodySmall, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.width(12.dp))
                MiniRackGlyph(usedU = state.racks.sumOf { rack -> rack.devices.sumOf { it.heightU } }, compact = true)
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onNewTask,
                    enabled = !state.isRunning,
                    modifier = Modifier.weight(1f).height(46.dp),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Icon(Icons.Outlined.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("正面采集", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                OutlinedButton(
                    onClick = onNewTask,
                    enabled = !state.isRunning,
                    modifier = Modifier.weight(1f).height(46.dp),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Icon(Icons.Outlined.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("反面采集", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        firstReviewRack?.let { rack ->
            item {
                OutlinedButton(
                    onClick = { onOpenReview(rack.cabinetId) },
                    enabled = !state.isRunning,
                    modifier = Modifier.fillMaxWidth().height(42.dp),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Icon(Icons.Outlined.CheckCircle, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("继续现场复核 · ${rack.cabinetId}", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        item {
            WorkbenchMetrics(
                pendingReview = pendingReview,
                recognizedRacks = state.racks.size,
                hasDelivery = hasDelivery,
            )
        }
        item {
            AlarmWorkbenchEntry(
                state = state,
                onOpenAlarms = onOpenAlarms,
                onInjectAlarm = onInjectAlarm,
                onGoRecognition = onNewTask,
            )
        }
        if (state.images.isNotEmpty() && state.resultName == null) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("当前采集任务", style = MaterialTheme.typography.titleSmall, color = Navy)
                        Text("已选 ${state.images.size} 张 · 本次同时处理 ${effective} 张", style = MaterialTheme.typography.bodySmall, color = Muted)
                    }
                    TextButton(onClick = onContinue, enabled = !state.isRunning) { Text("继续采集") }
                }
            }
        }
        if (state.racks.isNotEmpty()) {
            item { SectionTitle("已识别机柜", "按柜号排序；黄色标识表示仍需现场核对。") }
            item { RecognizedRackRail(state, onOpenReview) }
        }
        if (state.resultName != null) {
            item { SectionTitle("最近交付", "三表 Excel 已就绪，可保存、打开、分享或发送电脑。") }
            item {
                ResultFileCard(
                    name = state.resultName,
                    saved = state.resultUri != null,
                    connectedComputerName = state.connectedComputer?.receiverName,
                    transfer = state.desktopTransfer,
                    onSave = onSave,
                    onShare = onShare,
                    onOpen = onOpen,
                    onSendToDesktop = onSendToDesktop,
                    onPauseDesktopTransfer = onPauseDesktopTransfer,
                )
            }
        } else {
            item { Text("完成识别后自动生成三表 Excel。", style = MaterialTheme.typography.bodySmall, color = Muted) }
        }
        if (state.status.isNotBlank()) {
            item { StatusStrip(state.status, state.error) }
        }
        state.error?.let { error -> item { ErrorCard(error) } }
    }
}

@Composable
private fun CaptureButton(onClick: () -> Unit, enabled: Boolean, modifier: Modifier) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
    ) {
        Icon(Icons.Outlined.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(7.dp))
        Text("拍照采集", maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun GalleryButton(onClick: () -> Unit, enabled: Boolean, modifier: Modifier) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, Blue.copy(alpha = 0.45f)),
    ) {
        Icon(Icons.Outlined.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(7.dp))
        Text("从相册上传", maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun WorkbenchMetrics(pendingReview: Int, recognizedRacks: Int, hasDelivery: Boolean) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Line),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            WorkbenchMetricCell("待复核", "$pendingReview 项", Modifier.weight(1f), pendingReview > 0)
            Box(Modifier.width(1.dp).height(28.dp).background(Line))
            WorkbenchMetricCell("已识别", "$recognizedRacks 柜", Modifier.weight(1f))
            Box(Modifier.width(1.dp).height(28.dp).background(Line))
            WorkbenchMetricCell("最近交付", if (hasDelivery) "已生成" else "暂无", Modifier.weight(1f), hasDelivery)
        }
    }
}

@Composable
private fun WorkbenchMetricCell(
    title: String,
    value: String,
    modifier: Modifier,
    highlighted: Boolean = false,
) {
    Column(modifier = modifier.padding(horizontal = 9.dp)) {
        Text(title, style = MaterialTheme.typography.labelSmall, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(2.dp))
        Text(value, style = MaterialTheme.typography.labelLarge, color = if (highlighted) Success else Navy, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun AlarmWorkbenchEntry(
    state: RackExcelUiState,
    onOpenAlarms: () -> Unit,
    onInjectAlarm: () -> Unit,
    onGoRecognition: () -> Unit,
) {
    val hasRacks = state.racks.isNotEmpty()
    val unhandled = activeAlarmCount(state)
    val matched = state.alarmMatches.count { result ->
        result is AlarmMatchResult.Matched && result.alarm.status == AlarmStatus.UNHANDLED
    }
    val unmatched = state.alarmMatches.count { result ->
        result is AlarmMatchResult.Unmatched && result.alarm.status == AlarmStatus.UNHANDLED
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(9.dp),
        color = if (unhandled > 0) Color(0xFFFFF5E7) else Color.White,
        border = BorderStroke(1.dp, if (unhandled > 0) Color(0xFFE8C78E) else Line),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.Notifications,
                    contentDescription = null,
                    tint = if (unhandled > 0) Warning else Blue,
                    modifier = Modifier.size(19.dp),
                )
                Spacer(Modifier.width(7.dp))
                Column(Modifier.weight(1f)) {
                    Text("网管故障 / 告警联动", style = MaterialTheme.typography.titleSmall, color = Navy)
                    Text(
                        when {
                            !hasRacks -> "请先完成一次识别，告警才能定位到柜号和 U 位"
                            unhandled > 0 -> "当前告警 $unhandled 条 · 已定位 $matched 台 · 未匹配 $unmatched 条"
                            else -> "当前任务暂无活动告警，可载入演示数据进行联动演示"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = Muted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (hasRacks && unhandled > 0) {
                    TextButton(onClick = onOpenAlarms, contentPadding = PaddingValues(horizontal = 4.dp)) {
                        Text("查看", color = Warning)
                    }
                }
            }
            if (!hasRacks) {
                OutlinedButton(
                    onClick = onGoRecognition,
                    modifier = Modifier.fillMaxWidth().height(40.dp),
                    shape = RoundedCornerShape(8.dp),
                ) { Text("去智能识别") }
            } else if (state.alarmSettings.sourceMode == AlarmSourceMode.MOCK) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    OutlinedButton(
                        onClick = onInjectAlarm,
                        modifier = Modifier.weight(1f).height(40.dp),
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("注入一条告警", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (unhandled > 0) {
                        Button(
                            onClick = onOpenAlarms,
                            modifier = Modifier.weight(1f).height(40.dp),
                            shape = RoundedCornerShape(8.dp),
                        ) { Text("进入告警列表") }
                    } else {
                        OutlinedButton(
                            onClick = onOpenAlarms,
                            modifier = Modifier.weight(1f).height(40.dp),
                            shape = RoundedCornerShape(8.dp),
                        ) { Text("查看告警联动") }
                    }
                }
            } else if (unhandled > 0) {
                OutlinedButton(
                    onClick = onOpenAlarms,
                    modifier = Modifier.fillMaxWidth().height(40.dp),
                    shape = RoundedCornerShape(8.dp),
                ) { Text("进入告警列表") }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecognizedRackRail(state: RackExcelUiState, onOpenReview: (String) -> Unit) {
    val motionEnabled = rememberMotionEnabled()
    LazyRow(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        items(state.racks, key = { rack -> rack.cabinetId + rack.imageName }) { rack ->
            val usedU = rack.devices.sumOf { it.heightU }
            val rackAlarms = rackAlarmHighlights(state, rack)
            val reviewText = when {
                rackAlarms.any { it.severity == AlarmSeverity.CRITICAL } -> "严重告警 ${rackAlarms.count { it.severity == AlarmSeverity.CRITICAL }} 条"
                rackAlarms.any { it.severity == AlarmSeverity.WARNING } -> "一般告警 ${rackAlarms.count { it.severity == AlarmSeverity.WARNING }} 条"
                rackAlarms.any { it.severity == AlarmSeverity.INFO } -> "提示告警 ${rackAlarms.count { it.severity == AlarmSeverity.INFO }} 条"
                rack.uncertain.isNotEmpty() -> "${rack.uncertain.size} 项待复核"
                rack.riskCandidates.isNotEmpty() -> "${rack.riskCandidates.size} 条风险线索"
                else -> "已识别"
            }
            Surface(
                modifier = Modifier
                    .width(154.dp)
                    .then(
                        if (motionEnabled) {
                            Modifier.animateItemPlacement(animationSpec = tween(durationMillis = 180))
                        } else {
                            Modifier
                        },
                    )
                    .clickable { onOpenReview(rack.cabinetId) },
                shape = RoundedCornerShape(10.dp),
                color = Color.White,
                border = BorderStroke(1.dp, Line),
            ) {
                Row(Modifier.padding(11.dp), verticalAlignment = Alignment.CenterVertically) {
                    MiniRackGlyph(usedU = usedU, compact = true)
                    Spacer(Modifier.width(9.dp))
                    Column(Modifier.weight(1f)) {
                        Text(rack.cabinetId, style = MaterialTheme.typography.titleSmall, color = Navy, fontWeight = FontWeight.Bold)
                        Text("已用 ${usedU}U · ${rack.devices.size} 台", style = MaterialTheme.typography.labelSmall, color = Muted, maxLines = 1)
                        Spacer(Modifier.height(3.dp))
                        Text(
                            reviewText,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (rack.uncertain.isNotEmpty() || rack.riskCandidates.isNotEmpty()) Warning else Success,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ThreeSheetPreview(state: RackExcelUiState) {
    val totalUsed = state.racks.sumOf { rack -> rack.devices.sumOf { it.heightU } }
    Surface(shape = RoundedCornerShape(10.dp), color = SurfaceTint, border = BorderStroke(1.dp, Line)) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MiniRackGlyph(
                    usedU = totalUsed,
                    capacityU = state.racks.size * RACK_TOTAL_U,
                    compact = true,
                )
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f)) {
                    Text("三表 Excel 交付", style = MaterialTheme.typography.titleSmall, color = Navy)
                    Text("可确认字段自动填充，台账与环境待核验字段保留人工补录位。", style = MaterialTheme.typography.bodySmall, color = Muted)
                }
            }
            Spacer(Modifier.height(11.dp))
            HorizontalDivider(color = Line)
            Spacer(Modifier.height(9.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DeliverySheetCell("01", "机房上架图", "47U 图")
                DeliverySheetCell("02", "设备明细", "18 字段")
                DeliverySheetCell("03", "统计分析", "6 分析区")
            }
        }
    }
}

@Composable
private fun RowScope.DeliverySheetCell(index: String, title: String, detail: String) {
    Column(modifier = Modifier.weight(1f)) {
        Text(index, style = MaterialTheme.typography.labelSmall, color = Cyan, fontWeight = FontWeight.Bold)
        Text(title, style = MaterialTheme.typography.labelMedium, color = Navy, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(detail, style = MaterialTheme.typography.labelSmall, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun MiniRackGlyph(usedU: Int, capacityU: Int = RACK_TOTAL_U, compact: Boolean = false) {
    val height = if (compact) 48.dp else 78.dp
    val slots = if (compact) 5 else 8
    val capacity = capacityU.coerceAtLeast(1)
    // Round up so any non-zero occupancy lights at least one slot.
    val filledSlots = ((usedU.coerceIn(0, capacity).toLong() * slots + capacity - 1) / capacity).toInt()
    Surface(
        modifier = Modifier.width(if (compact) 34.dp else 46.dp).height(height),
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFF0E3859),
        border = BorderStroke(1.dp, Color(0xFF5C90B2)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            repeat(slots) { index ->
                val highlighted = index >= slots - filledSlots
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (highlighted) Cyan else Color(0xFF8EB3C9).copy(alpha = 0.55f)),
                )
            }
        }
    }
}

@Composable
private fun RecognitionPage(
    state: RackExcelUiState,
    onRoomNameChange: (String) -> Unit,
    onTakePhoto: () -> Unit,
    onPickImages: () -> Unit,
    onRemoveImage: (Uri) -> Unit,
    onOpenImage: (List<SelectedImage>, Int) -> Unit,
    onConcurrencyChange: (Int) -> Unit,
    onGenerate: () -> Unit,
    onConfirmNoIssues: () -> Unit,
    onStartNewTask: () -> Unit,
    onOpenReview: (ImageOutcome) -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit,
    onOpen: () -> Unit,
    onSendToDesktop: () -> Unit,
    onPauseDesktopTransfer: () -> Unit,
) {
    val motionEnabled = rememberMotionEnabled()
    val haptic = LocalHapticFeedback.current
    val effective = TaskPolicy.effectiveConcurrency(state.concurrency, state.images.size)
    val attentionImages = state.images.count { it.quality?.level == ImageQualityLevel.NEEDS_ATTENTION }
    val outcomesByImageId = remember(state.imageOutcomes) { state.imageOutcomes.associateBy(ImageOutcome::imageId) }
    var advancedOptionsOpen by rememberSaveable { mutableStateOf(false) }
    var showDirectConfirmDialog by rememberSaveable { mutableStateOf(false) }
    val listState = rememberLazyListState()
    var wasRunning by remember { mutableStateOf(state.isRunning) }
    LaunchedEffect(state.isRunning, state.resultName) {
        if (motionEnabled && wasRunning && !state.isRunning && state.resultName != null) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
        wasRunning = state.isRunning
    }
    val elapsedMillis by produceState(initialValue = 0L, state.isRunning, state.taskStartedAtMillis) {
        while (state.isRunning && state.taskStartedAtMillis != null) {
            value = (System.currentTimeMillis() - state.taskStartedAtMillis).coerceAtLeast(0L)
            delay(1_000)
        }
    }
    val exportPhase = state.exportPhase
    val awaitingExportConfirmation = exportPhase == ExportPhase.WAITING_CONFIRMATION
    val writingExcel = exportPhase == ExportPhase.WRITING
    val recognized = state.imageOutcomes.any { it.rack != null }
    val needsReview = state.imageOutcomes.any(ReviewPolicy::requiresManualReview)
    val reviewTarget = state.imageOutcomes.firstOrNull {
        it.imageId == TaskPolicy.preferredReviewOutcomeId(state.imageOutcomes)
    }
    val exportDots = ".".repeat(state.exportEllipsisCount.coerceIn(1, 6))
    val deliveryFlowVisible = awaitingExportConfirmation || writingExcel || state.resultName != null
    val scrollTarget = RecognitionScrollPolicy.target(
        isRunning = state.isRunning,
        exportPhase = exportPhase,
        hasResult = state.resultName != null,
    )
    var lastAutoScrollTarget by remember { mutableStateOf(RecognitionScrollTarget.NONE) }
    LaunchedEffect(scrollTarget) {
        if (scrollTarget == lastAutoScrollTarget) return@LaunchedEffect
        // Run after the new phase's LazyColumn content has entered composition.
        delay(80L)
        val targetIndex = when (scrollTarget) {
            RecognitionScrollTarget.TASK_PROGRESS -> RecognitionScrollPolicy.runningTimelineIndex(
                hasImages = state.images.isNotEmpty(),
                advancedOptionsOpen = advancedOptionsOpen,
            )
            RecognitionScrollTarget.EXPORT_WRITING -> RecognitionScrollPolicy.EXPORT_WRITING_INDEX
            RecognitionScrollTarget.DELIVERY -> 0
            RecognitionScrollTarget.NONE -> null
        }
        if (targetIndex != null) {
            // LazyColumn may still be measuring after the phase change. Wait a
            // few frames instead of risking an out-of-range scroll request.
            repeat(12) {
                if (targetIndex < listState.layoutInfo.totalItemsCount) {
                    runCatching { listState.animateScrollToItem(targetIndex) }
                    return@LaunchedEffect
                }
                delay(16L)
            }
        }
        lastAutoScrollTarget = scrollTarget
    }
    var bottomActionBarHeightPx by remember { mutableIntStateOf(0) }
    val bottomActionBarHeight = with(LocalDensity.current) { bottomActionBarHeightPx.toDp() }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                top = 14.dp,
                end = 16.dp,
                bottom = if (bottomActionBarHeightPx == 0) 128.dp else bottomActionBarHeight + 12.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
        if (deliveryFlowVisible) {
            item(key = "deliveryTaskSummary") {
                RecognitionTaskSummaryCard(
                    imageCount = state.images.size,
                    rackCount = state.racks.size,
                    reviewCount = state.imageOutcomes.count(ReviewPolicy::requiresManualReview),
                    writing = writingExcel,
                    delivered = state.resultName != null,
                )
            }
        } else {
            item {
                SectionTitle("开始采集", "拍照或选择完整机柜原图；每张图片独立识别。")
            }
            item {
                CaptureCommandStrip(
                    onTakePhoto = onTakePhoto,
                    onPickImages = onPickImages,
                    enabled = !state.isRunning,
                )
            }
            if (state.images.isNotEmpty()) {
                item {
                    Surface(shape = RoundedCornerShape(9.dp), color = Color.White, border = BorderStroke(1.dp, Line)) {
                        Column(Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("图片队列", style = MaterialTheme.typography.titleSmall, color = Navy)
                                Spacer(Modifier.width(7.dp))
                                Text("${state.images.size} 张", style = MaterialTheme.typography.labelMedium, color = Blue)
                            }
                            Spacer(Modifier.height(9.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                itemsIndexed(state.images, key = { _, image -> image.id }) { index, image ->
                                    val outcome = outcomesByImageId[image.id]
                                    ImagePreview(
                                        image = image,
                                        displayName = outcome?.displayImageAlias(index + 1) ?: "待识别图片 ${index + 1}",
                                        onOpen = { onOpenImage(state.images, index) },
                                        onRemove = { onRemoveImage(image.uri) },
                                        enabled = !state.isRunning && state.imageOutcomes.isEmpty(),
                                    )
                                }
                            }
                        }
                    }
                }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Outlined.Info,
                            contentDescription = null,
                            tint = if (attentionImages > 0) Warning else Success,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(7.dp))
                        Text(
                            if (attentionImages > 0) {
                                "建议补拍 $attentionImages 张：请核对侧轨 U 位、亮度和机柜完整性。"
                            } else {
                                "图片已完成本地检查；Excel 将按 K03、K04、K10…排序。"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (attentionImages > 0) Warning else Muted,
                        )
                    }
                }
                item {
                    OutlinedTextField(
                        value = state.roomName,
                        onValueChange = onRoomNameChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("机房名称（可留空）") },
                        singleLine = true,
                    )
                }
                item {
                    TextButton(
                        onClick = { advancedOptionsOpen = !advancedOptionsOpen },
                        enabled = !state.isRunning,
                        contentPadding = PaddingValues(0.dp),
                    ) {
                        Icon(Icons.Outlined.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(5.dp))
                        Text(if (advancedOptionsOpen) "收起高级选项" else "高级选项 · 本次同时处理 $effective 张")
                    }
                }
                if (advancedOptionsOpen) {
                    item {
                        Surface(shape = RoundedCornerShape(8.dp), color = Color.White, border = BorderStroke(1.dp, Line)) {
                            Column(Modifier.padding(12.dp)) {
                                Text("同时处理图片数", style = MaterialTheme.typography.titleSmall, color = Navy)
                                Spacer(Modifier.height(3.dp))
                                Text("默认 5 张；图片少于设定值时自动调整。单张异常最多自动修复 3 次。", style = MaterialTheme.typography.bodySmall, color = Muted)
                                Spacer(Modifier.height(9.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                    (1..TaskPolicy.MAX_CONCURRENCY).forEach { value ->
                                        FilterChip(
                                            selected = state.concurrency == value,
                                            onClick = { onConcurrencyChange(value) },
                                            enabled = !state.isRunning,
                                            label = { Text(value.toString()) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        // These cards appear and disappear as the task moves through its phases. Keys plus
        // animateItem() let the list fade them in/out and slide the neighbours, instead of
        // snapping the whole column. AnimatedVisibility cannot do this inside a LazyColumn with
        // spacedBy: a hidden zero-height item still consumes its 12dp gap.
        if (state.isRunning || state.imageOutcomes.isNotEmpty() || state.resultName != null) {
            item(key = "recognitionTimeline") {
                Box(Modifier.animateItem()) { RecognitionTimeline(state) }
            }
        }
        if (awaitingExportConfirmation && recognized) {
            item(key = "exportConfirmation") {
                PhaseEntry(modifier = Modifier.animateItem()) {
                    ExportConfirmationCard(
                        reviewCount = state.imageOutcomes.count(ReviewPolicy::requiresManualReview),
                        onOpenReview = reviewTarget?.let { target -> { onOpenReview(target) } },
                        onConfirm = {
                            if (needsReview) {
                                showDirectConfirmDialog = true
                            } else {
                                onConfirmNoIssues()
                            }
                        }
                    )
                }
            }
        }
        if (writingExcel) {
            item(key = "excelWriting") {
                PhaseEntry(modifier = Modifier.animateItem()) {
                    ExcelWritingStatusCard(
                        ellipsis = exportDots,
                        progress = state.exportProgress,
                        targetMillis = state.exportTargetMillis,
                    )
                }
            }
        }
        if (state.isRunning && !writingExcel) {
            item(key = "taskProgress") {
                val taskProgress = animatedProgress(state.progress, "taskProgress")
                Surface(
                    modifier = Modifier.animateItem(),
                    shape = RoundedCornerShape(9.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, Line),
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("识别任务进行中", style = MaterialTheme.typography.titleSmall, color = Navy)
                            Spacer(Modifier.width(8.dp))
                            AnimatedContent(
                                targetState = (taskProgress * 100).toInt(),
                                transitionSpec = {
                                    (slideInVertically { height -> height } + fadeIn()) togetherWith
                                        (slideOutVertically { height -> -height } + fadeOut())
                                },
                                label = "taskProgressPercent",
                            ) { percent ->
                                Text("$percent%", style = MaterialTheme.typography.labelMedium, color = Blue)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(progress = { taskProgress }, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(7.dp))
                        Text(state.status, style = MaterialTheme.typography.bodySmall, color = Muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text("任务计时：${formatDuration(elapsedMillis)}", style = MaterialTheme.typography.labelSmall, color = Blue)
                    }
                }
            }
        }
        if (state.resultName != null) {
            item(key = "resultFile") {
                PhaseEntry(modifier = Modifier.animateItem()) {
                    ResultFileCard(
                        name = state.resultName,
                        saved = state.resultUri != null,
                        connectedComputerName = state.connectedComputer?.receiverName,
                        transfer = state.desktopTransfer,
                        onSave = onSave,
                        onShare = onShare,
                        onOpen = onOpen,
                        onSendToDesktop = onSendToDesktop,
                        onPauseDesktopTransfer = onPauseDesktopTransfer,
                    )
                }
            }
            item(key = "currentTaskEfficiency") {
                PhaseEntry(modifier = Modifier.animateItem()) {
                    CurrentTaskEfficiencyCard(
                        imageCount = state.images.size,
                        rackCount = state.racks.size,
                        durationMillis = ((state.taskFinishedAtMillis ?: System.currentTimeMillis()) -
                            (state.taskStartedAtMillis ?: System.currentTimeMillis())).coerceAtLeast(0L),
                        metrics = state.efficiency,
                        compact = true,
                    )
                }
            }
        }
        // Once delivery is finished, keep the actionable result above the diagnostic detail.
        // Users can scroll down to inspect each source image after opening/saving the workbook.
        if (state.imageOutcomes.isNotEmpty()) {
            item { SectionTitle("逐图状态", "每张原图独立处理；自动修复过程可追溯。") }
            items(state.imageOutcomes, key = { it.imageId }) { outcome ->
                AnimatedOutcomeEntry(
                    outcome = outcome,
                    onOpenReview = outcome.rack?.let { { onOpenReview(outcome) } },
                    modifier = Modifier.animateItem(),
                )
            }
        }
        if (!state.isRunning && state.status.isNotBlank()) {
            item { StatusStrip(state.status, state.error) }
        }
        state.error?.let { error -> item { ErrorCard(error) } }
        }
        if (state.images.isNotEmpty() || state.isRunning || state.resultName != null || awaitingExportConfirmation || writingExcel) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .onSizeChanged { bottomActionBarHeightPx = it.height },
                color = Color.White,
                border = BorderStroke(1.dp, Line),
            ) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                        Text(
                            "已选 ${state.images.size} 张 · 本次同时处理 $effective 张",
                            style = MaterialTheme.typography.labelLarge,
                            color = Navy,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            when {
                                writingExcel -> "正在整理交付文件"
                                state.isRunning -> "运行 ${formatDuration(elapsedMillis)}"
                                awaitingExportConfirmation -> if (needsReview) "有 ${state.imageOutcomes.count(ReviewPolicy::requiresManualReview)} 项待复核，可直接进入现场复核" else "识别完成，可查看机柜图或确认生成 Excel"
                                state.resultName != null -> "Excel 已交付，可查看复核记录或开始新一轮采集"
                                else -> "识别完成后可进入现场复核"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = Muted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    Spacer(Modifier.height(8.dp))
                    when {
                        awaitingExportConfirmation && reviewTarget != null -> {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                OutlinedButton(
                                    onClick = { onOpenReview(reviewTarget) },
                                    modifier = Modifier.weight(1f).height(44.dp),
                                    shape = RoundedCornerShape(8.dp),
                                ) {
                                    Icon(Icons.Outlined.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(if (needsReview) "进入现场复核" else "查看机柜图", maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                Button(
                                    onClick = {
                                        if (needsReview) showDirectConfirmDialog = true else onConfirmNoIssues()
                                    },
                                    modifier = Modifier.weight(1f).height(44.dp),
                                    shape = RoundedCornerShape(8.dp),
                                ) {
                                    Icon(Icons.Outlined.Description, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(if (needsReview) "确认并生成" else "生成 Excel", maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                        state.resultName != null && reviewTarget != null -> {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                OutlinedButton(
                                    onClick = { onOpenReview(reviewTarget) },
                                    modifier = Modifier.weight(1f).height(44.dp),
                                    shape = RoundedCornerShape(8.dp),
                                ) {
                                    Icon(Icons.Outlined.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("查看复核记录", maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                Button(
                                    onClick = onStartNewTask,
                                    modifier = Modifier.weight(1f).height(44.dp),
                                    shape = RoundedCornerShape(8.dp),
                                ) {
                                    Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("新建采集", maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                        else -> Button(
                            onClick = when {
                                state.resultName != null -> onStartNewTask
                                writingExcel -> ({})
                                else -> onGenerate
                            },
                            enabled = when {
                                writingExcel -> false
                                state.resultName != null -> !state.isRunning
                                else -> state.images.isNotEmpty() && !state.isRunning && !state.isTestingConnection
                            },
                            modifier = Modifier.fillMaxWidth().height(44.dp),
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            if (state.isRunning && !writingExcel) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                            } else {
                                Icon(if (state.resultName != null) Icons.Outlined.Add else Icons.Outlined.PlayArrow, contentDescription = null, modifier = Modifier.size(17.dp))
                            }
                            Spacer(Modifier.width(5.dp))
                            Text(
                                when {
                                    writingExcel -> "生成中"
                                    state.isRunning -> "处理中"
                                    state.resultName != null -> "开始新一轮采集"
                                    else -> "开始识别"
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
    if (showDirectConfirmDialog) {
        val reviewCount = state.imageOutcomes.count(ReviewPolicy::requiresManualReview)
        AlertDialog(
            onDismissRequest = { showDirectConfirmDialog = false },
            title = { Text("仍按当前结果生成 Excel？") },
            text = {
                Text(
                    "当前有 $reviewCount 项待复核。你可以先进入现场复核，也可以确认无疑义，按当前识别结果继续生成交付文件。",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDirectConfirmDialog = false
                        onConfirmNoIssues()
                    },
                    shape = RoundedCornerShape(8.dp),
                ) { Text("确认无疑义并生成") }
            },
            dismissButton = {
                TextButton(onClick = { showDirectConfirmDialog = false }) { Text("返回复核") }
            },
        )
    }
}

@Composable
private fun RecognitionTaskSummaryCard(
    imageCount: Int,
    rackCount: Int,
    reviewCount: Int,
    writing: Boolean,
    delivered: Boolean,
) {
    val accent = when {
        writing -> Blue
        reviewCount > 0 -> Warning
        delivered -> Success
        else -> Cyan
    }
    val headline = when {
        writing -> "正在生成交付工作簿"
        reviewCount > 0 -> "识别完成，等待现场复核"
        delivered -> "本次交付已完成"
        else -> "识别完成，等待交付确认"
    }
    val detail = when {
        writing -> "正在整理三张交付表并校验文件完整性。"
        reviewCount > 0 -> "$reviewCount 项待复核；可逐柜修正，也可确认无疑义后交付。"
        delivered -> "原图与复核记录仍可查看；新建采集会清空当前队列。"
        else -> "未发现待复核项，可查看机柜图或确认生成 Excel。"
    }
    Surface(
        shape = RoundedCornerShape(9.dp),
        color = Color.White,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.38f)),
    ) {
        Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(8.dp), color = accent.copy(alpha = 0.12f)) {
                Icon(
                    imageVector = if (delivered) Icons.Outlined.CheckCircle else Icons.Outlined.Description,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.padding(9.dp).size(19.dp),
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(headline, style = MaterialTheme.typography.titleSmall, color = Navy)
                Spacer(Modifier.height(2.dp))
                Text(detail, style = MaterialTheme.typography.bodySmall, color = Muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(10.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text("$imageCount 张", style = MaterialTheme.typography.labelLarge, color = Navy)
                Text(
                    if (reviewCount > 0) "$reviewCount 项待复核" else "$rackCount 柜已识别",
                    style = MaterialTheme.typography.labelSmall,
                    color = accent,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun PhaseEntry(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val motionEnabled = rememberMotionEnabled()
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(tween(if (motionEnabled) 180 else 0)) +
            expandVertically(tween(if (motionEnabled) 220 else 0)),
        exit = fadeOut(tween(if (motionEnabled) 120 else 0)) +
            shrinkVertically(tween(if (motionEnabled) 120 else 0)),
    ) {
        content()
    }
}

@Composable
private fun ExportConfirmationCard(
    reviewCount: Int,
    onOpenReview: (() -> Unit)?,
    onConfirm: () -> Unit,
) {
    val hasReview = reviewCount > 0
    Surface(
        shape = RoundedCornerShape(9.dp),
        color = if (hasReview) Color(0xFFFFF5E7) else Color(0xFFEAF7F0),
        border = BorderStroke(1.dp, if (hasReview) Color(0xFFE8C78E) else Color(0xFFB7DFD2)),
    ) {
        Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = if (hasReview) Warning else Success, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(7.dp))
                Text("AI 识别已完成", style = MaterialTheme.typography.titleSmall, color = Navy)
            }
            Text(
                if (hasReview) "发现 $reviewCount 项待复核。可进入现场复核，也可确认无疑义后按当前结果交付。"
                else "未发现待复核项。确认后将开始整理并生成 Excel 文件。",
                style = MaterialTheme.typography.bodySmall,
                color = Ink,
            )
            if (onOpenReview != null) {
                OutlinedButton(
                    onClick = onOpenReview,
                    modifier = Modifier.fillMaxWidth().height(42.dp),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Icon(Icons.Outlined.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(5.dp))
                    Text(if (hasReview) "进入现场复核 · $reviewCount 项" else "查看机柜图")
                }
            }
            Button(onClick = onConfirm, modifier = Modifier.fillMaxWidth().height(42.dp), shape = RoundedCornerShape(8.dp)) {
                Icon(Icons.Outlined.Description, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(5.dp))
                Text(if (hasReview) "确认无疑义，生成 Excel" else "确认并生成 Excel")
            }
        }
    }
}

@Composable
private fun ExcelWritingStatusCard(
    ellipsis: String,
    progress: Float,
    targetMillis: Long,
) {
    val motionEnabled = rememberMotionEnabled()
    val writeProgress = animatedProgress(progress, "excelWriteProgress")
    Surface(shape = RoundedCornerShape(9.dp), color = Color.White, border = BorderStroke(1.dp, Line)) {
        Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Cyan)
                Spacer(Modifier.width(8.dp))
                Text("AI 已完成识别，正在按确认结果生成并校验三张 Excel 交付表，请稍候$ellipsis", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = Navy)
            }
            listOf("机房上架图", "设备明细", "统计分析").forEachIndexed { index, title ->
                ExcelSheetWriteRow(
                    index = index,
                    title = title,
                    progress = writeProgress,
                    motionEnabled = motionEnabled,
                )
            }
            LinearProgressIndicator(progress = { writeProgress }, modifier = Modifier.fillMaxWidth(), color = Cyan, trackColor = SurfaceTint)
            Text("正在完成模板写入与文件校验 · 预计 ${targetMillis / 1000} 秒", style = MaterialTheme.typography.labelSmall, color = Muted)
        }
    }
}

@Composable
private fun ExcelSheetWriteRow(index: Int, title: String, progress: Float, motionEnabled: Boolean) {
    // The writer reports one overall progress value, so each sheet owns an equal slice of it.
    val start = index / 3f
    val end = (index + 1) / 3f
    val done = progress >= end
    val active = !done && progress >= start
    val sliceFill = ((progress - start) / (end - start)).coerceIn(0f, 1f)
    val checkScale by animateFloatAsState(
        targetValue = if (done) 1f else 0f,
        animationSpec = if (motionEnabled) {
            spring(dampingRatio = 0.45f, stiffness = 500f)
        } else {
            tween(durationMillis = 0)
        },
        label = "excelSheetCheck$index",
    )
    val haptic = LocalHapticFeedback.current
    var wasDone by remember(index) { mutableStateOf(false) }
    LaunchedEffect(done) {
        if (done && !wasDone && motionEnabled) {
            // One compact acknowledgement per completed workbook sheet. It is deliberately
            // skipped when Android has reduced motion enabled.
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
        wasDone = done
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(18.dp), contentAlignment = Alignment.Center) {
            when {
                done -> Icon(
                    Icons.Outlined.CheckCircle,
                    contentDescription = null,
                    tint = Success,
                    modifier = Modifier.size(16.dp).graphicsLayer(scaleX = checkScale, scaleY = checkScale),
                )
                active -> CircularProgressIndicator(modifier = Modifier.size(13.dp), strokeWidth = 1.5.dp, color = Cyan)
                else -> Box(Modifier.size(6.dp).clip(RoundedCornerShape(99.dp)).background(Line))
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(
            title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = if (done || active) Navy else Muted,
        )
        Text(
            when {
                done -> "已写入"
                active -> "${(sliceFill * 100).toInt()}%"
                else -> "等待中"
            },
            style = MaterialTheme.typography.labelSmall,
            color = when {
                done -> Success
                active -> Cyan
                else -> Muted
            },
        )
    }
}

@Composable
private fun CurrentTaskEfficiencyCard(
    imageCount: Int,
    rackCount: Int,
    durationMillis: Long,
    metrics: TaskEfficiencyMetrics,
    compact: Boolean = false,
) {
    val motionEnabled = rememberMotionEnabled()
    var rolled by remember(metrics.totalSavedMillis) { mutableStateOf(false) }
    var rollFinished by remember(metrics.totalSavedMillis) { mutableStateOf(false) }
    LaunchedEffect(metrics.totalSavedMillis, motionEnabled) {
        rolled = true
        if (motionEnabled) delay(1_100L)
        rollFinished = true
    }
    val savedMillis by animateFloatAsState(
        targetValue = if (rolled) metrics.totalSavedMillis.toFloat() else 0f,
        animationSpec = tween(
            durationMillis = if (motionEnabled) 1100 else 0,
            easing = FastOutSlowInEasing,
        ),
        label = "savedMillisRoll",
    )
    val savedScale by animateFloatAsState(
        targetValue = if (rollFinished) 1f else 0.965f,
        animationSpec = if (motionEnabled) {
            spring(dampingRatio = 0.58f, stiffness = 380f)
        } else {
            tween(durationMillis = 0)
        },
        label = "savedMillisSettle",
    )
    Surface(
        shape = RoundedCornerShape(9.dp),
        color = Color(0xFFEAF7F0),
        border = BorderStroke(1.dp, Color(0xFFB7DFD2)),
    ) {
        Column(
            Modifier.padding(if (compact) 10.dp else 13.dp),
            verticalArrangement = Arrangement.spacedBy(if (compact) 5.dp else 7.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = Success, modifier = Modifier.size(if (compact) 16.dp else 18.dp))
                Spacer(Modifier.width(if (compact) 6.dp else 7.dp))
                Text("本次任务已完成", style = MaterialTheme.typography.titleSmall, color = Navy)
            }
            if (compact) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text("预计节省人工", style = MaterialTheme.typography.labelMedium, color = Muted)
                        Text("已扣除人工复核耗时", style = MaterialTheme.typography.labelSmall, color = Muted)
                    }
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            formatDuration(savedMillis.toLong()),
                            modifier = Modifier.graphicsLayer(scaleX = savedScale, scaleY = savedScale),
                            style = MaterialTheme.typography.titleLarge,
                            color = Success,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            if (metrics.totalSavedMillis >= 3_600_000L) "时:分:秒" else "分:秒",
                            modifier = Modifier.padding(bottom = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = Muted,
                        )
                    }
                }
                HorizontalDivider(color = Color(0xFFB7DFD2))
                Text(
                    "处理 ${imageCount} 张图片 · ${rackCount} 个机柜 · 任务耗时 ${formatDuration(durationMillis)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CompactEfficiencyEstimate(
                        label = "识别环节预计",
                        durationMillis = metrics.recognitionSavedMillis,
                        modifier = Modifier.weight(1f),
                    )
                    Box(
                        modifier = Modifier
                            .height(30.dp)
                            .width(1.dp)
                            .background(Color(0xFFB7DFD2)),
                    )
                    CompactEfficiencyEstimate(
                        label = "制表环节预计",
                        durationMillis = metrics.exportSavedMillis,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 10.dp),
                    )
                }
            } else {
                Text(
                    "处理图片：${imageCount} 张 · 识别机柜：${rackCount} 个 · 任务耗时：${formatDuration(durationMillis)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Ink,
                )
                HorizontalDivider(color = Color(0xFFB7DFD2))
                Text("预计节省人工", style = MaterialTheme.typography.labelMedium, color = Muted)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        // Monospace keeps the digit columns from jittering while the value rolls up.
                        formatDuration(savedMillis.toLong()),
                        modifier = Modifier.graphicsLayer(scaleX = savedScale, scaleY = savedScale, transformOrigin = TransformOrigin(0f, 0.5f)),
                        style = MaterialTheme.typography.headlineSmall,
                        fontSize = 34.sp,
                        lineHeight = 38.sp,
                        fontFamily = FontFamily.Monospace,
                        color = Success,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (metrics.totalSavedMillis >= 3_600_000L) "时:分:秒" else "分:秒",
                        modifier = Modifier.padding(bottom = 6.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = Muted,
                    )
                }
                EfficiencyMetricRow("识别环节预计", metrics.recognitionSavedMillis)
                EfficiencyMetricRow("制表环节预计", metrics.exportSavedMillis)
                EfficiencyMetricRow("人工复核耗时", metrics.manualReviewMillis, accent = Muted)
                Text("已按本次图片、机柜数量和实际耗时测算，并扣除人工复核时间。", style = MaterialTheme.typography.labelSmall, color = Muted)
            }
        }
    }
}

@Composable
private fun CompactEfficiencyEstimate(
    label: String,
    durationMillis: Long,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(formatDuration(durationMillis), style = MaterialTheme.typography.labelLarge, color = Navy, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun CaptureCommandStrip(onTakePhoto: () -> Unit, onPickImages: () -> Unit, enabled: Boolean) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth >= 520.dp) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                CaptureButton(onClick = onTakePhoto, enabled = enabled, modifier = Modifier.weight(1f).height(46.dp))
                GalleryButton(onClick = onPickImages, enabled = enabled, modifier = Modifier.weight(1f).height(46.dp))
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                CaptureButton(onClick = onTakePhoto, enabled = enabled, modifier = Modifier.fillMaxWidth().height(46.dp))
                GalleryButton(onClick = onPickImages, enabled = enabled, modifier = Modifier.fillMaxWidth().height(46.dp))
            }
        }
    }
}

@Composable
private fun RecognitionTimeline(state: RackExcelUiState) {
    val current = TaskPolicy.recognitionTimelineStep(
        isRunning = state.isRunning,
        hasResult = state.resultName != null,
        hasImages = state.images.isNotEmpty(),
        outcomeStates = state.imageOutcomes.map { it.state },
        exportPhase = state.exportPhase,
    )
    val motionEnabled = rememberMotionEnabled()
    val labels = listOf("采集", "质检", "识别", "修复", "交付")
    Surface(shape = RoundedCornerShape(9.dp), color = Color.White, border = BorderStroke(1.dp, Line)) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 12.dp), verticalAlignment = Alignment.Top) {
            labels.forEachIndexed { index, label ->
                TimelineStep(index = index, label = label, active = index <= current, current = index == current)
                if (index < labels.lastIndex) {
                    val fill by animateFloatAsState(
                        targetValue = if (index < current) 1f else 0f,
                        animationSpec = tween(
                            durationMillis = if (motionEnabled) 420 else 0,
                            easing = FastOutSlowInEasing,
                        ),
                        label = "timelineConnector$index",
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 3.dp, top = 7.dp, end = 3.dp)
                            .height(2.dp)
                            .clip(RoundedCornerShape(1.dp))
                            .background(Line),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(fill.coerceIn(0f, 1f))
                                .fillMaxHeight()
                                .background(Cyan),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RowScope.TimelineStep(index: Int, label: String, active: Boolean, current: Boolean) {
    val motionEnabled = rememberMotionEnabled()
    val nodeScale by animateFloatAsState(
        targetValue = if (current) 1f else 0.92f,
        animationSpec = if (motionEnabled) {
            spring(dampingRatio = 0.5f, stiffness = 420f)
        } else {
            tween(durationMillis = 0)
        },
        label = "timelineNodeScale$index",
    )
    val pulseTransition = rememberInfiniteTransition(label = "timelinePulse$index")
    val pulse by pulseTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "timelinePulseValue$index",
    )
    val haloVisible = current && motionEnabled
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(42.dp)) {
        Box(contentAlignment = Alignment.Center) {
            if (haloVisible) {
                // matchParentSize tracks the badge without feeding back into the row's layout,
                // so the ring can overshoot the badge bounds without shifting the timeline.
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .graphicsLayer(
                            scaleX = 1f + pulse * 0.85f,
                            scaleY = 1f + pulse * 0.85f,
                            alpha = (1f - pulse) * 0.38f,
                        )
                        .clip(RoundedCornerShape(99.dp))
                        .background(Cyan),
                )
            }
            Surface(
                modifier = Modifier.graphicsLayer(scaleX = nodeScale, scaleY = nodeScale),
                shape = RoundedCornerShape(99.dp),
                color = when {
                    current -> Navy
                    active -> Color(0xFFE4F3F0)
                    else -> SurfaceTint
                },
            ) {
                Text(
                    (index + 1).toString(),
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = when {
                        current -> Color.White
                        active -> Cyan
                        else -> Muted
                    },
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = if (active) Navy else Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun AlarmCenterPage(
    state: RackExcelUiState,
    onOpenAlarm: (String) -> Unit,
    onSeedDemo: () -> Unit,
    onInject: () -> Unit,
    onRefresh: () -> Unit,
    onGoRecognition: () -> Unit,
) {
    var showHandled by rememberSaveable { mutableStateOf(false) }
    val active = if (state.racks.isEmpty()) emptyList() else state.alarms.filter { it.status == AlarmStatus.UNHANDLED }
    val handled = if (state.racks.isEmpty()) emptyList() else state.alarms.filter { it.status == AlarmStatus.HANDLED }
    val visible = if (state.racks.isEmpty()) emptyList() else (if (showHandled) handled else active).sortedWith(
        compareBy<AlarmRecord> { alarmSeverityRank(it.severity) }.thenByDescending { it.occurredAtMillis },
    )
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Surface(shape = RoundedCornerShape(9.dp), color = Navy) {
                Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("告警联动", style = MaterialTheme.typography.titleLarge, color = Color.White)
                            Text(
                                if (state.racks.isEmpty()) "等待本次上架图后进行柜号与 U 位定位" else "本次已识别 ${state.racks.size} 个机柜 · 未处理 ${active.size} 条",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFD6E8F4),
                            )
                        }
                        Text(
                            state.alarmSettings.sourceMode.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFB8D5E9),
                        )
                    }
                    if (state.racks.isNotEmpty()) {
                        Text(
                            "已定位 ${state.alarmMatches.count { it is AlarmMatchResult.Matched }} 台 · 未匹配 ${state.alarmMatches.count { it is AlarmMatchResult.Unmatched }} 条",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFB8D5E9),
                        )
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                FilterChip(
                    selected = !showHandled,
                    onClick = { showHandled = false },
                    label = { Text("待处理 ${active.size}") },
                )
                FilterChip(
                    selected = showHandled,
                    onClick = { showHandled = true },
                    label = { Text("已处理 ${handled.size}") },
                )
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onRefresh, contentPadding = PaddingValues(horizontal = 4.dp)) { Text("刷新") }
            }
        }
        if (state.racks.isEmpty()) {
            item {
                EmptyCard("尚无可定位的告警", "请先完成一次机柜照片识别；告警不会引用固定柜号或旧任务设备。")
            }
            item {
                OutlinedButton(onClick = onGoRecognition, modifier = Modifier.fillMaxWidth().height(42.dp), shape = RoundedCornerShape(8.dp)) {
                    Icon(Icons.Outlined.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("去智能识别")
                }
            }
        } else if (state.alarmSettings.sourceMode == AlarmSourceMode.MOCK) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    OutlinedButton(onClick = onSeedDemo, modifier = Modifier.weight(1f).height(40.dp), shape = RoundedCornerShape(8.dp)) {
                        Text("按本次设备生成")
                    }
                    OutlinedButton(onClick = onInject, modifier = Modifier.weight(1f).height(40.dp), shape = RoundedCornerShape(8.dp)) {
                        Text("注入一条告警")
                    }
                }
            }
        }
        if (visible.isEmpty() && state.racks.isNotEmpty()) {
            item { EmptyCard(if (showHandled) "暂无已处理告警" else "暂无未处理告警", "可点击上方按钮载入当前任务的模拟告警。") }
        }
        items(visible, key = { it.alarmId }) { alarm ->
            val match = state.alarmMatches.firstOrNull { it.alarm.alarmId == alarm.alarmId }
            AlarmListRow(alarm = alarm, match = match, onClick = { onOpenAlarm(alarm.alarmId) })
        }
        item {
            Text(
                "匹配依据来自设备名称、管理 IP、资产编号中的已配置字段；未匹配告警用于提示台账缺口。",
                style = MaterialTheme.typography.labelSmall,
                color = Muted,
            )
        }
    }
}

@Composable
private fun AlarmListRow(
    alarm: AlarmRecord,
    match: AlarmMatchResult?,
    onClick: () -> Unit,
) {
    val matched = match as? AlarmMatchResult.Matched
    val unmatched = match as? AlarmMatchResult.Unmatched
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(9.dp),
        color = Color.White,
        border = BorderStroke(1.dp, if (alarm.status == AlarmStatus.HANDLED) Line else alarmSeverityColor(alarm.severity).copy(alpha = 0.55f)),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
            Box(Modifier.padding(top = 4.dp).size(9.dp).clip(RoundedCornerShape(99.dp)).background(alarmSeverityColor(alarm.severity)))
            Spacer(Modifier.width(9.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(alarm.severity.label, style = MaterialTheme.typography.labelLarge, color = alarmSeverityColor(alarm.severity))
                    Spacer(Modifier.width(6.dp))
                    Text(alarm.alarmId, style = MaterialTheme.typography.labelSmall, color = Muted)
                    Spacer(Modifier.weight(1f))
                    Text(alarm.status.label, style = MaterialTheme.typography.labelSmall, color = if (alarm.status == AlarmStatus.HANDLED) Success else Warning)
                }
                Text(alarm.description, style = MaterialTheme.typography.bodyMedium, color = Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (matched != null) {
                    Text(
                        "已匹配 · ${matched.coordinate.cabinetId} · U${matched.coordinate.upperU}${if (matched.coordinate.heightU > 1) "-U${matched.coordinate.bottomU}" else ""} · ${matched.coordinate.type}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Success,
                    )
                } else if (unmatched != null) {
                    Text(unmatched.reason, style = MaterialTheme.typography.labelSmall, color = Warning, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(unmatched.suggestion, style = MaterialTheme.typography.labelSmall, color = Muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                } else {
                    Text("等待本次上架图后进行定位", style = MaterialTheme.typography.labelSmall, color = Muted)
                }
                Text(formatCompactTime(alarm.occurredAtMillis), style = MaterialTheme.typography.labelSmall, color = Muted)
            }
            Icon(Icons.Outlined.PlayArrow, contentDescription = "打开告警复核", tint = Blue, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun AlarmReviewPage(
    alarm: AlarmRecord,
    match: AlarmMatchResult?,
    racks: List<Rack>,
    reviewer: String,
    error: String? = null,
    onDecision: (AlarmReviewDecision, String) -> Unit,
    onNext: () -> Unit,
    hasNext: Boolean,
    onBack: () -> Unit,
) {
    val matched = match as? AlarmMatchResult.Matched
    var note by rememberSaveable(alarm.alarmId) { mutableStateOf("") }
    var submitted by rememberSaveable(alarm.alarmId) { mutableStateOf(alarm.processing.isNotEmpty()) }
    val latestRecord = alarm.processing.lastOrNull()
    val targetRack = matched?.coordinate?.let { coordinate -> racks.firstOrNull { it.cabinetId == coordinate.cabinetId } }
    val highlights = targetRack?.let { rack ->
        listOf(
            RackAlarmHighlight(
                alarmId = alarm.alarmId,
                severity = alarm.severity,
                coordinate = matched.coordinate,
            ),
        )
    }.orEmpty()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Surface(shape = RoundedCornerShape(9.dp), color = alarmSeverityColor(alarm.severity).copy(alpha = 0.12f), border = BorderStroke(1.dp, alarmSeverityColor(alarm.severity).copy(alpha = 0.55f))) {
                Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${alarm.severity.label}告警", style = MaterialTheme.typography.titleLarge, color = alarmSeverityColor(alarm.severity))
                        Spacer(Modifier.weight(1f))
                        Text(alarm.status.label, style = MaterialTheme.typography.labelMedium, color = if (alarm.status == AlarmStatus.HANDLED) Success else Warning)
                    }
                    Text(alarm.description, style = MaterialTheme.typography.bodyLarge, color = Ink)
                    if (matched != null) {
                        Text(
                            "匹配结果：${matched.coordinate.cabinetId} · U${matched.coordinate.upperU}${if (matched.coordinate.heightU > 1) "-U${matched.coordinate.bottomU}" else ""} · ${matched.coordinate.type}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Success,
                        )
                        Text("关联主键：${matched.matchedFields.joinToString("、") { it.label }}", style = MaterialTheme.typography.labelSmall, color = Muted)
                    } else {
                        val text = (match as? AlarmMatchResult.Unmatched)?.reason ?: "等待本次识别结果后定位"
                        Text(text, style = MaterialTheme.typography.bodyMedium, color = Warning)
                        Text((match as? AlarmMatchResult.Unmatched)?.suggestion ?: "建议先完成机柜识别。", style = MaterialTheme.typography.labelSmall, color = Muted)
                    }
                }
            }
        }
        if (targetRack != null && matched != null) {
            item {
                RackOccupancyDiagram(
                    rack = targetRack,
                    title = "告警设备机架定位",
                    selectedDeviceIndex = matched.coordinate.deviceIndex,
                    alarmHighlights = highlights,
                    onAlarmClick = null,
                )
            }
        }
        item {
            Text("这台设备现场是什么情况？", style = MaterialTheme.typography.titleMedium, color = Navy)
            Text("选择一个判定后，结果会停留在本题；由你主动点击下一条告警。", style = MaterialTheme.typography.bodySmall, color = Muted)
        }
        if (!submitted && alarm.status != AlarmStatus.HANDLED) {
            item {
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("现场说明（可选）") },
                    minLines = 2,
                    maxLines = 4,
                    textStyle = MaterialTheme.typography.bodyMedium,
                )
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AlarmDecisionButton("设备不存在", "从当前上架图与台账移除，关闭无效告警", Danger) {
                        submitted = true
                        onDecision(AlarmReviewDecision.DEVICE_NOT_PRESENT, note)
                    }
                    AlarmDecisionButton("设备故障", "确认硬件故障，生成处置记录并留存当前机柜证据", Warning) {
                        submitted = true
                        onDecision(AlarmReviewDecision.DEVICE_FAULT, note)
                    }
                    AlarmDecisionButton("需进一步处理", "创建线缆、端口或配置核查待办，告警保持未处理", Blue) {
                        submitted = true
                        onDecision(AlarmReviewDecision.NEEDS_FURTHER_PROCESSING, note)
                    }
                }
            }
        } else {
            item {
                AlarmDecisionResultCard(alarm = alarm, latestRecord = latestRecord, reviewer = reviewer)
            }
            error?.let { message -> item { ErrorCard(message) } }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f).height(44.dp), shape = RoundedCornerShape(8.dp)) {
                        Text("返回告警列表")
                    }
                    if (hasNext) {
                        Button(onClick = onNext, modifier = Modifier.weight(1f).height(44.dp), shape = RoundedCornerShape(8.dp)) {
                            Text("下一条告警")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AlarmDecisionButton(title: String, detail: String, accent: Color, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(58.dp),
        shape = RoundedCornerShape(9.dp),
        border = BorderStroke(1.5.dp, accent.copy(alpha = 0.65f)),
    ) {
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = accent)
            Text(detail, style = MaterialTheme.typography.labelSmall, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.Outlined.PlayArrow, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun AlarmDecisionResultCard(
    alarm: AlarmRecord,
    latestRecord: com.rackexcel.mobile.alarm.AlarmProcessingRecord?,
    reviewer: String,
) {
    val record = latestRecord
    Surface(shape = RoundedCornerShape(9.dp), color = Color(0xFFEAF7F0), border = BorderStroke(1.dp, Color(0xFFB7DFD2))) {
        Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("本题判定已记录", style = MaterialTheme.typography.titleSmall, color = Success)
            Text(record?.decision?.label ?: "已处理", style = MaterialTheme.typography.titleMedium, color = Navy)
            Text("告警状态：${alarm.status.label} · 复核人：${record?.reviewer ?: reviewer}", style = MaterialTheme.typography.bodySmall, color = Ink)
            record?.note?.takeIf { it.isNotBlank() }?.let { Text("现场说明：$it", style = MaterialTheme.typography.bodySmall, color = Muted) }
            record?.todoText?.takeIf { it.isNotBlank() }?.let { Text("待办：$it", style = MaterialTheme.typography.bodySmall, color = Warning) }
            Text("记录时间：${record?.reviewedAtMillis?.let(::formatCompactTime) ?: "刚刚"}", style = MaterialTheme.typography.labelSmall, color = Muted)
            if (record?.evidencePhotoPaths?.isNotEmpty() == true) {
                Text("已留存 ${record.evidencePhotoPaths.size} 张证据照片", style = MaterialTheme.typography.labelSmall, color = Success)
            }
            Text("当前页面保持不跳题；点击“下一条告警”后才继续。", style = MaterialTheme.typography.labelSmall, color = Muted)
        }
    }
}

@Composable
private fun TaskCenterPage(
    state: RackExcelUiState,
    onRetryFailed: () -> Unit,
    onRetryImage: (String) -> Unit,
    onOpenReview: (ImageOutcome) -> Unit,
    onClearHistory: () -> Unit,
    onDeleteHistory: (String) -> Unit,
    onOpenHistory: (TaskHistoryItem) -> Unit,
    onShareHistory: (TaskHistoryItem) -> Unit,
    onOpenHistoryDetail: (TaskHistoryItem) -> Unit,
) {
    var filterName by rememberSaveable { mutableStateOf(TaskFilter.ALL.name) }
    var taskCenterSection by rememberSaveable { mutableStateOf("CURRENT") }
    var showClearHistoryDialog by rememberSaveable { mutableStateOf(false) }
    var pendingHistoryDeleteId by rememberSaveable { mutableStateOf<String?>(null) }
    val filter = TaskFilter.entries.firstOrNull { it.name == filterName } ?: TaskFilter.ALL
    val visibleOutcomes = when (filter) {
        TaskFilter.ALL -> state.imageOutcomes
        TaskFilter.ACTIVE -> state.imageOutcomes.filter {
            it.state == ImageProcessingState.QUEUED || it.state == ImageProcessingState.ANALYZING || it.state == ImageProcessingState.REPAIRING
        }
        TaskFilter.REVIEW -> state.imageOutcomes.filter(ReviewPolicy::requiresManualReview)
        TaskFilter.RISK -> state.imageOutcomes.filter { it.rack?.riskCandidates?.isNotEmpty() == true }
        TaskFilter.DONE -> state.imageOutcomes.filter { it.state == ImageProcessingState.COMPLETED }
    }
    val failed = state.imageOutcomes.count { it.state == ImageProcessingState.FAILED }
    val completed = state.imageOutcomes.count { it.rack != null }
    val review = state.imageOutcomes.count(ReviewPolicy::requiresManualReview)
    val historicalSavedMillis = state.taskHistory.sumOf { it.efficiency.totalSavedMillis.coerceAtLeast(0L) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { SectionTitle("任务中心", "查看本次识别、待复核线索和已交付文件") }
        item {
            TaskCenterSectionSelector(
                selected = taskCenterSection,
                onSelected = { taskCenterSection = it },
                currentCount = state.imageOutcomes.size,
                historyCount = state.taskHistory.size,
            )
        }
        if (taskCenterSection == "CURRENT" && state.imageOutcomes.isNotEmpty()) {
            item { TaskSummaryRail(total = state.imageOutcomes.size, completed = completed, review = review, racks = state.racks.size) }
        }
        if (taskCenterSection == "HISTORY" && state.taskHistory.isNotEmpty()) {
            item {
                SavedTimeSummaryCard(
                    savedMillis = historicalSavedMillis,
                    taskCount = state.taskHistory.count { it.efficiency.totalSavedMillis > 0L },
                )
            }
        }
        if (taskCenterSection == "CURRENT" && state.imageOutcomes.isNotEmpty()) {
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    items(TaskFilter.entries.toList()) { option ->
                        FilterChip(
                            selected = filter == option,
                            onClick = { filterName = option.name },
                            label = { Text(option.label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = if (option == TaskFilter.REVIEW || option == TaskFilter.RISK) Color(0xFFFFF1DE) else SurfaceTint,
                                selectedLabelColor = if (option == TaskFilter.REVIEW || option == TaskFilter.RISK) Warning else Blue,
                            ),
                        )
                    }
                }
            }
            if (failed > 0) {
                item {
                    Surface(shape = RoundedCornerShape(9.dp), color = Color(0xFFFFF5E7), border = BorderStroke(1.dp, Color(0xFFE8C78E))) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("有图片需要补拍或重试", style = MaterialTheme.typography.titleSmall, color = Warning)
                                Text("${failed} 张图片处于修复失败状态。", style = MaterialTheme.typography.bodySmall, color = Ink)
                            }
                            OutlinedButton(onClick = onRetryFailed, enabled = !state.isRunning, shape = RoundedCornerShape(8.dp)) {
                                Icon(Icons.Outlined.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("重试")
                            }
                        }
                    }
                }
            }
            if (filter == TaskFilter.REVIEW && visibleOutcomes.isNotEmpty()) {
                item { ReviewQueueBanner(visibleOutcomes.size) }
            }
            if (visibleOutcomes.isEmpty()) {
                item {
                    val detail = if (filter == TaskFilter.REVIEW) {
                        "本次没有需要人工核对的柜号、U 位边界或补拍图片。"
                    } else if (filter == TaskFilter.RISK) {
                        "当前识别结果没有附带照片风险线索。"
                    } else {
                        "切换筛选条件查看其他图片状态。"
                    }
                    EmptyCard("当前筛选暂无任务", detail)
                }
            }
            itemsIndexed(visibleOutcomes, key = { _, outcome -> outcome.imageId }) { index, outcome ->
                ImageOutcomeCard(
                    outcome = outcome,
                    displayName = outcome.displayImageAlias(index + 1),
                    onOpenReview = outcome.rack?.let { { onOpenReview(outcome) } },
                    onRetry = if (outcome.state == ImageProcessingState.FAILED) {
                        { onRetryImage(outcome.imageId) }
                    } else {
                        null
                    },
                )
            }
        } else if (taskCenterSection == "CURRENT") {
            item {
                EmptyCard("暂无处理中的任务", "从“智能识别”上传机柜图片后，这里会展示逐图状态、自动修复和复核线索。")
            }
        }
        if (taskCenterSection == "HISTORY") item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text("历史任务", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("本机持续保存全部任务记录。", style = MaterialTheme.typography.bodySmall, color = Muted)
                }
                if (state.taskHistory.isNotEmpty()) {
                    TextButton(
                        onClick = { showClearHistoryDialog = true },
                        enabled = !state.isRunning,
                    ) { Text("清理记录") }
                }
            }
        }
        if (taskCenterSection == "HISTORY" && state.taskHistory.isEmpty()) {
            item { EmptyCard("暂无历史记录", "识别完成后将在这里保留柜号、图片数量和文件入口。") }
        } else if (taskCenterSection == "HISTORY") {
            items(state.taskHistory, key = { it.taskId }) { item ->
                HistoryCard(
                    item = item,
                    onDetail = { onOpenHistoryDetail(item) },
                    onDelete = { pendingHistoryDeleteId = item.taskId },
                    deletionEnabled = !state.isRunning,
                )
            }
        }
    }
    if (showClearHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showClearHistoryDialog = false },
            title = { Text("清理全部历史任务？") },
            text = {
                Text(
                    "是否进行清理？此操作会导致所有历史记录和本地复核图片丢失。已保存到下载目录的 Excel 文件保留。",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showClearHistoryDialog = false
                        onClearHistory()
                    },
                    enabled = !state.isRunning,
                    colors = ButtonDefaults.buttonColors(containerColor = Danger),
                ) { Text("确认清理") }
            },
            dismissButton = {
                TextButton(onClick = { showClearHistoryDialog = false }) { Text("取消") }
            },
        )
    }
    pendingHistoryDeleteId?.let { taskId ->
        val item = state.taskHistory.firstOrNull { it.taskId == taskId }
        if (item == null) {
            LaunchedEffect(taskId) { pendingHistoryDeleteId = null }
        } else {
            AlertDialog(
                onDismissRequest = { pendingHistoryDeleteId = null },
                title = { Text("删除此历史任务？") },
                text = {
                    Text(
                        "${item.displayTaskName()} 的任务记录和本地复核图片将被移除。已保存到下载目录的 Excel 文件保留。",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            pendingHistoryDeleteId = null
                            onDeleteHistory(taskId)
                        },
                        enabled = !state.isRunning,
                        colors = ButtonDefaults.buttonColors(containerColor = Danger),
                    ) { Text("确认删除") }
                },
                dismissButton = {
                    TextButton(onClick = { pendingHistoryDeleteId = null }) { Text("取消") }
                },
            )
        }
    }
}

@Composable
private fun TaskSummaryRail(total: Int, completed: Int, review: Int, racks: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        MetricCard("图片", total.toString(), "本次采集", Modifier.weight(1f))
        MetricCard("机柜", racks.toString(), "已识别", Modifier.weight(1f))
        MetricCard("结果", completed.toString(), if (review > 0) "待复核 $review" else "已完成", Modifier.weight(1f))
    }
}

@Composable
private fun TaskCenterSectionSelector(
    selected: String,
    onSelected: (String) -> Unit,
    currentCount: Int,
    historyCount: Int,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TaskCenterSectionButton(
            label = "本次任务",
            count = currentCount,
            selected = selected == "CURRENT",
            onClick = { onSelected("CURRENT") },
            modifier = Modifier.weight(1f),
        )
        TaskCenterSectionButton(
            label = "历史记录",
            count = historyCount,
            selected = selected == "HISTORY",
            onClick = { onSelected("HISTORY") },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun TaskCenterSectionButton(
    label: String,
    count: Int,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = if (selected) Navy else Color.White,
        border = BorderStroke(1.dp, if (selected) Navy else Line),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = if (selected) Color.White else Navy)
            Spacer(Modifier.width(5.dp))
            Text("$count", style = MaterialTheme.typography.labelSmall, color = if (selected) Color(0xFFB8D5E9) else Muted)
        }
    }
}

@Composable
private fun SavedTimeSummaryCard(savedMillis: Long, taskCount: Int) {
    Surface(
        shape = RoundedCornerShape(9.dp),
        color = Color(0xFFEAF7F0),
        border = BorderStroke(1.dp, Color(0xFFB7DFD2)),
    ) {
        Row(Modifier.padding(horizontal = 13.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(8.dp), color = Color.White.copy(alpha = 0.72f)) {
                Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = Success, modifier = Modifier.padding(8.dp).size(18.dp))
            }
            Spacer(Modifier.width(9.dp))
            Column(Modifier.weight(1f)) {
                Text("历史累计节省人工", style = MaterialTheme.typography.labelMedium, color = Muted)
                Text(formatDuration(savedMillis), style = MaterialTheme.typography.titleLarge, color = Success)
            }
            Text("$taskCount 个已交付任务", style = MaterialTheme.typography.labelSmall, color = Muted)
        }
    }
}

@Composable
private fun ReviewQueueBanner(count: Int) {
    Surface(
        shape = RoundedCornerShape(9.dp),
        color = Color(0xFFFFF5E7),
        border = BorderStroke(1.dp, Color(0xFFE8C78E)),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(7.dp), color = Color(0xFFFFE5B6)) {
                Icon(
                    Icons.Outlined.Info,
                    contentDescription = null,
                    tint = Warning,
                    modifier = Modifier.padding(7.dp).size(17.dp),
                )
            }
            Spacer(Modifier.width(9.dp))
            Column(Modifier.weight(1f)) {
                Text("待复核清单 · $count 张图片", style = MaterialTheme.typography.titleSmall, color = Warning)
                Text("下方逐图展示柜号、具体待核对原因与照片证据。", style = MaterialTheme.typography.bodySmall, color = Ink)
            }
        }
    }
}

@Composable
private fun SettingsPage(
    state: RackExcelUiState,
    view: SettingsView,
    onOpenView: (SettingsView) -> Unit,
    onModelProfileActivate: (String) -> Unit,
    onCreateModelProfile: () -> Unit,
    onDeleteModelProfile: () -> Unit,
    onModelProfileNameChange: (String) -> Unit,
    onUrlChange: (String) -> Unit,
    onModelChange: (String) -> Unit,
    onKeyChange: (String) -> Unit,
    onSaveConfig: () -> Unit,
    onTestConnection: () -> Unit,
    onActivatePrompt: (String) -> Unit,
    onPromptNameChange: (String) -> Unit,
    onPromptVersionChange: (String) -> Unit,
    onPromptContentChange: (String) -> Unit,
    onSavePrompt: () -> Unit,
    onDuplicatePrompt: () -> Unit,
    onResetPrompt: () -> Unit,
    onDeletePrompt: () -> Unit,
    onPrefixChange: (String) -> Unit,
    onAutoSaveChange: (Boolean) -> Unit,
    onRetainImagesChange: (Boolean) -> Unit,
    onSaveSettings: () -> Unit,
    onStartComputerScan: () -> Unit,
    onPairComputer: (String) -> Unit,
    onCheckComputer: () -> Unit,
    onDisconnectComputer: () -> Unit,
    onAlarmSourceModeChange: (AlarmSourceMode) -> Unit,
    onAlarmDemoModeChange: (Boolean) -> Unit,
    onAlarmSoundChange: (Boolean) -> Unit,
    onAlarmVibrationChange: (Boolean) -> Unit,
    onAlarmReviewerChange: (String) -> Unit,
    onAlarmMatchFieldChange: (AlarmIdentityField, Boolean) -> Unit,
    onAlarmMatchModeChange: (AlarmMatchMode) -> Unit,
    onImportAlarmJson: () -> Unit,
    onGatewayConfigChange: (NetworkGatewayConfig) -> Unit,
    onGatewaySave: () -> Unit,
    onGatewayTest: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    when (view) {
        SettingsView.HUB -> SettingsHubPage(
            state = state,
            onOpenView = onOpenView,
            onOpenAbout = onOpenAbout,
        )

        SettingsView.COMPUTER -> ConnectedComputerSettingsPage(
            state = state,
            onStartScan = onStartComputerScan,
            onPair = onPairComputer,
            onCheck = onCheckComputer,
            onDisconnect = onDisconnectComputer,
        )

        SettingsView.MODEL -> ModelSettingsPage(
            state = state,
            onModelProfileActivate = onModelProfileActivate,
            onCreateModelProfile = onCreateModelProfile,
            onDeleteModelProfile = onDeleteModelProfile,
            onModelProfileNameChange = onModelProfileNameChange,
            onUrlChange = onUrlChange,
            onModelChange = onModelChange,
            onKeyChange = onKeyChange,
            onSaveConfig = onSaveConfig,
            onTestConnection = onTestConnection,
        )

        SettingsView.PROMPT -> PromptSettingsPage(
            state = state,
            onActivatePrompt = onActivatePrompt,
            onPromptNameChange = onPromptNameChange,
            onPromptVersionChange = onPromptVersionChange,
            onPromptContentChange = onPromptContentChange,
            onSavePrompt = onSavePrompt,
            onDuplicatePrompt = onDuplicatePrompt,
            onResetPrompt = onResetPrompt,
            onDeletePrompt = onDeletePrompt,
        )

        SettingsView.FILE -> FileDeliverySettingsPage(
            state = state,
            onPrefixChange = onPrefixChange,
            onAutoSaveChange = onAutoSaveChange,
            onRetainImagesChange = onRetainImagesChange,
            onSaveSettings = onSaveSettings,
        )

        SettingsView.ALARM -> AlarmSettingsPage(
            state = state,
            onSourceModeChange = onAlarmSourceModeChange,
            onDemoModeChange = onAlarmDemoModeChange,
            onSoundChange = onAlarmSoundChange,
            onVibrationChange = onAlarmVibrationChange,
            onReviewerChange = onAlarmReviewerChange,
            onMatchFieldChange = onAlarmMatchFieldChange,
            onMatchModeChange = onAlarmMatchModeChange,
            onImportJson = onImportAlarmJson,
            onGatewayConfigChange = onGatewayConfigChange,
            onGatewaySave = onGatewaySave,
            onGatewayTest = onGatewayTest,
        )
    }
}

@Composable
private fun SettingsHubPage(
    state: RackExcelUiState,
    onOpenView: (SettingsView) -> Unit,
    onOpenAbout: () -> Unit,
) {
    val activeModel = state.modelProfiles.firstOrNull { it.id == state.activeModelProfileId }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text(
                "管理本机识别引擎、规则与文件交付。",
                style = MaterialTheme.typography.bodySmall,
                color = Muted,
            )
        }
        if (state.engineBanner.phase != EngineBannerPhase.IDLE) {
            item { EngineStatusBanner(state.engineBanner) }
        }
        item {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val settingsRows = listOf(
                    SettingsEntry(
                        code = "01",
                        title = "已连接电脑",
                        detail = state.connectedComputer?.let { computer ->
                            "${computer.receiverName} · ${computer.host}:${computer.port}"
                        } ?: "扫码配对后，一键发送 Excel 到电脑",
                    ),
                    SettingsEntry(
                        code = "02",
                        title = "模型配置",
                        detail = "${state.modelProfiles.size} 个已保存配置 · ${activeModel?.model ?: "等待填写模型"}",
                    ),
                    SettingsEntry(
                        code = "03",
                        title = "识别规则（高级设置）",
                        detail = "${state.activePrompt?.name ?: "通信机房上架图通用版"} · ${state.activePrompt?.version ?: "v2.5"}",
                    ),
                    SettingsEntry(
                        code = "04",
                        title = "文件交付",
                        detail = "下载/云枢智维 · 电脑接收 · 系统分享",
                    ),
                    SettingsEntry(
                        code = "05",
                        title = "告警联动",
                        detail = if (state.alarmSettings.sourceMode == AlarmSourceMode.NETWORK_MANAGER) {
                            "网管接口 · ${state.networkGateway.name} · ${state.networkGateway.lastCheckMessage}"
                        } else {
                            "${state.alarmSettings.sourceMode.label} · ${if (state.alarmSettings.demoMode) "演示模式已开" else "演示模式已关"}"
                        },
                    ),
                    SettingsEntry(
                        code = "06",
                        title = "关于云枢智维",
                        detail = "产品能力、实践数据与使用说明",
                    ),
                )
                if (maxWidth >= 600.dp) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            SettingsNavigatorCard(settingsRows[0]) { onOpenView(SettingsView.COMPUTER) }
                            SettingsNavigatorCard(settingsRows[2]) { onOpenView(SettingsView.PROMPT) }
                            SettingsNavigatorCard(settingsRows[4]) { onOpenView(SettingsView.ALARM) }
                            }
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            SettingsNavigatorCard(settingsRows[1]) { onOpenView(SettingsView.MODEL) }
                            SettingsNavigatorCard(settingsRows[3]) { onOpenView(SettingsView.FILE) }
                            SettingsNavigatorCard(settingsRows[5], onClick = onOpenAbout)
                            }
                    }
                } else {
                    SettingsListSurface {
                        SettingsNavigatorRow(settingsRows[0]) { onOpenView(SettingsView.COMPUTER) }
                        HorizontalDivider(color = Line)
                        SettingsNavigatorRow(settingsRows[1]) { onOpenView(SettingsView.MODEL) }
                        HorizontalDivider(color = Line)
                        SettingsNavigatorRow(settingsRows[2]) { onOpenView(SettingsView.PROMPT) }
                        HorizontalDivider(color = Line)
                        SettingsNavigatorRow(settingsRows[3]) { onOpenView(SettingsView.FILE) }
                        HorizontalDivider(color = Line)
                        SettingsNavigatorRow(settingsRows[4]) { onOpenView(SettingsView.ALARM) }
                        HorizontalDivider(color = Line)
                        SettingsNavigatorRow(settingsRows[5], onClick = onOpenAbout)
                    }
                }
            }
        }
        item {
            Column(Modifier.fillMaxWidth()) {
                HorizontalDivider(color = Line)
                Spacer(Modifier.height(9.dp))
                Text("字段写入规则", style = MaterialTheme.typography.labelLarge, color = Navy)
                Spacer(Modifier.height(2.dp))
                Text(
                    "有图像证据的柜号、U 位、设备类别、数量和使用率自动写入；其余字段保留空白，便于现场补录。",
                    style = MaterialTheme.typography.bodySmall,
                    color = Muted,
                )
            }
        }
        if (state.status.isNotBlank()) item { StatusStrip(state.status, state.error) }
        state.error?.let { error -> item { ErrorCard(error) } }
    }
}

@Composable
private fun ConnectedComputerSettingsPage(
    state: RackExcelUiState,
    onStartScan: () -> Unit,
    onPair: (String) -> Unit,
    onCheck: () -> Unit,
    onDisconnect: () -> Unit,
) {
    var pairingUri by rememberSaveable { mutableStateOf("") }
    val computer = state.connectedComputer
    val transfer = state.desktopTransfer
    var guideExpanded by rememberSaveable(computer?.receiverId) { mutableStateOf(computer == null) }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text(
                    "连接同一局域网内运行“云枢智维桌面接收器”的 Windows 电脑，识别结果可直接交付到指定目录。",
                    style = MaterialTheme.typography.bodySmall,
                    color = Muted,
                )
            }
            item {
                DesktopReceiverGuideCard(
                    expanded = guideExpanded,
                    isConnected = computer != null,
                    onExpandedChange = { guideExpanded = it },
                )
            }
            if (computer == null) {
                item {
                    SettingsEditorSurface {
                        Text("尚未连接电脑", style = MaterialTheme.typography.titleSmall, color = Navy)
                        Spacer(Modifier.height(3.dp))
                        Text("在电脑接收器中刷新二维码后，用手机扫描即可完成一次性配对。", style = MaterialTheme.typography.bodySmall, color = Muted)
                        Spacer(Modifier.height(5.dp))
                        Text(
                            "手机热点场景：电脑先连接手机热点，接收器点击“启用局域网访问”并刷新二维码，再用当前二维码扫码。热点重连后地址会变化，旧二维码不再使用。",
                            style = MaterialTheme.typography.labelSmall,
                            color = Warning,
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = onStartScan,
                            enabled = !transfer.isWorking,
                            modifier = Modifier.fillMaxWidth().height(44.dp),
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Icon(Icons.Outlined.CameraAlt, contentDescription = null, modifier = Modifier.size(17.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("扫码连接电脑")
                        }
                        Spacer(Modifier.height(10.dp))
                        HorizontalDivider(color = Line)
                        Spacer(Modifier.height(10.dp))
                        Text("粘贴连接信息", style = MaterialTheme.typography.labelLarge, color = Navy)
                        Spacer(Modifier.height(5.dp))
                        Text("适用于无相机权限或电脑端已复制连接信息的场景。", style = MaterialTheme.typography.bodySmall, color = Muted)
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = pairingUri,
                            onValueChange = { pairingUri = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("桌面接收器连接信息") },
                            placeholder = { Text("yunshu-receiver://pair?...", maxLines = 1) },
                            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            minLines = 2,
                            maxLines = 4,
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { onPair(pairingUri) },
                            enabled = pairingUri.isNotBlank() && !transfer.isWorking,
                            modifier = Modifier.fillMaxWidth().height(42.dp),
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Icon(Icons.Outlined.Link, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(5.dp))
                            Text("使用连接信息配对")
                        }
                    }
                }
            } else {
                item {
                    SettingsEditorSurface {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(shape = RoundedCornerShape(8.dp), color = Color(0xFFE8F5F0)) {
                                Icon(
                                    Icons.Outlined.Link,
                                    contentDescription = null,
                                    tint = Success,
                                    modifier = Modifier.padding(8.dp).size(18.dp),
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(computer.receiverName, style = MaterialTheme.typography.titleSmall, color = Navy)
                                Text("${computer.host}:${computer.port} · 协议 v${computer.protocolVersion}", style = MaterialTheme.typography.bodySmall, color = Muted)
                            }
                        }
                        Spacer(Modifier.height(11.dp))
                        HorizontalDivider(color = Line)
                        Spacer(Modifier.height(9.dp))
                        val healthText = when (state.desktopLastCheckSucceeded) {
                            true -> "接收器已就绪 · ${state.desktopLastCheckedAtMillis?.let(::formatCompactTime).orEmpty()}"
                            false -> "最近检测未通过 · ${state.desktopLastCheckedAtMillis?.let(::formatCompactTime).orEmpty()}"
                            null -> "尚未检测连通性"
                        }
                        Text(healthText, style = MaterialTheme.typography.bodySmall, color = if (state.desktopLastCheckSucceeded == false) Warning else Muted)
                        Text("配对有效至 ${formatCompactTime(computer.expiresAtEpochMillis)}", style = MaterialTheme.typography.labelSmall, color = Muted)
                    }
                }
                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = onCheck,
                            enabled = !transfer.isWorking,
                            modifier = Modifier.weight(1f).height(42.dp),
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Icon(Icons.Outlined.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("检测连接", maxLines = 1)
                        }
                        OutlinedButton(
                            onClick = onDisconnect,
                            enabled = !transfer.isWorking,
                            modifier = Modifier.weight(1f).height(42.dp),
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Icon(Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("解除连接", maxLines = 1)
                        }
                    }
                }
            }
            if (transfer.phase != DesktopTransferPhase.IDLE) {
                item { DesktopTransferStatusCard(transfer = transfer) }
            }
            if (state.status.isNotBlank()) item { StatusStrip(state.status, state.error) }
            state.error?.let { error -> item { ErrorCard(error) } }
        }
    }
}

@Composable
private fun DesktopReceiverGuideCard(
    expanded: Boolean,
    isConnected: Boolean,
    onExpandedChange: (Boolean) -> Unit,
) {
    val steps = listOf(
        "关闭手机 Wi-Fi，开启手机热点" to "保持移动数据或热点可用，让手机与电脑进入同一局域网。",
        "让 Windows 电脑连接手机热点" to "确认电脑网络已连接到本次手机热点后再启动接收器。",
        "打开云枢智维桌面接收器" to "双击 EXE，选择交付目录；首次按系统提示允许专用网络访问。",
        "刷新桌面端二维码" to "确认接收器显示已就绪，使用当前二维码完成本次配对。",
        "返回 App 扫码连接" to "点击“扫码连接电脑”，扫描桌面二维码；显示连接成功即完成。",
        "完成识别后发送到电脑" to "在 Excel 交付结果中点击“发送到已连接电脑”。",
        "在电脑端打开交付文件" to "在桌面接收器“最近交付”中双击对应记录，即可打开 Excel。",
    )
    SettingsEditorSurface {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(7.dp), color = SurfaceTint) {
                Icon(
                    Icons.Outlined.Link,
                    contentDescription = null,
                    tint = Blue,
                    modifier = Modifier.padding(7.dp).size(17.dp),
                )
            }
            Spacer(Modifier.width(9.dp))
            Column(Modifier.weight(1f)) {
                Text("电脑交付操作指引", style = MaterialTheme.typography.titleSmall, color = Navy)
                Text(
                    if (isConnected) "已配对，可查看热点重连与交付步骤" else "首次使用按 7 步完成配对与交付",
                    style = MaterialTheme.typography.bodySmall,
                    color = Muted,
                )
            }
            TextButton(
                onClick = { onExpandedChange(!expanded) },
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
            ) {
                Text(if (expanded) "收起" else "查看", style = MaterialTheme.typography.labelMedium)
            }
        }
        if (expanded) {
            Spacer(Modifier.height(11.dp))
            HorizontalDivider(color = Line)
            steps.forEachIndexed { index, (title, detail) ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Surface(shape = RoundedCornerShape(6.dp), color = if (index < 5) Navy else Color(0xFFE8F5F0)) {
                        Text(
                            (index + 1).toString(),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (index < 5) Color.White else Success,
                        )
                    }
                    Spacer(Modifier.width(9.dp))
                    Column(Modifier.weight(1f)) {
                        Text(title, style = MaterialTheme.typography.labelLarge, color = Ink)
                        Spacer(Modifier.height(2.dp))
                        Text(detail, style = MaterialTheme.typography.bodySmall, color = Muted)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Surface(shape = RoundedCornerShape(6.dp), color = Color(0xFFFFF5E7)) {
                Text(
                    "热点重新连接、桌面接收器重启或二维码刷新后，请重新扫码配对。",
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = Warning,
                )
            }
        }
    }
}

@Composable
private fun ModelSettingsPage(
    state: RackExcelUiState,
    onModelProfileActivate: (String) -> Unit,
    onCreateModelProfile: () -> Unit,
    onDeleteModelProfile: () -> Unit,
    onModelProfileNameChange: (String) -> Unit,
    onUrlChange: (String) -> Unit,
    onModelChange: (String) -> Unit,
    onKeyChange: (String) -> Unit,
    onSaveConfig: () -> Unit,
    onTestConnection: () -> Unit,
) {
    val activeModel = state.modelProfiles.firstOrNull { it.id == state.activeModelProfileId }
    val modelMessage = modelSettingsStatus(state)
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (state.engineBanner.phase != EngineBannerPhase.IDLE) item { EngineStatusBanner(state.engineBanner) }
            item {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Text("已保存配置", style = MaterialTheme.typography.titleSmall, color = Navy)
                        Text(
                            "启用后，新任务将使用此识别引擎。",
                            style = MaterialTheme.typography.bodySmall,
                            color = Muted,
                        )
                    }
                    TextButton(onClick = onCreateModelProfile, enabled = !state.isRunning) {
                        Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(3.dp))
                        Text("新增")
                    }
                }
            }
            item {
                SettingsListSurface {
                    state.modelProfiles.forEachIndexed { index, profile ->
                        ModelProfileRow(
                            profile = profile,
                            selected = profile.id == state.activeModelProfileId,
                            enabled = !state.isRunning,
                            onClick = { onModelProfileActivate(profile.id) },
                        )
                        if (index != state.modelProfiles.lastIndex) HorizontalDivider(color = Line)
                    }
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Text("编辑当前配置", style = MaterialTheme.typography.titleSmall, color = Navy)
                        Text(
                            "${activeModel?.name ?: "当前配置"} · 密钥仅在本机掩码保存。",
                            style = MaterialTheme.typography.bodySmall,
                            color = Muted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (activeModel?.isBuiltIn == false) {
                        TextButton(onClick = onDeleteModelProfile, enabled = !state.isRunning) {
                            Icon(Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(3.dp))
                            Text("删除")
                        }
                    }
                }
            }
            item {
                SettingsEditorSurface {
                    OutlinedTextField(
                        value = state.modelProfileName,
                        onValueChange = onModelProfileNameChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("配置名称") },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = state.url,
                        onValueChange = onUrlChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("接口 URL") },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium,
                        leadingIcon = { Icon(Icons.Outlined.Link, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    )
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = state.model,
                        onValueChange = onModelChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("模型名称") },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = state.apiKey,
                        onValueChange = onKeyChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("API Key") },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium,
                        visualTransformation = PasswordVisualTransformation(),
                        leadingIcon = { Icon(Icons.Outlined.Lock, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    )
                }
            }
            modelMessage.first?.let { status -> item { StatusStrip(status, null) } }
            modelMessage.second?.let { error -> item { ErrorCard(error) } }
        }
        SettingsBottomActionBar(
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            OutlinedButton(
                onClick = onTestConnection,
                enabled = !state.isRunning && !state.isTestingConnection,
                modifier = Modifier.weight(1f).height(44.dp),
                shape = RoundedCornerShape(8.dp),
            ) {
                if (state.isTestingConnection) {
                    CircularProgressIndicator(modifier = Modifier.size(15.dp), strokeWidth = 2.dp, color = Blue)
                } else {
                    Icon(Icons.Outlined.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                }
                Spacer(Modifier.width(5.dp))
                Text(if (state.isTestingConnection) "测试中" else "测试识别引擎", maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Button(
                onClick = onSaveConfig,
                enabled = !state.isRunning,
                modifier = Modifier.weight(1f).height(44.dp),
                shape = RoundedCornerShape(8.dp),
            ) {
                Icon(Icons.Outlined.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(5.dp))
                Text("保存")
            }
        }
    }
}

@Composable
private fun ModelProfileRow(
    profile: ModelProfile,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(99.dp))
                .background(if (selected) Cyan else Line),
        )
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) {
            Text(profile.name, style = MaterialTheme.typography.titleSmall, color = Navy, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                profile.model.ifBlank { "未填写模型名称" },
                style = MaterialTheme.typography.bodySmall,
                color = Muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            ModelHealthLine(profile)
        }
        if (selected) {
            Text("使用中", style = MaterialTheme.typography.labelSmall, color = Cyan, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ModelHealthLine(profile: ModelProfile) {
    val status = when (profile.lastCheckSucceeded) {
        true -> "测试正常"
        false -> "测试未通过"
        null -> "尚未测试识别引擎"
    }
    val checkedAt = profile.lastCheckedAtMillis?.let(::formatTime)
    val timing = buildList {
        profile.lastLatencyMillis?.let { add("${it}ms") }
        checkedAt?.let { add(it) }
    }.joinToString(" · ")
    val color = when (profile.lastCheckSucceeded) {
        true -> Success
        false -> Danger
        null -> Muted
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(6.dp).clip(RoundedCornerShape(99.dp)).background(color))
        Spacer(Modifier.width(5.dp))
        Column {
            Text(status, style = MaterialTheme.typography.labelSmall, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (timing.isNotBlank()) {
                Text(timing, style = MaterialTheme.typography.labelSmall, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun PromptSettingsPage(
    state: RackExcelUiState,
    onActivatePrompt: (String) -> Unit,
    onPromptNameChange: (String) -> Unit,
    onPromptVersionChange: (String) -> Unit,
    onPromptContentChange: (String) -> Unit,
    onSavePrompt: () -> Unit,
    onDuplicatePrompt: () -> Unit,
    onResetPrompt: () -> Unit,
    onDeletePrompt: () -> Unit,
) {
    var editorOpen by rememberSaveable { mutableStateOf(false) }
    var editMode by rememberSaveable { mutableStateOf(false) }
    val activePrompt = state.activePrompt

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text(
                "切换后，新任务将使用所选规则的完整快照。",
                style = MaterialTheme.typography.bodySmall,
                color = Muted,
            )
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text("已保存模板", style = MaterialTheme.typography.titleSmall, color = Navy)
                    Text("选择一套规则后即可直接识别。", style = MaterialTheme.typography.bodySmall, color = Muted)
                }
                TextButton(onClick = onDuplicatePrompt, enabled = !state.isRunning) {
                    Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(3.dp))
                    Text("复制")
                }
            }
        }
        item {
            SettingsListSurface {
                state.promptTemplates.forEachIndexed { index, template ->
                    PromptTemplateRow(
                        template = template,
                        selected = template.id == state.activePromptId,
                        enabled = !state.isRunning,
                        onClick = { onActivatePrompt(template.id) },
                    )
                    if (index != state.promptTemplates.lastIndex) HorizontalDivider(color = Line)
                }
            }
        }
        item {
            SettingsEditorSurface {
                Text("当前规则", style = MaterialTheme.typography.titleSmall, color = Navy)
                Spacer(Modifier.height(3.dp))
                Text(
                    "${activePrompt?.name ?: "未选择模板"} · ${activePrompt?.version ?: "未标记版本"}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "${state.promptEditorContent.length} 个字符 · 适用于后续新任务",
                    style = MaterialTheme.typography.bodySmall,
                    color = Muted,
                )
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = {
                        editMode = false
                        editorOpen = true
                    },
                    enabled = !state.isRunning,
                    modifier = Modifier.fillMaxWidth().height(42.dp),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Icon(Icons.Outlined.Description, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("查看 / 编辑规则")
                }
                if (activePrompt?.isBuiltIn == false) {
                    Spacer(Modifier.height(3.dp))
                    TextButton(onClick = onDeletePrompt, enabled = !state.isRunning, contentPadding = PaddingValues(0.dp)) {
                        Icon(Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(3.dp))
                        Text("删除当前模板")
                    }
                }
            }
        }
        if (state.status.isNotBlank()) item { StatusStrip(state.status, state.error) }
        state.error?.let { error -> item { ErrorCard(error) } }
    }

    if (editorOpen) {
        PromptEditorDialog(
            state = state,
            editMode = editMode,
            onEditModeChange = { editMode = it },
            onClose = { editorOpen = false },
            onNameChange = onPromptNameChange,
            onVersionChange = onPromptVersionChange,
            onContentChange = onPromptContentChange,
            onReset = onResetPrompt,
            onSave = {
                onSavePrompt()
                editorOpen = false
                editMode = false
            },
        )
    }
}

@Composable
private fun PromptTemplateRow(
    template: PromptTemplate,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(99.dp))
                .background(if (selected) Cyan else Line),
        )
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) {
            Text(template.name, style = MaterialTheme.typography.titleSmall, color = Navy, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${template.version} · ${template.content.length} 个字符 · ${if (template.isBuiltIn) "标准模板" else "自定义模板"}",
                style = MaterialTheme.typography.bodySmall,
                color = Muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (selected) {
            Text("使用中", style = MaterialTheme.typography.labelSmall, color = Cyan, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun PromptEditorDialog(
    state: RackExcelUiState,
    editMode: Boolean,
    onEditModeChange: (Boolean) -> Unit,
    onClose: () -> Unit,
    onNameChange: (String) -> Unit,
    onVersionChange: (String) -> Unit,
    onContentChange: (String) -> Unit,
    onReset: () -> Unit,
    onSave: () -> Unit,
) {
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = Canvas) {
            Box(Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxSize()) {
                    Surface(color = Color.White, border = BorderStroke(1.dp, Line)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconButton(onClick = onClose) {
                                Icon(Icons.Outlined.Close, contentDescription = "关闭规则编辑器", tint = Navy)
                            }
                            Column(Modifier.weight(1f)) {
                                Text("识别规则", style = MaterialTheme.typography.titleSmall, color = Navy)
                                Text(
                                    "${state.promptEditorName.ifBlank { "未命名模板"}} · ${state.promptEditorVersion.ifBlank { "未标记版本"}}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Muted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            TextButton(onClick = { onEditModeChange(!editMode) }) {
                                Text(if (editMode) "预览" else "编辑")
                            }
                        }
                    }
                    if (editMode) {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(start = 16.dp, top = 14.dp, end = 16.dp, bottom = 72.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            OutlinedTextField(
                                value = state.promptEditorName,
                                onValueChange = onNameChange,
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("模板名称") },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyMedium,
                            )
                            OutlinedTextField(
                                value = state.promptEditorVersion,
                                onValueChange = onVersionChange,
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("版本标识") },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                "${state.promptEditorContent.length} 个字符",
                                style = MaterialTheme.typography.labelSmall,
                                color = Muted,
                            )
                            OutlinedTextField(
                                value = state.promptEditorContent,
                                onValueChange = onContentChange,
                                modifier = Modifier.fillMaxWidth().weight(1f),
                                label = { Text("识别规则内容") },
                                textStyle = MaterialTheme.typography.bodyMedium.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 14.sp,
                                    lineHeight = 20.sp,
                                ),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                                minLines = 12,
                                maxLines = Int.MAX_VALUE,
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = 16.dp, top = 14.dp, end = 16.dp, bottom = 72.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            item {
                                Text(
                                    "${state.promptEditorContent.length} 个字符 · 本次修改仅作用于后续新任务。",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Muted,
                                )
                            }
                            item {
                                Surface(shape = RoundedCornerShape(8.dp), color = Color.White, border = BorderStroke(1.dp, Line)) {
                                    Text(
                                        state.promptEditorContent,
                                        modifier = Modifier.padding(13.dp),
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 14.sp,
                                            lineHeight = 20.sp,
                                        ),
                                        color = Ink,
                                    )
                                }
                            }
                        }
                    }
                }
                SettingsBottomActionBar(
                    modifier = Modifier.align(Alignment.BottomCenter),
                    compact = true,
                ) {
                    if (editMode) {
                        OutlinedButton(
                            onClick = onReset,
                            modifier = Modifier.weight(1f).height(42.dp),
                            shape = RoundedCornerShape(8.dp),
                        ) { Text("恢复") }
                        Button(
                            onClick = onSave,
                            modifier = Modifier.weight(1f).height(42.dp),
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Icon(Icons.Outlined.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(5.dp))
                            Text("保存并启用")
                        }
                    } else {
                        Button(
                            onClick = { onEditModeChange(true) },
                            modifier = Modifier.fillMaxWidth().height(42.dp),
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Icon(Icons.Outlined.Description, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(5.dp))
                            Text("编辑规则")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FileDeliverySettingsPage(
    state: RackExcelUiState,
    onPrefixChange: (String) -> Unit,
    onAutoSaveChange: (Boolean) -> Unit,
    onRetainImagesChange: (Boolean) -> Unit,
    onSaveSettings: () -> Unit,
) {
    var showStructure by rememberSaveable { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text(
                    "生成后可保存、分享或发送到电脑。",
                    style = MaterialTheme.typography.bodySmall,
                    color = Muted,
                )
            }
            item {
                SettingsEditorSurface {
                    Text("保存设置", style = MaterialTheme.typography.titleSmall, color = Navy)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = state.filePrefix,
                        onValueChange = onPrefixChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Excel 文件名前缀") },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(9.dp))
                    SwitchRow(
                        title = "完成后自动保存",
                        detail = "保存到手机的云枢智维文件夹。",
                        checked = state.autoSave,
                        onCheckedChange = onAutoSaveChange,
                    )
                    HorizontalDivider(Modifier.padding(vertical = 7.dp), color = Line)
                    SwitchRow(
                        title = "保留完整原始图片",
                        detail = "保留复核图；开启后额外保留完整原图。",
                        checked = state.retainOriginalImages,
                        onCheckedChange = onRetainImagesChange,
                    )
                }
            }
            item {
                TextButton(
                    onClick = { showStructure = !showStructure },
                    contentPadding = PaddingValues(0.dp),
                ) {
                    Icon(Icons.Outlined.Description, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(5.dp))
                    Text(if (showStructure) "收起交付结构" else "查看交付结构")
                }
            }
            if (showStructure) {
                item {
                    SettingsListSurface {
                        DeliveryStructureRow("表 1", "机房上架图", "柜号、U 位与设备名称")
                        HorizontalDivider(color = Line)
                        DeliveryStructureRow("表 2", "设备明细登记表", "可确认字段自动填入，其余保持空白")
                        HorizontalDivider(color = Line)
                        DeliveryStructureRow("表 3", "统计分析", "机柜、设备与空间使用情况")
                    }
                }
            }
            if (state.status == "文件与数据设置已保存。") {
                item { StatusStrip(state.status, null) }
            }
        }
        SettingsBottomActionBar(
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Button(
                onClick = onSaveSettings,
                modifier = Modifier.fillMaxWidth().height(44.dp),
                shape = RoundedCornerShape(8.dp),
            ) {
                Icon(Icons.Outlined.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(5.dp))
                Text("保存设置")
            }
        }
    }
}

@Composable
private fun AlarmSettingsPage(
    state: RackExcelUiState,
    onSourceModeChange: (AlarmSourceMode) -> Unit,
    onDemoModeChange: (Boolean) -> Unit,
    onSoundChange: (Boolean) -> Unit,
    onVibrationChange: (Boolean) -> Unit,
    onReviewerChange: (String) -> Unit,
    onMatchFieldChange: (AlarmIdentityField, Boolean) -> Unit,
    onMatchModeChange: (AlarmMatchMode) -> Unit,
    onImportJson: () -> Unit,
    onGatewayConfigChange: (NetworkGatewayConfig) -> Unit,
    onGatewaySave: () -> Unit,
    onGatewayTest: () -> Unit,
) {
    val settings = state.alarmSettings
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text(
                "告警先进入本地匹配层，再定位到本次上架图的柜号和 U 位。真实网管接口接入后无需更换页面。",
                style = MaterialTheme.typography.bodySmall,
                color = Muted,
            )
        }
        item {
            SettingsEditorSurface {
                Text("告警数据源", style = MaterialTheme.typography.titleSmall, color = Navy)
                Spacer(Modifier.height(3.dp))
                Text("Mock 用于离线演示；网管接口参数已预留，可按客户平台配置。", style = MaterialTheme.typography.bodySmall, color = Muted)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    AlarmSourceMode.entries.forEach { mode ->
                        FilterChip(
                            selected = settings.sourceMode == mode,
                            onClick = { onSourceModeChange(mode) },
                            label = { Text(mode.label) },
                        )
                    }
                }
                if (settings.sourceMode == AlarmSourceMode.NETWORK_MANAGER) {
                    Spacer(Modifier.height(7.dp))
                    Text(
                        if (state.networkGateway.baseUrl.isBlank()) "请填写网关地址并测试链路；当前可切换回模拟告警源进行演示。"
                        else "当前使用网管接口配置：${state.networkGateway.name}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Warning,
                    )
                }
            }
        }
        if (settings.sourceMode == AlarmSourceMode.NETWORK_MANAGER) {
            item {
                SettingsEditorSurface {
                    Text("网管接口配置", style = MaterialTheme.typography.titleSmall, color = Navy)
                    Spacer(Modifier.height(3.dp))
                    Text("填写后可直接切换到真实告警源；当前测试只访问健康检查接口。", style = MaterialTheme.typography.bodySmall, color = Muted)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = state.networkGateway.name,
                        onValueChange = { onGatewayConfigChange(state.networkGateway.copy(name = it)) },
                        modifier = Modifier.fillMaxWidth(), label = { Text("配置名称") }, singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(7.dp))
                    OutlinedTextField(
                        value = state.networkGateway.baseUrl,
                        onValueChange = { onGatewayConfigChange(state.networkGateway.copy(baseUrl = it)) },
                        modifier = Modifier.fillMaxWidth(), label = { Text("网关地址") },
                        placeholder = { Text("https://gateway.example.com") }, singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(7.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        OutlinedTextField(
                            value = state.networkGateway.apiVersion,
                            onValueChange = { onGatewayConfigChange(state.networkGateway.copy(apiVersion = it)) },
                            modifier = Modifier.weight(1f), label = { Text("API 版本") }, placeholder = { Text("v1") }, singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium,
                        )
                        OutlinedTextField(
                            value = state.networkGateway.timeoutSeconds.toString(),
                            onValueChange = { value -> value.toIntOrNull()?.let { onGatewayConfigChange(state.networkGateway.copy(timeoutSeconds = it)) } },
                            modifier = Modifier.weight(1f), label = { Text("超时（秒）") }, singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Spacer(Modifier.height(7.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        OutlinedTextField(
                            value = state.networkGateway.healthPath,
                            onValueChange = { onGatewayConfigChange(state.networkGateway.copy(healthPath = it)) },
                            modifier = Modifier.weight(1f), label = { Text("健康检查路径") }, placeholder = { Text("/health") }, singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium,
                        )
                        OutlinedTextField(
                            value = state.networkGateway.alarmsPath,
                            onValueChange = { onGatewayConfigChange(state.networkGateway.copy(alarmsPath = it)) },
                            modifier = Modifier.weight(1f), label = { Text("告警接口路径") }, placeholder = { Text("/alarms") }, singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("认证方式", style = MaterialTheme.typography.labelLarge, color = Navy)
                    Spacer(Modifier.height(4.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(NetworkAuthMode.entries.toList()) { mode ->
                            FilterChip(
                                selected = state.networkGateway.authMode == mode,
                                onClick = { onGatewayConfigChange(state.networkGateway.copy(authMode = mode)) },
                                label = { Text(mode.label, maxLines = 1) },
                            )
                        }
                    }
                    if (state.networkGateway.authMode != NetworkAuthMode.NONE) {
                        Spacer(Modifier.height(7.dp))
                        if (state.networkGateway.authMode == NetworkAuthMode.API_KEY || state.networkGateway.authMode == NetworkAuthMode.CUSTOM_HEADER) {
                            OutlinedTextField(
                                value = state.networkGateway.headerName,
                                onValueChange = { onGatewayConfigChange(state.networkGateway.copy(headerName = it)) },
                                modifier = Modifier.fillMaxWidth(), label = { Text("请求头名称") }, singleLine = true,
                                textStyle = MaterialTheme.typography.bodyMedium,
                            )
                            Spacer(Modifier.height(7.dp))
                        }
                        OutlinedTextField(
                            value = state.networkGateway.credential,
                            onValueChange = { onGatewayConfigChange(state.networkGateway.copy(credential = it)) },
                            modifier = Modifier.fillMaxWidth(), label = { Text(if (state.networkGateway.authMode == NetworkAuthMode.BASIC) "Basic 凭据" else "访问令牌 / API Key") },
                            singleLine = true, visualTransformation = PasswordVisualTransformation(), textStyle = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Spacer(Modifier.height(9.dp))
                    Text("字段映射（支持点号路径）", style = MaterialTheme.typography.labelLarge, color = Navy)
                    Text("将网管返回字段统一映射为告警 ID、设备主键和告警状态。", style = MaterialTheme.typography.labelSmall, color = Muted)
                    Spacer(Modifier.height(6.dp))
                    GatewayMappingFields(state.networkGateway, onGatewayConfigChange)
                    Spacer(Modifier.height(9.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        OutlinedButton(onClick = onGatewaySave, modifier = Modifier.weight(1f).height(42.dp), shape = RoundedCornerShape(8.dp)) {
                            Icon(Icons.Outlined.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp)); Text("保存配置")
                        }
                        Button(onClick = onGatewayTest, enabled = !state.isTestingGateway, modifier = Modifier.weight(1f).height(42.dp), shape = RoundedCornerShape(8.dp)) {
                            if (state.isTestingGateway) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                            else Icon(Icons.Outlined.Link, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp)); Text(if (state.isTestingGateway) "检测中" else "测试网管接口")
                        }
                    }
                    state.networkGateway.lastCheckSucceeded?.let { succeeded ->
                        Spacer(Modifier.height(7.dp))
                        val color = if (succeeded) Success else Danger
                        Text(
                            "${state.networkGateway.lastCheckMessage} · ${state.networkGateway.lastLatencyMillis ?: 0} ms",
                            style = MaterialTheme.typography.labelSmall,
                            color = color,
                        )
                    }
                }
            }
        }
        item {
            SettingsEditorSurface {
                Text("演示控制", style = MaterialTheme.typography.titleSmall, color = Navy)
                Spacer(Modifier.height(3.dp))
                Text("开启后，识别完成会从本次真实识别设备动态生成严重、一般和未匹配告警。", style = MaterialTheme.typography.bodySmall, color = Muted)
                Spacer(Modifier.height(6.dp))
                SwitchRow(
                    title = "演示模式",
                    detail = if (settings.demoMode) "已开启：下一次识别完成后自动生成本任务告警" else "关闭：可手动刷新或注入模拟告警",
                    checked = settings.demoMode,
                    onCheckedChange = onDemoModeChange,
                )
                if (state.racks.isEmpty() && settings.demoMode) {
                    Spacer(Modifier.height(5.dp))
                    Text("请先完成一次图片识别；告警不会使用固定柜号或旧任务设备。", style = MaterialTheme.typography.labelSmall, color = Warning)
                }
                Spacer(Modifier.height(7.dp))
                OutlinedButton(
                    onClick = onImportJson,
                    modifier = Modifier.fillMaxWidth().height(40.dp),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Icon(Icons.Outlined.Description, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("导入本地告警 JSON")
                }
            }
        }
        item {
            SettingsEditorSurface {
                Text("现场提醒", style = MaterialTheme.typography.titleSmall, color = Navy)
                Spacer(Modifier.height(6.dp))
                SwitchRow(
                    title = "声音提醒",
                    detail = "检测到新的未处理告警时播放一次提示音",
                    checked = settings.soundEnabled,
                    onCheckedChange = onSoundChange,
                )
                HorizontalDivider(Modifier.padding(vertical = 6.dp), color = Line)
                SwitchRow(
                    title = "震动提醒",
                    detail = "检测到新的未处理告警时短暂震动",
                    checked = settings.vibrationEnabled,
                    onCheckedChange = onVibrationChange,
                )
            }
        }
        item {
            SettingsEditorSurface {
                Text("复核留痕", style = MaterialTheme.typography.titleSmall, color = Navy)
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = settings.reviewer,
                    onValueChange = onReviewerChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("默认复核人") },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(8.dp))
                Text("关联主键（高级）", style = MaterialTheme.typography.labelLarge, color = Navy)
                Text("告警可按任一主键匹配；主键为空时明确显示未匹配。", style = MaterialTheme.typography.labelSmall, color = Muted)
                Spacer(Modifier.height(5.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    AlarmIdentityField.entries.forEach { field ->
                        FilterChip(
                            selected = field in settings.matchConfig.fields,
                            onClick = { onMatchFieldChange(field, field !in settings.matchConfig.fields) },
                            label = { Text(field.label, maxLines = 1) },
                        )
                    }
                }
                Spacer(Modifier.height(5.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    FilterChip(
                        selected = settings.matchConfig.mode == AlarmMatchMode.ANY,
                        onClick = { onMatchModeChange(AlarmMatchMode.ANY) },
                        label = { Text("命中任一主键") },
                    )
                    FilterChip(
                        selected = settings.matchConfig.mode == AlarmMatchMode.ALL,
                        onClick = { onMatchModeChange(AlarmMatchMode.ALL) },
                        label = { Text("全部主键一致") },
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text("设备资产标签扫码关联：预留入口，后续可复用现有 ML Kit 扫码能力。", style = MaterialTheme.typography.labelSmall, color = Muted)
            }
        }
        if (state.alarms.isNotEmpty()) {
            item {
                Text(
                    "当前已载入 ${state.alarms.size} 条告警 · 未处理 ${activeAlarmCount(state)} 条",
                    style = MaterialTheme.typography.bodySmall,
                    color = Muted,
                )
            }
        }
        if (state.status.isNotBlank()) item { StatusStrip(state.status, state.error) }
        state.error?.let { error -> item { ErrorCard(error) } }
    }
}

@Composable
private fun GatewayMappingFields(
    config: NetworkGatewayConfig,
    onChange: (NetworkGatewayConfig) -> Unit,
) {
    val mapping = config.fieldMapping
    fun update(next: NetworkGatewayFieldMapping) = onChange(config.copy(fieldMapping = next))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        GatewayMappingField("告警 ID", mapping.alarmId, Modifier.weight(1f)) { update(mapping.copy(alarmId = it)) }
        GatewayMappingField("设备名称", mapping.deviceName, Modifier.weight(1f)) { update(mapping.copy(deviceName = it)) }
    }
    Spacer(Modifier.height(6.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        GatewayMappingField("管理 IP", mapping.managementIp, Modifier.weight(1f)) { update(mapping.copy(managementIp = it)) }
        GatewayMappingField("资产编号", mapping.assetId, Modifier.weight(1f)) { update(mapping.copy(assetId = it)) }
    }
    Spacer(Modifier.height(6.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        GatewayMappingField("告警等级", mapping.severity, Modifier.weight(1f)) { update(mapping.copy(severity = it)) }
        GatewayMappingField("发生时间", mapping.occurredAt, Modifier.weight(1f)) { update(mapping.copy(occurredAt = it)) }
    }
    Spacer(Modifier.height(6.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        GatewayMappingField("告警描述", mapping.description, Modifier.weight(1f)) { update(mapping.copy(description = it)) }
        GatewayMappingField("告警状态", mapping.status, Modifier.weight(1f)) { update(mapping.copy(status = it)) }
    }
}

@Composable
private fun GatewayMappingField(
    label: String,
    value: String,
    modifier: Modifier,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = { Text(label) },
        singleLine = true,
        textStyle = MaterialTheme.typography.bodySmall,
    )
}

@Composable
private fun DeliveryStructureRow(index: String, title: String, detail: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(index, style = MaterialTheme.typography.labelSmall, color = Cyan, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = Navy)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = Muted)
        }
    }
}

private data class SettingsEntry(
    val code: String,
    val title: String,
    val detail: String,
)

@Composable
private fun SettingsListSurface(content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Line),
    ) { Column(content = { content() }) }
}

@Composable
private fun SettingsNavigatorCard(entry: SettingsEntry, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Line),
    ) { SettingsNavigatorRow(entry, onClick) }
}

@Composable
private fun SettingsNavigatorRow(entry: SettingsEntry, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(shape = RoundedCornerShape(6.dp), color = if (entry.code == "05") SurfaceTint else Navy) {
            Text(
                entry.code,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                style = MaterialTheme.typography.labelSmall,
                color = if (entry.code == "05") Blue else Color.White,
            )
        }
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) {
            Text(entry.title, style = MaterialTheme.typography.titleSmall, color = Navy)
            Spacer(Modifier.height(2.dp))
            Text(entry.detail, style = MaterialTheme.typography.bodySmall, color = Muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.Outlined.PlayArrow, contentDescription = "打开${entry.title}", tint = Blue, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun SettingsEditorSurface(content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Line),
    ) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) { content() } }
}

@Composable
private fun SettingsBottomActionBar(
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    content: @Composable RowScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Color.White,
        border = BorderStroke(1.dp, Line),
        shadowElevation = 8.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = if (compact) 8.dp else 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

@Composable
private fun AboutPage(state: RackExcelUiState, onBack: () -> Unit) {
    val taskCount = state.taskHistory.size
    val deliveredCount = state.taskHistory.count { it.resultName != null }
    val savedMillis = state.taskHistory.sumOf { it.efficiency.totalSavedMillis.coerceAtLeast(0L) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                color = Navy,
            ) {
                Row(
                    modifier = Modifier.padding(15.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BrandLogoMark(
                        modifier = Modifier.size(56.dp),
                        contentDescription = null,
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("云枢智维", style = MaterialTheme.typography.titleLarge, color = Color.White)
                        Text("通信机房资产智能运维", style = MaterialTheme.typography.labelMedium, color = Color(0xFFB8D5E9))
                        Text("版本 ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.labelSmall, color = Color(0xFF8FB4CB))
                        Spacer(Modifier.height(4.dp))
                        Text("可复核识别、修正交付、动态效率测算", style = MaterialTheme.typography.bodySmall, color = Color(0xFFD4E5EF))
                    }
                }
            }
        }
        val hasCurrentTaskData = state.images.isNotEmpty() || state.imageOutcomes.isNotEmpty() || state.racks.isNotEmpty()
        if (hasCurrentTaskData) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    MetricCard("图片", state.images.size.toString(), "本次任务", Modifier.weight(1f))
                    MetricCard("机柜", state.racks.size.toString(), "已识别", Modifier.weight(1f))
                    MetricCard(
                        "待复核",
                        state.imageOutcomes.count(ReviewPolicy::requiresManualReview).toString(),
                        "当前任务",
                        Modifier.weight(1f),
                    )
                }
            }
        }
        if (taskCount > 0) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    MetricCard("已完成任务", deliveredCount.toString(), "次交付", Modifier.weight(1f))
                    MetricCard("累计节省", formatDuration(savedMillis), "预计人工", Modifier.weight(1f))
                }
            }
        }
        item {
            Surface(shape = RoundedCornerShape(9.dp), color = SurfaceTint, border = BorderStroke(1.dp, Line)) {
                Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("应用价值", style = MaterialTheme.typography.titleSmall, color = Navy)
                    Text("面向通信运营商机房与代维团队，将完整机柜照片转化为可复核、可追溯、可交付的运维数据，减少人工数U、抄录和重复制表工作。通过“采集—识别—复核—修正—导出”闭环，提升上架图和设备台账的整理效率与交付一致性。", style = MaterialTheme.typography.bodySmall, color = Ink)
                    AboutCapabilityLine("现场效率", "批量采集，自动识别并按柜号排序。", Blue)
                    AboutCapabilityLine("质量可控", "异常结果进入复核，确认后再交付。", Warning)
                    AboutCapabilityLine("交付闭环", "复核修正后重新生成最终 Excel。", Success)
                    AboutCapabilityLine("本地协同", "支持手机保存、系统分享和局域网电脑传输。", Cyan)
                    AboutCapabilityLine("效率可量化", "AI 自动填表并动态测算识别、复核和制表节省时间。", Blue)
                    AboutCapabilityLine("告警联动", "将模拟告警按设备主键定位到柜号和 U 位，支持现场三态处置留痕。", Warning)
                }
            }
        }
        item {
            Column(Modifier.fillMaxWidth()) {
                Text("名称由来", style = MaterialTheme.typography.titleSmall, color = Navy)
                Spacer(Modifier.height(3.dp))
                Text(
                    "云：代表云端智能能力、AI 模型和多点位机房协同。即使当前支持手机本地调用和局域网部署，也保留了后续对接云平台的扩展空间。\n\n" +
                        "枢：代表“中枢、枢纽”。我们的 App 连接照片采集、AI 识别、机柜上架图、资产台账、告警和网管平台，是整个运维流程的连接中心。\n\n" +
                        "智：代表 AI 智能识别、自动分析、风险提示和报告生成。\n\n" +
                        "维：代表机房运维、资产维护和故障定位。",
                    style = MaterialTheme.typography.bodySmall,
                    color = Muted,
                )
                Spacer(Modifier.height(10.dp))
                HorizontalDivider(color = Line)
                Spacer(Modifier.height(10.dp))
                Text("核心能力", style = MaterialTheme.typography.titleSmall, color = Navy)
                Spacer(Modifier.height(7.dp))
                AboutCapabilityLine("现场采集", "支持手机拍照或多图上传，自动读取柜号并排序。", Blue)
                Spacer(Modifier.height(7.dp))
                AboutCapabilityLine("可复核识别", "识别设备类型、U位和占用高度，异常结果进入复核。", Warning)
                Spacer(Modifier.height(7.dp))
                AboutCapabilityLine("复核修正", "原图、47U机柜图与设备清单联动，支持修正后重新交付。", Cyan)
                Spacer(Modifier.height(7.dp))
                AboutCapabilityLine("标准交付", "生成《机房上架图》《设备明细登记表》《统计分析》。", Success)
                Spacer(Modifier.height(7.dp))
                AboutCapabilityLine("告警联动", "告警按设备主键定位到柜号和 U 位，支持现场三态处置留痕。", Warning)
                Spacer(Modifier.height(7.dp))
                AboutCapabilityLine("网管接口预留", "当前使用本地模拟告警源，后续可平滑接入真实网管接口。", Cyan)
                Spacer(Modifier.height(7.dp))
                AboutCapabilityLine("数据边界", "型号、IP、资产编号等图片无法确认的字段保留为空。", Muted)
            }
        }
        item {
            Text("如何使用", style = MaterialTheme.typography.titleSmall, color = Navy)
        }
        item { AboutStep("1", "在模型配置中选择引擎，并完成测试识别引擎。") }
        item { AboutStep("2", "在智能识别页拍照或选择完整机柜原图。") }
        item { AboutStep("3", "识别完成后可直接确认，或进入现场复核并修正设备信息。") }
        item { AboutStep("4", "确认无疑义后生成最终 Excel：机房上架图、设备明细登记表、统计分析。") }
        item { AboutStep("5", "在任务详情打开文件，或通过系统分享、局域网电脑发送。") }
        item {
            Text(
                "后续可对接网管平台、告警系统和多点位运维平台，形成资产、状态与告警联动。",
                style = MaterialTheme.typography.bodySmall,
                color = Muted,
            )
        }
    }
}

@Composable
private fun AboutCapabilityLine(title: String, detail: String, accent: Color) {
    Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .padding(top = 5.dp)
                .size(7.dp)
                .clip(RoundedCornerShape(99.dp))
                .background(accent),
        )
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = Navy)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = Muted)
        }
    }
}

@Composable
private fun AboutStep(number: String, detail: String) {
    Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
        Surface(shape = RoundedCornerShape(99.dp), color = SurfaceTint) {
            Text(number, modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp), style = MaterialTheme.typography.labelLarge, color = Blue, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(10.dp))
        Text(detail, modifier = Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodyMedium, color = Ink)
    }
}

@Composable
private fun EngineStatusBanner(banner: EngineBanner, modifier: Modifier = Modifier) {
    val background = when (banner.phase) {
        EngineBannerPhase.CHECKING -> Color(0xFFE6F0FF)
        EngineBannerPhase.READY -> Color(0xFFE8F7F0)
        EngineBannerPhase.FAILED -> Color(0xFFFFEEEE)
        EngineBannerPhase.IDLE -> Color.White
    }
    val color = when (banner.phase) {
        EngineBannerPhase.CHECKING -> Blue
        EngineBannerPhase.READY -> Success
        EngineBannerPhase.FAILED -> Danger
        EngineBannerPhase.IDLE -> Muted
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = background,
        border = BorderStroke(1.dp, color.copy(alpha = 0.22f)),
    ) {
        Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
            if (banner.phase == EngineBannerPhase.CHECKING) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = color)
            } else {
                Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = color)
            }
            Spacer(Modifier.width(9.dp))
            Column(Modifier.weight(1f)) {
                Text(banner.title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = color)
                Text(banner.detail, style = MaterialTheme.typography.bodySmall, color = Ink)
                if (banner.latencyMillis != null) {
                    Text((banner.model.ifBlank { "当前模型" }) + " · " + banner.latencyMillis + " ms", style = MaterialTheme.typography.labelSmall, color = Muted)
                }
            }
        }
    }
}

@Composable
private fun MetricCard(title: String, value: String, caption: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Line),
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 9.dp)) {
            Text(title, style = MaterialTheme.typography.labelSmall, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.titleMedium, color = Navy, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(caption, style = MaterialTheme.typography.labelSmall, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun SectionTitle(title: String, detail: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = Navy, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(2.dp))
        Text(detail, style = MaterialTheme.typography.bodySmall, color = Muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SwitchRow(title: String, detail: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = Muted)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun ImagePreview(
    image: SelectedImage,
    displayName: String = image.name,
    onOpen: () -> Unit,
    onRemove: () -> Unit,
    enabled: Boolean,
) {
    val context = LocalContext.current
    val bitmap by produceState<android.graphics.Bitmap?>(initialValue = null, image.uri) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            decodePreviewBitmap(context, image.uri, maxDimension = 420)
        }
    }
    Card(modifier = Modifier.width(138.dp), shape = RoundedCornerShape(10.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column {
            Box(
                Modifier.fillMaxWidth().height(94.dp).background(Color(0xFFEAF0F7)).clickable(onClick = onOpen),
                contentAlignment = Alignment.Center,
            ) {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap!!.asImageBitmap(),
                        contentDescription = image.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Icon(Icons.Outlined.PhotoLibrary, contentDescription = null, tint = Blue)
                }
                image.quality?.let { quality ->
                    Surface(
                        modifier = Modifier.align(Alignment.BottomStart).padding(6.dp),
                        shape = RoundedCornerShape(6.dp),
                        color = if (quality.reasons.isEmpty()) Color(0xFFE8F7F0) else Color(0xFFFFF1DE),
                    ) {
                        Text(
                            quality.label,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (quality.reasons.isEmpty()) Success else Warning,
                        )
                    }
                }
                if (enabled) {
                    Surface(
                        modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
                        shape = RoundedCornerShape(8.dp),
                        color = Navy.copy(alpha = 0.90f),
                    ) {
                        IconButton(onClick = onRemove, modifier = Modifier.size(34.dp)) {
                            Icon(Icons.Outlined.Close, contentDescription = "移除此图片", tint = Color.White)
                        }
                    }
                }
            }
            Text(
                displayName,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            image.quality?.reasons?.firstOrNull()?.let { reason ->
                Text(
                    reason,
                    modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = Warning,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private fun decodePreviewBitmap(
    context: android.content.Context,
    uri: Uri,
    maxDimension: Int,
): android.graphics.Bitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    openImageStream(context, uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
    var sample = 1
    var largest = maxOf(bounds.outWidth, bounds.outHeight)
    while (largest > maxDimension) {
        sample *= 2
        largest = (largest + 1) / 2
    }
    val options = BitmapFactory.Options().apply {
        inSampleSize = sample
        inPreferredConfig = android.graphics.Bitmap.Config.RGB_565
    }
    openImageStream(context, uri)?.use { BitmapFactory.decodeStream(it, null, options) }
}.getOrNull()

private fun openImageStream(context: android.content.Context, uri: Uri): java.io.InputStream? =
    if (uri.scheme.equals("file", ignoreCase = true)) {
        uri.path?.takeIf { it.isNotBlank() }?.let(::File)?.takeIf { it.isFile }?.inputStream()
    } else {
        context.contentResolver.openInputStream(uri)
    }

@Composable
private fun AnimatedOutcomeEntry(
    outcome: ImageOutcome,
    onOpenReview: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val motionEnabled = rememberMotionEnabled()
    var visible by remember(outcome.imageId) { mutableStateOf(false) }
    LaunchedEffect(outcome.imageId) { visible = true }
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(tween(if (motionEnabled) 160 else 0)) +
            expandVertically(tween(if (motionEnabled) 220 else 0)),
        exit = fadeOut(tween(if (motionEnabled) 120 else 0)) +
            shrinkVertically(tween(if (motionEnabled) 120 else 0)),
    ) {
        ImageOutcomeCard(outcome, onOpenReview = onOpenReview)
    }
}

@Composable
private fun ImageOutcomeCard(
    outcome: ImageOutcome,
    displayName: String = outcome.displayImageAlias(),
    onOpenReview: (() -> Unit)? = null,
    onRetry: (() -> Unit)? = null,
) {
    var detailsExpanded by rememberSaveable(outcome.imageId) { mutableStateOf(false) }
    val motionEnabled = rememberMotionEnabled()
    val (targetBackground, targetTint) = when (outcome.state) {
        ImageProcessingState.COMPLETED -> Color(0xFFEAF7F0) to Success
        ImageProcessingState.FAILED -> Color(0xFFFFEEEE) to Danger
        ImageProcessingState.REPAIRING, ImageProcessingState.QUALITY_REVIEW -> Color(0xFFFFF5E7) to Warning
        ImageProcessingState.ANALYZING, ImageProcessingState.QUEUED -> Color(0xFFEAF2FF) to Blue
    }
    val colorSpec = tween<Color>(durationMillis = if (motionEnabled) 420 else 0, easing = FastOutSlowInEasing)
    val background by animateColorAsState(targetBackground, colorSpec, label = "outcomeBackground")
    val tint by animateColorAsState(targetTint, colorSpec, label = "outcomeTint")

    val settled = outcome.state == ImageProcessingState.COMPLETED ||
        outcome.state == ImageProcessingState.QUALITY_REVIEW ||
        outcome.state == ImageProcessingState.FAILED
    val inFlight = motionEnabled && !settled
    // A card only lights up on the transition into a settled state — not when the list is
    // rebuilt from history, where every card would flash at once.
    var litUp by remember(outcome.imageId) { mutableStateOf(settled) }
    val lightUp by animateFloatAsState(
        targetValue = if (litUp) 1f else 0f,
        animationSpec = if (motionEnabled) {
            spring(dampingRatio = 0.58f, stiffness = 360f)
        } else {
            tween(durationMillis = 0)
        },
        label = "outcomeLightUp",
    )
    LaunchedEffect(settled) { litUp = settled }

    val breath = if (inFlight) {
        val breathTransition = rememberInfiniteTransition(label = "outcomeBreath")
        val animatedBreath by breathTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "outcomeBreathValue",
        )
        animatedBreath
    } else {
        0f
    }
    val borderColor = when {
        inFlight -> Line.copy(alpha = 0.45f + breath * 0.55f)
        else -> lerp(Line, tint.copy(alpha = 0.55f), lightUp)
    }
    val cardScale = if (settled) 0.985f + lightUp * 0.015f else 1f

    val rack = outcome.rack
    val uncertainCount = rack?.uncertain?.size ?: 0
    val riskCount = rack?.riskCandidates?.size ?: 0
    val hasReviewDetails = uncertainCount > 0 || riskCount > 0
    val primaryHint = rack?.uncertain?.firstOrNull()
        ?: rack?.riskCandidates?.firstOrNull()?.description
    Card(
        modifier = Modifier.graphicsLayer(scaleX = cardScale, scaleY = cardScale),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, borderColor),
    ) {
        Column(Modifier.padding(13.dp).animateContentSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.PhotoLibrary,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.graphicsLayer(alpha = if (inFlight) 0.55f + breath * 0.45f else 1f),
                )
                Spacer(Modifier.width(8.dp))
                Text(displayName, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Surface(shape = RoundedCornerShape(99.dp), color = background) {
                    AnimatedContent(
                        targetState = outcome.state,
                        transitionSpec = {
                            (slideInVertically { height -> height } + fadeIn()) togetherWith
                                (slideOutVertically { height -> -height } + fadeOut())
                        },
                        label = "outcomeStateChip",
                    ) { state ->
                        Text(state.label, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, color = tint)
                    }
                }
            }
            if (onOpenReview != null || onRetry != null) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    onOpenReview?.let { openReview ->
                        OutlinedButton(
                            onClick = openReview,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Icon(Icons.Outlined.CheckCircle, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(if (ReviewPolicy.requiresManualReview(outcome)) "进入现场复核" else "查看机柜图", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    onRetry?.let { retry ->
                        OutlinedButton(
                            onClick = retry,
                            modifier = if (onOpenReview == null) Modifier.fillMaxWidth() else Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Icon(Icons.Outlined.PlayArrow, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(3.dp))
                            Text("重新识别", maxLines = 1)
                        }
                    }
                }
            }
            if (outcome.cabinetId != null || outcome.message.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    (outcome.cabinetId?.let { "识别柜号：" + it + " · " } ?: "") + outcome.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = Muted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (hasReviewDetails) {
                Spacer(Modifier.height(7.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFFFF5E7),
                    border = BorderStroke(1.dp, Color(0xFFE8C78E)),
                ) {
                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            when {
                                uncertainCount > 0 && riskCount > 0 -> "待复核 $uncertainCount 项 · 风险线索 $riskCount 条"
                                uncertainCount > 0 -> "待人工复核 $uncertainCount 项"
                                else -> "照片风险线索 $riskCount 条"
                            },
                            style = MaterialTheme.typography.labelLarge,
                            color = Warning,
                        )
                        primaryHint?.let { hint ->
                            Text(hint, style = MaterialTheme.typography.bodySmall, color = Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        TextButton(
                            onClick = { detailsExpanded = !detailsExpanded },
                            contentPadding = PaddingValues(0.dp),
                            modifier = Modifier.height(28.dp),
                        ) {
                            Text(if (detailsExpanded) "收起全部线索" else "查看全部线索")
                        }
                    }
                }
                if (detailsExpanded) OutcomeReviewDetails(outcome)
            }
            if (outcome.repairEvents.isNotEmpty()) {
                Spacer(Modifier.height(7.dp))
                outcome.repairEvents.forEach { event ->
                    Text(
                        "遇到问题，自动修复中 · 第 " + event.retry + "/3 次：" + event.message,
                        style = MaterialTheme.typography.labelSmall,
                        color = Warning,
                    )
                }
            }
        }
    }
}

@Composable
private fun OutcomeReviewDetails(outcome: ImageOutcome) {
    val rack = outcome.rack ?: return
    val uncertain = rack.uncertain
    val risks = rack.riskCandidates
    if (uncertain.isEmpty() && risks.isEmpty()) return

    Spacer(Modifier.height(10.dp))
    if (uncertain.isNotEmpty()) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = Color(0xFFFFF5E7),
            border = BorderStroke(1.dp, Color(0xFFE8C78E)),
        ) {
            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("待人工复核 · ${uncertain.size} 项", style = MaterialTheme.typography.labelLarge, color = Warning)
                uncertain.forEachIndexed { index, note ->
                    Text(
                        "${index + 1}. $note",
                        style = MaterialTheme.typography.bodySmall,
                        color = Ink,
                    )
                }
            }
        }
    }
    if (risks.isNotEmpty()) {
        Spacer(Modifier.height(if (uncertain.isEmpty()) 0.dp else 8.dp))
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = SurfaceTint,
            border = BorderStroke(1.dp, Line),
        ) {
            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("照片风险线索 · ${risks.size} 条", style = MaterialTheme.typography.labelLarge, color = Navy)
                risks.forEachIndexed { index, risk ->
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            "${index + 1}. ${risk.category} · ${risk.level.label}",
                            style = MaterialTheme.typography.labelMedium,
                            color = Ink,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(risk.description, style = MaterialTheme.typography.bodySmall, color = Ink)
                        Text("证据：${risk.evidence}", style = MaterialTheme.typography.labelSmall, color = Muted)
                        if (risk.recommendation.isNotBlank()) {
                            Text("建议：${risk.recommendation}", style = MaterialTheme.typography.labelSmall, color = Muted)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DesktopTransferStatusCard(
    transfer: DesktopTransferUiState,
    onPause: (() -> Unit)? = null,
) {
    if (transfer.phase == DesktopTransferPhase.IDLE) return
    val (background, tint, title) = when (transfer.phase) {
        DesktopTransferPhase.PAIRING -> Triple(Color(0xFFEAF2FF), Blue, "正在连接电脑")
        DesktopTransferPhase.CHECKING -> Triple(Color(0xFFEAF2FF), Blue, "正在检测电脑")
        DesktopTransferPhase.SENDING -> Triple(Color(0xFFEAF2FF), Blue, "正在发送 Excel")
        DesktopTransferPhase.PAUSED -> Triple(Color(0xFFFFF5E7), Warning, "发送已暂停")
        DesktopTransferPhase.COMPLETED -> Triple(Color(0xFFEAF7F0), Success, "电脑已接收")
        DesktopTransferPhase.FAILED -> Triple(Color(0xFFFFEEEE), Danger, "电脑交付未完成")
        DesktopTransferPhase.IDLE -> Triple(Color.Transparent, Muted, "")
    }
    Surface(shape = RoundedCornerShape(8.dp), color = background, border = BorderStroke(1.dp, tint.copy(alpha = 0.24f))) {
        Column(Modifier.padding(11.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (transfer.phase == DesktopTransferPhase.SENDING || transfer.phase == DesktopTransferPhase.PAIRING || transfer.phase == DesktopTransferPhase.CHECKING) {
                    CircularProgressIndicator(modifier = Modifier.size(15.dp), strokeWidth = 2.dp, color = tint)
                } else {
                    Icon(
                        if (transfer.phase == DesktopTransferPhase.COMPLETED) Icons.Outlined.CheckCircle else Icons.Outlined.Info,
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(17.dp),
                    )
                }
                Spacer(Modifier.width(7.dp))
                Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, color = tint)
                if (transfer.phase == DesktopTransferPhase.SENDING && onPause != null) {
                    TextButton(onClick = onPause, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)) {
                        Text("暂停", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            if (transfer.phase == DesktopTransferPhase.SENDING) {
                val sendProgress = animatedProgress(transfer.progress, "desktopTransferProgress")
                LinearProgressIndicator(
                    progress = { sendProgress },
                    modifier = Modifier.fillMaxWidth(),
                    color = tint,
                    trackColor = Color.White.copy(alpha = 0.72f),
                )
            }
            if (transfer.message.isNotBlank()) {
                Text(transfer.message, style = MaterialTheme.typography.bodySmall, color = Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun ResultFileCard(
    name: String,
    saved: Boolean,
    connectedComputerName: String?,
    transfer: DesktopTransferUiState,
    onSave: () -> Unit,
    onShare: () -> Unit,
    onOpen: () -> Unit,
    onSendToDesktop: () -> Unit,
    onPauseDesktopTransfer: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Line),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(9.dp), color = Color(0xFFE8F5F0)) {
                    Icon(Icons.Outlined.Description, contentDescription = null, tint = Success, modifier = Modifier.padding(8.dp).size(19.dp))
                }
                Spacer(Modifier.width(9.dp))
                Column(Modifier.weight(1f)) {
                    Text("三表 Excel 成果已就绪", style = MaterialTheme.typography.titleSmall, color = Navy)
                    Text(name, style = MaterialTheme.typography.bodySmall, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Surface(color = if (saved) Color(0xFFEAF7F0) else Color(0xFFFFF5E7), shape = RoundedCornerShape(8.dp)) {
                    Text(if (saved) "已就绪" else "待保存", modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, color = if (saved) Success else Warning)
                }
            }
            Spacer(Modifier.height(10.dp))
            if (transfer.phase != DesktopTransferPhase.IDLE) {
                DesktopTransferStatusCard(transfer = transfer, onPause = onPauseDesktopTransfer)
                Spacer(Modifier.height(8.dp))
            }
            if (connectedComputerName != null) {
                Button(
                    onClick = onSendToDesktop,
                    enabled = !transfer.isWorking,
                    modifier = Modifier.fillMaxWidth().height(42.dp),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Icon(Icons.Outlined.Link, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (transfer.phase == DesktopTransferPhase.COMPLETED) "再次发送到 $connectedComputerName" else "发送到 $connectedComputerName", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(7.dp))
            } else {
                Text("在设置中连接电脑后，可一键发送此 Excel。", style = MaterialTheme.typography.bodySmall, color = Muted)
                Spacer(Modifier.height(7.dp))
            }
            OutlinedButton(onClick = onSave, modifier = Modifier.fillMaxWidth().height(42.dp), shape = RoundedCornerShape(10.dp)) {
                Icon(Icons.Outlined.Save, contentDescription = null, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(6.dp))
                Text("保存到手机")
            }
            Spacer(Modifier.height(7.dp))
            OutlinedButton(onClick = onShare, modifier = Modifier.fillMaxWidth().height(42.dp), shape = RoundedCornerShape(10.dp)) {
                Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(6.dp))
                Text("系统分享（微信等）")
            }
            TextButton(onClick = onOpen, modifier = Modifier.fillMaxWidth()) { Text("打开 Excel 文件") }
        }
    }
}

@Composable
private fun HistoryCard(
    item: TaskHistoryItem,
    onDetail: () -> Unit,
    onDelete: () -> Unit,
    deletionEnabled: Boolean,
) {
    val cabinetText = item.cabinetIds.joinToString("、").ifBlank { "柜号待确认" }
    val reviewHint = item.primaryReviewHint()
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onDetail),
        shape = RoundedCornerShape(8.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Line),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(7.dp), color = SurfaceTint) {
                Icon(
                    Icons.Outlined.Description,
                    contentDescription = null,
                    modifier = Modifier.padding(7.dp).size(17.dp),
                    tint = Blue,
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(item.displayTaskName(), style = MaterialTheme.typography.bodyMedium, color = Ink, fontWeight = FontWeight.SemiBold)
                Text("$cabinetText · ${formatCompactTime(item.startedAtMillis)}", style = MaterialTheme.typography.labelSmall, color = Muted)
                if (item.efficiency.totalSavedMillis > 0L) {
                    Text("任务用时 ${formatDuration(item.durationMillis)} · 预计节省 ${formatDuration(item.efficiency.totalSavedMillis)}", style = MaterialTheme.typography.labelSmall, color = Success, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                val status = when {
                    reviewHint != null -> "待复核：$reviewHint"
                    item.summary.failedCount > 0 -> "待补拍 ${item.summary.failedCount} 张"
                    item.desktopDelivery != null -> "已发送到 ${item.desktopDelivery.receiverName}"
                    item.resultUri != null -> "Excel 已交付 · ${item.summary.successCount} 张识别完成"
                    item.resultName != null -> "Excel 已生成 · ${item.summary.successCount} 张识别完成"
                    else -> "图片 ${item.summary.imageCount} 张 · 识别完成 ${item.summary.successCount} 张"
                }
                Text(
                    status,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (reviewHint != null || item.summary.failedCount > 0) Warning else Muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(
                onClick = onDelete,
                enabled = deletionEnabled,
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = "删除历史任务",
                    tint = if (deletionEnabled) Danger else Muted,
                    modifier = Modifier.size(18.dp),
                )
            }
            Icon(
                Icons.Outlined.PlayArrow,
                contentDescription = "查看任务详情",
                tint = Blue,
                modifier = Modifier.size(18.dp).graphicsLayer(rotationZ = 90f),
            )
        }
    }
}

@Composable
private fun RackReviewPage(
    outcome: ImageOutcome,
    imageUri: Uri?,
    alarmHighlights: List<RackAlarmHighlight> = emptyList(),
    onOpenImage: (Uri, String) -> Unit,
    onUpdateDevice: (String, Int, String, Int, Int) -> Unit,
    onConfirm: () -> Unit,
    onReidentify: () -> Unit,
    onBack: () -> Unit,
) {
    val rack = outcome.rack
    if (rack == null) {
        EmptyCard("暂无可复核的机柜图", "该图片尚未形成有效识别结果，可返回任务中心重新识别。")
        return
    }
    var selectedTab by rememberSaveable(outcome.imageId) { mutableStateOf(0) }
    var selectedDeviceIndex by rememberSaveable(outcome.imageId) { mutableStateOf<Int?>(null) }
    var confirming by rememberSaveable(outcome.imageId) { mutableStateOf(false) }
    val motionEnabled = rememberMotionEnabled()
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val confirmScale by animateFloatAsState(
        targetValue = if (confirming) 0.98f else 1f,
        animationSpec = tween(durationMillis = if (motionEnabled) 180 else 0),
        label = "reviewConfirmationScale",
    )

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 112.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                ReviewSummaryStrip(
                    cabinetId = rack.cabinetId,
                    usedU = rack.devices.sumOf(Device::heightU),
                    reviewCount = reviewCount(outcome),
                    readOnly = false,
                )
            }
            item {
                ReviewTabSelector(selectedTab = selectedTab, onSelected = { selectedTab = it })
            }
            when (selectedTab) {
                0 -> item {
                    ReviewImagePanel(
                        uri = imageUri,
                        name = outcome.displayImageAlias(),
                        onOpen = onOpenImage,
                    )
                }

                1 -> item {
                    RackOccupancyDiagram(
                        rack = rack,
                        title = "47U 机柜图",
                        selectedDeviceIndex = selectedDeviceIndex,
                        alarmHighlights = alarmHighlights,
                        onDeviceClick = { index ->
                            selectedDeviceIndex = index
                            selectedTab = 2
                        },
                    )
                }

                else -> {
                    if (rack.uncertain.isNotEmpty() || rack.riskCandidates.isNotEmpty()) {
                        item { OutcomeReviewDetails(outcome) }
                    }
                    item {
                        Text(
                            "设备清单 · 点击项目可同步高亮机柜 U 位",
                            style = MaterialTheme.typography.labelMedium,
                            color = Muted,
                        )
                    }
                    itemsIndexed(rack.devices, key = { index, device -> "$index-${device.type}-${device.bottomU}-${device.heightU}" }) { index, device ->
                        DeviceReviewEditor(
                            index = index,
                            device = device,
                            selected = selectedDeviceIndex == index,
                            onSelect = {
                                selectedDeviceIndex = index
                                selectedTab = 1
                            },
                            onSave = { type, bottomU, heightU ->
                                selectedDeviceIndex = index
                                onUpdateDevice(outcome.imageId, index, type, bottomU, heightU)
                            },
                        )
                    }
                }
            }
        }
        Surface(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding(),
            color = Color.White,
            shadowElevation = 8.dp,
        ) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(
                    onClick = {
                        confirming = true
                        if (motionEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        scope.launch {
                            if (motionEnabled) delay(180)
                            onConfirm()
                        }
                    },
                    enabled = !confirming,
                    modifier = Modifier.fillMaxWidth().height(46.dp).graphicsLayer(scaleX = confirmScale, scaleY = confirmScale),
                    shape = RoundedCornerShape(9.dp),
                ) {
                    Icon(Icons.Outlined.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        when {
                            confirming -> "正在返回交付确认"
                            outcome.reviewConfirmed -> "已确认，返回交付确认"
                            else -> "确认本柜，返回交付确认"
                        },
                    )
                }
                TextButton(onClick = onReidentify, modifier = Modifier.fillMaxWidth().height(30.dp)) {
                    Text("重新识别此图")
                }
            }
        }
    }
}

@Composable
private fun HistoryTaskDetailPage(
    item: TaskHistoryItem,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onSendToDesktop: () -> Unit,
    connectedComputerName: String?,
    desktopTransfer: DesktopTransferUiState,
    alarmAudits: List<AlarmRecord> = emptyList(),
    feedbackStatus: String?,
    feedbackError: String?,
    onPauseDesktopTransfer: () -> Unit,
    onOpenReview: (TaskReviewSnapshot) -> Unit,
    onResumeForReview: () -> Unit,
    onBack: () -> Unit,
) {
    val rackCount = item.reviewItems.mapNotNull { it.rack }.map { it.cabinetId }.distinct().size.coerceAtLeast(1)
    val usedU = item.reviewItems.sumOf { snapshot -> snapshot.rack?.devices?.sumOf(Device::heightU) ?: 0 }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Surface(shape = RoundedCornerShape(9.dp), color = Color.White, border = BorderStroke(1.dp, Line)) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    MiniRackGlyph(
                        usedU = usedU,
                        capacityU = rackCount * RACK_TOTAL_U,
                        compact = true,
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(item.displayTaskName(), style = MaterialTheme.typography.titleSmall, color = Navy)
                        Text(
                            "开始 ${formatCompactTime(item.startedAtMillis)} · 结束 ${formatCompactTime(item.finishedAtMillis)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Muted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text("任务用时 ${formatDuration(item.durationMillis)}", style = MaterialTheme.typography.labelSmall, color = Blue)
                        Text("${item.cabinetIds.joinToString("、").ifBlank { "柜号待确认" }} · ${item.summary.imageCount} 张原图 · ${item.summary.successCount} 柜已识别", style = MaterialTheme.typography.bodySmall, color = Ink)
                        val reviewHint = item.primaryReviewHint()
                        if (reviewHint != null) {
                            Text("待复核：$reviewHint", style = MaterialTheme.typography.labelSmall, color = Warning, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
        item {
            EfficiencyDetailCard(item.efficiency, item.durationMillis)
        }
        if (alarmAudits.isNotEmpty()) {
            item {
                AlarmAuditSummaryCard(alarmAudits)
            }
        }
        item {
            Surface(shape = RoundedCornerShape(9.dp), color = SurfaceTint) {
                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Excel 交付文件", style = MaterialTheme.typography.labelMedium, color = Navy)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        OutlinedButton(
                            onClick = onOpen,
                            // Legacy history may have only the workbook name;
                            // the ViewModel will recover the private URI on tap.
                            enabled = item.resultUri != null || item.resultName != null,
                            modifier = Modifier.weight(1f).height(40.dp),
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Icon(Icons.Outlined.Description, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(3.dp))
                            Text("打开", maxLines = 1)
                        }
                        OutlinedButton(
                            onClick = onShare,
                            enabled = item.resultUri != null || item.resultName != null,
                            modifier = Modifier.weight(1f).height(40.dp),
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(3.dp))
                            Text("分享", maxLines = 1)
                        }
                        Button(
                            onClick = onSendToDesktop,
                            enabled = (item.resultUri != null || item.resultName != null) && !desktopTransfer.isWorking,
                            modifier = Modifier.weight(1f).height(40.dp),
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Icon(Icons.Outlined.Link, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(3.dp))
                            Text("发送", maxLines = 1)
                        }
                    }
                    if (connectedComputerName == null) {
                        Text("发送到电脑前，请先在设置中完成电脑连接。", style = MaterialTheme.typography.labelSmall, color = Muted)
                    }
                    if (item.reviewItems.any { it.rack != null }) {
                        OutlinedButton(
                            onClick = onResumeForReview,
                            modifier = Modifier.fillMaxWidth().height(40.dp),
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(5.dp))
                            Text("复核修改并重新生成 Excel", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
        feedbackStatus?.let { message -> item { StatusStrip(message, null) } }
        feedbackError?.let { message -> item { ErrorCard(message) } }
        if (desktopTransfer.taskId == item.taskId && desktopTransfer.phase != DesktopTransferPhase.IDLE) {
            item { DesktopTransferStatusCard(transfer = desktopTransfer, onPause = onPauseDesktopTransfer) }
        }
        item.desktopDelivery?.let { delivery ->
            item {
                Surface(shape = RoundedCornerShape(8.dp), color = Color(0xFFEAF7F0), border = BorderStroke(1.dp, Color(0xFFB7DFD2))) {
                    Column(Modifier.padding(11.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("已发送到 ${delivery.receiverName}", style = MaterialTheme.typography.labelLarge, color = Success)
                        Text(delivery.fileName, style = MaterialTheme.typography.bodySmall, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(delivery.savedPath, style = MaterialTheme.typography.labelSmall, color = Muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text("接收时间 ${formatCompactTime(delivery.receivedAtMillis)}", style = MaterialTheme.typography.labelSmall, color = Muted)
                    }
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("复核记录", style = MaterialTheme.typography.titleSmall, color = Navy)
                Text("点击机柜查看原图、U 位图和设备清单。", style = MaterialTheme.typography.bodySmall, color = Muted)
            }
        }
        if (item.reviewItems.isEmpty()) {
            item { EmptyCard("旧任务暂无复核快照", "后续完成的新任务会自动保留复核图片和识别明细。") }
        } else {
            items(item.reviewItems, key = { review -> review.imageId }) { review ->
                ReviewSnapshotCard(item = review, onClick = { onOpenReview(review) })
            }
        }
    }
}

@Composable
private fun EfficiencyDetailCard(metrics: TaskEfficiencyMetrics, durationMillis: Long) {
    Surface(shape = RoundedCornerShape(9.dp), color = Color.White, border = BorderStroke(1.dp, Line)) {
        Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text("时间与效益", style = MaterialTheme.typography.titleSmall, color = Navy)
            Text("任务耗时 ${formatDuration(durationMillis)} · 以下为本次任务动态测算", style = MaterialTheme.typography.bodySmall, color = Muted)
            EfficiencyMetricRow("识别环节预计", metrics.recognitionSavedMillis)
            EfficiencyMetricRow("制表环节预计", metrics.exportSavedMillis)
            EfficiencyMetricRow("人工复核耗时", metrics.manualReviewMillis, accent = Muted)
            HorizontalDivider(color = Line)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("预计节省人工", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, color = Navy)
                Text(formatDuration(metrics.totalSavedMillis), style = MaterialTheme.typography.titleMedium, color = Success, fontWeight = FontWeight.Bold)
            }
            Text("测算包含人工识别、人工制表、AI 实际耗时及复核耗时，仅作为效率评估。", style = MaterialTheme.typography.labelSmall, color = Muted)
        }
    }
}

@Composable
private fun AlarmAuditSummaryCard(alarms: List<AlarmRecord>) {
    Surface(shape = RoundedCornerShape(9.dp), color = Color.White, border = BorderStroke(1.dp, Line)) {
        Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("告警联动记录", style = MaterialTheme.typography.titleSmall, color = Navy)
            Text("本任务共记录 ${alarms.sumOf { it.processing.size }} 次现场判定", style = MaterialTheme.typography.bodySmall, color = Muted)
            alarms.forEach { alarm ->
                val last = alarm.processing.lastOrNull()
                Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
                    Box(Modifier.padding(top = 5.dp).size(7.dp).clip(RoundedCornerShape(99.dp)).background(alarmSeverityColor(alarm.severity)))
                    Spacer(Modifier.width(7.dp))
                    Column(Modifier.weight(1f)) {
                        Text("${alarm.severity.label} · ${last?.decision?.label ?: alarm.status.label}", style = MaterialTheme.typography.labelLarge, color = Ink)
                        Text(alarm.description, style = MaterialTheme.typography.labelSmall, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        last?.let {
                            Text("${it.reviewer} · ${formatCompactTime(it.reviewedAtMillis)}", style = MaterialTheme.typography.labelSmall, color = Muted)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EfficiencyMetricRow(label: String, millis: Long, accent: Color = Success) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = Ink)
        Text(formatDuration(millis), style = MaterialTheme.typography.labelLarge, color = accent)
    }
}

@Composable
private fun HistoryReviewPage(
    item: TaskReviewSnapshot,
    onOpenImage: (Uri, String) -> Unit,
    onBack: () -> Unit,
) {
    val rack = item.rack
    val outcome = remember(item) {
        ImageOutcome(
            imageName = item.imageName,
            rack = rack,
            state = ImageProcessingState.entries.firstOrNull { it.label == item.state } ?: ImageProcessingState.COMPLETED,
            retryCount = item.retryCount,
            message = item.message,
            imageId = item.imageId,
            reviewConfirmed = item.reviewConfirmed,
            reviewImagePath = item.reviewImagePath,
        )
    }
    var selectedTab by rememberSaveable(item.imageId) { mutableStateOf(0) }
    var selectedDeviceIndex by rememberSaveable(item.imageId) { mutableStateOf<Int?>(null) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        rack?.let { savedRack ->
            item {
                ReviewSummaryStrip(
                    cabinetId = savedRack.cabinetId,
                    usedU = savedRack.devices.sumOf(Device::heightU),
                    reviewCount = reviewCount(outcome),
                    readOnly = true,
                )
            }
        } ?: item {
            ReviewSummaryStrip(
                cabinetId = item.cabinetId ?: "柜号待确认",
                usedU = 0,
                reviewCount = item.uncertain.size,
                readOnly = true,
            )
        }
        item { ReviewTabSelector(selectedTab = selectedTab, onSelected = { selectedTab = it }) }
        when (selectedTab) {
            0 -> item {
                ReviewImagePanel(
                    uri = item.reviewUri(),
                    name = item.displayImageAlias(),
                    onOpen = onOpenImage,
                )
            }

            1 -> rack?.let { savedRack ->
                item {
                    RackOccupancyDiagram(
                        rack = savedRack,
                        title = "已归档 47U 机柜图",
                        selectedDeviceIndex = selectedDeviceIndex,
                        onDeviceClick = { index ->
                            selectedDeviceIndex = index
                            selectedTab = 2
                        },
                    )
                }
            } ?: item {
                EmptyCard("未保存机柜图", "该历史任务没有可读取的设备识别结果。")
            }

            else -> rack?.let { savedRack ->
                if (savedRack.uncertain.isNotEmpty() || savedRack.riskCandidates.isNotEmpty()) {
                    item { OutcomeReviewDetails(outcome) }
                }
                item {
                    Text("设备清单 · 归档只读", style = MaterialTheme.typography.labelMedium, color = Muted)
                }
                itemsIndexed(savedRack.devices, key = { index, device -> "$index-${device.type}-${device.bottomU}-${device.heightU}" }) { index, device ->
                    DeviceReadonlyRow(
                        index = index,
                        device = device,
                        selected = selectedDeviceIndex == index,
                        onClick = { selectedDeviceIndex = index },
                    )
                }
            } ?: item {
                EmptyCard("未保存设备清单", "该历史任务没有可读取的设备识别结果。")
            }
        }
        if (item.repairEvents.isNotEmpty() && selectedTab == 2) {
            item {
                Surface(shape = RoundedCornerShape(8.dp), color = SurfaceTint) {
                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("自动修复记录", style = MaterialTheme.typography.labelMedium, color = Navy)
                        item.repairEvents.forEach { event ->
                            Text(event, style = MaterialTheme.typography.labelSmall, color = Ink)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReviewSnapshotCard(item: TaskReviewSnapshot, onClick: () -> Unit) {
    val usedU = item.rack?.devices?.sumOf(Device::heightU) ?: 0
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Line),
    ) {
        Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
            MiniRackGlyph(usedU = usedU, compact = true)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(item.cabinetId ?: "柜号待确认", style = MaterialTheme.typography.titleSmall, color = Navy)
                Text(item.displayImageAlias(), style = MaterialTheme.typography.labelSmall, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(item.primaryReviewHint() ?: item.message.ifBlank { item.state }, style = MaterialTheme.typography.bodySmall, color = Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.Outlined.PlayArrow, contentDescription = "查看复核", tint = Blue, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun ReviewImagePanel(uri: Uri?, name: String, onOpen: (Uri, String) -> Unit) {
    val context = LocalContext.current
    val bitmap by produceState<android.graphics.Bitmap?>(initialValue = null, uri) {
        value = uri?.let { source ->
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { decodePreviewBitmap(context, source, maxDimension = 1280) }
        }
    }
    Surface(shape = RoundedCornerShape(10.dp), color = Color.White, border = BorderStroke(1.dp, Line)) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("原图证据", style = MaterialTheme.typography.titleSmall, color = Navy, modifier = Modifier.weight(1f))
                if (uri != null) TextButton(onClick = { onOpen(uri, name) }) { Text("放大查看") }
            }
            Box(
                modifier = Modifier.fillMaxWidth().height(210.dp).clip(RoundedCornerShape(8.dp)).background(SurfaceTint)
                    .let { modifier -> if (uri != null) modifier.clickable { onOpen(uri, name) } else modifier },
                contentAlignment = Alignment.Center,
            ) {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap!!.asImageBitmap(),
                        contentDescription = name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                    )
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Outlined.PhotoLibrary, contentDescription = null, tint = Blue)
                        Spacer(Modifier.height(5.dp))
                        Text(if (uri == null) "该历史任务未保留复核图片" else "正在读取归档图片", style = MaterialTheme.typography.bodySmall, color = Muted)
                    }
                }
            }
            Spacer(Modifier.height(7.dp))
            Text(name, style = MaterialTheme.typography.labelSmall, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun ReviewSummaryStrip(
    cabinetId: String,
    usedU: Int,
    reviewCount: Int,
    readOnly: Boolean,
) {
    Surface(shape = RoundedCornerShape(9.dp), color = Navy) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(cabinetId, style = MaterialTheme.typography.titleSmall, color = Color.White)
                Text(
                    "已占用 ${usedU}U · ${if (reviewCount > 0) "$reviewCount 项待复核" else "边界已记录"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.76f),
                )
            }
            Surface(shape = RoundedCornerShape(99.dp), color = if (reviewCount > 0) Color(0xFFFFDFA6) else Color(0xFFBDE8DF)) {
                Text(
                    if (readOnly) "归档只读" else if (reviewCount > 0) "需要复核" else "可确认",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (reviewCount > 0) Warning else Success,
                )
            }
        }
    }
}

@Composable
private fun ReviewTabSelector(selectedTab: Int, onSelected: (Int) -> Unit) {
    val tabs = listOf("原图", "机柜 U 位图", "设备清单")
    Surface(shape = RoundedCornerShape(8.dp), color = SurfaceTint) {
        Row(Modifier.padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            tabs.forEachIndexed { index, label ->
                val selected = selectedTab == index
                Surface(
                    modifier = Modifier.weight(1f).clickable { onSelected(index) },
                    shape = RoundedCornerShape(6.dp),
                    color = if (selected) Color.White else Color.Transparent,
                    shadowElevation = if (selected) 1.dp else 0.dp,
                ) {
                    Text(
                        label,
                        modifier = Modifier.padding(vertical = 8.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selected) Navy else Muted,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun RackOccupancyDiagram(
    rack: Rack,
    title: String,
    selectedDeviceIndex: Int? = null,
    onDeviceClick: ((Int) -> Unit)? = null,
    alarmHighlights: List<RackAlarmHighlight> = emptyList(),
    onAlarmClick: ((String) -> Unit)? = null,
) {
    val segments = remember(rack.devices) { rack.compressedDiagramSegments() }
    Surface(shape = RoundedCornerShape(9.dp), color = Color.White, border = BorderStroke(1.dp, Line)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = Navy, modifier = Modifier.weight(1f))
                Text("${rack.cabinetId} · ${rack.devices.sumOf(Device::heightU)}U", style = MaterialTheme.typography.labelSmall, color = Blue)
            }
            Text("连续空位已折叠；点击设备可定位到设备清单。", style = MaterialTheme.typography.labelSmall, color = Muted)
            segments.forEach { segment ->
                when (segment) {
                    is RackDiagramSegment.Empty -> {
                        Row(
                            modifier = Modifier.fillMaxWidth().height(25.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(segment.label, modifier = Modifier.width(56.dp), style = MaterialTheme.typography.labelSmall, color = Muted)
                            HorizontalDivider(modifier = Modifier.weight(1f), color = Line)
                            Spacer(Modifier.width(7.dp))
                            Text("空闲", style = MaterialTheme.typography.labelSmall, color = Muted)
                        }
                    }

                    is RackDiagramSegment.DeviceBlock -> {
                        val selected = selectedDeviceIndex == segment.index
                        val alarm = alarmHighlights.firstOrNull { it.coordinate.deviceIndex == segment.index }
                        val color = when (segment.device.type) {
                            "服务器" -> Navy
                            "交换机" -> Cyan
                            else -> Blue
                        }
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height((segment.device.heightU * 22).coerceAtLeast(30).dp)
                                .let { modifier ->
                                    if (alarm != null && onAlarmClick != null) {
                                        modifier.clickable { onAlarmClick(alarm.alarmId) }
                                    } else if (onDeviceClick != null) {
                                        modifier.clickable { onDeviceClick(segment.index) }
                                    } else modifier
                                },
                            shape = RoundedCornerShape(5.dp),
                            color = color,
                            border = when {
                                alarm != null -> BorderStroke(2.dp, alarmSeverityColor(alarm.severity))
                                selected -> BorderStroke(2.dp, Color(0xFFFFD166))
                                else -> null
                            },
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(segment.rangeLabel, modifier = Modifier.width(54.dp), style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.86f))
                                Text(
                                    "${segment.device.type} · ${segment.device.heightU}U",
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.White,
                                )
                                if (selected) {
                                    Text("已选", style = MaterialTheme.typography.labelSmall, color = Color.White)
                                }
                                alarm?.let {
                                    Text(
                                        if (it.count > 1) "${it.severity.label}${it.count}条" else it.severity.label,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private sealed interface RackDiagramSegment {
    data class Empty(val topU: Int, val bottomU: Int) : RackDiagramSegment {
        val label: String get() = if (topU == bottomU) "U$topU" else "U$topU-U$bottomU"
    }

    data class DeviceBlock(val index: Int, val device: Device) : RackDiagramSegment {
        val rangeLabel: String
            get() {
                val topU = (device.bottomU + device.heightU - 1).coerceAtMost(47)
                return if (topU == device.bottomU) "U$topU" else "U$topU-U${device.bottomU}"
            }
    }
}

private fun Rack.compressedDiagramSegments(): List<RackDiagramSegment> = buildList {
    var u = 47
    while (u >= 1) {
        val deviceIndex = devices.indexOfFirst { device -> u in device.bottomU until (device.bottomU + device.heightU) }
        if (deviceIndex >= 0) {
            val device = devices[deviceIndex]
            add(RackDiagramSegment.DeviceBlock(deviceIndex, device))
            u = (device.bottomU - 1).coerceAtMost(u - 1)
        } else {
            val topU = u
            while (u >= 1 && devices.none { device -> u in device.bottomU until (device.bottomU + device.heightU) }) {
                u -= 1
            }
            add(RackDiagramSegment.Empty(topU = topU, bottomU = u + 1))
        }
    }
}

@Composable
private fun DeviceReviewEditor(
    index: Int,
    device: Device,
    selected: Boolean,
    onSelect: () -> Unit,
    onSave: (String, Int, Int) -> Unit,
) {
    var type by remember(index, device.type, device.bottomU, device.heightU) { mutableStateOf(device.type) }
    var bottomText by remember(index, device.type, device.bottomU, device.heightU) { mutableStateOf(device.bottomU.toString()) }
    var heightU by remember(index, device.type, device.bottomU, device.heightU) { mutableStateOf(device.heightU) }
    var error by remember(index, device.type, device.bottomU, device.heightU) { mutableStateOf<String?>(null) }
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onSelect),
        shape = RoundedCornerShape(9.dp),
        color = if (selected) Color(0xFFF2F9F8) else Color.White,
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) Cyan else Line),
    ) {
        Column(Modifier.padding(11.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text("设备 ${index + 1} · ${device.evidence.ifBlank { "按可见边界登记" }}", style = MaterialTheme.typography.labelMedium, color = Muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                items(DeviceTypePolicy.allowedTypes.toList()) { option ->
                    FilterChip(selected = type == option, onClick = { type = option }, label = { Text(option) })
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = bottomText,
                    onValueChange = { bottomText = it.filter(Char::isDigit) },
                    modifier = Modifier.weight(1f),
                    label = { Text("下沿 U 位") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("设备高度", style = MaterialTheme.typography.labelSmall, color = Muted)
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        listOf(1, 2).forEach { value ->
                            FilterChip(selected = heightU == value, onClick = { heightU = value }, label = { Text("${value}U") })
                        }
                    }
                }
            }
            error?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = Danger) }
            OutlinedButton(
                onClick = {
                    val bottomU = bottomText.toIntOrNull()
                    if (bottomU == null) {
                        error = "请输入 1 至 47 的设备下沿 U 位。"
                    } else {
                        error = null
                        onSave(type, bottomU, heightU)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
            ) { Text("保存本项调整") }
        }
    }
}

@Composable
private fun DeviceReadonlyRow(
    index: Int,
    device: Device,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val topU = device.bottomU + device.heightU - 1
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = if (selected) Color(0xFFF2F9F8) else Color.White,
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) Cyan else Line),
    ) {
        Row(Modifier.padding(horizontal = 11.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("${index + 1}", modifier = Modifier.width(24.dp), style = MaterialTheme.typography.labelMedium, color = Blue)
            Column(Modifier.weight(1f)) {
                Text(device.type, style = MaterialTheme.typography.bodyMedium, color = Ink, fontWeight = FontWeight.SemiBold)
                Text("U$topU-U${device.bottomU} · ${device.heightU}U", style = MaterialTheme.typography.labelSmall, color = Muted)
            }
            if (selected) Text("已定位", style = MaterialTheme.typography.labelSmall, color = Cyan)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FullscreenImageGalleryDialog(gallery: FullscreenImageGallery, onDismiss: () -> Unit) {
    if (gallery.images.isEmpty()) return
    val pagerState = rememberPagerState(
        initialPage = gallery.initialPage.coerceIn(0, gallery.images.lastIndex),
        pageCount = { gallery.images.size },
    )
    val scope = rememberCoroutineScope()
    var activeScale by remember { mutableStateOf(1f) }
    val currentImage = gallery.images[pagerState.currentPage]

    LaunchedEffect(pagerState.currentPage) {
        activeScale = 1f
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFF071A2B),
        ) {
            Box(Modifier.fillMaxSize()) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    beyondViewportPageCount = 0,
                    userScrollEnabled = activeScale <= 1.01f,
                    key = { page -> gallery.images[page].id },
                ) { page ->
                    ZoomableGalleryImage(
                        image = gallery.images[page],
                        isCurrentPage = page == pagerState.currentPage,
                        onScaleChanged = { scale ->
                            if (page == pagerState.currentPage) activeScale = scale
                        },
                    )
                }

                Row(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(7.dp),
                        color = Color.Black.copy(alpha = 0.48f),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "${pagerState.currentPage + 1} / ${gallery.images.size}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Cyan,
                                fontWeight = FontWeight.Bold,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = currentImage.name,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (activeScale > 1.01f) {
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "${(activeScale * 100).toInt()}%",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.75f),
                                )
                            }
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    Surface(shape = RoundedCornerShape(99.dp), color = Color.Black.copy(alpha = 0.48f)) {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Outlined.Close, contentDescription = "关闭图片预览", tint = Color.White)
                        }
                    }
                }

                if (gallery.images.size > 1) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 24.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        GalleryPageButton(
                            contentDescription = "上一张图片",
                            enabled = pagerState.currentPage > 0,
                            onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) } },
                        ) {
                            Icon(Icons.Outlined.ArrowBack, contentDescription = null, tint = Color.White)
                        }
                        Surface(shape = RoundedCornerShape(99.dp), color = Color.Black.copy(alpha = 0.48f)) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                galleryIndicatorPages(
                                    totalPages = gallery.images.size,
                                    currentPage = pagerState.currentPage,
                                ).forEach { page ->
                                    Box(
                                        modifier = Modifier
                                            .size(if (page == pagerState.currentPage) 7.dp else 5.dp)
                                            .clip(RoundedCornerShape(99.dp))
                                            .background(if (page == pagerState.currentPage) Cyan else Color.White.copy(alpha = 0.48f)),
                                    )
                                }
                            }
                        }
                        GalleryPageButton(
                            contentDescription = "下一张图片",
                            enabled = pagerState.currentPage < gallery.images.lastIndex,
                            onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) } },
                        ) {
                            Icon(
                                Icons.Outlined.ArrowBack,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.graphicsLayer(rotationZ = 180f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GalleryPageButton(
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Surface(shape = RoundedCornerShape(99.dp), color = Color.Black.copy(alpha = if (enabled) 0.48f else 0.22f)) {
        IconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.semantics { this.contentDescription = contentDescription },
            content = content,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ZoomableGalleryImage(
    image: FullscreenImage,
    isCurrentPage: Boolean,
    onScaleChanged: (Float) -> Unit,
) {
    val context = LocalContext.current
    val bitmap by produceState<android.graphics.Bitmap?>(initialValue = null, image.uri) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            decodePreviewBitmap(context, image.uri, maxDimension = 2560)
        }
    }
    var scale by remember(image.id) { mutableStateOf(1f) }
    var offset by remember(image.id) { mutableStateOf(Offset.Zero) }

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize().clipToBounds(),
        contentAlignment = Alignment.Center,
    ) {
        val density = LocalDensity.current
        val viewportWidth = with(density) { (maxWidth - 24.dp).coerceAtLeast(1.dp).toPx() }
        val viewportHeight = with(density) { (maxHeight - 24.dp).coerceAtLeast(1.dp).toPx() }
        val imageRatio = bitmap?.let { it.width.toFloat() / it.height.toFloat() } ?: 1f
        val viewportRatio = viewportWidth / viewportHeight
        val fittedWidth = if (imageRatio >= viewportRatio) viewportWidth else viewportHeight * imageRatio
        val fittedHeight = if (imageRatio >= viewportRatio) viewportWidth / imageRatio else viewportHeight
        val transformState = rememberTransformableState { zoomChange, panChange, _ ->
            val nextScale = (scale * zoomChange).coerceIn(1f, 4f)
            val maxPanX = ((fittedWidth * nextScale - viewportWidth) / 2f).coerceAtLeast(0f)
            val maxPanY = ((fittedHeight * nextScale - viewportHeight) / 2f).coerceAtLeast(0f)
            scale = nextScale
            offset = Offset(
                x = (offset.x + panChange.x).coerceIn(-maxPanX, maxPanX),
                y = (offset.y + panChange.y).coerceIn(-maxPanY, maxPanY),
            )
        }

        LaunchedEffect(isCurrentPage) {
            if (isCurrentPage) {
                scale = 1f
                offset = Offset.Zero
            }
        }
        LaunchedEffect(scale, isCurrentPage) {
            if (isCurrentPage) onScaleChanged(scale)
        }

        if (bitmap == null) {
            CircularProgressIndicator(color = Cyan)
        } else {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = image.name,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                        transformOrigin = TransformOrigin.Center
                    }
                    .transformable(
                        state = transformState,
                        canPan = { scale > 1.01f },
                    ),
                contentScale = ContentScale.Fit,
            )
        }
    }
}

private fun ImageOutcome.reviewUri(state: RackExcelUiState): Uri? {
    reviewImagePath?.let { path ->
        val file = File(path)
        if (file.isFile) return Uri.fromFile(file)
    }
    return state.images.firstOrNull { it.id == imageId }?.uri
}

private fun TaskReviewSnapshot.reviewUri(): Uri? = reviewImagePath
    ?.let(::File)
    ?.takeIf(File::isFile)
    ?.let(Uri::fromFile)

private fun historyDeliveryStatus(state: RackExcelUiState, item: TaskHistoryItem): String? {
    val status = state.status
    val appliesToItem = state.desktopTransfer.taskId == item.taskId
    return status.takeIf {
            appliesToItem ||
            it.startsWith("已交由系统打开历史 Excel") ||
            it.startsWith("已打开历史文件的系统分享面板") ||
            it.startsWith("该任务未保存本地文件") ||
            it.startsWith("该历史任务没有")
    }
}

private fun historyDeliveryError(state: RackExcelUiState, item: TaskHistoryItem): String? {
    val error = state.error ?: return null
    val appliesToItem = state.desktopTransfer.taskId == item.taskId
    return error.takeIf {
        appliesToItem ||
            it.startsWith("打开历史文件出现问题") ||
            it.startsWith("分享历史文件出现问题") ||
            it.startsWith("请先在设置中连接电脑")
    }
}

@Composable
private fun StatusStrip(status: String, error: String?) {
    Surface(color = if (error == null) Color(0xFFEAF2FF) else Color(0xFFFFEEEE), shape = RoundedCornerShape(8.dp)) {
        Text(
            status,
            modifier = Modifier.padding(12.dp),
            style = MaterialTheme.typography.bodySmall,
            color = if (error == null) Blue else Danger,
        )
    }
}

@Composable
private fun ErrorCard(message: String) {
    Surface(color = Color(0xFFFFEEEE), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
        Text(message, modifier = Modifier.padding(12.dp), color = Danger, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun EmptyCard(title: String, detail: String) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Line),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(5.dp))
            Text(detail, style = MaterialTheme.typography.bodySmall, color = Muted)
        }
    }
}

private fun TaskHistoryItem.displayTaskName(): String = roomName.trim()
    .takeIf { it.isNotBlank() && it != "未命名机房" }
    ?: "现场采集 · ${formatCompactTime(startedAtMillis)}"

private fun TaskHistoryItem.primaryReviewHint(): String? = reviewItems.asSequence()
    .mapNotNull(TaskReviewSnapshot::primaryReviewHint)
    .firstOrNull()
    ?: if (summary.pendingCount > 0) "设备边界待人工核对" else null

private fun TaskReviewSnapshot.primaryReviewHint(): String? = uncertain.firstOrNull()
    ?: riskTexts.firstOrNull()
    ?: message.takeIf { it.contains("复核") || it.contains("遮挡") || it.contains("边界") }

/** Keeps task/export messages from leaking into the model settings editor. */
private fun modelSettingsStatus(state: RackExcelUiState): Pair<String?, String?> {
    val status = state.status.takeIf { value ->
        value.isNotBlank() && (
            value.startsWith("模型配置") ||
            value.startsWith("已切换模型配置") ||
            value.startsWith("已新建模型配置") ||
            value.startsWith("已删除模型配置") ||
            value.startsWith("平台默认配置") ||
            value.startsWith("智能识别引擎") ||
            state.isTestingConnection
        )
    }
    val error = state.error?.takeIf { value ->
        value.startsWith("请填写模型配置") ||
            value.startsWith("配置保存出现问题") ||
            value.startsWith("模型配置") ||
            state.isTestingConnection ||
            state.status.startsWith("智能识别引擎")
    }
    return status to error
}

private fun ImageOutcome.displayImageAlias(position: Int? = null): String = cabinetId
    ?.takeIf { it.isNotBlank() }
    ?.let { "$it · 原图" }
    ?: position?.let { "第${it}张原图" }
    ?: "待识别原图"

private fun TaskReviewSnapshot.displayImageAlias(): String = cabinetId
    ?.takeIf { it.isNotBlank() }
    ?.let { "$it · 原图" }
    ?: "归档原图"

private fun reviewCount(outcome: ImageOutcome): Int = outcome.rack?.uncertain?.size ?: 0

private fun formatCompactTime(value: Long): String =
    SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(Date(value))

private fun formatTime(value: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date(value))

private fun formatDuration(value: Long): String {
    val seconds = (value / 1_000).coerceAtLeast(0L)
    val hours = seconds / 3_600
    val minutes = (seconds % 3_600) / 60
    val remaining = seconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(Locale.ROOT, hours, minutes, remaining)
    } else {
        "%02d:%02d".format(Locale.ROOT, minutes, remaining)
    }
}
