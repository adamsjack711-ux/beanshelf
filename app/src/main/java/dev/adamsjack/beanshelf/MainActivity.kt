package dev.adamsjack.beanshelf

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import dev.adamsjack.beanshelf.ui.AddEditScreen
import dev.adamsjack.beanshelf.ui.BeanshelfTheme
import dev.adamsjack.beanshelf.ui.BottomBar
import dev.adamsjack.beanshelf.ui.DetailScreen
import dev.adamsjack.beanshelf.ui.LeaderboardScreen
import dev.adamsjack.beanshelf.ui.SettingsScreen
import dev.adamsjack.beanshelf.ui.ShelfScreen
import dev.adamsjack.beanshelf.ui.SocialScreen

class MainActivity : ComponentActivity() {

    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Restore the saved palette before first frame; the theme flips bar icons.
        dev.adamsjack.beanshelf.ui.setPalette(
            dev.adamsjack.beanshelf.ui.Palettes.byKey(
                dev.adamsjack.beanshelf.data.ThemePrefs.loadKey(this)
            )
        )
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        setContent {
            BeanshelfTheme {
                App(vm)
            }
        }
        requestSegmentationModel()
        handleDeepLink(intent)
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        handleDeepLink(intent)
    }

    /** beanshelf://u/<username> → jump to that person's profile in Social. */
    private fun handleDeepLink(intent: android.content.Intent?) {
        val data = intent?.data ?: return
        if (data.scheme == "beanshelf" && data.host == "u") {
            data.lastPathSegment?.takeIf { it.isNotBlank() }?.let { vm.openProfile(it) }
        }
    }

    /** Kick off the subject-segmentation model download so the first scan can cut out the bag. */
    private fun requestSegmentationModel() {
        runCatching {
            val segmenter = com.google.mlkit.vision.segmentation.subject.SubjectSegmentation.getClient(
                com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions.Builder()
                    .enableForegroundBitmap()
                    .build()
            )
            com.google.android.gms.common.moduleinstall.ModuleInstall.getClient(this)
                .installModules(
                    com.google.android.gms.common.moduleinstall.ModuleInstallRequest.newBuilder()
                        .addApi(segmenter)
                        .build()
                )
                .addOnSuccessListener {
                    android.util.Log.d("BagCropper", "segmentation module: ${if (it.areModulesAlreadyInstalled()) "already installed" else "install requested"}")
                }
                .addOnFailureListener { android.util.Log.d("BagCropper", "module install failed: $it") }
        }
    }
}

@Composable
private fun App(vm: AppViewModel) {
    val beans by vm.beans.collectAsState()

    BackHandler(enabled = vm.overlay != null || vm.tab != Tab.Shelf) { vm.goBack() }

    // A full-screen overlay (Detail / Edit / Leaderboard) covers the bottom bar.
    val overlay = vm.overlay
    if (overlay != null) {
        Crossfade(targetState = overlay, label = "overlay") { o ->
            when (o) {
                is Overlay.Leaderboard -> LeaderboardScreen(
                    beans = beans,
                    onBack = { vm.closeOverlay() },
                    onOpen = { vm.openDetail(it.id) },
                )

                is Overlay.Detail -> {
                    val bean = beans.firstOrNull { it.id == o.beanId }
                    if (bean == null) {
                        vm.closeOverlay()
                    } else {
                        DetailScreen(
                            bean = bean,
                            allBrews = beans.flatMap { it.brews }.sortedByDescending { it.timestamp },
                            onBack = { vm.closeOverlay() },
                            onEdit = { vm.openEdit(bean.id) },
                            onDelete = { vm.deleteBean(bean.id); vm.closeOverlay() },
                            onLogBrew = { vm.addBrew(bean.id, it) },
                        )
                    }
                }

                is Overlay.Edit -> AddEditScreen(
                    existing = o.beanId?.let { vm.bean(it) },
                    onSave = { bean -> vm.upsertBean(bean); vm.openDetail(bean.id) },
                    onBack = { vm.goBack() },
                )
            }
        }
        return
    }

    // Otherwise: the four tabs with a persistent bottom bar.
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            Crossfade(targetState = vm.tab, label = "tab") { tab ->
                when (tab) {
                    Tab.Feed -> SocialScreen(
                        onBack = { vm.pendingProfile = null; vm.tab = Tab.Shelf },
                        initialProfile = vm.pendingProfile,
                    )

                    Tab.Shelf -> ShelfScreen(
                        beans = beans,
                        onAdd = { vm.openEdit(null) },
                        onOpen = { vm.openDetail(it.id) },
                        onLeaderboard = { vm.openLeaderboard() },
                        onImport = { vm.importBean(it) },
                    )

                    Tab.Profile -> SocialScreen(
                        onBack = { vm.tab = Tab.Shelf },
                        startOnProfile = true,
                    )

                    Tab.Settings -> SettingsScreen(onBack = { vm.tab = Tab.Shelf })
                }
            }
        }
        BottomBar(current = vm.tab, onSelect = { vm.tab = it })
    }
}
