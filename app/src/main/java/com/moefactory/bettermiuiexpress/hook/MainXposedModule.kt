package com.moefactory.bettermiuiexpress.hook

import android.content.Context
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import com.moefactory.bettermiuiexpress.base.app.PA_EXPRESS_ENTRY
import com.moefactory.bettermiuiexpress.base.app.PA_EXPRESS_INFO_DETAIL
import com.moefactory.bettermiuiexpress.base.app.PA_EXPRESS_REPOSITOIRY
import com.moefactory.bettermiuiexpress.base.app.PA_EXPRESS_ROUTER
import com.moefactory.bettermiuiexpress.base.app.PA_PACKAGE_NAME
import com.moefactory.bettermiuiexpress.activity.ExpressDetailsActivity
import com.moefactory.bettermiuiexpress.model.ExpressEntryWrapper
import com.moefactory.bettermiuiexpress.model.MiuiExpress
import com.moefactory.bettermiuiexpress.model.isJingDong
import com.moefactory.bettermiuiexpress.model.shouldUseNativeUI
import com.moefactory.bettermiuiexpress.model.toExpressEntryWrapper
import com.moefactory.bettermiuiexpress.model.toExpressInfoJumpListWrapper
import com.moefactory.bettermiuiexpress.model.ExpressInfoWrapper
import com.moefactory.bettermiuiexpress.model.ExpressTrace
import com.moefactory.bettermiuiexpress.model.isXiaomiOrJingDong
import com.moefactory.bettermiuiexpress.model.toExpressInfoDetailWrapper
import com.moefactory.bettermiuiexpress.model.toExpressInfoWrapper
import com.moefactory.bettermiuiexpress.model.toExpressTrace
import com.moefactory.bettermiuiexpress.repository.ExpressActualRepository
import com.moefactory.bettermiuiexpress.utils.ExpressCompanyUtils
import kotlinx.coroutines.runBlocking
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainXposedModule : XposedModule() {

    companion object {
        const val PREF_KEY_DEVICE_TRACK_ID = "deviceTrackId"
    }

    override fun onPackageReady(param: PackageReadyParam) {
        if (param.packageName != PA_PACKAGE_NAME) return

        val classLoader = param.classLoader
        
        hookExpressRouter(classLoader)
        hookExpressRepository(classLoader)
    }

    private fun hookExpressRouter(classLoader: ClassLoader) {
        val routerClass = Class.forName(PA_EXPRESS_ROUTER, false, classLoader)
        val entryClass = Class.forName(PA_EXPRESS_ENTRY, false, classLoader)

        val routeMethod = routerClass.getDeclaredMethod("route", Context::class.java, Any::class.java, entryClass)
        
        hook(routeMethod).intercept { chain ->
            val context = chain.args[0] as Context
            val expressEntry = chain.args[2]
            val expressEntryWrapper = expressEntry.toExpressEntryWrapper()

            if (jumpToDetailsActivity(context, expressEntryWrapper)) {
                return@intercept null
            } else if (expressEntryWrapper.shouldUseNativeUI() && expressEntryWrapper.isJingDong) {
                // From new versions of PA, details of packages from JingDong will be display in JD app by default, which is unexpected
                // Here we just intercept it
                val arg1 = chain.args[1]
                if (arg1 != null) {
                    val gotoNativeMethod = routerClass.getDeclaredMethod("gotoNative", Context::class.java, Any::class.java, entryClass)
                    getInvoker(gotoNativeMethod).setType(XposedInterface.Invoker.Type.ORIGIN).invoke(null, context, arg1, expressEntry)
                    return@intercept null
                }
            }

            // Other details will be processed normally
            return@intercept chain.proceed()
        }
    }

    private fun jumpToDetailsActivity(context: Context, expressEntryWrapper: ExpressEntryWrapper): Boolean {
        val companyCode = expressEntryWrapper.companyCode
        val companyName = expressEntryWrapper.companyName
        val mailNumber = expressEntryWrapper.orderNumber
        val phoneNumber = expressEntryWrapper.phone
        val jumpList = expressEntryWrapper.jumpList?.mapNotNull { it?.toExpressInfoJumpListWrapper() }
        val uris = expressEntryWrapper.uris?.mapNotNull { it?.toExpressInfoJumpListWrapper() } 
        if (!expressEntryWrapper.shouldUseNativeUI()) {
            if (!jumpList.isNullOrEmpty()) {
                ExpressDetailsActivity.gotoDetailsActivity(
                    context,
                    MiuiExpress(companyCode, companyName, mailNumber, phoneNumber),
                    ArrayList(jumpList)
                )
            } else if (!uris.isNullOrEmpty()) {
                ExpressDetailsActivity.gotoDetailsActivity(
                    context,
                    MiuiExpress(companyCode, companyName, mailNumber, phoneNumber),
                    ArrayList(uris)
                )
            } else {
                ExpressDetailsActivity.gotoDetailsActivity(
                    context,
                    MiuiExpress(companyCode, companyName, mailNumber, phoneNumber),
                    null
                )
            }
            return true
        }
        return false
    }

    private fun hookExpressRepository(classLoader: ClassLoader) {
        val repositoryClass = Class.forName(PA_EXPRESS_REPOSITOIRY, false, classLoader)
        val saveExpressMethod = repositoryClass.getDeclaredMethod("saveExpress", java.util.List::class.java)

        hook(saveExpressMethod).intercept { chain ->
            runBlocking {
                val listArg = chain.args[0] as? java.util.List<*>
                val expressInfoList = listArg?.map { it!!.toExpressInfoWrapper() }
                    ?.filter { !it.isXiaomiOrJingDong }
                
                if (expressInfoList != null) {
                    val deviceTrackId = getRemotePreferences("default").getString(PREF_KEY_DEVICE_TRACK_ID, "") ?: ""
                    for (expressInfoWrapper in expressInfoList) {
                        val companyCode = expressInfoWrapper.companyCode
                        val mailNumber = expressInfoWrapper.orderNumber
                        val phoneNumber = expressInfoWrapper.phone ?: expressInfoWrapper.sendPhone

                        val detailList = fetchExpressDetails(mailNumber, companyCode, phoneNumber, deviceTrackId)

                        if (detailList.isNullOrEmpty()) {
                            continue
                        }

                        val detailClass = Class.forName(PA_EXPRESS_INFO_DETAIL, false, classLoader)
                        saveLatestExpressTrace(expressInfoWrapper, detailClass, detailList)
                    }
                }
            }
            chain.proceed()
        }
    }

    private fun saveLatestExpressTrace(
        expressInfoWrapper: ExpressInfoWrapper,
        detailClass: Class<*>,
        detailList: List<ExpressTrace>
    ) {
        expressInfoWrapper.clickDisappear = false
        val originalDetails = expressInfoWrapper.details
        when {
            originalDetails == null -> {
                val newDetail = detailClass.getDeclaredConstructor().newInstance()
                val newDetailWrapper = newDetail.toExpressInfoDetailWrapper()
                newDetailWrapper.desc = detailList[0].description
                newDetailWrapper.time = detailList[0].fullDateTime
                val newDetails = ArrayList<Any>(1)
                newDetails.add(newDetail)
                expressInfoWrapper.details = newDetails
            }
            originalDetails.isEmpty() -> {
                val newDetail = detailClass.getDeclaredConstructor().newInstance()
                val newDetailWrapper = newDetail.toExpressInfoDetailWrapper()
                newDetailWrapper.desc = detailList[0].description
                newDetailWrapper.time = detailList[0].fullDateTime
                originalDetails.add(newDetail)
            }
            else -> {
                expressInfoWrapper.details?.getOrNull(0)
                    ?.toExpressInfoDetailWrapper()
                    ?.desc = detailList[0].description
            }
        }
    }

    private suspend fun fetchExpressDetails(
        mailNumber: String, originalCompanyCode: String, phoneNumber: String?, deviceTrackId: String
    ): List<ExpressTrace>? {
        val convertedCompanyCode = ExpressCompanyUtils.convertCode(originalCompanyCode)
            ?: ExpressActualRepository.queryCompanyActual(mailNumber).firstOrNull()?.companyCode

        if (deviceTrackId.isEmpty()) {
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA)
            val currentDateTimeString = sdf.format(Date())

            return listOf(
                ExpressTrace(
                    fullDateTime = currentDateTimeString,
                    date = currentDateTimeString.split(" ")[0],
                    time = currentDateTimeString.split(" ")[1],
                    description = "请先打开模块主界面完成初始化"
                )
            )
        }

        val response = ExpressActualRepository.queryExpressDetailsFromKuaiDi100Actual(convertedCompanyCode!!, mailNumber, phoneNumber, deviceTrackId)
        return response?.lastResult?.data?.map { it.toExpressTrace() }?.sortedDescending()
    }
}
