# NEON OS 0.3.0 • relatório de verificação

Código testado em [`e729a76c0d4e`](https://github.com/MegaDev13/index.html/commit/e729a76c0d4e47cfeca7b13387008a8a647553dd). [Rodada 20 do Actions](https://github.com/MegaDev13/index.html/actions/runs/42140986740) · [APK de desenvolvimento e evidências](https://github.com/MegaDev13/index.html/actions/runs/42140986740/artifacts/13467283308).

## Resultado

- Android 15/API 35 com 233 verificações aprovadas. O ADB reiniciou o processo e verificou o tema Dark Terminal e dois favoritos persistidos.
- O Actions compilou os APKs de debug, instrumentação e release. Os 21 testes Kotlin/JVM passaram. O lint registrou zero erros e 77 avisos. O log de falhas ficou vazio e o Android não registrou ANR na sessão.
- 12 testes do backend executados. O modo integrado foi exercitado com servidor de teste e resposta marcada como fixture.
- O release da rodada foi assinado localmente com a chave permanente. O apksigner verificou a assinatura v3 e confirmou o mesmo certificado do APK 0.2.0. Os arquivos originais dentro do APK assinado correspondem ao binário do Actions. Consulte [os resultados e hashes](verification-summary.json).

## Implementação

| Área | Entrega |
| --- | --- |
| Home HUD | Bateria, calendário, mapa, leitura meteorológica no mapa e no anel, relógio, atalhos e dock. A rodada 20 capturou 26 °C para Capim Grosso, Bahia, de uma consulta do emulador ao Open-Meteo. O artefato inclui a resposta meteorológica bruta. |
| Temas | Cyberpunk Neon, Dark Terminal e Corporate Dystopia selecionáveis. Dark Terminal tem estilos próprios para Home, dock, Grid, busca, ícones, widgets NEON, relógio, Status, Config e ATLAS. |
| Organização | Gaveta com pesquisa e categorias, páginas, pastas e ordem persistente. A instrumentação exercitou gestos de paginação e gaveta por injeção Android. O arraste foi verificado com a sequência MotionEvent entregue à Activity no emulador, com conferência da ordem gravada. |
| Ícones | Desenhos vetoriais e monogramas, troca por app, prévia, exportação e importação ZIP, restauração e bloqueio de caminhos inseguros. |
| Wallpaper | Imagem própria por tema, prévia, recorte, escurecimento, aplicação via Android e animação opcional. |
| Terminal | Eventos observados pelo NEON, comandos locais permitidos e widget. |
| ATLAS | ChatGPT oficial por padrão e opção integrada. Comandos locais, conexão e estado sem configuração. O Android testou a ausência do app oficial, o teclado, o estado offline e o fluxo autenticado com fixture. |

As imagens de interface são capturas do Android. Os nomes principais incluem `v2-hud-map-weather.png`, `v2-gesture-drawer.png`, `v2-folder.png`, `v2-icon-pack-preview.png`, `v2-wallpaper-preview.png`, `dark_terminal-home.png`, `dark_terminal-grid.png` e `dark_terminal-atlas.png`. As capturas da entrega vêm do [artefato da rodada 20](https://github.com/MegaDev13/index.html/actions/runs/42140986740/artifacts/13467283308).

## Validação pendente

Versão física do HyperOS, gestos globais e multitarefa, abertura do ChatGPT genuíno, voz, biometria, bancos, NFC, chamadas, notificações, 120 Hz, autonomia e instalação sobre a 0.2.0. O emulador registrou 1.746 ms de partida após encerrar o processo e PSS de 46,7 MiB. Na amostra de efeitos, os 39 quadros ultrapassaram o orçamento de 60 Hz no SwiftShader. A fluidez no aparelho ainda precisa ser medida. O Android continua responsável pelo bloqueio e pela autenticação.

O modo integrado depende de hospedagem HTTPS, login e chave OpenAI no servidor. Voz Realtime, WebRTC, hotword e sincronização do histórico do ChatGPT estão fora desta entrega. O NEON mantém a seleção opcional de launcher. Veja [instalação e reversão](INSTALACAO.md), [personalização](IMPLEMENTACAO-V2.md), [formato dos ícones](ICON-PACK-FORMAT.md) e [configuração opcional da API](ATLAS-CONFIGURACAO.md).

[Relatório da versão 0.2.0](RELATORIO-0.2.0.md)
