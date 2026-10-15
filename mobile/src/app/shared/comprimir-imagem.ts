/** Lado maior da foto enviada: nítida na tela do celular e com ~10x menos dados que a foto da câmera. */
const LADO_MAXIMO = 1600;
const QUALIDADE_JPEG = 0.82;
/** Abaixo disso e já no tamanho certo, recomprimir só perderia qualidade. */
const TAMANHO_JA_LEVE = 500 * 1024;

/**
 * Reduz a foto no próprio aparelho antes do upload: no 3G do campo, uma foto de 5 MB da câmera não sobe.
 * Se algo falhar (formato não decodificável, navegador antigo), devolve o arquivo original.
 */
export async function comprimirImagem(arquivo: File): Promise<File> {
  if (arquivo.type === 'image/gif') {
    return arquivo;
  }
  const url = URL.createObjectURL(arquivo);
  try {
    const imagem = new Image();
    imagem.src = url;
    await imagem.decode();
    const largura = imagem.naturalWidth;
    const altura = imagem.naturalHeight;
    const escala = Math.min(1, LADO_MAXIMO / Math.max(largura, altura));
    if (escala === 1 && arquivo.size <= TAMANHO_JA_LEVE) {
      return arquivo;
    }

    const canvas = document.createElement('canvas');
    canvas.width = Math.round(largura * escala);
    canvas.height = Math.round(altura * escala);
    const contexto = canvas.getContext('2d');
    if (!contexto) {
      return arquivo;
    }
    // PNG com transparência viraria fundo preto no JPEG.
    contexto.fillStyle = '#ffffff';
    contexto.fillRect(0, 0, canvas.width, canvas.height);
    contexto.drawImage(imagem, 0, 0, canvas.width, canvas.height);

    const blob = await new Promise<Blob | null>(resolve => canvas.toBlob(resolve, 'image/jpeg', QUALIDADE_JPEG));
    if (!blob || blob.size >= arquivo.size) {
      return arquivo;
    }
    const nome = arquivo.name.replace(/\.[^.]+$/, '') + '.jpg';
    return new File([blob], nome, { type: 'image/jpeg', lastModified: Date.now() });
  } catch {
    return arquivo;
  } finally {
    URL.revokeObjectURL(url);
  }
}
