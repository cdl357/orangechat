/*
 * 橘瓣 OrangeChat - 朋友圈
 */
package me.rerere.rikkahub.ui.pages.moments

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rerere.rikkahub.ui.components.nav.BackButton
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

private const val SUPA_URL = "https://byqqwypdfiwvalozihgs.supabase.co"
private const val SUPA_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImJ5cXF3eXBkZml3dmFsb3ppaGdzIiwicm9sZSI6InNlcnZpY2Vfcm9sZSIsImlhdCI6MTc4MzY1NDA4MCwiZXhwIjoyMDk5MjMwMDgwfQ.LIbE9DFsLSRhOig5bUUfUP4r7t1ykdNy8L0gZM_xtGw"
private const val BUCKET = "moments"

// ── 配色：暖白纸底 ────────────────────────────────────────────
private val PageBg = Color(0xFFFCFAF6)
private val CardBg = Color(0xFFFFFFFF)
private val BannerFallback = Color(0xFFEFE7DA)
private val InkMain = Color(0xFF33322E)
private val InkSub = Color(0xFF4A4844)
private val InkMuted = Color(0xFF9A968C)
private val InkFaint = Color(0xFFBDB8AE)
private val LineColor = Color(0xFFEDE7DC)
private val AccentRed = Color(0xFFC9705F)
private val SeanName = Color(0xFFB4695A)
private val YuriName = Color(0xFFC48A6A)
private val SeanA = Color(0xFF7E8C93)
private val SeanB = Color(0xFFA3B0B4)
private val YuriA = Color(0xFFE0A177)
private val YuriB = Color(0xFFEFC7A2)

private val Serif = FontFamily.Serif

/**
 * 图片附件：现在存在 moments / moment_comments 的 images 列（text[]，2026-08-09 加的）。
 *
 * 早先没有这一列（只有 PostgREST 权限，不能 ALTER TABLE），图片 URL 是塞在 content 末尾的
 * 隐藏标记里：正文 + "\n<!--imgs:url1|url2-->"。下面的 stripImages / parseImages 保留下来
 * 读旧数据，写入一律走 images 列，不再产生新的隐藏标记。
 */
private val IMG_REGEX = Regex("""<!--imgs:(.*?)-->""", RegexOption.DOT_MATCHES_ALL)

private fun stripImages(raw: String): String = IMG_REGEX.replace(raw, "").trim()

private fun parseImages(raw: String): List<String> =
    IMG_REGEX.find(raw)?.groupValues?.get(1)
        ?.split("|")?.map { it.trim() }?.filter { it.isNotBlank() }
        ?: emptyList()

data class Moment(
    val id: String,
    val author: String,
    val content: String,
    val images: List<String>,
    val yuriLiked: Boolean,
    val liked: Boolean,
    val replyContent: String?,
    val replyStatus: String,
    val createdAt: String,
)

data class MomentComment(
    val id: String,
    val momentId: String,
    val author: String,
    val content: String,
    val images: List<String>,
    val replyStatus: String,
    val createdAt: String,
)

/** author 历史上混用过 "sean"/"沈聿淮"，统一判断"是不是他发的"。 */
private fun isSean(author: String): Boolean =
    author != "yuri" && author != "Yuri" && author != "小鑫" && author != "李雨鑫"

// ── 网络 ────────────────────────────────────────────────────

/** PostgREST 把 text[] 返回成 JSON 数组；列为 null 时给空列表。 */
private fun readImages(o: JSONObject): List<String> {
    if (o.isNull("images")) return emptyList()
    val arr = o.optJSONArray("images") ?: return emptyList()
    return (0 until arr.length()).map { arr.optString(it, "") }.filter { it.isNotBlank() }
}

private fun conn(url: String, method: String = "GET"): HttpURLConnection =
    (URL(url).openConnection() as HttpURLConnection).apply {
        requestMethod = method
        setRequestProperty("apikey", SUPA_KEY)
        setRequestProperty("Authorization", "Bearer $SUPA_KEY")
        setRequestProperty("Accept", "application/json")
        connectTimeout = 20000
        readTimeout = 20000
    }

private fun writeJson(c: HttpURLConnection, payload: String) {
    c.doOutput = true
    c.setRequestProperty("Content-Type", "application/json")
    c.setRequestProperty("Prefer", "return=minimal")
    OutputStreamWriter(c.outputStream, Charsets.UTF_8).use { it.write(payload) }
    c.responseCode
}

fun fetchMoments(): List<Moment> {
    val body = conn("$SUPA_URL/rest/v1/moments?order=created_at.desc&limit=50")
        .inputStream.bufferedReader().use { it.readText() }
    val arr = JSONArray(body)
    return (0 until arr.length()).map { i ->
        val o = arr.getJSONObject(i)
        val raw = o.optString("content", "")
        Moment(
            id = o.getString("id"),
            author = o.optString("author", "sean"),
            content = stripImages(raw),
            // images 列是新加的；老数据的图片还在 content 的隐藏标记里，回退解析一次
            images = readImages(o).ifEmpty { parseImages(raw) },
            yuriLiked = o.optBoolean("yuri_liked", false),
            liked = o.optBoolean("liked", false),
            replyContent = if (o.isNull("reply_content")) null else o.optString("reply_content"),
            replyStatus = o.optString("reply_status", "done"),
            createdAt = o.optString("created_at", ""),
        )
    }
}

/** 一次把这批动态的评论全拉回来，避免每条动态一个请求。 */
fun fetchComments(momentIds: List<String>): List<MomentComment> {
    if (momentIds.isEmpty()) return emptyList()
    val inList = momentIds.joinToString(",")
    val body = conn("$SUPA_URL/rest/v1/moment_comments?moment_id=in.($inList)&order=created_at.asc")
        .inputStream.bufferedReader().use { it.readText() }
    val arr = JSONArray(body)
    return (0 until arr.length()).map { i ->
        val o = arr.getJSONObject(i)
        val raw = o.optString("content", "")
        MomentComment(
            id = o.getString("id"),
            momentId = o.optString("moment_id", ""),
            author = o.optString("author", "yuri"),
            content = stripImages(raw),
            images = readImages(o).ifEmpty { parseImages(raw) },
            replyStatus = o.optString("reply_status", "done"),
            createdAt = o.optString("created_at", ""),
        )
    }
}

fun postMoment(content: String, images: List<String>, author: String = "yuri") {
    // reply_status=pending 才会让服务器 worker 去生成回复。
    // 早先这里写死 "done"，等于告诉后台"这条不用回"，所以小鑫发的动态永远等不到回应。
    val payload = JSONObject().apply {
        put("author", author)
        put("content", content)
        put("images", JSONArray(images))
        put("reply_status", if (author == "yuri") "pending" else "done")
    }.toString()
    writeJson(conn("$SUPA_URL/rest/v1/moments", "POST"), payload)
}

fun postComment(momentId: String, content: String, images: List<String>, author: String = "yuri") {
    val payload = JSONObject().apply {
        put("moment_id", momentId)
        put("author", author)
        put("content", content)
        put("images", JSONArray(images))
        put("reply_status", if (author == "yuri") "pending" else "done")
    }.toString()
    writeJson(conn("$SUPA_URL/rest/v1/moment_comments", "POST"), payload)
}

fun toggleYuriLike(momentId: String, current: Boolean) {
    val payload = JSONObject().apply { put("yuri_liked", !current) }.toString()
    writeJson(conn("$SUPA_URL/rest/v1/moments?id=eq.$momentId", "PATCH"), payload)
}

fun deleteMoment(momentId: String) {
    conn("$SUPA_URL/rest/v1/moment_comments?moment_id=eq.$momentId", "DELETE").responseCode
    conn("$SUPA_URL/rest/v1/moments?id=eq.$momentId", "DELETE").responseCode
}

/**
 * 上传图片到 Supabase Storage 的 moments 桶（public），返回公开 URL。
 * 失败返回空串，调用方降级用本地文件路径（只有本机能看到）。
 */
fun uploadImage(context: Context, uri: Uri): String = runCatching {
    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return ""
    val name = "m_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}.jpg"
    val c = (URL("$SUPA_URL/storage/v1/object/$BUCKET/$name").openConnection() as HttpURLConnection).apply {
        requestMethod = "POST"
        doOutput = true
        setRequestProperty("apikey", SUPA_KEY)
        setRequestProperty("Authorization", "Bearer $SUPA_KEY")
        setRequestProperty("Content-Type", "image/jpeg")
        setRequestProperty("x-upsert", "true")
        connectTimeout = 30000
        readTimeout = 30000
    }
    c.outputStream.use { it.write(bytes) }
    if (c.responseCode in 200..299) "$SUPA_URL/storage/v1/object/public/$BUCKET/$name" else ""
}.getOrDefault("")

/** 存一份到 App 私有目录，作为上传失败时的兜底显示。文件名带时间戳，避免 Coil 缓存不刷新。 */
private fun saveLocal(context: Context, uri: Uri, prefix: String): String = runCatching {
    val dir = File(context.filesDir, "moments_images").apply { mkdirs() }
    val out = File(dir, "${prefix}_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}.jpg")
    context.contentResolver.openInputStream(uri)?.use { input ->
        FileOutputStream(out).use { input.copyTo(it) }
    }
    out.absolutePath
}.getOrDefault("")

// ── 装修配置（本机 SharedPreferences；只有小鑫会看这个页面，不需要同步到云端）──

private const val PREFS = "moments_decor"

private class Decor(context: Context) {
    private val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    fun get(key: String, def: String = "") = sp.getString(key, def) ?: def
    fun put(key: String, value: String) = sp.edit().putString(key, value).apply()
}

private const val K_BG = "background"
private const val K_AV_SEAN = "avatar_sean"
private const val K_AV_YURI = "avatar_yuri"
private const val K_TITLE = "banner_title"
private const val K_SUB = "banner_sub"

// ── 页面 ────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MomentsPage() {
    val context = LocalContext.current
    val decor = remember { Decor(context) }

    var moments by remember { mutableStateOf<List<Moment>>(emptyList()) }
    var comments by remember { mutableStateOf<Map<String, List<MomentComment>>>(emptyMap()) }
    var loading by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }

    var background by remember { mutableStateOf(decor.get(K_BG)) }
    var avatarSean by remember { mutableStateOf(decor.get(K_AV_SEAN)) }
    var avatarYuri by remember { mutableStateOf(decor.get(K_AV_YURI)) }
    var bannerTitle by remember { mutableStateOf(decor.get(K_TITLE, "ours, quietly.")) }
    var bannerSub by remember { mutableStateOf(decor.get(K_SUB, "LITTLE THINGS WE KEEP")) }

    var showDecorDialog by remember { mutableStateOf(false) }
    var editBannerText by remember { mutableStateOf(false) }
    var commentingOn by remember { mutableStateOf<Moment?>(null) }
    var confirmDelete by remember { mutableStateOf<Moment?>(null) }
    var viewingImage by remember { mutableStateOf<String?>(null) }

    // 底部输入栏正在攒的内容
    var draft by remember { mutableStateOf("") }
    var draftImages by remember { mutableStateOf<List<String>>(emptyList()) }

    val scope = rememberCoroutineScope()

    fun reload() {
        scope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val ms = fetchMoments()
                    val cs = fetchComments(ms.map { it.id }).groupBy { it.momentId }
                    moments = ms
                    comments = cs
                } catch (e: Exception) {
                    // 网络失败保留上一次数据，不清空
                }
            }
            loading = false
        }
    }

    LaunchedEffect(Unit) { reload() }

    // 装修用的选图器
    var pickTarget by remember { mutableStateOf("") } // bg / av_sean / av_yuri
    val decorPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val target = pickTarget
        if (uri == null || target.isBlank()) return@rememberLauncherForActivityResult
        scope.launch {
            busy = true
            val value = withContext(Dispatchers.IO) {
                val remote = uploadImage(context, uri)
                if (remote.isNotBlank()) remote else saveLocal(context, uri, "decor")
            }
            if (value.isNotBlank()) {
                when (target) {
                    "bg" -> { decor.put(K_BG, value); background = value }
                    "av_sean" -> { decor.put(K_AV_SEAN, value); avatarSean = value }
                    "av_yuri" -> { decor.put(K_AV_YURI, value); avatarYuri = value }
                }
            }
            busy = false
        }
    }

    // 发动态的选图器（可多选）
    val draftPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNullOrEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            busy = true
            val urls = withContext(Dispatchers.IO) {
                uris.take(9).map { u ->
                    val remote = uploadImage(context, u)
                    if (remote.isNotBlank()) remote else saveLocal(context, u, "draft")
                }.filter { it.isNotBlank() }
            }
            draftImages = (draftImages + urls).take(9)
            busy = false
        }
    }

    Scaffold(containerColor = PageBg) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            Column(Modifier.fillMaxSize()) {
                // ── 顶栏 ──
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(PageBg)
                        .padding(horizontal = 4.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BackButton()
                    Text(
                        "Moments",
                        modifier = Modifier.weight(1f),
                        fontFamily = Serif,
                        fontStyle = FontStyle.Italic,
                        fontSize = 20.sp,
                        color = InkMain,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                    TextButton(onClick = { showDecorDialog = true }) {
                        Text("装修", fontSize = 12.sp, color = InkMuted)
                    }
                }

                LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    // ── 横幅（可换背景图）──
                    item {
                        BannerHeader(
                            background = background,
                            title = bannerTitle,
                            subtitle = bannerSub,
                            onClick = { showDecorDialog = true },
                        )
                    }

                    when {
                        loading && moments.isEmpty() -> item {
                            Box(
                                Modifier.fillMaxWidth().padding(vertical = 60.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator(
                                    strokeWidth = 2.dp, color = InkMuted,
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                        }

                        moments.isEmpty() -> item {
                            Box(
                                Modifier.fillMaxWidth().padding(vertical = 60.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("还没有动态，在下面写第一条", fontSize = 13.sp, color = InkMuted)
                            }
                        }

                        else -> items(moments, key = { it.id }) { moment ->
                            MomentBlock(
                                moment = moment,
                                comments = comments[moment.id].orEmpty(),
                                avatarSean = avatarSean,
                                avatarYuri = avatarYuri,
                                onLike = {
                                    moments = moments.map {
                                        if (it.id == moment.id) it.copy(yuriLiked = !it.yuriLiked) else it
                                    }
                                    scope.launch {
                                        withContext(Dispatchers.IO) {
                                            runCatching { toggleYuriLike(moment.id, moment.yuriLiked) }
                                        }
                                    }
                                },
                                onComment = { commentingOn = moment },
                                onDelete = { confirmDelete = moment },
                                onImageClick = { viewingImage = it },
                            )
                        }
                    }
                    item { Spacer(Modifier.height(12.dp)) }
                }

                // ── 底部输入栏 ──
                ComposerBar(
                    text = draft,
                    images = draftImages,
                    busy = busy,
                    onTextChange = { draft = it },
                    onPickImage = { draftPicker.launch("image/*") },
                    onRemoveImage = { url -> draftImages = draftImages.filter { it != url } },
                    onSend = {
                        val body = draft.trim()
                        val imgs = draftImages
                        if (body.isBlank() && imgs.isEmpty()) return@ComposerBar
                        draft = ""
                        draftImages = emptyList()
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                runCatching { postMoment(body, imgs) }
                            }
                            reload()
                        }
                    },
                )
            }

            if (busy) {
                Box(
                    Modifier.fillMaxWidth().padding(top = 60.dp),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    Surface(shape = RoundedCornerShape(12.dp), color = CardBg) {
                        Text(
                            "正在上传图片…",
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            fontSize = 12.sp, color = InkSub,
                        )
                    }
                }
            }
        }
    }

    // ── 弹窗 ──

    if (showDecorDialog) {
        DecorDialog(
            onDismiss = { showDecorDialog = false },
            onPickBackground = { pickTarget = "bg"; decorPicker.launch("image/*"); showDecorDialog = false },
            onPickSeanAvatar = { pickTarget = "av_sean"; decorPicker.launch("image/*"); showDecorDialog = false },
            onPickYuriAvatar = { pickTarget = "av_yuri"; decorPicker.launch("image/*"); showDecorDialog = false },
            onEditText = { showDecorDialog = false; editBannerText = true },
            onReset = {
                decor.put(K_BG, ""); background = ""
                decor.put(K_AV_SEAN, ""); avatarSean = ""
                decor.put(K_AV_YURI, ""); avatarYuri = ""
                showDecorDialog = false
            },
        )
    }

    if (editBannerText) {
        BannerTextDialog(
            title = bannerTitle,
            subtitle = bannerSub,
            onDismiss = { editBannerText = false },
            onConfirm = { t, s ->
                decor.put(K_TITLE, t); bannerTitle = t
                decor.put(K_SUB, s); bannerSub = s
                editBannerText = false
            },
        )
    }

    commentingOn?.let { moment ->
        CommentDialog(
            onDismiss = { commentingOn = null },
            onConfirm = { text, imgs ->
                commentingOn = null
                scope.launch {
                    withContext(Dispatchers.IO) {
                        runCatching { postComment(moment.id, text, imgs) }
                    }
                    reload()
                }
            },
        )
    }

    confirmDelete?.let { moment ->
        ConfirmDialog(
            message = "删掉这条动态？下面的评论也会一起删掉，删了就找不回来了。",
            onDismiss = { confirmDelete = null },
            onConfirm = {
                confirmDelete = null
                scope.launch {
                    withContext(Dispatchers.IO) { runCatching { deleteMoment(moment.id) } }
                    reload()
                }
            },
        )
    }

    viewingImage?.let { url ->
        Dialog(onDismissRequest = { viewingImage = null }) {
            Surface(color = Color.Black.copy(alpha = 0.92f), shape = RoundedCornerShape(12.dp)) {
                Box(
                    Modifier.fillMaxWidth().heightIn(max = 620.dp)
                        .clickable { viewingImage = null },
                    contentAlignment = Alignment.Center,
                ) {
                    AsyncImage(
                        model = imageModel(url),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

/** 远程 URL 直接给字符串，本地路径要包成 File，Coil 才认。 */
private fun imageModel(path: String): Any =
    if (path.startsWith("http")) path else File(path)

@Composable
private fun BannerHeader(
    background: String,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.9f)
            .background(BannerFallback)
            .clickable(onClick = onClick),
    ) {
        if (background.isNotBlank()) {
            AsyncImage(
                model = imageModel(background),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        // 底部压一层柔和渐变，保证文字在任何图上都读得清
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0.45f to Color.Transparent,
                    1f to Color(0x66FFFFFF),
                )
            )
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 18.dp, bottom = 14.dp, end = 18.dp)
        ) {
            if (title.isNotBlank()) {
                Text(
                    title,
                    fontFamily = Serif,
                    fontStyle = FontStyle.Italic,
                    fontSize = 20.sp,
                    color = InkMain,
                )
            }
            if (subtitle.isNotBlank()) {
                Spacer(Modifier.height(3.dp))
                Text(
                    subtitle.uppercase(Locale.US),
                    fontFamily = Serif,
                    fontSize = 10.sp,
                    letterSpacing = 2.sp,
                    color = InkSub,
                )
            }
        }
        if (background.isBlank()) {
            Text(
                "点这里换背景",
                modifier = Modifier.align(Alignment.Center),
                fontSize = 12.sp,
                color = InkMuted,
            )
        }
    }
}

@Composable
private fun MomentBlock(
    moment: Moment,
    comments: List<MomentComment>,
    avatarSean: String,
    avatarYuri: String,
    onLike: () -> Unit,
    onComment: () -> Unit,
    onDelete: () -> Unit,
    onImageClick: (String) -> Unit,
) {
    val mine = isSean(moment.author)
    val name = if (mine) "沈聿淮" else "小鑫"
    val avatar = if (mine) avatarSean else avatarYuri
    val timeStr = remember(moment.createdAt) { formatMomentTime(moment.createdAt) }

    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp)) {
        // 头部：头像 + 名字 + 时间 + 删除
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(avatar, mine, 30.dp)
            Spacer(Modifier.width(9.dp))
            Text(
                name.uppercase(Locale.US),
                fontFamily = Serif,
                fontSize = 11.sp,
                letterSpacing = 1.5.sp,
                color = InkSub,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                timeStr,
                fontFamily = Serif,
                fontStyle = FontStyle.Italic,
                fontSize = 12.sp,
                color = InkMuted,
                modifier = Modifier.weight(1f),
            )
            Text(
                "delete",
                fontSize = 11.sp,
                color = InkFaint,
                modifier = Modifier.clickable(onClick = onDelete),
            )
        }

        Spacer(Modifier.height(10.dp))
        if (moment.content.isNotBlank()) {
            Text(moment.content, fontSize = 15.sp, color = InkMain, lineHeight = 26.sp)
        }
        if (moment.images.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            ImageStrip(moment.images, onImageClick)
        }

        // 点赞行
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (moment.yuriLiked) "♥" else "♡",
                fontSize = 15.sp,
                color = if (moment.yuriLiked) AccentRed else InkFaint,
                modifier = Modifier.clickable(onClick = onLike),
            )
            Spacer(Modifier.width(8.dp))
            val likers = buildList {
                if (moment.liked) add("沈聿淮")
                if (moment.yuriLiked) add("小鑫")
            }
            if (likers.isNotEmpty()) {
                Text(
                    "liked by " + likers.joinToString(" & "),
                    fontFamily = Serif,
                    fontSize = 12.sp,
                    color = AccentRed.copy(alpha = 0.85f),
                    modifier = Modifier.weight(1f),
                )
            } else {
                Spacer(Modifier.weight(1f))
            }
            Text(
                if (comments.isEmpty()) "评论" else "评论 ${comments.size}",
                fontSize = 11.sp,
                color = InkMuted,
                modifier = Modifier.clickable(onClick = onComment),
            )
        }

        // reply_content 是服务器惰性回复写回来的"作者本人补充"，当成一条评论显示
        val hasThread = !moment.replyContent.isNullOrBlank() || comments.isNotEmpty() ||
            moment.replyStatus == "pending"
        if (hasThread) {
            Spacer(Modifier.height(8.dp))
            Column(Modifier.fillMaxWidth()) {
                if (!moment.replyContent.isNullOrBlank()) {
                    CommentRow("沈聿淮", moment.replyContent, emptyList(), "", true, 0.dp, onImageClick)
                }
                comments.forEachIndexed { index, c ->
                    val fromSean = isSean(c.author)
                    CommentRow(
                        name = if (fromSean) "沈聿淮" else "小鑫",
                        content = c.content,
                        images = c.images,
                        time = formatClock(c.createdAt),
                        isSean = fromSean,
                        indent = if (index == 0) 0.dp else 14.dp,
                        onImageClick = onImageClick,
                    )
                }
                if (moment.replyStatus == "pending") {
                    Row(
                        modifier = Modifier.padding(top = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(4.dp).background(InkFaint, CircleShape))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "沈聿淮 待会儿会回你",
                            fontFamily = Serif,
                            fontSize = 11.sp,
                            color = InkFaint,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(LineColor))
    }
}

@Composable
private fun CommentRow(
    name: String,
    content: String,
    images: List<String>,
    time: String,
    isSean: Boolean,
    indent: androidx.compose.ui.unit.Dp,
    onImageClick: (String) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(start = indent, top = 8.dp)) {
        Text(
            name,
            fontFamily = Serif,
            fontStyle = FontStyle.Italic,
            fontSize = 13.sp,
            color = if (isSean) SeanName else YuriName,
        )
        Spacer(Modifier.height(3.dp))
        if (content.isNotBlank()) {
            Text(content, fontSize = 14.sp, color = InkSub, lineHeight = 23.sp)
        }
        if (images.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            ImageStrip(images, onImageClick, small = true)
        }
        if (time.isNotBlank()) {
            Spacer(Modifier.height(3.dp))
            Text(time, fontSize = 10.sp, color = InkFaint)
        }
    }
}

@Composable
private fun ImageStrip(
    images: List<String>,
    onClick: (String) -> Unit,
    small: Boolean = false,
) {
    val side = if (small) 84.dp else 132.dp
    if (images.size == 1) {
        AsyncImage(
            model = imageModel(images[0]),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth(if (small) 0.55f else 0.82f)
                .aspectRatio(1.25f)
                .clip(RoundedCornerShape(10.dp))
                .background(LineColor)
                .clickable { onClick(images[0]) },
        )
    } else {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(images) { url ->
                AsyncImage(
                    model = imageModel(url),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(side)
                        .clip(RoundedCornerShape(10.dp))
                        .background(LineColor)
                        .clickable { onClick(url) },
                )
            }
        }
    }
}

@Composable
private fun ComposerBar(
    text: String,
    images: List<String>,
    busy: Boolean,
    onTextChange: (String) -> Unit,
    onPickImage: () -> Unit,
    onRemoveImage: (String) -> Unit,
    onSend: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(PageBg)
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        if (images.isNotEmpty()) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(bottom = 8.dp),
            ) {
                items(images) { url ->
                    Box {
                        AsyncImage(
                            model = imageModel(url),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(LineColor),
                        )
                        Text(
                            "×",
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                                .clickable { onRemoveImage(url) }
                                .padding(horizontal = 5.dp),
                            color = Color.White,
                            fontSize = 12.sp,
                        )
                    }
                }
            }
        }
        Surface(shape = RoundedCornerShape(24.dp), color = CardBg) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "◎",
                    fontSize = 18.sp,
                    color = InkMuted,
                    modifier = Modifier.clickable(onClick = onPickImage),
                )
                Spacer(Modifier.width(12.dp))
                Box(Modifier.weight(1f)) {
                    if (text.isEmpty()) {
                        Text(
                            "share a little thing...",
                            fontFamily = Serif,
                            fontStyle = FontStyle.Italic,
                            fontSize = 14.sp,
                            color = InkFaint,
                        )
                    }
                    BasicTextField(
                        value = text,
                        onValueChange = onTextChange,
                        textStyle = androidx.compose.ui.text.TextStyle(
                            fontSize = 14.sp, color = InkMain,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.width(10.dp))
                val canSend = !busy && (text.isNotBlank() || images.isNotEmpty())
                Text(
                    "发出",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (canSend) AccentRed else InkFaint,
                    modifier = Modifier.clickable(enabled = canSend, onClick = onSend),
                )
            }
        }
    }
}

@Composable
private fun Avatar(path: String, mine: Boolean, size: androidx.compose.ui.unit.Dp) {
    val colors = if (mine) listOf(SeanA, SeanB) else listOf(YuriA, YuriB)
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(Brush.linearGradient(colors)),
        contentAlignment = Alignment.Center,
    ) {
        if (path.isNotBlank()) {
            AsyncImage(
                model = imageModel(path),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                if (mine) "沈" else "鑫",
                fontSize = (size.value * 0.38f).sp,
                color = Color.White,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

// ── 各种弹窗（都自绘：Material3 AlertDialog 的默认容器色取自 surfaceContainerHigh，
//    会被全局界面透明度设置牵连，之前 Theme.kt 那次踩过这个坑）──

@Composable
private fun BaseDialog(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(16.dp), color = CardBg, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(18.dp)) { content() }
        }
    }
}

@Composable
private fun DecorDialog(
    onDismiss: () -> Unit,
    onPickBackground: () -> Unit,
    onPickSeanAvatar: () -> Unit,
    onPickYuriAvatar: () -> Unit,
    onEditText: () -> Unit,
    onReset: () -> Unit,
) {
    BaseDialog(onDismiss) {
        Text("装修朋友圈", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = InkMain)
        Spacer(Modifier.height(4.dp))
        Text("换的是这台手机上的显示，不影响云端数据", fontSize = 11.sp, color = InkMuted)
        Spacer(Modifier.height(14.dp))
        DecorRow("换顶部背景图", onPickBackground)
        DecorRow("换沈聿淮的头像", onPickSeanAvatar)
        DecorRow("换我的头像", onPickYuriAvatar)
        DecorRow("改横幅上的字", onEditText)
        DecorRow("全部恢复默认", onReset)
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onDismiss) { Text("关掉", fontSize = 13.sp, color = InkMuted) }
        }
    }
}

@Composable
private fun DecorRow(label: String, onClick: () -> Unit) {
    Text(
        label,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        fontSize = 14.sp,
        color = InkSub,
    )
}

@Composable
private fun BannerTextDialog(
    title: String,
    subtitle: String,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit,
) {
    var t by remember { mutableStateOf(title) }
    var s by remember { mutableStateOf(subtitle) }
    BaseDialog(onDismiss) {
        Text("横幅上的字", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = InkMain)
        Spacer(Modifier.height(12.dp))
        PlainField(t, "大字（比如 ours, quietly.）") { t = it }
        Spacer(Modifier.height(10.dp))
        PlainField(s, "小字（比如 LITTLE THINGS WE KEEP）") { s = it }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onDismiss) { Text("取消", fontSize = 13.sp, color = InkMuted) }
            TextButton(onClick = { onConfirm(t.trim(), s.trim()) }) {
                Text("保存", fontSize = 13.sp, color = AccentRed)
            }
        }
    }
}

@Composable
private fun CommentDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, List<String>) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    var images by remember { mutableStateOf<List<String>>(emptyList()) }
    var uploading by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNullOrEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            uploading = true
            val urls = withContext(Dispatchers.IO) {
                uris.take(4).map { u ->
                    val remote = uploadImage(context, u)
                    if (remote.isNotBlank()) remote else saveLocal(context, u, "comment")
                }.filter { it.isNotBlank() }
            }
            images = (images + urls).take(4)
            uploading = false
        }
    }

    BaseDialog(onDismiss) {
        Text("写评论", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = InkMain)
        Spacer(Modifier.height(12.dp))
        PlainField(text, "说点什么...", minHeight = 76.dp) { text = it }
        if (images.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(images) { url ->
                    Box {
                        AsyncImage(
                            model = imageModel(url),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp))
                                .background(LineColor),
                        )
                        Text(
                            "×",
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                                .clickable { images = images.filter { it != url } }
                                .padding(horizontal = 5.dp),
                            color = Color.White, fontSize = 12.sp,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (uploading) "上传中…" else "＋ 图片",
                fontSize = 13.sp,
                color = if (uploading) InkFaint else InkSub,
                modifier = Modifier.clickable(enabled = !uploading) { picker.launch("image/*") },
            )
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onDismiss) { Text("取消", fontSize = 13.sp, color = InkMuted) }
            val canSend = !uploading && (text.isNotBlank() || images.isNotEmpty())
            TextButton(
                onClick = { onConfirm(text.trim(), images) },
                enabled = canSend,
            ) {
                Text("发送", fontSize = 13.sp, color = if (canSend) AccentRed else InkFaint)
            }
        }
    }
}

@Composable
private fun ConfirmDialog(message: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    BaseDialog(onDismiss) {
        Text(message, fontSize = 14.sp, color = InkSub, lineHeight = 22.sp)
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onDismiss) { Text("算了", fontSize = 13.sp, color = InkMuted) }
            TextButton(onClick = onConfirm) { Text("删掉", fontSize = 13.sp, color = AccentRed) }
        }
    }
}

@Composable
private fun PlainField(
    value: String,
    placeholder: String,
    minHeight: androidx.compose.ui.unit.Dp = 44.dp,
    onValueChange: (String) -> Unit,
) {
    Surface(shape = RoundedCornerShape(10.dp), color = PageBg, modifier = Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().heightIn(min = minHeight).padding(12.dp)) {
            if (value.isEmpty()) {
                Text(placeholder, fontSize = 13.sp, color = InkFaint)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, color = InkMain),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

// ── 时间 ────────────────────────────────────────────────────

/**
 * Supabase 返回 UTC 时间戳（形如 2026-08-06T16:37:06.52343+00:00）。
 * 早先用 "yyyy-MM-dd'T'HH:mm:ssXXX" 解析带小数秒的串会抛异常，catch 后退化成裸截字符串，
 * 显示的还是 UTC 时间（比北京时间慢 8 小时）。这里先剥掉小数秒再解析。
 */
private fun parseTs(raw: String): Date? = runCatching {
    val cleaned = raw.replace(Regex("""\.\d+"""), "")
    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).parse(cleaned)
}.getOrNull()

private fun formatMomentTime(raw: String): String {
    val d = parseTs(raw) ?: return raw.take(16)
    val today = local("yyyy-MM-dd").format(Date())
    val that = local("yyyy-MM-dd").format(d)
    val clock = local("HH:mm").format(d)
    return when {
        that == today -> "Today · $clock"
        else -> local("M月d日").format(d) + " · " + clock
    }
}

private fun formatClock(raw: String): String {
    val d = parseTs(raw) ?: return ""
    return local("HH:mm").format(d)
}

private fun local(pattern: String) = SimpleDateFormat(pattern, Locale.CHINA).apply {
    timeZone = TimeZone.getDefault()
}
