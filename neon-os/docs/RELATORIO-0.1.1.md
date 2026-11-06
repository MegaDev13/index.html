# NEON OS 0.1.1 — relatório de implementação

Entrega de um aplicativo Android nativo funcional, com modo companion recomendado e Home opcional. A interface de bloqueio e a autenticação reais permanecem sob controle do HyperOS. Não é uma ROM ou substituição de SystemUI.

## Escopo e dispositivo

Destino: POCO X7 Pro, 256 GB. A versão instalada do HyperOS não pôde ser verificada remotamente; nenhum aparelho do usuário estava conectado. O app mostra os dados públicos Android/modelo/build e abre Sobre o telefone para confirmação da versão comercial.

Projeto: https://github.com/MegaDev13/index.html/tree/neon-os/neon-os
PR de revisão: https://github.com/MegaDev13/index.html/pull/1
A ferramenta conectada não cria repositórios; foi usada a branch independente autorizada. O index.html original é preservado byte a byte. Nenhum merge para main foi feito.

## Verificação executada

Rodada final do GitHub Actions: [42082353580](https://github.com/MegaDev13/index.html/actions/runs/42082353580), aprovada. Commit testado: `44902eb88d74c799f22a237fd428adf82dd5d0b5`. Artefato: `13442371351`.

- 9 testes unitários: 0 falhas e 0 erros.
- Lint: 0 erros e 7 avisos (APIs desaconselhadas, textos e regras de extração/estilo).
- Android 15/API 35 emulado, perfil Pixel 6: 32 verificações aprovadas. Navegação HOME/GRID/STATUS/CONFIG usa injeção de toque real. Cada captura e as ações principais verificam que a janela NEON está ativa e sem aviso sobreposto.
- Permissões, busca, lançamento de Configurações, cinco efeitos, geração do wallpaper e ciclo de vincular/criar/remover widget exercitados.
- APK debug executado no emulador. APK release compilado no mesmo Actions, assinado depois com a chave privada permanente e verificado pelo apksigner. O release assinado não foi executado em aparelho físico.
- A inspeção das primeiras capturas revelou ANR do Quickstep. O registro da rodada final confirma ANR do launcher do emulador antes da instalação do NEON. O teste restaura a escolha de Home do ambiente e interrompe esse processo apenas no emulador descartável. Esse procedimento não faz parte do aplicativo. As capturas finais foram inspecionadas e estão sem o aviso.

### Profiling real, sem capturas na janela de medição

Emulador com GPU por software SwiftShader, sessão de 2.490 ms: PSS 46.810 KiB (45,7 MiB), CPU do processo 1.695 ms (68,1% de um núcleo lógico), 34 frames, duração média 177,15 ms, pior 235,42 ms. Os 34 frames excederam o orçamento da tela de 60 Hz.

**A meta de fluidez não está aprovada por esses números.** Eles mostram lentidão no ambiente emulado e não podem ser convertidos em previsão de desempenho, bateria, 120 Hz ou latência biométrica do POCO. A medição foi separada das capturas para evitar sua interferência direta. O modo companion preserva o launcher original; o modo instantâneo desliga os efeitos na Home NEON.

Evidências incluídas no pacote: XML dos testes, relatório de lint, instrumentation.txt, profiling-emulator.json, dumpsys-memory.txt, registro de ANR anterior, crash-log do AndroidRuntime, certificado público, hashes e capturas reais. O crash-log coletado ficou vazio; isso não comprova estabilidade global do HyperOS.


## Funcionalidades implementadas

| Funcionalidade | Implementação e limite | Evidência |
|---|---|---|
| Companion para launcher original | App normal, wallpaper AMOLED exclusivo, widget de relógio; não solicita mudança de launcher ao abrir | UI real e fontes MainActivity/NeonWidget |
| NEON HOME | Hora/data locais, bateria real, favoritos por componente/perfil, apps e widgets | UI no emulador; favoritos implementados, sem teste completo de edição em aparelho |
| NEON GRID | LauncherApps, perfis Android visíveis, pesquisa sem acentos, filtros pelas categorias declaradas, abertura e informações | Testes de pesquisa e lançamento de Configurações no emulador |
| NEON STATUS | Bateria, partição de dados, Android/build, taxas da tela, memória PSS e CPU do processo, FrameMetrics | JSON real de profiling em emulador; nenhuma métrica inventada |
| NEON CONFIG | Quatro paletas, fonte, duração 200–350 ms, brilho/intensidade, som opcional, instantâneo e economia | UI e testes de política; alterações específicas de cada controle não foram todas testadas automaticamente |
| Cinco efeitos | Neural Link, Ghost Protocol, Neon Pulse, Cyber Matrix e Quantum Gate em Canvas acelerado pela janela | Renderização e capturas no Android emulado |
| Sem atraso artificial | Efeito não clicável; abrir app cancela o efeito; não há espera antes de lançar atividades | Implementação; latência física não medida |
| Wallpaper | Geração vetorial nativa em 1220×2712; aplicação no bloqueio/inicial e exportação PNG | PNG gerado pelo mesmo código; aplicação no HyperOS depende do aparelho |
| Economia | Sem animação contínua, câmera, serviço permanente, rede ou telemetria; efeitos param em onPause | Manifesto e código; autonomia física não medida |

## Implementações parciais ou que exigem validação específica

| Recurso | O que funciona no código | O que não foi comprovado |
|---|---|---|
| Widgets de terceiros | AppWidgetHost, autorização Android, configuração do provedor, persistência, remoção e limpeza de IDs | Provedores proprietários Xiaomi, redimensionamento avançado, todos os caminhos de configuração/cancelamento e perfis de trabalho |
| Pacotes de ícones | Descoberta ADW e leitura appfilter.xml, cache e fallback ao ícone original com moldura NEON | Pacotes externos reais e formatos proprietários; não há um pack completo de ícones redesenhados |
| Atalhos fixados | Confirma PinItemRequest válido, guarda componente/perfil e chama LauncherApps.startShortcut | Fluxo completo com aplicativos externos; widgets solicitados por requestPinAppWidget não são aceitos pela Home NEON |
| Pós-desbloqueio | Usa USER_PRESENT ou estado público de keyguard liberado após bloqueio observado; somente na Home NEON visível | Entrega de eventos/lifecycle na build física do POCO; nenhum efeito global quando outro app volta à frente ou processo reinicia |
| 120 Hz | Solicitação opcional de até 120 Hz, sem forçar modo/resolução; taxa ativa exibida | O HyperOS pode limitar. Não foi medido em tela física 120 Hz |
| Assinatura automática futura | Workflow de release valida certificado permanente e falha se secrets ausentes | Secrets não configuráveis pelo conector atual; configuração pelo proprietário ainda necessária |
| Compatibilidade original | Não toca autenticação, SystemUI, dados de terceiros ou componentes de pagamento | NFC, bancos, biometria, chamadas, câmera, jogos, gestos e atualizações exigem teste no aparelho |

## Limites do HyperOS / não implementado por API pública

- Substituir a animação real do leitor de digital ou do reconhecimento facial.
- Saber, pelo evento público de desbloqueio, se foi usada digital, rosto ou PIN.
- Personalizar automaticamente o relógio/atalhos/notificações do bloqueio real ou injetar AOD próprio.
- Garantir gestos e animações de multitarefa originais com launcher externo no HyperOS. Recomenda-se manter POCO Launcher.
- Importação de MTZ personalizado, profundidade real do bloqueio, tema completo de todos os aplicativos e modificação das barras/painel de notificações. Nenhum tema não validado foi instalado.

O modo companion aplica o wallpaper e orienta a escolha de relógio, notificações, digital e AOD pelos controles oficiais. A página oficial do POCO X7 Pro descreve AOD com até 10 segundos; confirmar comportamento da build instalada. As fontes e distinções entre confirmação específica do X7 Pro e exemplos de outros aparelhos estão em COMPATIBILIDADE.md.

## Instalação e reversão

Instale o APK assinado de entrega, abra o app e comece por CONFIG → Guia nativo. Para manter gestos, deixe o POCO Launcher como Home. Para experimentar a Home NEON, use a seleção explícita em CONFIG após ler a limitação.

Reverta em CONFIG → Restaurar POCO Launcher e restaure o tema/wallpaper em Temas. Não é necessário apagar dados de outros apps. Os layouts do launcher original não são importados nem apagados. O backup de assinatura é privado e separado da entrega pública.

## Critérios ainda pendentes no POCO físico

Confirmar HyperOS; aplicar e reverter wallpaper; adicionar o relógio no POCO Launcher; conferir bloqueio, notificações e atalhos; verificar gestos/Recentes antes de optar pela Home; testar cada autenticação original; abrir bancos/NFC/câmera/chamadas/jogos; exportar diagnóstico; comparar bateria e estabilidade durante uso normal. Esses itens não são apresentados como aprovados.

## Integridade do APK de entrega

SHA-256: `2eb137696c5ff3c97c728a6dd919c3eaf93f7eb09ef810aca8d1efbf5b17ac1c`.

Certificado SHA-256: `973185c207025f9b01339ce1130c85bd1fa6a22d7f652c786ae84d690874774b`.
