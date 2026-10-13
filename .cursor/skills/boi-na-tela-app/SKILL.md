---
name: boi-na-tela-app
description: >-
  Holds full product, stack, business, and architecture context for Boi na Tela
  (classificados agropecuários, Formosa/GO e Entorno do DF). Use whenever
  programming new features, designing Ionic/Angular screens, changing the Spring
  Boot API, JPA entities, Spring Security, WhatsApp login, or cattle classifieds
  (BEZERRO, GARROTE, VACA, TOURO).
---

# App Boi na Tela - Contexto do Projeto

## Resumo
Projeto de MVP de um aplicativo de classificados agropecuários focado na região de Formosa/GO e Entorno do DF. A ideia é conectar produtores e compradores, com as transações fechadas via WhatsApp. Nome Oficial do Aplicativo (ASO): Boi na Tela: Venda de Gado e Bezerros (Ícone no celular: Boi na Tela).

## Stack Tecnológico
Backend: Java 17+, Spring Boot 3, Spring Data JPA, Spring Security, PostgreSQL.
Frontend Mobile: Ionic 8 com Angular 18.

## O Modelo de Negócios e Monetização
A versão 1.0 não processa pagamentos. O fechamento é via WhatsApp.

Monetização Futura: Anúncios em Destaque (taxa avulsa via PIX), Planos Premium (mensalidade para contas comerciais ilimitadas), Espaços Publicitários para lojistas (B2B) e Google AdMob.

## Estratégia de Captação e Crescimento (A Máquina)
Agente de IA (Listener de WhatsApp): Um bot usando chip dedicado escuta grupos de negócios rurais da região, extrai informações com a API da OpenAI (título, preço, descrição, foto), cadastra no banco de dados e avisa o vendedor no PV que ele ganhou um anúncio grátis.
Tráfego Pago: Utilizar os melhores anúncios pescados pelo bot para rodar anúncios no Instagram/Facebook geolocalizados, atraindo compradores que baixam o app para pegar o número do vendedor.
Abordagem Inicial (MVP): Começar o povoamento manualmente com grupos de Facebook focando em anúncios reais de Formosa, mandando o link para os vendedores.

## O Pilar Inicial (MVP Restrito - O Laboratório)
Para garantir foco e validação rápida, o MVP será exclusivo para Gado (Corte, Leite e Genética).

## Arquitetura de Software e Fluxo do App
O banco de dados deve ser dinâmico (com todas as categorias da agronomia cadastradas), mas o App renderiza apenas o que tem estoque (count > 0).

Estratégia de Lazy Login: O usuário acessa a vitrine de produtos e faz buscas livremente sem precisar de senha (Zero atrito inicial).
Apenas duas ações exigem Login/Token: Clicar no botão para "Falar com o Vendedor" no WhatsApp ou tentar "Publicar Anúncio" (através de um Modal Bottom Sheet).
Autenticação: O login e cadastro (Silencioso e Rápido) é feito usando o Número do WhatsApp + Senha (Sem e-mail longo).

## Entidades Principais no Backend (JPA - Foco em Gado)
Usuario: id, nome, whatsapp (login), senha, isPremium, telefoneVerificado, codigoVerificacao ("BT-" + 6 dígitos; validação reversa: o usuário manda o código do próprio WhatsApp para o bot, que chama POST /api/webhook/whatsapp com X-Webhook-Token; publicar exige verificado).
CodigoRecuperacao: id, fk_usuario, whatsapp, codigoHash (BCrypt do código de 4 dígitos), dataCriacao, dataExpiracao (10 min), utilizado, tentativas (5 erros bloqueiam). "Esqueci minha senha": POST /api/auth/esqueci-senha (404 se não cadastrado, 429 com reenvio a cada 60 s e no máximo 5 por hora) envia o código pelo bot (WhatsappBotService: POST WHATSAPP_BOT_URL com X-Bot-Token e corpo {numero, mensagem}); POST /api/auth/redefinir-senha (400 código errado, 410 expirado/usado/bloqueado) devolve AuthResponse para auto-login e marca o telefone verificado.
Anuncio: id, titulo, descricao, preco, cidade (sempre "Cidade - UF", ex.: "Formosa - GO"), fotos (List<String>, @ElementCollection em anuncio_foto, até 5, a primeira é a capa), CategoriaAnimal (Enum: BEZERRO, GARROTE, VACA, TOURO), Aptidao (Enum: CORTE, LEITE, REPRODUCAO), Raca (Enum: NELORE, ANGUS, GIROLANDO, BRAHMAN, SENEPOL, BRANGUS, GUZERA, TABAPUA, GIR, HOLANDES, CARACU, CANCHIM, MISTO, OUTRA), TipoVenda (Enum: CABECA, KG, LOTE), status (AnuncioStatus: ATIVO, VENDIDO), fk_vendedor.

Anúncio VENDIDO some da vitrine/busca/contagens, mas abre por id; o contato responde 409.

## Diretrizes de Desenvolvimento
Sempre obedeça a regra de permitAll() no GET de Anúncios e authenticated() nas transações de criação, contato e gestão (GET /api/anuncios/meus, PUT, DELETE e PATCH /status — estes só pelo dono).

Ao mudar enums de entidade, crie um script SQL em `backend/db/` (o ddl-auto não atualiza as CHECK constraints do Hibernate).

## Design System (mobile)
App sempre escuro (`dark.always.css`); cores em `mobile/src/theme/variables.scss`:
- `primary` #1E5E3A (Verde Agro): toolbars, avatar, botão sólido de ação da marca. Não usar como cor de texto sobre o fundo escuro.
- `success` #22C55E (Verde Vibrante, texto preto): preços, badges, destaques, aba selecionada.
- `--color-whatsapp` #25D366: só botões de conversa e login, via classe global `botao-whatsapp`.
- Fundo #121212, texto #E0E0E0, cards #1E1E1E. Selo VENDIDO: classe global `selo-vendido` (cor `danger`).

## Layout do repositório
- `backend/` Spring Boot 3 (Java 17)
- `mobile/` Ionic/Angular
- `docker-compose.yml` PostgreSQL local

## Como aplicar este contexto
- Não introduzir pagamento, PIX, AdMob, planos premium ou categorias fora de gado no código do MVP, a menos que o pedido seja explícito.
- Manter a vitrine e a busca públicas; exigir token só em "Falar com o Vendedor" e "Publicar Anúncio" (modal bottom sheet).
- Login/cadastro: WhatsApp + senha, sem e-mail.
- App mostra só categorias com estoque (count > 0), mesmo que o banco tenha o restante da agronomia.
- Fechamento da transação é sempre WhatsApp, nunca checkout in-app na v1.0.
