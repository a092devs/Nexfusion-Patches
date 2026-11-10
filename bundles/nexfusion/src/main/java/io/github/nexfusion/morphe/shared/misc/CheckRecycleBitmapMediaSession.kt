package io.github.nexfusion.morphe.shared.misc

import android.media.MediaMetadata
import android.media.session.MediaSession
import android.os.Build
import io.github.nexfusion.compat.XposedBridge
import io.github.nexfusion.callStaticMethod
import io.github.nexfusion.patch
import io.github.nexfusion.hookMethod
import io.github.nexfusion.morphe.shared.misc.fix.bitmap.fixRecycledBitmapPatch
import org.luckypray.dexkit.wrap.DexMethod
import java.lang.reflect.Member

val CheckRecycleBitmapMediaSession = patch(name = "<CheckRecycleBitmapMediaSession>") {

    dependsOn(fixRecycledBitmapPatch)

    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return@patch

    if (runCatching { DexMethod("Landroid/media/MediaMetadata\$Builder;->calculateSampleSize(IIII)I").toMember() }.isSuccess) {
        XposedBridge.log("Applying MediaMetadata recycle Bitmap workaround (https://github.com/a092devs/ReVancedXposed/issues/29#issuecomment-3084170279)")

        try {
            MediaMetadata.Builder::class.java.getMethod("build").hookMethod {
                before {
                    (it.thisObject as MediaMetadata.Builder).setBitmapDimensionLimit(Integer.MAX_VALUE)
                }
            }

            val setMetadata =
                MediaSession::class.java.getMethod("setMetadata", MediaMetadata::class.java)
            XposedBridge::class.java.callStaticMethod("deoptimizeMethod", setMetadata as Member)
        } catch (err: Throwable) {
            XposedBridge.log(err)
        }
    }
}

