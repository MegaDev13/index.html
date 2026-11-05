# NEON OS — compatibilidade e escolhas

A: POCO Launcher + wallpaper exclusivo + widget NEON é o modo recomendado para preservar os gestos originais. Relógio do bloqueio, profundidade, AOD e animação de digital são ajustados manualmente em Temas/Personalização do HyperOS. Nenhum componente de sistema é alterado.

B: o app também é uma Home Android nativa opcional. HyperOS pode exigir botões de navegação. Não há promessa de gestos, multitarefa ou animação de fechamento idênticos ao launcher de fábrica. O app explica isso antes de abrir a seleção de Home. Nenhum ADB, root, acessibilidade ou sobreposição é usado para contornar a restrição.

A versão instalada do HyperOS só pode ser confirmada no aparelho em Configurações → Sobre o telefone. NEON STATUS mostra Android, modelo e Build.DISPLAY; não usa APIs ocultas para inferir versão comercial.

## NEON ACCESS
Cinco desenhos vetoriais reais, de 200–350 ms, ajustáveis. Usam uma View não clicável sobre a Home NEON: aplicativos continuam recebendo toques. Economia de bateria e animações do sistema desligadas desabilitam o efeito. Modo instantâneo disponível. Som opcional usa fluxo do sistema e respeita modo silencioso.

O evento público USER_PRESENT não revela o método de autenticação. O efeito é universal: nenhuma indicação de rosto/digital/PIN. É exibido apenas depois de bloqueio observado, evento do sistema e Home NEON visível. Sem serviço em segundo plano, eventos podem ser perdidos se o processo for encerrado, se o app não estiver ativo ao bloquear ou se o HyperOS não entregar o evento. Não há efeito sobre outros apps ou sobre o bloqueio real. A prévia na configuração é apenas uma prévia.

## Referências primárias consultadas em 05/11/2026
- https://github.com/LawnchairLauncher/docs/blob/main/help/troubleshooting/gesture-navigation-issues.md — documentação do próprio launcher sobre restrições Xiaomi.
- https://www.mi.com/uk/support/faq/details/KA-527946/ — POCO X7 Pro: AOD nativo com máximo de 10 s segundo a página oficial; confirmar diferenças da build instalada.
- https://www.mi.com/global/support/faq/details/KA-533481/ — indicador de digital oficial no bloqueio do POCO X7 Pro.
- https://www.mi.com/global/support/faq/details/KA-503758/ — exemplo oficial de configuração de efeitos, Xiaomi 14T Pro; não comprova quais efeitos existem na build do X7 Pro.
- https://developer.android.com/reference/android/content/Intent#ACTION_USER_PRESENT
- https://developer.android.com/reference/android/app/KeyguardManager
- https://developer.android.com/develop/ui/views/appwidgets/host
- https://developer.android.com/reference/android/content/pm/LauncherApps
- https://developer.android.com/reference/android/app/WallpaperManager

Não foi produzido MTZ: a instalação de temas personalizados depende da região, permissões e formato aceito pelo HyperOS; não foi possível validar no aparelho. Não há suporte público para trocar SystemUI, interface de apps bancários, leitor de digital, reconhecimento facial ou animações protegidas.
