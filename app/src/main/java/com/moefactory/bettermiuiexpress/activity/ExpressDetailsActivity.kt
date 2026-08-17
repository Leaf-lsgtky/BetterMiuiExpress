package com.moefactory.bettermiuiexpress.activity

import android.annotation.SuppressLint
import android.app.Activity
import android.app.ActivityManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.moefactory.bettermiuiexpress.R
import com.moefactory.bettermiuiexpress.base.app.PREF_KEY_DEVICE_TRACK_ID
import com.moefactory.bettermiuiexpress.model.ExpressTrace
import com.moefactory.bettermiuiexpress.model.MiuiExpress
import com.moefactory.bettermiuiexpress.model.ExpressInfoJumpListWrapper
import com.moefactory.bettermiuiexpress.viewmodel.ExpressDetailsViewModel
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.menu.OverlayIconDropdownMenu
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.style.TextAlign
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventDispatcherOwner
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.compose.runtime.CompositionLocalProvider
@SuppressLint("WorldReadableFiles")
class ExpressDetailsActivity : ComponentActivity() {

    companion object {
        private const val ACTION_GO_TO_DETAILS = "com.moefactory.bettermiuiexpress.details"
        const val INTENT_EXPRESS_SUMMARY = "express_summary"
        const val INTENT_URL_CANDIDATES = "url_candidates"

        fun gotoDetailsActivity(
            context: Context,
            miuiExpress: MiuiExpress,
            uris: ArrayList<ExpressInfoJumpListWrapper>?
        ) {
            if (context is Activity) {
                context.startActivity(
                    Intent(ACTION_GO_TO_DETAILS)
                        .putExtra(INTENT_EXPRESS_SUMMARY, miuiExpress)
                        .putExtra(INTENT_URL_CANDIDATES, uris)
                )
            } else {
                context.startActivity(
                    Intent(ACTION_GO_TO_DETAILS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        .putExtra(INTENT_EXPRESS_SUMMARY, miuiExpress)
                        .putExtra(INTENT_URL_CANDIDATES, uris)
                )
            }
        }
    }

    private val miuiExpress by lazy { intent.getParcelableExtra<MiuiExpress>(INTENT_EXPRESS_SUMMARY) }
    private val uris by lazy { intent.getParcelableArrayListExtra<ExpressInfoJumpListWrapper>(INTENT_URL_CANDIDATES) }
    private val viewModel by viewModels<ExpressDetailsViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            setTaskDescription(ActivityManager.TaskDescription(getString(R.string.express_details_title), R.drawable.ic_pa))
        } else {
            setTaskDescription(ActivityManager.TaskDescription(getString(R.string.express_details_title)))
        }

        if (miuiExpress == null) {
            Toast.makeText(this, R.string.unexpected_error, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        viewModel.kuaiDi100CompanyInfo.observe(this) {
            queryExpressDetails(
                miuiExpress!!.mailNumber,
                it.companyCode,
                miuiExpress!!.phoneNumber,
            )
        }

        setContent {
            val controller = remember { ThemeController(ColorSchemeMode.System) }
            val expressDetailsState = viewModel.expressDetails.observeAsState(null)
            val scrollBehavior = MiuixScrollBehavior()
            
            val navigationEventDispatcherOwner = remember {
                object : NavigationEventDispatcherOwner {
                    override val navigationEventDispatcher = NavigationEventDispatcher()
                }
            }

            MiuixTheme(controller = controller) {
                CompositionLocalProvider(
                    LocalNavigationEventDispatcherOwner provides navigationEventDispatcherOwner
                ) {
                    Scaffold(
                        topBar = {
                            TopAppBar(
                                title = miuiExpress?.companyName ?: "物流详情",
                                navigationIcon = {
                                    IconButton(onClick = { finish() }) {
                                        Icon(imageVector = MiuixIcons.Back, contentDescription = "Back")
                                    }
                                },
                                actions = {
                                    if (!uris.isNullOrEmpty()) {
                                        OverlayIconDropdownMenu(
                                            entries = listOf(
                                                DropdownEntry(
                                                    items = listOf(
                                                        DropdownItem(
                                                            text = getString(R.string.jump_to_third_party_app),
                                                            onClick = { startThirdAppByUris(uris) }
                                                        )
                                                    )
                                                )
                                            )
                                        ) {
                                            Icon(imageVector = MiuixIcons.More, contentDescription = "More")
                                        }
                                    }
                                },
                                scrollBehavior = scrollBehavior
                            )
                        }
                    ) { paddingValues ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                        ) {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .nestedScroll(scrollBehavior.nestedScrollConnection),
                                contentPadding = paddingValues
                            ) {
                                item {
                                    // Header Card
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 8.dp),
                                        onClick = {
                                            val debugMiuiExpress = miuiExpress?.copy(phoneNumber = null)
                                            (getSystemService(CLIPBOARD_SERVICE) as? ClipboardManager)?.setPrimaryClip(
                                                ClipData.newPlainText("BME-Debug", debugMiuiExpress.toString())
                                            )
                                            Toast.makeText(this@ExpressDetailsActivity, R.string.debug_info_copied, Toast.LENGTH_SHORT).show()
                                        }
                                    ) {
                                        Column(modifier = Modifier.padding(16.dp)) {
                                            expressDetailsState.value?.let { result ->
                                                if (result.isSuccess) {
                                                    val response = result.getOrNull()
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        if (response != null && response.traces.isNotEmpty()) {
                                                            Box(
                                                                modifier = Modifier
                                                                    .background(
                                                                        color = MiuixTheme.colorScheme.primary.copy(alpha = 0.2f),
                                                                        shape = RoundedCornerShape(4.dp)
                                                                    )
                                                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                                                                contentAlignment = Alignment.Center
                                                            ) {
                                                                Text(
                                                                    text = response.status,
                                                                    style = MiuixTheme.textStyles.body2,
                                                                    color = MiuixTheme.colorScheme.primary,
                                                                    textAlign = TextAlign.Center
                                                                )
                                                            }
                                                            Spacer(modifier = Modifier.width(8.dp))
                                                        }
                                                        Text(
                                                            text = getString(R.string.express_details_mail_number, miuiExpress!!.mailNumber),
                                                            style = MiuixTheme.textStyles.title4,
                                                            fontWeight = FontWeight.Medium,
                                                            textAlign = TextAlign.Center
                                                        )
                                                    }
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    
                                                    if (response == null || response.traces.isEmpty()) {
                                                        Text(
                                                            text = getString(R.string.express_state_unknown),
                                                            style = MiuixTheme.textStyles.body2,
                                                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                                                        )
                                                    }
                                                } else {
                                                    Text(
                                                        text = getString(R.string.express_state_unknown),
                                                        style = MiuixTheme.textStyles.body2,
                                                        color = MiuixTheme.colorScheme.error
                                                    )
                                                }
                                            } ?: run {
                                                Text(
                                                    text = getString(R.string.express_state_unknown),
                                                    style = MiuixTheme.textStyles.body2,
                                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                                                )
                                            }
                                        }
                                    }
                                }
    
                                // Timeline
                                expressDetailsState.value?.let { result ->
                                    if (result.isSuccess) {
                                        val response = result.getOrNull()
                                        if (response != null && response.traces.isNotEmpty()) {
                                            item {
                                                Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                                                    Column(
                                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 16.dp)
                                                    ) {
                                                        response.traces.forEachIndexed { index, trace ->
                                                            TimelineNode(
                                                                trace = trace,
                                                                isFirst = index == 0,
                                                                isLast = index == response.traces.size - 1
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                            item {
                                                Text(
                                                    text = getString(R.string.data_provider_tips, response.dataSource),
                                                    style = MiuixTheme.textStyles.body2,
                                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                                    textAlign = TextAlign.Center,
                                                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                                                )
                                            }
                                        }
                                    } else {
                                        item {
                                            Column(
                                                modifier = Modifier.fillMaxWidth().padding(32.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.Center
                                            ) {
                                                Image(
                                                    painter = painterResource(id = R.drawable.app_express_detail_list_empty),
                                                    contentDescription = null,
                                                    modifier = Modifier.padding(bottom = 17.dp)
                                                )
                                                Text(
                                                    text = getString(R.string.app_express_detail_list_empty_text),
                                                    style = MiuixTheme.textStyles.body2,
                                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                                    textAlign = TextAlign.Center
                                                )
                                            }
                                        }
                                    }
                                } ?: run {
                                    item {
                                        Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                            CircularProgressIndicator()
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        queryExpressDetails(
            miuiExpress!!.mailNumber,
            miuiExpress!!.companyCode,
            miuiExpress!!.phoneNumber,
        )
    }

    private fun queryExpressDetails(
        mailNumber: String,
        companyCode: String?,
        phoneNumber: String?,
    ) {
        val prefs = getSharedPreferences(com.moefactory.bettermiuiexpress.base.app.PREF_NAME, Context.MODE_PRIVATE)
        val deviceTrackId = prefs.getString(PREF_KEY_DEVICE_TRACK_ID, "")

        viewModel.queryExpressDetails(
            mailNumber,
            companyCode,
            phoneNumber,
            deviceTrackId
        ) { generatedTrackId ->
            prefs.edit().apply {
                putString(PREF_KEY_DEVICE_TRACK_ID, generatedTrackId)
            }.apply()
            Toast.makeText(this, R.string.init_success, Toast.LENGTH_SHORT).show()
        }
    }

    private fun startThirdAppByUris(jumpList: ArrayList<ExpressInfoJumpListWrapper>?) {
        if (jumpList.isNullOrEmpty()) return
        val sortedList = jumpList.sorted()

        for (uri in sortedList) {
            if (uri.type == "applet" && !resolvePackage("com.tencent.mm")) {
                // Missing wechat
            } else if (uri.link.isNullOrEmpty()) {
                // Missing jump link
            } else {
                val intent = Intent("android.intent.action.VIEW")
                intent.data = uri.link.toUri()
                if (tryToStartThirdPartyActivity(intent)) {
                    finish()
                    return
                }
            }
        }
        Toast.makeText(this, R.string.failed_to_jump, Toast.LENGTH_SHORT).show()
    }

    private fun resolvePackage(packageName: String?): Boolean {
        return try {
            packageName != null && packageManager.getPackageInfo(packageName, 0) != null
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    private fun tryToStartThirdPartyActivity(intent: Intent): Boolean {
        return try {
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }
}

@Composable
fun TimelineNode(trace: ExpressTrace, isFirst: Boolean, isLast: Boolean) {
    val primaryColor = MiuixTheme.colorScheme.primary
    val mutedColor = MiuixTheme.colorScheme.onSurfaceVariantSummary
    val textColor = if (isFirst) MiuixTheme.colorScheme.onSurface else mutedColor
    val dotColor = if (isFirst) primaryColor else mutedColor.copy(alpha = 0.2f)
    val lineColor = mutedColor.copy(alpha = 0.1f)

    Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        // Timeline graphic
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(28.dp)
        ) {
            if (!isFirst) {
                Box(modifier = Modifier.width(1.dp).height(20.dp).background(lineColor))
            } else {
                Spacer(modifier = Modifier.height(20.dp))
            }
            Box(
                modifier = Modifier
                    .size(if (isFirst) 12.dp else 8.dp)
                    .background(dotColor, CircleShape)
            )
            if (!isLast) {
                Box(modifier = Modifier.width(1.dp).weight(1f).defaultMinSize(minHeight = 32.dp).background(lineColor))
            } else {
                Spacer(modifier = Modifier.weight(1f).defaultMinSize(minHeight = 32.dp))
            }
        }
        
        Spacer(modifier = Modifier.width(12.dp))

        // Content
        Column(modifier = Modifier.weight(1f).padding(bottom = 16.dp, top = 12.dp)) {
            Text(
                text = "${trace.date} ${trace.time}",
                style = MiuixTheme.textStyles.body2,
                color = textColor,
                fontWeight = if (isFirst) FontWeight.SemiBold else FontWeight.Normal
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = trace.description,
                style = MiuixTheme.textStyles.body1,
                color = textColor
            )
        }
    }
}