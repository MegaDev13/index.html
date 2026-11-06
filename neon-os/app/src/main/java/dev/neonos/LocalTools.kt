package dev.neonos

import java.text.Normalizer
import java.util.Locale

data class LocalAction(val name:String,val value:String="",val number:Int=0) {
    val sensitive:Boolean get()=name in setOf("brightness_settings","web_search","refresh_weather")
}
object LocalTools {
    val allowed=setOf("open_app","open_settings","open_camera","search_apps","set_theme","show_battery","open_calendar","brightness_settings","web_search","refresh_weather")
    fun normalize(s:String)=Normalizer.normalize(s.lowercase(Locale.ROOT),Normalizer.Form.NFD).replace("\\p{M}+".toRegex(),"").trim().trimEnd('.','!','?')
    fun valid(a:LocalAction):Boolean = a.name in allowed && a.value.length<=160 && a.number in 0..100 && when(a.name){"set_theme"->a.value in Themes.ids;"open_app","web_search"->a.value.isNotBlank();else->true}
    fun description(a:LocalAction):String=when(a.name){
        "set_theme"->"Ativar o tema "+Themes.titles[Themes.ids.indexOf(a.value).coerceAtLeast(0)]
        "open_app"->"Abrir o aplicativo ${a.value}"
        "open_settings"->"Abrir as configurações do Android"
        "open_camera"->"Abrir a câmera"
        "search_apps"->"Pesquisar aplicativos: ${a.value}"
        "show_battery"->"Mostrar a bateria"
        "refresh_weather"->"Consultar o clima no Open-Meteo"
        "open_calendar"->"Abrir o calendário"
        "brightness_settings"->"Abrir os controles de brilho"
        "web_search"->"Pesquisar na internet: ${a.value}"
        else->"Ação não permitida"
    }
    fun parse(raw:String):LocalAction? {
        val s=normalize(raw).replace(Regex("^atlas[, :]+"),"")
        if(s in setOf("atualize a temperatura","atualize o clima","atualizar clima"))return LocalAction("refresh_weather")
        if(s.length>200)return null
        if(s in setOf("abra as configuracoes","abrir configuracoes","abra configuracoes"))return LocalAction("open_settings")
        if(s in setOf("abra a camera","abrir camera","abra camera"))return LocalAction("open_camera")
        if(s in setOf("mostre minha bateria","mostre a bateria","bateria","qual minha bateria"))return LocalAction("show_battery")
        if(s in setOf("abra o calendario","abrir calendario","abra calendario"))return LocalAction("open_calendar")
        if(s.startsWith("ajuste o brilho")||s=="ajustar brilho")return LocalAction("brightness_settings")
        if(s in setOf("pesquise meus aplicativos","pesquisar aplicativos","abra a gaveta","mostre os aplicativos"))return LocalAction("search_apps")
        val themes=mapOf("dark terminal" to Themes.TERMINAL,"cyberpunk neon" to Themes.NEON,"corporate dystopia" to Themes.CORPORATE)
        for((label,id)in themes)if(s in setOf("mude para $label","volte para $label","tema $label","troque para $label","ative o $label","ative $label"))return LocalAction("set_theme",id)
        Regex("^(abra|abrir) (?:o |a )?(.+)$").matchEntire(s)?.let {return LocalAction("open_app",it.groupValues[2]).takeIf(::valid)}
        Regex("^pesquise na internet (.+)$").matchEntire(s)?.let {return LocalAction("web_search",Regex("(?i)internet\\s+(.+)$").find(raw)?.groupValues?.get(1)?.trim()?:"").takeIf(::valid)}
        return null
    }
}
