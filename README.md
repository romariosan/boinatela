São três comandos, cada um num terminal separado, todos a partir da raiz do projeto (C:\Users\romar\OneDrive\Documentos\app). A API e o app já estão rodando agora nas portas 8080 e 8100; para subir de novo, pare os atuais antes, senão a porta vai estar ocupada.

1. Banco (PostgreSQL no Docker):
docker compose up -d

2. Backend (API na porta 8080):
cd backend
mvn spring-boot:run

Para testar a verificação do WhatsApp e o "Esqueci minha senha" sem um bot de verdade, defina antes as variáveis de ambiente no mesmo terminal. O código enviado aparece no log da API
cd backend
$env:WHATSAPP_WEBHOOK_TOKEN = "um-segredo-local"
$env:WHATSAPP_ENVIO_NO_LOG = "true"
mvn spring-boot:run

3. Frontend (app na porta 8100):

cd mobile
npm start

Depois é só abrir http://localhost:8100.
Como o seu Node está instalado via nvm, se o npm não for encontrado, rode isto antes no mesmo terminal:


$env:Path = "C:\Program Files\nodejs;" + $env:Path

Para parar a API quando a porta 8080 estiver ocupada:


Get-NetTCPConnection -LocalPort 8080 -State Listen | ForEach-Object { Stop-Process -Id $_.OwningProcess -Force }



# CompreGado

MVP de classificados agropecuários para Formosa/GO e Entorno do DF. A vitrine é pública; login (WhatsApp + senha) só entra para **Falar com o vendedor** ou **Publicar anúncio**. O fechamento da venda é pelo WhatsApp — sem pagamento in-app na v1.0.

Domínio: [appcompregado.com.br](https://appcompregado.com.br) (site/PWA) e `api.appcompregado.com.br` (API). App nativo: appId `br.com.appcompregado`. Nome ASO: **CompreGado: Venda de Gado e Bezerros**.

## Estrutura

- `backend/` — Spring Boot 3, Java 17, JPA, Security, PostgreSQL (pacote `br.com.appcompregado`)
- `mobile/` — Ionic (CLI atual gerou Ionic 9 / Angular 22) + Angular + Capacitor
- `deploy/` — stack de produção (Docker Compose), Nginx da VPS e scripts
- `build-e-teste.sh` — build completo e teste local da stack de produção

## Como rodar

1. Banco (container `compregado-postgres-dev`, volume `compregado_pg_dev`):

```bash
docker compose up -d
```

2. API (porta 8080):

```bash
cd backend
mvn spring-boot:run
```

3. App (porta 8100; em desenvolvimento usa a API em `http://localhost:8080`):

```bash
cd mobile
npm start
```

Usuário de seed (não é criado no perfil `prod`): WhatsApp `61999999999`, senha `123456`.

## Ícones e splash screen (Android/iOS)

Fontes em `mobile/resources/` (Custom Mode do [@capacitor/assets](https://github.com/ionic-team/capacitor-assets)): `icon-only.png`, `icon-foreground.png` e `icon-background.png` (1024 × 1024, só o símbolo do logo) e `splash.png` (2732 × 2732, logo completo), com fundo creme `#F7F3EA`. Para trocar a arte, substitua esses arquivos mantendo nomes e tamanhos mínimos. Depois de criar as plataformas nativas:

```bash
cd mobile
npm install @capacitor/android @capacitor/ios
npx cap add android
npx cap add ios          # o build iOS exige macOS + Xcode
npm run assets           # = npx @capacitor/assets generate (gera para as plataformas existentes)
npx cap sync
```

A splash screen fica 2 s na tela (`plugins.SplashScreen` em `mobile/capacitor.config.ts`).

## Regras de segurança da API

- `GET /api/anuncios` e `GET /api/anuncios/{id}` — `permitAll`. O `q` é quebrado em palavras (ex.: `Bezerros Nelore, Formosa`) e cada uma precisa aparecer no título, descrição, cidade ou no nome da categoria/raça/aptidão; `cidade` é comparada inteira (`Formosa - GO`); `uf` (ex.: `GO`) filtra pelo sufixo da cidade.
- `GET /api/categorias` — só categorias com `count > 0` — `permitAll`
- `GET /api/cidades` — só cidades com anúncio — `permitAll`
- `POST /api/auth/cadastro` e `POST /api/auth/login` — `permitAll`
- `GET /uploads/**` — fotos dos anúncios (pasta `backend/uploads/`) — `permitAll`
- `POST /api/anuncios` (multipart: parte `imagens` repetida de 1 a 5 vezes — a primeira é a capa — + parte `dados` em JSON) e `POST /api/anuncios/{id}/contato` — `authenticated`
- `GET /api/anuncios/meus` — anúncios do usuário logado, inclusive vendidos — `authenticated`
- `PUT /api/anuncios/{id}` (multipart: `dados` com `fotosMantidas` = URLs atuais que ficam + `imagens` novas opcionais; total de 1 a 5), `DELETE /api/anuncios/{id}` e `PATCH /api/anuncios/{id}/status` (`{"status":"VENDIDO"}` ou `ATIVO`) — `authenticated` e só o dono (senão 403)

O WhatsApp do vendedor não vai na vitrine; só no endpoint de contato autenticado.

Anúncio `VENDIDO` sai da vitrine, da busca e das contagens de categorias/cidades, mas continua acessível por id; o contato responde 409.

`cidade` é sempre gravada como `Cidade - UF` (ex.: `Formosa - GO`). No Publicar, a lista de municípios vem da API do IBGE, com uma lista fixa das principais cidades caso ela falhe.

CORS: em desenvolvimento, `localhost:8100`/`4200`; no perfil `prod`, `CORS_ALLOWED_ORIGINS` (padrão: `https://appcompregado.com.br`, `https://www.appcompregado.com.br`, `https://localhost` e `capacitor://localhost` do app nativo).

## Verificação do WhatsApp (validação reversa)

Cadastro e login devolvem `telefoneVerificado` e `codigoVerificacao` (ex.: `CG-482913`). Publicar exige número verificado (senão a API responde 428). O app mostra um modal com o botão **Confirmar no meu WhatsApp**, que abre o `wa.me` do bot com o código, e consulta `GET /api/auth/verificar-status` a cada 3 s. O número do bot vem da API (`whatsappBot`, variável `WHATSAPP_BOT_NUMERO`); sem ela, o app usa `whatsappVerificacao` de `mobile/src/environments/`.

O bot é a [Evolution API](https://doc.evolution-api.com) (v2.3.7), que conecta o WhatsApp do CompreGado como um WhatsApp Web. Quando alguém manda o código, a Evolution chama o webhook da API, a API verifica a conta e responde ao usuário pelo WhatsApp ("✅ Pronto! Seu WhatsApp foi confirmado...").

- `GET /api/auth/verificar-status` — `authenticated`. Devolve `{telefoneVerificado, codigoVerificacao, whatsappBot}`.
- `POST /api/webhook/evolution` — usado pela Evolution (evento `MESSAGES_UPSERT`). Ignora mensagens do próprio bot, grupos e mensagens sem `CG-`; contatos que chegam como `...@lid` usam o telefone de `remoteJidAlt`/`senderPn`.
- `POST /api/webhook/whatsapp` — formato simples, para simular o bot: `{"remetente": "5561999999999@s.whatsapp.net", "mensagem": "... CG-482913"}`.

Os dois webhooks são públicos na Security, mas exigem o header `X-Webhook-Token` igual a `WHATSAPP_WEBHOOK_TOKEN` (sem ela, 503; token errado, 401). Só verificam se o remetente for o número da conta dona do código (aceita com/sem DDI, JID e sem o nono dígito).

Simular o bot localmente (a API precisa ter subido com o mesmo token):

```powershell
$env:WHATSAPP_WEBHOOK_TOKEN = "um-segredo-local"; mvn spring-boot:run   # em backend/
Invoke-RestMethod -Method POST http://localhost:8080/api/webhook/whatsapp -ContentType 'application/json' `
  -Headers @{ 'X-Webhook-Token' = 'um-segredo-local' } `
  -Body '{"remetente":"5561999999999","mensagem":"Codigo: CG-482913"}'
```

## Esqueci minha senha (código pelo WhatsApp)

- `POST /api/auth/esqueci-senha` `{"whatsapp"}` — 404 se o número não tem conta; 429 se pedir de novo em menos de 60 s ou mais de 5 vezes por hora. Gera um código de 4 dígitos (válido por 10 min, guardado como hash BCrypt em `codigo_recuperacao`), invalida os anteriores e envia pelo bot. Responde `{expiraEmSegundos, reenviarEmSegundos}`.
- `POST /api/auth/redefinir-senha` `{"whatsapp", "codigo", "novaSenha"}` — 400 código errado; 410 código expirado, já usado ou bloqueado (5 erros no mesmo código). Sucesso devolve o mesmo corpo do login (token incluso) e marca o WhatsApp como verificado. JWTs emitidos antes continuam válidos até expirar (24 h).

Envio pela Evolution: `POST ${WHATSAPP_BOT_URL}/message/sendText/${WHATSAPP_BOT_INSTANCIA}` com header `apikey: ${WHATSAPP_BOT_TOKEN}` e corpo `{"number": "5561999999999", "text": "..."}` (tempo limite de 20 s). Falha → 502 (o código não fica salvo); sem `WHATSAPP_BOT_URL` → 503. Só em desenvolvimento, `WHATSAPP_ENVIO_NO_LOG=true` escreve a mensagem no log da API em vez de enviar.

## Migrações manuais

O `ddl-auto: update` não recria as CHECK constraints de enum do Hibernate. Ao mudar enums, rode o script correspondente em `backend/db/` no banco existente (com a API parada):

```powershell
Get-Content backend\db\2026-10-13-racas-status-cidade-uf.sql -Raw | docker exec -i compregado-postgres-dev psql -U compregado -d compregado -v ON_ERROR_STOP=1
```

Também há `2026-10-13-verificacao-whatsapp.sql` e `2026-10-13-recuperacao-senha.sql`, aplicados do mesmo jeito. Um banco novo (criado pelo `ddl-auto`) não precisa deles.

## Teste local da stack de produção

```bash
./build-e-teste.sh            # Linux/macOS/Git Bash; no Windows: & "C:\Program Files\Git\bin\bash.exe" ./build-e-teste.sh
./build-e-teste.sh --parar    # derruba (mantém os volumes de teste)
./build-e-teste.sh --limpar   # derruba e apaga os volumes de teste
```

Faz `mvn clean package -DskipTests`, o build web de produção (`mobile/www`), valida o `nginx-compregado.conf` com `nginx -t` e sobe a stack com `STACK_NOME=compregado-teste` (API em `127.0.0.1:18080`, Evolution em `18081`, Postgres em `55432`), com segredos aleatórios em `deploy/.env.teste`. No fim roda testes de fumaça: vitrine, CORS, cadastro com código `CG-`, verificação pelo webhook e Evolution no ar. Não toca na stack de produção nem no Postgres de desenvolvimento.

## Deploy em produção (VPS)

```
internet ─443─▶ Nginx (VPS) ─┬─ appcompregado.com.br / www ─▶ /var/www/appcompregado (build web)
                             └─ api.appcompregado.com.br   ─▶ 127.0.0.1:8080 ─▶ container compregado-api
```

| Arquivo | Para quê |
| --- | --- |
| `deploy/docker-compose.yml` | Stack: `postgres`, `postgres-init`, `evolution-api`, `compregado-api`, na rede `compregado-net` |
| `deploy/.env.example` | Modelo das variáveis (copiar para `deploy/.env`) |
| `deploy/nginx/nginx-compregado.conf` | Nginx da VPS: HTTPS, site e proxy da API |
| `deploy/scripts/init-evolution-db.sh` | Cria o usuário e o banco `evolution` (roda a cada `up`, idempotente) |
| `deploy/scripts/whatsapp-conectar.sh` | Cria a instância do WhatsApp, liga o webhook e mostra o QR Code |
| `deploy/scripts/backup-full.sh` | Backup dos dois bancos, da sessão do WhatsApp e das fotos |

Segurança: só o Nginx (80/443) fica aberto para a internet. API (`API_PORT`), Postgres (`POSTGRES_PORT`) e Evolution (`EVOLUTION_PORT`) escutam apenas no `127.0.0.1` da VPS; o Docker ignora o UFW ao publicar portas, então é esse bind que os protege. Acesso pelo IP ou por outro domínio na porta 80 é fechado (`444`). A Evolution usa um usuário próprio no Postgres, sem acesso ao banco do CompreGado. Containers com `no-new-privileges` e logs com rotação (3 × 10 MB). No perfil `prod` a API não sobe sem `DB_*` e `JWT_SECRET`, e não cria o usuário de seed.

### Primeira instalação

1. VPS (Hostinger, região Brasil, Ubuntu 22.04/24.04 com Docker), com pelo menos 4 GB de RAM (API 1 GB + Evolution 768 MB + Postgres + sistema).
2. DNS do domínio: registros `A` para `@`, `www` e `api` → IP da VPS. Confira com `nslookup api.appcompregado.com.br` antes do Certbot.
3. No firewall da Hostinger, libere só 22, 80 e 443.
4. Stack Docker:

```bash
git clone https://github.com/romariosan/boinatela.git /opt/compregado && cd /opt/compregado/deploy
cp .env.example .env && chmod 600 .env
nano .env                                  # preencha seguindo os comentários do arquivo
docker compose up -d --build
docker compose ps                          # postgres e evolution-api "healthy"; postgres-init "Exited (0)"
docker compose logs -f compregado-api      # espere "Started CompreGadoApplication"
curl -s http://127.0.0.1:8080/api/anuncios # []
```

5. Nginx + certificado (um só certificado Let's Encrypt para os três nomes):

```bash
sudo apt update && sudo apt install -y nginx certbot
sudo certbot certonly --standalone -m SEU-EMAIL --agree-tos --no-eff-email \
  -d appcompregado.com.br -d www.appcompregado.com.br -d api.appcompregado.com.br \
  --pre-hook "systemctl stop nginx" --post-hook "systemctl start nginx"
sudo mkdir -p /var/www/appcompregado
sudo cp /opt/compregado/deploy/nginx/nginx-compregado.conf /etc/nginx/sites-available/compregado.conf
sudo ln -sf /etc/nginx/sites-available/compregado.conf /etc/nginx/sites-enabled/compregado.conf
sudo rm -f /etc/nginx/sites-enabled/default
sudo nginx -t && sudo systemctl reload nginx
sudo certbot renew --dry-run               # a renovação automática para o Nginx por alguns segundos
```

6. Site (build web): no PC, `./build-e-teste.sh` (ou `cd mobile && npx ng build --configuration production`) e envie a pasta `mobile/www`:

```bash
scp -r mobile/www root@IP-DA-VPS:/var/www/appcompregado-novo          # no PC, na raiz do projeto
ssh root@IP-DA-VPS "rm -rf /var/www/appcompregado && mv /var/www/appcompregado-novo /var/www/appcompregado"
```

Testes: `curl -I https://appcompregado.com.br` (200), `curl -I https://www.appcompregado.com.br` (301 para o domínio principal) e `curl https://api.appcompregado.com.br/api/anuncios` (`[]`).

Atualizar a API depois de um `git pull`: `docker compose up -d --build --remove-orphans`. Se o `nginx-compregado.conf` mudou, copie de novo para `/etc/nginx/sites-available/compregado.conf` e rode `sudo nginx -t && sudo systemctl reload nginx`.

### WhatsApp do bot (Evolution API)

Use um chip dedicado (de preferência com WhatsApp Business), não o seu número pessoal: a Evolution não é oficial da Meta e números com muitas mensagens automáticas podem ser bloqueados. A API fala com a Evolution em `http://evolution-api:8080`, e a Evolution chama o webhook em `http://compregado-api:8080/api/webhook/evolution`, os dois pela rede interna.

```bash
apt install -y qrencode                     # uma vez, para desenhar o QR no terminal
sh scripts/whatsapp-conectar.sh             # cria a instância, liga o webhook e mostra o QR Code
sh scripts/whatsapp-conectar.sh status      # "open" = conectado
```

No celular do bot: WhatsApp → Aparelhos conectados → Conectar um aparelho → ler o QR (aparece como "CompreGado"). O QR vale uns 40 s; se expirar, rode de novo. Alternativa sem QR: `sh scripts/whatsapp-conectar.sh 5561999999999` mostra um código de 8 caracteres para "Conectar com número de telefone". Depois, coloque o número em `WHATSAPP_BOT_NUMERO` no `.env` e rode `docker compose up -d`.

Painel da Evolution (opcional): no seu PC, `ssh -L 8081:127.0.0.1:8081 root@IP-DA-VPS` e abra `http://localhost:8081/manager` (Server URL `http://localhost:8081`, API Key = `EVOLUTION_API_KEY`).

### Onde ficam os dados

| Volume / pasta | Conteúdo | Se for perdido |
| --- | --- | --- |
| `compregado_pg_data` | Bancos `compregado` (usuários, anúncios) e `evolution` (instância e credenciais do WhatsApp) | Perde tudo e precisa ler o QR de novo |
| `compregado_evolution_instances` | Chaves de criptografia da sessão do WhatsApp | Precisa ler o QR de novo |
| `compregado_uploads` | Fotos dos anúncios | Anúncios ficam sem foto |
| `/etc/letsencrypt` (VPS) | Certificado HTTPS | Nova emissão com o mesmo `certbot certonly` (o Let's Encrypt limita emissões por semana) |
| `/var/www/appcompregado` (VPS) | Build web | Basta enviar o build de novo |

`docker compose down`, `restart`, `up -d --force-recreate` e reboot da VPS **não** apagam volumes. Só `docker compose down -v` ou `docker volume rm` apagam: não use em produção.

### Backup

`scripts/backup-full.sh` grava em `/var/backups/compregado/AAAA-MM-DD_HHMMSS/` (só root lê): `banco_compregado.sql.gz`, `banco_evolution.sql.gz`, `whatsapp_instances.tar.gz` e `fotos_uploads.tar.gz`. Confere a integridade de cada arquivo, descarta o backup se algo falhar e apaga backups com mais de 7 dias (`RETENCAO_DIAS`).

```bash
sudo chmod +x /opt/compregado/deploy/scripts/backup-full.sh
sudo /opt/compregado/deploy/scripts/backup-full.sh          # teste manual
sudo crontab -e                                              # e adicione a linha:
0 3 * * * /opt/compregado/deploy/scripts/backup-full.sh >> /var/log/compregado-backup.log 2>&1
```

Conferir: `sudo crontab -l`, `ls -lh /var/backups/compregado/`, `tail /var/log/compregado-backup.log`. Backup só na VPS não protege contra perder a VPS: copie para fora de vez em quando (`scp -r root@IP:/var/backups/compregado/AAAA-MM-DD_HHMMSS .`). Guarde o `.env` num gerenciador de senhas: ele não entra no backup.

Restaurar (na VPS, em `deploy/`, com a pasta do backup em `B`):

```bash
B=/var/backups/compregado/AAAA-MM-DD_HHMMSS
docker compose stop compregado-api evolution-api
zcat $B/banco_compregado.sql.gz | docker compose exec -T postgres psql -U compregado -d compregado
zcat $B/banco_evolution.sql.gz | docker compose exec -T postgres psql -U compregado -d evolution
docker run --rm -v compregado_evolution_instances:/destino -v $B:/b:ro alpine sh -c "rm -rf /destino/* && tar -xzf /b/whatsapp_instances.tar.gz -C /destino"
docker run --rm -v compregado_uploads:/destino -v $B:/b:ro alpine sh -c "tar -xzf /b/fotos_uploads.tar.gz -C /destino"
docker compose up -d
```

### Guia de verificação da persistência

Rode na VPS, em `/opt/compregado/deploy`. A ideia é anotar o estado, derrubar os containers e conferir que nada mudou.

```bash
# 1. Estado antes
K=$(sed -n 's/^EVOLUTION_API_KEY=//p' .env)
docker compose exec -T postgres psql -U compregado -d compregado -tAc "select count(*) from usuario"
docker compose exec -T postgres psql -U compregado -d compregado -tAc "select count(*) from anuncio"
curl -s -H "apikey: $K" http://127.0.0.1:8081/instance/fetchInstances | grep -o '"id":"[^"]*"\|"connectionStatus":"[^"]*"'
curl -s -H "apikey: $K" http://127.0.0.1:8081/webhook/find/compregado | grep -o '"url":"[^"]*"'
docker volume ls | grep compregado_

# 2. Derrubar e subir de novo (SEM -v)
docker compose down
docker volume ls | grep compregado_         # os 3 volumes continuam listados
docker compose up -d
sleep 60 && docker compose ps

# 3. Estado depois: repita os comandos do passo 1
```

O esperado depois do passo 3: mesmas contagens de usuários e anúncios; a instância com o mesmo `id` e `connectionStatus` `open` (sem pedir QR); o webhook apontando para `http://compregado-api:8080/api/webhook/evolution`; o app continua logando e as fotos abrem. Repita com `docker compose up -d --force-recreate` e com `sudo reboot` (os containers voltam sozinhos por causa do `restart: unless-stopped`). Para testar de ponta a ponta, mande uma mensagem com um código `CG-` para o bot depois do reboot e confira a resposta automática.

### Migrando do Boi na Tela (VPS antiga com Caddy)

A versão anterior rodava como `boinatela` com Caddy nas portas 80/443. O CompreGado começa com banco, fotos e sessão do WhatsApp novos (volumes `compregado_*`):

```bash
cd /opt/boinatela/deploy && docker compose down   # ANTES do pull: para a stack antiga e libera 80/443 (volumes ficam)
cd /opt && mv boinatela compregado && cd compregado && git pull
cd deploy && cp .env .env.boinatela && cp .env.example .env && chmod 600 .env
nano .env                                         # gere segredos novos (openssl rand ...)
```

Depois siga a "Primeira instalação" a partir do `docker compose up -d --build` (passos 4 a 6) e conecte o WhatsApp de novo. Quando tudo estiver no ar, apague o que sobrou da versão antiga:

```bash
docker volume ls | grep boinatela_                # confira a lista antes
docker volume rm boinatela_pg_data boinatela_uploads boinatela_evolution_instances boinatela_caddy_data boinatela_caddy_config
rm /opt/compregado/deploy/.env.boinatela
```

Os volumes antigos que não existirem na lista podem ser omitidos do `docker volume rm`.
