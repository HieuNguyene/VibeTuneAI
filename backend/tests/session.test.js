const test = require('node:test');
const assert = require('node:assert/strict');
const { normalizeVector } = require('../src/services/moodFlow/session');
const { cosineSimilarity } = require('../src/services/moodFlow/candidates');
const config = require('../src/services/moodFlow/config/moodFlow');

test('Session: normalizeVector scales vector to unit length (L2 norm = 1)', () => {
  const vec = [3, 4];
  const normalized = normalizeVector(vec);
  assert.equal(normalized[0], 0.6);
  assert.equal(normalized[1], 0.8);
  const norm = Math.sqrt(normalized[0] * normalized[0] + normalized[1] * normalized[1]);
  assert.ok(Math.abs(norm - 1.0) < 1e-6);
});

test('Session: complete event moves session vector towards listened track', () => {
  const sessionVec = normalizeVector([1, 0, 0]);
  const trackVec = normalizeVector([0, 1, 0]);

  const simBefore = cosineSimilarity(sessionVec, trackVec);

  // Apply complete update formula: S_t+1 = (1 - alpha) * S_t + alpha * v_k
  const alpha = config.learningRate.alphaComplete;
  const updatedVec = normalizeVector(sessionVec.map((val, idx) => (1 - alpha) * val + alpha * trackVec[idx]));

  const simAfter = cosineSimilarity(updatedVec, trackVec);

  assert.ok(simAfter > simBefore, `Cosine similarity towards completed track must increase. Before: ${simBefore}, After: ${simAfter}`);
});

test('Session: skip event (<30s) moves session vector away from skipped track', () => {
  const sessionVec = normalizeVector([0.7, 0.7, 0]);
  const trackVec = normalizeVector([0, 1, 0]);

  const simBefore = cosineSimilarity(sessionVec, trackVec);

  // Apply skip update formula: S_t+1 = S_t - beta * v_k
  const beta = config.learningRate.betaSkip;
  const updatedVec = normalizeVector(sessionVec.map((val, idx) => val - beta * trackVec[idx]));

  const simAfter = cosineSimilarity(updatedVec, trackVec);

  assert.ok(simAfter < simBefore, `Cosine similarity towards skipped track must decrease. Before: ${simBefore}, After: ${simAfter}`);
});
