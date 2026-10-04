package io.github.menglian18.skyworkbench.ui.common

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

/**
 * 联网取物品图（1.1 改进 · C2 / C3，方案 A：图片不打进安装包）。
 *
 * 为什么不引图片库：这个项目一直保持"不引不必要的依赖"，一个几十行的取图组件够用了。
 *
 * 行为：
 *  - 先用内存缓存；没有就查本地缓存文件；再没有才联网；
 *  - 取回来的图**存在 App 私有的缓存目录**里，下次打开就不用再联网；
 *  - 断网、网址为空、取回来不是图片 —— 一律显示占位方块，**不崩、不空白转圈**；
 *  - 图片来自 sky-planner.com 与光遇 wiki，版权属游戏方，这里只做"联网显示"。
 */
object RemoteImageCache {

    private val memory = ConcurrentHashMap<String, Bitmap>()

    /** 已经在内存里的图（界面首帧就能显示，避免闪一下占位） */
    fun cached(url: String): Bitmap? = if (url.isBlank()) null else memory[url]

    suspend fun load(context: Context, url: String): Bitmap? {
        if (url.isBlank()) return null
        memory[url]?.let { return it }
        return withContext(Dispatchers.IO) {
            val file = File(context.cacheDir, "img/" + url.hashCode().toString(16) + ".img")
            val bitmap = runCatching {
                if (file.exists()) {
                    BitmapFactory.decodeFile(file.absolutePath)
                } else {
                    val bytes = download(url)
                    if (bytes == null) {
                        null
                    } else {
                        runCatching {
                            file.parentFile?.mkdirs()
                            file.writeBytes(bytes)
                        }
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    }
                }
            }.getOrNull()
            if (bitmap != null) memory[url] = bitmap
            bitmap
        }
    }

    private fun download(url: String): ByteArray? = runCatching {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000
            readTimeout = 8000
            requestMethod = "GET"
            setRequestProperty("User-Agent", "sky-workbench")
        }
        try {
            connection.inputStream.use { it.readBytes() }
        } finally {
            connection.disconnect()
        }
    }.getOrNull()
}

/** 一张网络图；没取到就显示一个小方块占位 */
@Composable
fun RemoteImage(
    url: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    imageSize: Dp = 40.dp,
) {
    val context = LocalContext.current
    var bitmap by remember(url) { mutableStateOf(RemoteImageCache.cached(url)) }

    LaunchedEffect(url) {
        if (bitmap == null && url.isNotBlank()) {
            bitmap = RemoteImageCache.load(context, url)
        }
    }

    val loaded = bitmap
    if (loaded != null) {
        Image(
            bitmap = loaded.asImageBitmap(),
            contentDescription = contentDescription,
            modifier = modifier.size(imageSize),
            contentScale = ContentScale.Fit,
        )
    } else {
        Box(
            modifier = modifier.size(imageSize),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "▫",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
