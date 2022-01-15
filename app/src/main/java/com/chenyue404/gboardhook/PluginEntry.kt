package com.chenyue404.gboardhook

import android.content.SharedPreferences
import android.database.Cursor
import android.inputmethodservice.InputMethodService
import android.net.Uri
import de.robv.android.xposed.*
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.util.*
import kotlin.collections.ArrayList

class PluginEntry : IXposedHookLoadPackage {
    companion object {
        const val SP_FILE_NAME = "GboardinHook"
        const val SP_KEY = "color"
        const val TAG = "xposed-Gboard-hook-"
        const val PACKAGE_NAME = "com.google.android.inputmethod.latin"
        const val DAY: Long = 1000 * 60 * 60 * 24

        fun getPref(): SharedPreferences? {
            val pref = XSharedPreferences(BuildConfig.APPLICATION_ID, SP_FILE_NAME)
            return if (pref.file.canRead()) pref else null
        }
    }

    private fun log(str: String) {
        XposedBridge.log(TAG + "\n" + str)
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        val packageName = lpparam.packageName
        val classLoader = lpparam.classLoader

        if (packageName != PACKAGE_NAME) {
            return
        }

        //粘贴板最大展示量
        XposedHelpers.findAndHookMethod(
            "com.google.android.apps.inputmethod.libs.clipboard.ClipboardContentProvider",
            classLoader,
            "b",
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    log("com.google.android.apps.inputmethod.libs.clipboard.ClipboardContentProvider#b")
                    param.result = 10
                }
            }
        )
        //粘贴板过期时间，毫秒
        XposedHelpers.findAndHookMethod(
            "com.google.android.apps.inputmethod.libs.clipboard.ClipboardContentProvider",
            classLoader,
            "c",
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    log("com.google.android.apps.inputmethod.libs.clipboard.ClipboardContentProvider#c")
                    param.result = DAY * 3
                }
            }
        )
    }
}