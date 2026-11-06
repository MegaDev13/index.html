# ATLAS 0.2.0 — configuração e limites

## ChatGPT oficial (padrão, sem chave de API)

Instale o ChatGPT publicado pela OpenAI no Google Play: https://play.google.com/store/apps/details?id=com.openai.chatgpt e entre na sua conta pelo aplicativo oficial. Toque em ATLAS na Home/dock/widget para abrir esse app; mantenha pressionado o ATLAS da Home/dock para abrir o painel NEON. No widget, PAINEL abre os comandos locais. O painel também tem ABRIR CHATGPT e ajuda de instalação.

No ChatGPT, Configurações → Voz → Iniciar no Modo Voz, quando disponível, permite iniciar voz ao abrir um chat novo/vazio. A opção depende da versão/conta; o NEON não altera configurações internas nem controla a sessão. Fonte oficial consultada: https://help.openai.com/pt-br/articles/20001274-chatgpt-voice

A conta e as conversas permanecem no ChatGPT. Não há extração de cookies, sessões ou credenciais. O pacote público usado é com.openai.chatgpt. Se o app não estiver instalado, o NEON mostra ajuda. A instalação genuína e a abertura do ChatGPT não podem ser comprovadas em um emulador que não tem o aplicativo; isso exige o aparelho.

## Painel integrado opcional

O painel é Kotlin nativo; o backend independente fica em backend/. Não há chave principal da OpenAI no APK ou Git. O APK só recebe um token de sessão do SEU backend, guardado com AES-GCM/Android Keystore em arquivo privado sem backup. A senha do backend não é persistida.

1. Em um servidor seu com Python 3.12+, publique backend/server.py atrás de HTTPS, ou use o Dockerfile com volume privado /data e proxy TLS. O backend não usa nem altera Supabase ou qualquer aplicativo existente. Não foi contratado serviço nem criado projeto pago.
2. Configure OPENAI_API_KEY e OPENAI_MODEL como secrets do servidor. Escolha um modelo compatível com Responses e function calling e verifique os preços oficiais: https://developers.openai.com/api/docs/pricing
3. Configure INPUT_PRICE_CEILING_PER_MILLION e OUTPUT_PRICE_CEILING_PER_MILLION com limites de preço por milhão de tokens iguais ou superiores aos preços do modelo selecionado. Sem esses valores, a IA permanece desativada. As quantidades são em dólares americanos, não reais.
4. Crie sua conta local: defina ATLAS_NEW_PASSWORD no ambiente e execute `python server.py --add-user seu_usuario`. Use senha forte de no mínimo 14 caracteres e remova ATLAS_NEW_PASSWORD do ambiente depois. Não coloque senha/chave em argumentos ou no Git.
5. Publique o serviço na raiz de uma URL HTTPS (sem subcaminho). A API interna deve ficar acessível somente pelo proxy TLS; limite o acesso ao servidor e proteja seu volume SQLite.
6. No NEON, abra o painel ATLAS → CONFIGURAR → Backend e login, informe essa URL, usuário e senha do backend. Não use sua senha do ChatGPT nem a chave OpenAI nesses campos. Escolha ATLAS INTEGRADO.
7. O texto enviado e até oito mensagens anteriores seguem ao backend. Respostas e propostas de ações usam a Responses API oficial. Nenhuma ação do aparelho é executada no servidor. Toda proposta de ação vinda da IA exige confirmação no Android.

O histórico é independente do ChatGPT. É criptografado no aparelho e pode ser apagado no painel ou configurado para não salvar/1/7/30 dias. A retenção é aplicada quando o histórico é aberto ou salvo; não há serviço rodando no fundo para limpeza. O servidor não armazena mensagens; guarda somente usuários com hash PBKDF2, hashes de tokens de sessão, tentativas de login e contadores de uso. A chamada OpenAI usa store:false; isso não elimina políticas de retenção de segurança do provedor.

O modo integrado NÃO é a sua sessão oficial do ChatGPT e não sincroniza automaticamente memória, conversas, assinatura ou apps conectados. Exige configuração de acesso e faturamento da API. Nesta entrega não há chave OpenAI nem backend público fornecido pelo proprietário: o caminho Android/backend é testado com fixture rotulada, e a chamada real à OpenAI fica pendente.

## Limites de custo e uso

O servidor reserva a pior quantidade estimada antes de cada chamada, de forma atômica no SQLite: até 10 solicitações/dia, 100/mês, 20.000 unidades de tokens/dia e 200.000/mês globalmente; por usuário há o mesmo limite diário. Há reserva máxima de US$ 0,10/dia e US$ 1,00/mês, calculada usando os preços máximos configurados. Entrada total limitada a 6.000 bytes e saída a até 512 tokens. Reservas NÃO são devolvidas depois de erros, timeouts ou respostas menores: a preferência é parar cedo. O servidor não faz retries automáticos.

Esses tetos dependem de preços máximos corretos. Alteração de preço/modelo exige reavaliá-los. Configure também um projeto/chave exclusivos e limites/alertas no painel OpenAI. O backend não limita outros aplicativos que usem a mesma chave nem cobranças de hospedagem. Realtime não é ativado: a validade de um token efêmero não é um teto de duração/custo de uma sessão.

## Voz e ações locais

FALAR inicia o reconhecimento do Android, pede RECORD_AUDIO apenas nesse momento e limita a captura a 25 s. Revise a transcrição antes de ENVIAR. É solicitado reconhecimento offline preferencial; disponibilidade e processamento dependem do provedor de fala instalado. Ao sair do painel, a captura é cancelada. Não há escuta permanente ou palavra de ativação.

Respostas faladas usam TTS do Android em pt-BR, opcional e inicializado sob demanda. Não são vozes Realtime da OpenAI. Os anéis reagem ao RMS reportado pelo reconhecedor durante captura. Processamento e fala têm animação de estado, sem alegação de sincronização com a forma de onda de saída. O núcleo para quando invisível e respeita economia/animações desligadas.

A lista permitida está em LocalTools.kt: abrir aplicativo por nome entre apps lançáveis, configurações, câmera, calendário, pesquisar gaveta, trocar tema, consultar bateria, abrir controles de brilho e enviar pesquisa ao navegador. Brilho e pesquisa web pedem confirmação; comandos de brilho abrem a tela do Android e não alteram silenciosamente o brilho global. A IA não executa shell, código, URLs arbitrárias nem acessa mensagens, bancos, senhas, notificações ou arquivos privados.

Sem rede, os comandos locais digitados continuam funcionando; a IA mostra indisponibilidade. O papel ROLE_ASSISTANT é solicitado somente pelo botão de configuração e depende da implementação do HyperOS. ACTION_ASSIST está declarado para entrada pública. Não há VoiceInteractionService, AccessibilityService, root ou hotword.

## Fontes técnicas

- https://developers.openai.com/api/docs/guides/text
- https://developers.openai.com/api/docs/guides/function-calling
- https://developers.openai.com/api/docs/guides/realtime
- https://developer.android.com/reference/android/app/role/RoleManager
- https://developer.android.com/reference/android/speech/SpeechRecognizer
