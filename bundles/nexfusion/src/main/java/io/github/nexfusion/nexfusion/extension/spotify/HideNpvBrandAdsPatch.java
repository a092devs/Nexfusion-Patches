package io.github.nexfusion.nexfusion.extension.spotify;

import java.lang.reflect.Field;
import java.util.Iterator;
import java.util.List;

import io.github.nexfusion.nexfusion.extension.shared.Logger;
import io.github.nexfusion.nexfusion.extension.shared.Reflection;

/**
 * Removes the embedded brand-ad card from the now playing view scroll feed, i.e. the ad banner
 * shown below the lyrics card.
 */
@SuppressWarnings("unused")
public final class HideNpvBrandAdsPatch {

    private HideNpvBrandAdsPatch() {
    }

    /**
     * Injection point: the sections list of the now playing view scroll feed.
     */
    public static void removeNpvBrandAdSections(List<?> sections) {
        if (sections == null || sections.isEmpty()) return;

        try {
            Class<?> sectionClass = sections.get(0).getClass();
            int imageBrandAd = getFieldNumber(sectionClass, "IMAGE_BRAND_AD_FIELD_NUMBER");

            // Protobuf lite lists are immutable once built and guard their mutability with a flag.
            Reflection.makeProtobufListMutable(sections);

            for (Iterator<?> iterator = sections.iterator(); iterator.hasNext(); ) {
                Object section = iterator.next();
                boolean isBrandAd = imageBrandAd > 0
                        ? Reflection.getIntField(section, "sectionTypeCase_") == imageBrandAd
                        : hasBrandAdValue(section);
                if (isBrandAd) iterator.remove();
            }
        } catch (Throwable ex) {
            Logger.error("removeNpvBrandAdSections failure", ex);
        }
    }

    /**
     * Resolves a protobuf {@code FIELD_NUMBER} constant from the real section class at runtime;
     * hardcoded numbers are not stable across app versions.
     */
    private static int getFieldNumber(Class<?> sectionClass, String fieldName) {
        try {
            Field field = Reflection.findField(sectionClass, fieldName);
            return (int) field.get(null);
        } catch (Throwable t) {
            Logger.info("Protobuf field number " + fieldName +
                    " not found, falling back to the oneof value field");
            return -1;
        }
    }

    /**
     * Fallback for builds whose {@code FIELD_NUMBER} constant was inlined by the shrinker: a
     * protobuf oneof stores the set member in a single {@code Object} field whose runtime class
     * names the member.
     */
    private static boolean hasBrandAdValue(Object section) {
        for (String name : new String[]{"sectionType_", "featureType_"}) {
            try {
                Object value = Reflection.getField(section, name);
                if (value != null &&
                        value.getClass().getSimpleName().toLowerCase().contains("brandad")) {
                    return true;
                }
            } catch (Throwable ignored) {
                // This section model does not use the field.
            }
        }
        return false;
    }
}
