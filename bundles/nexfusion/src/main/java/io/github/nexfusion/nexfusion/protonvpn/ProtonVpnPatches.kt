package io.github.nexfusion.nexfusion.protonvpn

import io.github.nexfusion.compat.XC_MethodHook
import io.github.nexfusion.compat.XposedBridge
import io.github.nexfusion.compat.XposedHelpers
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

    // Hook VpnUser tier & free status to unlock client-side settings (supports both unobfuscated & 5.21+ obfuscated hi.a)
    runCatching {
        val userCandidates = listOf("com.protonvpn.android.auth.data.VpnUser", "hi.a")
        for (className in userCandidates) {
            val userClass = runCatching { classLoader.loadClass(className) }.getOrNull() ?: continue
            userClass.declaredMethods.forEach { method ->
                val name = method.name
                val returnType = method.returnType
                if (returnType == java.lang.Boolean.TYPE || returnType == java.lang.Boolean::class.java) {
                    if (name == "isUserPlusOrAbove" || name == "c") {
                        XposedBridge.hookMethod(method, object : XC_MethodHook() {
                            override fun beforeHookedMethod(param: MethodHookParam) {
                                param.result = true
                            }
                        })
                    } else if (name == "isFreeUser" || name == "d") {
                        XposedBridge.hookMethod(method, object : XC_MethodHook() {
                            override fun beforeHookedMethod(param: MethodHookParam) {
                                param.result = false
                            }
                        })
                    }
                } else if (returnType == java.lang.Integer.TYPE || returnType == java.lang.Integer::class.java) {
                    if (name == "getUserTier" || name == "getMaxTier" || name == "a") {
                        XposedBridge.hookMethod(method, object : XC_MethodHook() {
                            override fun beforeHookedMethod(param: MethodHookParam) {
                                param.result = 2
                            }
                        })
                    }
                } else if (returnType == String::class.java) {
                    if (name == "getUserTierName" || name == "b") {
                        XposedBridge.hookMethod(method, object : XC_MethodHook() {
                            override fun beforeHookedMethod(param: MethodHookParam) {
                                param.result = "vpn2022"
                            }
                        })
                    }
                }
            }
        }
    }
}

val ProtonVpnHidePromotions = patch(
    name = "Hide upgrade promotions",
    description = "Hides upgrade dialogs, onboarding prompts, and account upgrade banners.",
) {
    // 1. Skip upgrade onboarding dialog after login
    runCatching {
        val launcherClasses = listOf(
            "com.protonvpn.android.ui.planupgrade.UpgradeDialogLauncherVM",
        )
        for (className in launcherClasses) {
            val launcherClass = runCatching { classLoader.loadClass(className) }.getOrNull() ?: continue
            launcherClass.declaredMethods.filter { it.name.startsWith("launch") }.forEach { method ->
                XposedBridge.hookMethod(method, object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        param.result = null
                    }
                })
            }
        }
    }

    // 2. Suppress upgrade banners in AccountSettingsViewState (unobfuscated & 5.21+ il.v1)
    runCatching {
        val accountStateClasses = listOf(
            "com.protonvpn.android.settings.AccountSettingsViewState",
            "il.v1",
        )
        for (className in accountStateClasses) {
            val accountStateClass = runCatching { classLoader.loadClass(className) }.getOrNull() ?: continue
            accountStateClass.declaredConstructors.forEach { constructor ->
                XposedBridge.hookMethod(constructor, object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        for (i in param.args.indices) {
                            if (param.args[i] is Boolean) {
                                param.args[i] = false
                            }
                        }
                    }
                    override fun afterHookedMethod(param: MethodHookParam) {
                        accountStateClass.declaredFields.filter { it.type == java.lang.Boolean.TYPE }.forEach { field ->
                            field.isAccessible = true
                            field.setBoolean(param.thisObject, false)
                        }
                    }
                })
            }
            accountStateClass.declaredMethods.filter { it.returnType == java.lang.Boolean.TYPE }.forEach { method ->
                if (method.name == "getUpgradeToPlusBanner" || method.name == "f" || method.name == "g") {
                    XposedBridge.hookMethod(method, object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            param.result = false
                        }
                    })
                }
            }
        }
    }

    // 3. Suppress promo notifications (popups, badges, padlock banners) from ApiNotificationsResponse
    runCatching {
        val notifClass = classLoader.loadClass("com.protonvpn.android.promooffers.data.ApiNotificationsResponse")
        notifClass.declaredMethods.filter {
            (it.name == "getNotifications" || it.name == "component1") &&
            java.util.List::class.java.isAssignableFrom(it.returnType)
        }.forEach { method ->
            XposedBridge.hookMethod(method, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    param.result = emptyList<Any>()
                }
            })
        }
    }

    // 4. Suppress upgrade banners from ServerGroupsMainScreenState (items list containing Banner)
    runCatching {
        val serverGroupClasses = listOf(
            "com.protonvpn.android.servers.ServerGroupsMainScreenState",
            "xk.u0",
        )
        for (className in serverGroupClasses) {
            val serverGroupClass = runCatching { classLoader.loadClass(className) }.getOrNull() ?: continue
            serverGroupClass.declaredConstructors.forEach { constructor ->
                XposedBridge.hookMethod(constructor, object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        for (i in param.args.indices) {
                            val arg = param.args[i]
                            if (arg is List<*>) {
                                val filtered = arg.filterNot { item ->
                                    item != null && (
                                        item.javaClass.name == "xk.f0" ||
                                        item.toString().contains("Banner(type=")
                                    )
                                }
                                param.args[i] = filtered
                            }
                        }
                    }
                })
            }
        }
    }

    // 5. Hide Discover VPN Plus carousel in Compose LazyColumn builder (el.f0)
    runCatching {
        val lazyColumnClass = classLoader.loadClass("el.f0")
        lazyColumnClass.declaredConstructors.forEach { constructor ->
            XposedBridge.hookMethod(constructor, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    for (i in param.args.indices) {
                        val arg = param.args[i]
                        if (arg != null && arg.javaClass.name == "el.v") {
                            param.args[i] = null
                        }
                    }
                }
                override fun afterHookedMethod(param: MethodHookParam) {
                    runCatching {
                        val fieldF = lazyColumnClass.getDeclaredField("F")
                        fieldF.isAccessible = true
                        fieldF.set(param.thisObject, null)
                    }
                }
            })
        }
    }

    // 6. Suppress in-app upgrade checks
    runCatching {
        val upgradeUseCase = classLoader.loadClass("com.protonvpn.android.ui.planupgrade.IsInAppUpgradeAllowedUseCase")
        upgradeUseCase.declaredMethods.filter {
            it.name == "invoke" && (it.returnType == java.lang.Boolean.TYPE || it.returnType == java.lang.Boolean::class.java)
        }.forEach { method ->
            XposedBridge.hookMethod(method, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    param.result = false
                }
            })
        }
    }

    // 7. Auto-dismiss upgrade and promo activities if opened
    runCatching {
        val promoActivities = listOf(
            "com.protonvpn.android.promooffers.ui.PromoOfferActivity",
            "com.protonvpn.android.promooffers.ui.PromoOfferIapActivity",
            "com.protonvpn.android.ui.planupgrade.comparison_table.UpgradeDialogActivityV2",
            "com.protonvpn.android.ui.planupgrade.UpgradeOnboardingDialogActivity",
        )
        for (activityName in promoActivities) {
            val actClass = runCatching { classLoader.loadClass(activityName) }.getOrNull() ?: continue
            XposedBridge.hookAllMethods(actClass, "onCreate", object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val activity = param.thisObject as? android.app.Activity
                    activity?.finish()
                }
            })
        }
    }
}

val ProtonVpnPremiumUi = patch(
    name = "Plus UI badge",
    description = "Displays Plus plan UI indicators.",
) {
    runCatching {
        val userCandidates = listOf("com.protonvpn.android.auth.data.VpnUser", "hi.a")
        for (className in userCandidates) {
            val userClass = runCatching { classLoader.loadClass(className) }.getOrNull() ?: continue
            userClass.declaredMethods.filter {
                it.returnType == String::class.java && (it.name == "getUserTierName" || it.name == "b")
            }.forEach { method ->
                XposedBridge.hookMethod(method, object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        param.result = "vpn2022"
                    }
                })
            }
        }
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

