package io.github.a092devs.nexfusion.morphe.youtube.misc.settings

import io.github.a092devs.nexfusion.morphe.findClassDirect
import io.github.a092devs.nexfusion.morphe.findMethodDirect
import io.github.a092devs.nexfusion.morphe.findMethodListDirect
import io.github.a092devs.nexfusion.morphe.resourceMappings
import org.luckypray.dexkit.query.enums.StringMatchType
import java.lang.reflect.Modifier

val PreferenceFragmentCompatClass = findClassDirect {
    findClass {
        matcher {
            usingStrings(
                "Could not create RecyclerView",
                "Content has view with id attribute 'android.R.id.list_container' that is not a ViewGroup class",
                "androidx.preference.PreferenceFragmentCompat.PREFERENCE_ROOT"
            )
        }
    }.single()
}

val PreferenceFragmentCompat_addPreferencesFromResource = findMethodDirect {
    PreferenceFragmentCompatClass().let { preferenceFragmentCompat ->
        preferenceFragmentCompat.findMethod {
            matcher {
                returnType = "void"
                paramTypes("int")
            }
        }.singleOrNull() ?: preferenceFragmentCompat.findMethod {
            matcher {
                name = "addPreferencesFromResource"
            }
        }.single()
    }
}

val licenseActivityClass = findClassDirect {
    findClass {
        matcher {
            className(".LicenseActivity", StringMatchType.EndsWith)
        }
    }.single()
}

val licenseActivitySuperOnCreate = findMethodDirect {
    licenseActivityClass().superClass!!.findMethod { matcher { name = "onCreate" } }.single()
}

val licenseActivityOnCreateFingerprint = findMethodDirect {
    licenseActivityClass().findMethod { matcher { name = "onCreate" } }.single()
}

val licenseActivityNOTonCreate = findMethodListDirect {
    licenseActivityClass().methods.filter { it.name != "onCreate" && it.isMethod }
}

val appearanceStringId get() = resourceMappings["string", "app_theme_appearance_dark"]

val setThemeFingerprint = findMethodDirect {
    findMethod {
        matcher {
            modifiers = Modifier.PUBLIC or Modifier.FINAL
            paramCount = 0
            addUsingNumber(appearanceStringId)
        }
    }.single { it.returnTypeName != "void" }
}

val PreferenceFragmentCompat_getPreferenceScreen = findMethodDirect {
    PreferenceFragmentCompatClass().findMethod {
        matcher {
            paramCount = 0
            returnType = "androidx.preference.PreferenceScreen"
        }
    }.single()
}

val PreferenceGroup_findPreference = findMethodDirect {
    findMethod {
        matcher {
            declaredClass = "androidx.preference.PreferenceGroup"
            modifiers = Modifier.PUBLIC or Modifier.FINAL
            returnType = "androidx.preference.Preference"
            paramTypes("java.lang.CharSequence")
        }
    }.single()
}

val Preference_setTitle = findMethodDirect {
    findMethod {
        matcher {
            declaredClass = "androidx.preference.Preference"
            modifiers = Modifier.PUBLIC or Modifier.FINAL
            returnType = "void"
            paramTypes("java.lang.CharSequence")
        }
    }.single()
}