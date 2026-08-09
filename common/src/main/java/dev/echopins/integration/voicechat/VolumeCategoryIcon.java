package dev.echopins.integration.voicechat;

/**
 * The 16x16 icon shown beside the EchoPins slider in Simple Voice Chat's volume screen.
 *
 * <p>Kept as a character map rather than a packed integer array so the shape is reviewable and
 * editable by eye. Colours match the mod's branding: a teal pin body, a dark outline, and a white
 * microphone capsule.
 */
final class VolumeCategoryIcon {

    /** '.' transparent, '#' outline, 'o' pin body, 'w' microphone. */
    private static final String[] PIXELS = {
            "......####......",
            "....########....",
            "...##oooooo##...",
            "..#oooowwoooo#..",
            "..#ooowwwwooo#..",
            ".#oooowwwwoooo#.",
            ".#oooowwwwoooo#.",
            ".#oooowwwwoooo#.",
            ".#ooooowwooooo#.",
            "..#oooowwwwoo#..",
            "..#ooooowwooo#..",
            "...##oooooo##...",
            "....##oooo##....",
            ".....##oo##.....",
            "......####......",
            ".......##.......",
    };

    private static final int TRANSPARENT = 0x00000000;
    private static final int OUTLINE = rgba(0x16, 0x20, 0x2B, 0xFF);
    private static final int BODY = rgba(0x2F, 0xB6, 0xC4, 0xFF);
    private static final int MICROPHONE = rgba(0xF5, 0xFA, 0xFB, 0xFF);

    private VolumeCategoryIcon() {
    }

    private static int rgba(int r, int g, int b, int a) {
        return (r << 24) | (g << 16) | (b << 8) | a;
    }

    /** @return a fresh 16x16 RGBA array, as the API requires */
    static int[][] create() {
        int[][] icon = new int[16][16];
        for (int y = 0; y < 16; y++) {
            String row = PIXELS[y];
            for (int x = 0; x < 16; x++) {
                icon[y][x] = switch (row.charAt(x)) {
                    case '#' -> OUTLINE;
                    case 'o' -> BODY;
                    case 'w' -> MICROPHONE;
                    default -> TRANSPARENT;
                };
            }
        }
        return icon;
    }
}
