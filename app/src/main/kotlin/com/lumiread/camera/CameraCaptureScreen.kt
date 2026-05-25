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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.lumiread.R
import com.lumiread.ui.components.AppBackground
import com.lumiread.ui.components.LumiOutlinedButton
import com.lumiread.ui.components.LumiPrimaryButton
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * CameraX 多张拍照取景屏。
 *
 * - 用 [LifecycleCameraController] + [PreviewView] 包进 Compose 的 [AndroidView]。
 * - 模式 = `CAPTURE_MODE_MAXIMIZE_QUALITY`,绘本是静态近景 → 单次 `takePicture`。
 * - **拍后手动裁切**:每张拍完进入 [CropConfirmScreen],用户拖矩形选区确认才入批。
 * - **多张支持**:一批裁好的路径累计在 `capturedPaths`,点"完成 (N)"一次性交给 Pipeline。
 * - 拍完写到 `context.cacheDir`,裁切后再写一份 `crop-*.jpg`,原图删除。
 * - 权限走 `ActivityResultContracts.RequestPermission`。
 */
@Composable
fun CameraCaptureScreen(
    onCaptured: (paths: List<String>) -> Unit,
    onOpenSettings: () -> Unit,
    onCancel: () -> Unit,
    /**
     * 可选:"💬 直接开始对话"按钮的回调。非空时,在首张照片拍出来之前显示在拍照按钮旁边。
     * 用于"无绘本、直接聊"路径(LumiReadApp 在 NEW intent + 空批时传入)。
     */
    onStartChatDirect: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED
        )
    }

    val permLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted -> hasPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasPermission) permLauncher.launch(Manifest.permission.CAMERA)
    }

    if (!hasPermission) {
        PermissionRationale(
            onRetry = { permLauncher.launch(Manifest.permission.CAMERA) },
            onCancel = onCancel,
        )
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

    // 多张:累计本批已确认裁切的路径
    val capturedPaths = remember { mutableStateListOf<String>() }
    var capturing by remember { mutableStateOf(false) }
    var lastError by remember { mutableStateOf<String?>(null) }
    // 拍完但还没确认裁切的原图;非空时显示裁切页,空时显示相机预览
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

    AppBackground {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        PreviewView(ctx).also { it.controller = controller }
                    },
                )
                // 左上返回:半透明圆形浮层,适合任意相机画面;走 onCancel ——
                // 有进行中会话回 CHAT,否则在 LumiReadApp 里 fall back 到 Activity.finish
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp)
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.4f))
                        .clickable(onClick = onCancel),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("←", color = Color.White, fontSize = 24.sp)
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    stringResource(R.string.capture_status_hint, capturedPaths.size),
                    style = MaterialTheme.typography.bodyMedium,
                )
                lastError?.let {
                    Text(stringResource(R.string.capture_failed, it), color = MaterialTheme.colorScheme.error)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LumiPrimaryButton(
                        modifier = Modifier.weight(1f),
                        enabled = !capturing,
                        label = stringResource(if (capturing) R.string.capture_in_progress else R.string.btn_capture_another),
                        onClick = {
                            capturing = true
                            lastError = null
                            scope.launch {
                                runCatching { takePictureToCache(context, controller) }
                                    .onSuccess { rawPath ->
                                        capturing = false
                                        pendingCropPath = rawPath
                                    }
                                    .onFailure { err ->
                                        capturing = false
                                        lastError = err.message ?: err.javaClass.simpleName
                                    }
                            }
                        },
                    )

                    // 仅在"还没拍过、又允许直接开聊"时显示 —— 一旦拍出第一张,就锁定走拍照流。
                    if (onStartChatDirect != null && capturedPaths.isEmpty()) {
                        LumiOutlinedButton(
                            enabled = !capturing,
                            onClick = onStartChatDirect,
                            label = stringResource(R.string.btn_chat_direct),
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    LumiPrimaryButton(
                        modifier = Modifier.weight(1f),
                        enabled = capturedPaths.isNotEmpty() && !capturing,
                        label = stringResource(R.string.btn_done_with_count, capturedPaths.size),
                        onClick = {
                            val snapshot = capturedPaths.toList()
                            capturedPaths.clear()
                            onCaptured(snapshot)
                        },
                    )

                    LumiOutlinedButton(
                        modifier = Modifier.weight(1f),
                        enabled = capturedPaths.isNotEmpty() && !capturing,
                        label = stringResource(R.string.btn_clear),
                        onClick = {
                            // 顺手删本批的缓存文件,避免无限堆积
                            capturedPaths.forEach { runCatching { File(it).delete() } }
                            capturedPaths.clear()
                        },
                    )
                }

                LumiOutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onOpenSettings,
                    label = stringResource(R.string.btn_settings),
                )
            }
        }
    }
}

@Composable
private fun PermissionRationale(onRetry: () -> Unit, onCancel: () -> Unit) {
    AppBackground {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.permission_camera_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.permission_camera_body))
            LumiPrimaryButton(onClick = onRetry, label = stringResource(R.string.btn_grant_permission))
            LumiOutlinedButton(onClick = onCancel, label = stringResource(R.string.btn_back))
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
            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                cont.resume(outFile.absolutePath)
            }
            override fun onError(exc: ImageCaptureException) {
                cont.resumeWithException(exc)
            }
        },
    )
}
