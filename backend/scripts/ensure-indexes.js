const mongoose = require('mongoose');
const { PlaySession, ListeningEvent, Song } = require('../src/models');
const config = require('../src/config/environment');

async function ensureIndexes() {
  try {
    const mongoUri = config.mongoUri || 'mongodb://127.0.0.1:27017/music_app';
    console.log(`[ensure-indexes] Connecting to MongoDB: ${mongoUri}`);
    await mongoose.connect(mongoUri, {
      useNewUrlParser: true,
      useUnifiedTopology: true
    });

    console.log('[ensure-indexes] Creating indexes for PlaySession...');
    await PlaySession.createIndexes();
    const playSessionIndexes = await PlaySession.collection.indexes();
    console.log('[ensure-indexes] PlaySession indexes:', playSessionIndexes.map(idx => idx.name));

    console.log('[ensure-indexes] Creating indexes for ListeningEvent...');
    await ListeningEvent.createIndexes();
    const eventIndexes = await ListeningEvent.collection.indexes();
    console.log('[ensure-indexes] ListeningEvent indexes:', eventIndexes.map(idx => idx.name));

    console.log('[ensure-indexes] Indexes ensured successfully!');
    process.exit(0);
  } catch (error) {
    console.error('[ensure-indexes] Error:', error);
    process.exit(1);
  }
}

ensureIndexes();
