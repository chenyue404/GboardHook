package com.chenyue404.gboardhook

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.database.Cursor
import android.net.Uri
import androidx.core.content.edit
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XSharedPreferences
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.XposedHelpers.ClassNotFoundError
import de.robv.android.xposed.XposedHelpers.findAndHookMethod
import de.robv.android.xposed.callbacks.XC_LoadPackage
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.wrap.DexMethod
import java.lang.System.loadLibrary
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
    }

    init {
        loadLibrary("dexkit")
    }

    private fun getPref(): SharedPreferences? {
        val pref = XSharedPreferences(BuildConfig.APPLICATION_ID, SP_FILE_NAME)
        return if (pref.file.canRead()) pref else null
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

        findAndHookMethod(
            Application::class.java,
            "attach",
            Context::class.java,
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val dexBridge: DexKitBridge by lazy {
                        DexKitBridge.create(classLoader, true)
                    }
                    val context = param.args.first() as Context
                    val sp = context.getSharedPreferences(
                        "gboard_hook",
                        Context.MODE_PRIVATE
                    )
                    val spKeyMethod = "SP_KEY_METHOD"
                    val spKeyMethodReadConfig = "SP_KEY_METHOD_READ_CONFIG"
                    val spKeyVersion = "SP_KEY_VERSION"
                    val versionCode = context.packageManager.getPackageInfo(
                        context.packageName,
                        0
                    ).versionCode
                    val gboardVersion = sp.getInt(spKeyVersion, -1)
                    val isSameVersion = versionCode == gboardVersion
                    val methodStr = sp.getString(spKeyMethod, null)
                    val dexMethod: DexMethod? = methodStr?.let {
                        try {
                            DexMethod(it)
                        } catch (e: Exception) {
                            log("dexMethod-$it")
                            XposedBridge.log(e.toString())
                            null
                        }
                    }
                    (if (isSameVersion && dexMethod != null) {
                        dexMethod
                    } else {
                        val method = findAdapterMethod(dexBridge)
                        if (method != null) {
                            sp.edit {
                                putInt(spKeyVersion, versionCode)
                                putString(spKeyMethod, method.serialize())
                            }
                        }
                        method
                    })?.let {
                        hookAdapter(it, classLoader)
                    }

                    val methodReadConfigStr = sp.getString(spKeyMethodReadConfig, null)
                    val dexMethodReadConfig: DexMethod? = methodReadConfigStr?.let {
                        try {
                            DexMethod(it)
                        } catch (e: Exception) {
                            log("dexMethodReadConfig-$it")
                            XposedBridge.log(e.toString())
                            null
                        }
                    }
                    (if (isSameVersion && dexMethodReadConfig != null) {
                        dexMethodReadConfig
                    } else {
                        val method = findReadConfigMethod(dexBridge)
                        if (method != null) {
                            sp.edit {
                                putInt(spKeyVersion, versionCode)
                                putString(spKeyMethodReadConfig, method.serialize())
                            }
                        }
                        method
                    })?.let {
                        hookReadConfig(it, classLoader)
                    }
                }
            }
        )

        tryHook("com.google.android.apps.inputmethod.libs.clipboard.ClipboardContentProvider#query") { name ->
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
                        log(name)
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

//        tryHook("SQLiteQuery#query") {
//            findAndHookMethod(
//                "android.database.sqlite.SQLiteDatabase",
//                classLoader,
//                "query",
//                "java.lang.String",
//                "java.lang.String[]",
//                "java.lang.String",
//                "java.lang.String[]",
//                "java.lang.String",
//                "java.lang.String",
//                "java.lang.String",
//                object : XC_MethodHook() {
//                    override fun beforeHookedMethod(param: MethodHookParam) {
//                        val args = param.args
//                        val table = args.first().toString()
//                        if (table == "clips") {
//                            val arg1 = if (param.args[1] != null) {
//                                param.args[1] as Array<*>
//                            } else null
//                            val arg2 = param.args[2].toString()
//                            val arg3 = if (param.args[3] != null) {
//                                param.args[3] as Array<String>
//                            } else null
//                            val arg4 = param.args[4]
//                            val arg5 = param.args[5]
//                            val arg6 = param.args[6]
//                            log("SQLiteQuery#query, arg1=${arg1?.joinToString()}, arg2=$arg2, arg3=${arg3?.joinToString()}, arg4=$arg4, arg5=$arg5, arg6=$arg6")
//                        }
//                    }
//                }
//            )
//        }


//        try {
//            val dexFile = DexFile(lpparam.appInfo.sourceDir)
//            val entries = dexFile.entries()
//            while (entries.hasMoreElements()) {
//                val className = entries.nextElement()
//                try {
//                    val clazz = lpparam.classLoader.loadClass(className)
//                    // 查找是否有 getDumpableTag 方法
//                    val method = clazz.declaredMethods.firstOrNull {
//                        it.parameterTypes.contentEquals(
//                            arrayOf(
//                                ImageView::class.java, ImageView::class.java, String::class.java
//                            )
//                        ) && it.returnType == Void.TYPE
//                    } ?: continue
//
//                    // 找到了候选类，hook它
//                    log("Hooking candidate: $className")
//                    findAndHookMethod(
//                        clazz,
//                        "E",
//                        object : XC_MethodHook() {
//                            override fun beforeHookedMethod(param: MethodHookParam) {
//                                val x = XposedHelpers.getIntField(param.thisObject, "x")
//                                val p = XposedHelpers.getIntField(param.thisObject, "p")
//                                log("$className#E, x=$x, p=$p")
//                                XposedHelpers.setIntField(
//                                    param.thisObject,
//                                    "p",
//                                    0
//                                )
//                            }
//                        }
//                    )
//                } catch (e: Throwable) {
//                    // 加载失败的类忽略
//                }
//            }
//        } catch (e: Throwable) {
//            log("Error scanning dex: $e")
//        }
    }

    private fun tryHook(logStr: String, unit: ((name: String) -> Unit)) {
        try {
            unit(logStr)
        } catch (e: NoSuchMethodError) {
            log("NoSuchMethodError--$logStr")
        } catch (e: ClassNotFoundError) {
            log("ClassNotFoundError--$logStr")
        }
    }

    private fun findAdapterMethod(bridge: DexKitBridge): DexMethod? {
        val methodData = bridge.findClass {
            matcher {
                usingStrings("com/google/android/apps/inputmethod/libs/clipboard/ClipboardAdapter")
                superClass {
                    this.classNameMatcher != null
                }
            }
        }.findMethod {
            matcher {
                usingNumbers(5)
            }
        }.singleOrNull()
        if (methodData == null) {
            log("Can't find adapter")
            return null
        }
        return methodData.toDexMethod()
    }

    private fun hookAdapter(dexMethod: DexMethod, classLoader: ClassLoader) {
        val methodName = dexMethod.name
        val className = dexMethod.className
        val tag = "$className#$methodName"
        log(tag)
        tryHook(tag) {
            findAndHookMethod(
                className, classLoader, methodName,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        log(tag)
//                        printInfo("${tag}-before", param.thisObject)
                        param.result = null
                    }
                })
        }
    }

    private fun printInfo(tag: String, obj: Any) {
        val p = XposedHelpers.getIntField(obj, "p")
        val x = XposedHelpers.getIntField(obj, "x")
        val y = XposedHelpers.getIntField(obj, "y")
        val list = XposedHelpers.getObjectField(obj, "o") as List<*>
        log("$tag: p=$p, x=$x, y=$y, list.size=${list.size}")
    }

    private fun findReadConfigMethod(bridge: DexKitBridge): DexMethod? {
        val methodData = bridge.findMethod {
            matcher {
                usingStrings("Invalid flag: ")
                returnType("java.lang.Object")
            }
        }.singleOrNull()
        if (methodData == null) {
            log("Can't find ReadConfig")
            return null
        }
        return methodData.toDexMethod()
    }

    /**
     * 写死enable_clipboard_entity_extraction的值，不然limit永远是100，查出来就只有5个
     */
    private fun hookReadConfig(dexMethod: DexMethod, classLoader: ClassLoader) {
        val methodName = dexMethod.name
        val className = dexMethod.className
        val tag = "$className#$methodName"
        log(tag)
        tryHook(tag) {
            findAndHookMethod(
                className, classLoader, methodName,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val name = XposedHelpers.getObjectField(param.thisObject, "a").toString()
                        if (name == "enable_clipboard_entity_extraction"
                            || name == "enable_clipboard_query_refactoring"
                        ) {
                            param.result = false
                        }
                    }

//                    override fun afterHookedMethod(param: MethodHookParam) {
//                        val name = XposedHelpers.getObjectField(param.thisObject, "a").toString()
//                        if (name.contains("clipboard")) {
//                            log("$tag, name=$name, result=${param.result}")
//                        }
//                    }
                })
        }
    }
}