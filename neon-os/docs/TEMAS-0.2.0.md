# Temas reais — NEON OS 0.2.0

CONFIG oferece Cyberpunk Neon, Dark Terminal e Corporate Dystopia. A seleção é persistida em SharedPreferences, sem alterar favoritos, IDs de widgets ou disposição dos apps. A Home é reconstruída no próprio processo; não exige reiniciar aparelho ou limpar dados. O comando local "Mude para Dark Terminal" produz a mesma seleção. As propostas vindas da IA usam a mesma lista de IDs e pedem confirmação.

| Componente | Dark Terminal |
|---|---|
| Home e wallpaper | Preto AMOLED, eixos discretos, identidade SYS://NEON e sessão local |
| Dock | Cinco destinos com fonte monoespaçada, ATLAS e estados em verde |
| Gaveta | Mesma lista de apps/perfis, molduras retas e tipografia monoespaçada |
| Pesquisa | Campo retangular, prompt grep e cores de terminal |
| Ícones | Conversão monocromática verde preservando contraste do desenho original |
| Widgets | Layouts RemoteViews próprios para Relógio e ATLAS, com cantos retos e fontes mono |
| Relógio | Fonte mono, cor #D6E5DF, hora/data reais |
| Indicadores | Bateria e dados reais, rótulos técnicos, contraste cinza #74818A |
| Configuração | Botões/painéis, fontes e campos derivados dos tokens do tema |
| ATLAS | Núcleo verde, campos, mensagens, controles e estado de rede no mesmo tema |

Paleta exata: #000000, #00FF88, #00B86B, #74818A e #D6E5DF. Não há animação ociosa de cursor/texto nesta versão. Os cinco efeitos de acesso existentes usam o acento do tema e continuam opcionais.

Cyberpunk Neon conserva a identidade anterior e a personalização de acento. Corporate Dystopia utiliza preto, painéis cinza industriais, âmbar, fontes condensadas, ícones dessaturados e HUD próprio. O launcher não redesenha o conteúdo interno de apps de terceiros nem substitui o bloqueio do HyperOS. Widgets de outros fornecedores mantêm o desenho próprio do fornecedor.

Os testes instrumentados verificam a existência das telas, as cores/fontes de componentes, filtros de ícones, os dois widgets, preservação de favoritos/IDs e fechamento/reabertura real da Activity. Reboot físico, gestos, apps bancários, NFC e biometria continuam dependendo de teste no POCO.
