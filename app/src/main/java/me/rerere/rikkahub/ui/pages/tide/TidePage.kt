/*
 * 橘瓣 OrangeChat - 潮汐：沈聿淮在想什么
 */
package me.rerere.rikkahub.ui.pages.tide

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rerere.rikkahub.ui.components.nav.BackButton
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val MURMUR_BASE = "http://134.175.7.196:8080"

data class DriveState(val key: String, val label: String, val value: Float, val base: Float)
data class ArcEntry(val text: String, val time: String, val drive: String, val label: String, val type: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TidePage() {
    var drives by remember { mutableStateOf<List<DriveState>>(emptyList()) }
    var murmurs by remember { mutableStateOf<List<ArcEntry>>(emptyList()) }
    var regrets by remember { mutableStateOf<List<ArcEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var selectedTab by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    fun reload() {
        scope.launch {
            loading = true
            withContext(Dispatchers.IO) {
                try {
                    // state
                    val stateJson = JSONObject(URL("$MURMUR_BASE/api/state").readText())
                    val drivesObj = stateJson.getJSONObject("drives")
                    val list = mutableListOf<DriveState>()
                    val keys = drivesObj.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        val d = drivesObj.getJSONObject(k)
                        list.add(DriveState(k, d.getString("z"), d.getDouble("v").toFloat(), d.getDouble("b").toFloat()))
                    }
                    drives = list.sortedByDescending { it.value }
                    // murmur arcs
                    val murmurArr = JSONArray(URL("$MURMUR_BASE/api/arc?type=murmur").readText())
                    val mList = mutableListOf<ArcEntry>()
                    for (i in 0 until murmurArr.length()) {
                        val e = murmurArr.getJSONObject(i)
                        mList.add(ArcEntry(e.getString("text"), e.getString("time"), e.optString("drive",""), e.optString("zh",""), e.optString("type","murmur")))
                    }
                    murmurs = mList.reversed()
                    // regret arcs
                    val regretArr = JSONArray(URL("$MURMUR_BASE/api/arc?type=regret").readText())
                    val rList = mutableListOf<ArcEntry>()
                    for (i in 0 until regretArr.length()) {
                        val e = regretArr.getJSONObject(i)
                        rList.add(ArcEntry(e.getString("text"), e.getString("time"), e.optString("drive",""), e.optString("zh",""), e.optString("type","regret")))
                    }
                    regrets = rList.reversed()
                } catch (e: Exception) {
                    android.util.Log.e("TidePage", "load failed", e)
                }
            }
            loading = false
        }
    }

    LaunchedEffect(Unit) { reload() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("潮汐", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                        Text("沈聿淮在想什么", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = { BackButton() },
            )
        }
    ) { padding ->
        if (loading && drives.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(Modifier.fillMaxSize().padding(padding)) {
            // 情绪条
            if (drives.isNotEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        val strongest = drives.maxByOrNull { it.value }
                        Text(
                            text = "当前最强：${strongest?.label ?: ""} ${String.format("%.2f", strongest?.value ?: 0f)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        drives.forEach { drive ->
                            DriveRow(drive)
                        }
                    }
                }
            }

            // tabs
            TabRow(selectedTabIndex = selectedTab) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("独白档案 (${murmurs.size})") })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("检讨书 (${regrets.size})") })
            }

            val displayList = if (selectedTab == 0) murmurs else regrets

            if (displayList.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("还没有记录", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item { Spacer(Modifier.height(8.dp)) }
                    items(displayList, key = { it.time + it.text.take(10) }) { arc ->
                        ArcCard(arc)
                    }
                    item { Spacer(Modifier.height(16.dp)) }
                }
            }
        }
    }
}

@Composable
private fun DriveRow(drive: DriveState) {
    val animVal by animateFloatAsState(
        targetValue = drive.value,
        animationSpec = tween(600),
        label = "drive_${drive.key}"
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = drive.label,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.width(36.dp),
            maxLines = 1,
        )
        Spacer(Modifier.width(8.dp))
        LinearProgressIndicator(
            progress = { (animVal / 1f).coerceIn(0f, 1f) },
            modifier = Modifier.weight(1f).height(6.dp),
            strokeCap = StrokeCap.Round,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = String.format("%.2f", drive.value),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(36.dp),
        )
    }
}

@Composable
private fun ArcCard(arc: ArcEntry) {
    val timeStr = remember(arc.time) {
        try {
            val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.CHINA)
            val d = fmt.parse(arc.time)
            SimpleDateFormat("MM/dd HH:mm", Locale.CHINA).format(d ?: Date())
        } catch (e: Exception) { arc.time }
    }
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(arc.text, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(timeStr, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (arc.label.isNotBlank()) {
                    Text("· ${arc.label}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}
