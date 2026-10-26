# CompreGado — Documentação do sistema e do produto

Classificados de gado (corte, leite e genética) para Formosa/GO e o Entorno do DF. O negócio fecha no WhatsApp; o app não cobra, não processa PIX e não tem checkout. Marca oficial: **CompreGado: Venda de Gado e Bezerros**. Domínios: `appcompregado.com.br` (PWA) e `api.appcompregado.com.br` (API). appId Capacitor: `br.com.appcompregado`.

| Camada | Onde | Stack |
| --- | --- | --- |
| API | `backend/` | Java 17+, Spring Boot 3.4.5, Spring Data JPA, Spring Security (JWT stateless), PostgreSQL |
| App | `mobile/` | Ionic 9, Angular 22 (NgModule), Capacitor |
| Banco local | `docker-compose.yml` | Postgres `compregado` / usuário `compregado` / senha `compregado`, container `compregado-postgres-dev` |
| Produção | `deploy/` | VPS, Nginx, Evolution API, perfil Spring `prod` |

A vitrine e a busca são públicas. Login só entra em **Falar com o vendedor** e **Publicar anúncio**, num modal bottom sheet. Sessão no `localStorage`, chave `compregado-auth`. Login é WhatsApp + senha, sem e-mail.

---

## 1. Resumo executivo da arquitetura

### 1.1 Os três chips de WhatsApp

A Evolution API é compartilhada (`whatsapp.api.url` + `whatsapp.api.key`). Cada chip é uma instância. Sem URL e chave da Evolution, o Chip 1 e o Chip 3 caem no bot legado (`app.whatsapp.bot-*`) ou no simulador de log.

| Chip | Papel | Número padrão (dev) | Instância | Envia? | Escuta? |
| --- | --- | --- | --- | --- | --- |
| **1 — Sistema** | Transacional: OTP de cadastro/reivindicação (6 dígitos), recuperação de senha (4 dígitos) e aviso do Radar de Demanda | `5561999999991` | `compregado-system` | Sim (`WhatsAppSystemService`) | Não |
| **2 — Scraper** | Ouvinte de grupos rurais. Recebe o webhook, extrai o anúncio e grava. Não responde no grupo | `5561999999992` | `compregado-scraper` | Não | Sim (`POST /api/webhook/whatsapp-scraper`) |
| **3 — Consultor** | Prospecção anti-ban (spintax), aviso de comprador interessado e lembrete de retenção de 15 dias | `5561999999993` | `compregado-consultant` | Sim (`WhatsAppConsultantService`) | Não |

O bot legado (`app.whatsapp`) continua responsável pela verificação reversa do WhatsApp (`CG-` + 6 dígitos): o usuário manda o código do próprio aparelho, a Evolution chama `POST /api/webhook/evolution` e a API confirma a conta. Esse caminho é independente dos três chips de produto.

Pausa anti-ban do Chip 3: 3 a 6 segundos entre envios, fora da transação. Lote do consultor: no máximo 5 anúncios por minuto. Lote da retenção: no máximo 20 por execução diária.

### 1.2 Variáveis de ambiente (`application.yml`)

O projeto não usa `application.properties`. Tudo está em `backend/src/main/resources/application.yml`. O perfil `prod` (`application-prod.yml`) exige `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` e `JWT_SECRET` (mínimo 32 caracteres) e desliga o envio no log.

| Chave | Padrão de desenvolvimento | Função |
| --- | --- | --- |
| `spring.datasource.url` | `jdbc:postgresql://localhost:5432/compregado` | Banco |
| `spring.datasource.username` / `password` | `compregado` / `compregado` | Credenciais locais |
| `spring.jpa.hibernate.ddl-auto` | `update` | Cria coluna nova; **não** atualiza CHECK de enum |
| `server.port` | `8080` | API |
| `spring.servlet.multipart.max-file-size` | `10MB` | Uma foto |
| `spring.servlet.multipart.max-request-size` | `52MB` | Até 5 fotos |
| `GEMINI_API_KEY` → `gemini.api.key` | vazio | Sem chave, a captura grava o texto original |
| `gemini.api.url` | Gemini 1.5 Flash `generateContent` | Extração do anúncio |
| `WHATSAPP_API_URL` → `whatsapp.api.url` | `http://localhost:8080/evolution-api` | Base da Evolution dos 3 chips |
| `WHATSAPP_API_KEY` → `whatsapp.api.key` | vazio | Sem chave, não há instância dedicada |
| `compregado.app.googleplay.url` | Play Store `br.com.appcompregado` | Botão “Baixar Aplicativo Oficial” |
| `compregado.web.base-url` | `https://appcompregado.com.br` | Links `/lote/{id}` |
| `COMPREGADO_WHATSAPP_SYSTEM_NUMBER` / `_INSTANCE` | `5561999999991` / `compregado-system` | Chip 1 |
| `COMPREGADO_WHATSAPP_SCRAPER_NUMBER` / `_INSTANCE` | `5561999999992` / `compregado-scraper` | Chip 2 |
| `COMPREGADO_WHATSAPP_CONSULTANT_NUMBER` / `_INSTANCE` | `5561999999993` / `compregado-consultant` | Chip 3 |
| `compregado.consultor.cron` | `0 * * * * ?` (America/Sao_Paulo) | Consultor a cada minuto. `"-"` desliga |
| `compregado.retencao.dias` | `15` | Idade mínima do lote para o lembrete |
| `compregado.retencao.cron` | `0 0 9 * * ?` | Todo dia às 9h. `"-"` desliga |
| `compregado.radar.enabled` | `true` | `false` no perfil de teste |
| `compregado.limite.anuncios.gratis` | `3` | Anúncios ATIVOS sem PRO. Env: `COMPREGADO_LIMITE_ANUNCIOS_GRATIS` |
| `compregado.assinatura.cron` | `0 1 0 * * ?` | Expira PRO à 00:01. `"-"` desliga |
| `app.uploads.dir` | `uploads` (prod: `UPLOADS_DIR`, padrão `/data/uploads`) | Fotos |
| `app.jwt.secret` | segredo de desenvolvimento | HS256 e HMAC do link “marcar vendido”. Prod: `JWT_SECRET` |
| `app.jwt.expiration-ms` | `86400000` (24 h) | Validade do token |
| `WHATSAPP_WEBHOOK_TOKEN` → `app.whatsapp.webhook-token` | vazio (503 se chamar webhook) | Header `X-Webhook-Token` |
| `WHATSAPP_BOT_URL` / `BOT_TOKEN` / `BOT_INSTANCIA` / `BOT_NUMERO` | instância `compregado` | Bot legado de verificação |
| `WHATSAPP_ENVIO_NO_LOG` → `app.whatsapp.envio-no-log` | `false` | `true` liga `/dev/whatsapp` só em localhost |
| `app.cors.allowed-origins` | `localhost:8100` e `:4200` | Prod inclui o site e as origens do Capacitor |

No perfil de teste (`application-test.yml`): crons de assinatura, consultor e retenção em `"-"`, radar desligado.

### 1.3 PostgreSQL e scripts

Desenvolvimento: `ddl-auto: update`. Produção e bancos que já existem precisam dos scripts em `backend/db/`, porque o Hibernate não recria CHECK de enum nem converte dado antigo. Rodar com a API parada:

```text
docker exec -i compregado-postgres-dev psql -U compregado -d compregado -v ON_ERROR_STOP=1 < backend/db/<arquivo>.sql
```

| Script | O que faz |
| --- | --- |
| `2026-10-13-verificacao-whatsapp.sql` | `usuario.telefone_verificado`, `usuario.codigo_verificacao` |
| `2026-10-13-recuperacao-senha.sql` | Tabela `codigo_recuperacao` (código de 4 dígitos, hash, expiração, tentativas) |
| `2026-10-13-racas-status-cidade-uf.sql` | Raças atuais (`OUTRO` → `OUTRA`), status `ATIVO`/`VENDIDO`, cidade no padrão `Cidade - UF` |
| `2026-10-15-plano-pro-pausado.sql` | Status passa a aceitar `PAUSADO`; `anuncio.data_criacao`; `usuario.data_expiracao_premium` |
| `2026-10-16-cavalo-de-troia.sql` | `usuario.data_ultima_abordagem_bot`; no anúncio: telefone original, reivindicado, data de captura, flags do consultor; tabela `codigo_otp` |
| `2026-10-16-retencao-radar.sql` | `mensagem_retencao_enviada`, `quantidade_cliques_contato`, `ativo` (vendidos antigos ficam `ativo = false`); tabela `alerta_busca` |

Tabelas de domínio: `usuario`, `anuncio`, `anuncio_foto`, `codigo_recuperacao`, `codigo_otp`, `alerta_busca`.

`DataSeeder` (`@Profile("!prod")`) só roda se o banco de anúncios estiver vazio: cria o vendedor `61999999999` / senha `123456` e anúncios de exemplo.

---

## 2. Levantamento de funcionalidades

### 2.1 Autenticação e segurança

`PhoneUtils` é a forma canônica do telefone: tira máscara e sufixo de JID, remove o DDI 55 quando o número tem 12 ou 13 dígitos e insere o nono dígito em celular de 10 dígitos cujo terceiro dígito é 6–9. O que fica gravado é DDD + 9 + 8 dígitos. `variantesLegadas()` ainda acha cadastro antigo sem o nono dígito. Celular válido: 11 dígitos, nono dígito 9, DDD de 11 a 99.

| Fluxo | Contrato | Regras |
| --- | --- | --- |
| Cadastro | `POST /api/auth/cadastro` | Nome, WhatsApp e senha (mínimo 6). Celular inválido → 400. Número já usado (forma nova ou legada) → 409. Devolve JWT e entra logado. Telefone **não** fica verificado |
| Login | `POST /api/auth/login` | WhatsApp + senha. 401 se não achar ou a senha não bater |
| OTP de reivindicação / cadastro | `POST /api/auth/otp/cadastro-ou-reivindicar` | **Chip 1**, 6 dígitos, hash, 10 minutos, reenvio a cada 60 s, 5 por hora, 5 tentativas. Se vier `anuncioId`, o número tem de ser o `telefoneVendedorOriginal` de um lote capturado e ainda não reivindicado |
| Recuperação de senha | `POST /api/auth/otp/recuperar-senha` (o app) e `POST /api/auth/esqueci-senha` (mesmo serviço) | **Chip 1**, código de **4 dígitos**. 404 se o número não tem conta. Mesmos limites: 60 s, 5/hora, 5 tentativas, 10 min |
| Redefinir | `POST /api/auth/redefinir-senha` | JSON com campo `codigo` (`\d{4}`). 400 código errado, 410 expirado, usado ou bloqueado. Devolve sessão, marca o telefone verificado e vincula lotes reivindicados que ainda estão na conta de captura |
| Verificar status | `GET /api/auth/verificar-status` | Autenticado. Devolve se o WhatsApp já foi confirmado, o código `CG-xxxxxx` e o número do bot |
| Trocar senha | `PUT /api/usuarios/alterar-senha` | Autenticado. Senha atual errada → 422 com `tentativasRestantes`. 5 erros → bloqueio de 15 min (429). Acertar incrementa `versaoSessao` e devolve token novo; tokens antigos morrem |
| JWT | filtro `JwtAuthFilter` | Stateless, claim de versão de sessão. 403 no app desloga; limite de anúncio usa **400** de propósito |

Verificação reversa (não é o OTP do Chip 1): publicar exige `telefoneVerificado`. O app mostra o código `CG-` + 6 dígitos; o usuário envia esse código do próprio WhatsApp. `POST /api/webhook/evolution` (Evolution `MESSAGES_UPSERT`, `@lid` resolvido por `remoteJidAlt` / `senderPn`) ou `POST /api/webhook/whatsapp` `{remetente, mensagem}` (simulação). Só confirma se o remetente for o número da conta. Ao verificar, `ReivindicacaoService.vincularVerificados` assume lotes capturados daquele telefone que ainda pertencem à conta técnica `00000000000`.

O modal de login/cadastro/esqueci-senha (`AuthModalComponent`) abre por `SessaoService.exigirLogin()`. O envio do código de recuperação mostra loading e não aceita duplo clique.

### 2.2 Captura e IA (“Cavalo de Troia”)

`POST /api/webhook/whatsapp-scraper` é público na Security e fechado pelo header `X-Webhook-Token` (503 se o token não estiver configurado, 401 se estiver errado). Recusa de negócio volta HTTP 200, para a Evolution não ficar reenviando.

`ScraperAnuncioService.ingerir` aceita o payload da Evolution ou o formato simples `{remetente, mensagem, timestamp}`:

1. Ignora `fromMe`.
2. Se o payload tem `key`, só aceita grupo (`@g.us`).
3. Precisa de texto (conversa, texto estendido ou legenda de imagem).
4. Timestamp em segundos ou milissegundos, no máximo **10 minutos** atrás e no máximo 2 minutos no futuro. Sem timestamp, recusa.
5. Remetente tem de ser celular brasileiro (`PhoneUtils`).
6. Mesmo telefone + mesma descrição nos últimos 30 minutos = duplicata, recusa.

`GeminiExtractionService` pede título, preço, raça e quantidade (timeout 20 s, header `x-goog-api-key`). Sem chave, timeout ou JSON inválido: título `Anúncio de gado`, preço 0, raça `OUTRA`, descrição = texto original. Categoria e aptidão saem de palavras do texto. Cidade fixa `A confirmar - GO`. Status `ATIVO`, `reivindicado = false`, `dataCaptura = agora`, `telefoneVendedorOriginal` normalizado. O vendedor é a conta técnica `CompreGado` / `00000000000` (senha aleatória; esse número não passa no cadastro real). Depois do save, o Radar é agendado para depois do commit.

O Chip 2 não envia mensagem.

### 2.3 Prospecção e anti-ban (Chip 3)

`WhatsAppConsultantService`, cron `compregado.consultor.cron`, fuso `America/Sao_Paulo`.

Seleção: anúncio capturado (`dataCaptura` entre 2 horas e 30 minutos atrás), ainda sem `mensagemConsultorEnviada`, não reivindicado, com telefone original. Máximo 5 por execução.

Recorrência de 30 dias: se `Usuario.dataUltimaAbordagemBot` **ou** a última `dataAbordagemConsultor` daquele telefone (incluindo a forma sem o nono dígito) cair dentro da janela, o texto é de **manutenção**. Caso contrário, **apresentação**. Os dois textos são spintax (saudação, introdução, corpo, fecho) e terminam com o link `https://appcompregado.com.br/lote/{id}`.

Entre um envio e outro, a thread dorme 3–6 s **fora** da transação. Cada envio é uma transação. Falha não marca como enviado, então o próximo minuto tenta de novo. Sucesso grava `mensagemConsultorEnviada`, `dataAbordagemConsultor` e, se existir usuário, `dataUltimaAbordagemBot`.

### 2.4 Experiência web / PWA e reivindicação

Rotas públicas, sem login para ver o lote:

- `/lote/:id` — ficha do anúncio (a mesma página do detalhe interno).
- `/lote/:id/vendido?token=` — a mesma página, e em seguida pergunta se marca como vendido.

Dentro do app o mesmo componente abre em `/tabs/tab1/anuncio/:id`, `/tabs/tab2/anuncio/:id` e `/tabs/tab3/anuncio/:id`. O componente sobe a árvore de rotas para achar o `:id`, porque a rota filha é `path: ''`.

Se o lote foi capturado (`dataCaptura != null`) e ainda não foi reivindicado, a página mostra **“Este gado é seu? Reivindique para editar”**:

1. WhatsApp com máscara.
2. `POST /api/auth/otp/cadastro-ou-reivindicar` (Chip 1, 6 dígitos) com loading.
3. `POST /api/anuncios/{id}/reivindicar` com o OTP.
4. Toast, fecha o modal e recarrega.
5. Botão grande **Baixar Aplicativo Oficial** abre a Play Store.

A reivindicação só conclui se o OTP bater, não estiver expirado/usado e o telefone for o `telefoneVendedorOriginal`. Se já existir usuário verificado e o vendedor ainda for a conta de captura, o anúncio muda de dono. Cadastro não verificado **não** assume lote (evita tomar a conta de outra pessoa).

Compartilhar usa `{webBaseUrl}/lote/{id}`.

### 2.5 Radar de Demanda (“Me avise”)

`POST /api/alertas` é público. Corpo: `whatsapp`, `raca`, `categoriaPilar`, `cidade`. O WhatsApp passa por `PhoneUtils`. Pelo menos um critério. Duplicata do mesmo número + mesmos filtros devolve o alerta que já existe. Máximo **10 alertas ativos** por número (429).

Na Busca (`/tabs/tab2`), lista vazia mostra o card “Não encontrou o gado que procura?”. **Ativar Alerta Grátis** abre o modal de WhatsApp. A busca não tem filtro de raça: a raça sai do texto livre quando ele coincide com um rótulo (`Nelore`, `Angus`, …). Sem categoria, cidade nem raça reconhecida, o app avisa e não chama a API.

Quando um anúncio é criado (publicação ou captura), `AlertaNotificador` espera o commit e chama `AlertaMatchService` numa thread separada. Combina alerta ativo se raça **ou** cidade **ou** categoria coincidir (comparação sem diferenciar maiúsculas; campo vazio não casa). Não avisa o próprio vendedor. Mensagem no **Chip 1**:

> Opa! O Radar do CompreGado achou o gado que você procurava em [Cidade]: [N cab. de Raça | um lote de Raça]. Confira no link antes que seja vendido: [link do lote]

`compregado.radar.enabled=false` desliga o disparo (é o caso dos testes).

### 2.6 Retenção e ciclo de vida

**Clique de contato.** No detalhe, “Falar com o Vendedor” exige login, chama em segundo plano `POST /api/anuncios/{id}/registrar-intere-contato` (autenticado) e abre o `wa.me` devolvido por `POST /api/anuncios/{id}/contato`. O endpoint de clique incrementa `quantidadeCliquesContato`. Se houver celular do vendedor (`telefoneVendedorOriginal` ou WhatsApp do dono, nunca a conta `00000000000`), o **Chip 3** manda, depois do commit:

> Aqui é o Antonio, do CompreGado. Um comprador da região acabou de clicar para ver seu anúncio. Fique atento ao seu WhatsApp!

Falha no envio não desfaz a contagem.

**Lembrete de 15 dias.** `AnuncioRetentionScheduler`, todo dia às 9h (Brasília). Entra anúncio com `ativo = true`, status diferente de `VENDIDO`, `mensagemRetencaoEnviada = false` e `dataCaptura` (ou, se ela for nula, `dataCriacao`) com pelo menos `compregado.retencao.dias` dias. Vale lote reivindicado ou não: a pergunta é se já vendeu. Sem telefone válido, marca a flag e para de tentar. Com telefone, o **Chip 3** manda spintax no formato “Fala [primeiro nome ou produtor], aqui é o Antonio, do CompreGado. … lote de [Raça] … marcar como VENDIDO … [link]”, espera 3–6 s entre um e outro, e só então grava `mensagemRetencaoEnviada = true`. Falha de envio não marca a flag.

**Marcar vendido.** O link não é um PUT nu (o WhatsApp abre GET). `VendidoToken` assina `vendido:{id}` com HMAC-SHA256 do `app.jwt.secret` (Base64 URL, sem padding) e monta `{base}/lote/{id}/vendido?token=`. A página pede confirmação e chama `PUT /api/anuncios/{id}/marcar-vendido`. Aceita o dono logado **ou** o token. Efeito: `status = VENDIDO` e `ativo = false`. Token inválido → 403.

Na ficha, `status === VENDIDO` ou `ativo === false` cobre a foto com a tarja **LOTE VENDIDO PELO COMPREGADO** e esconde o botão de WhatsApp. `VENDIDO` e `PAUSADO` somem da vitrine, da busca e das contagens; o detalhe por id continua abrindo. Contato de lote vendido ou pausado responde 409.

O dono também muda status em Meus anúncios (`PATCH /api/anuncios/{id}/status`). Reativar (`VENDIDO` ou `PAUSADO` → `ATIVO`) passa de novo pelo limite grátis e liga `ativo`.

### 2.7 Monetização (paywall B2B)

Não há cobrança no app. Quem não é `isPremium` pode ter no máximo 3 anúncios `ATIVO` (criar e reativar). A contagem relê o usuário com trava. Estourar responde **400**:

```json
{"erro":"LIMITE_ANUNCIOS_ATINGIDO","mensagem":"..."}
```

`PlanoProService` mostra o alerta **Limite Atingido!** com **Assinar via WhatsApp**, abrindo `environment.whatsappPlanoPro` (`wa.me`) com o texto pedindo o plano Vendedor PRO. O PRO é ligado à mão no banco: `is_premium = true` e `data_expiracao_premium` (último dia incluso; nulo = não expira).

`AssinaturaJobService` às 00:01 (Brasília) rebaixa quem venceu, uma transação por vendedor com a linha travada: tira o premium e deixa `ATIVO` só os N mais recentes (`dataCriacao` nula conta como mais antiga). O resto vira `PAUSADO`. O dono reativa em Meus anúncios, de novo sujeito ao limite.

### 2.8 O restante do produto que já existe

| Superfície | O que faz |
| --- | --- |
| Vitrine `/tabs/tab1` | Lista pública só do que está `ATIVO`. Categorias com estoque (`GET /api/categorias`): Bezerro, Garrote, Vaca, Touro. Cidades com estoque (`GET /api/cidades`). Cache local quando a rede cai. Modo sol (classe `modo-sol`, chave `compregado-modo-sol`) para leitura no campo |
| Busca `/tabs/tab2` | Texto, categoria, UF e cidade. UF padrão da região. Lista vazia abre o Radar |
| Publicar `/tabs/publicar` e editar `/tabs/editar/:id` | Multipart: até 5 fotos (a primeira é a capa), título, descrição, preço, cidade `Cidade - UF` (municípios via IBGE), categoria, aptidão (`CORTE`, `LEITE`, `REPRODUCAO`), raça (14 valores, inclusive `MISTO` e `OUTRA`), tipo de venda (`CABECA`, `KG`, `LOTE`), quantidade e peso médio. Exige login e WhatsApp verificado |
| Fila offline | Publicação sem rede entra numa fila local e sobe quando o sinal volta. Vários estouros de limite mostram o paywall uma vez só |
| Conta `/tabs/tab3` | Perfil, confirmar WhatsApp, publicar, meus anúncios (pausar, marcar vendido, excluir, editar), pendentes da fila, alterar senha `/tabs/alterar-senha` |
| Fotos | `GET /uploads/**` público. Diretório `app.uploads.dir` |
| Simulador | Com `envio-no-log=true`, `GET /dev/whatsapp` (HTML), listar/enviar/limpar mensagens. Só loopback. Desligado em produção |
| Enums de gado | Categorias: `BEZERRO`, `GARROTE`, `VACA`, `TOURO`. Raças: `NELORE`, `ANGUS`, `GIROLANDO`, `BRAHMAN`, `SENEPOL`, `BRANGUS`, `GUZERA`, `TABAPUA`, `GIR`, `HOLANDES`, `CARACU`, `CANCHIM`, `MISTO`, `OUTRA` |

---

## 3. Tabela de endpoints e rotas

Permissão “público” na Security ainda pode exigir `X-Webhook-Token`. “Autenticado” = header `Authorization: Bearer`. O que não está listado cai em `anyRequest().authenticated()`.

### 3.1 API

| Método | Endpoint | Acesso | App / quem chama |
| --- | --- | --- | --- |
| POST | `/api/auth/cadastro` | Público | Modal de conta |
| POST | `/api/auth/login` | Público | Modal de conta |
| POST | `/api/auth/esqueci-senha` | Público | Mesmo serviço da recuperação; o app usa a rota de OTP abaixo |
| POST | `/api/auth/otp/recuperar-senha` | Público | Esqueci a senha (Chip 1, 4 dígitos) |
| POST | `/api/auth/otp/cadastro-ou-reivindicar` | Público | Reivindicação do lote (Chip 1, 6 dígitos) |
| POST | `/api/auth/redefinir-senha` | Público | Nova senha + auto-login |
| GET | `/api/auth/verificar-status` | Autenticado | Confirmar WhatsApp na Conta e antes de publicar |
| PUT | `/api/usuarios/alterar-senha` | Autenticado | `/tabs/alterar-senha` |
| GET | `/api/anuncios` | Público | Vitrine e Busca. Query: `q`, `cidade`, `uf`, `categoria`, `aptidao`, `raca` |
| GET | `/api/anuncios/{id}` | Público | Detalhe e `/lote/:id` |
| GET | `/api/anuncios/meus` | Autenticado | Conta → Meus anúncios |
| GET | `/api/categorias` | Público | Vitrine: só pilares com estoque |
| GET | `/api/cidades` | Público | Filtros |
| POST | `/api/anuncios` | Autenticado | Publicar (multipart `imagens` + `dados`) |
| PUT | `/api/anuncios/{id}` | Autenticado, dono | Editar |
| DELETE | `/api/anuncios/{id}` | Autenticado, dono | Excluir |
| PATCH | `/api/anuncios/{id}/status` | Autenticado, dono | Pausar, reativar, vendido |
| POST | `/api/anuncios/{id}/contato` | Autenticado | Devolve `wa.me` do vendedor |
| POST | `/api/anuncios/{id}/registrar-intere-contato` | Autenticado | Clique silencioso; Chip 3 avisa o vendedor. 204 |
| POST | `/api/anuncios/{id}/reivindicar` | Público | OTP de 6 dígitos do dono do lote capturado |
| PUT | `/api/anuncios/{id}/marcar-vendido?token=` | Público na Security; dono **ou** token HMAC | `/lote/:id/vendido` |
| POST | `/api/alertas` | Público | Radar na Busca vazia |
| POST | `/api/webhook/whatsapp` | Público + `X-Webhook-Token` | Simulação da verificação reversa |
| POST | `/api/webhook/evolution` | Público + `X-Webhook-Token` | Evolution `MESSAGES_UPSERT` da verificação |
| POST | `/api/webhook/whatsapp-scraper` | Público + `X-Webhook-Token` | Chip 2 |
| GET | `/uploads/**` | Público | Fotos |
| GET | `/dev/whatsapp` | Só com `envio-no-log=true` e só localhost | Página do simulador |
| GET | `/dev/whatsapp/mensagens` | Idem | Lista o que o bot “enviou” |
| POST | `/dev/whatsapp/mensagens` | Idem | Finge uma mensagem do usuário |
| DELETE | `/dev/whatsapp/mensagens` | Idem | Limpa o simulador |

### 3.2 Rotas Ionic

| Rota | Página | Login |
| --- | --- | --- |
| `/` → `/tabs/tab1` | Vitrine | Não |
| `/tabs/tab1/anuncio/:id` | Detalhe | Só no contato e ao reivindicar conta |
| `/tabs/tab2` | Busca + Radar no vazio | Não para buscar nem para criar alerta |
| `/tabs/tab2/anuncio/:id` | Detalhe | Igual à vitrine |
| `/tabs/tab3` | Conta, meus anúncios, fila offline | A aba abre; publicar e a lista “meus” pedem sessão |
| `/tabs/tab3/anuncio/:id` | Detalhe | Igual |
| `/tabs/publicar` | Novo anúncio | Sim, e WhatsApp verificado |
| `/tabs/editar/:id` | Editar anúncio | Sim, dono |
| `/tabs/alterar-senha` | Troca de senha | Sim |
| `/lote/:id` | Ficha pública + reivindicação + Play Store | Não para ver |
| `/lote/:id/vendido` | Ficha + confirmação de vendido | Não, se o `token` da query for válido |

O modal de autenticação não é rota: é bottom sheet sobre a ação que exigiu login.
