package dev.adamsjack.beanshelf.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.adamsjack.beanshelf.data.SocialClient
import dev.adamsjack.beanshelf.data.SocialClient.Account
import dev.adamsjack.beanshelf.data.SocialClient.FeedPost
import dev.adamsjack.beanshelf.data.SocialClient.UserHit
import dev.adamsjack.beanshelf.model.formatRating
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Permanent public HTTPS: Cloudflare named tunnel on Jack's own domain (stable,
// reboot-proof via the beanshelf-tunnel LaunchAgent). Tailnet fallback if ever
// needed: http://100.75.23.96:8787
private const val DEFAULT_SERVER = "https://beans.beanshelf.ca"

/**
 * The social side: account section (register/sign in), find & follow people,
 * a feed of the beans they're having, and each person's leaderboard.
 */
@Composable
fun SocialScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var account by remember { mutableStateOf(SocialClient.account(context)) }

    val acct = account
    if (acct == null) {
        AccountForm(onBack = onBack, onSignedIn = { account = it })
    } else {
        SocialHome(
            account = acct,
            onBack = onBack,
            onSignOut = {
                SocialClient.signOut(context)
                account = null
            },
        )
    }
}

// ── Account section ─────────────────────────────────────────────────────────

@Composable
private fun AccountForm(onBack: () -> Unit, onSignedIn: (Account) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var username by rememberSaveable { mutableStateOf("") }
    var display by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun submit(register: Boolean) {
        if (busy) return
        busy = true; error = null
        scope.launch {
            runCatching {
                if (register) SocialClient.register(context, DEFAULT_SERVER, username.trim(), display, password)
                else SocialClient.login(context, DEFAULT_SERVER, username.trim(), password)
            }.onSuccess { onSignedIn(it) }
                .onFailure { error = it.message ?: "Couldn't reach the server" }
            busy = false
        }
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .padding(horizontal = 24.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 12.dp)) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Parchment)
                }
                Text("Account", style = MaterialTheme.typography.headlineMedium, color = Parchment)
            }
            Text(
                "Follow friends and see the beans they're having. Your shelf stays on your phone — only what you post is shared.",
                style = MaterialTheme.typography.bodyMedium,
                color = Dim,
                modifier = Modifier.padding(top = 8.dp, bottom = 20.dp),
            )

            Eyebrow("You", Modifier.padding(bottom = 8.dp))
            SocialField(username, { username = it.lowercase() }, "Username — letters, numbers, _")
            SocialField(display, { display = it }, "Display name (optional)")
            SocialField(password, { password = it }, "Password", password = true)

            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 10.dp))
            }

            Row(Modifier.padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = { submit(register = true) },
                    enabled = !busy && username.isNotBlank() && password.length >= 4,
                    colors = ButtonDefaults.buttonColors(containerColor = Crema, contentColor = Roast),
                    modifier = Modifier.weight(1f).height(48.dp),
                ) { Text("Create account") }
                OutlinedButton(
                    onClick = { submit(register = false) },
                    enabled = !busy && username.isNotBlank() && password.isNotBlank(),
                    modifier = Modifier.weight(1f).height(48.dp),
                ) { Text("Sign in", color = Crema) }
            }
            if (busy) {
                CircularProgressIndicator(color = Crema, strokeWidth = 2.dp,
                    modifier = Modifier.padding(top = 16.dp).size(20.dp))
            }
        }
    }
}

// ── Signed-in home: search, feed, friend leaderboards ───────────────────────

@Composable
private fun SocialHome(account: Account, onBack: () -> Unit, onSignOut: () -> Unit) {
    val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf<List<UserHit>>(emptyList()) }
    var feed by remember { mutableStateOf<List<FeedPost>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var viewingUser by remember { mutableStateOf<String?>(null) }

    fun refresh() {
        loading = true; error = null
        scope.launch {
            runCatching { SocialClient.feed(account) }
                .onSuccess { feed = it }
                .onFailure { error = it.message ?: "Couldn't reach the server" }
            loading = false
        }
    }
    LaunchedEffect(Unit) { refresh() }
    LaunchedEffect(query) {
        if (query.length < 2) { results = emptyList(); return@LaunchedEffect }
        kotlinx.coroutines.delay(350) // debounce
        runCatching { SocialClient.search(account, query) }.onSuccess { results = it }
    }

    viewingUser?.let { username ->
        UserLeaderboard(account, username, onBack = { viewingUser = null })
        return
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
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 8.dp, end = 16.dp, top = 12.dp)) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Parchment)
                    }
                    Box(
                        Modifier.size(36.dp).clip(CircleShape).background(Crema),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            account.username.take(1).uppercase(),
                            color = Roast, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold,
                        )
                    }
                    Column(Modifier.weight(1f).padding(start = 10.dp)) {
                        Text(account.display, style = MaterialTheme.typography.titleMedium, color = Parchment)
                        Text("@${account.username}", style = MaterialTheme.typography.bodySmall, color = Dim)
                    }
                    TextButton(onClick = onSignOut) { Text("Sign out", color = Dim) }
                }
            }

            item {
                SocialField(
                    query, { query = it }, "Find people…",
                    modifier = Modifier.padding(horizontal = 24.dp).padding(top = 12.dp),
                )
            }
            items(results.size) { i ->
                val hit = results[i]
                var following by remember(hit.username) { mutableStateOf(hit.following) }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewingUser = hit.username }
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(hit.display, style = MaterialTheme.typography.titleMedium, color = Parchment)
                        Text("@${hit.username}", style = MaterialTheme.typography.bodySmall, color = Dim)
                    }
                    OutlinedButton(onClick = {
                        following = !following
                        scope.launch {
                            runCatching { SocialClient.setFollowing(account, hit.username, following) }
                                .onFailure { following = !following }
                        }
                    }) {
                        Text(if (following) "Following" else "Follow", color = if (following) Dim else Crema)
                    }
                }
            }

            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 24.dp, end = 12.dp, top = 20.dp),
                ) {
                    Eyebrow("The beans they're having", Modifier.weight(1f))
                    IconButton(onClick = { refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh feed", tint = Crema)
                    }
                }
            }
            when {
                loading -> item {
                    CircularProgressIndicator(color = Crema, strokeWidth = 2.dp,
                        modifier = Modifier.padding(start = 24.dp, top = 12.dp).size(20.dp))
                }
                error != null -> item {
                    Text(error ?: "", color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
                }
                feed.isEmpty() -> item {
                    Text(
                        "Nothing here yet. Follow someone, or post a bean from its page (share → Post to feed).",
                        style = MaterialTheme.typography.bodyMedium, color = Dim,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    )
                }
                else -> items(feed.size) { i ->
                    FeedRow(account, feed[i]) { viewingUser = feed[i].username }
                }
            }
        }
    }
}

@Composable
private fun UserLeaderboard(account: Account, username: String, onBack: () -> Unit) {
    var posts by remember { mutableStateOf<List<FeedPost>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    LaunchedEffect(username) {
        runCatching { SocialClient.userLeaderboard(account, username) }.onSuccess { posts = it }
        loading = false
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
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 8.dp, top = 12.dp)) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Parchment)
                    }
                    Column {
                        Text("@$username", style = MaterialTheme.typography.headlineMedium, color = Parchment)
                        Eyebrow("Their leaderboard")
                    }
                }
            }
            if (loading) {
                item {
                    CircularProgressIndicator(color = Crema, strokeWidth = 2.dp,
                        modifier = Modifier.padding(start = 24.dp, top = 16.dp).size(20.dp))
                }
            } else if (posts.isEmpty()) {
                item {
                    Text("No rated beans posted yet.", color = Dim,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp))
                }
            } else {
                items(posts.size) { i ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
                    ) {
                        Text(
                            "${i + 1}",
                            fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold,
                            fontSize = if (i < 3) 22.sp else 18.sp,
                            color = if (i < 3) Crema else Dim,
                            modifier = Modifier.padding(end = 14.dp),
                        )
                        FeedPhoto(posts[i])
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(posts[i].name, style = MaterialTheme.typography.titleMedium, color = Parchment, maxLines = 1)
                            if (posts[i].roaster.isNotBlank()) {
                                Text(posts[i].roaster, style = MaterialTheme.typography.bodySmall, color = Dim, maxLines = 1)
                            }
                        }
                        RoastStamp(posts[i].rating, size = 44.dp)
                    }
                }
            }
        }
    }
}

@Composable
private fun FeedRow(account: Account, post: FeedPost, onUser: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 10.dp)) {
        FeedPhoto(post)
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "@${post.username}",
                    style = MaterialTheme.typography.labelLarge,
                    color = Crema,
                    modifier = Modifier.clickable(onClick = onUser),
                )
                Text(
                    "  ·  " + relativeTime(post.createdAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = Dim,
                )
            }
            Text(post.name, style = MaterialTheme.typography.titleMedium, color = Parchment, maxLines = 1)
            val sub = listOf(post.roaster, post.origin).filter { it.isNotBlank() }.joinToString("  ·  ")
            if (sub.isNotBlank()) Text(sub, style = MaterialTheme.typography.bodySmall, color = Dim, maxLines = 1)
            if (post.notes.isNotBlank()) {
                Text(
                    post.notes, style = MaterialTheme.typography.bodyMedium,
                    color = Parchment.copy(alpha = 0.8f), maxLines = 2,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        if (post.rating > 0f) RoastStamp(post.rating, size = 44.dp, modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun FeedPhoto(post: FeedPost) {
    val context = LocalContext.current
    val photo by produceState<ImageBitmap?>(initialValue = null, post.id) {
        value = SocialClient.fetchPhoto(context, post)?.let { f ->
            withContext(Dispatchers.IO) {
                android.graphics.BitmapFactory.decodeFile(f.absolutePath)?.asImageBitmap()
            }
        }
    }
    Box(
        Modifier.size(width = 56.dp, height = 70.dp).clip(RoundedCornerShape(8.dp)).background(SurfaceHigh),
        contentAlignment = Alignment.Center,
    ) {
        val p = photo
        if (p != null) {
            Image(bitmap = p, contentDescription = post.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Icon(Icons.Default.LocalCafe, contentDescription = null, tint = Dim, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun SocialField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    password: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = if (password) KeyboardOptions(keyboardType = KeyboardType.Password) else KeyboardOptions.Default,
        modifier = modifier.then(Modifier.fillMaxWidth().padding(bottom = 10.dp)),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Crema,
            unfocusedBorderColor = Dim.copy(alpha = 0.4f),
            focusedLabelColor = Crema,
            unfocusedLabelColor = Dim,
            cursorColor = Crema,
            focusedTextColor = Parchment,
            unfocusedTextColor = Parchment,
        ),
    )
}

private fun relativeTime(ts: Long): String {
    val mins = (System.currentTimeMillis() - ts) / 60000
    return when {
        mins < 1 -> "now"
        mins < 60 -> "${mins}m"
        mins < 60 * 24 -> "${mins / 60}h"
        else -> "${mins / (60 * 24)}d"
    }
}
