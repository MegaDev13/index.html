# NEON OS 0.2.0 — relatório verificável

Entrega incremental sobre 0.1.1, na mesma branch `neon-os` do repositório `MegaDev13/index.html`. O site da branch principal não foi substituído. Permanecem os cinco efeitos NEON ACCESS, favoritos, host de widgets, gaveta, status, configurações, atalhos e suporte anterior a pacotes de ícones.

## Implementado e exercitado

| Área | Resultado e evidência |
|---|---|
| Três temas | Cyberpunk Neon, Dark Terminal e Corporate Dystopia selecionáveis em CONFIG, sem reiniciar o telefone. Tokens compartilhados de cores, fontes, painéis, botões, pesquisa, dock e ícones. |
| Dark Terminal completo no NEON | Home, dock, gaveta, pesquisa, ícones monocromáticos, widgets próprios, relógio, status, configuração e ATLAS possuem versões de tema. Capturas Android reais por tela em `verification/dark_terminal-*.png`. Os widgets próprios são RemoteViews renderizados pelo Android no harness visual; a vinculação Android é exercitada separadamente no smoke. |
| Persistência | Activity encerrada/reaberta e processo forçado a parar/reiniciado via ADB: Dark Terminal e dois favoritos preservados. A troca não modifica as preferências de vínculos de widgets; provedores externos permanecem com validação parcial. |
| ATLAS visual | Esfera/anéis nativos Canvas, compacto/expandido, transcrição, estados de processamento/escuta/fala e tema correspondente. Sem animação contínua ociosa; animações param ao perder visibilidade. O teclado recolhe o núcleo para manter campo e Enviar disponíveis. |
| Comandos locais | Lista declarada e parser validados. Execução de bateria/temas no painel e abertura de Configurações no smoke exercitadas. App/câmera/calendário/pesquisa/brilho possuem caminhos Android implementados, ainda sem teste completo de cada provedor real. Pesquisa externa e opções de brilho com confirmação; propostas do modelo sempre exigem autorização. |
| Erros/offline | Falta de configuração explica backend/login; rede do AVD realmente desligada, erro de IA visível e consulta local de bateria funcionando. |
| Contrato integrado | Android envia HTTP autenticado ao backend real de teste, recebe resposta explicitamente rotulada FIXTURE; login, cotas e armazenamento criptografado exercitados. Não representa uma chamada real à OpenAI. |
| Privacidade local | AES-GCM/Android Keystore, token e histórico ausentes em texto claro, exclusão e retenção desativada exercitadas. Microfone não concedido automaticamente. |
| Compilação | Debug, release e APK de instrumentação compilados no Actions. Artefato release assinado com a mesma chave da versão anterior. |

## Implementado, com validação parcial

- **ChatGPT oficial:** identificação de `com.openai.chatgpt`, abertura pública pelo Android, orientações para instalação e início em voz quando suportado. O AVD não possui o aplicativo oficial; detecção de ausência foi testada. A abertura positiva e o Modo Voz do aplicativo genuíno precisam de teste no aparelho. Não foi instalado um pacote falso para declarar esse teste aprovado.
- **OpenAI Responses:** backend independente com autenticação, sessões, limites de requisições/tokens/custo, `store:false` e ferramentas estritas; frontend completo para HTTPS/login/conversa. Sem chave e hospedagem configuradas, nenhuma chamada real foi efetuada. O teste integrado substitui apenas o provedor OpenAI por uma resposta de teste identificada; rede Android, autenticação e servidor são reais.
- **Voz:** SpeechRecognizer por toque, transcrição antes do envio e TTS Android opcional implementados. Reconhecimento, qualidade da voz e hardware de microfone não foram validados no POCO. Anéis respondem ao RMS recebido no reconhecimento; a fala sintetizada usa estado visual, sem alegar amplitude real do áudio de saída.
- **Papel de assistente:** ACTION_ASSIST e solicitação opcional de ROLE_ASSISTANT implementados. A disponibilidade do papel foi observada no AVD; concessão e integração do HyperOS não foram testadas. Nenhum papel é concedido automaticamente.
- **Widgets/ícones de terceiros:** integrações anteriores preservadas; o tema controla widgets e ícones renderizados pelo próprio NEON. Não recolore internamente widgets de outros aplicativos ou as telas dos apps instalados.

## Fora do escopo suportado nesta versão

Sem root, ROM, SystemUI, captura biométrica, sessão extraída do ChatGPT, AccessibilityService, câmera própria facial ou tela falsa de bloqueio. A animação biométrica real continua no HyperOS. NEON ACCESS é transição decorativa universal na Home NEON; o Android não informa aqui se o desbloqueio ocorreu por digital, rosto ou PIN.

Realtime/WebRTC, palavra de ativação e microfone permanente não foram habilitados. A rota de voz do backend retorna 501 explícito e não cria sessões com cobrança. Não há sincronização com memória/histórico da conta ChatGPT. Brilho abre a configuração do sistema, sem escrita global silenciosa. Sem ferramentas de shell, execução arbitrária ou acesso a dados privados de outros apps.

## Validação no POCO e desempenho

Não houve acesso físico ao POCO X7 Pro: versão instalada do HyperOS, gestos/multitarefa, biometria, bancos, NFC, notificações, chamadas, jogos, acessibilidade, 120 Hz e autonomia continuam pendentes. Mantenha o POCO Launcher como padrão para preservar os gestos até verificar essa compatibilidade.

O profiling registrado é do emulador Android 15 com SwiftShader, em uma sessão curta e com capturas separadas. Os frames excederam o orçamento de 60 Hz; portanto **não há comprovação da meta de fluidez/desempenho no aparelho**. Consulte os valores brutos em `profiling-emulator.json` e `dumpsys-memory.txt`, sem extrapolá-los para o hardware do POCO. O diagnóstico local pode ser repetido pelo usuário em STATUS.

## Configuração e custos

[Instalação e reversão](INSTALACAO.md), [ATLAS e backend opcional](ATLAS-CONFIGURACAO.md), [componentes dos temas](TEMAS-0.2.0.md). A API requer chave isolada somente no servidor, modelo e preços máximos declarados. Limites padrão conservadores reservam o pior caso antes da chamada e não fazem tentativas automáticas: US$0,10/dia, US$1/mês, 10 chamadas/dia, 100/mês, 20 mil tokens/dia e 200 mil/mês. Esses tetos dependem de preços máximos corretos e não limitam hospedagem ou outros aplicativos usando a mesma chave. ChatGPT oficial não exige chave de API do NEON.

## Testes e assinatura

Os resultados finais, hashes e metadados da rodada aprovada estão em [verification-summary.json](verification-summary.json). Evidências completas acompanham o ZIP da entrega e o artefato do Actions. O relatório anterior foi preservado em [RELATORIO-0.1.1.md](RELATORIO-0.1.1.md).

O release é compilado pelo Actions e assinado localmente com a chave permanente preservada. `applicationId=dev.neonos.poco`, `versionCode=10200`, certificado SHA-256 `973185c207025f9b01339ce1130c85bd1fa6a22d7f652c786ae84d690874774b`. A verificação criptográfica comprova a mesma identidade de assinatura; a instalação real sobre 0.1.1 no POCO ainda não foi exercitada. O job de assinatura no Actions depende de secrets ainda não configurados pelo proprietário; não foi declarado executado.

## Rodada final aprovada

- Actions: [rodada 12](https://github.com/MegaDev13/index.html/actions/runs/42094692849), código `633f6113253cb58915a39fa6afceb93b7c18d8ef`.
- Backend: 12 testes aprovados; Kotlin/JVM: 14 testes, zero erros/falhas.
- Android 15/API 35: **187 verificações aprovadas**, incluindo teclado real aberto, campo/Enviar visíveis, resposta mais recente no viewport, temas, comandos, offline e integração com fixture.
- Reinício real do processo: Dark Terminal e dois favoritos preservados.
- Lint: zero erros; 53 avisos de estilo, legibilidade e uso de APIs. O relatório bruto está no pacote; não foram ocultados avisos.
- A execução Android usa build debug; o release assinado de entrega recebeu verificação de certificado, identificação, permissões e alinhamento.
- Assinatura: mesmo certificado da 0.1.1. APK SHA-256: `82349f29f64752ea3dcde049e5936ee4b61b960b226cf00e622da6674d5b8adb`.
- A atualização final de documentação é posterior ao commit de código testado; não altera os binários ou a implementação validada.
