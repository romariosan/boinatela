#!/usr/bin/env sh
# Liga o WhatsApp do Boi na Tela na Evolution API e aponta o webhook para a API. Idempotente.
#   sh scripts/whatsapp-conectar.sh                 -> mostra o QR Code no terminal (apt install -y qrencode)
#   sh scripts/whatsapp-conectar.sh 5561999999999   -> mostra um código de 8 caracteres para digitar no celular
#   sh scripts/whatsapp-conectar.sh status          -> "open" = conectado
set -eu
cd "$(dirname "$0")/.."

ler() { sed -n "s/^$1=//p" .env | tail -n 1 | tr -d '\r'; }
CHAVE="$(ler EVOLUTION_API_KEY)"
TOKEN="$(ler WHATSAPP_WEBHOOK_TOKEN)"
INSTANCIA="$(ler EVOLUTION_INSTANCIA)"
INSTANCIA="${INSTANCIA:-boinatela}"
PORTA="$(ler EVOLUTION_PORT)"
EVO="http://127.0.0.1:${PORTA:-8081}"
WEBHOOK="http://boinatela-api:8080/api/webhook/evolution"

if [ -z "$CHAVE" ] || [ -z "$TOKEN" ]; then
  echo "Preencha EVOLUTION_API_KEY e WHATSAPP_WEBHOOK_TOKEN no .env e rode 'docker compose up -d' antes." >&2
  exit 1
fi

evo() { curl -sS -H "apikey: $CHAVE" -H "Content-Type: application/json" "$@"; }
campo() { sed -n "s/.*\"$1\":\"\([^\"]*\)\".*/\1/p"; }
estado() { evo "$EVO/instance/connectionState/$INSTANCIA" | campo state; }

if [ "${1:-}" = "status" ]; then
  echo "Instância $INSTANCIA: $(estado)"
  exit 0
fi

# Se a instância já existir, a Evolution responde 403 e seguimos.
evo -o /dev/null -X POST "$EVO/instance/create" -d "{\"instanceName\":\"$INSTANCIA\",\"integration\":\"WHATSAPP-BAILEYS\",\"qrcode\":false,\"groupsIgnore\":true,\"rejectCall\":false,\"alwaysOnline\":false,\"readMessages\":false,\"readStatus\":false,\"syncFullHistory\":false}"
evo -o /dev/null -X POST "$EVO/webhook/set/$INSTANCIA" -d "{\"webhook\":{\"enabled\":true,\"url\":\"$WEBHOOK\",\"headers\":{\"X-Webhook-Token\":\"$TOKEN\"},\"byEvents\":false,\"base64\":false,\"events\":[\"MESSAGES_UPSERT\"]}}"
echo "Webhook configurado: mensagens recebidas vão para $WEBHOOK"

if [ "$(estado)" = "open" ]; then
  echo "O WhatsApp já está conectado."
  exit 0
fi

if [ -n "${1:-}" ]; then
  NUMERO="$(echo "$1" | tr -cd '0-9')"
  CODIGO="$(evo "$EVO/instance/connect/$INSTANCIA?number=$NUMERO" | campo pairingCode)"
  if [ -z "$CODIGO" ]; then
    echo "A Evolution não devolveu o código. Tente de novo ou use o QR Code (rode sem o número)." >&2
    exit 1
  fi
  echo
  echo "Código de conexão: $CODIGO"
  echo "No celular: WhatsApp > Aparelhos conectados > Conectar um aparelho > Conectar com número de telefone."
else
  QR="$(evo "$EVO/instance/connect/$INSTANCIA" | campo code)"
  if ! command -v qrencode >/dev/null 2>&1; then
    echo "Instale o gerador de QR no terminal: apt install -y qrencode (ou rode passando o número)." >&2
    exit 1
  fi
  qrencode -t ANSIUTF8 "$QR"
  echo "No celular: WhatsApp > Aparelhos conectados > Conectar um aparelho, e leia o QR acima."
fi
echo "O código vale por uns 40 segundos; se expirar, rode o script de novo."
echo "Depois de conectar: sh scripts/whatsapp-conectar.sh status  (deve mostrar 'open')"
