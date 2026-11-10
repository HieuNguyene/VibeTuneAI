const mongoose = require('mongoose');

const GenreSchema = new mongoose.Schema(
  {
    name: { type: String, required: true, unique: true, trim: true },
    slug: { type: String, required: true, unique: true, lowercase: true, trim: true },
    colorCode: {
      type: String,
      default: '#1DB954',
      match: [/^#([A-Fa-f0-9]{6})$/, 'Invalid hex color code']
    },
    iconUrl: { type: String, default: '' },
    description: { type: String, default: '', maxlength: 500 }
  },
  { timestamps: true }
);

module.exports = mongoose.model('Genre', GenreSchema);
