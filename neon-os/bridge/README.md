# NEON Bridge

O servidor é o OpenSSH executado no Termux. O APK do launcher mantém seu próprio terminal de comandos locais. Use o bridge quando quiser uma sessão de shell real no ambiente privado do Termux ou diagnósticos por MCP.

## Parear com um computador

1. Instale [Termux](https://github.com/termux/termux-app#installation) de uma fonte oficial. Abra o Termux e execute `pkg install openssh`. Copie `start-termux.sh` para o diretório privado do Termux.
2. No computador, crie uma chave exclusiva com `ssh-keygen -t ed25519 -f ~/.ssh/neon_phone`. Transfira **só** o arquivo `neon_phone.pub` ao Termux. No telefone, rode `bash start-termux.sh ~/neon_phone.pub`. O servidor imprime o usuário e a chave pública do aparelho.
3. Com o cabo USB, autorize o computador na depuração USB do Android e rode `adb devices`. Para o aparelho autorizado, execute `adb -s SERIAL forward tcp:38222 tcp:38222`, trocando SERIAL pelo identificador de `adb devices`. O SSH do telefone escuta em 127.0.0.1. [Documentação ADB](https://developer.android.com/tools/adb)
4. Acrescente a chave pública exibida no telefone a `~/.ssh/known_hosts` com o alias `neon-phone`. A linha tem o formato `neon-phone ssh-ed25519 BASE64`, usando o tipo e o conteúdo da chave do aparelho. Configure o host abaixo com o usuário que o Termux mostrou.

```sshconfig
Host neon-bridge
    HostName 127.0.0.1
    Port 38222
    User u0_a123
    IdentityFile ~/.ssh/neon_phone
    IdentitiesOnly yes
    HostKeyAlias neon-phone
    StrictHostKeyChecking yes
```

Use `ssh neon-bridge` para o shell interativo. A sessão usa a conta do Termux e as permissões que ela recebeu no Android.

## Cliente MCP

Em um cliente compatível com servidores locais stdio, no computador que executa o encaminhamento ADB e tem o SSH configurado, registre:

```json
{
  "mcpServers": {
    "neon-bridge": {
      "command": "python3",
      "args": ["/caminho/neon_mcp.py"]
    }
  }
}
```

A ferramenta `neon_diagnostic` aceita `android`, `kernel`, `uptime` e `storage`. O shell interativo fica no cliente SSH, sob o controle do operador. Se a chave privada tiver frase secreta, carregue-a no agente SSH da estação antes de chamar o MCP.

Para encerrar, saia das sessões SSH, pressione Ctrl+C no Termux e execute `adb -s SERIAL forward --remove tcp:38222`. Desative a depuração USB no telefone quando terminar. Esta conversa só consegue consultar o aparelho quando um executor com o encaminhamento autorizado tiver acesso à porta local ou quando o cliente MCP correspondente estiver conectado.
