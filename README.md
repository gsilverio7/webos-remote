# Controle Remoto TV LG (Android Nativo em Kotlin)

Aplicativo Android nativo desenvolvido em **Kotlin + Jetpack Compose** para controle direto de Smart TVs **LG webOS** na rede Wi-Fi local (sem depender do servidor no PC).

## Principais Recursos

- **Design de Controle Remoto Físico Realista:**
  - Chassi em preto fosco com chanfro metálico, visor OLED de status e LED infravermelho no topo.
  - Teclas físicas 3D em cinza escuro (`#32343B` → `#1E2025`) com afundamento mecânico ao toque e resposta tátil (vibração curta).
  - **Botão Power Vermelho Unificado:** Um único botão circular vermelho em destaque que **liga** a TV via _Wake-on-LAN_ (quando em standby) e **desliga** a TV via _SSAP_ (`ssap://system/turnOff`) quando ligada.
  - **D-Pad Circular Esculpido:** Anel direcional físico (Cima, Baixo, Esquerda, Direita) com sulcos diagonais e botão central **OK**.
- **Comunicação Direta com a TV LG:**
  - **SSAP WebSocket** seguro (`wss://<IP>:3001` com suporte a certificado autoassinado da LG e fallback para `ws://<IP>:3000`).
  - **Pointer Input Socket** (`getPointerInputSocket`) para navegação rápida e responsiva.
  - **Descoberta Automática SSDP** (`239.255.255.250:1900`) para localizar o IP da TV LG na rede.
  - **Atalhos de Apps e Canais Ao Vivo (3x3):** Prime Video, HBO Max, Disney+, Globoplay, SBT Ao Vivo (com resolução dinâmica da live no YouTube), Jovem Pan (LG Channels), IPP TV Ao Vivo, YouTube e Moonlight.

## Como Abrir e Compilar

### Opção 1: Pelo Android Studio (Interface Gráfica)

1. Abra o **Android Studio** e selecione **Open** apontando para a pasta raiz do projeto (`webos-remote`).
2. O Android Studio sincronizará o projeto Gradle automaticamente.
3. No seletor de dispositivos no topo, escolha o emulador (ex: `Pixel 5`) ou seu celular físico.
4. Clique no botão de **Run (`▶`)** ou pressione `Shift + F10`.

---

### Opção 2: Pelo Terminal (PowerShell / CMD)

Se preferir rodar diretamente via linha de comando usando o emulador e ADB já instalados:

#### 0. Gerar build (apk)

- Versão Debug

```powershell
gradle assembleDebug
```

- Versão Release

```powershell
gradle assembleRelease
```

#### 1. (Opcional) Adicionar ferramentas do SDK ao PATH temporário

```powershell
$env:PATH += ";$env:LOCALAPPDATA\Android\Sdk\emulator;$env:LOCALAPPDATA\Android\Sdk\platform-tools"
```

#### 2. Listar os emuladores disponíveis

```powershell
emulator -list-avds
# Exemplo de saída: Pixel_5
```

#### 3. Iniciar o emulador desejado

```powershell
emulator -avd Pixel_5
```

#### 4. Verificar se o emulador foi reconhecido pelo ADB

```powershell
adb devices
# Deve listar: emulator-5554    device
```

#### 5. Instalar o APK compilado

```powershell
adb install -r app\build\outputs\apk\release\app-release.apk
```

#### 6. Abrir o aplicativo na tela do emulador

```powershell
adb shell am start -n io.github.gsilverio7.webosremote/.MainActivity
```

#### 7. Para desenvolvimento

.\gradlew installDebug; adb shell am start -n io.github.gsilverio7.webosremote/.MainActivity
