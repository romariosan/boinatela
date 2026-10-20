# Um arquivo por chip

Cada chip tem o próprio arquivo, a própria sessão e a própria janela de QR.

| Arquivo | Janela do QR | Função |
|---|---|---|
| `sistema.js` | CHIP SISTEMA | Envia código e alerta |
| `radar.js` | CHIP RADAR | Escuta grupo e publica o anúncio |
| `consultor.js` | CHIP CONSULTOR | Avisa o produtor no privado |

## Preparar

```powershell
cd C:\Users\romar\OneDrive\Documentos\leitor-whatsapp-local
npm install
copy .env.example .env
```

No `.env`, preencha `ROBO_TOKEN` e `WHATSAPP_WEBHOOK_TOKEN` com os valores de `/etc/compregado/api.env` na VPS.

## Subir

Os três de uma vez:

```powershell
.\iniciar-chips.ps1
```

Ou um só, para testar o código:

```powershell
node sistema.js
```

Abre uma página grande com o nome do chip. No celular desse número: **Aparelhos conectados** → **Conectar um aparelho** → leia o QR da página. Quando conectar, a página muda para **Conectado**.

Sistema e consultor ficam fora dos grupos. O radar é o número que entra nos grupos.

Se o celular disser “tente mais tarde”, feche essa janela e espere. Não gere outro QR em seguida.
