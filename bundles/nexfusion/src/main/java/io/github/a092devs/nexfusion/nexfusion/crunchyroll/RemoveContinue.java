package io.github.a092devs.nexfusion.nexfusion.crunchyroll;

import android.content.Context;
import android.content.SharedPreferences;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public final class RemoveContinue {
    private static final String PANEL_CLASS = "com.ellation.crunchyroll.model.Panel";
    private static final String PREFS_NAME = "nexfusion_remove_continue";
    private static final String HIDDEN_PLAYHEAD_PREFIX = "hidden_playhead:";
    private static final Object REMOVE_CONTAINER = new Object();
    private static final ThreadLocal<Object> currentData = new ThreadLocal<>();

    private static volatile Context appContext;
    private static volatile SharedPreferences preferences;

    private RemoveContinue() {
    }

    public static void initialize(Context context) {
        if (context == null) {
            return;
        }

        appContext = context;
        preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static void captureData(Object data) {
        currentData.set(data);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static List addRemoveContinue(List original, Object provider) {
        Object data = currentData.get();
        currentData.remove();

        if (original == null || data == null) {
            return original;
        }

        try {
            Class<?> menuItemClass = original.isEmpty()
                    ? null
                    : original.get(0).getClass();

            Object menuLabel = getRemoveMenuLabel(original, menuItemClass);
            if (menuLabel == null || menuItemClass == null) {
                return original;
            }

            Constructor<?> constructor = findMenuItemConstructor(menuItemClass, menuLabel);
            if (constructor == null) {
                return original;
            }

            Class<?> callbackType = constructor.getParameterTypes()[1];
            Object callback = Proxy.newProxyInstance(
                    callbackType.getClassLoader(),
                    new Class<?>[]{callbackType},
                    new MenuClickHandler(data, provider)
            );

            Object removeItem = constructor.newInstance(menuLabel, callback);
            ArrayList result = new ArrayList(original.size() + 1);
            result.addAll(original);
            result.add(removeItem);
            return result;
        } catch (Throwable ignored) {
            return original;
        }
    }

    public static void filterFeed(Object feed) {
        SharedPreferences prefs = preferences;
        if (prefs == null || feed == null) {
            return;
        }

        try {
            List<?> items = findItems(feed);
            if (items == null || items.isEmpty()) {
                return;
            }

            Iterator<?> iterator = items.iterator();
            while (iterator.hasNext()) {
                Object item = iterator.next();

                try {
                    String contentId = getContentId(item);
                    if (contentId == null) {
                        continue;
                    }

                    String key = HIDDEN_PLAYHEAD_PREFIX + contentId;
                    if (!prefs.contains(key)) {
                        continue;
                    }

                    long hiddenPlayhead = prefs.getLong(key, Long.MIN_VALUE);
                    Long currentPlayhead = getPlayheadSec(item);

                    if (currentPlayhead != null
                            && currentPlayhead.longValue() != hiddenPlayhead) {
                        prefs.edit().remove(key).apply();
                        continue;
                    }

                    iterator.remove();
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private static final class MenuClickHandler implements InvocationHandler {
        private final Object data;
        private final Object provider;

        private MenuClickHandler(Object data, Object provider) {
            this.data = data;
            this.provider = provider;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            if (method.getDeclaringClass() == Object.class) {
                return handleObjectMethod(proxy, method, args);
            }

            if ("invoke".equals(method.getName())) {
                hide(data, provider);
                return null;
            }

            return defaultValue(method.getReturnType());
        }
    }

    private static void hide(Object data, Object provider) {
        SharedPreferences prefs = preferences;
        if (prefs == null || data == null) {
            return;
        }

        try {
            String contentId = getContentId(data);
            Long playheadSec = getPlayheadSec(data);

            if (contentId != null && playheadSec != null) {
                prefs.edit()
                        .putLong(HIDDEN_PLAYHEAD_PREFIX + contentId, playheadSec)
                        .apply();
            }

            removeFeedItem(data, provider);
        } catch (Throwable ignored) {
        }
    }

    private static void removeFeedItem(Object data, Object provider) {
        if (provider == null) {
            return;
        }

        try {
            for (Field field : provider.getClass().getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) {
                    continue;
                }

                field.setAccessible(true);
                Object value = field.get(provider);

                if (value instanceof List<?>) {
                    List<?> list = (List<?>) value;
                    if (list.remove(data)) {
                        return;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Object> findItems(Object feed) {
        if (feed == null) {
            return null;
        }

        if (feed instanceof List<?>) {
            return (List<Object>) feed;
        }

        Class<?> current = feed.getClass();
        while (current != null) {
            for (Field field : current.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) {
                    continue;
                }

                if (List.class.isAssignableFrom(field.getType())) {
                    try {
                        field.setAccessible(true);
                        Object value = field.get(feed);
                        if (value instanceof List<?>) {
                            return (List<Object>) value;
                        }
                    } catch (Throwable ignored) {
                    }
                }
            }
            current = current.getSuperclass();
        }

        return null;
    }

    private static String getContentId(Object item) {
        Object panel = findPanel(item, 3, new IdentityHashMap<>());
        if (panel == null) {
            return null;
        }

        try {
            Method method = panel.getClass().getMethod("getId");
            method.setAccessible(true);
            Object id = method.invoke(panel);
            return id != null ? id.toString() : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Long getPlayheadSec(Object item) {
        Object panel = findPanel(item, 3, new IdentityHashMap<>());
        if (panel == null) {
            return null;
        }

        try {
            Method method = panel.getClass().getMethod("getPlayheadSec");
            method.setAccessible(true);
            Object val = method.invoke(panel);
            if (val instanceof Number) {
                return ((Number) val).longValue();
            }
        } catch (Throwable ignored) {
        }

        return null;
    }

    private static Object findPanel(Object value, int depth, Map<Object, Boolean> visited) {
        if (value == null || depth <= 0 || visited.put(value, Boolean.TRUE) != null) {
            return null;
        }

        if (PANEL_CLASS.equals(value.getClass().getName())) {
            return value;
        }

        Class<?> current = value.getClass();
        while (current != null) {
            for (Field field : current.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || field.getType().isPrimitive()) {
                    continue;
                }

                field.setAccessible(true);
                try {
                    Object nested = field.get(value);
                    if (nested == null) {
                        continue;
                    }

                    if (PANEL_CLASS.equals(nested.getClass().getName())) {
                        return nested;
                    }

                    String className = nested.getClass().getName();
                    if (className.startsWith("java.")
                            || className.startsWith("kotlin.")
                            || className.startsWith("android.")) {
                        continue;
                    }

                    Object panel = findPanel(nested, depth - 1, visited);
                    if (panel != null) {
                        return panel;
                    }
                } catch (Throwable ignored) {
                }
            }

            current = current.getSuperclass();
        }

        return null;
    }

    private static Object getRemoveMenuLabel(List<?> original, Class<?> menuItemClass) throws Exception {
        Context context = appContext;
        if (context == null) {
            return null;
        }

        Class<?> labelClass = null;
        if (menuItemClass != null) {
            for (Constructor<?> constructor : menuItemClass.getDeclaredConstructors()) {
                Class<?>[] params = constructor.getParameterTypes();
                if (params.length == 2 && params[1].isInterface()) {
                    labelClass = params[0];
                    break;
                }
            }
        }

        if (labelClass == null) {
            ClassLoader loader = original.isEmpty()
                    ? RemoveContinue.class.getClassLoader()
                    : original.get(0).getClass().getClassLoader();
            try {
                labelClass = Class.forName("cd0.i", false, loader);
            } catch (Throwable ignored) {
                return null;
            }
        }

        Object template = null;
        for (Field field : labelClass.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers())
                    || !labelClass.isAssignableFrom(field.getType())) {
                continue;
            }

            field.setAccessible(true);
            template = field.get(null);
            if (template != null) {
                break;
            }
        }

        if (template == null) {
            return null;
        }

        int removeText = context.getResources().getIdentifier(
                "remove",
                "string",
                context.getPackageName()
        );

        if (removeText == 0) {
            return template;
        }

        Class<?> baseClass = labelClass.getSuperclass();
        if (baseClass == null || baseClass == Object.class) {
            baseClass = labelClass;
        }

        Integer color = null;
        Integer trailing = null;
        boolean flag = false;

        for (Field field : baseClass.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())) {
                continue;
            }

            field.setAccessible(true);

            if (field.getType() == Integer.class) {
                Integer value = (Integer) field.get(template);
                if (value != null && color == null) {
                    color = value;
                } else {
                    trailing = value;
                }
            } else if (field.getType() == Boolean.TYPE) {
                flag = field.getBoolean(template);
            }
        }

        for (Constructor<?> constructor : baseClass.getDeclaredConstructors()) {
            Class<?>[] params = constructor.getParameterTypes();
            if (params.length == 4
                    && params[0] == Integer.TYPE
                    && params[1] == Integer.class
                    && params[2] == Boolean.TYPE
                    && params[3] == Integer.class) {
                constructor.setAccessible(true);
                return constructor.newInstance(removeText, color, flag, trailing);
            }
        }

        return template;
    }

    private static Constructor<?> findMenuItemConstructor(
            Class<?> menuItemClass,
            Object menuLabel
    ) {
        for (Constructor<?> constructor : menuItemClass.getDeclaredConstructors()) {
            Class<?>[] parameters = constructor.getParameterTypes();

            if (parameters.length == 2
                    && parameters[0].isInstance(menuLabel)
                    && parameters[1].isInterface()) {
                constructor.setAccessible(true);
                return constructor;
            }
        }

        return null;
    }

    private static Object handleObjectMethod(Object proxy, Method method, Object[] args) {
        switch (method.getName()) {
            case "toString":
                return "NexFusionRemoveContinueCallback";
            case "hashCode":
                return System.identityHashCode(proxy);
            case "equals":
                return args != null && args.length == 1 && proxy == args[0];
            default:
                return null;
        }
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }

        if (type == Boolean.TYPE) {
            return false;
        }
        if (type == Character.TYPE) {
            return '\0';
        }
        if (type == Byte.TYPE) {
            return (byte) 0;
        }
        if (type == Short.TYPE) {
            return (short) 0;
        }
        if (type == Integer.TYPE) {
            return 0;
        }
        if (type == Long.TYPE) {
            return 0L;
        }
        if (type == Float.TYPE) {
            return 0f;
        }
        if (type == Double.TYPE) {
            return 0d;
        }

        return null;
    }
}

