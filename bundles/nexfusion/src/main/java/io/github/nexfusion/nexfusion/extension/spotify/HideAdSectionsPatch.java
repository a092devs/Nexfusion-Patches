package io.github.nexfusion.nexfusion.extension.spotify;

import java.util.Iterator;
import java.util.List;

import io.github.nexfusion.nexfusion.extension.shared.Logger;
import io.github.nexfusion.nexfusion.extension.shared.Reflection;

/**
 * Removes brand ad sections from the home and browse (search) pages.
 */
@SuppressWarnings("unused")
public final class HideAdSectionsPatch {
    /**
     * {@code Section.VIDEO_BRAND_AD_FIELD_NUMBER}, {@code Section.IMAGE_BRAND_AD_FIELD_NUMBER},
     * {@code Section.PROMOTION_V1_FIELD_NUMBER}, {@code Section.PROMOTION_V3_FIELD_NUMBER},
     * {@code Section.BANNER_FIELD_NUMBER}, {@code Section.BANNER_WITH_ANIMATIONS_FIELD_NUMBER},
     * {@code Section.FULL_BLEED_BANNER_FIELD_NUMBER}, {@code Section.PREVIEW_PROMOTION_CAROUSEL_FIELD_NUMBER},
     * {@code Section.CAMPAIGN_LINK_CAROUSEL_FIELD_NUMBER}, {@code Section.AUDIO_PROMO_FIELD_NUMBER},
     * {@code Section.VIDEO_PROMO_FIELD_NUMBER} of the home page protobuf.
     */
    private static final int[] HOME_AD_FEATURE_TYPES = {4, 13, 18, 19, 20, 21, 23, 28, 32, 33, 40};

    /**
     * {@code Section.BRAND_ADS_FIELD_NUMBER} of the browse page protobuf.
     */
    private static final int[] BROWSE_AD_SECTION_TYPES = {6};

    private HideAdSectionsPatch() {
    }

    private static boolean contains(int[] values, int value) {
        for (int v : values) {
            if (v == value) return true;
        }
        return false;
    }

    private static boolean isHomeAd(Object section) throws ReflectiveOperationException {
        if (contains(HOME_AD_FEATURE_TYPES, Reflection.getIntField(section, "featureTypeCase_"))) return true;

        Object featureType = Reflection.getField(section, "featureType_");
        if (featureType == null) return false;
        try {
            if (Reflection.getField(featureType, "adMetadata_") != null) return true;
        } catch (NoSuchFieldException ignored) {
        }
        String name = featureType.getClass().getSimpleName().toLowerCase(java.util.Locale.ROOT);
        return name.contains("promo") || name.contains("banner") || name.contains("upsell") ||
                name.contains("ad") || name.contains("campaign");
    }

    /**
     * Injection point: return value of {@code HomeStructure.getSections()}.
     */
    public static void removeHomeSections(List<?> sections) {
        if (sections == null || sections.isEmpty()) return;
        try {
            // Only filter lists of home sections.
            Reflection.findField(sections.get(0).getClass(), "featureTypeCase_");
        } catch (Throwable ignored) {
            return;
        }

        try {
            Reflection.makeProtobufListMutable(sections);
            for (Iterator<?> it = sections.iterator(); it.hasNext(); ) {
                if (isHomeAd(it.next())) it.remove();
            }
        } catch (Throwable ex) {
            Logger.error("removeHomeSections failure", ex);
        }
    }

    /**
     * Injection point: return value of {@code BrowseStructure.getSections()}.
     */
    public static void removeBrowseSections(List<?> sections) {
        if (sections == null || sections.isEmpty()) return;
        try {
            Reflection.makeProtobufListMutable(sections);
            for (Iterator<?> it = sections.iterator(); it.hasNext(); ) {
                if (contains(BROWSE_AD_SECTION_TYPES, Reflection.getIntField(it.next(), "sectionTypeCase_"))) {
                    it.remove();
                }
            }
        } catch (Throwable ex) {
            Logger.error("removeBrowseSections failure", ex);
        }
    }
}
