const test = require('node:test');
const assert = require('node:assert/strict');
const { getTargetArc } = require('../src/services/moodFlow/arcs');

test('Arcs: wind_down drops energy from ~0.75 down to ~0.25', () => {
  const start = getTargetArc('wind_down', 0, 20);
  const mid = getTargetArc('wind_down', 10, 20);
  const end = getTargetArc('wind_down', 20, 20);

  assert.ok(start.targetEnergy >= 0.70, `Start energy should be >= 0.70, got ${start.targetEnergy}`);
  assert.ok(start.targetEnergy > mid.targetEnergy, 'Start energy must be higher than mid');
  assert.ok(mid.targetEnergy > end.targetEnergy, 'Mid energy must be higher than end');
  assert.ok(end.targetEnergy <= 0.30, `End energy should be <= 0.30, got ${end.targetEnergy}`);
});

test('Arcs: workout starts warm, peaks above 0.80, then cools down', () => {
  const warm = getTargetArc('workout', 1, 20);
  const peak = getTargetArc('workout', 10, 20);
  const cool = getTargetArc('workout', 20, 20);

  assert.ok(warm.targetEnergy >= 0.60 && warm.targetEnergy <= 0.80, `Warmup energy in [0.6, 0.8], got ${warm.targetEnergy}`);
  assert.ok(peak.targetEnergy >= 0.85, `Peak workout energy >= 0.85, got ${peak.targetEnergy}`);
  assert.ok(cool.targetEnergy <= 0.55, `Cooldown energy <= 0.55, got ${cool.targetEnergy}`);
});

test('Arcs: focus maintains stable moderate energy throughout (~0.40 - 0.46)', () => {
  for (let pos = 0; pos <= 20; pos += 4) {
    const res = getTargetArc('focus', pos, 20);
    assert.ok(res.targetEnergy >= 0.35 && res.targetEnergy <= 0.50, `Focus energy between 0.35 and 0.50 at pos ${pos}, got ${res.targetEnergy}`);
  }
});

test('Arcs: steady keeps baseline within tight variation', () => {
  const baseline = { energy: 0.60, valence: 0.50 };
  for (let pos = 0; pos <= 20; pos += 5) {
    const res = getTargetArc('steady', pos, 20, baseline);
    assert.ok(Math.abs(res.targetEnergy - 0.60) <= 0.05, `Steady energy stays within 0.05 of baseline, got ${res.targetEnergy}`);
  }
});
