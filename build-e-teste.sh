#!/usr/bin/env bash
# =============================================================================================
# CompreGado — build completo + stack de produção local para teste.
#
#   ./build-e-teste.sh            build do backend e do app web, sobe a stack e roda os testes de fumaça
#   ./build-e-teste.sh --parar    derruba a stack de teste (mantém os volumes de teste)
#   ./build-e-teste.sh --limpar   derruba a stack de teste e APAGA os volumes dela
#
# Roda no Linux, no macOS e no Windows (Git Bash). Precisa de: Java 17, Maven, Node/npm, Docker.
#
# A stack de teste usa STACK_NOME=compregado-teste: containers, rede e volumes compregado-teste-*,
# portas 18080 (API), 18081 (Evolution) e 55432 (Postgres). Nada disso toca na stack "compregado"
# de produção nem no Postgres de desenvolvimento (docker-compose.yml da raiz).
# Os segredos de teste são gerados em deploy/.env.teste (no .gitignore) na primeira execução.
# =============================================================================================
set -euo pipefail

RAIZ="$(cd "$(dirname "$0")" && pwd)"

# Git Bash: o Docker do Windows precisa de caminhos C:/..., e o MSYS não pode reescrever argumentos
# que começam com "/" (caminhos dentro dos containers, -subj do openssl). Só nesses comandos: o
# script do mvn quebra se a conversão for desligada para tudo.
if command -v cygpath >/dev/null 2>&1; then
  RAIZ="$(cygpath -m "$RAIZ")"
  [ -d "/c/Program Files/nodejs" ] && export PATH="/c/Program Files/nodejs:$PATH"
fi
caminho_host() { if command -v cygpath >/dev/null 2>&1; then cygpath -m "$1"; else echo "$1"; fi; }
literal() { MSYS_NO_PATHCONV=1 "$@"; }

ENV_TESTE="$RAIZ/deploy/.env.teste"
COMPOSE_ARQ="$RAIZ/deploy/docker-compose.yml"
API="http://127.0.0.1:18080"
EVOLUTION="http://127.0.0.1:18081"

passo() { printf '\n\033[1;32m==> %s\033[0m\n' "$*"; }
falha() { printf '\n\033[1;31mFALHOU: %s\033[0m\n' "$*" >&2; exit 1; }
trap 'falha "erro na linha $LINENO"' ERR

compose() { literal docker compose --env-file "$ENV_TESTE" -f "$COMPOSE_ARQ" "$@"; }
aleatorio() { head -c "$1" /dev/urandom | od -An -tx1 | tr -d ' \n'; }

case "${1:-}" in
  --parar)
    compose down
    exit 0 ;;
  --limpar)
    compose down -v
    exit 0 ;;
  "") ;;
  *)
    echo "Uso: $0 [--parar | --limpar]" >&2
    exit 2 ;;
esac

passo "Conferindo as ferramentas"
for cmd in java mvn node npm docker curl; do
  command -v "$cmd" >/dev/null 2>&1 || falha "'$cmd' não encontrado no PATH"
done
docker info >/dev/null 2>&1 || falha "o Docker não está rodando (abra o Docker Desktop)"

# ---------------------------------------------------------------------------------------------
passo "1/5 Backend: mvn clean package -DskipTests"
cd "$RAIZ/backend"
mvn -B -q clean package -DskipTests
ls target/*.jar
cd "$RAIZ"

# ---------------------------------------------------------------------------------------------
passo "2/5 Frontend: build web de produção (mobile/www)"
cd "$RAIZ/mobile"
[ -d node_modules ] || npm ci
npx ng build --configuration production
grep -q "<title>CompreGado</title>" www/index.html || falha "www/index.html sem o título CompreGado"
grep -lq "api.appcompregado.com.br" www/main-*.js || falha "o build não aponta para https://api.appcompregado.com.br"
echo "Build web pronto em mobile/www (é isso que vai para /var/www/appcompregado na VPS)."
cd "$RAIZ"

# ---------------------------------------------------------------------------------------------
passo "3/5 Validando deploy/nginx/nginx-compregado.conf (nginx -t com certificado autoassinado)"
if command -v openssl >/dev/null 2>&1; then
  CERTS="$(caminho_host "$(mktemp -d)")"
  literal openssl req -x509 -nodes -newkey rsa:2048 -days 1 -subj "/CN=appcompregado.com.br" \
    -keyout "$CERTS/privkey.pem" -out "$CERTS/fullchain.pem" 2>/dev/null
  literal docker run --rm \
    -v "$RAIZ/deploy/nginx/nginx-compregado.conf:/etc/nginx/conf.d/default.conf:ro" \
    -v "$CERTS:/etc/letsencrypt/live/appcompregado.com.br:ro" \
    nginx:stable-alpine nginx -t
  rm -rf "$CERTS"
else
  echo "openssl não encontrado: validação do Nginx pulada."
fi

# ---------------------------------------------------------------------------------------------
passo "4/5 Subindo a stack de teste (compregado-teste)"
if [ ! -f "$ENV_TESTE" ]; then
  echo "Gerando $ENV_TESTE com segredos aleatórios..."
  cat > "$ENV_TESTE" <<EOF
# Gerado pelo build-e-teste.sh. Só para a stack local de teste; não use em produção.
STACK_NOME=compregado-teste
POSTGRES_DB=compregado
POSTGRES_USER=compregado
POSTGRES_PASSWORD=$(aleatorio 24)
POSTGRES_PORT=55432
EVOLUTION_API_KEY=$(aleatorio 24)
EVOLUTION_DB_PASSWORD=$(aleatorio 24)
EVOLUTION_PORT=18081
EVOLUTION_URL=http://localhost:18081
EVOLUTION_INSTANCIA=compregado
WHATSAPP_WEBHOOK_TOKEN=$(aleatorio 24)
WHATSAPP_BOT_NUMERO=
SPRING_PROFILES_ACTIVE=prod
API_PORT=18080
JWT_SECRET=$(aleatorio 48)
CORS_ALLOWED_ORIGINS=https://appcompregado.com.br,https://www.appcompregado.com.br,http://localhost:8100
API_MEM_LIMIT=1g
EVOLUTION_MEM_LIMIT=768m
EOF
  chmod 600 "$ENV_TESTE"
fi
compose up -d --build

echo "Aguardando a API responder em $API ..."
for _ in $(seq 1 90); do
  curl -fsS -o /dev/null "$API/api/anuncios" 2>/dev/null && break
  sleep 2
done
curl -fsS -o /dev/null "$API/api/anuncios" || { compose logs --tail 60 compregado-api; falha "a API não subiu"; }

# ---------------------------------------------------------------------------------------------
passo "5/5 Testes de fumaça"
TOKEN_WEBHOOK="$(sed -n 's/^WHATSAPP_WEBHOOK_TOKEN=//p' "$ENV_TESTE" | tr -d '\r')"

echo "- Vitrine (GET /api/anuncios)"
curl -fsS "$API/api/anuncios"; echo

echo "- CORS: domínio oficial liberado, origem estranha bloqueada"
CABECALHOS="$(curl -fsS -o /dev/null -D - -X OPTIONS "$API/api/anuncios" \
  -H "Origin: https://appcompregado.com.br" -H "Access-Control-Request-Method: GET")"
grep -qi "access-control-allow-origin: https://appcompregado.com.br" <<<"$CABECALHOS" \
  || falha "CORS do domínio oficial"
CODIGO_HTTP="$(curl -s -o /dev/null -w '%{http_code}' -X OPTIONS "$API/api/anuncios" \
  -H "Origin: https://site-estranho.com" -H "Access-Control-Request-Method: GET")"
[ "$CODIGO_HTTP" = "403" ] || falha "origem estranha deveria receber 403 (veio $CODIGO_HTTP)"

echo "- Cadastro com código de verificação CG-"
NUMERO="619$(printf '%08d' $(( (RANDOM * 32768 + RANDOM) % 100000000 )))"
CADASTRO="$(curl -fsS -X POST "$API/api/auth/cadastro" -H "Content-Type: application/json" \
  -d "{\"nome\":\"Teste CompreGado\",\"whatsapp\":\"$NUMERO\",\"senha\":\"teste123\"}")"
JWT="$(echo "$CADASTRO" | sed -n 's/.*"token":"\([^"]*\)".*/\1/p')"
CODIGO="$(echo "$CADASTRO" | sed -n 's/.*"codigoVerificacao":"\(CG-[0-9]\{6\}\)".*/\1/p')"
[ -n "$JWT" ] && [ -n "$CODIGO" ] || falha "cadastro sem token ou sem código CG-: $CADASTRO"
echo "  usuário $NUMERO, código $CODIGO"

echo "- Webhook do bot confirma o WhatsApp"
curl -fsS -o /dev/null -X POST "$API/api/webhook/whatsapp" -H "Content-Type: application/json" \
  -H "X-Webhook-Token: $TOKEN_WEBHOOK" \
  -d "{\"remetente\":\"55$NUMERO@s.whatsapp.net\",\"mensagem\":\"Quero confirmar meu cadastro no CompreGado. Codigo: $CODIGO\"}"
STATUS="$(curl -fsS "$API/api/auth/verificar-status" -H "Authorization: Bearer $JWT")"
[[ "$STATUS" == *'"telefoneVerificado":true'* ]] || falha "o webhook não verificou o telefone: $STATUS"

echo "- Evolution API no ar (pode levar até 2 min para subir)"
for _ in $(seq 1 60); do
  curl -fsS -o /dev/null "$EVOLUTION/" 2>/dev/null && break
  sleep 2
done
EVO_INFO="$(curl -fsS "$EVOLUTION/")"
[[ "$EVO_INFO" == *'"clientName":"compregado"'* ]] || falha "Evolution sem clientName compregado: $EVO_INFO"

compose ps
passo "Tudo certo. API em $API | Evolution em $EVOLUTION/manager"
echo "Parar: ./build-e-teste.sh --parar    Apagar dados de teste: ./build-e-teste.sh --limpar"
