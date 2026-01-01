package Main.Java;

import java.awt.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.text.SimpleDateFormat;
import java.util.Date;

public final class CrashLogger {

    private static final Object LOCK = new Object();
    private static volatile PrintWriter writer;

    private CrashLogger() {}

    /**
     * Installs exception handlers. Does NOT create any log file unless an error occurs.
     * Errors are appended to: %APPDATA%\TBC\logs\errors.log
     */
    public static void init(String appName) {
        synchronized (LOCK) {
            if (writer != null) return; // already initialized + error file opened once

            try {
                // Ensure base folders exist (folder can exist even with no log file)
                Files.createDirectories(AppPaths.logsDir().toPath());

                Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
                    log("Uncaught exception in thread: " + t.getName(), e);
                    flush();
                });

                installEventQueueTrap();

                // NOTE: We intentionally do NOT redirect System.err,
                // because that can capture non-error noise and clutter the file.

            } catch (Exception e) {
                // Last resort: print to console if logging setup itself fails
                e.printStackTrace();
            }
        }
    }

    /**
     * Optional: keep this for compatibility; does not write to disk.
     * If you want, you can change it to System.out.println(msg).
     */
    public static void info(String msg) {
        // no-op (errors-only logging)
    }

    public static void log(String context, Throwable t) {
        synchronized (LOCK) {
            try {
                ensureWriter();
                logLine("[ERROR] " + context);
                t.printStackTrace(writer);
                writer.flush();
            } catch (Exception e) {
                // If file logging fails, fall back to console
                System.err.println("CrashLogger failed to write error log.");
                t.printStackTrace();
                e.printStackTrace();
            }
        }
    }

    public static void flush() {
        synchronized (LOCK) {
            if (writer != null) writer.flush();
        }
    }

    private static void ensureWriter() throws IOException {
        if (writer != null) return;

        Path logsDir = AppPaths.logsDir().toPath();
        Files.createDirectories(logsDir);

        Path logFile = logsDir.resolve("errors.log");

        writer = new PrintWriter(new OutputStreamWriter(
                new FileOutputStream(logFile.toFile(), true),
                StandardCharsets.UTF_8
        ), true);

        // Write a small separator when the file is first opened (still "errors-only")
        writer.println();
        writer.println("----- " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()) + " -----");
        writer.flush();
    }

    private static void logLine(String line) {
        if (writer == null) return;
        writer.println(new SimpleDateFormat("HH:mm:ss.SSS").format(new Date()) + " " + line);
    }

    private static void installEventQueueTrap() {
        EventQueue systemQueue = Toolkit.getDefaultToolkit().getSystemEventQueue();
        systemQueue.push(new EventQueue() {
            @Override
            protected void dispatchEvent(AWTEvent event) {
                try {
                    super.dispatchEvent(event);
                } catch (Throwable t) {
                    CrashLogger.log("Exception during AWT event dispatch: " + event, t);
                    throw t;
                }
            }
        });
    }
}
