# NEON OS 0.3.1 • abertura da gaveta por gesto

Ao arrastar para cima no HUD, a Home sobe e a gaveta entra por baixo acompanhando o dedo. Ela é preparada antes do gesto. A busca e os controles ocupam menos espaço para expor os ícones mais cedo. Soltar após um gesto curto volta à Home. Um arraste suficiente ou um gesto rápido para cima conclui a abertura.

A atualização tem o mesmo pacote Android e o mesmo certificado da 0.3.0. Instale o APK assinado por cima da versão anterior para preservar os dados. O versionCode passou de 10300 para 10301. A instalação sobre a versão anterior e a fluidez no POCO físico ainda precisam ser conferidas no aparelho.

## Verificação

- O teste no Android 15 conferiu dois pontos do movimento antes da soltura e confirmou que os ícones já apareciam antes da soltura. A gaveta também acompanhou a inversão do movimento.
- Um gesto curto fechou a gaveta. Um gesto completo abriu a mesma gaveta sem montar outra tela na soltura.
- 243 verificações Android, 21 testes Kotlin/JVM e 12 testes do backend passaram. Lint com zero erros e 77 avisos.
- O GitHub Actions compilou o release em `f15c71dfc3a91d03772b0a44fd2554575d058dfe`. A assinatura foi feita localmente com a chave da 0.3.0 e verificada com apksigner.
- SHA-256 do APK `bd1772b534061c51ea64d8ae1c89df118d084928abe82a892e7f9b79993f5037`.

[Execução do Actions](https://github.com/MegaDev13/index.html/actions/runs/42146509699) · [Artefato da compilação](https://github.com/MegaDev13/index.html/actions/runs/42146509699/artifacts/13470517586) · [Guia de instalação](INSTALACAO.md)
