package com.moefactory.bettermiuiexpress.activity

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import android.content.Context
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import com.moefactory.bettermiuiexpress.R
import com.moefactory.bettermiuiexpress.base.app.PREF_KEY_DEVICE_TRACK_ID
import com.moefactory.bettermiuiexpress.ktx.hideLauncherIcon
import com.moefactory.bettermiuiexpress.ktx.isLauncherIconEnabled
import com.moefactory.bettermiuiexpress.repository.ExpressActualRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import java.util.UUID
import top.yukonga.miuix.kmp.basic.SmallTitle
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.Alignment

class MainActivity : ComponentActivity(), XposedServiceHelper.OnServiceListener {

    private var mService: XposedService? = null
    private val isModuleActiveState = mutableStateOf(false)
    private val frameworkNameState = mutableStateOf("")
    private val frameworkApiVersionState = mutableStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val isModuleActive by isModuleActiveState
            val frameworkName by frameworkNameState
            val frameworkApiVersion by frameworkApiVersionState
            val controller = remember { ThemeController(ColorSchemeMode.System) }
            
            MiuixTheme(controller = controller) {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            color = MiuixTheme.colorScheme.background,
                            title = stringResource(R.string.app_name)
                        )
                    }
                ) { paddingValues ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                            .padding(horizontal = 16.dp)
                    ) {
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Card(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .padding(16.dp)
                                    .fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (isModuleActive) stringResource(R.string.active) else stringResource(R.string.inactive),
                                        style = MiuixTheme.textStyles.title3.copy(fontWeight = FontWeight.SemiBold),
                                        color = if (isModuleActive) Color(0xFF4CAF50) else Color(0xFFF44336)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = if (isModuleActive) stringResource(R.string.active_hook_framework_version, frameworkName, frameworkApiVersion)
                                               else stringResource(R.string.inactive_description),
                                        style = MiuixTheme.textStyles.body2,
                                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))
                        
                        SmallTitle(text = "关于")
                        
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column {
                                ArrowPreference(
                                    title = "GitHub",
                                    summary = "查看源码与开源协议",
                                    onClick = {
                                        startActivity(Intent(Intent.ACTION_VIEW).setData("https://github.com/Robotxm/BetterMiuiExpress".toUri()))
                                    }
                                )
                                ArrowPreference(
                                    title = "博客",
                                    summary = "访问作者博客",
                                    onClick = {
                                        startActivity(Intent(Intent.ACTION_VIEW).setData("https://moefactory.com".toUri()))
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        XposedServiceHelper.registerListener(this)
    }

    override fun onServiceBind(service: XposedService) {
        mService = service
        isModuleActiveState.value = true
        frameworkNameState.value = service.frameworkName ?: ""
        frameworkApiVersionState.value = service.apiVersion
        
        checkAndGenerateTrackId(service)
    }

    override fun onServiceDied(service: XposedService) {
        mService = null
        isModuleActiveState.value = false
        frameworkNameState.value = ""
        frameworkApiVersionState.value = 0
    }

    private fun checkAndGenerateTrackId(service: XposedService) {
        val prefs = getSharedPreferences(com.moefactory.bettermiuiexpress.base.app.PREF_NAME, Context.MODE_PRIVATE)
        lifecycleScope.launch(Dispatchers.IO) {
            val currentGeneratedTrackId = prefs.getString(PREF_KEY_DEVICE_TRACK_ID, "") ?: ""
            if (currentGeneratedTrackId.isNotEmpty()) {
                // Sync to RemotePreferences just in case
                service.getRemotePreferences("default").edit()?.putString(PREF_KEY_DEVICE_TRACK_ID, currentGeneratedTrackId)?.apply()
                return@launch
            }

            val generatedTrackId = UUID.randomUUID().toString()
            if (ExpressActualRepository.registerDeviceTrackIdActual(generatedTrackId)) {
                prefs.edit().apply {
                    putString(PREF_KEY_DEVICE_TRACK_ID, generatedTrackId)
                }.apply()
                service.getRemotePreferences("default").edit()?.putString(PREF_KEY_DEVICE_TRACK_ID, generatedTrackId)?.apply()

                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, R.string.init_success_and_hide, Toast.LENGTH_SHORT).show()
                }

                delay(5000)

                if (isLauncherIconEnabled()) {
                    hideLauncherIcon()
                }
            }
        }
    }
}
