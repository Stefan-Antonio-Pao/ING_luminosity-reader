package com.lumiread.camera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.lumiread.R
import com.lumiread.ui.components.CaptureHint
import com.lumiread.ui.components.ErrorCard
import com.lumiread.ui.components.LumiCaptureButton
import com.lumiread.ui.components.LumiIconButton
import com.lumiread.ui.components.LumiScreenBackground
import com.lumiread.ui.components.PromptChip
import com.lumiread.ui.screens.tierRes
import com.lumiread.ui.theme.LocalTier
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * CameraX 拍照取景屏 —— v3.0.0 取景器(components.md#camera-viewfinder)。
 *
 * 视觉:黑底 + 四角金色取景框 + 顶部半透明提示胶囊 + 底部 88/96dp 暖金大快门 + 反白图标按钮。
 * 逻辑保持不变:[LifecycleCameraController] + `CAPTURE_MODE_MAXIMIZE_QUALITY` 单拍;
 * 每张拍完进 [CropConfirmScreen] 手动裁切;一批裁好的路径累计,"完成 (N)" 一次交给链路。
 */
@Composable
fun CameraCaptureScreen(
    onCaptured: (paths: List<String>) -> Unit,
    onOpenSettings: () -> Unit,
    onCancel: () -> Unit,
    @Suppress("UNUSED_PARAMETER") onStartChatDirect: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val tier = LocalTier.current

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED
        )
    }
    val permLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted -> hasPermission = granted }

    LaunchedEffect(Unit) { if (!hasPermission) permLauncher.launch(Manifest.permission.CAMERA) }

    if (!hasPermission) {
        // 缺权限:友好说明(先解释为什么需要,再给行动),不报错(accessibility §6)。
        LumiScreenBackground(decor = false) {
            Box(Modifier.fillMaxSize().statusBarsPadding(), contentAlignment = Alignment.Center) {
                ErrorCard(
                    iconRes = R.drawable.ic_lumi_camera,
                    title = stringResource(R.string.lr_err_perm_t),
                    body = stringResource(R.string.permission_camera_body),
                    actionLabel = stringResource(R.string.btn_grant_permission),
                    onAction = { permLauncher.launch(Manifest.permission.CAMERA) },
                    homeLabel = stringResource(R.string.lr_err_home),
                    onHome = onCancel,
                    soft = true,
                )
            }
        }
        return
    }

    val controller = remember(context) {
        LifecycleCameraController(context).apply {
            setEnabledUseCases(CameraController.IMAGE_CAPTURE)
            imageCaptureMode = ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY
        }
    }
    DisposableEffect(lifecycleOwner) {
        controller.bindToLifecycle(lifecycleOwner)
        onDispose { controller.unbind() }
    }

    val capturedPaths = remember { mutableStateListOf<String>() }
    var capturing by remember { mutableStateOf(false) }
    var lastError by remember { mutableStateOf<String?>(null) }
    var pendingCropPath by remember { mutableStateOf<String?>(null) }

    val pending = pendingCropPath
    if (pending != null) {
        CropConfirmScreen(
            srcPath = pending,
            onConfirm = { croppedPath ->
                runCatching { File(pending).delete() }
                capturedPaths += croppedPath
                pendingCropPath = null
            },
            onRetake = {
                runCatching { File(pending).delete() }
                pendingCropPath = null
            },
        )
        return
    }

    Box(Modifier.fillMaxSize().background(com.lumiread.ui.theme.LumiColors.CameraDark)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx -> PreviewView(ctx).also { it.controller = controller } },
        )

        // 四角金色取景框(居中方形)
        Image(
            painter = painterResource(R.drawable.illu_camera_corners),
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(0.84f)
                .aspectRatio(1f),
        )

        // 顶部:取消(反白)+ 提示胶囊
        Box(
            modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(12.dp),
        ) {
            LumiIconButton(
                resId = R.drawable.ic_lumi_close,
                contentDescription = stringResource(R.string.lr_camera_cancel_cd),
                onClick = onCancel,
                onDark = true,
                modifier = Modifier.align(Alignment.TopStart),
            )
            CaptureHint(
                text = stringResource(tierRes(tier, R.string.lr_camera_hint_toddler, R.string.lr_camera_hint_preschool, R.string.lr_camera_hint_pre)),
                modifier = Modifier.align(Alignment.TopCenter).padding(horizontal = 56.dp),
            )
        }

        // 底部:设置 / 大快门 / 完成(N)
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (capturedPaths.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.45f), androidx.compose.foundation.shape.RoundedCornerShape(999.dp))
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                ) { Text(stringResource(R.string.lr_camera_status, capturedPaths.size), color = Color.White) }
            }
            lastError?.let {
                Text(stringResource(R.string.capture_failed, it), color = com.lumiread.ui.theme.LumiColors.Gold300)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                LumiIconButton(
                    resId = R.drawable.ic_lumi_settings,
                    contentDescription = stringResource(R.string.lr_camera_settings_cd),
                    onClick = onOpenSettings,
                    onDark = true,
                )
                LumiCaptureButton(
                    onClick = {
                        if (capturing) return@LumiCaptureButton
                        capturing = true
                        lastError = null
                        scope.launch {
                            runCatching { takePictureToCache(context, controller) }
                                .onSuccess { capturing = false; pendingCropPath = it }
                                .onFailure { capturing = false; lastError = it.message ?: it.javaClass.simpleName }
                        }
                    },
                    contentDescription = stringResource(R.string.lr_camera_shutter_cd),
                    enabled = !capturing,
                )
                // 完成(N):有图才显示,否则占位保持快门居中
                if (capturedPaths.isNotEmpty()) {
                    PromptChip(
                        text = stringResource(R.string.lr_camera_done, capturedPaths.size),
                        onClick = {
                            val snapshot = capturedPaths.toList()
                            capturedPaths.clear()
                            onCaptured(snapshot)
                        },
                        gold = true,
                    )
                } else {
                    androidx.compose.foundation.layout.Spacer(Modifier.size(48.dp))
                }
            }
        }
    }
}


private suspend fun takePictureToCache(
    context: Context,
    controller: LifecycleCameraController,
): String = suspendCancellableCoroutine { cont ->
    val outFile = File(context.cacheDir, "capture-${System.currentTimeMillis()}.jpg")
    val options = ImageCapture.OutputFileOptions.Builder(outFile).build()
    val executor = ContextCompat.getMainExecutor(context)
    controller.takePicture(
        options,
        executor,
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(output: ImageCapture.OutputFileResults) = cont.resume(outFile.absolutePath)
            override fun onError(exc: ImageCaptureException) = cont.resumeWithException(exc)
        },
    )
}
