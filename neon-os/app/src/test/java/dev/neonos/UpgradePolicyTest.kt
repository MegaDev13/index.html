package dev.neonos

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream

class UpgradePolicyTest {
    @Test fun zipPathsRejectTraversalAndAbsolutePaths() {
        assertTrue(ArchiveSafety.allowedIconPath("NEON_ICON_PACK/icons/chat.png"))
        for (path in listOf("../x", "/NEON_ICON_PACK/a", "NEON_ICON_PACK/../x", "NEON_ICON_PACK/a//x", "NEON_ICON_PACK/a\\x")) {
            assertFalse(path, ArchiveSafety.allowedIconPath(path))
        }
    }
    @Test fun streamingLimitAppliesWhenZipSizesAreUnknown() {
        assertEquals(100, ArchiveSafety.read(ByteArrayInputStream(ByteArray(100)), 100).size)
        assertThrows(IllegalArgumentException::class.java) {
            ArchiveSafety.read(ByteArrayInputStream(ByteArray(101)), 100)
        }
    }
    @Test fun terminalRejectsSystemCommandsAndSeparators() {
        for (value in listOf("sh", "su", "rm -rf /", "help; id", "theme dark && id", "cat /data")) {
            assertEquals(TerminalCommand.UNKNOWN, TerminalParser.parse(value))
        }
        assertEquals(TerminalCommand.DARK, TerminalParser.parse("theme dark"))
        assertEquals(TerminalCommand.BATTERY, TerminalParser.parse("battery"))
        assertEquals(TerminalCommand.OPEN, TerminalParser.parse("open WhatsApp"))
        assertEquals("WhatsApp", TerminalParser.argument("open WhatsApp"))
        assertEquals(TerminalCommand.MUTE, TerminalParser.parse("silenciar"))
        assertEquals(TerminalCommand.UNMUTE, TerminalParser.parse("dessilenciar"))
        assertEquals(TerminalCommand.VOLUME, TerminalParser.parse("volume 50"))
        assertEquals(TerminalCommand.BRIGHTNESS, TerminalParser.parse("brilho auto"))
    }
    @Test fun mapProjectionPlacesBahiaSouthAndWestOfOrigin() {
        assertEquals(.5f, MapProjection.x(0.0), .00001f)
        assertEquals(.5f, MapProjection.y(0.0), .00001f)
        assertTrue(MapProjection.x(-40.01278) < .5f)
        assertTrue(MapProjection.y(-11.38111) > .5f)
    }
    @Test fun genericIconIsSeparateFromPackageArtwork() {
        assertEquals("whatsapp", NeonIcons.mapped("com.whatsapp"))
        assertEquals("plane", NeonIcons.mapped("org.telegram.messenger"))
        assertNull(NeonIcons.mapped("example.unknown"))
    }
    @Test fun atlasPrefixAndWeatherAuthorization() {
        assertEquals("show_battery", LocalTools.parse("Atlas, mostre minha bateria.")!!.name)
        assertEquals(Themes.TERMINAL, LocalTools.parse("Atlas, ative o Dark Terminal.")!!.value)
        assertTrue(LocalTools.parse("Atlas, atualize a temperatura.")!!.sensitive)
    }
    @Test fun updatesAreRestrictedToThisProject() {
        assertEquals(300, ReleasePolicy.version("neon-os-v0.3.0"))
        assertNull(ReleasePolicy.version("rpg-v1.0.0"))
        assertTrue(ReleasePolicy.allowed("https://github.com/MegaDev13/index.html/releases/download/neon-os-v0.4.0/NEON.apk"))
        assertFalse(ReleasePolicy.allowed("https://evil.example/NEON.apk"))
    }
}
