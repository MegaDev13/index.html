package dev.neonos

import android.Manifest
import android.app.*
import android.app.role.RoleManager
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.net.Uri
import android.os.*
import android.provider.*
import android.speech.*
import android.speech.tts.*
import android.view.*
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.Executors

object OfficialChatGpt {
    const val PACKAGE="com.openai.chatgpt"
    fun launchIntent(context:Context):Intent?=context.packageManager.getLaunchIntentForPackage(PACKAGE)
    fun open(activity:Activity):Boolean {
        val intent=launchIntent(activity)?:return false
        return try {activity.startActivity(intent);true}catch(_:Exception){false}
    }
    fun help(activity:Activity) {
        AlertDialog.Builder(activity).setTitle("ChatGPT oficial").setMessage("Instale o app publicado pela OpenAI e entre na sua conta. No ChatGPT, abra Configurações → Voz e ative Iniciar no Modo Voz, se a opção existir na sua versão. O NEON abre somente o aplicativo; não controla conversas ou configurações internas.")
            .setPositiveButton("Google Play"){_,_->try{activity.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("https://play.google.com/store/apps/details?id=$PACKAGE")))}catch(_:Exception){}}
            .setNegativeButton("Fechar",null).show()
    }
}
class AtlasEntryActivity:Activity() {
    override fun onCreate(state:Bundle?) {
        super.onCreate(state)
        if(!getSharedPreferences("neon",0).getBoolean("atlas_integrated",false) && OfficialChatGpt.open(this)){finish();return}
        startActivity(Intent(this,AtlasActivity::class.java).putExtra("compact",true));finish()
    }
}
@Suppress("DEPRECATION")
class AtlasActivity:Activity() {
    private val prefs by lazy{getSharedPreferences("neon",0)}
    private val secure by lazy{AtlasStorage(this)}
    private val api by lazy{AtlasApi(this)}
    private val weather by lazy{WeatherClient(this)}
    private val io=Executors.newSingleThreadExecutor()
    private val main=Handler(Looper.getMainLooper())
    private var generation=0
    private var active=false
    private var busy=false
    private var compact=false
    private var drawnTheme=""
    private val theme:ThemeSpec get()=Themes.current(this)
    private lateinit var root:LinearLayout
    private lateinit var composer:EditText
    private lateinit var transcript:LinearLayout
    private lateinit var messageScroll:ScrollView
    private lateinit var status:TextView
    private lateinit var orb:AtlasOrb
    private val networkCallback=object:android.net.ConnectivityManager.NetworkCallback(){override fun onAvailable(network:android.net.Network){main.post{if(active)refreshStatus()}};override fun onLost(network:android.net.Network){main.post{if(active)refreshStatus()}}}
    private var messages=JSONArray()
    private var recognizer:SpeechRecognizer?=null
    private var tts:TextToSpeech?=null
    private var ttsReady=false
    private val stopMic=Runnable{recognizer?.cancel();orb.status("PRONTO");status.text="Microfone encerrado por limite de tempo."}
    override fun onCreate(state:Bundle?) {
        super.onCreate(state);window.setDecorFitsSystemWindows(false);compact=intent.getBooleanExtra("compact",false)
        EventJournal.record("ATLAS","Painel inicializado");messages=secure.history();render()

    }
    override fun onStart(){super.onStart();getSystemService(android.net.ConnectivityManager::class.java).registerDefaultNetworkCallback(networkCallback)}
    override fun onStop(){getSystemService(android.net.ConnectivityManager::class.java).unregisterNetworkCallback(networkCallback);super.onStop()}
    override fun onResume(){super.onResume();active=true;if(drawnTheme!=theme.id)render();refreshStatus()}
    override fun onPause(){active=false;generation++;api.cancel();weather.cancel();busy=false;recognizer?.cancel();recognizer?.destroy();recognizer=null;main.removeCallbacks(stopMic);tts?.stop();tts?.shutdown();tts=null;ttsReady=false;orb.stop();super.onPause()}
    override fun onDestroy(){io.shutdownNow();tts?.shutdown();super.onDestroy()}
    private fun dp(v:Int)=(v*resources.displayMetrics.density).toInt()
    private fun text(value:String,size:Float=14f,color:Int=theme.text)=TextView(this).apply{text=value;textSize=size;setTextColor(color);typeface=Typeface.create(theme.font,Typeface.NORMAL);setPadding(0,dp(5),0,dp(5));Themes.tag(this,"atlas-text",theme)}
    private fun button(value:String,block:()->Unit)=text(value,12f,theme.accent).apply {gravity=Gravity.CENTER;setPadding(dp(10),dp(12),dp(10),dp(12));background=theme.background(this@AtlasActivity,true);isFocusable=true;setOnClickListener{block()};Themes.tag(this,"atlas-button",theme)}
    private fun add(v:View){root.addView(v,LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=dp(9)})}
    private fun render() {
        val draft=if(::composer.isInitialized)composer.text.toString()else ""
        drawnTheme=theme.id
        val frame=android.widget.FrameLayout(this)
        frame.addView(ArtView(this,theme.accent),android.widget.FrameLayout.LayoutParams(-1,-1))
        root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(18),dp(12),dp(18),dp(8));Themes.tag(this,"atlas-root",theme)}
        frame.addView(root,android.widget.FrameLayout.LayoutParams(-1,-1))
        setContentView(frame)
        val header=LinearLayout(this)
        header.addView(text(if(theme.mono)"[ ATLAS / CORE ]"else "ATLAS",24f,theme.accent),LinearLayout.LayoutParams(0,-2,1f))
        header.addView(button(if(compact)"EXPANDIR" else "COMPACTAR"){compact=!compact;render()});add(header)
        orb=AtlasOrb(this).apply{spec=theme};root.addView(orb,LinearLayout.LayoutParams(-1,dp(if(compact)100 else 170)))
        status=text("",12f,theme.muted);Themes.tag(status,"atlas-status",theme);add(status)
        val mode=LinearLayout(this)
        mode.addView(button("CHATGPT OFICIAL"){prefs.edit().putBoolean("atlas_integrated",false).apply();AtlasWidget.refresh(this);refreshStatus()},LinearLayout.LayoutParams(0,-2,1f))
        mode.addView(button("ATLAS INTEGRADO"){prefs.edit().putBoolean("atlas_integrated",true).apply();AtlasWidget.refresh(this);refreshStatus()},LinearLayout.LayoutParams(0,-2,1f));add(mode)
        val controls=LinearLayout(this)
        controls.addView(button("ABRIR CHATGPT"){if(!OfficialChatGpt.open(this))OfficialChatGpt.help(this)},LinearLayout.LayoutParams(0,-2,1f))
        controls.addView(button("CONFIGURAR"){configuration()},LinearLayout.LayoutParams(0,-2,1f));add(controls)
        transcript=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
        messageScroll=ScrollView(this).apply{addView(transcript);isFillViewport=false}
        root.addView(messageScroll,LinearLayout.LayoutParams(-1,0,1f));renderMessages()
        val guidance=if(!compact)text("Comandos locais funcionam sem IA. No modo integrado, o texto enviado vai ao seu backend. Histórico separado do ChatGPT.",11f,theme.muted).also{add(it)}else null
        composer=EditText(this).apply {hint=if(theme.mono)"user@atlas > comando ou mensagem_" else "Comando local ou mensagem";setTextColor(theme.text);setHintTextColor(theme.muted);textSize=15f;typeface=Typeface.create(theme.font,Typeface.NORMAL);maxLines=3;background=theme.background(this@AtlasActivity,true);setPadding(dp(12),dp(10),dp(12),dp(10));setText(draft);contentDescription="Mensagem para ATLAS";Themes.tag(this,"atlas-input",theme)};add(composer)
        val actions=LinearLayout(this)
        actions.addView(button("FALAR"){listen()},LinearLayout.LayoutParams(0,-2,1f))
        actions.addView(button("ENVIAR"){submit(composer.text.toString())},LinearLayout.LayoutParams(0,-2,1f))
        actions.addView(button("VOLTAR"){finish()},LinearLayout.LayoutParams(0,-2,1f));add(actions)
        frame.setOnApplyWindowInsetsListener{_,insets->
            val b=insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            val typing=insets.isVisible(WindowInsets.Type.ime())
            root.setPadding(dp(18)+b.left,dp(12)+b.top,dp(18)+b.right,dp(8)+maxOf(b.bottom,insets.getInsets(WindowInsets.Type.ime()).bottom))
            orb.visibility=if(typing)View.GONE else View.VISIBLE
            guidance?.visibility=if(typing)View.GONE else View.VISIBLE
            insets
        };frame.requestApplyInsets()
        refreshStatus()
        if(!prefs.getBoolean("instant",false)&&!prefs.getBoolean("saver",false)){root.alpha=.3f;root.animate().alpha(1f).setDuration(220).start()}
    }
    private fun refreshStatus() {
        if(!::status.isInitialized)return
        val online=api.connected();val mode=if(prefs.getBoolean("atlas_integrated",false))"INTEGRADO"else "CHATGPT OFICIAL"
        status.text="$mode / ${if(online)"REDE DISPONÍVEL"else"OFFLINE"} / ${if(OfficialChatGpt.launchIntent(this)!=null)"CHATGPT INSTALADO"else"CHATGPT NÃO INSTALADO"}"
        if(prefs.getBoolean("atlas_integrated",false)&&(api.endpoint().isBlank()||secure.token().isBlank()))status.text=status.text.toString()+"\nIA não configurada. Configure backend e login."
    }
    private fun renderMessages() {
        if(!::transcript.isInitialized)return
        transcript.removeAllViews()
        if(messages.length()==0)transcript.addView(text("ATLAS pronto. Experimente: Mostre minha bateria ou Mude para Dark Terminal.",14f,theme.muted))
        for(i in maxOf(0,messages.length()-20) until messages.length()) {
            val m=messages.getJSONObject(i);val card=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(12),dp(8),dp(12),dp(8));background=theme.background(this@AtlasActivity)}
            card.addView(text(m.optString("source"),10f,theme.accent));card.addView(text(m.optString("text"),14f))
            transcript.addView(card,LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=dp(8)})
        }
        val viewport=messageScroll
        viewport.post{if(viewport.isAttachedToWindow)viewport.scrollTo(0,viewport.getChildAt(0)?.height?:0)}
    }
    private fun record(source:String,value:String) {
        val entry=JSONObject().put("source",source).put("text",value.take(8000)).put("time",System.currentTimeMillis())
        messages.put(entry);while(messages.length()>60)messages.remove(0)
        if(prefs.getInt("atlas_retention",7)>0)try{secure.setHistory(messages)}catch(_:Exception){status.text="Não foi possível salvar o histórico criptografado."}
        renderMessages()
    }
    private fun say(value:String){record("LOCAL",value);if(prefs.getBoolean("atlas_speak",false))speak(value)}
    private fun speak(value:String){
        if(!active)return
        if(ttsReady){tts?.speak(value.take(4000),TextToSpeech.QUEUE_FLUSH,null,"atlas-answer");return}
        if(tts!=null){status.text="Mecanismo de voz inicializando; tente novamente.";return}
        tts=TextToSpeech(this){code->
            if(active&&code==TextToSpeech.SUCCESS){
                val language=tts?.setLanguage(Locale("pt","BR"))?:TextToSpeech.LANG_NOT_SUPPORTED
                ttsReady=language>=0
                tts?.setOnUtteranceProgressListener(object:UtteranceProgressListener(){override fun onStart(id:String?){main.post{if(active)orb.status("FALANDO")}};override fun onDone(id:String?){main.post{if(active)orb.status("PRONTO")}};@Deprecated("Framework callback") override fun onError(id:String?){main.post{if(active)orb.status("PRONTO")}}})
                if(ttsReady)speak(value)else status.text="Voz pt-BR não disponível no mecanismo TTS do aparelho."
            }else if(active)status.text="Mecanismo de voz do Android indisponível."
        }
    }

    fun submit(raw:String) {
        val value=raw.trim();if(value.isEmpty())return
        if(value.length>1500){say("Mensagem muito longa; use até 1.500 caracteres.");return}
        if(busy){status.text="Uma solicitação está em andamento.";return}
        composer.setText("");record("VOCÊ",value)
        val action=LocalTools.parse(value)
        if(action!=null){perform(action,false);return}
        if(!prefs.getBoolean("atlas_integrated",false)){say("Para conversar, use ABRIR CHATGPT. Os comandos locais podem ser digitados aqui. A conversa no próprio NEON exige o modo integrado configurado.");return}
        if(api.endpoint().isBlank()||secure.token().isBlank()){say("ATLAS integrado não configurado. Abra CONFIGURAR, informe seu backend HTTPS e faça login. Nenhuma resposta de IA foi gerada.");return}
        if(!api.connected()){say("Sem conexão. A IA está indisponível; comandos locais continuam disponíveis.");return}
        busy=true;orb.status("PROCESSANDO");status.text="Enviando ao backend configurado..."
        val history=JSONArray()
        for(i in maxOf(0,messages.length()-9) until messages.length()-1){val m=messages.getJSONObject(i);if(m.optString("source") in setOf("VOCÊ","ATLAS / API"))history.put(JSONObject().put("role",if(m.optString("source")=="VOCÊ")"user"else"assistant").put("content",m.optString("text")))}
        val ticket=++generation
        io.execute {
            try {
                val response=api.request("/v1/chat",JSONObject().put("message",value).put("history",history),secure.token())
                main.post {if(active&&ticket==generation){busy=false;orb.status("PRONTO");refreshStatus();if(response.optBoolean("truncated"))status.text="Resposta limitada pelo teto de saída do backend.";val reply=response.optString("reply");if(reply.isNotBlank()){record("ATLAS / API",reply);if(prefs.getBoolean("atlas_speak",false))speak(reply)}
                    val actions=response.optJSONArray("actions")?:JSONArray();for(i in 0 until minOf(3,actions.length())) {val a=actions.optJSONObject(i)?:continue;val command=LocalAction(a.optString("name"),a.optString("value"),a.optInt("number",0));if(LocalTools.valid(command))perform(command,true)else say("A IA propôs uma ação não permitida; bloqueada.")}
                }}
            } catch(e:Exception){main.post{if(active&&ticket==generation){busy=false;orb.status("PRONTO");say("IA indisponível: ${e.message?.take(240)?:"falha de conexão"}")}}}
        }
    }
    private fun perform(action:LocalAction,fromAi:Boolean) {
        if(!LocalTools.valid(action)){say("Ação fora da lista permitida.");return}
        val execute={executeAction(action)}
        if(fromAi||action.sensitive)AlertDialog.Builder(this).setTitle("Autorizar ação local?").setMessage("${LocalTools.description(action)}\n${if(action.name=="brightness_settings")"Será aberta a configuração de tela; você ajusta o brilho no Android."else if(action.name=="web_search")"A consulta será enviada ao navegador externo."else "A ação ocorrerá neste aparelho."}").setPositiveButton("Autorizar"){_,_->execute()}.setNegativeButton("Cancelar"){_,_->say("Ação cancelada.")}.show()else execute()
    }
    private fun executeAction(action:LocalAction) {
        when(action.name) {
            "set_theme"->{Themes.select(this,action.value);render();say("Tema ${theme.title} ativado. Favoritos e widgets preservados.")}
            "refresh_weather"->updateWeatherAction()
            "show_battery"->{val b=registerReceiver(null,IntentFilter(Intent.ACTION_BATTERY_CHANGED));val level=b?.getIntExtra("level",-1)?:-1;val scale=b?.getIntExtra("scale",100)?:100;say(if(level>=0&&scale>0)"Bateria: ${level*100/scale}%."else"Bateria indisponível.")}
            "open_settings"->launch(Intent(Settings.ACTION_SETTINGS))
            "open_camera"->launch(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))
            "open_calendar"->launch(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN,Intent.CATEGORY_APP_CALENDAR))
            "brightness_settings"->launch(Intent(Settings.ACTION_DISPLAY_SETTINGS))
            "search_apps"->launch(Intent(this,MainActivity::class.java).putExtra("neon.page","GRID").putExtra("neon.query",action.value))
            "web_search"->{if(!api.connected())say("Sem conexão para pesquisar na internet.")else launch(Intent(Intent.ACTION_VIEW,Uri.parse("https://www.google.com/search?q="+Uri.encode(action.value))))}
            "open_app"->{
                val matches=AppRepository(this).load().filter {LocalTools.normalize(it.label)==LocalTools.normalize(action.value) || it.component.packageName==action.value}
                if(matches.isEmpty())say("Aplicativo '${action.value}' não encontrado entre os apps disponíveis. Use Pesquise meus aplicativos.")
                else if(matches.size==1)try{AppRepository(this).open(matches[0])}catch(_:Exception){say("Não foi possível abrir esse aplicativo.")}
                else AlertDialog.Builder(this).setTitle("Escolha o aplicativo/perfil").setItems(matches.map{"${it.label} / perfil ${it.serial}"}.toTypedArray()){_,i->try{AppRepository(this).open(matches[i])}catch(_:Exception){say("Perfil indisponível.")}}.show()
            }
        }
    }
    private fun updateWeatherAction(){
        if(weather.city()==null||!prefs.getBoolean("weather_enabled",false)){
            say("Escolha uma cidade e ative o clima.")
            launch(Intent(this,HudSettingsActivity::class.java))
            return
        }
        val ticket=++generation
        busy=true
        orb.status("PROCESSANDO")
        io.execute{
            val result=runCatching{weather.refresh()}
            main.post{
                if(active&&ticket==generation){
                    busy=false
                    orb.status("PRONTO")
                    result.onSuccess{say("${it.city.name}: ${it.degrees}, ${it.condition}. Fonte: Open-Meteo.")}
                        .onFailure{say("Clima indisponível: ${it.message}")}
                }
            }
        }
    }
    private fun launch(intent:Intent){try{startActivity(intent)}catch(_:Exception){say("Essa função não está disponível no aparelho.")}}
    private fun listen() {
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO),20);return}
        if(!SpeechRecognizer.isRecognitionAvailable(this)){say("Reconhecimento de voz do Android indisponível. Você pode digitar ou usar o app ChatGPT.");return}
        recognizer?.destroy();recognizer=SpeechRecognizer.createSpeechRecognizer(this)
        recognizer?.setRecognitionListener(object:RecognitionListener {
            override fun onReadyForSpeech(params:Bundle?){orb.status("OUVINDO");status.text="Microfone ativo por até 25 s. Transcrição será revisada antes de enviar."}
            override fun onBeginningOfSpeech(){}
            override fun onRmsChanged(rmsdB:Float){orb.rms=(rmsdB/12f).coerceIn(0f,1f);orb.invalidate()}
            override fun onBufferReceived(buffer:ByteArray?){}
            override fun onEndOfSpeech(){main.removeCallbacks(stopMic);orb.status("PRONTO")}
            override fun onError(error:Int){main.removeCallbacks(stopMic);orb.status("PRONTO");status.text="Voz indisponível (Android $error). Use texto ou tente novamente."}
            override fun onResults(results:Bundle?){main.removeCallbacks(stopMic);orb.status("PRONTO");composer.setText(results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?:"");status.text="Transcrição pronta. Revise e toque ENVIAR."}
            override fun onPartialResults(partialResults:Bundle?){}
            override fun onEvent(eventType:Int,params:Bundle?){}
        })
        recognizer?.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM).putExtra(RecognizerIntent.EXTRA_LANGUAGE,"pt-BR").putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE,true))
        main.postDelayed(stopMic,25000)
    }
    override fun onRequestPermissionsResult(code:Int,permissions:Array<out String>,results:IntArray){super.onRequestPermissionsResult(code,permissions,results);if(code==20){if(results.firstOrNull()==PackageManager.PERMISSION_GRANTED)listen()else say("Microfone não autorizado. O ATLAS continua disponível por texto.")}}
    private fun configuration() {
        val items=arrayOf("Ajuda do ChatGPT oficial","Backend e login","Histórico: ${prefs.getInt("atlas_retention",7)} dias","Apagar histórico local","${if(prefs.getBoolean("atlas_speak",false))"Desativar"else"Ativar"} respostas faladas","Assistente padrão do Android","Sair do backend")
        AlertDialog.Builder(this).setTitle("CONFIGURAR ATLAS").setItems(items){_,i->when(i){
            0->OfficialChatGpt.help(this)
            1->backendLogin()
            2->AlertDialog.Builder(this).setTitle("Retenção local criptografada").setItems(arrayOf("Não salvar","1 dia","7 dias","30 dias")){_,index->prefs.edit().putInt("atlas_retention",intArrayOf(0,1,7,30)[index]).apply();if(index==0)secure.clearHistory();else{messages=secure.history();renderMessages()}}.show()
            3->AlertDialog.Builder(this).setMessage("Apagar o histórico local independente do ATLAS?").setPositiveButton("Apagar"){_,_->messages=JSONArray();secure.clearHistory();renderMessages()}.setNegativeButton("Cancelar",null).show()
            4->{prefs.edit().putBoolean("atlas_speak",!prefs.getBoolean("atlas_speak",false)).apply();refreshStatus()}
            5->{val role=getSystemService(RoleManager::class.java);try{if(role.isRoleAvailable(RoleManager.ROLE_ASSISTANT))startActivity(role.createRequestRoleIntent(RoleManager.ROLE_ASSISTANT))else launch(Intent(Settings.ACTION_VOICE_INPUT_SETTINGS))}catch(_:Exception){say("O HyperOS não disponibilizou esse papel. Use o botão ou o widget ATLAS.")}}
            6->{generation++;api.cancel();busy=false;orb.status("PRONTO");val token=secure.token();secure.setToken("");refreshStatus();if(token.isNotBlank())io.execute {try{api.request("/v1/logout",JSONObject(),token)}catch(_:Exception){main.post{if(active)status.text="Sessão removida do aparelho. Sem confirmação de revogação no servidor; expira em até 24 h."}}}}
        }}.show()
    }
    private fun backendLogin() {
        val form=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(20),0,dp(20),0)}
        val url=EditText(this).apply{hint="https://seu-backend";setText(api.endpoint());inputType=android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_URI}
        val user=EditText(this).apply{hint="Usuário do seu backend";setSingleLine(true)}
        val password=EditText(this).apply{isSaveEnabled=false;hint="Senha do backend";inputType=android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD}
        form.addView(url);form.addView(user);form.addView(password)
        AlertDialog.Builder(this).setTitle("ATLAS integrado / conexão").setMessage("Use uma conta do backend independente. Nunca digite uma chave da OpenAI ou senha do ChatGPT aqui.").setView(form).setPositiveButton("Conectar"){_,_->
            val endpoint=url.text.toString().trim().trimEnd('/');val username=user.text.toString().trim();val pass=password.text.toString();password.setText("")
            if(!api.validEndpoint(endpoint)){say("Informe uma URL HTTPS válida, sem caminho, usuário ou parâmetros.");return@setPositiveButton}
            if(busy){return@setPositiveButton}
            prefs.edit().putString("atlas_url",endpoint).apply();secure.setToken("");busy=true;orb.status("PROCESSANDO");val ticket=++generation
            io.execute {try{val response=api.request("/v1/login",JSONObject().put("username",username).put("password",pass));main.post{if(active&&ticket==generation){secure.setToken(response.getString("token"));busy=false;orb.status("PRONTO");refreshStatus();say("Login no backend concluído. Histórico separado do ChatGPT.")}}}catch(e:Exception){main.post{if(active&&ticket==generation){busy=false;orb.status("PRONTO");say("Falha no login: ${e.message?.take(240)}")}}}}
        }.setNegativeButton("Cancelar",null).show()
    }
}
