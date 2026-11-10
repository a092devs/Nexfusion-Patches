package io.github.nexfusion.nexfusion.extension.spotify;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;
import android.util.Log;

import java.security.MessageDigest;
import java.lang.reflect.Field;
import java.net.URI;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

/** Read-only diagnostics. Never log request bodies, tokens, or exception messages. */
@SuppressWarnings("unused")
public final class DiagnoseClientPatch {
    private static final String TAG = "SpotifyClientDiag";
    /**
     * The running app's package.
     * Null until resolved; never assumed, so a renamed app never logs another installed app's certificates.
     */
    private static volatile String ownPackage;
    private static volatile boolean processNameLookedUp;
    private static volatile Callable<String> processNameSource = DiagnoseClientPatch::currentProcessName;
    private static final AtomicInteger tokenResultsLogged = new AtomicInteger();
    private static final AtomicInteger responsesLogged = new AtomicInteger();
    private static final AtomicInteger homeFailuresLogged = new AtomicInteger();

    private DiagnoseClientPatch() {
    }

    public static void onStartup(Context context) {
        try {
            Log.i(TAG, "diagnostics=v5; behavior=unchanged");
            String packageName = context.getPackageName();
            rememberOwnPackage(packageName);
            PackageManager pm = context.getPackageManager();
            Log.i(TAG, "package=" + packageName);
            Log.i(TAG, "installer=" + pm.getInstallerPackageName(packageName));
            observe(pm.getPackageInfo(packageName, Build.VERSION.SDK_INT >= 28
                    ? PackageManager.GET_SIGNING_CERTIFICATES : PackageManager.GET_SIGNATURES));
        } catch (Throwable error) {
            safeLog("startup diagnostic failed; " + describeFailure(error));
        }
    }

    public static PackageInfo getPackageInfo(PackageManager pm, String name, int flags)
            throws PackageManager.NameNotFoundException {
        PackageInfo info = pm.getPackageInfo(name, flags);
        observe(info);
        return info;
    }

    public static PackageInfo getPackageInfo(PackageManager pm, String name, PackageManager.PackageInfoFlags flags)
            throws PackageManager.NameNotFoundException {
        PackageInfo info = pm.getPackageInfo(name, flags);
        observe(info);
        return info;
    }

    static void rememberOwnPackage(String packageName) {
        if (packageName != null && !packageName.isEmpty()) ownPackage = packageName;
    }

    static boolean isOwnPackage(String packageName) {
        String own = ownPackage;
        if (own == null && !processNameLookedUp) own = lookUpOwnPackage();
        return own != null && own.equals(packageName);
    }

    private static synchronized String lookUpOwnPackage() {
        if (ownPackage == null && !processNameLookedUp) {
            String processName = null;
            try {
                processName = processNameSource.call();
            } catch (Throwable ignored) {
            }
            rememberOwnPackage(packageFromProcessName(processName));
            processNameLookedUp = true;
        }
        return ownPackage;
    }

    static synchronized void resetOwnPackage(Callable<String> source) {
        ownPackage = null;
        processNameLookedUp = false;
        processNameSource = source;
    }

    static String packageFromProcessName(String processName) {
        if (processName == null) return null;
        int separator = processName.indexOf(':');
        String packageName = separator >= 0 ? processName.substring(0, separator) : processName;
        return packageName.isEmpty() ? null : packageName;
    }

    private static String currentProcessName() {
        try {
            if (Build.VERSION.SDK_INT >= 28) return android.app.Application.getProcessName();
            return (String) Class.forName("android.app.ActivityThread")
                    .getDeclaredMethod("currentProcessName").invoke(null);
        } catch (Throwable error) {
            safeLog("currentProcessName diagnostic unavailable; " + describeFailure(error));
            return null;
        }
    }

    public static void observe(PackageInfo info) {
        try {
            if (info == null || !isOwnPackage(info.packageName)) return;
            Signature[] signatures = null;
            if (Build.VERSION.SDK_INT >= 28 && info.signingInfo != null) {
                signatures = info.signingInfo.hasMultipleSigners()
                        ? info.signingInfo.getApkContentsSigners()
                        : info.signingInfo.getSigningCertificateHistory();
            }
            @SuppressWarnings("deprecation")
            Signature[] legacySignatures = info.signatures;
            if (signatures == null) signatures = legacySignatures;
            if (signatures == null || signatures.length == 0) return;

            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            StringBuilder hex = new StringBuilder();
            for (Signature signature : signatures) {
                byte[] digest = sha256.digest(signature.toByteArray());
                for (byte b : digest) hex.append(String.format("%02x", b));
                hex.append(',');
            }
            safeLog("cert=" + hex.substring(0, hex.length() - 1));
        } catch (Throwable error) {
            safeLog("signature diagnostic unavailable; " + describeFailure(error));
        }
    }

    public static void onVerificationStarted() {
        safeLog("integrity request started");
    }

    public static void onHomeHttpStatus(int status) {
        safeLog("home http status=" + status);
    }

    public static void onHomeFailure(Throwable error) {
        if (homeFailuresLogged.getAndIncrement() < 10) {
            safeLog(describeHomeFailure(error));
        }
    }

    static String describeHomeFailure(Throwable error) {
        if (error == null) return "home failed; exception=null";
        StringBuilder result = new StringBuilder("home failed; ");
        IdentityHashMap<Throwable, Boolean> seen = new IdentityHashMap<>();
        boolean first = true;
        while (error != null && seen.put(error, Boolean.TRUE) == null) {
            if (!first) result.append("; caused_by=");
            first = false;
            result.append(describeFailure(error));
            try {
                StackTraceElement[] frames = error.getStackTrace();
                for (int i = 0; i < Math.min(frames.length, 6); i++) {
                    StackTraceElement frame = frames[i];
                    String owner = frame.getClassName();
                    String method = frame.getMethodName();
                    if (owner.length() <= 200 && method.length() <= 120 &&
                            owner.matches("[A-Za-z0-9_.$]+") && method.matches("[A-Za-z0-9_$<>-]+")) {
                        result.append("; at=").append(owner).append('#').append(method);
                    }
                }
                error = error.getCause();
            } catch (Throwable ignored) {
                break;
            }
        }
        return result.toString();
    }

    public static void onVerificationResponse(String response) {
        safeLog("integrity response received; nonempty=" + (response != null && !response.isEmpty()));
    }

    public static void onVerificationFailure(Throwable error) {
        safeLog("integrity request failed; " + describeFailure(error));
    }

    public static void onClientTokenResult(Object result) {
        if (tokenResultsLogged.getAndIncrement() < 20) safeLog(describeClientTokenResult(result));
    }

    static String describeClientTokenResult(Object result) {
        if (result == null) return "client token result=null";
        try {
            int resultCase = (Integer) field(result, "resultCase_");
            if (resultCase == 1) return "client token acquired";
            if (resultCase == 2) {
                Object failure = field(result, "result_");
                int code = (Integer) field(failure, "code_");
                return "client token acquisition failed; code=" + code;
            }
            return "client token result case=" + resultCase;
        } catch (Throwable error) {
            return "client token diagnostic unavailable; " + describeFailure(error);
        }
    }

    private static Object field(Object instance, String name) throws ReflectiveOperationException {
        Field field = instance.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(instance);
    }

    public static void onHttpResponse(int status, String url) {
        if (responsesLogged.getAndIncrement() < 150) safeLog(describeHttpResponse(status, url));
    }

    static String describeHttpResponse(int status, String url) {
        String host = "other";
        String service = "other";
        try {
            URI uri = URI.create(url);
            String hostname = uri.getHost();
            if ("clienttoken.spotify.com".equals(hostname)) host = "clienttoken";
            else if ("api.spotify.com".equals(hostname)) host = "api";
            else if ("login5.spotify.com".equals(hostname)) host = "login5";
            else if ("spclient.wg.spotify.com".equals(hostname) ||
                    (hostname != null && hostname.endsWith("-spclient.spotify.com"))) host = "spclient";
            String[] path = uri.getPath().split("/");
            if (path.length > 1 && Arrays.asList("home", "homeview-service", "metadata",
                    "extended-metadata", "playlist", "playlist-read", "playlist4", "storage-resolve",
                    "track-playback", "connect-state", "clienttoken", "v1", "v2").contains(path[1])) {
                service = path[1];
            }
        } catch (Throwable ignored) {
        }
        return "http response=" + status + "; host=" + host + "; service=" + service;
    }

    static String describeFailure(Throwable error) {
        if (error == null) return "exception=null";
        String description = "exception=" + error.getClass().getName();
        try {
            Object code = error.getClass().getMethod("code").invoke(error);
            if (code instanceof Integer && (Integer) code >= 100 && (Integer) code <= 599) {
                description += "; http=" + code;
            }
        } catch (Throwable ignored) {
        }
        return description;
    }

    private static void safeLog(String message) {
        try {
            Log.i(TAG, message);
        } catch (Throwable ignored) {
        }
    }
}
