package com.gallerysorter.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        vm.start(hasPhotoPermission())
        setContent {
            GalleryTheme { AppRoot(vm) }
        }
    }

    override fun onStop() {
        vm.saveNow()
        super.onStop()
    }

    private fun hasPhotoPermission() = hasPhotoPermission(this)
}

private fun photoPermission() =
    if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE

private fun hasPhotoPermission(context: android.content.Context) =
    ContextCompat.checkSelfPermission(context, photoPermission()) == PackageManager.PERMISSION_GRANTED

@Composable
private fun AppRoot(vm: MainViewModel) {
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        vm.onPermission(granted)
    }
    val requestLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        vm.onRequestResult(result.resultCode == android.app.Activity.RESULT_OK)
    }

    // Launch system trash / restore / delete confirmations requested by the ViewModel.
    LaunchedEffect(vm.pendingRequest) {
        vm.pendingRequest?.let { sender ->
            vm.requestLaunched()
            try {
                requestLauncher.launch(IntentSenderRequest.Builder(sender).build())
            } catch (e: Exception) {
                vm.onRequestResult(false)
            }
        }
    }

    LaunchedEffect(vm.message) {
        vm.message?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            vm.messageShown()
        }
    }

    // Returning from system settings with access granted.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (hasPhotoPermission(context)) vm.onPermission(true)
    }

    val screen = vm.screen
    BackHandler(enabled = screen != Screen.HOME && screen != Screen.WELCOME) { vm.back() }

    Box(Modifier.fillMaxSize().background(Color.White)) {
        Crossfade(targetState = screen, label = "screen") { current ->
            when (current) {
                Screen.WELCOME -> WelcomeScreen(onStart = vm::goPermission)
                Screen.PERMISSION -> PermissionScreen(
                    denied = vm.permissionDenied,
                    onAllow = { permissionLauncher.launch(photoPermission()) },
                    onOpenSettings = {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                        )
                    },
                )
                Screen.HOME -> HomeScreen(vm)
                Screen.SWIPE -> SwipeScreen(vm)
                Screen.REVIEW -> ReviewScreen(vm)
                Screen.MARKED -> MarkedScreen(vm)
                Screen.TRASH -> TrashScreen(vm)
                Screen.DONE -> DoneScreen(vm)
            }
        }
        if (vm.busy) {
            Box(
                Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.6f)).clickable(enabled = true) {},
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator(color = Navy) }
        }
    }
}
