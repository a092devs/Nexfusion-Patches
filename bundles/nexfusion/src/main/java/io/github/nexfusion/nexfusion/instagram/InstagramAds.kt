package io.github.nexfusion.nexfusion.instagram

import de.robv.android.xposed.XC_MethodReplacement.returnConstant
import io.github.nexfusion.PatchExecutor
import io.github.nexfusion.PatcherLog
import io.github.nexfusion.morphe.findMethodDirect
import org.luckypray.dexkit.query.enums.StringMatchType

/**
 * The ad injector, the method the ad patches guard.
 *
 * Instagram puts ads into feeds, reels and stories two ways. Some it fetches by an endpoint, which
 * InstagramRequestFilter blocks. The rest it inserts itself, through this one method, which answers
 * whether the item went in; blocking endpoints never stops those, they are how sponsored reels and
 * stories keep appearing.
 *
 * The anchor is HushGram's, the cross-surface one: on 449 and 450 the insert is the only method
 * holding both "cross_surface_duplicate_ad" and "Is ad pod", the strings its debug map is written
 * with. Instagram 451 holds both in one method as well, LX/05gS;->A02(LX/05gS;LX/01h7;LX/04nk;)Z,
 * which answers a boolean. Piko's finder matched "Is ad pod" alone; the second string guards
 * against a method copied around the build without the insert.
 */
internal val adInjectorMethod = findMethodDirect {
    fun query(matchType: StringMatchType) = findMethod {
        matcher { usingStrings(listOf("cross_surface_duplicate_ad", "Is ad pod"), matchType) }
    }
    query(StringMatchType.Equals).ifEmpty { query(StringMatchType.Contains) }
        .single { it.returnTypeName == "boolean" }
}

@Volatile
private var adInjectorGuarded = false

/**
 * Makes the ad injector answer that nothing went in, from the patches that hide ads. False leaves
 * no gap where an ad would have been, and the insert's callers skip the item entirely. The guard
 * is said once per app process because the insert serves every surface: which ad patch runs first
 * installs it, the rest find it in place.
 */
internal fun PatchExecutor.guardAdInjector() {
    if (adInjectorGuarded) return
    runCatching {
        ::adInjectorMethod.hookMethod(returnConstant(false))
        adInjectorGuarded = true
        PatcherLog.info { "NexFusion Instagram: the ad injector answers that nothing went in" }
    }.onFailure {
        PatcherLog.info(it) { "NexFusion Instagram: failed to guard ad injector" }
    }
}
