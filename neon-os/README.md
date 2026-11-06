# NEON OS 0.3.0

Launcher Android nativo em Kotlin para o POCO X7 Pro. O código está na pasta `neon-os/` da branch `neon-os` de `MegaDev13/index.html`.

- **Home HUD** com circuitos, mapa e anel de temperatura real, bateria, calendário, relógio, atalhos laterais e dock circular. O clima usa Open-Meteo após configuração.
- **Home e Grid** com páginas, pastas, arraste, pesquisa, categorias, ordenação, favoritos, atalhos e widgets Android.
- **Icon Studio** com desenhos vetoriais, escolha individual, restauração, importação de imagens e prévia de ZIP. Exporta os ícones originais e identificadores dos apps escolhidos.
- **Wallpaper** com imagem própria por tema, recorte, escurecimento, prévia e aplicação no Android. O papel de parede animado é opcional.
- **Terminal** com eventos reais observados pelo NEON, comandos locais e widget de resumo.
- **ATLAS** com abertura do ChatGPT oficial como padrão e modo integrado opcional. Dark Terminal, Cyberpunk Neon e Corporate Dystopia continuam selecionáveis.

Mantenha o POCO Launcher para conservar a navegação original do HyperOS. A Home completa do NEON é opcional. O modo companion abre a interface NEON como aplicativo e oferece widgets ao launcher atual.

## Compilar
JDK 17, Android SDK 35/build-tools 35.0.0, Gradle 8.13.

```sh
gradle testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest assembleRelease
```

Workflow: `.github/workflows/neon-android.yml` na raiz do repositório. PR roda testes, lint, compilação e teste em emulador API 35. O artefato inclui screenshots reais e diagnóstico do processo. APK release do teste é **unsigned**, a entrega ao usuário recebe assinatura permanente fora do Git. Nunca instale debug sobre a entrega assinada.

## Documentação

- [Personalização, clima e gestos](docs/IMPLEMENTACAO-V2.md)
- [Formato de importação e exportação de ícones](docs/ICON-PACK-FORMAT.md)
- [Instalação e reversão](docs/INSTALACAO.md)
- [Limitações e fontes](docs/COMPATIBILIDADE.md)
- [Relatório de entrega e evidências](docs/RELATORIO.md)
- [Instalação do ChatGPT e backend opcional](docs/ATLAS-CONFIGURACAO.md)

O launcher e os comandos locais digitados funcionam offline. Localização aproximada, estado Bluetooth e microfone são opcionais. A compatibilidade física do POCO é acompanhada no relatório de entrega.
