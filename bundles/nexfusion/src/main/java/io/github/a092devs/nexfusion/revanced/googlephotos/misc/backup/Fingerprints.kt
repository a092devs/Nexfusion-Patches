package io.github.a092devs.nexfusion.revanced.googlephotos.misc.backup

import io.github.a092devs.nexfusion.morphe.Fingerprint

internal object isDCIMFolderBackupControlMethod : Fingerprint(
    strings = listOf(
        "/dcim",
        "/mars_files/"
    ),
    parameters = listOf("Ljava/lang/String;"),
    returnType = "Z"
)