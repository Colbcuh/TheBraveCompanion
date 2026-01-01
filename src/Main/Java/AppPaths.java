package Main.Java;

import java.io.File;

public final class AppPaths {

    private static final String APP_FOLDER = "TBC";

    private AppPaths() {}

    /**
     * Base folder: %APPDATA%\TBC
     * Example: C:\Users\<You>\AppData\Roaming\TBC
     */
    public static File baseDir() {
        String appData = System.getenv("APPDATA");
        if (appData == null || appData.trim().isEmpty()) {
            // Fallback (rare): user.home
            return new File(System.getProperty("user.home"), APP_FOLDER);
        }
        return new File(appData, APP_FOLDER);
    }

    /**
     * %APPDATA%\TBC\logs
     */
    public static File logsDir() {
        return new File(baseDir(), "logs");
    }

    /**
     * %APPDATA%\TBC\cache
     */
    public static File cacheDir() {
        return new File(baseDir(), "cache");
    }

    /**
     * %APPDATA%\TBC\app
     * (Good place for version files, settings.json, etc.)
     */
    public static File appDir() {
        return new File(baseDir(), "app");
    }
}
