package dev.adamsjack.beanshelf.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.adamsjack.beanshelf.data.PhotoStore
import dev.adamsjack.beanshelf.model.Bean
import dev.adamsjack.beanshelf.model.formatRating

/**
 * The leaderboard: beans ranked by your rating, brew methods ranked by the
 * average rating of the cups you logged with them.
 */
@Composable
fun LeaderboardScreen(
    beans: List<Bean>,
    onBack: () -> Unit,
    onOpen: (Bean) -> Unit,
) {
    val rankedBeans = remember(beans) {
        beans.filter { it.rating > 0f }.sortedWith(
            compareByDescending<Bean> { it.rating }.thenByDescending { it.brews.size }
        )
    }
    val rankedMethods = remember(beans) {
        beans.flatMap { it.brews }
            .groupBy { it.method }
            .map { (method, brews) ->
                val rated = brews.filter { it.rating > 0f }
                MethodStanding(
                    method = method,
                    brewCount = brews.size,
                    avgRating = if (rated.isEmpty()) null else rated.map { it.rating }.average().toFloat(),
                )
            }
            .sortedWith(compareByDescending<MethodStanding> { it.avgRating ?: -1f }.thenByDescending { it.brewCount })
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 48.dp,
            ),
        ) {
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 8.dp, end = 24.dp, top = 12.dp),
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Parchment)
                    }
                    Text("Leaderboard", style = MaterialTheme.typography.headlineMedium, color = Parchment)
                }
            }

            item { Eyebrow("Top beans", Modifier.padding(start = 24.dp, top = 20.dp, bottom = 6.dp)) }
            if (rankedBeans.isEmpty()) {
                item {
                    Text(
                        "Rate a bag and it climbs on here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Dim,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    )
                }
            } else {
                items(rankedBeans.size) { i -> BeanStandingRow(i + 1, rankedBeans[i]) { onOpen(rankedBeans[i]) } }
            }

            item { Eyebrow("Top brew methods", Modifier.padding(start = 24.dp, top = 28.dp, bottom = 6.dp)) }
            if (rankedMethods.isEmpty()) {
                item {
                    Text(
                        "Log some brews to rank your methods.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Dim,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    )
                }
            } else {
                items(rankedMethods.size) { i -> MethodStandingRow(i + 1, rankedMethods[i]) }
            }
        }
    }
}

private data class MethodStanding(val method: String, val brewCount: Int, val avgRating: Float?)

@Composable
private fun RankNumeral(rank: Int) {
    // Podium gets crema; the field stays quiet.
    Text(
        text = rank.toString(),
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Bold,
        fontSize = if (rank <= 3) 22.sp else 18.sp,
        color = if (rank <= 3) Crema else Dim,
        modifier = Modifier.width(34.dp),
    )
}

@Composable
private fun BeanStandingRow(rank: Int, bean: Bean, onClick: () -> Unit) {
    val photo by PhotoStore.rememberPhoto(bean.photoPath, targetWidth = 200)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 8.dp),
    ) {
        RankNumeral(rank)
        Box(
            modifier = Modifier
                .size(width = 44.dp, height = 56.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(SurfaceHigh),
            contentAlignment = Alignment.Center,
        ) {
            val p = photo
            if (p != null) {
                Image(bitmap = p, contentDescription = bean.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Icon(Icons.Default.LocalCafe, contentDescription = null, tint = Dim, modifier = Modifier.size(18.dp))
            }
        }
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(bean.name, style = MaterialTheme.typography.titleMedium, color = Parchment, maxLines = 1)
            val sub = listOf(bean.roaster, "${bean.brews.size} brews".takeIf { bean.brews.isNotEmpty() } ?: "")
                .filter { it.isNotBlank() }.joinToString("  ·  ")
            if (sub.isNotBlank()) {
                Text(sub, style = MaterialTheme.typography.bodySmall, color = Dim, maxLines = 1)
            }
        }
        RoastStamp(rating = bean.rating, size = 44.dp, modifier = Modifier.padding(start = 10.dp))
    }
}

@Composable
private fun MethodStandingRow(rank: Int, standing: MethodStanding) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 10.dp),
    ) {
        RankNumeral(rank)
        Column(Modifier.weight(1f)) {
            Text(standing.method, style = MaterialTheme.typography.titleMedium, color = Parchment)
            Text(
                if (standing.brewCount == 1) "1 brew" else "${standing.brewCount} brews",
                style = MaterialTheme.typography.bodySmall,
                color = Dim,
            )
        }
        Text(
            standing.avgRating?.let { formatRating(it) } ?: "—",
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = StampInk,
        )
    }
}
