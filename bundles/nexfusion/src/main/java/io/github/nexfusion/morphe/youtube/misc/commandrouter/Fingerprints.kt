/*
 * Copyright 2026 NexFusion contributors
 */

package io.github.nexfusion.morphe.youtube.misc.commandrouter

import io.github.nexfusion.morphe.AccessFlags
import io.github.nexfusion.morphe.Fingerprint
import io.github.nexfusion.morphe.Opcode
import io.github.nexfusion.morphe.methodCall

/**
 * Looks up the account scoped command router, which only the command router of an activity does.
 */
internal object CommandRouterFragmentFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC),
    strings = listOf("com.google.android.apps.youtube.app.endpoint.routers.AccountScopeCommandRouterFragment")
)

/**
 * The command router of an activity, created with it.
 */
internal object CommandRouterConstructorFingerprint : Fingerprint(
    classFingerprint = CommandRouterFragmentFingerprint,
    name = "<init>",
    parameters = listOf("Landroid/app/Activity;", "L", "Ljava/util/concurrent/Executor;"),
    filters = listOf(
        methodCall("Ljava/lang/Object;-><init>()V")
    )
)

/**
 * Runs one command with its context, by the command router itself if no other router handles the command.
 */
internal object CommandRouterExecuteCommandFingerprint : Fingerprint(
    classFingerprint = CommandRouterFragmentFingerprint,
    returnType = "V",
    parameters = listOf("L", "Ljava/util/Map;"),
    filters = listOf(
        methodCall(
            opcode = Opcode.INVOKE_VIRTUAL,
            definingClass = "this",
            parameters = listOf("L", "Ljava/util/Map;"),
            returnType = "V"
        )
    )
)
