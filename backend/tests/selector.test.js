const test = require('node:test');
const assert = require('node:assert/strict');
const { selectNextTrack } = require('../src/services/moodFlow/selector');

test('Selector: produces deterministic selection when seed is fixed', () => {
  const scoredList = [
    { candidate: { id: 'track-1', title: 'Bài 1' }, totalScore: 0.82, breakdown: {} },
    { candidate: { id: 'track-2', title: 'Bài 2' }, totalScore: 0.78, breakdown: {} },
    { candidate: { id: 'track-3', title: 'Bài 3' }, totalScore: 0.65, breakdown: {} }
  ];

  const run1 = selectNextTrack(scoredList, 0.3, 12345);
  const run2 = selectNextTrack(scoredList, 0.3, 12345);
  const run3 = selectNextTrack(scoredList, 0.3, 12345);

  assert.equal(run1.selected.id, run2.selected.id, 'Deterministic seed must produce identical track');
  assert.equal(run2.selected.id, run3.selected.id, 'Deterministic seed must produce identical track');
});

test('Selector: higher score has highest probability in softmax distribution', () => {
  const scoredList = [
    { candidate: { id: 'high', title: 'High' }, totalScore: 0.95, breakdown: {} },
    { candidate: { id: 'low', title: 'Low' }, totalScore: 0.20, breakdown: {} }
  ];

  const result = selectNextTrack(scoredList, 0.3, 100);
  assert.ok(result.probability > 0.5, 'Top candidate should have dominant probability');
});
