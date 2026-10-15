# Runbook — Deploy CompreGado na VPS Hostinger

Manual de infraestrutura para manter o site institucional, a API Spring Boot e o Nginx em **appcompregado.com.br**.

> **Segurança:** nunca grave senha de root, JWT ou token do WhatsApp neste arquivo nem no Git. Use chave SSH e variáveis de ambiente. Se uma senha de root foi colada em chat ou ticket, **troque-a imediatamente** na Hostinger.

---

## 1. Visão geral da arquitetura

```
Internet
   │
   ▼
Nginx (443)  —  appcompregado.com.br
   │
   ├── /              → arquivos estáticos em /var/www/appcompregado  (landing em site/)
   ├── /app/          → build Ionic/PWA (opcional) em /var/www/appcompregado/app/
   ├── /apiC/…        → proxy reverso → http://127.0.0.1:8080/api/…
   └── /uploads/…     → proxy reverso → http://127.0.0.1:8080/uploads/…
                              │
                              ▼
                    Spring Boot (systemd)
                    JAR em /opt/compregado/
                    só escuta 127.0.0.1:8080
```

| Peça | Onde fica |
|------|-----------|
| Landing (`site/`) | `/var/www/appcompregado/` |
| App Ionic (build `mobile/www`) | `/var/www/appcompregado/app/` |
| JAR da API | `/opt/compregado/app.jar` |
| Unit systemd | `/etc/systemd/system/compregado-api.service` |
| Nginx | `/etc/nginx/sites-available/appcompregado` |
| Env de produção | `/opt/compregado/app.env` (modo `600`, fora do Git) |

**Ofuscação `/apiC`:** o cliente chama `https://appcompregado.com.br/apiC/anuncios`. O Nginx reescreve para `http://127.0.0.1:8080/api/anuncios` (rotas reais do Spring). A porta 8080 **não** deve ficar aberta na internet (firewall só 22/80/443).

IP da VPS (referência): `72.60.44.212`.

---

## 2. Conexão SSH

No PowerShell do seu PC:

```powershell
ssh root@72.60.44.212
```

Prefira chave SSH em vez de senha:

```powershell
# No PC (uma vez)
ssh-keygen -t ed25519 -C "compregado-deploy"
type $env:USERPROFILE\.ssh\id_ed25519.pub | ssh root@72.60.44.212 "mkdir -p ~/.ssh && cat >> ~/.ssh/authorized_keys"
```

Depois: `ssh root@72.60.44.212` sem digitar senha.

---

## 3. Tarefa 0 — Faxina geral (reset)

Execute **logo ao entrar** na VPS, **antes** do deploy novo. Isso apaga site antigo, configs Nginx do domínio e processos Java na 8080.

```bash
set -euo pipefail

echo "==> Parando Java na 8080 (se houver)"
if command -v fuser >/dev/null 2>&1; then
  fuser -k 8080/tcp 2>/dev/null || true
fi
pkill -f 'compregado|app\.jar|spring-boot' 2>/dev/null || true
systemctl stop compregado-api 2>/dev/null || true
systemctl disable compregado-api 2>/dev/null || true

echo "==> (Opcional) Derruba stack Docker antiga do CompreGado, se existir"
if command -v docker >/dev/null 2>&1; then
  docker ps -aq --filter "name=compregado" | xargs -r docker rm -f
  docker compose -f /opt/compregado/docker-compose.yml down 2>/dev/null || true
fi

echo "==> Parando Nginx"
systemctl stop nginx 2>/dev/null || true

echo "==> Removendo site antigo"
rm -rf /var/www/appcompregado
# Só limpe /var/www/html se tiver lixo do domínio; não apague se outros sites usam.
# rm -rf /var/www/html/*

echo "==> Removendo configs Nginx antigas do domínio"
rm -f /etc/nginx/sites-enabled/default
rm -f /etc/nginx/sites-enabled/compregado.conf
rm -f /etc/nginx/sites-enabled/appcompregado
rm -f /etc/nginx/sites-available/compregado.conf
rm -f /etc/nginx/sites-available/appcompregado
rm -f /etc/nginx/sites-enabled/appcompregado.com.br
rm -f /etc/nginx/sites-available/appcompregado.com.br

echo "==> Recriando pasta limpa do site"
mkdir -p /var/www/appcompregado
chown -R www-data:www-data /var/www/appcompregado

echo "==> Faxina concluída"
```

---

## 4. Pacotes base (Ubuntu)

```bash
apt update
apt install -y nginx certbot python3-certbot-nginx openjdk-17-jre-headless curl ufw
```

Firewall (se ainda não estiver ativo):

```bash
ufw allow OpenSSH
ufw allow 'Nginx Full'
ufw --force enable
ufw status
```

Confirme que **8080 não está liberada** para o mundo:

```bash
ss -tlnp | grep 8080 || echo "8080 livre ou só local — ok"
```

---

## 5. Deploy da landing (Frontend estático) via SCP

No **PowerShell do PC**, a partir da raiz do repositório:

```powershell
cd C:\Users\romar\OneDrive\Documentos\app

# Envia o conteúdo de site/ para /var/www/appcompregado/
scp -r .\site\* root@72.60.44.212:/var/www/appcompregado/
```

Na VPS, ajuste dono:

```bash
chown -R www-data:www-data /var/www/appcompregado
find /var/www/appcompregado -type d -exec chmod 755 {} \;
find /var/www/appcompregado -type f -exec chmod 644 {} \;
ls -la /var/www/appcompregado
```

### (Opcional) Publicar o app Ionic em `/app/`

```powershell
cd C:\Users\romar\OneDrive\Documentos\app\mobile
$env:Path = "C:\Program Files\nodejs;" + $env:Path
npx ng build --configuration production

# No environment.prod.ts use: apiUrl: 'https://appcompregado.com.br/apiC'
scp -r .\www\* root@72.60.44.212:/var/www/appcompregado/app/
```

Na VPS:

```bash
mkdir -p /var/www/appcompregado/app
chown -R www-data:www-data /var/www/appcompregado
```

---

## 6. Build e deploy da API (JAR + systemd)

### 6.1 Build no PC

```powershell
cd C:\Users\romar\OneDrive\Documentos\app\backend
mvn clean package -DskipTests
# Artefato típico: target\compregado-*.jar ou target\*-SNAPSHOT.jar
dir .\target\*.jar
```

Ajuste o nome do JAR abaixo ao que o Maven gerar.

### 6.2 Enviar o JAR

```powershell
ssh root@72.60.44.212 "mkdir -p /opt/compregado /opt/compregado/uploads"
scp .\target\*.jar root@72.60.44.212:/opt/compregado/app.jar
```

Se houver vários JARs, envie só o executável do Spring Boot (não o `*.jar.original`).

### 6.3 Arquivo de ambiente (na VPS)

```bash
nano /opt/compregado/app.env
```

Exemplo (troque todos os valores):

```bash
SPRING_PROFILES_ACTIVE=prod
SERVER_PORT=8080
# Escuta só localhost (recomendado)
SERVER_ADDRESS=127.0.0.1

DB_URL=jdbc:postgresql://127.0.0.1:5432/compregado
DB_USERNAME=compregado
DB_PASSWORD=TROCAR_SENHA_FORTE

JWT_SECRET=TROCAR_MINIMO_32_CARACTERES_ALEATORIOS
UPLOADS_DIR=/opt/compregado/uploads

CORS_ALLOWED_ORIGINS=https://appcompregado.com.br,https://www.appcompregado.com.br

WHATSAPP_WEBHOOK_TOKEN=TROCAR
WHATSAPP_BOT_URL=
WHATSAPP_BOT_TOKEN=
WHATSAPP_BOT_INSTANCIA=compregado
WHATSAPP_BOT_NUMERO=
```

```bash
chmod 600 /opt/compregado/app.env
chown root:root /opt/compregado/app.env
```

> O Postgres precisa existir (instalação local ou container só na rede interna). Sem banco, a API não sobe.

### 6.4 Unit systemd

```bash
cat >/etc/systemd/system/compregado-api.service <<'EOF'
[Unit]
Description=CompreGado API (Spring Boot)
After=network.target

[Service]
Type=simple
User=root
WorkingDirectory=/opt/compregado
EnvironmentFile=/opt/compregado/app.env
ExecStart=/usr/bin/java -jar /opt/compregado/app.jar
Restart=on-failure
RestartSec=5
SuccessExitStatus=143

# Hardening básico
NoNewPrivileges=true
PrivateTmp=true

[Install]
WantedBy=multi-user.target
EOF

systemctl daemon-reload
systemctl enable compregado-api
systemctl restart compregado-api
systemctl status compregado-api --no-pager
```

Logs da API:

```bash
journalctl -u compregado-api -f
journalctl -u compregado-api -n 200 --no-pager
```

Teste local na VPS:

```bash
curl -sS -o /dev/null -w "%{http_code}\n" http://127.0.0.1:8080/api/anuncios
```

---

## 7. Nginx

### 7.1 Antes do certificado (HTTP só para ACME + landing)

Se ainda **não** tem certificado Let's Encrypt, use um server block temporário só na porta 80, ou emita o cert com `--standalone` (Nginx parado).

### 7.2 Certbot (SSL)

**Opção A — standalone** (Nginx parado; primeiro certificado):

```bash
systemctl stop nginx
certbot certonly --standalone \
  -d appcompregado.com.br \
  -d www.appcompregado.com.br \
  --agree-tos \
  -m SEU_EMAIL@dominio.com \
  --non-interactive
systemctl start nginx
```

**Opção B — plugin nginx** (depois que o site HTTP já responde):

```bash
certbot --nginx -d appcompregado.com.br -d www.appcompregado.com.br
```

Renovação (timer do Certbot costuma já existir):

```bash
certbot renew --dry-run
systemctl list-timers | grep certbot
```

### 7.3 Instalar o conf definitivo

No PC, envie o arquivo do repositório:

```powershell
scp .\deploy\nginx\appcompregado.conf root@72.60.44.212:/etc/nginx/sites-available/appcompregado
```

Na VPS:

```bash
ln -sf /etc/nginx/sites-available/appcompregado /etc/nginx/sites-enabled/appcompregado
rm -f /etc/nginx/sites-enabled/default
nginx -t
systemctl reload nginx
systemctl status nginx --no-pager
```

O arquivo canônico no repositório é `deploy/nginx/appcompregado.conf`. Trecho essencial do proxy:

```nginx
location /apiC/ {
    proxy_pass http://compregado_api/api/;
    proxy_http_version 1.1;
    proxy_set_header Host              $host;
    proxy_set_header X-Real-IP         $remote_addr;
    proxy_set_header X-Forwarded-For   $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
    # ...
}
```

### 7.4 Checagens

```bash
curl -I https://appcompregado.com.br/
curl -sS -o /dev/null -w "%{http_code}\n" https://appcompregado.com.br/apiC/anuncios
```

---

## 8. Operação do dia a dia

| Ação | Comando |
|------|---------|
| Reiniciar Nginx | `systemctl restart nginx` |
| Recarregar Nginx (sem downtime) | `nginx -t && systemctl reload nginx` |
| Reiniciar API | `systemctl restart compregado-api` |
| Status | `systemctl status nginx compregado-api` |
| Log Nginx erro | `tail -f /var/log/nginx/error.log` |
| Log Nginx acesso | `tail -f /var/log/nginx/access.log` |
| Log API | `journalctl -u compregado-api -f` |
| Atualizar só a landing | `scp -r .\site\* root@72.60.44.212:/var/www/appcompregado/` |
| Atualizar só o JAR | enviar `app.jar` + `systemctl restart compregado-api` |

---

## 9. Checklist pós-deploy

1. [ ] `https://appcompregado.com.br` abre a landing.
2. [ ] `https://www.appcompregado.com.br` redireciona para o apex.
3. [ ] `https://appcompregado.com.br/apiC/anuncios` responde JSON (não 502).
4. [ ] Porta 8080 **não** acessível de fora (`Test-NetConnection 72.60.44.212 -Port 8080` no PC → falha).
5. [ ] Certificado válido (cadeado no navegador).
6. [ ] `app.env` com permissão `600` e fora do Git.
7. [ ] Se o app Ionic estiver em `/app/`, `environment.prod.ts` aponta `apiUrl` para `https://appcompregado.com.br/apiC`.

---

## 10. Notas importantes

- Este runbook descreve o modelo **landing + JAR + systemd + `/apiC`**, alinhado ao reset pedido. A pasta `deploy/docker-compose.yml` e o conf antigo `deploy/nginx/nginx-compregado.conf` (subdomínio `api.`) são o modelo Docker anterior — **não misture os dois** na mesma VPS sem revisar DNS, CORS e paths.
- Ofuscar o prefixo (`/apiC`) **não substitui** autenticação JWT, HTTPS, firewall e segredos fortes; só reduz enumeração óbvia de `/api`.
- Contato / WhatsApp PRO / número do bot: atualize nos `environment*.ts`, no rodapé da landing (`site/index.html`) e no `app.env` com os números reais.

---

## 11. Arquivos deste repositório

| Caminho | Uso |
|---------|-----|
| `site/` | Landing estática |
| `deploy/nginx/appcompregado.conf` | Nginx (landing + `/apiC` + `/uploads` + `/app`) |
| `DEPLOY_VPS.md` | Este runbook |
| `backend/` | Código e JAR da API |
| `mobile/` | App Ionic (build → `/var/www/appcompregado/app/`) |
