package io.github.nexfusion.morphe.music.misc.playservice

import io.github.nexfusion.patch
import kotlin.properties.Delegates


// Use notNull delegate so an exception is thrown if these fields are accessed before they are set.

var is_9_19_or_greater: Boolean by Delegates.notNull()
    private set
var is_9_20_or_greater: Boolean by Delegates.notNull()
    private set
var is_9_21_or_greater: Boolean by Delegates.notNull()
    private set
var is_9_24_or_greater: Boolean by Delegates.notNull()
    private set
var is_9_26_or_greater: Boolean by Delegates.notNull()
    private set
var is_9_28_or_greater: Boolean by Delegates.notNull()
    private set
var is_9_30_or_greater: Boolean by Delegates.notNull()
    private set
var is_9_32_or_greater: Boolean by Delegates.notNull()
    private set
var is_9_33_or_greater: Boolean by Delegates.notNull()
    private set
var is_9_35_or_greater: Boolean by Delegates.notNull()
    private set
var is_9_37_or_greater: Boolean by Delegates.notNull()
    private set
var is_9_39_or_greater: Boolean by Delegates.notNull()
    private set
var is_9_41_or_greater: Boolean by Delegates.notNull()
    private set
var is_9_43_or_greater: Boolean by Delegates.notNull()
    private set
var is_9_45_or_greater: Boolean by Delegates.notNull()
    private set

val versionCheckPatch = patch {
    val versionName = appContext.packageManager
        .getPackageInfo(appContext.packageName, 0).versionName!!

    fun isEqualsOrGreaterThan(version: String): Boolean {
        return io.github.nexfusion.patches.isVersionAtLeast(versionName, version)
    }

    is_9_19_or_greater = isEqualsOrGreaterThan("9.19.00")
    is_9_20_or_greater = isEqualsOrGreaterThan("9.20.00")
    is_9_21_or_greater = isEqualsOrGreaterThan("9.21.00")
    is_9_24_or_greater = isEqualsOrGreaterThan("9.24.00")
    is_9_26_or_greater = isEqualsOrGreaterThan("9.26.00")
    is_9_28_or_greater = isEqualsOrGreaterThan("9.28.00")
    is_9_30_or_greater = isEqualsOrGreaterThan("9.30.00")
    is_9_32_or_greater = isEqualsOrGreaterThan("9.32.00")
    is_9_33_or_greater = isEqualsOrGreaterThan("9.33.00")
    is_9_35_or_greater = isEqualsOrGreaterThan("9.35.00")
    is_9_37_or_greater = isEqualsOrGreaterThan("9.37.00")
    is_9_39_or_greater = isEqualsOrGreaterThan("9.39.00")
    is_9_41_or_greater = isEqualsOrGreaterThan("9.41.00")
    is_9_43_or_greater = isEqualsOrGreaterThan("9.43.00")
    is_9_45_or_greater = isEqualsOrGreaterThan("9.45.00")
}