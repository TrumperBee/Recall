package com.kumbukaapp.recall

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.kumbukaapp.recall.data.repository.RecordingRepository
import com.kumbukaapp.recall.service.AppLock
import com.kumbukaapp.recall.service.RecordingFileManager
import com.kumbukaapp.recall.ui.screens.CallDetailScreen
import com.kumbukaapp.recall.ui.screens.HistoryScreen
import com.kumbukaapp.recall.ui.screens.OnboardingScreen
import com.kumbukaapp.recall.ui.screens.SearchScreen
import com.kumbukaapp.recall.ui.screens.SettingsScreen
import com.kumbukaapp.recall.ui.screens.isOnboardingDone
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject lateinit var repository: RecordingRepository

    // Activity result launcher for SAF folder selection
    private val folderLauncher: ActivityResultLauncher<Intent> = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                try {
                    contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (e: Exception) {
                    // Persist grant failed; folder won't survive reboot
                }
                getSharedPreferences("recall_prefs", MODE_PRIVATE)
                    .edit()
                    .putString("folder_uri", uri.toString())
                    .apply()
            }
        }
    }

    // Activity result launcher for attaching an audio file
    private val filePickerLauncher: ActivityResultLauncher<String> = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            val callId = pendingAttachCallId
            if (callId != null) {
                pendingAttachCallId = null
                pendingAttachUri = null
                lifecycleScope.launch {
                    val path = RecordingFileManager.importFromUri(
                        applicationContext,
                        uri,
                        callId,
                        repository
                    )
                    Toast.makeText(
                        this@MainActivity,
                        if (path != null) "Recording attached" else "Failed to attach recording",
                        Toast.LENGTH_SHORT
                    ).show()
                    attachVersion++
                }
            } else {
                // No call selected; remember the URI for the next open call detail
                pendingAttachUri = uri.toString()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                RecallApp(
                    onPickFolder = {
                        folderLauncher.launch(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE))
                    },
                    onAttachFile = { callId ->
                        pendingAttachCallId = callId
                        filePickerLauncher.launch("audio/*")
                    },
                    attachVersion = attachVersion
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (AppLock.isAppLocked()) {
            AppLock.authenticate(this, onSuccess = {}, onFailed = {})
        }
    }

    override fun onStop() {
        super.onStop()
        val enabled = getSharedPreferences("recall_prefs", MODE_PRIVATE)
            .getBoolean("app_lock_enabled", false)
        if (enabled) {
            AppLock.lock()
        }
    }

    fun launchFolderPicker() {
        folderLauncher.launch(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE))
    }

    companion object {
        /** Set when a file has been picked; consumed by the attach flow */
        @Volatile
        var pendingAttachUri: String? = null

        /** Call id that a picked file should be attached to (set before the picker) */
        @Volatile
        var pendingAttachCallId: Int? = null
    }

    /** Incremented after each successful import so call detail can reload */
    private var attachVersion by mutableIntStateOf(0)
}

private data class BottomTab(
    val route: String,
    val label: String,
    val icon: ImageVector
)

private val BOTTOM_TABS = listOf(
    BottomTab("history", "History", Icons.Default.List),
    BottomTab("search", "Search", Icons.Default.Search),
    BottomTab("settings", "Settings", Icons.Default.Settings)
)

@Composable
private fun RecallApp(
    onPickFolder: () -> Unit,
    onAttachFile: (Int) -> Unit,
    attachVersion: Int
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val context = LocalContext.current
    val startDestination = remember {
        if (isOnboardingDone(context)) "history" else "onboarding"
    }

    Scaffold(
        bottomBar = {
            if (currentRoute in BOTTOM_TABS.map { it.route }) {
                NavigationBar {
                    BOTTOM_TABS.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = {
                                navController.navigate(tab.route) {
                                    launchSingleTop = true
                                    popUpTo(navController.graph.startDestinationId) {
                                        saveState = true
                                    }
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(padding)
        ) {
            composable("onboarding") {
                OnboardingScreen(
                    onStart = {
                        navController.navigate("history") {
                            popUpTo("onboarding") { inclusive = true }
                        }
                    }
                )
            }

            composable("history") {
                HistoryScreen(
                    onCallSelected = { recording ->
                        navController.navigate("call_detail/${recording.id}")
                    }
                )
            }

            composable("search") {
                SearchScreen(
                    onCallSelected = { recording ->
                        navController.navigate("call_detail/${recording.id}")
                    }
                )
            }

            composable("settings") {
                SettingsScreen(onPickFolder = onPickFolder)
            }

            composable(
                route = "call_detail/{callId}",
                arguments = listOf(navArgument("callId") { type = NavType.IntType })
            ) { entry ->
                val callId = entry.arguments?.getInt("callId") ?: -1
                if (callId >= 0) {
                    CallDetailScreen(
                        callId = callId,
                        onBack = { navController.popBackStack() },
                        onAttachRequest = onAttachFile,
                        attachVersion = attachVersion
                    )
                }
            }
        }
    }
}
