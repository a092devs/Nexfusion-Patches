package io.github.a092devs.nexfusion.revanced.googlephotos

import io.github.a092devs.nexfusion.revanced.googlephotos.misc.backup.EnableDCIMFoldersBackupControl
import io.github.a092devs.nexfusion.revanced.googlephotos.misc.features.SpoofFeaturesPatch

val GooglePhotosPatches = arrayOf(
    SpoofFeaturesPatch,
    EnableDCIMFoldersBackupControl,
)