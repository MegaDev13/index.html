# NEON OS 0.3.1 — instalação, configuração e reversão

## Disponibilidade da 0.3.1

O GitHub Actions compilou a 0.3.1 e publicou as evidências. O arquivo `NEON-OS-0.3.1.apk` da entrega foi assinado com a chave permanente e verificado com apksigner. Veja [a verificação do gesto](GAVETA-0.3.1.md).

## Atualizar a instalação

1. Baixe **NEON-OS-0.3.1.apk**, o release assinado da entrega. Os APKs unsigned/debug do Actions são artefatos de desenvolvimento.
2. Instale por cima do NEON OS 0.3.0 para preservar as preferências e o histórico local. O pacote continua `dev.neonos.poco`, o versionCode sobe de 10300 para 10301 e o certificado é o mesmo.
3. Autorize apenas a instalação solicitada normalmente pelo Android. Mantenha as proteções do aparelho. Uma assinatura igual permite a atualização, mas a instalação sobre a versão anterior ainda precisa ser verificada no aparelho físico.
4. Abra NEON OS. Em **CONFIG**, escolha Cyberpunk Neon, Dark Terminal ou Corporate Dystopia. A escolha é persistente e não altera a ordem dos favoritos ou os IDs de widgets.

Em **CONFIG**, abra **HUD / CLIMA** para configurar uma cidade, **NEON ICON STUDIO** para escolher ou importar ícones e **WALLPAPER PRÓPRIO** para escolher uma imagem. Veja [os controles da Home e os limites](IMPLEMENTACAO-V2.md).

## Preservar os gestos do POCO

O modo recomendado continua sendo usar NEON como aplicativo complementar, mantendo **POCO Launcher** como tela inicial. Adicione os widgets **Relógio NEON** e **ATLAS** pelos controles de widgets do launcher original e aplique o wallpaper se desejar.

A Home completa do NEON é opcional: **CONFIG → Testar como launcher padrão** abre a seleção do Android. O HyperOS pode limitar os gestos ao launcher original; o NEON não os força nem contorna essa restrição. Verifique a versão instalada em **Configurações → Sobre o telefone** antes de experimentar essa opção. A versão comercial do HyperOS não pode ser deduzida apenas da identificação da build Android.

A tela de bloqueio, o reconhecimento facial, a digital, o PIN, as notificações e o AOD continuam no HyperOS. Use as opções oficiais de Temas/Personalização para relógio e efeitos biométricos. As cinco transições do NEON são decorativas e universais sobre sua própria Home; não identificam digital ou rosto.

## ATLAS com ChatGPT oficial — padrão, sem chave de API

1. Instale o aplicativo **ChatGPT**, publicado pela OpenAI, a partir da Play Store. O pacote procurado pelo NEON é `com.openai.chatgpt`.
2. Faça login e use sua conta dentro do próprio aplicativo oficial.
3. Toque no botão ATLAS da Home, dock ou widget. No modo padrão, ele abre o aplicativo oficial pelo mecanismo público de abertura do Android. Se não estiver instalado, o painel oferece orientação e o link da loja.
4. Pressione prolongadamente ATLAS na Home/dock para abrir o painel NEON com comandos locais e configuração. No widget, use **PAINEL**. Toque em **CONFIGURAR ATLAS** na configuração para selecionar os modos.
5. Nas versões compatíveis do ChatGPT, ative **Configurações → Voz → Iniciar no Modo Voz**. O ajuste inicia a voz quando o ChatGPT abre em uma conversa nova ou vazia. [Guia oficial do Modo Voz](https://help.openai.com/pt-br/articles/20001274-chatgpt-voice)

O painel integrado não acessa a sessão, o histórico ou a memória da sua conta ChatGPT. A assinatura ChatGPT e o uso da API são serviços separados.

## ATLAS integrado — opcional

Siga [ATLAS-CONFIGURACAO.md](ATLAS-CONFIGURACAO.md). É necessário hospedar o backend isolado em HTTPS, configurar uma chave de projeto OpenAI somente no servidor, definir modelo/preços e criar um usuário. No painel, informe a URL HTTPS, faça login e selecione **ATLAS INTEGRADO**.

Sem backend/login, o painel explica que não está configurado. Sem internet, mostra a indisponibilidade da IA e mantém comandos locais. A integração real com a OpenAI não foi executada nesta entrega por ausência de chave e servidor de produção configurados.

**FALAR** usa o reconhecimento de fala do Android por acionamento manual, após autorização de microfone. A transcrição fica disponível para revisão antes de enviar. Respostas faladas usam TTS Android opcional; dependem do mecanismo/voz pt-BR instalado. Não há escuta permanente, palavra de ativação, voz OpenAI Realtime ou streaming WebRTC nesta versão.

Comandos disponíveis incluem abrir aplicativos/configurações/câmera/calendário, pesquisar aplicativos, trocar os três temas, consultar a bateria, abrir as opções de brilho e pesquisar no navegador. Ajustes de brilho e pesquisa externa pedem confirmação; ações propostas pelo modelo sempre pedem confirmação. O brilho é ajustado pelo usuário na tela original do Android.

## Permissões e dados

O APK declara `SET_WALLPAPER`, `INTERNET`, `ACCESS_NETWORK_STATE`, `RECORD_AUDIO`, `ACCESS_COARSE_LOCATION` e `BLUETOOTH_CONNECT`. O microfone é solicitado ao tocar em FALAR. Use cidade manual se preferir dispensar localização. O estado Bluetooth depende da autorização opcional no HUD. A importação de imagens usa somente o documento escolhido pelo seletor Android. O provedor Open-Meteo recebe a busca da cidade ou a coordenada aproximada para calcular a previsão. A vinculação de widgets e os papéis padrão passam pelos controles do Android.

Token e histórico local independente usam AES-GCM com chave no Android Keystore, em arquivo privado sem backup Android. A retenção pode ser desativada ou ajustada para 1, 7 ou 30 dias. Expurgo por idade ocorre ao ler/gravar, sem serviço em segundo plano. **CONFIGURAR → Apagar histórico** remove as conversas locais; sair remove o token e tenta revogar a sessão do servidor. Sem confirmação de rede, a sessão remota expira em até 24 horas.

## Teste no aparelho e reversão

Comece mantendo POCO Launcher. Verifique abertura de seus apps, notificações, chamadas, NFC, bancos e autenticação pelos fluxos normais antes de escolher outra Home. Esses itens, a autonomia e os 120 Hz não foram validados em um POCO físico.

Em **STATUS → Iniciar medição local**, use o NEON, volte e exporte o diagnóstico. CPU/memória/frames pertencem apenas ao processo/janela do NEON; não medem bateria ou tempo de autenticação. A medição para quando a janela fica invisível.

Para reverter, use **CONFIG → Restaurar POCO Launcher** ou **Configurações → Aplicativos → Apps padrão → Tela inicial**. Restaure wallpaper/tema pelas opções do HyperOS e remova os widgets, se desejar. Desinstalar NEON apaga somente seus dados locais; não migra nem altera os dados de outros aplicativos. A desinstalação não é necessária para trocar de tema ou retornar ao launcher original.

## Assinatura e futuras compilações

Certificado SHA-256 esperado:

`973185c207025f9b01339ce1130c85bd1fa6a22d7f652c786ae84d690874774b`

A chave privada permanente permanece no backup privado já entregue; não está no repositório nem neste pacote. Os APKs 0.3.0 e 0.3.1 foram verificados com apksigner. O release 0.3.1 foi compilado no GitHub Actions e assinado localmente com a chave preservada. A verificação confirmou o pacote `dev.neonos.poco` e o aumento do versionCode de 10300 para 10301. A assinatura automática do workflow exige os secrets `NEON_KEYSTORE_B64` e `NEON_STORE_PASSWORD`, ainda não configurados pelo proprietário. Ela falha se os secrets estiverem ausentes ou o certificado for diferente, evitando uma nova identidade incompatível.
