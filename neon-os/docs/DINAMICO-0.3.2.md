# NEON OS 0.3.2 • gestos e personalização

- Deslize para cima no HUD para abrir a gaveta. Puxe para baixo no topo da gaveta para voltar à Home. Se a gaveta estiver rolada, volte ao início antes de fechar. Comece o gesto dentro do launcher, abaixo da barra do Android.
- Deslize para a esquerda ou direita para passar entre Home, Grid, Status e Config. ATLAS abre pelo botão. As páginas de atalhos da Home continuam nos botões numerados.
- Toque em GRID para abrir com a mesma transição vertical. Em movimentos curtos, a aba retorna à posição inicial. Economia de energia e a opção de animações do Android são respeitadas.
- O anel do clima gira em torno da temperatura. A tela Status mostra bateria, armazenamento, rede, atividades de apps, clima disponível e frequência da tela.
- Em CONFIG → NEON ICON STUDIO, selecione os apps e toque em Exportar inventário. O ZIP só é oferecido ao seletor depois de preparado, com `apps.json` e as imagens. Se um ícone original estiver indisponível, o manifesto identifica a imagem padrão.
- Em CONFIG → Ativar teclado NEON, habilite-o no Android. Em Escolher teclado, selecione Teclado NEON. O teclado acompanha o tema, oferece letras, números, símbolos e acentos por pressão longa. Os caracteres seguem para o campo em foco. Campos com teclado próprio e algumas telas protegidas podem usar o método do sistema.
- Em CONFIG → Bloqueio NEON, escolha e teste Cadeado abrindo, Varredura ocular ou Análise digital. Aplique a arte à tela de bloqueio nativa. Ao voltar à Home NEON após um desbloqueio observado, o efeito começa com a mesma arte. O HyperOS controla o enquadramento, a hora, o PIN, as notificações e a biometria. Os desenhos são decorativos e independem do método real de autenticação.

## Terminal do launcher

| Comando | Efeito |
| --- | --- |
| `open WhatsApp` | Abre o aplicativo instalado. Pode usar nome ou pacote. Em caso de ambiguidade, escolhe o perfil em um diálogo. |
| `volume` / `volume 50` | Consulta ou ajusta o volume de mídia. |
| `silenciar` / `dessilenciar` | Silencia ou restaura o canal de mídia. |
| `brilho 42` / `brilho auto` | Ajusta o brilho nas janelas Home e Terminal do NEON ou devolve o controle ao Android. |
| `brilho sistema` | Abre os ajustes de tela do Android para alterar o brilho global. |

Para um shell real, instale e pareie o [NEON Bridge](../bridge/README.md). O servidor OpenSSH roda sob a conta do Termux. O adaptador MCP expõe apenas as consultas documentadas. Instalação, pareamento e acesso ao POCO físico precisam ser feitos com o aparelho conectado.

## Evidências

- 284 verificações Android 15, 21 testes Kotlin/JVM e 12 testes do backend passaram. Lint com zero erros e 85 avisos.
- O teste do inventário passou pelo botão Exportar e forneceu um destino de teste ao callback do seletor. O ZIP resultante foi reaberto e teve os registros e PNGs verificados. O exemplo do pacote contém apps do emulador.
- Os testes confirmaram a volta pelo topo do Grid, a transição do botão, a navegação horizontal sem ATLAS, a rotação do anel, a digitação de letra e espaço pelo teclado NEON, o wallpaper nativo de bloqueio, a abertura de Configurações por `open` e o brilho da janela. O evento biométrico real e a continuidade visual ainda precisam de conferência no POCO.
- O teste local do bridge validou mensagens MCP e rejeição de entradas fora da lista, com transporte SSH simulado. O pareamento real permanece pendente.
- O APK foi assinado com o mesmo certificado da 0.3.1, com `versionCode` 10302 e pacote `dev.neonos.poco`. SHA-256 `ad485f3736de3d263d6705aeb00d5444e0c847c803e1d2f5c57e41f185707447`.
- [Execução do GitHub Actions](https://github.com/MegaDev13/index.html/actions/runs/42216679784) · [Artefato de desenvolvimento](https://github.com/MegaDev13/index.html/actions/runs/42216679784/artifacts/13501741913)
