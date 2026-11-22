package io.github.a092devs.nexfusion;

/**
 * Build information read by the NexFusion fork of the Morphe extensions.
 * <p>
 * All fields are compile-time constants, so they are inlined and this class is never loaded.
 */
public final class BuildConfig {
    public static final boolean DEBUG = io.github.a092devs.nexfusion.patches.BuildConfig.DEBUG;
    public static final String APPLICATION_ID = io.github.a092devs.nexfusion.patches.BuildConfig.APPLICATION_ID;
    public static final String VERSION_NAME = io.github.a092devs.nexfusion.patches.BuildConfig.VERSION_NAME;
    public static final String PATCH_VERSION = io.github.a092devs.nexfusion.patches.BuildConfig.PATCH_VERSION;

    private BuildConfig() {
    }
}

