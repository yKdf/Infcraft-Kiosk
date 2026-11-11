# Infcraft Kiosk

Aplicativo Android próprio para o Philco PTB7R (Android 9), com WebView em tela cheia e painel de administração servido pelo tablet. Não depende do Fully ou de hospedagem externa.

Na v1.5, o painel local adota a identidade visual do infcraft.net: logotipo oficial, azul da marca, navegação translúcida, cards em azul-marinho e o wallpaper público armazenado dentro do APK. A interface continua responsiva e não depende desses arquivos externos durante o uso.

## Uso

- Painel na mesma rede: **https://10.40.2.137:8080** (o IP pode mudar).
- Chave de acesso desta instalação: arquivo local `.private/admin-token`. A chave foi gerada aleatoriamente no tablet e exibida na primeira abertura.
- Pelo USB: `adb forward tcp:18080 tcp:8080`, depois abra **https://localhost:18080**.
- O painel permite mudar endereço, brilho, orientação, ativar/desativar lock task e recarregar o site.
- Site inicial: dashboard Grafana informado pelo usuário, incluindo parâmetros de organização, intervalo, variáveis, atualização e modo kiosk.
- O modo kiosk usa Device Owner + Lock Task. O aplicativo também é a tela inicial padrão e recebe a inicialização do Android.
- A opção de desativar kiosk libera a navegação do Android; não remove a função Device Owner nem troca o launcher padrão.

O painel utiliza HTTPS na porta 8080 com certificado local. Importe `certificates/infcraft-kiosk-ca.crt` como autoridade confiável no navegador/dispositivo de acesso. A chave de administração permite criar uma sessão; o cookie de sessão usa Secure, HttpOnly e SameSite=Strict. O painel não envia a chave para o site exibido.

## Compilar

Requisitos Linux: Java/Javac (testado com OpenJDK 25), Python 3 e ferramentas Android oficiais. Não há dependências de bibliotecas externas no aplicativo.

```sh
python tools/setup-sdk.py
./tools/build.sh
```

Resultado: `build/infcraft-kiosk.apk`. O bootstrap baixa Android Platform 28 e Build Tools 35.0.0 e verifica os checksums publicados no repositório oficial. O build gera e preserva uma chave de assinatura em `.private/signing.p12`.

No Windows, instale um JDK 17 ou mais recente e deixe `javac.exe` no `PATH` ou configure `JAVA_HOME`. O JDK incluído no Android Studio também é detectado automaticamente. No PowerShell:

```powershell
.\tools\setup-sdk.ps1
.\tools\build.ps1
```

Se a política de execução bloquear scripts locais, libere-os apenas para esse processo:

```powershell
Set-ExecutionPolicy -Scope Process Bypass
```

**Faça backup de `.private/` em local seguro.** A mesma chave de assinatura é necessária para instalar atualizações sem desinstalar o aplicativo. Esses arquivos não devem ser versionados.

## Instalar em outro aparelho dedicado

```sh
adb install -r build/infcraft-kiosk.apk
adb shell am start -n com.biel.kiosk/.MainActivity
```

Guarde a chave de administração mostrada na primeira abertura. Para bloquear Home e Recentes, o usuário deve autorizar o gerenciamento Device Owner. O Android pode recusar o provisionamento se houver contas cadastradas, outro administrador principal ou outras restrições. Não apague dados automaticamente.

```sh
adb shell dpm set-device-owner com.biel.kiosk/.AdminReceiver
adb shell cmd package set-home-activity com.biel.kiosk/.MainActivity
```

Após provisionar, reinicie o app/tablet para aplicar a política. A remoção de um aplicativo Device Owner exige desprovisionamento; não execute restauração de fábrica sem backup e autorização.

## Verificação no dispositivo

```sh
adb forward tcp:18080 tcp:8080
python tests/device_smoke.py
adb shell dumpsys activity activities
```

O teste lê `.private/admin-token`, verifica autenticação, rejeição de URLs/propriedades inválidas, atualização persistida, recarga e rotas. Restaura o brilho ao valor inicial. Na saída do Android, procure `mLockTaskModeState=LOCKED`.

## Estrutura e limites

- `android/src/com/biel/kiosk/`: aplicativo, administração Android, inicialização e servidor HTTP.
- `android/assets/`: interface web em português, sem dependências externas.
- `tools/`: build e bootstrap reproduzíveis.
- `tests/`: teste de integração contra o tablet.

Esta versão oferece gestão local de um tablet, sem nuvem, atualização remota de APK ou suporte a câmera/microfone/upload de arquivos dentro do site. A autenticação do site ocorre no próprio tablet. O dashboard de destino foi conferido visualmente carregando os gráficos.

## Validação nesta instalação

No PTB7R conectado, a instalação e os testes da API passaram. Após reiniciar fisicamente o Android, o aplicativo voltou como atividade principal, o painel respondeu pela rede e o sistema confirmou `mLockTaskModeState=LOCKED`. Os comandos Home e Recentes mantiveram o kiosk em primeiro plano. Nenhum erro do aplicativo foi registrado no log consultado. A interface web foi entregue com verificações HTTP/API; não foi executada uma suíte automatizada de navegador.

## Descanso da tela (v1.1)

No painel web, configure **Reduzir brilho após**, **Brilho de descanso** e **Tela preta após**. Os tempos são em segundos desde o último toque: 0 desativa o temporizador; valores ativos vão de 5 a 86400 segundos. Quando ambos estão ativos, a tela preta deve começar depois da redução de brilho. Os padrões são 120 segundos, 10% e 300 segundos, respectivamente. O brilho reduzido nunca ultrapassa o brilho normal.

O primeiro toque em descanso restaura a tela e é consumido pelo app, evitando cliques involuntários no dashboard. Movimentos de toque também reiniciam a contagem. Recarregamentos automáticos do site e consultas do painel não contam como interação. Os tempos configurados persistem entre reinicializações; a contagem recomeça ao iniciar o app.

**Tela preta agora** e **Restaurar tela** enviam comandos imediatos sem salvar o formulário. O painel exibe o estado da tela, tempo sem toque e tempo ligado do Android, atualizados a cada 5 segundos.

A tela preta é uma camada preta com brilho mínimo; o display continua ligado para detectar o toque. Não é suspensão/desligamento físico e não oferece a mesma economia de energia. O Android do PTB7R reportou `mDoubleTapWakeEnabled=false`; não foi habilitado nem prometido retorno por toque com o display fisicamente desligado.

Teste adicional no tablet: `python tests/device_idle.py`. Usa temporariamente 5/10 segundos, verifica as transições e o retorno por toque e restaura a configuração original. As capturas de verificação ficam em `build/idle-black.png` e `build/idle-awake.png`.

## Desligamento real do display (v1.6)

O painel mantém **Descanso por inatividade** (redução de brilho e tela preta) e oferece, em uma seção independente, **Desligamento real do display**. Há duas formas de ativação:

- **Desligar após inatividade**: tempo em segundos desde o último toque. `0` desativa; valores ativos vão de 5 a 86400 segundos.
- **Usar período diário**: define um horário para desligar e outro para ligar. Períodos que atravessam a meia-noite são aceitos, por exemplo, desligar às `22:00` e ligar às `06:00`.

Esse recurso usa a política `force-lock` do Device Owner para apagar fisicamente o display. Por isso, ele só pode ser salvo quando o aplicativo é o administrador principal do aparelho. Com o display realmente desligado, o toque não o religa; use o horário programado, o botão físico ou **Ligar display** no painel. Os alarmes são recriados ao reiniciar o Android e após mudanças manuais de data ou fuso horário.

Os botões **Desligar display agora** e **Ligar display** controlam o display físico sem substituir os comandos antigos **Tela preta agora** e **Restaurar brilho**.

## Atualização da página (v1.4)

A atualização automática é controlada exclusivamente pelo próprio Grafana. Foram removidos a recarga programada do aplicativo, a tentativa automática após erro e a adaptação local da resposta do Grafana. URLs são salvas sem remover ou esvaziar o parâmetro `refresh`. O intervalo salvo no dashboard ou escolhido no Grafana é respeitado, sem alterar o servidor.

O botão **Recarregar site** continua disponível: volta à URL salva. O descanso da tela permanece ativo e recargas não contam como toque. Durante a tela preta, os timers do WebView continuam pausados para economizar recursos; voltam ao tocar ou restaurar a tela.

## HTTPS e acesso salvo (v1.3)

O endereço é **https://10.40.2.137:8080**. HTTP simples nessa porta deixa de funcionar. Até confiar na CA local, o navegador exibe aviso de certificado. A CA pública para importar é `certificates/infcraft-kiosk-ca.crt`; não distribua nenhum arquivo de `.private/tls/`. O certificado do servidor vale por 365 dias e inclui o IP atual, 127.0.0.1 e localhost. Mudanças do IP exigem emitir e provisionar outro certificado; prefira reservar o IP no DHCP.

Entre uma vez com a chave existente e mantenha **Lembrar neste navegador por 30 dias** marcado. A sessão será restaurada em próximas visitas. **Sair do painel** remove o cookie e revoga a sessão no tablet. Sem a opção marcada, o cookie é de sessão, com validade máxima de 12 horas no servidor (o comportamento de restauração ao reabrir depende do navegador). A chave não é gravada em localStorage. Não foi alterado o armazenamento de confiança do computador automaticamente.

Para gerar/provisionar HTTPS em outra instalação:

```sh
python tools/create-tls.py 10.40.2.137
adb forward tcp:18080 tcp:8080
python tools/provision-tls.py
```

Sem certificado instalado, o servidor atende **somente no loopback do tablet**, para provisionamento via USB autenticado; não expõe HTTP na rede. O provisionamento ativa HTTPS automaticamente na mesma porta. Para renovar, use `python tools/provision-tls.py --renew` após gerar o novo certificado com a mesma CA. Guarde a CA privada e os arquivos de assinatura em backup seguro. A chave TLS é armazenada na área privada do aplicativo, nunca incluída no APK.

Teste de integração: `python tests/device_https.py`. Verifica cadeia e IP do certificado, cookie persistente, restauração sem chave, bloqueio de origem indevida e revogação ao sair. Os testes existentes passam a usar HTTPS com validação da CA local.


Na v1.3.1, **Recarregar site** volta ao endereço salvo no painel, incluindo seus parâmetros, mesmo após navegar para outra página. Alterações ainda não salvas no formulário não são aplicadas por esse botão. O comando preserva o descanso da tela.


Na v1.4.1, o modo kiosk impede a abertura do teclado virtual na janela do aplicativo, inclusive em campos do site. Ativar kiosk também fecha o teclado já aberto. Desativar kiosk no painel devolve o comportamento normal do teclado.

## Recuperação do site (v1.5.1)

Falhas da página principal agora ficam visíveis no painel com o código HTTP, erro de rede ou erro de certificado e a URL correspondente. O aplicativo tenta novamente após 5, 10, 20 e 40 segundos, limitando as tentativas seguintes a intervalos de 60 segundos. Uma carga concluída limpa o erro e reinicia essa progressão. O botão **Tentar carregar agora** permite antecipar a recuperação.

Se o Android encerrar ou travar o processo de renderização do WebView, o aplicativo recria o WebView e retorna ao endereço salvo. A entrada em Lock Task também espera a atividade estar em primeiro plano, evitando o crash `Invalid task, not in foreground` observado no Android 9.
