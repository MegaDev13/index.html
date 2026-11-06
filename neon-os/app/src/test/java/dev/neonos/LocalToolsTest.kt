package dev.neonos
import org.junit.Assert.*
import org.junit.Test
class LocalToolsTest {
 @Test fun themeCommands(){assertEquals(Themes.TERMINAL,LocalTools.parse("Mude para Dark Terminal.")!!.value);assertEquals(Themes.NEON,LocalTools.parse("Volte para Cyberpunk Neon")!!.value);assertEquals(Themes.CORPORATE,LocalTools.parse("Tema Corporate Dystopia")!!.value)}
 @Test fun localKnownCommands(){assertEquals("show_battery",LocalTools.parse("Mostre minha bateria")!!.name);assertEquals("open_app",LocalTools.parse("Abra o WhatsApp")!!.name);assertEquals("open_settings",LocalTools.parse("Abra as configurações.")!!.name);assertEquals("open_camera",LocalTools.parse("Abra a câmera")!!.name)}
 @Test fun arbitraryCommandsAreNotTools(){assertNull(LocalTools.parse("execute rm -rf /"));assertFalse(LocalTools.valid(LocalAction("shell","id")));assertFalse(LocalTools.valid(LocalAction("set_theme","file:///system")))}
 @Test fun sensitiveActionsAreExplicit(){assertTrue(LocalAction("web_search","clima").sensitive);assertTrue(LocalTools.parse("Ajuste o brilho")!!.sensitive);assertFalse(LocalAction("show_battery").sensitive)}
 @Test fun boundedArguments(){assertFalse(LocalTools.valid(LocalAction("open_app","x".repeat(200))));assertFalse(LocalTools.valid(LocalAction("set_theme",Themes.TERMINAL,500)))}
}
