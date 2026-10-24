# A história dos três chips

Esta é a história de como um anúncio de gado sai de um grupo de WhatsApp e vira uma conversa com o produtor. São três números, cada um com um papel. Nenhum faz o trabalho do outro.

Os três rodam neste computador, cada um no seu Chrome. A memória da conversa fica na API, em `https://appcompregado.com.br`.

## Os três personagens

### Chip Radar — o ouvinte

Ele entra nos grupos de pecuária de Formosa e da região. Lê. Não responde ninguém. Não manda mensagem no privado.

Quando alguém posta um lote, o Radar manda o texto e as fotos para a API. A API tenta entender o anúncio: raça, quantidade, preço, cidade. Quem faz essa leitura é a Gemini. Se ela falhar, o anúncio ainda é gravado, com título genérico, e **não** entra na conversa com o produtor.

O Radar não sabe se o produtor vai ser chamado. Ele só entrega o que viu no grupo.

### Chip Consultor — a voz

Ele não entra em grupo. Fala só no privado, com o número de quem postou o lote.

Ele pergunta à API, de tempos em tempos: “tem mensagem para eu enviar?”. Se tiver, manda uma. Enquanto essa não termina, não pega outra. Quando o produtor responde, o Consultor entrega essa resposta para a API e espera o próximo texto.

A primeira frase sempre diz que é a equipe do CompreGado, em Formosa. O link do lote só sai depois que o produtor aceita ver.

### Chip Sistema — o porteiro

Ele também não entra em grupo. Não lê anúncio e não aborda produtor.

Ele manda código de verificação, aviso de senha e os alertas da conta. É o número que o aplicativo usa para provar que o WhatsApp da pessoa é dela. Por isso ele não pode ser o mesmo aparelho que aborda o produtor: se esse número for bloqueado, cadastro e verificação param juntos.

## A manhã em que o lote aparece

Imagine o grupo “Pecuária Formosa”. Alguém escreve que tem 30 novilhas Nelore e manda foto.

1. O **Radar** vê a mensagem e entrega para a API.
2. A API guarda o anúncio na vitrine. A Gemini só ajuda a preencher os dados. O texto da conversa ela não escreve nessa hora.
3. Se o anúncio ficou publicado, a origem é WhatsApp e o celular de quem postou é válido, nasce uma conversa ligada àquele lote. O estado se chama **capturado**.
4. A API não chama o produtor na hora. Ela deixa a primeira mensagem na fila para um horário de gente: entre 8h e 18h, em Brasília, e nunca antes de duas horas. O estado passa a **aguardando abordagem**.

Se aquele telefone já pediu para sair, a história para aqui. Não há abordagem, nem neste lote nem num lote novo.

Se a pessoa já tinha outra conversa em aberto, a frase não se apresenta de novo. Ela fala do lote novo: “Vi outro lote seu no grupo… posso te mandar o link?”

## A primeira conversa

No horário marcado, o **Consultor** pega a mensagem e envia. Só então o estado vira **quebra-gelo enviado**.

A frase é desta família:

> Boa tarde. Aqui é da equipe do CompreGado. Vi seu lote de 30 Nelore em Cristalina, no grupo Pecuária Cristalina. Ainda tá na mão?

A cidade é a do lote. Em Cabeceiras a frase diz “em Cabeceiras”. Em Formosa, “em Formosa”. Se a cidade não veio no anúncio, ela não inventa o lugar.

Sem link. Sem cargo. Sem palavra de empresa vazia.

## O que o produtor pode responder

A Gemini lê a resposta e só classifica. O texto que volta no WhatsApp é escolhido pela API.

| Ele quis dizer | O que a equipe responde | Para onde a conversa vai |
|---|---|---|
| Ainda está na mão | Conta que o lote está na vitrine e pergunta se pode mandar o link | Continua |
| Sim, manda o link | Manda `https://appcompregado.com.br/lote/{id}` e se oferece para ajustar preço ou telefone | **Link enviado** |
| Já vendeu | “Fechou, então. Tiro o lote da vitrine…” e encerra | Encerrada |
| Não quero | Tira o lote da vitrine, se ele for da conta de captura, e não chama esse número de novo | **Rejeitado** |
| Desconfia, acha golpe | Explica o que é o CompreGado e diz que tira o anúncio se ele não quiser | Continua |
| Pergunta outra coisa | Uma frase curta, se a Gemini devolver um texto limpo. Sem link, se ele ainda não aceitou ver | Continua |

Se a resposta da Gemini vier torta, nada é enviado. A conversa fica quieta em vez de mandar texto errado.

Se ele responder **enquanto a primeira mensagem ainda está na fila**, essa mensagem é cancelada. A equipe responde ao que ele escreveu, sem mandar as duas coisas juntas.

## Se ele não responder

No dia seguinte, em horário comercial, vai **uma** segunda mensagem:

> Aqui é do CompreGado de novo, sobre as 30 Nelore do grupo Pecuária Formosa. Se não quiser o anúncio no ar, é só falar que eu tiro.

Não há terceira. Silêncio depois disso é não.

## Se o link já foi

A conversa não recomeça do zero. O acompanhamento é sobre o anúncio que ele já viu:

> As 30 Nelore continuam na vitrine. Se vendeu, me avisa que eu tiro. Se o preço mudou, eu ajusto.

Aí o endereço do lote pode aparecer de novo.

## Os estados, em ordem

1. **Capturado** — o lote entrou e a conversa nasceu.
2. **Aguardando abordagem** — a primeira frase está marcada para mais tarde.
3. **Quebra-gelo enviado** — o Consultor confirmou que a primeira frase saiu.
4. **Respondido** — o produtor escreveu.
5. **Link enviado** — ele aceitou e o endereço do lote saiu.
6. **Rejeitado** — ele não quis. Esse telefone não é chamado outra vez.

A conversa também pode encerrar sem rejeição, quando ele diz que vendeu.

## O que cada um não faz

- O Radar não chama o produtor.
- O Consultor não lê grupo.
- O Sistema não aborda lote.
- A Gemini não inventa a primeira frase nem o link. Ela classifica o que o produtor escreveu.
- Ninguém manda o link antes do produtor aceitar ver.
