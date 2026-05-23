package com.lumiread.vision

import com.google.android.gms.tasks.Task
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * 把 Google Play Services [Task] 桥到 coroutines。
 *
 * 故意手写而非引入 `kotlinx-coroutines-play-services` —— 实现就 8 行,
 * 一个依赖换不到几行代码不划算(也避开了它跟我们 coroutines 版本对齐的问题)。
 */
internal suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { cont.resume(it) }
    addOnFailureListener { cont.resumeWithException(it) }
    addOnCanceledListener { cont.cancel() }
}
