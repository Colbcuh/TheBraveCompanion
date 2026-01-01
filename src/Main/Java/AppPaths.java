package Main.Java;

import java.io.File;

public final class AppPaths {

    private static final String APP_NAME = "TBC";

    private AppPaths() {}

    public static File baseDir() {
        String appData = System.getenv("APPDATA"); // Windows
        File base;
        if (appData != null && !appData.trim().isEmpty()) {
            base = new File(appData, APP_NAME);
        } else {
            // Fallback for non-Windows
            base = new File(System.getProperty("user.home"), "." + APP_NAME);
        }
        return base;
    }

    public static File appDir() {
        return new File(baseDir(), "app");
    }

    public static File cacheDir() {
        return new File(baseDir(), "cache");
    }

    public static File logsDir() {
        return new File(baseDir(), "logs");
    }

    public static void ensureDirs() {
        baseDir().mkdirs();
        appDir().mkdirs();
        cacheDir().mkdirs();
        logsDir().mkdirs();
    }
}
