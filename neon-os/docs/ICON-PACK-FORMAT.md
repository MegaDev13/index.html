# NEON Icon Studio

Em **CONFIG → NEON ICON STUDIO**, abra um app para alternar entre ícone original, desenho NEON ou imagem selecionada no Android. As escolhas são gravadas por tema e componente do aplicativo. Os desenhos NEON têm variantes Cyberpunk Neon, Dark Terminal e Corporate Dystopia. Apps sem associação específica recebem um monograma.

## Enviar a lista de apps para redesenho

1. Em **Exportar inventário**, selecione até 250 apps e salve o ZIP.
2. Compartilhe o ZIP com quem for desenhar. Ele inclui os nomes e componentes escolhidos, o perfil Android, o tema, o estado do ícone e as imagens originais.
3. Ao receber um pacote de desenhos, use **Importar pacote ZIP** e confira a prévia. Escolha os desenhos que quer aplicar. O Studio permite restaurar o conjunto de escolhas anterior.

O formato de exportação é `NEON_ICON_EXPORT/apps.json`. A exportação ocorre no aparelho pelo seletor de arquivos. Revise o inventário antes de compartilhá-lo, pois ele revela os nomes dos apps selecionados.

## Preparar um pacote para importar

Na raiz do ZIP, inclua `NEON_ICON_PACK/manifest.json` em UTF-8 e as imagens indicadas no manifesto sob `NEON_ICON_PACK/icons/`.

```json
{
  "version": 1,
  "icons": [
    {
      "label": "WhatsApp",
      "package": "com.whatsapp",
      "theme": "dark_terminal",
      "file": "icons/whatsapp-verde.png"
    },
    {
      "label": "Telegram",
      "package": "org.telegram.messenger",
      "theme": "all",
      "file": "icons/telegram.png"
    }
  ]
}
```

- `package` é o identificador do app. Use `component` do inventário para limitar a entrada a uma atividade específica.
- `theme` aceita `cyberpunk_neon`, `dark_terminal`, `corporate_dystopia` ou `all`. Sem o campo, a entrada vale nos três temas.
- Use PNG, JPEG ou WebP, dimensões de 16 a 2048 px e até 2 MiB por imagem. Prefira 256 × 256 com transparência para manter a proporção na normalização.
- Limites de 750 desenhos, 1000 entradas no ZIP, manifesto de 256 KiB e 64 MiB descompactados. Duplicatas e caminhos fora da estrutura são rejeitados.
- Mapas por pacote/componente ficam disponíveis para apps instalados depois. As escolhas individuais feitas no Studio têm precedência sobre o desenho padrão.
