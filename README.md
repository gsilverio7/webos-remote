# Controle Remoto TV LG (Android Nativo em Kotlin)

Aplicativo Android nativo desenvolvido em **Kotlin + Jetpack Compose** para controle direto de Smart TVs **LG webOS** na rede Wi-Fi local (sem depender do servidor no PC).

## Principais Recursos

- **Design de Controle Remoto Físico Realista:**
  - Chassi em preto fosco com chanfro metálico, visor OLED de status e LED infravermelho no topo.
  - Teclas físicas 3D em cinza escuro (`#32343B` → `#1E2025`) com afundamento mecânico ao toque e resposta tátil (vibração curta).
  - **Botão Power Vermelho Unificado:** Um único botão circular vermelho em destaque que **liga** a TV via *Wake-on-LAN* (quando em standby) e **desliga** a TV via *SSAP* (`ssap://system/turnOff`) quando ligada.
  - **D-Pad Circular Esculpido:** Anel direcional físico (Cima, Baixo, Esquerda, Direita) com sulcos diagonais e botão central **OK**.
- **Comunicação Direta com a TV LG:**
  - **SSAP WebSocket** seguro (`wss://<IP>:3001` com suporte a certificado autoassinado da LG e fallback para `ws://<IP>:3000`).
  - **Pointer Input Socket** (`getPointerInputSocket`) para navegação rápida e responsiva.
  - **Descoberta Automática SSDP** (`239.255.255.250:1900`) para localizar o IP da TV LG na rede.
  - **Atalhos de Apps e Canais Ao Vivo (3x3):** Prime Video, HBO Max, Disney+, Globoplay, SBT Ao Vivo (com resolução dinâmica da live no YouTube), Jovem Pan (LG Channels), IPP TV Ao Vivo, YouTube e Moonlight.

## Como Abrir e Compilar

1. Abra o **Android Studio** e selecione **Open** apontando diretamente para a pasta `mobile/`.
2. O Android Studio sincronizará o projeto Gradle e baixará o wrapper/dependências automaticamente.
3. Conecte seu celular Android (ou emulador na mesma rede Wi-Fi) e clique em **Run (`▶`)**.
