package io.github.nexfusion.nexfusion.gmail

import io.github.nexfusion.morphe.findMethodListDirect
import org.luckypray.dexkit.query.enums.StringMatchType

// Ported from MrxSiN/GmailHideAds (GPL-3.0) and gnadgnaoh/SexAlloy (GPL-3.0).

internal const val AD_PACKAGE = "com.google.android.gm.ads"

val adViewConstructors = findMethodListDirect {
    findMethod {
        matcher {
            declaredClass { className("$AD_PACKAGE.", StringMatchType.StartsWith) }
            name = "<init>"
            paramTypes(listOf("android.content.Context", "android.util.AttributeSet"))
        }
    }
}

