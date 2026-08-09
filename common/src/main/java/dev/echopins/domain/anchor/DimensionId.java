package dev.echopins.domain.anchor;

import java.util.Objects;

/**
 * A dimension key in {@code namespace:path} form, kept as a plain value so the domain layer
 * stays free of Minecraft types.
 *
 * <p>Validation mirrors Minecraft's {@code ResourceLocation} character rules. Anything that
 * cannot be a valid dimension key is rejected at construction, which means a hostile client
 * cannot smuggle a path fragment (for example {@code ../../}) into a value that later reaches
 * the audio store's filename logic.
 */
public record DimensionId(String namespace, String path) {

    public static final int MAX_LENGTH = 256;

    public DimensionId {
        Objects.requireNonNull(namespace, "namespace");
        Objects.requireNonNull(path, "path");
        if (!isValidNamespace(namespace)) {
            throw new IllegalArgumentException("Invalid dimension namespace: " + namespace);
        }
        if (!isValidPath(path)) {
            throw new IllegalArgumentException("Invalid dimension path: " + path);
        }
        if (namespace.length() + path.length() + 1 > MAX_LENGTH) {
            throw new IllegalArgumentException("Dimension id too long");
        }
    }

    /**
     * Parses {@code namespace:path}. A value with no colon is treated as the {@code minecraft}
     * namespace, matching Minecraft's own shorthand.
     *
     * @throws IllegalArgumentException if the value is not a well-formed dimension key
     */
    public static DimensionId parse(String value) {
        Objects.requireNonNull(value, "value");
        int colon = value.indexOf(':');
        if (colon < 0) {
            return new DimensionId("minecraft", value);
        }
        return new DimensionId(value.substring(0, colon), value.substring(colon + 1));
    }

    public static DimensionId of(String namespace, String path) {
        return new DimensionId(namespace, path);
    }

    private static boolean isValidNamespace(String value) {
        if (value.isEmpty()) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '.' || c == '-';
            if (!ok) {
                return false;
            }
        }
        return true;
    }

    private static boolean isValidPath(String value) {
        if (value.isEmpty()) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
                    || c == '_' || c == '.' || c == '-' || c == '/';
            if (!ok) {
                return false;
            }
        }
        return hasNoTraversalSegments(value);
    }

    /**
     * Minecraft's own path rules permit {@code .} and {@code /}, which means {@code ../../etc}
     * is a syntactically valid {@code ResourceLocation} path. No real dimension is ever named
     * that way, so traversal-shaped segments are rejected here rather than relied upon to be
     * harmless further down.
     */
    private static boolean hasNoTraversalSegments(String path) {
        if (path.startsWith("/") || path.endsWith("/") || path.contains("//")) {
            return false;
        }
        int start = 0;
        while (start <= path.length()) {
            int end = path.indexOf('/', start);
            if (end < 0) {
                end = path.length();
            }
            String segment = path.substring(start, end);
            if (segment.equals(".") || segment.equals("..")) {
                return false;
            }
            start = end + 1;
        }
        return true;
    }

    @Override
    public String toString() {
        return namespace + ":" + path;
    }
}
