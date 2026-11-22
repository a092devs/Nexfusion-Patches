package io.github.a092devs.nexfusion.nexfusion.reddit

import io.github.a092devs.nexfusion.PatcherLog
import io.github.a092devs.nexfusion.getObjectField
import io.github.a092devs.nexfusion.morphe.Fingerprint
import io.github.a092devs.nexfusion.patch
import io.github.a092devs.nexfusion.setObjectField

// Ported from MorpheApp/morphe-patches (GPL-3.0),
// patches/src/main/kotlin/app/morphe/patches/reddit/ad/HideAdsPatch.kt.
//
// Morphe rewrites the listing constructors at build time. The same anchors are hooked here at
// runtime: old ads are dropped from the Listing's children, the newer AdPostSection is built
// empty, comment ad loaders return immediately and the ad-state constructor is marked complete
// so Reddit does not draw its placeholders. Each hook is installed independently so a build that
// moved one anchor keeps the others.

private fun versionAtLeast(versionName: String, target: String): Boolean {
    fun parse(version: String) = version.substringBefore('-').split('.')
        .mapNotNull { it.trim().toIntOrNull() }
    val installed = parse(versionName)
    val wanted = parse(target)
    for (index in 0 until maxOf(installed.size, wanted.size)) {
        val left = installed.getOrElse(index) { 0 }
        val right = wanted.getOrElse(index) { 0 }
        if (left != right) return left > right
    }
    return true
}

val HideAds = patch(
    name = "Hide Reddit ads",
    description = "Hides promoted posts, comment ads and their placeholders.",
) {
    val versionName = runCatching {
        appContext.packageManager.getPackageInfo(appContext.packageName, 0).versionName.orEmpty()
    }.getOrDefault("")
    val is2026_04 = versionAtLeast(versionName, "2026.04.0")
    val is2026_16 = versionAtLeast(versionName, "2026.16.0")

    fun hideOldAds(fingerprint: Fingerprint) {
        fingerprint.hookMethod {
            val iLink = runCatching { classLoader.loadClass("com.reddit.domain.model.ILink") }.getOrNull()
                ?: return@hookMethod
            val getPromoted = iLink.methods.singleOrNull { it.name == "getPromoted" } ?: return@hookMethod
            after { param ->
                runCatching {
                    val rawChildren = runCatching { param.thisObject.getObjectField("children") }.getOrNull()
                    val arrayList = (rawChildren as? Iterable<*>) ?: return@after
                    val result = mutableListOf<Any?>()
                    var filtered = 0
                    for (item in arrayList) {
                        try {
                            if (item != null && iLink.isAssignableFrom(item.javaClass) && getPromoted(item) == true) {
                                filtered++
                                continue
                            }
                        } catch (_: Throwable) {
                            // Not an ILink; keep it.
                        }
                        result.add(item)
                    }
                    if (filtered > 0) {
                        param.thisObject.setObjectField("children", result)
                    }
                }.onFailure { error ->
                    PatcherLog.info(error) { "NexFusion: Reddit listing ad filter skipped" }
                }
            }
        }
    }

    runCatching { hideOldAds(ListingFingerprint) }
        .onFailure { PatcherLog.info(it) { "NexFusion: Reddit Listing ad anchor not found" } }

    if (!is2026_16) {
        runCatching { hideOldAds(SubmittedListingFingerprint) }
            .onFailure { PatcherLog.info(it) { "NexFusion: Reddit SubmittedListing ad anchor not found" } }
    }

    runCatching {
        AdPostSectionConstructorFingerprint.hookMethod {
            val immutableListBuilder = ::ImmutableListBuilderReference.method
            before {
                it.args[4] = immutableListBuilder(null, arrayListOf<Any>())
            }
        }
    }.onFailure { PatcherLog.info(it) { "NexFusion: Reddit AdPostSection anchor not found" } }

    runCatching {
        CommentsViewModelAdLoaderFingerprint.hookMethod {
            before { it.result = Unit }
        }
    }.onFailure { PatcherLog.info(it) { "NexFusion: Reddit comment ad loader anchor not found" } }

    if (is2026_04) {
        runCatching {
            CommentsAdStateConstructorFingerprint.hookMethod {
                val adsLoadCompleted = ::adsLoadCompletedField.field
                after { adsLoadCompleted.set(it.thisObject, true) }
            }
        }.onFailure { PatcherLog.info(it) { "NexFusion: Reddit comment ad placeholder anchor not found" } }
    }
}
