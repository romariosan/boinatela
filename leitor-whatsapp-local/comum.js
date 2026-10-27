const { execFile } = require('child_process');
const fs = require('fs');
const path = require('path');
const QRCode = require('qrcode');
const { Client, LocalAuth } = require('whatsapp-web.js');

const VERDE = '\x1b[32m';
const AMARELO = '\x1b[33m';
const VERMELHO = '\x1b[31m';
const RESET = '\x1b[0m';

const PAPEIS = {
  sistema: {
    envia: ['SISTEMA'],
    escutaGrupo: false,
    titulo: 'CHIP SISTEMA',
    texto: 'Envia codigo e alerta. Nao entra em grupo.',
  },
  radar: {
    envia: [],
    escutaGrupo: true,
    titulo: 'CHIP RADAR',
    texto: 'Escuta grupo e publica o anuncio. Nao responde.',
  },
  consultor: {
    envia: ['CONSULTOR'],
    escutaGrupo: false,
    titulo: 'CHIP CONSULTOR',
    texto: 'Avisa o produtor no privado. Nao entra em grupo.',
  },
};

/** Mesma regra de PhoneUtils.celularBrasileiro. LID e fixo voltam vazio. */
function celularBrasileiro(valor) {
  let digitos = String(valor || '').split('@')[0].split(':')[0].replace(/\D/g, '');
  if (digitos.startsWith('55') && (digitos.length === 12 || digitos.length === 13)) {
    digitos = digitos.substring(2);
  }
  if (digitos.length === 10 && digitos.charAt(2) >= '6' && digitos.charAt(2) <= '9') {
    digitos = digitos.substring(0, 2) + '9' + digitos.substring(2);
  }
  if (digitos.length !== 11 || digitos.charAt(2) !== '9') {
    return '';
  }
  const ddd = Number(digitos.slice(0, 2));
  if (!Number.isInteger(ddd) || ddd < 11 || ddd > 99) {
    return '';
  }
  return digitos;
}

function iniciar(nome) {
  carregarEnv();
  const papel = PAPEIS[nome];
  if (!papel) {
    throw new Error('Chip desconhecido: ' + nome);
  }

  const API = (process.env.API_URL || 'https://appcompregado.com.br').replace(/\/$/, '');
  const ROBO = process.env.ROBO_TOKEN || '';
  const WEBHOOK = process.env.WHATSAPP_WEBHOOK_TOKEN || '';
  const png = path.join(__dirname, `qr-${nome}.png`);
  const html = path.join(__dirname, `qr-${nome}.html`);
  let paginaAberta = false;
  let ocupado = false;
  process.title = papel.titulo;

  function log(cor, texto) {
    console.log(`${cor}[${papel.titulo}] ${texto}${RESET}`);
  }

  const client = new Client({
    authStrategy: new LocalAuth({
      clientId: nome,
      dataPath: path.join(__dirname, 'sessoes'),
    }),
    puppeteer: {
      headless: false,
      args: ['--no-sandbox', '--disable-setuid-sandbox'],
    },
  });

  log(AMARELO, papel.texto);
  log(AMARELO, 'Abrindo o Chrome deste chip...');
  if (papel.envia.length && !ROBO) {
    log(AMARELO, 'Sem ROBO_TOKEN no .env. O QR conecta, mas nada e enviado.');
  }
  if (papel.escutaGrupo && !WEBHOOK) {
    log(AMARELO, 'Sem WHATSAPP_WEBHOOK_TOKEN no .env. O grupo nao vira anuncio.');
  }

  client.on('loading_screen', (percentual, mensagem) => {
    log(AMARELO, `[CARREGANDO] ${percentual}% ${mensagem || ''}`.trim());
  });

  client.on('qr', (qr) => {
    publicarQr(qr).catch((erro) => log(VERMELHO, `[QR] ${erro.message}`));
  });

  client.on('authenticated', () => {
    log(VERDE, '[QR] Leitura aceita.');
    escreverPagina('Leitura aceita. Terminando de abrir o WhatsApp.', null);
  });

  client.on('ready', () => {
    log(VERDE, '[CONECTADO] WhatsApp Web esta pronto.');
    escreverPagina('Conectado. Pode fechar esta janela do QR.', null);
    if (papel.envia.length) {
      log(VERDE, `[CONECTADO] Fila ${papel.envia.join(', ')} a cada 3s.`);
      setInterval(() => {
        if (ocupado) {
          return;
        }
        buscarFila().catch((erro) => log(VERMELHO, `[FILA] ${erro.message}`));
      }, 3000);
    } else {
      log(VERDE, '[CONECTADO] Escutando grupos. Este chip nao envia mensagem.');
    }
  });

  client.on('auth_failure', (mensagem) => {
    log(VERMELHO, `[FALHA] O QR Code nao conectou: ${mensagem}`);
  });

  client.on('change_state', (estado) => {
    log(AMARELO, `[ESTADO] ${estado}`);
  });

  client.on('disconnected', (motivo) => {
    log(VERMELHO, `[QUEDA] Sessao encerrada: ${motivo}`);
  });

  client.on('message', (msg) => {
    const deGrupo = String(msg.from || '').endsWith('@g.us');
    const numero = String((deGrupo ? msg.author : msg.from) || msg.from || '').split('@')[0];
    log(RESET, `[MENSAGEM] De: ${numero}, Texto: ${msg.body || ''}`);
    if (!deGrupo && nome === 'consultor') {
      if (!msg.fromMe) {
        publicarEntrada(msg).catch((erro) => log(VERMELHO, `[ENTRADA] ${erro.message}`));
      }
      return;
    }
    if (!deGrupo || !papel.escutaGrupo) {
      if (deGrupo) {
        log(AMARELO, '[GRUPO] Este chip nao escuta grupo. Tire este numero dos grupos.');
      }
      return;
    }
    publicarGrupo(msg).catch((erro) => log(VERMELHO, `[RADAR] ${erro.message}`));
  });

  client.initialize().catch((erro) => {
    log(VERMELHO, `[FALHA] Nao foi possivel abrir o WhatsApp Web: ${erro.message}`);
  });

  async function publicarQr(qr) {
    await QRCode.toFile(png, qr, { width: 560, margin: 2 });
    escreverPagina('No celular: Aparelhos conectados, depois Conectar um aparelho.', `qr-${nome}.png?v=${Date.now()}`);
    log(AMARELO, `[QR] Pagina pronta: ${html}`);
    if (!paginaAberta) {
      abrirPaginaQr();
    }
  }

  /**
   * Abre o HTML no app padrão.
   * Nunca passe o título do chip ("CHIP RADAR") como executável: o Windows
   * mostra "nao pode encontrar" e o QR nao abre. Start-Process -FilePath no
   * arquivo .html usa a associacao do sistema (browser). Em PS antigo nao
   * existe -LiteralPath no Start-Process.
   */
  function abrirPaginaQr() {
    const caminhoPs = String(html).replace(/'/g, "''");
    execFile(
      'powershell.exe',
      [
        '-NoProfile',
        '-ExecutionPolicy',
        'Bypass',
        '-Command',
        `Start-Process -FilePath '${caminhoPs}'`,
      ],
      (erro) => {
        if (erro) {
          // Fallback: titulo vazio no `start` do cmd (1o "..." seria o titulo).
          execFile('cmd.exe', ['/c', 'start', '', html], (erro2) => {
            if (erro2) {
              log(VERMELHO, `[QR] Nao abriu a janela. Abra manualmente: ${html}`);
              return;
            }
            paginaAberta = true;
            log(AMARELO, `[QR] Janela aberta. Aponte a camera para qr-${nome}.html.`);
          });
          return;
        }
        paginaAberta = true;
        log(AMARELO, `[QR] Janela aberta. Aponte a camera para qr-${nome}.html.`);
      }
    );
  }

  function escreverPagina(instrucao, imagem) {
    const figura = imagem
      ? `<img src="${imagem}" width="560" height="560" alt="QR ${papel.titulo}">`
      : '';
    const atualizar = imagem ? '<meta http-equiv="refresh" content="2">' : '';
    fs.writeFileSync(
      html,
      `<!doctype html>
<html lang="pt-BR">
<head>
  <meta charset="utf-8">
  ${atualizar}
  <title>${papel.titulo}</title>
</head>
<body style="margin:0;background:#111;color:#fff;font-family:sans-serif;text-align:center">
  <h1 style="margin:24px 0 8px">${papel.titulo}</h1>
  <p style="font-size:20px">${instrucao}</p>
  ${figura}
</body>
</html>`
    );
  }

  // Mesmo autor+grupo: junta texto e até 5 fotos. Após a ÚLTIMA mensagem espera ~35s
  // para o produtor mandar mais fotos; o lote inteiro vive no máximo 3 min.
  const JANELA_LOTE_MS = 3 * 60 * 1000;
  const ESPERA_APOS_ULTIMA_MS = 35000;
  const FOTO_TENTATIVAS_IMEDIATAS = 5;
  const FOTO_REENVIO_MAX = 8;
  const FOTO_REENVIO_BASE_MS = 8000;
  const lotesGrupo = new Map();

  async function publicarGrupo(msg) {
    if (!WEBHOOK) {
      return;
    }
    const chave = `${msg.from}|${msg.author || msg.from}`;
    const aberto = lotesGrupo.get(chave);
    if (aberto && aberto.timer) {
      clearTimeout(aberto.timer);
      aberto.timer = null;
    }
    const texto = msg.body || '';
    const captura = await capturarMidiaDaMensagem(msg);
    if (!texto && captura.midias.length === 0 && !captura.pendente) {
      const parado = lotesGrupo.get(chave);
      if (parado) {
        armarEnvio(parado, chave);
      }
      return;
    }
    const agora = Date.now();
    let lote = lotesGrupo.get(chave);
    if (lote && agora - lote.inicio > JANELA_LOTE_MS) {
      clearTimeout(lote.timer);
      lotesGrupo.delete(chave);
      enviarLote(lote).catch((erro) => log(VERMELHO, `[RADAR] ${textoErro(erro)}`));
      lote = null;
    }
    if (!lote) {
      lote = { inicio: agora, textos: [], midias: [], pendentes: [], msg, timer: null };
      lotesGrupo.set(chave, lote);
    }
    if (texto) {
      lote.textos.push(texto);
    }
    for (const midia of captura.midias) {
      if (lote.midias.length >= 5) {
        break;
      }
      lote.midias.push(midia);
    }
    if (captura.pendente) {
      lote.pendentes.push(captura.pendente);
      log(AMARELO, `[RADAR] Foto pendente (vai retentar). Restam ${lote.pendentes.length}.`);
    }
    if (captura.midias.length > 0) {
      log(VERDE, `[RADAR] Foto ok no lote (${lote.midias.length}/5).`);
    }
    lote.msg = msg;
    armarEnvio(lote, chave);
  }

  function armarEnvio(lote, chave) {
    if (lote.timer) {
      clearTimeout(lote.timer);
    }
    const restante = JANELA_LOTE_MS - (Date.now() - lote.inicio);
    lote.timer = setTimeout(() => {
      if (lotesGrupo.get(chave) !== lote) {
        return;
      }
      lotesGrupo.delete(chave);
      enviarLote(lote).catch((erro) => log(VERMELHO, `[RADAR] ${textoErro(erro)}`));
    }, Math.min(ESPERA_APOS_ULTIMA_MS, Math.max(0, restante)));
  }

  /** Baixa uma mensagem de imagem; se falhar, devolve pendente para retentativa. */
  async function capturarMidiaDaMensagem(msg) {
    const pareceFoto = msg.hasMedia || msg.type === 'image' || msg.type === 'sticker';
    if (!pareceFoto) {
      return { midias: [], pendente: null };
    }
    const sid = repararIdMensagem(msg);
    const resultado = await baixarImagensComRetentativas(msg, sid, FOTO_TENTATIVAS_IMEDIATAS);
    if (resultado.midias.length > 0) {
      return { midias: resultado.midias, pendente: null };
    }
    const detalhe = resultado.erro ? descreverErro(resultado.erro) : 'resposta sem imagem';
    log(VERMELHO, `[RADAR] Foto nao baixou agora: ${detalhe}`);
    return { midias: [], pendente: { msg, sid } };
  }

  async function baixarImagensComRetentativas(msg, sid, maxTentativas) {
    let ultimo = null;
    for (let tentativa = 1; tentativa <= maxTentativas; tentativa++) {
      const resultado = await baixarComEstrategias([
        () => baixarPelaPagina(sid || repararIdMensagem(msg)),
        () => baixarPelaBiblioteca(msg),
      ]);
      if (resultado.midias.length > 0) {
        return resultado;
      }
      ultimo = resultado.erro;
      if (tentativa < maxTentativas) {
        await new Promise((resolve) => setTimeout(resolve, 1000 * tentativa));
      }
    }
    return { midias: [], erro: ultimo };
  }

  async function baixarPelaPagina(sid) {
    if (!sid || !client.pupPage) {
      throw new Error('mensagem sem id para baixar a foto');
    }
    const result = await client.pupPage.evaluate(baixarMidiaNoWhatsApp, sid);
    if (!result || !result.data) {
      return undefined;
    }
    return { data: result.data, mimetype: result.mimetype || 'image/jpeg' };
  }

  async function baixarPelaBiblioteca(msg) {
    if (!msg || typeof msg.downloadMedia !== 'function') {
      throw new Error('biblioteca sem download');
    }
    repararIdMensagem(msg);
    const media = await msg.downloadMedia();
    if (!media || !media.data) {
      return undefined;
    }
    return { data: media.data, mimetype: media.mimetype || 'image/jpeg' };
  }

  async function esgotarPendentes(lote, rodadas) {
    for (let r = 0; r < rodadas && lote.pendentes.length > 0 && lote.midias.length < 5; r++) {
      const fila = lote.pendentes.splice(0, lote.pendentes.length);
      for (const item of fila) {
        if (lote.midias.length >= 5) {
          lote.pendentes.push(item);
          break;
        }
        const sid = item.sid || repararIdMensagem(item.msg);
        const resultado = await baixarImagensComRetentativas(item.msg, sid, 3);
        if (resultado.midias.length > 0) {
          lote.midias.push(...resultado.midias);
          log(VERDE, `[RADAR] Foto recuperada na retentativa (${lote.midias.length}/5).`);
        } else {
          lote.pendentes.push({ msg: item.msg, sid });
        }
      }
      if (lote.pendentes.length > 0 && r + 1 < rodadas) {
        await new Promise((resolve) => setTimeout(resolve, 2000 * (r + 1)));
      }
    }
  }

  async function enviarLote(lote) {
    await esgotarPendentes(lote, 2);
    const msg = lote.msg;
    const texto = lote.textos.join('\n').trim();
    const midias = lote.midias.slice(0, 5);
    if (!texto && midias.length === 0) {
      if (lote.pendentes.length > 0) {
        agendarRetentativaFoto({
          tentativa: 0,
          pendentes: lote.pendentes.slice(),
          msg,
          textos: lote.textos.slice(),
        });
      }
      return;
    }
    let grupoNome = '';
    try {
      const chat = await msg.getChat();
      grupoNome = chat.name || '';
    } catch {
      grupoNome = '';
    }
    const produtor = await telefoneDoAutor(msg);
    if (!produtor) {
      log(AMARELO, `[RADAR] Autor sem celular brasileiro. Grupo ${grupoNome || msg.from} nao foi enviado.`);
      return;
    }
    await postarOferta({
      msg,
      grupoNome,
      produtor,
      texto,
      midias,
      sufixoId: '',
    });
    if (lote.pendentes.length > 0) {
      agendarRetentativaFoto({
        tentativa: 0,
        pendentes: lote.pendentes.slice(),
        msg,
        textos: lote.textos.slice(),
        grupoNome,
        produtor,
      });
    }
  }

  async function postarOferta({ msg, grupoNome, produtor, texto, midias, sufixoId }) {
    const baseId = msg.id && msg.id.id != null ? String(msg.id.id) : '';
    const mensagemId = `${baseId || 'radar'}${sufixoId || ''}`;
    const resposta = await fetch(`${API}/api/webhook/radar-ofertas`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'X-Webhook-Token': WEBHOOK,
      },
      body: JSON.stringify({
        grupoId: msg.from,
        grupoNome: grupoNome || '',
        mensagemId,
        produtor,
        texto: texto || '',
        midias,
      }),
    });
    const corpoResposta = await resposta.text();
    const resumo = corpoResposta.replace(/\s+/g, ' ').slice(0, 160);
    log(
      resposta.ok ? VERDE : VERMELHO,
      `[RADAR] HTTP ${resposta.status} ${resumo} fotos=${midias.length} grupo ${grupoNome || msg.from}`
    );
    return resposta.ok;
  }

  function agendarRetentativaFoto(ctx) {
    if (ctx.tentativa >= FOTO_REENVIO_MAX || !ctx.pendentes || ctx.pendentes.length === 0) {
      if (ctx.tentativa >= FOTO_REENVIO_MAX) {
        log(VERMELHO, `[RADAR] Foto esgotou ${FOTO_REENVIO_MAX} reenvios. Texto ja pode estar no rascunho.`);
      }
      return;
    }
    const espera = FOTO_REENVIO_BASE_MS * Math.min(ctx.tentativa + 1, 5);
    log(AMARELO, `[RADAR] Retentativa de foto #${ctx.tentativa + 1} em ${Math.round(espera / 1000)}s (${ctx.pendentes.length} pendente(s)).`);
    setTimeout(() => {
      retentarFotosDepois(ctx).catch((erro) => log(VERMELHO, `[RADAR] Retentativa: ${textoErro(erro)}`));
    }, espera);
  }

  async function retentarFotosDepois(ctx) {
    const lote = {
      midias: [],
      pendentes: ctx.pendentes.slice(),
      textos: ctx.textos || [],
      msg: ctx.msg,
    };
    await esgotarPendentes(lote, 3);
    let grupoNome = ctx.grupoNome || '';
    let produtor = ctx.produtor || '';
    if (lote.midias.length > 0) {
      if (!produtor) {
        produtor = await telefoneDoAutor(ctx.msg);
      }
      if (!grupoNome) {
        try {
          const chat = await ctx.msg.getChat();
          grupoNome = chat.name || '';
        } catch {
          grupoNome = '';
        }
      }
      if (produtor) {
        // Texto vazio: a API atualiza o mesmo anúncio (14 dias) e anexa as fotos.
        await postarOferta({
          msg: ctx.msg,
          grupoNome,
          produtor,
          texto: '',
          midias: lote.midias.slice(0, 5),
          sufixoId: `-foto${Date.now()}`,
        });
      }
    }
    if (lote.pendentes.length > 0) {
      agendarRetentativaFoto({
        tentativa: ctx.tentativa + 1,
        pendentes: lote.pendentes,
        msg: ctx.msg,
        textos: ctx.textos,
        grupoNome,
        produtor,
      });
    }
  }

  function textoErro(erro) {
    if (!erro) {
      return 'erro desconhecido';
    }
    return erro.message || String(erro);
  }

  async function telefoneDoAutor(msg) {
    const autor = String(msg.author || '');
    const direto = celularBrasileiro(autor);
    if (direto) {
      return direto;
    }
    const jid = autor.includes('@') ? autor : '';
    if (jid) {
      try {
        const pares = await client.getContactLidAndPhone([jid]);
        const pn = pares && pares[0] && pares[0].pn;
        const resolvido = celularBrasileiro(pn);
        if (resolvido) {
          return resolvido;
        }
      } catch (erro) {
        log(AMARELO, `[RADAR] Nao achei o celular do autor: ${erro.message}`);
      }
    }
    try {
      const contato = await msg.getContact();
      const id = contato && contato.id;
      const candidato = id && id.server === 'c.us' ? id._serialized || id.user : '';
      const resolvido = celularBrasileiro(candidato);
      if (resolvido) {
        return resolvido;
      }
    } catch (erro) {
      log(AMARELO, `[RADAR] Contato sem celular: ${erro.message}`);
    }
    return '';
  }

  async function publicarEntrada(msg) {
    if (!ROBO) {
      return;
    }
    const texto = msg.body || '';
    if (!texto) {
      return;
    }
    const telefone = String(msg.from || '').split('@')[0];
    const mensagemId = msg.id && msg.id.id ? msg.id.id : '';
    const resposta = await fetch(`${API}/api/robo/entrada`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'X-Robo-Token': ROBO,
      },
      body: JSON.stringify({ telefone, texto, mensagemId }),
    });
    log(resposta.ok ? VERDE : VERMELHO, `[ENTRADA] HTTP ${resposta.status}`);
  }

  async function buscarFila() {
    if (ocupado || !ROBO) {
      return;
    }
    ocupado = true;
    try {
      await drenarFila();
    } finally {
      ocupado = false;
    }
  }

  async function drenarFila() {
    for (const chip of papel.envia) {
      const resposta = await fetch(`${API}/api/robo/fila?chip=${encodeURIComponent(chip)}`, {
        headers: { 'X-Robo-Token': ROBO },
      });
      if (!resposta.ok) {
        log(VERMELHO, `[FILA] ${chip} HTTP ${resposta.status}`);
        continue;
      }
      const itens = await resposta.json();
      for (const item of itens) {
        if (String(item.chip || '').toUpperCase() === chip) {
          await enviarItem(item);
        }
      }
    }
  }

  async function enviarItem(item) {
    const digitos = String(item.numero || '').replace(/\D/g, '');
    if (!digitos || digitos.endsWith('@g.us')) {
      await marcarFalha(item.id);
      log(VERMELHO, `[FILA] Recusei envio para grupo: ${item.numero}`);
      return;
    }
    const meu = client.info && client.info.wid ? String(client.info.wid.user || '') : '';
    if (meu && (digitos.endsWith(meu) || meu.endsWith(digitos.slice(-11)))) {
      await marcarFalha(item.id);
      log(VERMELHO, '[FALHA ENVIO] O chip sistema está no mesmo número que deveria receber o código. Conecte outro WhatsApp em sistema.js.');
      return;
    }
    try {
      const destino = await resolverDestino(digitos);
      await client.sendMessage(destino, item.texto);
      await fetch(`${API}/api/robo/fila/${item.id}/enviada`, {
        method: 'POST',
        headers: { 'X-Robo-Token': ROBO },
      });
      log(VERDE, `[ENVIADA] ${item.chip} para ${digitos}`);
    } catch (erro) {
      await marcarFalha(item.id);
      const mesmo = /No LID for user/i.test(erro.message)
        ? 'O WhatsApp não abriu a conversa com esse número. Confira se o chip sistema é outro celular, não o que vai receber o código.'
        : erro.message;
      log(VERMELHO, `[FALHA ENVIO] ${digitos}: ${mesmo}`);
    }
  }

  async function resolverDestino(digitos) {
    const cUs = `${digitos}@c.us`;
    const achado = await client.pupPage.evaluate(async (userId) => {
      const fabrica = window.require('WAWebWidFactory');
      const wid = fabrica.createWid(userId);
      let alvo = wid;
      try {
        const consulta = await window.require('WAWebQueryExistsJob').queryWidExists(wid);
        if (consulta && consulta.wid) {
          alvo = consulta.wid;
        }
      } catch (ignorado) {
        alvo = wid;
      }
      let lid = null;
      try {
        lid = window.require('WAWebApiContact').getCurrentLid(alvo);
      } catch (ignorado) {
        lid = null;
      }
      return {
        lid: lid && lid._serialized ? lid._serialized : '',
        wid: alvo && alvo._serialized ? alvo._serialized : '',
      };
    }, cUs);
    if (achado.lid) {
      return achado.lid;
    }
    if (achado.wid && achado.wid.endsWith('@lid')) {
      return achado.wid;
    }
    const pares = await client.getContactLidAndPhone([cUs]).catch(() => []);
    const par = Array.isArray(pares) ? pares[0] : null;
    if (par && par.lid) {
      return par.lid;
    }
    if (achado.wid) {
      return achado.wid;
    }
    throw new Error('Número sem conversa no WhatsApp.');
  }

  async function marcarFalha(id) {
    await fetch(`${API}/api/robo/fila/${id}/falhou`, {
      method: 'POST',
      headers: { 'X-Robo-Token': ROBO },
    }).catch(() => {});
  }
}

function carregarEnv() {
  const arquivo = path.join(__dirname, '.env');
  if (!fs.existsSync(arquivo)) {
    return;
  }
  for (const linha of fs.readFileSync(arquivo, 'utf8').split(/\r?\n/)) {
    const texto = linha.trim();
    if (!texto || texto.startsWith('#') || !texto.includes('=')) {
      continue;
    }
    const corte = texto.indexOf('=');
    const chave = texto.slice(0, corte).trim();
    let valor = texto.slice(corte + 1).trim();
    if (
      (valor.startsWith('"') && valor.endsWith('"')) ||
      (valor.startsWith("'") && valor.endsWith("'"))
    ) {
      valor = valor.slice(1, -1);
    }
    if (chave && process.env[chave] == null) {
      process.env[chave] = valor;
    }
  }
}

async function baixarComEstrategias(estrategias, opcoes = {}) {
  const lista = Array.isArray(estrategias) ? estrategias : [];
  const dormir = opcoes.dormir || ((ms) => new Promise((resolve) => setTimeout(resolve, ms)));
  const esperaMs = opcoes.esperaMs ?? 0;
  let ultimo = null;
  for (let i = 0; i < lista.length; i++) {
    try {
      const media = await lista[i]();
      if (media && media.data && String(media.mimetype || '').startsWith('image/')) {
        return {
          midias: [{ tipo: media.mimetype, base64: media.data }],
          erro: null,
          tentativas: i + 1,
        };
      }
    } catch (erro) {
      ultimo = erro;
    }
    if (i < lista.length - 1 && esperaMs > 0) {
      await dormir(esperaMs);
    }
  }
  return { midias: [], erro: ultimo, tentativas: lista.length };
}

function repararIdMensagem(msg) {
  const id = msg && msg.id;
  if (!id || typeof id !== 'object') {
    return '';
  }
  if (typeof id._serialized === 'string' && id._serialized) {
    return id._serialized;
  }
  const renomeado = id['$1'];
  if (typeof renomeado === 'string' && renomeado) {
    id._serialized = renomeado;
    return renomeado;
  }
  if (id.remote && id.id != null && id.fromMe != null) {
    const base = `${id.fromMe}_${id.remote}_${id.id}`;
    const participante = id.participant || msg.author || '';
    id._serialized = participante ? `${base}_${participante}` : base;
    return id._serialized;
  }
  return '';
}

function descreverErro(erro) {
  if (!erro) {
    return 'erro desconhecido';
  }
  const nome = erro.name || 'Error';
  const mensagem = erro.message || String(erro);
  const pilha = erro.stack ? String(erro.stack).split('\n').slice(0, 4).join(' | ') : '';
  return pilha ? `${nome}: ${mensagem} | ${pilha}` : `${nome}: ${mensagem}`;
}

/**
 * Roda dentro da página do WhatsApp.
 * O download exige downloadQpl.addAnnotations. Sem o mimetype declarado, o WhatsApp
 * assume application/octet-stream e lança o erro minificado "t" (InvalidMediaFileType).
 */
async function baixarMidiaNoWhatsApp(msgId) {
  const colecao = window.require('WAWebCollections').Msg;
  let encontrado = colecao.get(msgId);
  if (!encontrado) {
    const busca = await colecao.getMessagesById([msgId]);
    encontrado = busca && busca.messages && busca.messages[0];
  }
  if (!encontrado || !encontrado.mediaData) {
    return null;
  }
  for (let espera = 0; espera < 6; espera++) {
    if (encontrado.mediaData.mediaStage === 'REUPLOADING') {
      return null;
    }
    if (
      encontrado.mediaData.mediaStage === 'RESOLVED'
      || (encontrado.mediaData.mediaBlob && typeof encontrado.mediaData.mediaBlob.arrayBuffer === 'function')
      || encontrado.directPath
    ) {
      break;
    }
    await new Promise((resolve) => setTimeout(resolve, 1000));
    const deNovo = colecao.get(msgId);
    if (deNovo && deNovo.mediaData) {
      encontrado = deNovo;
    }
  }
  if (encontrado.mediaData.mediaStage === 'REUPLOADING') {
    return null;
  }
  const qpl = {
    addAnnotations() {
      return this;
    },
    addPoint() {
      return this;
    },
  };
  const declarado = encontrado.mimetype;
  const mime = typeof declarado === 'string' && declarado.startsWith('image/')
    ? declarado
    : 'image/jpeg';
  if (encontrado.mediaData.mediaStage !== 'RESOLVED' && typeof encontrado.downloadMedia === 'function') {
    try {
      await encontrado.downloadMedia({
        downloadEvenIfExpensive: true,
        rmrReason: 1,
        downloadQpl: qpl,
      });
    } catch {
      /* a descriptografia abaixo ainda pode ler a mídia já recebida */
    }
  }
  const blobPronto = encontrado.mediaData.mediaBlob;
  if (blobPronto && typeof blobPronto.arrayBuffer === 'function') {
    const bruto = await blobPronto.arrayBuffer();
    const pronto = await window.WWebJS.arrayBufferToBase64Async(bruto);
    if (pronto) {
      return { data: pronto, mimetype: mime };
    }
  }
  const decrypted = await window.require('WAWebDownloadManager').downloadManager.downloadAndMaybeDecrypt({
    directPath: encontrado.directPath,
    encFilehash: encontrado.encFilehash,
    filehash: encontrado.filehash,
    mediaKey: encontrado.mediaKey,
    mediaKeyTimestamp: encontrado.mediaKeyTimestamp,
    type: encontrado.type,
    mimetype: mime,
    signal: new AbortController().signal,
    downloadQpl: qpl,
  });
  const data = await window.WWebJS.arrayBufferToBase64Async(decrypted);
  return { data, mimetype: mime };
}

/** Junta midias novas no lote sem passar de 5 (várias fotos do mesmo produtor). */
function acumularMidias(existentes, novas, limite = 5) {
  const lista = Array.isArray(existentes) ? existentes.slice() : [];
  for (const midia of Array.isArray(novas) ? novas : []) {
    if (lista.length >= limite) {
      break;
    }
    if (midia && midia.base64) {
      lista.push(midia);
    }
  }
  return lista;
}

module.exports = {
  iniciar,
  celularBrasileiro,
  baixarComEstrategias,
  repararIdMensagem,
  descreverErro,
  baixarMidiaNoWhatsApp,
  acumularMidias,
};
