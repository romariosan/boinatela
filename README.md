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



# Boi na Tela

MVP de classificados agropecuários para Formosa/GO e Entorno do DF. A vitrine é pública; login (WhatsApp + senha) só entra para **Falar com o vendedor** ou **Publicar anúncio**. O fechamento da venda é pelo WhatsApp — sem pagamento in-app na v1.0.

Nome ASO: **Boi na Tela: Venda de Gado e Bezerros**. Ícone: Boi na Tela.

## Estrutura

- `backend/` — Spring Boot 3, Java 17, JPA, Security, PostgreSQL
- `mobile/` — Ionic (CLI atual gerou Ionic 9 / Angular 22) + Angular

## Como rodar

1. Banco:

```bash
docker compose up -d
```

2. API (porta 8080):

```bash
cd backend
mvn spring-boot:run
```

3. App (porta 8100):

```bash
cd mobile
npm start
```

Usuário de seed: WhatsApp `61999999999`, senha `123456`.

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

## Verificação do WhatsApp (validação reversa)

Cadastro e login devolvem `telefoneVerificado` e `codigoVerificacao` (ex.: `BT-482913`). Publicar exige número verificado (senão a API responde 428). O app mostra um modal com o botão **Confirmar no meu WhatsApp**, que abre o `wa.me` do bot (`whatsappVerificacao` em `mobile/src/environments/`) com o código, e consulta `GET /api/auth/verificar-status` a cada 3 s.

- `GET /api/auth/verificar-status` — `authenticated`
- `POST /api/webhook/whatsapp` — público na Security, mas exige o header `X-Webhook-Token` igual à variável de ambiente `WHATSAPP_WEBHOOK_TOKEN` (sem ela, responde 503). Corpo: `{"remetente": "5561999999999@s.whatsapp.net", "mensagem": "... BT-482913"}`. Só verifica se o remetente for o número da conta dona do código (aceita com/sem DDI, JID e sem o nono dígito).

Simular o bot localmente (a API precisa ter subido com o mesmo token):

```powershell
$env:WHATSAPP_WEBHOOK_TOKEN = "um-segredo-local"; mvn spring-boot:run   # em backend/
Invoke-RestMethod -Method POST http://localhost:8080/api/webhook/whatsapp -ContentType 'application/json' `
  -Headers @{ 'X-Webhook-Token' = 'um-segredo-local' } `
  -Body '{"remetente":"5561999999999","mensagem":"Codigo: BT-482913"}'
```

## Esqueci minha senha (código pelo WhatsApp)

- `POST /api/auth/esqueci-senha` `{"whatsapp"}` — 404 se o número não tem conta; 429 se pedir de novo em menos de 60 s ou mais de 5 vezes por hora. Gera um código de 4 dígitos (válido por 10 min, guardado como hash BCrypt em `codigo_recuperacao`), invalida os anteriores e envia pelo bot. Responde `{expiraEmSegundos, reenviarEmSegundos}`.
- `POST /api/auth/redefinir-senha` `{"whatsapp", "codigo", "novaSenha"}` — 400 código errado; 410 código expirado, já usado ou bloqueado (5 erros no mesmo código). Sucesso devolve o mesmo corpo do login (token incluso) e marca o WhatsApp como verificado. JWTs emitidos antes continuam válidos até expirar (24 h).

Envio pelo bot: `POST ${WHATSAPP_BOT_URL}` com header `X-Bot-Token: ${WHATSAPP_BOT_TOKEN}` e corpo `{"numero": "5561999999999", "mensagem": "..."}`. Falha do bot → 502 (o código não fica salvo); sem `WHATSAPP_BOT_URL` → 503. Só em desenvolvimento, `WHATSAPP_ENVIO_NO_LOG=true` escreve a mensagem no log da API em vez de enviar.

## Migrações manuais

O `ddl-auto: update` não recria as CHECK constraints de enum do Hibernate. Ao mudar enums, rode o script correspondente em `backend/db/` no banco existente (com a API parada):

```powershell
Get-Content backend\db\2026-10-13-racas-status-cidade-uf.sql -Raw | docker exec -i boinatela-postgres psql -U boinatela -d boinatela -v ON_ERROR_STOP=1
```

Também há `2026-10-13-verificacao-whatsapp.sql` e `2026-10-13-recuperacao-senha.sql`, aplicados do mesmo jeito.
