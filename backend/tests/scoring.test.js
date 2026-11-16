const test = require('node:test');
const assert = require('node:assert/strict');
const { scoreCandidate, normalizedAudioDistance } = require('../src/services/moodFlow/scoring');
const { cosineSimilarity } = require('../src/services/moodFlow/candidates');

test('Scoring: cosineSimilarity returns 1 for identical vectors and 0.5 for orthogonal', () => {
  const vecA = [1, 0, 0];
  const vecB = [1, 0, 0];
  const vecC = [0, 1, 0];

  assert.equal(cosineSimilarity(vecA, vecB), 1.0);
  assert.equal(cosineSimilarity(vecA, vecC), 0.5); // (0 + 1) / 2 = 0.5 normalized
});

test('Scoring: normalizedAudioDistance returns 0 for identical audio features', () => {
  const af1 = { energy: 0.7, valence: 0.6, bpm: 120, danceability: 0.5 };
  const af2 = { energy: 0.7, valence: 0.6, bpm: 120, danceability: 0.5 };
  const dist = normalizedAudioDistance(af1, af2);
  assert.equal(dist, 0);
});

test('Scoring: applies penalty when candidate is same artist as previous track', () => {
  const candidateA = {
    id: 'song-1',
    artist: { _id: 'artist-jack', name: 'Jack' },
    audioFeatures: { energy: 0.7, valence: 0.6, bpm: 120, danceability: 0.5 },
    embedding: [0.5, 0.5]
  };

  const contextSameArtist = {
    session: { sessionVec: [0.5, 0.5], audioProfile: { energy: 0.7, valence: 0.6, bpm: 120, danceability: 0.5 } },
    targetArc: { targetEnergy: 0.7, targetValence: 0.6 },
    recentArtists: ['artist-jack']
  };

  const contextDifferentArtist = {
    session: { sessionVec: [0.5, 0.5], audioProfile: { energy: 0.7, valence: 0.6, bpm: 120, danceability: 0.5 } },
    targetArc: { targetEnergy: 0.7, targetValence: 0.6 },
    recentArtists: ['artist-sontung']
  };

  const scorePenalized = scoreCandidate(candidateA, contextSameArtist);
  const scoreUnpenalized = scoreCandidate(candidateA, contextDifferentArtist);

  assert.equal(scorePenalized.breakdown.penalty, -0.15, 'Immediate same artist penalty should be -0.15');
  assert.equal(scoreUnpenalized.breakdown.penalty, 0, 'Different artist penalty should be 0');
  assert.ok(scorePenalized.totalScore < scoreUnpenalized.totalScore, 'Penalized score must be lower');
});
