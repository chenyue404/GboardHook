package com.chenyue404.gboardhook

import android.content.Context
import android.content.SharedPreferences
import android.database.Cursor
import android.net.Uri
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XSharedPreferences
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers.*
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


class PluginEntry : IXposedHookLoadPackage {
    companion object {
        const val SP_FILE_NAME = "GboardinHook"
        const val SP_KEY = "key"
        const val SP_KEY_LOG = "key_log"
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

    private val clipboardTextSize by lazy {
        getPref()?.getString(SP_KEY, null)?.split(",")?.get(0)?.toIntOrNull()
            ?: DEFAULT_NUM
    }

    private val clipboardTextTime by lazy {
        getPref()?.getString(SP_KEY, null)?.split(",")?.get(1)?.toLongOrNull()
            ?: DEFAULT_TIME
    }

    private val logSwitch by lazy {
        getPref()?.getBoolean(SP_KEY_LOG, false) ?: false
    }

    private fun log(str: String) {
        if (logSwitch)
            XposedBridge.log(TAG + "\n" + str)
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

        tryHook("com.google.android.apps.inputmethod.libs.clipboard.ClipboardContentProvider#b") {
            //粘贴板最大展示量
            findAndHookMethod(
                "com.google.android.apps.inputmethod.libs.clipboard.ClipboardContentProvider",
                classLoader,
                "b",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        param.result = clipboardTextSize
                    }
                }
            )
        }
        tryHook("com.google.android.apps.inputmethod.libs.clipboard.ClipboardContentProvider#c") {
            //粘贴板过期时间，毫秒
            findAndHookMethod(
                "com.google.android.apps.inputmethod.libs.clipboard.ClipboardContentProvider",
                classLoader,
                "c",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        param.result = clipboardTextTime
                    }
                }
            )
        }

//        tryHook("fgv#a") {
//            findAndHookMethod("fgv", classLoader, "a",
//                Uri::class.java,
//                String::class.java,
//                Array<String>::class.java,
//                String::class.java, object : XC_MethodHook() {
//                    override fun beforeHookedMethod(param: MethodHookParam) {
//                        val uri = param.args.first() as Uri
//                        val arg1 = param.args[1] as String
//                        val arg2 = param.args[2] as Array<String>
//                        val arg3 = param.args[3] as String
//                        log("uri=$uri\narg1=$arg1\narg2=${arg2.joinToString()}\narg3=$arg3")
//                        val indexOf = arg1.indexOf("timestamp >= ?")
//                        if (indexOf != -1) {
//                            var indexOfWen = 0
//                            StringBuilder(arg1).forEachIndexed { index, c ->
//                                if (index >= indexOf) return@forEachIndexed
//                                if (c == '?') {
//                                    indexOfWen++
//                                }
//                            }
//
//                            val afterTimeStamp = System.currentTimeMillis() - clipboardTextTime
//                            arg2[indexOfWen] = afterTimeStamp.toString()
//                            param.args[2] = arg2
//                            log(
//                                "修改时间限制, ${
//                                    SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.ROOT)
//                                        .format(Date(afterTimeStamp))
//                                }"
//                            )
//                        }
//                        if (arg3 == "timestamp DESC limit 5") {
//                            param.args[3] = "timestamp DESC limit $clipboardTextSize"
//                            log("修改大小限制, $clipboardTextSize")
//                        }
//                    }
//                })
//        }

        tryHook("fhv#d(context,Collection)") {
            findAndHookMethod(
                "fhv", classLoader,
                "d",
                Context::class.java,
                Collection::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        log("fhv#e(context,Collection)")
                        val collection = param.args[1] as Collection<*>
                        log(collection.joinToString())
                    }
                }
            )
        }

        tryHook("com.google.android.apps.inputmethod.libs.clipboard.ClipboardContentProvider.delete(Uri,String,String[])") {
            findAndHookMethod(
                "com.google.android.apps.inputmethod.libs.clipboard.ClipboardContentProvider",
                classLoader,
                "delete",
                Uri::class.java,
                String::class.java,
                Array<String>::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        log("com.google.android.apps.inputmethod.libs.clipboard.ClipboardContentProvider.delete(Uri,String,String[])")
                        val uri = param.args[0] as Uri
                        val str = param.args[1]
                        val strArr = if (param.args[2] != null) {
                            param.args[2] as Array<*>
                        } else null
                        log("uri=$uri, str=$str, strArr=${strArr?.joinToString() ?: "null"}")
                    }
                }
            )
        }

        tryHook("com.google.android.apps.inputmethod.libs.clipboard.ClipboardContentProvider.query") {
            findAndHookMethod(
                "com.google.android.apps.inputmethod.libs.clipboard.ClipboardContentProvider",
                classLoader,
                "query",
                Uri::class.java,
                Array<String>::class.java,
                String::class.java,
                Array<String>::class.java,
                String::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        log("com.google.android.apps.inputmethod.libs.clipboard.ClipboardContentProvider.query")
                        val arg0 = param.args[0] as Uri
                        val arg1 = if (param.args[1] != null) {
                            param.args[1] as Array<*>
                        } else null
                        val arg2 = param.args[2].toString()
                        val arg3 = if (param.args[3] != null) {
                            param.args[3] as Array<String>
                        } else null
                        val arg4 = param.args[4]
                        log("query, arg0=$arg0, arg1=${arg1?.joinToString()}, arg2=$arg2, arg3=${arg3?.joinToString()}, arg4=$arg4")

                        val indexOf = arg2.indexOf("timestamp >= ?")
                        if (indexOf != -1) {
                            var indexOfWen = 0
                            StringBuilder(arg2).forEachIndexed { index, c ->
                                if (index >= indexOf) return@forEachIndexed
                                if (c == '?') {
                                    indexOfWen++
                                }
                            }

                            val afterTimeStamp = System.currentTimeMillis() - clipboardTextTime
                            arg3?.let {
                                it[indexOfWen] = afterTimeStamp.toString()
                                param.args[3] = it
                            }
                            log(
                                "修改时间限制, ${
                                    SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.ROOT)
                                        .format(Date(afterTimeStamp))
                                }"
                            )
                        }
                        if (arg4 == "timestamp DESC limit 5") {
                            param.args[4] = "timestamp DESC limit $clipboardTextSize"
                            log("修改大小限制, $clipboardTextSize")
                        }
                    }

                    override fun afterHookedMethod(param: MethodHookParam) {
                        log("query end, ${(param.result as Cursor).count}")
                    }
                }
            )
        }

        tryHook("qcy#a") {
            findAndHookMethod("qcy", classLoader, "a",
                String::class.java,
                Boolean::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        return
//                        log("qcy#a")
                        val str = param.args[0].toString()
                        val value = param.args[1] as Boolean
//                        log("qcy#a, str=$str, value=$value")
                        val modify = when (str) {
                            "enable_clipboard_entity_extraction",
                            "enable_settings_search",
                            "enable_settings_two_pane_display",
                            -> true

                            else -> null
                        }
                        if (modify == true) {
                            log("qcy#a, str=$str, value=$value")
                            param.args[1] = true
                        }
                    }

                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (param.args[0] == "enable_clipboard_entity_extraction") {
                            log("qcy#a, ${param.result}")
                        }
                    }
                })
        }

        tryHook("fij#b") {
            findAndHookMethod("fij", classLoader, "b",
                Uri::class.java,
                Int::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        log("fij#b, uri=${param.args[0]}, i=${param.args[1]}")
                    }
                })
        }
    }

    private fun tryHook(logStr: String, unit: (() -> Unit)) {
        try {
            unit()
        } catch (e: NoSuchMethodError) {
            log("NoSuchMethodError--$logStr")
        } catch (e: ClassNotFoundError) {
            log("ClassNotFoundError--$logStr")
        }
    }
}