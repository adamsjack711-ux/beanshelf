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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import dev.adamsjack.beanshelf.ui.AddEditScreen
import dev.adamsjack.beanshelf.ui.BeanshelfTheme
import dev.adamsjack.beanshelf.ui.DetailScreen
import dev.adamsjack.beanshelf.ui.LeaderboardScreen
import dev.adamsjack.beanshelf.ui.ShelfScreen

class MainActivity : ComponentActivity() {

    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // App is dark regardless of system theme — force light system-bar icons.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        setContent {
            BeanshelfTheme {
                App(vm)
            }
        }
        requestSegmentationModel()
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

    BackHandler(enabled = vm.nav != Screen.Shelf) { vm.goBack() }

    Crossfade(targetState = vm.nav, label = "nav") { screen ->
        when (screen) {
            is Screen.Shelf -> ShelfScreen(
                beans = beans,
                onAdd = { vm.nav = Screen.Edit(null) },
                onOpen = { vm.nav = Screen.Detail(it.id) },
                onLeaderboard = { vm.nav = Screen.Leaderboard },
            )

            is Screen.Leaderboard -> LeaderboardScreen(
                beans = beans,
                onBack = { vm.nav = Screen.Shelf },
                onOpen = { vm.nav = Screen.Detail(it.id) },
            )

            is Screen.Detail -> {
                val bean = beans.firstOrNull { it.id == screen.beanId }
                if (bean == null) {
                    // Deleted underneath us — fall back to the shelf.
                    ShelfScreen(
                        beans = beans,
                        onAdd = { vm.nav = Screen.Edit(null) },
                        onOpen = { vm.nav = Screen.Detail(it.id) },
                        onLeaderboard = { vm.nav = Screen.Leaderboard },
                    )
                } else {
                    DetailScreen(
                        bean = bean,
                        allBrews = beans.flatMap { it.brews }.sortedByDescending { it.timestamp },
                        onBack = { vm.nav = Screen.Shelf },
                        onEdit = { vm.nav = Screen.Edit(bean.id) },
                        onDelete = {
                            vm.deleteBean(bean.id)
                            vm.nav = Screen.Shelf
                        },
                        onLogBrew = { vm.addBrew(bean.id, it) },
                    )
                }
            }

            is Screen.Edit -> AddEditScreen(
                existing = screen.beanId?.let { vm.bean(it) },
                onSave = { bean ->
                    vm.upsertBean(bean)
                    vm.nav = Screen.Detail(bean.id)
                },
                onBack = { vm.goBack() },
            )
        }
    }
}
