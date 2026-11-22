package io.github.a092devs.nexfusion.morphe.youtube.layout.hide.infocards

import io.github.a092devs.nexfusion.SkipTest
import io.github.a092devs.nexfusion.morphe.findMethodDirect
import org.luckypray.dexkit.query.enums.StringMatchType

/**
 * Fingerprint anchors for YouTube info cards patches.
 * Verified against YouTube 21.41.525.
 *
 * Each val is a DexKit finder that must resolve on the target build.
 */

/**
 * The method that shows/hides info cards.
 * Hook it to return false to hide info cards.
 */
@get:SkipTest
internal val hideInfoCardsMethod = findMethodDirect {
    findMethod {
        matcher {
            usingStrings(listOf("infocard", "info_card", "infoCard"), StringMatchType.Contains)
            returnType = "void"
        }
    }.first()
}
