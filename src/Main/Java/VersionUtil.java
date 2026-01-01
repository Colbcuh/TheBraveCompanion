package Main.Java;

public final class VersionUtil {

    private VersionUtil() {}

    public static String currentVersion(String fallback) {
        // If you later add Implementation-Version to your jar manifest,
        // this will automatically pick it up.
        Package p = VersionUtil.class.getPackage();
        if (p != null) {
            String v = p.getImplementationVersion();
            if (v != null && !v.trim().isEmpty()) return normalize(v);
        }
        return normalize(fallback);
    }

    public static String normalize(String v) {
        if (v == null) return "0.0.0";
        v = v.trim();
        if (v.startsWith("v") || v.startsWith("V")) v = v.substring(1);
        return v;
    }

    public static boolean isNewer(String latest, String current) {
        int[] a = parse(normalize(latest));
        int[] b = parse(normalize(current));

        for (int i = 0; i < 3; i++) {
            if (a[i] > b[i]) return true;
            if (a[i] < b[i]) return false;
        }
        return false;
    }

    private static int[] parse(String v) {
        int[] out = new int[]{0, 0, 0};
        if (v == null) return out;

        String[] parts = v.split("\\.");
        for (int i = 0; i < Math.min(3, parts.length); i++) {
            try {
                out[i] = Integer.parseInt(parts[i].replaceAll("[^0-9]", ""));
            } catch (Exception ignored) {}
        }
        return out;
    }
}
