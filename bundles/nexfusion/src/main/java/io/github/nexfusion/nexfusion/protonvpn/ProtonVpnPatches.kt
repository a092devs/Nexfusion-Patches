package io.github.nexfusion.nexfusion.protonvpn

import io.github.nexfusion.compat.XC_MethodHook
import io.github.nexfusion.compat.XposedBridge
import io.github.nexfusion.compat.XposedHelpers
import io.github.nexfusion.PatcherLog
import io.github.nexfusion.patch

// Ported from hxreborn/morphe-patches (GPL-3.0), hoo-dles/morphe-patches (GPL-3.0),
// and Paresh-Maheshwari/paresh-patches (GPL-3.0):
// - Remove server change delay and cooldowns on free plans (AppConfigResponse)
// - Unlock advanced client settings: NetShield ad/malware blocking, split tunneling,
//   custom DNS, and LAN connections (LocalUserSettings & VpnUser)
// - Show free server locations: browse, search, and connect directly to free server locations
// - Unlock connection preferences: default protocol choices (WireGuard, OpenVPN, Stealth) and profiles
// - Hide upgrade promotions: onboarding dialogs, upsell banners, and plan upgrade cards
// - Disable telemetry, analytics, and observability data collection
// - Disable in-app update checks

val ProtonVpnDisableTelemetry = patch(
    name = "Disable telemetry",
    description = "Blocks telemetry, analytics, and observability data collection in Proton VPN.",
) {
    // Block Proton Core telemetry worker scheduling
    runCatching {
        val workerClass = classLoader.loadClass("me.proton.core.telemetry.data.worker.TelemetryWorkerManagerImpl")
        workerClass.declaredMethods.filter { it.name.startsWith("enqueueOrKeep") }.forEach { method ->
            XposedBridge.hookMethod(method, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    param.result = null
                }
            })
        }
    }

    // Block observability events sending
    runCatching {
        val obsClass = classLoader.loadClass("me.proton.core.observability.data.usecase.SendObservabilityEventsImpl")
        obsClass.declaredMethods.filter { it.name == "invoke" }.forEach { method ->
            XposedBridge.hookMethod(method, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    param.result = Unit
                }
            })
        }
    }

    // Block VPN telemetry event recording
    runCatching {
        val vpnTelemetryClass = classLoader.loadClass("com.protonvpn.android.telemetry.VpnTelemetry")
        vpnTelemetryClass.declaredMethods.filter { it.name == "addEvent" }.forEach { method ->
            XposedBridge.hookMethod(method, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    param.result = Unit
                }
            })
        }
    }

    // Block telemetry background workers and heartbeat in 5.20+
    runCatching {
        val workers = listOf(
            "com.protonvpn.android.telemetry.TelemetryUploadWorker",
            "com.protonvpn.android.telemetry.SettingsHeartbeatWorker",
            "me.proton.core.telemetry.data.worker.TelemetryWorker",
        )
        for (workerName in workers) {
            val workerCls = runCatching { classLoader.loadClass(workerName) }.getOrNull() ?: continue
            workerCls.declaredMethods.filter { it.name == "doWork" }.forEach { method ->
                XposedBridge.hookMethod(method, object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        // Return Result.success() to prevent worker execution and rescheduling
                        val successMethod = runCatching {
                            classLoader.loadClass("androidx.work.ListenableWorker\$Result").getMethod("success")
                        }.getOrNull()
                        if (successMethod != null) {
                            param.result = successMethod.invoke(null)
                        }
                    }
                })
            }
        }
    }
}

val ProtonVpnServerChangeDelay = patch(
    name = "Remove server change delay",
    description = "Removes the wait period and cooldown between server changes on free plans.",
) {
    runCatching {
        val configClass = classLoader.loadClass("com.protonvpn.android.appconfig.AppConfigResponse")
        configClass.declaredMethods.filter {
            it.name.contains("ChangeServer", ignoreCase = true) ||
            it.name.contains("ServerDelay", ignoreCase = true)
        }.forEach { method ->
            if (method.returnType == java.lang.Integer.TYPE || method.returnType == java.lang.Long.TYPE) {
                XposedBridge.hookMethod(method, object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        param.result = 0
                    }
                })
            } else if (method.returnType == java.lang.Boolean.TYPE) {
                if (method.name.contains("Promo", ignoreCase = true) || method.name.contains("Upsell", ignoreCase = true)) {
                    XposedBridge.hookMethod(method, object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            param.result = false
                        }
                    })
                } else if (method.name.contains("Available", ignoreCase = true) || method.name.contains("Enabled", ignoreCase = true)) {
                    XposedBridge.hookMethod(method, object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            param.result = true
                        }
                    })
                }
            }
        }
    }
}

val ProtonVpnShowFreeServerLocations = patch(
    name = "Show free server locations",
    description = "Allows users on free tier plans to browse, search, and connect directly to free server locations/countries instead of random assignment.",
) {
    // Enable direct country selection and server browsing
    runCatching {
        val configClass = classLoader.loadClass("com.protonvpn.android.appconfig.AppConfigResponse")
        configClass.declaredMethods.filter {
            it.name.contains("ChangeServer", ignoreCase = true) ||
            it.name.contains("ServerSelect", ignoreCase = true) ||
            it.name.contains("FreeUser", ignoreCase = true)
        }.forEach { method ->
            if (method.returnType == java.lang.Boolean.TYPE) {
                if (method.name.contains("Promo", ignoreCase = true) || method.name.contains("Upsell", ignoreCase = true)) {
                    XposedBridge.hookMethod(method, object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            param.result = false
                        }
                    })
                } else {
                    XposedBridge.hookMethod(method, object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            param.result = true
                        }
                    })
                }
            }
        }
    }

    // Unblock free location selection across country item view states
    runCatching {
        val countryItemClasses = listOf(
            "com.protonvpn.android.servers.CountryItemViewState",
            "com.protonvpn.android.servers.CountryItem",
            "com.protonvpn.android.ui.home.countries.CountryItem",
        )
        for (className in countryItemClasses) {
            val clazz = runCatching { classLoader.loadClass(className) }.getOrNull() ?: continue
            clazz.declaredMethods.filter {
                it.name == "isLocked" || it.name == "getIsLocked" || it.name == "isPlusOnly"
            }.forEach { method ->
                XposedBridge.hookMethod(method, object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        param.result = false
                    }
                })
            }
        }
    }
}

val ProtonVpnUnlockConnectionPreferences = patch(
    name = "Unlock connection preferences",
    description = "Unlocks default connection protocol (WireGuard, OpenVPN, Stealth) and profile settings on free plans.",
) {
    runCatching {
        val settingsClasses = listOf(
            "com.protonvpn.android.settings.ConnectionSettingsViewState",
            "com.protonvpn.android.settings.ProtocolSettingsViewState",
        )
        for (className in settingsClasses) {
            val clazz = runCatching { classLoader.loadClass(className) }.getOrNull() ?: continue
            clazz.declaredMethods.filter {
                it.name.contains("Protocol", ignoreCase = true) &&
                (it.name.contains("Enabled", ignoreCase = true) || it.name.contains("Available", ignoreCase = true))
            }.forEach { method ->
                if (method.returnType == java.lang.Boolean.TYPE) {
                    XposedBridge.hookMethod(method, object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            param.result = true
                        }
                    })
                }
            }
        }
    }
}

val ProtonVpnUnlockSettings = patch(
    name = "Unlock advanced settings & NetShield",
    description = "Unlocks NetShield ad/tracker blocking, split tunneling, custom DNS, and LAN connections.",
) {
    // NetShield availability -> AVAILABLE
    runCatching {
        val netShieldEnumClass = classLoader.loadClass("com.protonvpn.android.netshield.NetShieldAvailability")
        val availableConstant = netShieldEnumClass.enumConstants?.firstOrNull { (it as Enum<*>).name == "AVAILABLE" }
        if (availableConstant != null) {
            val netShieldManagerClass = classLoader.loadClass("com.protonvpn.android.netshield.NetShieldManager")
            netShieldManagerClass.declaredMethods.filter { it.returnType == netShieldEnumClass }.forEach { method ->
                XposedBridge.hookMethod(method, object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        param.result = availableConstant
                    }
                })
            }
        }
    }

    // Hook VpnUser tier & free status to unlock client-side settings
    runCatching {
        val userClass = classLoader.loadClass("com.protonvpn.android.auth.data.VpnUser")
        XposedHelpers.findAndHookMethod(userClass, "getUserTier", object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                param.result = 2
            }
        })
        XposedHelpers.findAndHookMethod(userClass, "isUserPlusOrAbove", object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                param.result = true
            }
        })
        XposedHelpers.findAndHookMethod(userClass, "isFreeUser", object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                param.result = false
            }
        })
        XposedHelpers.findAndHookMethod(userClass, "getUserTierName", object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                param.result = "vpn2022"
            }
        })
        runCatching {
            XposedHelpers.findAndHookMethod(userClass, "getMaxTier", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    param.result = java.lang.Integer.valueOf(2)
                }
            })
        }
    }
}

val ProtonVpnHidePromotions = patch(
    name = "Hide upgrade promotions",
    description = "Hides upgrade dialogs, onboarding prompts, and account upgrade banners.",
) {
    // Skip upgrade onboarding dialog after login
    runCatching {
        val launcherClass = classLoader.loadClass("com.protonvpn.android.ui.planupgrade.UpgradeDialogLauncherVM")
        launcherClass.declaredMethods.filter { it.name.startsWith("launch") }.forEach { method ->
            XposedBridge.hookMethod(method, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    param.result = null
                }
            })
        }
    }

    // Suppress upgrade banners in AccountSettingsViewState
    runCatching {
        val accountStateClass = classLoader.loadClass("com.protonvpn.android.settings.AccountSettingsViewState")
        accountStateClass.declaredMethods.filter { it.name == "getUpgradeToPlusBanner" && it.returnType == java.lang.Boolean.TYPE }.forEach { method ->
            XposedBridge.hookMethod(method, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    param.result = false
                }
            })
        }
    }
}

val ProtonVpnPremiumUi = patch(
    name = "Plus UI badge",
    description = "Displays Plus plan UI indicators.",
) {
    // Ensure Plus tier presentation in client headers
    runCatching {
        val userClass = classLoader.loadClass("com.protonvpn.android.auth.data.VpnUser")
        XposedHelpers.findAndHookMethod(userClass, "getUserTierName", object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                param.result = "vpn2022"
            }
        })
    }
}

val ProtonVpnDisableUpdateChecks = patch(
    name = "Disable in-app update checks",
    description = "Suppresses in-app update prompts and version checks.",
    use = false,
) {
    // 1. Hook Play Core AppUpdateManager
    runCatching {
        val appUpdateManagerClass = classLoader.loadClass("com.google.android.play.core.appupdate.AppUpdateManager")
        XposedBridge.hookAllMethods(appUpdateManagerClass, "getAppUpdateInfo", object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val tasksClass = runCatching { classLoader.loadClass("com.google.android.gms.tasks.Tasks") }.getOrNull()
                if (tasksClass != null) {
                    val forResult = tasksClass.getMethod("forResult", Any::class.java)
                    param.result = forResult.invoke(null, null)
                }
            }
        })
    }

    // 2. Suppress Proton update usecases
    runCatching {
        val updateClasses = listOf(
            "me.proton.core.update.domain.usecase.CheckAppUpdate",
            "com.protonvpn.android.update.domain.usecase.CheckAppUpdate",
            "com.protonvpn.android.update.AppUpdateManager",
        )
        for (className in updateClasses) {
            val clazz = runCatching { classLoader.loadClass(className) }.getOrNull() ?: continue
            clazz.declaredMethods.filter { it.name == "invoke" || it.name.startsWith("check") }.forEach { method ->
                XposedBridge.hookMethod(method, object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        param.result = null
                    }
                })
            }
        }
    }
}

val ProtonVpnPatches = arrayOf(
    ProtonVpnDisableTelemetry,
    ProtonVpnServerChangeDelay,
    ProtonVpnUnlockSettings,
    ProtonVpnShowFreeServerLocations,
    ProtonVpnUnlockConnectionPreferences,
    ProtonVpnHidePromotions,
    ProtonVpnPremiumUi,
    ProtonVpnDisableUpdateChecks,
)

