# Atualização 0.2.0 — ATLAS e três temas

Continuação da base 0.1.1. Novos temas: Cyberpunk Neon, Dark Terminal e Corporate Dystopia. ATLAS abre o ChatGPT oficial por padrão e oferece um painel integrado opcional com backend autenticado independente.

- [ATLAS: instalação, API, voz e limites](docs/ATLAS-CONFIGURACAO.md)
- [Componentes dos três temas](docs/TEMAS-0.2.0.md)
- [Relatório atual](docs/RELATORIO.md)

Abertura do ChatGPT genuíno e compatibilidade física do POCO exigem o aparelho. O teste Android/backend usa fixture explicitamente identificada; uma resposta OpenAI real exige chave configurada pelo proprietário.

# NEON OS — Cyberpunk Edition

Android nativo em Kotlin para POCO X7 Pro. Projeto independente nesta subpasta, mantido na branch `neon-os` de `MegaDev13/index.html` conforme alternativa autorizada. O site original não é alterado.

**Comece no modo companion:** mantém o POCO Launcher e os gestos; oferece relógio como widget, wallpaper original AMOLED e guia de configuração nativa. A Home completa é opcional e informa a restrição de gestos do HyperOS antes da escolha padrão.

- HOME: relógio local, bateria real, favoritos, atalhos fixados e widgets Android.
- GRID: apps lançáveis dos perfis visíveis, pesquisa sem acentos, categorias publicadas pelos próprios apps, informações e favoritos por pressão longa.
- STATUS: modelo, Android/build, bateria, partição de armazenamento, Hz disponíveis e diagnóstico local de memória/CPU/frames.
- CONFIG: paleta, fonte, efeitos, som opcional, economia, pacotes de ícones appfilter, wallpaper, widgets, seleção de launcher e reversão.
- ACCESS: cinco transições vetoriais universais; não substituem nem simulam autenticação. Veja limites em COMPATIBILIDADE.md.

## Compilar
JDK 17, Android SDK 35/build-tools 35.0.0, Gradle 8.13.

```sh
gradle testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest assembleRelease
```

Workflow: `.github/workflows/neon-android.yml` na raiz do repositório. PR roda testes, lint, compilação e teste em emulador API 35. O artefato inclui screenshots reais e diagnóstico do processo. APK release do teste é **unsigned**, a entrega ao usuário recebe assinatura permanente fora do Git. Nunca instale debug sobre a entrega assinada.

## Documentação
- [Instalação e reversão](docs/INSTALACAO.md)
- [Limitações e fontes](docs/COMPATIBILIDADE.md)
- [Relatório de entrega e evidências](docs/RELATORIO.md)
- [Instalação do ChatGPT e backend opcional](docs/ATLAS-CONFIGURACAO.md)

Não declara compatibilidade física sem teste no aparelho. Sem câmera, serviço de acessibilidade, overlay de bloqueio ou serviço permanente. A versão 0.2.0 acrescenta internet/estado de rede para o ATLAS integrado opcional e microfone por acionamento manual com autorização. O launcher e os comandos locais digitados continuam funcionando offline.
