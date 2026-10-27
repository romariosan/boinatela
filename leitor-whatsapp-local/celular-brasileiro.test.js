const { describe, it } = require('node:test');
const assert = require('node:assert/strict');
const { celularBrasileiro } = require('./comum');

describe('celular brasileiro do radar', () => {
  it('aceita celular com DDI e jid', () => {
    assert.equal(celularBrasileiro('5561998411786@c.us'), '61998411786');
  });

  it('rejeita LID e fixo', () => {
    assert.equal(celularBrasileiro('145823940192834@lid'), '');
    assert.equal(celularBrasileiro('6133334444'), '');
  });
});
