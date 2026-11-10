const mongoose = require('mongoose');

const OtpVerificationSchema = new mongoose.Schema(
  {
    phoneNumber: {
      type: String,
      required: true,
      index: true,
      trim: true
    },
    otp: {
      type: String,
      required: true
    },
    expiresAt: {
      type: Date,
      required: true,
      index: { expires: 0 } // Document expires when expiresAt is reached
    }
  },
  { timestamps: true }
);

module.exports = mongoose.model('OtpVerification', OtpVerificationSchema);
