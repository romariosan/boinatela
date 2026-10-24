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
    const numero = String(msg.from || '').split('@')[0];
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
    log(AMARELO, `[QR] Aberto em qr-${nome}.html. Aponte a camera para essa janela.`);
    if (!paginaAberta) {
      paginaAberta = true;
      execFile('cmd.exe', ['/c', `start "${papel.titulo}" "${html}"`]);
    }
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

  async function publicarGrupo(msg) {
    if (!WEBHOOK) {
      return;
    }
    const midias = [];
    if (msg.hasMedia) {
      const media = await msg.downloadMedia();
      if (media && media.data && String(media.mimetype || '').startsWith('image/')) {
        midias.push({ tipo: media.mimetype, base64: media.data });
      }
    }
    const texto = msg.body || '';
    if (!texto && midias.length === 0) {
      return;
    }
    let grupoNome = '';
    try {
      const chat = await msg.getChat();
      grupoNome = chat.name || '';
    } catch {
      grupoNome = '';
    }
    const resposta = await fetch(`${API}/api/webhook/radar-ofertas`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'X-Webhook-Token': WEBHOOK,
      },
      body: JSON.stringify({
        grupoId: msg.from,
        grupoNome,
        mensagemId: msg.id && msg.id.id ? msg.id.id : '',
        produtor: String(msg.author || msg.from || '').split('@')[0],
        texto,
        midias,
      }),
    });
    log(resposta.ok ? VERDE : VERMELHO, `[RADAR] HTTP ${resposta.status} grupo ${grupoNome || msg.from}`);
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

module.exports = { iniciar };
