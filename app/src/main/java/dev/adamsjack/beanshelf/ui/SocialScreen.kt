package dev.adamsjack.beanshelf.ui

import android.content.Intent
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.adamsjack.beanshelf.data.Qr
import dev.adamsjack.beanshelf.data.SocialClient
import dev.adamsjack.beanshelf.data.SocialClient.Account
import dev.adamsjack.beanshelf.data.SocialClient.Comment
import dev.adamsjack.beanshelf.data.SocialClient.FeedPost
import dev.adamsjack.beanshelf.data.SocialClient.Profile
import dev.adamsjack.beanshelf.data.SocialClient.UserHit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val DEFAULT_SERVER = "https://beans.beanshelf.ca"

/** In-Social navigation stack entries. */
private sealed interface View {
    data object Home : View
    data class ProfileOf(val username: String) : View
    data class People(val username: String, val followers: Boolean) : View
}

@Composable
fun SocialScreen(onBack: () -> Unit, initialProfile: String? = null, startOnProfile: Boolean = false) {
    val context = LocalContext.current
    var account by remember { mutableStateOf(SocialClient.account(context)) }
    val acct = account
    if (acct == null) {
        AccountForm(onBack = onBack, onSignedIn = { account = it })
    } else {
        SocialRoot(
            account = acct,
            // Profile tab opens straight to your own profile.
            initialProfile = initialProfile ?: acct.username.takeIf { startOnProfile },
            onBack = onBack,
            onSignOut = { SocialClient.signOut(context); account = null },
        )
    }
}

// ── Root: tabs + in-social navigation stack ─────────────────────────────────

@Composable
private fun SocialRoot(account: Account, initialProfile: String?, onBack: () -> Unit, onSignOut: () -> Unit) {
    // A simple back-stack; the tab root is the bottom entry (no back arrow there).
    val stack = remember {
        mutableStateListOf<View>(if (initialProfile != null) View.ProfileOf(initialProfile) else View.Home)
    }
    var commentsPost by remember { mutableStateOf<FeedPost?>(null) }
    val atRoot = stack.size <= 1
    fun push(v: View) { stack.add(v) }
    fun pop() { if (stack.size > 1) stack.removeAt(stack.lastIndex) }

    // Hardware back pops the in-Social stack; at the root it falls through to the app.
    androidx.activity.compose.BackHandler(enabled = !atRoot) { pop() }

    val openProfile: (String) -> Unit = { push(View.ProfileOf(it)) }
    val openComments: (FeedPost) -> Unit = { commentsPost = it }

    when (val v = stack.last()) {
        is View.Home -> HomeTabs(
            account = account, onSignOut = onSignOut,
            onOpenProfile = openProfile, onOpenComments = openComments,
            onOpenMe = { push(View.ProfileOf(account.username)) },
        )
        is View.ProfileOf -> ProfileView(
            account = account, username = v.username,
            showBack = !atRoot,
            onBack = { pop() },
            onOpenProfile = openProfile, onOpenComments = openComments,
            onOpenPeople = { followers -> push(View.People(v.username, followers)) },
        )
        is View.People -> PeopleList(
            account = account, username = v.username, followers = v.followers,
            onBack = { pop() },
            onOpenProfile = openProfile,
        )
    }

    commentsPost?.let { CommentsSheet(account, it) { commentsPost = null } }
}

// ── Home with Feed / Discover / Find tabs ───────────────────────────────────

@Composable
private fun HomeTabs(
    account: Account, onSignOut: () -> Unit,
    onOpenProfile: (String) -> Unit, onOpenComments: (FeedPost) -> Unit, onOpenMe: () -> Unit,
) {
    var tab by rememberSaveable { mutableStateOf(0) } // 0 Feed, 1 Discover, 2 Find people
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
            // header — no back arrow; the bottom bar owns top-level navigation
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 20.dp, end = 12.dp, top = 10.dp)) {
                Avatar(account.username, size = 34.dp, onClick = onOpenMe)
                Column(Modifier.weight(1f).padding(start = 10.dp).clickable(onClick = onOpenMe)) {
                    Text(account.display, style = MaterialTheme.typography.titleMedium, color = Parchment)
                    Text("@${account.username} · your profile", style = MaterialTheme.typography.bodySmall, color = Dim)
                }
                TextButton(onClick = onSignOut) { Text("Sign out", color = Dim) }
            }
            // tab strip
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TabChip("Feed", tab == 0) { tab = 0 }
                TabChip("Discover", tab == 1) { tab = 1 }
                TabChip("Find people", tab == 2) { tab = 2 }
            }
            when (tab) {
                2 -> FindPeople(account, onOpenProfile)
                else -> PostFeed(account, discover = tab == 1, onOpenProfile = onOpenProfile, onOpenComments = onOpenComments)
            }
        }
    }
}

@Composable
private fun TabChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) Crema else Surface2)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(label, color = if (selected) Roast else Dim, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun PostFeed(account: Account, discover: Boolean, onOpenProfile: (String) -> Unit, onOpenComments: (FeedPost) -> Unit) {
    var posts by remember(discover) { mutableStateOf<List<FeedPost>>(emptyList()) }
    var loading by remember(discover) { mutableStateOf(true) }
    var error by remember(discover) { mutableStateOf<String?>(null) }
    var reloadKey by remember { mutableStateOf(0) }
    LaunchedEffect(discover, reloadKey) {
        loading = true; error = null
        runCatching { if (discover) SocialClient.discover(account) else SocialClient.feed(account) }
            .onSuccess { posts = it }.onFailure { error = it.message ?: "Couldn't reach the server" }
        loading = false
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 48.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 24.dp, end = 12.dp, top = 10.dp)) {
                Eyebrow(if (discover) "Recent beans, everyone" else "The beans they're having", Modifier.weight(1f))
                IconButton(onClick = { reloadKey++ }) { Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = Crema) }
            }
        }
        when {
            loading -> item { Loader() }
            error != null -> item { ErrorText(error!!) }
            posts.isEmpty() -> item {
                Text(
                    if (discover) "No beans posted yet. Be the first — share a bean from its page → Post to feed."
                    else "Nothing here yet. Follow people in Discover or Find people, or post a bean.",
                    style = MaterialTheme.typography.bodyMedium, color = Dim,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
            }
            else -> items(posts.size) { i ->
                PostCard(account, posts[i], onOpenProfile, onOpenComments)
            }
        }
    }
}

// ── One post card, with cheers + comments ───────────────────────────────────

@Composable
private fun PostCard(account: Account, post: FeedPost, onOpenProfile: (String) -> Unit, onOpenComments: (FeedPost) -> Unit) {
    var cheered by remember(post.id) { mutableStateOf(post.iCheered) }
    var cheerCount by remember(post.id) { mutableStateOf(post.cheers) }
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 10.dp)) {
        Row {
            FeedPhoto(post)
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("@${post.username}", style = MaterialTheme.typography.labelLarge, color = Crema,
                        modifier = Modifier.clickable { onOpenProfile(post.username) })
                    Text("  ·  " + relativeTime(post.createdAt), style = MaterialTheme.typography.bodySmall, color = Dim)
                }
                Text(post.name, style = MaterialTheme.typography.titleMedium, color = Parchment, maxLines = 1)
                val sub = listOf(post.roaster, post.origin).filter { it.isNotBlank() }.joinToString("  ·  ")
                if (sub.isNotBlank()) Text(sub, style = MaterialTheme.typography.bodySmall, color = Dim, maxLines = 1)
                if (post.notes.isNotBlank()) {
                    Text(post.notes, style = MaterialTheme.typography.bodyMedium, color = Parchment.copy(alpha = 0.8f),
                        maxLines = 2, modifier = Modifier.padding(top = 2.dp))
                }
            }
            if (post.rating > 0f) RoastStamp(post.rating, size = 44.dp, modifier = Modifier.padding(start = 8.dp))
        }
        // Like + comment action bar (with a hairline above it)
        Row(Modifier.fillMaxWidth().padding(top = 10.dp).height(1.dp).background(Dim.copy(alpha = 0.15f))) {}
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clickable {
                        cheered = !cheered
                        cheerCount += if (cheered) 1 else -1
                        scope.launch {
                            runCatching { SocialClient.setCheer(account, post.id, cheered) }
                                .onSuccess { cheerCount = it }
                                .onFailure { cheered = !cheered; cheerCount += if (cheered) 1 else -1 }
                        }
                    }
                    .padding(vertical = 4.dp, horizontal = 4.dp),
            ) {
                Icon(
                    if (cheered) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = "Like", tint = if (cheered) Crema else Dim, modifier = Modifier.size(20.dp),
                )
                Text(
                    if (cheerCount > 0) "$cheerCount" else "Like",
                    color = if (cheered) Crema else Dim, style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 18.dp).clickable { onOpenComments(post) }.padding(vertical = 4.dp, horizontal = 4.dp),
            ) {
                Icon(Icons.Default.ChatBubbleOutline, contentDescription = "Comment", tint = Dim, modifier = Modifier.size(19.dp))
                Text(
                    if (post.commentCount > 0) "${post.commentCount}" else "Comment",
                    color = Dim, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 6.dp),
                )
            }
        }
        // Inline comment preview / prompt — commenting is always one tap from the feed.
        if (post.commentCount > 1) {
            Text(
                "View all ${post.commentCount} comments",
                color = Dim, style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp).clickable { onOpenComments(post) },
            )
        }
        val lu = post.lastCommentUser
        val lt = post.lastCommentText
        if (lu != null && lt != null) {
            Row(Modifier.padding(top = 2.dp).clickable { onOpenComments(post) }) {
                Text("@$lu", color = Crema, style = MaterialTheme.typography.bodySmall)
                Text("  $lt", color = Parchment.copy(alpha = 0.85f), style = MaterialTheme.typography.bodySmall, maxLines = 2)
            }
        } else {
            Text(
                "Add a comment…",
                color = Dim, style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp).clickable { onOpenComments(post) },
            )
        }
    }
}

// ── Comments sheet ──────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CommentsSheet(account: Account, post: FeedPost, onDismiss: () -> Unit) {
    var comments by remember { mutableStateOf<List<Comment>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var draft by rememberSaveable { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var reload by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(reload) {
        runCatching { SocialClient.comments(account, post.id) }.onSuccess { comments = it }
        loading = false
    }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Surface2) {
        Column(Modifier.imePadding().padding(start = 24.dp, end = 24.dp, bottom = 24.dp)) {
            Text("Comments on ${post.name}", style = MaterialTheme.typography.titleMedium, color = Parchment)
            if (loading) {
                Loader()
            } else if (comments.isEmpty()) {
                Text("No comments yet. Say something.", color = Dim, modifier = Modifier.padding(vertical = 12.dp))
            } else {
                Column(Modifier.padding(top = 10.dp)) {
                    comments.forEach { c ->
                        Row(Modifier.padding(vertical = 6.dp)) {
                            Text("@${c.username}", color = Crema, style = MaterialTheme.typography.labelMedium)
                            Text("  ${c.text}", color = Parchment, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 12.dp)) {
                OutlinedTextField(
                    value = draft, onValueChange = { draft = it },
                    placeholder = { Text("Add a comment…", color = Dim) },
                    modifier = Modifier.weight(1f),
                    colors = fieldColors(),
                )
                TextButton(
                    enabled = draft.isNotBlank() && !sending,
                    onClick = {
                        sending = true
                        val text = draft.trim()
                        scope.launch {
                            runCatching { SocialClient.addComment(account, post.id, text) }
                                .onSuccess { draft = ""; reload++ }
                            sending = false
                        }
                    },
                ) { Text("Post", color = if (draft.isNotBlank()) Crema else Dim) }
            }
        }
    }
}

// ── Find people ─────────────────────────────────────────────────────────────

@Composable
private fun FindPeople(account: Account, onOpenProfile: (String) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf<List<UserHit>>(emptyList()) }
    LaunchedEffect(query) {
        if (query.length < 2) { results = emptyList(); return@LaunchedEffect }
        kotlinx.coroutines.delay(300)
        runCatching { SocialClient.search(account, query) }.onSuccess { results = it }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 48.dp)) {
        item { SocialField(query, { query = it }, "Search by name or @username", modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)) }
        items(results.size) { i -> PersonRow(account, results[i], onOpenProfile) }
        if (query.length >= 2 && results.isEmpty()) {
            item { Text("No one found.", color = Dim, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) }
        }
    }
}

@Composable
private fun PersonRow(account: Account, hit: UserHit, onOpenProfile: (String) -> Unit) {
    var following by remember(hit.username) { mutableStateOf(hit.following) }
    val scope = rememberCoroutineScope()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable { onOpenProfile(hit.username) }.padding(horizontal = 24.dp, vertical = 8.dp),
    ) {
        Avatar(hit.username, size = 40.dp)
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(hit.display, style = MaterialTheme.typography.titleMedium, color = Parchment)
            Text("@${hit.username}", style = MaterialTheme.typography.bodySmall, color = Dim)
        }
        OutlinedButton(onClick = {
            following = !following
            scope.launch { runCatching { SocialClient.setFollowing(account, hit.username, following) }.onFailure { following = !following } }
        }) { Text(if (following) "Following" else "Follow", color = if (following) Dim else Crema) }
    }
}

// ── Profile (mine shows QR + share; others show follow) ──────────────────────

@Composable
private fun ProfileView(
    account: Account, username: String, showBack: Boolean, onBack: () -> Unit,
    onOpenProfile: (String) -> Unit, onOpenComments: (FeedPost) -> Unit, onOpenPeople: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var profile by remember(username) { mutableStateOf<Profile?>(null) }
    var posts by remember(username) { mutableStateOf<List<FeedPost>>(emptyList()) }
    var following by remember(username) { mutableStateOf(false) }
    var reload by remember(username) { mutableStateOf(0) }
    LaunchedEffect(username, reload) {
        runCatching { SocialClient.profile(account, username) }.onSuccess { profile = it; following = it.iFollow }
        runCatching { SocialClient.userLeaderboard(account, username) }.onSuccess { posts = it }
    }
    val p = profile
    LazyColumn(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding(), contentPadding = PaddingValues(bottom = 48.dp)) {
        item {
            if (showBack) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 8.dp, top = 10.dp)) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Parchment) }
                }
            } else {
                Spacer(Modifier.height(16.dp))
            }
        }
        if (p == null) { item { Loader() }; return@LazyColumn }
        item {
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Avatar(p.username, size = 72.dp)
                Text(p.display, style = MaterialTheme.typography.headlineMedium, color = Parchment, modifier = Modifier.padding(top = 10.dp))
                Text("@${p.username}", style = MaterialTheme.typography.bodyMedium, color = Dim)
                Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                    Stat(p.beans.toString(), "beans")
                    Stat(p.followers.toString(), "followers", onClick = { onOpenPeople(true) })
                    Stat(p.following.toString(), "following", onClick = { onOpenPeople(false) })
                }
                if (p.isMe) {
                    MyProfileShare(p, context)
                } else {
                    Button(
                        onClick = {
                            following = !following
                            scope.launch { runCatching { SocialClient.setFollowing(account, p.username, following) }.onFailure { following = !following } }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (following) Surface2 else Crema,
                            contentColor = if (following) Dim else Roast,
                        ),
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp).height(46.dp),
                    ) { Text(if (following) "Following" else "Follow") }
                }
                Eyebrow("Their top beans", Modifier.align(Alignment.Start).padding(top = 24.dp, bottom = 4.dp))
            }
        }
        if (posts.isEmpty()) {
            item { Text("No rated beans posted yet.", color = Dim, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) }
        } else {
            items(posts.size) { i -> PostCard(account, posts[i], onOpenProfile, onOpenComments) }
        }
    }
}

@Composable
private fun MyProfileShare(p: Profile, context: android.content.Context) {
    val qr by produceState<ImageBitmap?>(initialValue = null, p.profileUrl) {
        value = withContext(Dispatchers.Default) { Qr.encode(p.profileUrl) }
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(top = 18.dp)) {
        Eyebrow("Follow me on Beanshelf")
        Box(Modifier.padding(top = 10.dp).clip(RoundedCornerShape(12.dp)).background(Parchment).padding(12.dp)) {
            qr?.let { Image(bitmap = it, contentDescription = "Your profile QR", modifier = Modifier.size(200.dp)) }
        }
        Text(p.profileUrl, style = MaterialTheme.typography.bodySmall, color = Dim, modifier = Modifier.padding(top = 8.dp))
        Button(
            onClick = {
                val share = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, "Follow me on Beanshelf: ${p.profileUrl}")
                }
                runCatching { context.startActivity(Intent.createChooser(share, "Share your profile")) }
            },
            colors = ButtonDefaults.buttonColors(containerColor = Crema, contentColor = Roast),
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp).height(46.dp),
        ) {
            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
            Text("Share invite link", modifier = Modifier.padding(start = 8.dp))
        }
    }
}

@Composable
private fun Stat(value: String, label: String, onClick: (() -> Unit)? = null) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
    ) {
        Text(value, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = Parchment)
        Eyebrow(label)
    }
}

// ── Followers / following list ──────────────────────────────────────────────

@Composable
private fun PeopleList(account: Account, username: String, followers: Boolean, onBack: () -> Unit, onOpenProfile: (String) -> Unit) {
    var people by remember(username, followers) { mutableStateOf<List<UserHit>?>(null) }
    LaunchedEffect(username, followers) {
        runCatching { if (followers) SocialClient.followers(account, username) else SocialClient.followingList(account, username) }
            .onSuccess { people = it }
    }
    LazyColumn(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding(), contentPadding = PaddingValues(bottom = 48.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 8.dp, top = 10.dp)) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Parchment) }
                Text(if (followers) "Followers" else "Following", style = MaterialTheme.typography.headlineMedium, color = Parchment)
            }
        }
        val list = people
        when {
            list == null -> item { Loader() }
            list.isEmpty() -> item { Text(if (followers) "No followers yet." else "Not following anyone yet.", color = Dim, modifier = Modifier.padding(24.dp)) }
            else -> items(list.size) { i -> PersonRow(account, list[i], onOpenProfile) }
        }
    }
}

// ── Account form (sign in / register) ───────────────────────────────────────

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
            }.onSuccess { onSignedIn(it) }.onFailure { error = it.message ?: "Couldn't reach the server" }
            busy = false
        }
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding().padding(horizontal = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 12.dp)) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Parchment) }
                Text("Account", style = MaterialTheme.typography.headlineMedium, color = Parchment)
            }
            Text(
                "Follow friends and see the beans they're having. Your shelf stays on your phone — only what you post is shared.",
                style = MaterialTheme.typography.bodyMedium, color = Dim, modifier = Modifier.padding(top = 8.dp, bottom = 20.dp),
            )
            Eyebrow("You", Modifier.padding(bottom = 8.dp))
            SocialField(username, { username = it.lowercase() }, "Username — letters, numbers, _")
            SocialField(display, { display = it }, "Display name (optional)")
            SocialField(password, { password = it }, "Password", password = true)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 10.dp)) }
            Row(Modifier.padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = { submit(true) },
                    enabled = !busy && username.isNotBlank() && password.length >= 4,
                    colors = ButtonDefaults.buttonColors(containerColor = Crema, contentColor = Roast),
                    modifier = Modifier.weight(1f).height(48.dp),
                ) { Text("Create account") }
                OutlinedButton(
                    onClick = { submit(false) },
                    enabled = !busy && username.isNotBlank() && password.isNotBlank(),
                    modifier = Modifier.weight(1f).height(48.dp),
                ) { Text("Sign in", color = Crema) }
            }
            if (busy) CircularProgressIndicator(color = Crema, strokeWidth = 2.dp, modifier = Modifier.padding(top = 16.dp).size(20.dp))
        }
    }
}

// ── shared bits ─────────────────────────────────────────────────────────────

@Composable
private fun Avatar(username: String, size: androidx.compose.ui.unit.Dp, onClick: (() -> Unit)? = null) {
    Box(
        (if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .size(size).clip(CircleShape).background(Crema),
        contentAlignment = Alignment.Center,
    ) {
        Text(username.take(1).uppercase(), color = Roast, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold,
            fontSize = (size.value * 0.4f).sp)
    }
}

@Composable
private fun FeedPhoto(post: FeedPost) {
    val context = LocalContext.current
    val photo by produceState<ImageBitmap?>(initialValue = null, post.id) {
        value = SocialClient.fetchPhoto(context, post)?.let { f ->
            withContext(Dispatchers.IO) { android.graphics.BitmapFactory.decodeFile(f.absolutePath)?.asImageBitmap() }
        }
    }
    Box(Modifier.size(width = 56.dp, height = 70.dp).clip(RoundedCornerShape(8.dp)).background(SurfaceHigh), contentAlignment = Alignment.Center) {
        val p = photo
        if (p != null) Image(bitmap = p, contentDescription = post.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        else Icon(Icons.Default.LocalCafe, contentDescription = null, tint = Dim, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun Loader() = CircularProgressIndicator(color = Crema, strokeWidth = 2.dp, modifier = Modifier.padding(24.dp).size(20.dp))

@Composable
private fun ErrorText(msg: String) = Text(msg, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Crema, unfocusedBorderColor = Dim.copy(alpha = 0.4f),
    focusedLabelColor = Crema, unfocusedLabelColor = Dim, cursorColor = Crema,
    focusedTextColor = Parchment, unfocusedTextColor = Parchment,
)

@Composable
private fun SocialField(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier = Modifier, password: Boolean = false) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) }, singleLine = true,
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = if (password) KeyboardOptions(keyboardType = KeyboardType.Password) else KeyboardOptions.Default,
        modifier = modifier.then(Modifier.fillMaxWidth().padding(bottom = 10.dp)),
        colors = fieldColors(),
    )
}

private fun relativeTime(ts: Long): String {
    val mins = (System.currentTimeMillis() - ts) / 60000
    return when {
        mins < 1 -> "now"; mins < 60 -> "${mins}m"; mins < 60 * 24 -> "${mins / 60}h"; else -> "${mins / (60 * 24)}d"
    }
}
