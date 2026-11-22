package io.github.a092devs.nexfusion.nexfusion.instagram.download

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore

// Added for NexFusion: the download options of the a092devs contract — where a save lands, whether
// each poster gets their own folder, and whether file names carry the time of the save. They live
// in the app's own preferences, written by the settings dialog in DownloadPatches and read once at
// the start of a download, so a save costs one small read and nothing else.

/**
 * The options a save obeys.
 */
internal class DownloadSettings private constructor(private val prefs: SharedPreferences) {

    /** The directory saves start at: [DOWNLOADS] under Download, [GALLERY] under Pictures and Movies. */
    enum class Location(val label: String) {
        DOWNLOADS("Downloads"),
        GALLERY("Pictures and Movies"),
    }

    var location: Location
        get() = Location.values().firstOrNull { it.name == prefs.getString(KEY_LOCATION, null) } ?: Location.DOWNLOADS
        set(value) { prefs.edit().putString(KEY_LOCATION, value.name).apply() }

    /** Whether each poster's saves land in a folder named after them. */
    var perUsername: Boolean
        get() = prefs.getBoolean(KEY_PER_USERNAME, false)
        set(value) { prefs.edit().putBoolean(KEY_PER_USERNAME, value).apply() }

    /** Whether file names carry the time of the save. */
    var timestamp: Boolean
        get() = prefs.getBoolean(KEY_TIMESTAMP, false)
        set(value) { prefs.edit().putBoolean(KEY_TIMESTAMP, value).apply() }

    /** Custom base folder under the chosen location (defaults to [DEFAULT_FOLDER]). */
    var customFolder: String
        get() = prefs.getString(KEY_CUSTOM_FOLDER, null)?.trim()?.trim('/')?.takeIf { it.isNotEmpty() } ?: DEFAULT_FOLDER
        set(value) {
            var cleaned = value.trim().trim('/')
            if (cleaned.startsWith("Download/", ignoreCase = true)) {
                cleaned = cleaned.substring("Download/".length).trim('/')
            } else if (cleaned.startsWith("Downloads/", ignoreCase = true)) {
                cleaned = cleaned.substring("Downloads/".length).trim('/')
            }
            if (cleaned.isEmpty() || cleaned.equals(DEFAULT_FOLDER, ignoreCase = true)) {
                prefs.edit().remove(KEY_CUSTOM_FOLDER).apply()
            } else {
                prefs.edit().putString(KEY_CUSTOM_FOLDER, cleaned).apply()
            }
        }

    enum class Kind { VIDEO, IMAGE, AUDIO }

    /** The folder under the base directory, with [username]'s subfolder when asked for. */
    fun folder(username: String?): String {
        val base = customFolder
        val name = username?.takeIf { perUsername }
            ?.filter { it.isLetterOrDigit() || it == '.' || it == '_' || it == '-' }
            ?.takeIf { it.isNotBlank() }
        return if (name != null) "$base/$name" else base
    }

    /** The public directory a save of this kind starts at. */
    fun baseDirectory(kind: Kind): String = when {
        location == Location.DOWNLOADS -> Environment.DIRECTORY_DOWNLOADS
        kind == Kind.VIDEO -> Environment.DIRECTORY_MOVIES
        kind == Kind.AUDIO -> Environment.DIRECTORY_MUSIC
        else -> Environment.DIRECTORY_PICTURES
    }

    /** The MediaStore collection a save of this kind is inserted into. */
    fun collection(kind: Kind): Uri = when {
        location == Location.DOWNLOADS -> MediaStore.Downloads.EXTERNAL_CONTENT_URI
        kind == Kind.VIDEO -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        kind == Kind.AUDIO -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        else -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    }

    /** Where a save of [username] lands, for the toasts and the log. */
    fun destination(username: String?, kind: Kind) = "${baseDirectory(kind)}/${folder(username)}"

    fun baseDirectory(isVideo: Boolean): String = baseDirectory(if (isVideo) Kind.VIDEO else Kind.IMAGE)
    fun collection(isVideo: Boolean): Uri = collection(if (isVideo) Kind.VIDEO else Kind.IMAGE)
    fun destination(username: String?, isVideo: Boolean) = destination(username, if (isVideo) Kind.VIDEO else Kind.IMAGE)

    companion object {
        private const val PREFS = "nexfusion_downloads"
        private const val KEY_LOCATION = "save_location"
        private const val KEY_PER_USERNAME = "per_username_folders"
        private const val KEY_TIMESTAMP = "timestamp_names"
        private const val KEY_CUSTOM_FOLDER = "custom_download_folder"

        /** The default folder under whichever base the person chose. */
        const val DEFAULT_FOLDER = "NexFusion/Instagram"
        const val FOLDER = DEFAULT_FOLDER

        fun open(context: Context) = DownloadSettings(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE))
    }
}
