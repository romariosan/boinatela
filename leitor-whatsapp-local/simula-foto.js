const {
  baixarComEstrategias,
  repararIdMensagem,
  descreverErro,
  baixarMidiaNoWhatsApp,
  acumularMidias,
} = require('./comum');

function erroR() {
  const erro = new Error('r');
  erro.name = 'Error';
  return erro;
}

async function main() {
  const mensagem = {
    id: {
      fromMe: false,
      remote: '120363433042599513@g.us',
      id: 'ABC',
      $1: 'false_120363433042599513@g.us_ABC',
    },
  };
  const sid = repararIdMensagem(mensagem);
  console.log('id-reparado', sid === 'false_120363433042599513@g.us_ABC' && mensagem.id._serialized === sid);

  const jpeg = { mimetype: 'image/jpeg', data: 'QUFB' };
  const jpeg2 = { mimetype: 'image/jpeg', data: 'QkJC' };
  const umaFalha = await baixarComEstrategias([
    async () => {
      throw erroR();
    },
    async () => jpeg,
  ]);
  console.log(
    'falha-depois-ok',
    umaFalha.midias.length === 1 && umaFalha.midias[0].tipo === 'image/jpeg' && umaFalha.erro === null
  );

  const sempre = await baixarComEstrategias([
    async () => {
      throw erroR();
    },
    async () => {
      throw erroR();
    },
  ]);
  const detalhe = descreverErro(sempre.erro);
  const envio = { texto: 'vendo vaca', fotos: sempre.midias.length };
  console.log('sempre-falha', sempre.midias.length === 0 && detalhe.startsWith('Error: r') && envio.texto.length > 0);
  console.log(detalhe.split(' | ')[0]);
  console.log('envio', JSON.stringify(envio));

  const erroT = new Error('t');
  erroT.name = 't';
  const depoisDoT = await baixarComEstrategias([
    async () => {
      throw erroT;
    },
    async () => jpeg,
  ]);
  console.log(
    't-nao-e-caminho-feliz',
    depoisDoT.midias.length === 1 && depoisDoT.midias[0].base64 === 'QUFB' && depoisDoT.erro === null
  );

  // Várias fotos do mesmo lote: acumula até 5 e não perde as que já baixaram.
  const lote = acumularMidias([], [{ tipo: 'image/jpeg', base64: 'QUFB' }]);
  const lote2 = acumularMidias(lote, [{ tipo: 'image/jpeg', base64: 'QkJC' }, { tipo: 'image/jpeg', base64: 'Q0ND' }]);
  const loteCheio = acumularMidias(lote2, [
    { tipo: 'image/jpeg', base64: 'RERE' },
    { tipo: 'image/jpeg', base64: 'RUVF' },
    { tipo: 'image/jpeg', base64: 'RkZG' },
  ]);
  console.log(
    'multi-foto',
    lote2.length === 3 && loteCheio.length === 5 && loteCheio[0].base64 === 'QUFB' && loteCheio[4].base64 === 'RUVF'
  );

  // Retentativa: primeira estratégia falha, segunda devolve bytes (caminho do reenvio).
  let tentativas = 0;
  const comRetry = await baixarComEstrategias([
    async () => {
      tentativas += 1;
      throw erroR();
    },
    async () => {
      tentativas += 1;
      return jpeg2;
    },
  ]);
  console.log(
    'retry-ate-ok',
    comRetry.midias.length === 1 && comRetry.midias[0].base64 === 'QkJC' && tentativas === 2
  );

  const mensagemFoto = {
    mediaData: { mediaStage: 'FETCHING' },
    directPath: '/v/t62',
    encFilehash: 'abc',
    filehash: 'def',
    mediaKey: 'key',
    mediaKeyTimestamp: 1,
    type: 'image',
    mimetype: 'image/jpeg',
    async downloadMedia(opcoes) {
      if (!opcoes || !opcoes.downloadQpl || typeof opcoes.downloadQpl.addAnnotations !== 'function') {
        throw new TypeError("Cannot read properties of undefined (reading 'addAnnotations')");
      }
      opcoes.downloadQpl.addAnnotations();
      this.mediaData.mediaStage = 'RESOLVED';
    },
  };
  global.window = {
    require(nome) {
      if (nome === 'WAWebCollections') {
        return {
          Msg: {
            get(id) {
              return id === sid ? mensagemFoto : null;
            },
            async getMessagesById() {
              return { messages: [] };
            },
          },
        };
      }
      if (nome === 'WAWebDownloadManager') {
        return {
          downloadManager: {
            async downloadAndMaybeDecrypt(opcoes) {
              if (!opcoes || !opcoes.downloadQpl || typeof opcoes.downloadQpl.addAnnotations !== 'function') {
                throw new TypeError("Cannot read properties of undefined (reading 'addAnnotations')");
              }
              opcoes.downloadQpl.addAnnotations();
              const mime = opcoes.mimetype || 'application/octet-stream';
              if (opcoes.type === 'image' && mime === 'application/octet-stream') {
                const erro = new Error('t');
                erro.name = 't';
                throw erro;
              }
              return new Uint8Array([255, 216, 255]);
            },
          },
        };
      }
      throw new Error(nome);
    },
    WWebJS: {
      async arrayBufferToBase64Async() {
        return 'QUFB';
      },
    },
  };
  const baixada = await baixarMidiaNoWhatsApp(sid);
  const caminhoFeliz = baixada && baixada.data === 'QUFB' && baixada.mimetype === 'image/jpeg';
  console.log('pagina-com-qpl', caminhoFeliz);
  console.log('sem-erro-t', caminhoFeliz);
  if (!caminhoFeliz) {
    throw new Error('caminho feliz ainda quebra em addAnnotations ou em t');
  }
  if (loteCheio.length !== 5 || comRetry.midias.length !== 1) {
    throw new Error('multi-foto ou retry falhou na simulacao');
  }
}

main()
  .then(() => process.exit(0))
  .catch((erro) => {
    console.error(erro);
    process.exit(1);
  });
