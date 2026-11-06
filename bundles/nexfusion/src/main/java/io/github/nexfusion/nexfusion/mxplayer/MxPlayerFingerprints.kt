package io.github.nexfusion.nexfusion.mxplayer

import io.github.nexfusion.morphe.findClassDirect
import org.luckypray.dexkit.query.enums.StringMatchType

/**
 * Bytecode fingerprints for MX Player ad blocking and debloating.
 * Resolves obfuscated R8 classes dynamically across versions.
 */

val mxGlobalConfig = findClassDirect {
    findClass {
        matcher {
            usingStrings(listOf("localmusicOpen"), StringMatchType.Equals)
        }
    }.firstOrNull { !it.name.contains("$") }
        ?: findClass {
            matcher { className("bw7") }
        }.firstOrNull()
        ?: findClass {
            matcher { className("com.mxtech.videoplayer.ad.GlobalConfig") }
        }.first()
}

val mxWelcomeAdFeatureFlag = findClassDirect {
    findClass {
        matcher {
            usingStrings(listOf("shouldWelcomeAdEnabled"), StringMatchType.Equals)
        }
    }.firstOrNull { !it.name.contains("$") }
        ?: findClass {
            matcher { className("hgl") }
        }.firstOrNull()
        ?: findClass {
            matcher { className("com.mxtech.videoplayer.ad.online.ad.welcome.WelcomeAdFeatureFlag") }
        }.first()
}

val mxAdInitializer = findClassDirect {
    findClass {
        matcher {
            usingStrings(listOf("AdInitializer"), StringMatchType.Equals)
        }
    }.firstOrNull { !it.name.contains("$") }
        ?: findClass {
            matcher { className("mq") }
        }.firstOrNull()
        ?: findClass {
            matcher { className("com.mxplay.monetize.v2.adinitializer.AdInitializer") }
        }.first()
}

val mxInterstitial = findClassDirect {
    findClass {
        matcher {
            usingStrings(listOf("key_show_interstitial_ad_tips"), StringMatchType.Equals)
        }
    }.firstOrNull { !it.name.contains("$") }
        ?: findClass {
            matcher { className("en9") }
        }.firstOrNull()
        ?: findClass {
            matcher { className("com.mxplay.monetize.v2.interstitial.InterstitialV2") }
        }.first()
}

val mxVideoRollManager = findClassDirect {
    findClass {
        matcher {
            usingStrings(listOf("vastTimeOut"), StringMatchType.Equals)
        }
    }.firstOrNull { !it.name.contains("$") }
        ?: findClass {
            matcher { className("ozk") }
        }.firstOrNull()
        ?: findClass {
            matcher { className("com.mxplay.monetize.v2.videoroll.VideoRollManager") }
        }.first()
}
