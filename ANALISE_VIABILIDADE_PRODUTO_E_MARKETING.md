# CompreGado — Análise de viabilidade de produto e marketing

Leitura de CPO/CMO sobre o que o repositório faz hoje: API Spring Boot, app Ionic/Angular, rotas públicas `/lote/:id`, scripts em `backend/db/` e a documentação de sistema. Não há telemetria de produção no código (sem funil medido, sem cohort, sem painel). Onde aparece taxa, é julgamento de mercado a partir do desenho do produto, não número observado.

Mercado-alvo do código: classificado de gado (bezerro, garrote, vaca, touro; corte, leite e reprodução) em Formosa/GO e no Entorno do DF. O negócio fecha no WhatsApp. A v1 não cobra comissão e não tem checkout.

---

## 1. Visão geral do produto e proposta de valor

### A dor que o pecuarista já sente

O canal real de compra e venda nessa região é o grupo de WhatsApp e, em segundo lugar, o Facebook. O anúncio nasce como texto solto, foto sem legenda ou áudio, some no scroll em horas e o comprador não tem como filtrar “20 bezerros Nelore em Planaltina”. O vendedor repete o mesmo texto em quatro grupos e atende gente de fora da região que não vai buscar o gado. Quem compra perde o lote bom porque a mensagem passou às 6h e ele abriu o grupo às 11h.

OLX e portais nacionais existem, mas o pecuarista dessa faixa trata taxa, cadastro longo e anúncio sem resposta local como atrito. O hábito vencedor é: “mando no grupo e quem quiser me chama”.

### O que o CompreGado oferece de fato

O produto não pede para o produtor abandonar o grupo. O Chip 2 escuta o grupo, o Gemini tenta virar aquilo num anúncio, o Chip 3 manda o link, e o vendedor continua fechando no WhatsApp dele. O comprador ganha uma vitrine filtrável (categoria, cidade, UF, texto) e um Radar para o dia em que o lote não está no ar.

Isso é a proposta certa para um marketplace rural novo: **indexar o hábito que já existe**, em vez de criar um hábito novo.

### Proposta de valor, item a item

| Promessa | No código | Força comercial |
| --- | --- | --- |
| Sem comissão | Não há PIX, checkout nem taxa sobre a venda. O paywall só aparece depois de 3 anúncios ativos, e a “assinatura” é uma conversa de WhatsApp | Forte. É o único jeito de entrar contra o grupo, que também é de graça. Cobrar comissão no primeiro ano mata o argumento |
| Uso pelo WhatsApp | Login é número + senha. OTP, prospecção, radar e retenção saem por WhatsApp. O link público abre no navegador | Forte no primeiro contato. O app nativo é consequência, não porta de entrada |
| Vitrine regional | Cidade no padrão `Cidade - UF`. A vitrine só mostra categoria com estoque. Busca tem UF e cidade | A intenção é certa. A captura ainda grava cidade fixa `A confirmar - GO`, então a promessa regional não se cumpre sozinha no lote pescado |

A marca (CompreGado, verde do logo, modo sol para ler no curral) está alinhada com uso no campo. O nome diz a ação. O nome antigo “Boi na Tela” não deve voltar em peça nenhuma.

**Limite honesto da proposta:** o CompreGado não garante que o gado existe, que o preço é o pedido nem que a cidade é a da fazenda. No lote capturado, preço ausente vira `0` e, sem palavra-chave, a categoria cai em bezerro. Para o comprador da região, anúncio sem foto real, sem preço e com cidade “a confirmar” parece golpe. A confiança se constrói no lote reivindicado e completado, não no texto cru do grupo.

---

## 2. Avaliação da estratégia “Cavalo de Troia” e do funil de atração

O funil desenhado é de oferta primeiro, demanda depois:

1. Chip 2 lê o grupo e cria o lote (oferta sem o vendedor pedir).
2. Trinta minutos depois, o Chip 3 manda o link (ativação do vendedor).
3. A página `/lote/:id` deixa qualquer pessoa ver o gado (demanda com atrito baixo).
4. Reivindicar exige o WhatsApp de quem postou e um OTP de 6 dígitos (posse do anúncio).
5. Comprador que não acha o perfil deixa o Radar (demanda futura).
6. Clique em “Falar com o vendedor” exige login e avisa o vendedor no Chip 3.

É um funil coerente para liquidez local. A parte fraca está na qualidade do degrau 1.

### Chip 2 + Gemini contra a vitrine vazia

O problema clássico do classificado novo é abrir o app e não ter o que comprar. O scraper ataca isso: mensagem de grupo com menos de 10 minutos, remetente com celular válido, texto presente, e recusa de duplicata do mesmo telefone com a mesma descrição nos 30 minutos seguintes. O Gemini pede título, preço, raça e quantidade. Se a IA falha, o anúncio entra mesmo assim, com título genérico e o texto original.

Isso **enche a grade**. Não **enche uma grade que o comprador confia**.

O que o código não faz, e o mercado vai cobrar:

- A foto do grupo não é baixada. O scraper usa a legenda. Publicar pelo app exige pelo menos uma foto; o lote pescado pode ir para a vitrine só com o placeholder. Em gado, a foto é o anúncio.
- Cidade fica `A confirmar - GO`. O filtro regional, que é o diferencial, não funciona nesse lote.
- Preço `0` e categoria chutada (bezerro, se o texto não disser garrote, vaca ou touro) geram clique desqualificado e desgaste do vendedor.
- A janela de duplicata é curta. O mesmo texto republicado no dia seguinte vira outro card.
- Áudio, figura sem legenda e “chama no privado” caem fora. Uma fatia grande do grupo real não vira anúncio.

Eficiência estimada do scraper como máquina de estoque: alta para volume bruto, baixa para estoque vendável. Num grupo ativo de Formosa, é razoável esperar dezenas de cards por semana e uma minoria apresentável. Sem uma peneira humana (ou uma regra que esconda lote sem foto e sem cidade), a vitrine cheia afasta o comprador em vez de segurá-lo.

Conclusão de produto: o Cavalo de Troia resolve o cold start de **cadastro**, desde que os primeiros grupos sejam poucos, locais e revisados. Não resolve sozinho o cold start de **desejo de comprar**.

### Chip 3 no primeiro contato

O consultor espera o lote completar 30 minutos (janela até 2 horas, no máximo 5 envios por minuto, pausa de 3 a 6 segundos). Quem já foi abordado nos últimos 30 dias recebe texto de manutenção; os outros, apresentação. O texto cita o lote, manda o link e diz que reivindicar é grátis. Isso é melhor do que um “olá, tudo bem?” genérico, porque a pessoa reconhece o próprio anúncio.

Ainda é mensagem não solicitada, de um número que ela não salvou, apontando para um site. No agro de grupo, a referência ao lote próprio aumenta a resposta. O medo de golpe e o cansaço de disparo em massa puxam para o outro lado.

Faixas estimadas para lead frio (mensagem do Chip 3 → vendedor ativo), nos primeiros 90 dias, com chip aquecido e copy atual:

| Etapa | Faixa estimada | Por quê |
| --- | --- | --- |
| Lê e não bloqueia na hora | 50–70% | O texto fala do lote dele. O resto bloqueia ou ignora por desconfiança |
| Abre o link `/lote/:id` | 8–15% dos enviados | Um toque, sem instalar app. Cai se o preview do link vier sem foto |
| Conclui o OTP e reivindica em 7 dias | 2–5% dos enviados | O código chega, mas muita gente só olha e volta para o grupo |
| Volta a publicar pelo app, com foto e cidade | 0,5–2% dos enviados | Aqui nasce o vendedor ativo de verdade |

Traduzindo: em 100 abordagens bem feitas, espere algo como 8 a 15 visitas e 2 a 5 reivindicações. Uma reivindicação não é um vendedor retido. O número que importa para o negócio é menor.

Essas faixas sobem se o primeiro lote que a pessoa vê já tem a foto dela e a cidade certa, e caem para perto de zero se o chip entra em disparo amplo ou se o link abre um card com preço zero.

O controle de 30 dias evita martelar o mesmo produtor. Está certo. A manutenção (“seu lote continua no ar”) só ajuda depois que a primeira mensagem gerou confiança.

### PWA `/lote/:id` e reivindicação

A rota pública é a melhor decisão de crescimento do produto. O vendedor não precisa da Play Store para ver o gado. O comprador vindo de status, grupo ou anúncio do Instagram também não. O botão “Baixar Aplicativo Oficial” fica depois da reivindicação, como convite, não como pedágio.

O fluxo de reivindicar está no tamanho certo para o campo: “Este gado é seu?”, WhatsApp com máscara, código de 6 dígitos com loading, toast, página atualizada. O OTP prova que o chip na mão é o mesmo que postou no grupo. Isso protege o anúncio e, ao mesmo tempo, é o maior degrau de abandono.

Dois atritos que o lançamento precisa medir:

- **Falar com o vendedor exige login** (WhatsApp + senha). Ver o lote é livre; pegar o contato, não. Para um comprador que chegou no impulso, o modal é onde a conversa morre. O Radar compensa quem não encontrou gado. Não compensa quem encontrou e desistiu na senha.
- **Publicar exige WhatsApp verificado** (código `CG-` enviado do próprio aparelho para o bot). É uma segunda prova, diferente do OTP de reivindicação. Faz sentido contra anúncio falso. No lançamento, cada passo a mais custa vendedor.

O link de vendido (`/lote/:id/vendido?token=`) está bem resolvido: o WhatsApp abre uma página, a pessoa confirma, e o token não é uma URL que marca vendido só de ser pré-visualizada.

---

## 3. Ciclo de retenção e engajamento

### Radar de Demanda

Na Busca, lista vazia vira um pedido concreto: “avise no WhatsApp quando este perfil aparecer em Formosa e região”. O alerta guarda raça (quando o texto livre é uma raça conhecida), categoria e cidade. O disparo sai no Chip 1, com quantidade, raça, cidade e o link, e não avisa o próprio vendedor.

Papel no crescimento: o Radar transforma um fracasso da vitrine (não tinha o gado hoje) em um comprador identificado. Em marketplace rural, a demanda é intermitente. A pessoa procura 30 bezerras Girolando uma vez por mês. Sem o Radar, essa visita se perde. Com ele, o próximo lote certo tem um destinatário.

Limites do desenho atual:

- O casamento é por raça **ou** cidade **ou** categoria. Um alerta só de “Formosa” recebe quase todo lote da cidade. Um alerta só de “Nelore” recebe Nelore de qualquer lugar. No começo, com poucos alertas, isso até ajuda. Com dezenas de alertas, vira spam e queima o Chip 1.
- Se a pessoa não escolheu categoria, cidade nem uma raça que o app reconhece, o alerta nem é criado. O vazio “quero qualquer gado bom” não entra.
- O aviso promete gado “antes que seja vendido”. Se o card estiver sem foto ou com cidade a confirmar, o comprador aprende a ignorar o Radar na segunda mensagem.

Uso certo no lançamento: tratar o Radar como lista de espera qualificada, não como broadcast. A primeira versão comercial deveria avisar pouco e certo (raça e cidade juntos), mesmo que o código hoje aceite um critério só.

### Scheduler de 15 dias e a tarja “LOTE VENDIDO”

O lembrete das 9h, no Chip 3, pergunta se o lote saiu e entrega um link para marcar vendido sem senha. Isso ataca um problema real: anúncio velho gera ligação para gado que já foi, o vendedor irrita e o comprador perde a confiança na vitrine.

O efeito psicossocial da tarja **LOTE VENDIDO PELO COMPREGADO** é duplo.

A favor: classificado com selo de vendido parece vivo. O produtor que passa no link vê que gente dali fecha negócio. A frase “não receber mais ligações” é o benefício egoísta certo. Quinze dias é um prazo plausível para bezerro de grupo; touro ou lote grande pode precisar de mais, mas um único prazo simples é melhor do que nenhum.

Contra: a tarja diz que a plataforma vendeu. Na maior parte dos casos, a venda aconteceu no grupo ou no curral, e o CompreGado só tirou o card do ar. Se o produtor perceber exagero, a marca paga. O selo funciona como prova social de **giro da vitrine**, não como prova de que o app intermediou o pagamento. A copy da tarja promete mais do que o produto entrega.

Recomendação de marca, sem mudar a operação: o selo pode dizer que o lote saiu, e o crédito “pelo CompreGado” fica para o dia em que o clique de contato (`quantidadeCliquesContato`) mostrar que a conversa nasceu aqui. Enquanto o giro vier do grupo, “lote encerrado” constrói mais confiança do que “nós vendemos”.

O contador de cliques e o aviso “um comprador da região acabou de clicar” são o motor de hábito do vendedor. Uma mensagem dessas, verdadeira, vale mais do que dez mensagens de “seu lote continua no ar”. Ela só pode disparar quando houver comprador de verdade. No lançamento, tráfego falso ou teste interno queimam essa frase para sempre.

---

## 4. Riscos, gargalos e ameaças

### Operacionais

**Bloqueio de chip.** Três números com papéis separados (sistema, escuta, consultor) são a arquitetura certa. A pausa de 3–6 segundos, o teto de 5 abordagens por ciclo e a régua de 30 dias reduzem o ritmo. Não tornam o disparo a frio seguro. O Chip 3 fala com gente que não pediu mensagem. O Chip 1, no Radar, também fala com quem pediu, e esse opt-in é o único envio comercial defensável. Um chip banido no meio da reivindicação (OTP não chega) para o funil inteiro. Operar com número novo, disparo largo e link encurtado estranho é o caminho mais curto para perder o canal. O lançamento cabe em poucos grupos, volume baixo e resposta humana quando o produtor responder o consultor.

**Foto ruim ou inexistente.** O app, na publicação manual, já orienta foto de dia, de lado e do lote inteiro, e exige ao menos uma imagem. O scraper não traz a mídia do grupo. A vitrine mistura anúncio de produtor (foto real) com card de placeholder. Para quem compra gado, isso não é detalhe visual: é critério de descarte.

**Anúncio duplicado e anúncio morto.** A trava de 30 minutos não cobre a republicação do dia seguinte, comum em grupo. O lembrete de 15 dias ajuda a aposentar o card, desde que o vendedor toque no link. Até lá, o mesmo lote pode aparecer duas vezes e o comprador desconfia.

**Qualidade da extração.** Título genérico, preço zero e cidade a confirmar são lixo estruturado. Gemini sem chave (`GEMINI_API_KEY` vazio) faz o sistema gravar tudo assim de propósito. Subir a captura em produção sem a chave, ou sem alguém olhando a grade, enche o site de anúncio imprestável.

**Números e instâncias de exemplo.** Os chips padrão (`5561999999991` e seguintes) e o WhatsApp do plano PRO no app são placeholders. Ir a mercado com isso manda o produtor para um número que não é o da operação.

**Dependência de um único canal.** Evolution, webhook e três chips são um ponto único de falha. O grupo de Facebook, citado na estratégia original de povoamento, não está no código. Se o WhatsApp apertar, não há segundo canal de captação implementado.

### Comportamentais

O produtor tradicional não resiste ao WhatsApp. Resiste a “mais um aplicativo”, a senha e a qualquer coisa que pareça que vão tomar o cliente dele. O desenho atual respeita isso no primeiro minuto (link, não instalação) e cobra conta quando ele quer editar, publicar ou quando o comprador quer o telefone.

Riscos reais de comportamento:

- Ele vê o lote no ar, gosta, e não reivindica porque “no grupo já está vendendo”. O anúncio fica órfão, com dado ruim, e o Chip 3 insiste.
- Ele desconfia que o CompreGado vai revender o contato ou cobrar em cima da negociação. A isenção de comissão precisa estar na primeira frase, em português de curral, não em rodapé.
- O comprador da cidade quer o número na hora. O login no botão de contato parece pedágio. Se a região interpretar o app como “mais um cadastro para ver telefone”, a comparação com o grupo fica cruel: no grupo o número já está na mensagem.
- Indicação boca a boca no agro é local e lenta. Um produtor que levou golpe (ou achou que levou) conta na loja agropecuária e o prejuízo dura meses. Um único lote falso com o nome CompreGado custa mais do que cem abordagens.

A resistência cai quando o primeiro contato é “achei o seu anúncio no grupo e deixei ele organizadinho, de graça, você continua falando no seu WhatsApp”. Sobe quando a primeira tela pede para baixar app, criar senha e confirmar um código `CG-`.

### Concorrência

O concorrente principal não é um app. É o administrador do grupo de Formosa. Enquanto o grupo entregar comprador no mesmo dia, o produtor não tem motivo para mudar de canal. O CompreGado ganha no que o grupo faz mal: achar de novo um lote de ontem, filtrar raça e cidade, e avisar quando chegar. Perde no que o grupo faz bem: foto na hora, confiança de quem está no grupo e zero cadastro.

Portais nacionais ficam em segundo plano nessa praça, desde que a densidade local exista. Sem densidade, o produtor compara com o portal e o portal já tem anúncio.

---

## 5. Score de viabilidade e veredito

**Nota da ideia, no agro brasileiro de 2026: 7,5 / 10.**

O problema é real, a região é pequena o bastante para ter liquidez, o fechamento no WhatsApp é o jeito que o dinheiro já anda, e o conjunto scraper + link público + radar + lembrete de vendido é um sistema de crescimento, não uma lista de telas. Tira ponto a execução ainda incompleta da oferta (foto, cidade, preço), o disparo a frio como motor principal e a ausência de prova de que comprador paga o custo de um login para falar com o vendedor.

**Prontidão para abrir as portas hoje: 6 / 10.** O software sobe, a rota pública existe, o paywall manual existe. A grade que o scraper produz ainda não está no nível em que um pecuarista encaminharia o link para o vizinho.

### Vale ir a mercado?

Sim, como **piloto regional de 90 dias em Formosa e no Entorno**, com poucos grupos, revisão do que entra na vitrine e meta de conversa iniciada. Não vale abrir tráfego pago amplo, nem tratar o Android publicado como lançamento nacional, enquanto lote sem foto e cidade “a confirmar” aparecer na capa.

O investimento que se paga agora é operação (chips aquecidos, alguém da região nos grupos, correção do que o Gemini erra) mais do que mídia. O código já segura esse piloto. O mercado não perdoa vitrine que parece automática e vazia de gado de verdade.

Critério para continuar depois dos 90 dias: lotes com foto e cidade real recebendo contato de comprador que não é o próprio time. Sem isso, o funil está girando em falso e o próximo real deve ir para densidade, não para funcionalidade nova.

---

## 6. Plano de ação e quick wins

### North Star

**Conversas iniciadas por semana em lote confiável.**

No sistema, isso já tem rastro: `quantidadeCliquesContato` em anúncio `ATIVO`, com ao menos uma foto e com cidade diferente de `A confirmar - GO`. Um clique é o comprador pedindo o WhatsApp do vendedor. É o momento em que o CompreGado cria valor que o grupo, sozinho, não organiza.

Não usar como norte: download na Play Store, cadastro, nem quantidade bruta de cards pescados. Download sem conversa é vaidade. Card sem foto é estoque ilusório.

Métricas de apoio, nesta ordem:

1. Reivindicações em até 72 horas depois da mensagem do Chip 3, sobre mensagens enviadas.
2. Alertas de Radar que receberam um lote compatível e geraram abertura do link.
3. Lotes marcados como vendidos que tinham pelo menos um clique de contato. Esse é o único “vendido pelo CompreGado” que a marca pode repetir em público.

### Três movimentos para o lançamento em Formosa e Entorno

**1. Subir só o que um comprador encaminharia.** Antes de divulgar o site, a vitrine pública precisa esconder (ou não criar) lote sem foto, com preço zero ou com cidade a confirmar. Os primeiros 30 lotes devem ser de gente conhecida na praça, com foto de dia e cidade real: Formosa, Planaltina de Goiás, Padre Bernardo, Flores de Goiás, Cabeceiras, Planaltina/DF. Densidade em seis municípios vale mais do que presença em Goiás inteiro. O scraper pode continuar gravando; o que aparece na capa é outra decisão.

**2. Tratar o Chip 3 como relacionamento, não como disparo.** Começar por um ou dois grupos em que alguém do time já está e pode responder se o produtor devolver a mensagem. A primeira frase útil já está no produto (“publiquei o seu lote”, “é grátis”, “você fala no seu WhatsApp”). Completar com a cidade e uma foto, nem que a foto seja colocada à mão nas primeiras semanas. Meta da semana 1: 20 conversas humanas, não 200 envios. Se o produtor responder “tira isso do ar”, sair na hora. Essa obediência vira reputação na loja agropecuária.

**3. Levar comprador para a busca vazia de propósito.** O Radar só nasce quando a pessoa procura e não acha. Na semana de lançamento, publicar conteúdo local (status de WhatsApp, grupo de compradores, balcão de uma casa agropecuária parceira) com buscas específicas: “bezerro Nelore em Formosa”, “vaca Girolando no Entorno”. Quem não achar deixa o número. Quem achar fala com o vendedor. Os dois resultados alimentam o norte. Evitar anúncio genérico “baixe o app de gado”. O criativo é o lote, com foto, preço e cidade.

### Monetização futura, na ordem em que o mercado aguenta

A isenção de comissão permanece. O que se cobra é audiência e ferramenta para quem vive de anunciar, não um pedaço da venda da vaca.

| Caminho | Quando | Como encaixa no que já existe |
| --- | --- | --- |
| Vendedor PRO | Assim que 10 produtores passarem de 3 anúncios ativos | O limite, o alerta “Limite Atingido!” e o WhatsApp de assinatura já estão no app. Nos primeiros meses, ativar `is_premium` à mão, com vencimento. Sem cartão no app até o piloto provar que alguém paga |
| Destaque de lote | Quando a vitrine tiver mais anúncio do que cabe na primeira tela | Taxa avulsa por lote no topo, por poucos dias. Combinado no WhatsApp, como o PRO. Serve para quem tem pressa (lote grande, saída de pasto) |
| Casa de ração, farmácia veterinária, leiloeira | Quando compradores voltarem toda semana | Card patrocinado na vitrine, geolocalizado em Formosa e Entorno. Elas já têm o relacionamento com o produtor e podem ser o canal de distribuição do app, não só o anunciante |
| Freteiro de gado | Quando existir rota recorrente de lote fechado | Oferta ao lado do anúncio vendido ou da ficha (“precisa de caminhão até o DF?”). Só depois de haver venda de verdade, senão é anúncio de frete num site vazio |
| AdMob e destaque automático | Por último | Banner cedo demais barateia a marca num público que já desconfia de golpe |

O que não fazer no primeiro ano: comissão sobre a cabeça, leilão com pagamento dentro do app, e categoria fora de gado. O foco em bezerro, garrote, vaca e touro é o que permite a vitrine parecer cheia numa região pequena. Abrir para “todo o agro” antes da liquidez de gado repete a síndrome da vitrine vazia em mais prateleiras.

### Sequência sugerida dos 90 dias

- Dias 1–21: grupos locais, grade só com lote apresentável, Chip 3 com resposta humana, North Star contada toda sexta.
- Dias 22–60: Radar anunciado nas buscas que o time sabe que vão falhar, primeiros PROs manuais para quem já publicou 3 vezes, selo de vendido apenas onde houve clique.
- Dias 61–90: decidir. Se lotes confiáveis recebem conversa de gente de fora do time, repetir o mesmo playbook no município vizinho. Se a reivindicação ficar abaixo de 2% e o clique não aparecer, parar mídia e corrigir foto, cidade e o pedágio do login antes de escrever mais código.
