/*
 * 橘瓣 OrangeChat - 朋友圈
 */
package me.rerere.rikkahub.ui.pages.moments

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Favourite
import me.rerere.hugeicons.stroke.FavouriteCircle
import me.rerere.hugeicons.stroke.PlusSign
import me.rerere.rikkahub.ui.components.nav.BackButton
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.io.OutputStreamWriter
import java.net.HttpURLConnection

private const val SUPA_URL = "https://byqqwypdfiwvalozihgs.supabase.co"
private const val SUPA_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImJ5cXF3eXBkZml3dmFsb3ppaGdzIiwicm9sZSI6InNlcnZpY2Vfcm9sZSIsImlhdCI6MTc4MzY1NDA4MCwiZXhwIjoyMDk5MjMwMDgwfQ.LIbE9DFsLSRhOig5bUUfUP4r7t1ykdNy8L0gZM_xtGw"

data class Moment(
    val id: String,
    val author: String,
    val content: String,
    val yuriLiked: Boolean,
    val liked: Boolean,
    val replyContent: String?,
    val createdAt: String,
)

fun fetchMoments(): List<Moment> {
    val url = URL("$SUPA_URL/rest/v1/moments?order=created_at.desc&limit=30")
    val conn = url.openConnection() as HttpURLConnection
    conn.setRequestProperty("apikey", SUPA_KEY)
    conn.setRequestProperty("Authorization", "Bearer $SUPA_KEY")
    conn.setRequestProperty("Accept", "application/json")
    val body = conn.inputStream.bufferedReader().readText()
    val arr = JSONArray(body)
    val list = mutableListOf<Moment>()
    for (i in 0 until arr.length()) {
        val o = arr.getJSONObject(i)
        list.add(Moment(
            id = o.getString("id"),
            author = o.optString("author", "sean"),
            content = o.optString("content", ""),
            yuriLiked = o.optBoolean("yuri_liked", false),
            liked = o.optBoolean("liked", false),
            replyContent = if (o.isNull("reply_content")) null else o.optString("reply_content"),
            createdAt = o.optString("created_at", ""),
        ))
    }
    return list
}

fun postMoment(content: String, author: String) {
    val url = URL("$SUPA_URL/rest/v1/moments")
    val conn = url.openConnection() as HttpURLConnection
    conn.requestMethod = "POST"
    conn.doOutput = true
    conn.setRequestProperty("apikey", SUPA_KEY)
    conn.setRequestProperty("Authorization", "Bearer $SUPA_KEY")
    conn.setRequestProperty("Content-Type", "application/json")
    conn.setRequestProperty("Prefer", "return=minimal")
    val payload = JSONObject().apply {
        put("author", author)
        put("content", content)
        put("reply_status", "done")
    }.toString()
    OutputStreamWriter(conn.outputStream).use { it.write(payload) }
    conn.responseCode // trigger
}

fun toggleYuriLike(momentId: String, current: Boolean) {
    val url = URL("$SUPA_URL/rest/v1/moments?id=eq.$momentId")
    val conn = url.openConnection() as HttpURLConnection
    conn.requestMethod = "PATCH"
    conn.doOutput = true
    conn.setRequestProperty("apikey", SUPA_KEY)
    conn.setRequestProperty("Authorization", "Bearer $SUPA_KEY")
    conn.setRequestProperty("Content-Type", "application/json")
    conn.setRequestProperty("Prefer", "return=minimal")
    val payload = JSONObject().apply { put("yuri_liked", !current) }.toString()
    OutputStreamWriter(conn.outputStream).use { it.write(payload) }
    conn.responseCode
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MomentsPage() {
    var moments by remember { mutableStateOf<List<Moment>>(emptyList()) }
    var showPostDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun reload() {
        scope.launch {
            withContext(Dispatchers.IO) {
                try { moments = fetchMoments() } catch (e: Exception) {}
            }
        }
    }

    LaunchedEffect(Unit) { reload() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("朋友圈") },
                navigationIcon = { BackButton() },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showPostDialog = true }) {
                Icon(HugeIcons.PlusSign, contentDescription = "发动态")
            }
        }
    ) { padding ->
        if (moments.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("还没有动态，点 + 发一条", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                item { Spacer(Modifier.height(8.dp)) }
                items(moments, key = { it.id }) { moment ->
                    MomentCard(
                        moment = moment,
                        onLike = {
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    try { toggleYuriLike(moment.id, moment.yuriLiked) } catch (e: Exception) {}
                                }
                                reload()
                            }
                        }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }

    if (showPostDialog) {
        PostMomentDialog(
            onDismiss = { showPostDialog = false },
            onConfirm = { content, author ->
                scope.launch {
                    withContext(Dispatchers.IO) {
                        try { postMoment(content, author) } catch (e: Exception) {}
                    }
                    reload()
                    showPostDialog = false
                }
            }
        )
    }
}

@Composable
private fun MomentCard(moment: Moment, onLike: () -> Unit) {
    val timeStr = remember(moment.createdAt) {
        try {
            val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.CHINA)
            val d = fmt.parse(moment.createdAt) ?: Date()
            SimpleDateFormat("MM/dd HH:mm", Locale.CHINA).format(d)
        } catch (e: Exception) { moment.createdAt.take(16) }
    }
    val authorLabel = if (moment.author == "sean") "Sean" else "Yuri"

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                color = if (moment.author == "sean") MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        authorLabel.first().toString(),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (moment.author == "sean") MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(authorLabel, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
                Text(timeStr, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onLike, modifier = Modifier.size(36.dp)) {
                Icon(
                    if (moment.yuriLiked) HugeIcons.FavouriteCircle else HugeIcons.Favourite,
                    contentDescription = "点赞",
                    tint = if (moment.yuriLiked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(moment.content, style = MaterialTheme.typography.bodyMedium)
        if (!moment.replyContent.isNullOrBlank()) {
            Spacer(Modifier.height(8.dp))
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "Sean：${moment.replyContent}",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (moment.liked) {
            Spacer(Modifier.height(4.dp))
            Text("♥ Sean 觉得很好", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun PostMomentDialog(
    onDismiss: () -> Unit,
    onConfirm: (content: String, author: String) -> Unit,
) {
    var content by remember { mutableStateOf("") }
    var author by remember { mutableStateOf("yuri") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("发动态") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("说点什么...") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("发布身份：", style = MaterialTheme.typography.bodySmall)
                    Surface(
                        onClick = { author = "yuri" },
                        shape = RoundedCornerShape(20.dp),
                        color = if (author == "yuri") MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                    ) { Text("Yuri", modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium) }
                    Surface(
                        onClick = { author = "sean" },
                        shape = RoundedCornerShape(20.dp),
                        color = if (author == "sean") MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                    ) { Text("Sean", modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium) }
                }
            }
        },
        confirmButton = {
            FilledTonalButton(onClick = { onConfirm(content, author) }, enabled = content.isNotBlank()) {
                Text("发出去")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
