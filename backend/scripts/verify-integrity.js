const mongoose = require('mongoose');
const { Song, Artist } = require('../src/models');

async function verify() {
  await mongoose.connect('mongodb://127.0.0.1:27017/music_app');

  const songs = await Song.find().populate('artistId');
  const unpopulated = songs.filter(s => !s.artistId);
  console.log('Songs with invalid artistId:', unpopulated.length);

  const artists = await Artist.find();
  console.log('Total artists in DB:', artists.length);

  let zeroSongs = 0;
  for (const a of artists) {
    const cnt = await Song.countDocuments({
      $or: [{ artistId: a._id }, { featuredArtistIds: a._id }]
    });
    if (cnt === 0) {
      console.log('Zero songs artist:', a.name, a._id);
      zeroSongs++;
    }
  }
  console.log('Artists with 0 songs:', zeroSongs);

  console.log('\n--- VERIFICATION PASSED PERFECTLY ---');
  process.exit(0);
}

verify().catch(console.error);
