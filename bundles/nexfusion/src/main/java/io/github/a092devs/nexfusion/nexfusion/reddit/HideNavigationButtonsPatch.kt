package io.github.a092devs.nexfusion.nexfusion.reddit

import app.morphe.extension.reddit.patches.HideNavigationButtonsPatch as ExtHideNav
import io.github.a092devs.nexfusion.compat.XC_MethodHook
import io.github.a092devs.nexfusion.compat.XposedBridge
import io.github.a092devs.nexfusion.morphe.AccessFlags
import io.github.a092devs.nexfusion.morphe.Fingerprint
import io.github.a092devs.nexfusion.morphe.Opcode
import io.github.a092devs.nexfusion.morphe.fieldAccess
import io.github.a092devs.nexfusion.morphe.methodCall
import io.github.a092devs.nexfusion.morphe.newInstance
import io.github.a092devs.nexfusion.patch

internal object BottomNavScreenListBuilderFingerprint : Fingerprint(
    definingClass = "Lcom/reddit/launch/bottomnav/BottomNavScreen;",
    returnType = "L",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    filters = listOf(
        newInstance("Ljava/util/ArrayList;"),
        methodCall(
            opcode = Opcode.INVOKE_INTERFACE,
            smali = "Ljava/util/Iterator;->hasNext()Z",
        ),
        fieldAccess(
            opcode = Opcode.IGET_OBJECT,
            type = "Lcom/reddit/launch/bottomnav/BottomNavTab;",
        ),
    ),
)

val HideNavigationButtons = patch(
    name = "Hide Reddit navigation buttons",
    description = "Hides selected buttons (Games, Answers, Chat, Communities, Create) from Reddit's navigation bar.",
    use = false,
) {
    val listBuilder = BottomNavScreenListBuilderFingerprint.memberOrNull ?: return@patch
    val tabClass = runCatching {
        classLoader.loadClass("com.reddit.launch.bottomnav.BottomNavTab")
    }.getOrNull()

    XposedBridge.hookMethod(
        listBuilder,
        object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val res = param.result ?: return
                val listField = if (res is List<*>) null else res.javaClass.declaredFields
                    .firstOrNull { List::class.java.isAssignableFrom(it.type) }
                    ?.apply { isAccessible = true }
                val list = (res as? List<*>) ?: (listField?.get(res) as? List<*>) ?: return

                val toRemove = ArrayList<Any?>()
                val kept = ArrayList<Any?>(list.size)
                for (item in list) {
                    if (item == null) {
                        kept.add(null)
                        continue
                    }
                    val tabEnum: Enum<*>? = when {
                        item is Enum<*> -> item
                        else -> generateSequence<Class<*>>(item.javaClass) { it.superclass }
                            .flatMap { it.declaredFields.asSequence() }
                            .firstOrNull {
                                (tabClass != null && it.type == tabClass) ||
                                    Enum::class.java.isAssignableFrom(it.type)
                            }
                            ?.apply { isAccessible = true }
                            ?.get(item) as? Enum<*>
                    }
                    if (ExtHideNav.hideNavigationTab(tabEnum)) {
                        toRemove.add(item)
                    } else {
                        kept.add(item)
                    }
                }
                if (toRemove.isEmpty()) return

                if (list is MutableList<*>) {
                    @Suppress("UNCHECKED_CAST")
                    val mutable = list as MutableList<Any?>
                    mutable.clear()
                    mutable.addAll(kept)
                } else {
                    val removeAll = list.javaClass.methods.firstOrNull {
                        it.name == "removeAll" && it.parameterTypes.size == 1 &&
                            Collection::class.java.isAssignableFrom(it.parameterTypes[0])
                    }
                    val updated = if (removeAll != null) {
                        runCatching { removeAll.invoke(list, toRemove) }.getOrNull() ?: kept
                    } else {
                        kept
                    }
                    if (listField != null) {
                        listField.set(res, updated)
                    } else {
                        param.result = updated
                    }
                }
            }
        },
    )
}
