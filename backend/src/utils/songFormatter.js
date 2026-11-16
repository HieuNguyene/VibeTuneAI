const { formatSongArtistFields } = (() => {
  const OBJECT_ID_REGEX = /^[0-9a-f]{24}$/i;

  function extractArtistNames(list) {
    if (!Array.isArray(list)) return [];
    return list
      .map((entry) => {
        if (entry && typeof entry === 'object' && typeof entry.name === 'string') {
          return entry.name.trim();
        }
        return '';
      })
      .filter((name) => name && !OBJECT_ID_REGEX.test(name));
  }

  function cleanFallbackString(value) {
    if (typeof value !== 'string') return '';
    const trimmed = value.trim();
    if (!trimmed || OBJECT_ID_REGEX.test(trimmed)) return '';
    return trimmed;
  }

  /**
   * Chuẩn hóa trường artistName và composer của bài hát mà không bao giờ rò rỉ raw ObjectId.
   */
  function formatSongArtistFields(song) {
    if (!song || typeof song !== 'object') return song;

    const mainArtistName =
      song.artistId && typeof song.artistId === 'object' && typeof song.artistId.name === 'string'
        ? song.artistId.name.trim()
        : cleanFallbackString(song.artistName);

    const featNames = extractArtistNames(song.featuredArtistIds);
    let computedArtistName = mainArtistName;
    if (computedArtistName && featNames.length > 0 && !computedArtistName.includes(' ft. ')) {
      computedArtistName = `${computedArtistName} ft. ${featNames.join(', ')}`;
    } else if (!computedArtistName && featNames.length > 0) {
      computedArtistName = featNames.join(', ');
    }

    const composerNames = extractArtistNames(song.composerIds);
    let computedComposer = composerNames.join(', ');
    if (!computedComposer && song.composerId && typeof song.composerId === 'object' && song.composerId.name) {
      computedComposer = song.composerId.name.trim();
    }
    if (!computedComposer) {
      computedComposer = cleanFallbackString(song.composer);
    }

    return {
      ...song,
      artistName: computedArtistName || 'Nghệ sĩ',
      composer: computedComposer
    };
  }

  return { formatSongArtistFields };
})();

module.exports = { formatSongArtistFields };
