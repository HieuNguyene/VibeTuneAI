const mongoose = require('mongoose');
const { getNextTrack, initSession, arcs, scoring, selector, reason, config } = require('../src/services/moodFlow');
const { Song } = require('../src/models');

async function testPureModule() {
  try {
    console.log('Connecting to Mongo...');
    await mongoose.connect('mongodb://127.0.0.1:27017/music_app');

    console.log('Testing arcs...');
    const arcWindDown = arcs.getTargetArc('wind_down', 0, 20);
    console.log('Arc wind_down pos 0:', arcWindDown);
    const arcWindDownEnd = arcs.getTargetArc('wind_down', 20, 20);
    console.log('Arc wind_down pos 20:', arcWindDownEnd);

    console.log('\nTesting getNextTrack...');
    const result = await getNextTrack({
      arc: 'wind_down',
      debug: true,
      seed: 42
    });
    console.log('Session ID:', result.sessionId);
    console.log('Track:', result.track.title, 'by', result.track.artistName);
    console.log('Reason:', result.reason);
    console.log('Debug breakdown:', result.debug);

    console.log('\nTesting next track in same session...');
    const result2 = await getNextTrack({
      sessionId: result.sessionId,
      debug: true
    });
    console.log('Track 2:', result2.track.title, 'by', result2.track.artistName);
    console.log('Reason 2:', result2.reason);

    console.log('\nSUCCESS! Pure moodFlow module works perfectly.');
    process.exit(0);
  } catch (err) {
    console.error('Test error:', err);
    process.exit(1);
  }
}

testPureModule();
