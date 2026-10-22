/**
 * Só o `ng serve`. O APK usa environment.prod.ts e o CapacitorHttp, sem este proxy.
 * A API pública responde 403 "Invalid CORS request" quando o Origin é http://127.0.0.1:4200.
 * Sem o header Origin o mesmo GET /api/anuncios responde 200.
 */
module.exports = {
  '/api': {
    target: 'https://appcompregado.com.br',
    secure: true,
    changeOrigin: true,
    configure(proxy) {
      proxy.on('proxyReq', (proxyReq) => {
        proxyReq.removeHeader('origin');
      });
    },
  },
};
