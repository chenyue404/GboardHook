package com.chenyue404.gboardhook

import android.content.SharedPreferences
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XSharedPreferences
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

class PluginEntry : IXposedHookLoadPackage {
    companion object {
        const val SP_FILE_NAME = "GboardinHook"
        const val SP_KEY = "key"
        const val TAG = "xposed-Gboard-hook-"
        const val PACKAGE_NAME = "com.google.android.inputmethod.latin"
        const val DAY: Long = 1000 * 60 * 60 * 24
        const val DEFAULT_NUM = 10
        const val DEFAULT_TIME = DAY * 3

        fun getPref(): SharedPreferences? {
            val pref = XSharedPreferences(BuildConfig.APPLICATION_ID, SP_FILE_NAME)
            return if (pref.file.canRead()) pref else null
        }
    }

    private fun log(str: String) {
//        XposedBridge.log(TAG + "\n" + str)
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        val packageName = lpparam.packageName
        val classLoader = lpparam.classLoader

        if (packageName != PACKAGE_NAME &&
            getPref()?.getString(SP_KEY, null)?.split(",")?.getOrNull(2)
                ?.equals("true", true) == false
        ) {
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
                    val num = getPref()?.getString(SP_KEY, null)?.split(",")?.get(0)?.toIntOrNull()
                        ?: DEFAULT_NUM
                    log(num.toString())
                    param.result = num

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
                    val time: Long =
                        getPref()?.getString(SP_KEY, null)?.split(",")?.get(1)?.toLongOrNull()
                            ?: DEFAULT_TIME
                    log(time.toString())
                    param.result = time
                }
            }
        )
    }
}