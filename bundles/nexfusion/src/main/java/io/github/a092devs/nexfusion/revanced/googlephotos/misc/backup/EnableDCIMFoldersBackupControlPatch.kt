package io.github.a092devs.nexfusion.revanced.googlephotos.misc.backup

import io.github.a092devs.nexfusion.compat.XC_MethodReplacement
import io.github.a092devs.nexfusion.patch

val EnableDCIMFoldersBackupControl = patch(
    name = "Enable DCIM folders backup control",
    description = "Disables always on backup for the Camera and other DCIM folders, allowing you to control backup " +
            "for each folder individually. This will make the app default to having no folders backed up.",
    use = true,
) {
    isDCIMFolderBackupControlMethod.hookMethod(XC_MethodReplacement.returnConstant(false))
}
