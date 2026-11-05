# Instalação, teste e reversão

1. Instale somente o APK assinado NEON-OS-0.1.1.apk. A instalação pede autorização normal do Android para a origem do arquivo. Não desative proteções do sistema. APKs `unsigned` e `debug` do CI são para desenvolvimento; não substituem o APK de entrega.
2. Abra NEON OS como aplicativo. Não é necessário torná-lo launcher padrão.
3. Em CONFIG → Guia nativo, siga o modo recomendado: mantenha POCO Launcher; aplique wallpaper; adicione Relógio NEON em Widgets. Ajuste o bloqueio real e a digital pelas opções oficiais do HyperOS.
4. Se quiser testar a Home completa, CONFIG → Testar como launcher padrão. O HyperOS pode exigir botões. O app não força gestos.
5. STATUS → Iniciar medição local. Use o app, volte ao STATUS e exporte JSON. Frames são os da janela NEON, memória é do processo e CPU é percentual de um núcleo lógico. Não é uma medição de bateria nem de latência de autenticação.
6. Para reverter: CONFIG → Restaurar POCO Launcher (ou Configurações → Aplicativos → Apps padrão → Tela inicial). Em Temas, restaure wallpaper/ícones/bloqueio. Remova o widget e desinstale NEON OS se desejar. Dados de outros apps e disposição do POCO Launcher não são migrados nem apagados.

Apenas a permissão normal SET_WALLPAPER é declarada. Não há internet, câmera, coleta biométrica, serviço de acessibilidade, administrador de dispositivo ou serviço permanente. Widgets Android exigem a autorização normal de vinculação do sistema; alguns provedores têm restrições próprias.

Compatibilidade mínima: Android 13 (API 33). A validação inicial cobre Android 15 em emulador. POCO X7 Pro/HyperOS, biometria, NFC, bancos, chamadas, 120 Hz e autonomia exigem testes físicos e não são declarados aprovados por estes testes.

## Atualizações e assinatura
applicationId fixo: dev.neonos.poco. versionCode inicial: 10001; aumente sempre. Certificado esperado em signing-certificate.sha256.
A chave privada permanente não entra no Git. O backup de assinatura deve ser preservado pelo proprietário. Para assinar versões futuras localmente, use o mesmo arquivo neon.jks e senha do backup com o apksigner do Android SDK.
O workflow gera APK release sem assinatura para verificação. A entrega inicial é assinada com a chave privada preservada. A opção de release assinado do workflow exige os secrets NEON_KEYSTORE_B64 e NEON_STORE_PASSWORD; falha se ausentes e valida o certificado para impedir troca acidental de identidade. Não gera chave temporária para uma atualização. O acesso atual do conector não permite configurar esses secrets automaticamente.
