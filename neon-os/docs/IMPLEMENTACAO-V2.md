# NEON OS 0.3.1 • controles e escopo

A Home aproxima o HUD da referência com molduras angulares, circuitos discretos, bateria e data no topo, silhueta mundial, anel de temperatura, relógio central, atalhos laterais e dock circular. Os três temas têm geometria, tipografia e desenhos próprios. As capturas da entrega são da interface Android executada pelo workflow.

| Controle | Uso |
| --- | --- |
| CONFIG → HUD / CLIMA | Ative clima, escolha cidade ou autorize localização aproximada. Ajuste mapa, anel, hora, escala do HUD e atualização. Toque no indicador para consultar dados e origem. |
| CONFIG → ORGANIZAR HOME / GESTOS | Ajuste páginas, pastas, edição por arraste, acesso a widgets e ação de dois toques. |
| Home | Arraste para cima no HUD para revelar a gaveta conforme move o dedo. Soltar após um gesto curto volta à Home. Deslize para baixo para pesquisar e para os lados para trocar de página. A pinça abre a organização. |
| Páginas de apps | No modo de edição, pressione um app e arraste sobre outro para ordenar ou sobre um número para mover. Crie pastas no menu de organização. |
| Dock | Pressione prolongadamente um botão para escolher o atalho, quando disponível. ATLAS também tem acesso ao painel por pressão longa. |
| GRID → ORDENAR / GRADE / OCULTOS | Ajuste colunas, ordenação e visibilidade. Favoritos e páginas são independentes do tema. |
| CONFIG → WALLPAPER PRÓPRIO | Importe PNG, JPEG ou WebP, enquadre e escureça. Salve na Home por tema ou confirme aplicação pelo Android. O animado abre a prévia do sistema. |
| CONFIG → NEON ICON STUDIO | Escolha ícone original, desenho NEON ou imagem por app. Importe ZIP com prévia ou exporte inventário selecionado. |
| CONFIG → TERMINAL / EVENTOS REAIS | Consulte observações do NEON. Use `help` para ver comandos locais. O widget mostra um resumo local. |
| CONFIG → MODO DE DESEMPENHO | Selecione animação completa, equilibrada ou econômica. Diagnóstico em STATUS. |

Os valores do clima são consultados no Open-Meteo. O HUD informa quando há cache; se não houver observação válida, exibe estado indisponível. O atualizador só trabalha com o launcher visível. O mapa é esquemático e indica a região com marcador.

O terminal mantém até 120 observações em memória. Mostra inicialização, tema, bateria, rede, clima, abertura de aplicativos, ações ATLAS e alterações observadas pelo NEON. Os comandos são locais e declarados no app. A lista não inclui registros globais do Android ou conteúdo privado de outros apps.

## HyperOS e recursos dependentes do sistema

A tela de bloqueio real, a digital, o reconhecimento facial, as notificações e o AOD seguem as opções do HyperOS. O NEON ACCESS exibe transições universais após o desbloqueio observado na Home NEON. Seu efeito não depende de qual credencial foi usada.

Os gestos do HUD pertencem ao NEON. A troca de launcher padrão pode mudar a navegação global conforme a versão do HyperOS. Confirme a versão em **Configurações → Sobre o telefone** e use **Restaurar POCO Launcher** para voltar à configuração nativa. Dois toques têm ações da Home e podem abrir as configurações de tela. O bloqueio imediato exigiria poderes adicionais no Android.

O wallpaper animado usa um serviço hospedado pelo Android enquanto estiver selecionado. Pausa quando invisível e usa um quadro estático no modo econômico. Se o HyperOS limitar a escolha para a tela de bloqueio, aplique a imagem pelo gerenciador oficial de Temas.

A busca de atualizações é manual e procura releases com tag `neon-os-` no GitHub. Quando o repositório disponibilizar uma release com APK, o painel oferece o link. A instalação usa a confirmação do Android. O APK da entrega também pode ser atualizado manualmente por cima, com o certificado anterior.

[Open-Meteo — documentação da previsão](https://open-meteo.com/en/docs) · [Android — WallpaperManager](https://developer.android.com/reference/android/app/WallpaperManager) · [Android — DevicePolicyManager](https://developer.android.com/reference/android/app/admin/DevicePolicyManager#lockNow())
