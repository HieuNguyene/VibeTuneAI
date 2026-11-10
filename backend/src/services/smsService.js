const { exec } = require('child_process');

class SmsService {
  /**
   * Chuyển đổi định dạng số điện thoại Việt Nam sang chuẩn quốc tế E.164 (+84...)
   */
  static formatToE164(phoneNumber) {
    let clean = phoneNumber.trim().replace(/[^0-9+]/g, '');
    if (clean.startsWith('0')) {
      clean = '+84' + clean.slice(1);
    } else if (clean.startsWith('84')) {
      clean = '+' + clean;
    } else if (!clean.startsWith('+')) {
      clean = '+84' + clean;
    }
    return clean;
  }

  /**
   * Gửi tin nhắn SMS OTP đến số điện thoại (Hỗ trợ Twilio, eSMS.vn, và máy ảo ADB)
   */
  static async sendOtp(phoneNumber, otp) {
    const message = `[Vibe] Ma xac thuc OTP cua ban la: ${otp}. Hieu luc trong 5 phut.`;
    const e164Phone = this.formatToE164(phoneNumber);

    const twilioSid = process.env.TWILIO_ACCOUNT_SID;
    const twilioToken = process.env.TWILIO_AUTH_TOKEN;
    const twilioFrom = process.env.TWILIO_PHONE_NUMBER;

    const esmsApiKey = process.env.ESMS_API_KEY;
    const esmsSecretKey = process.env.ESMS_SECRET_KEY;

    let sent = false;

    // 1. Thử gửi qua Twilio (Tổng đài quốc tế SMS)
    if (twilioSid && twilioToken && twilioFrom) {
      try {
        console.log(`[SMS Service] Đang gửi SMS viễn thông qua Twilio đến ${e164Phone}...`);
        const auth = Buffer.from(`${twilioSid}:${twilioToken}`).toString('base64');
        const params = new URLSearchParams();
        params.append('To', e164Phone);
        params.append('From', twilioFrom);
        params.append('Body', message);

        const response = await fetch(`https://api.twilio.com/2010-04-01/Accounts/${twilioSid}/Messages.json`, {
          method: 'POST',
          headers: {
            'Authorization': `Basic ${auth}`,
            'Content-Type': 'application/x-www-form-urlencoded'
          },
          body: params.toString()
        });

        const data = await response.json();
        if (response.ok) {
          console.log(`[SMS Service] ✅ Twilio SMS đã gửi thành công tới điện thoại ${e164Phone}, SID: ${data.sid}`);
          sent = true;
          return { success: true, provider: 'twilio', sid: data.sid };
        } else {
          console.error(`[SMS Service] ❌ Twilio gửi thất bại:`, data.message || data);
        }
      } catch (err) {
        console.error(`[SMS Service] ❌ Lỗi kết nối Twilio:`, err.message);
      }
    }

    // 2. Thử gửi qua eSMS.vn (Tổng đài SMS Việt Nam)
    if (!sent && esmsApiKey && esmsSecretKey) {
      try {
        console.log(`[SMS Service] Đang gửi SMS qua eSMS.vn đến ${phoneNumber}...`);
        const url = `http://rest.esms.vn/MainService.svc/json/SendMultipleMessage_V4_get?Phone=${phoneNumber}&ApiKey=${esmsApiKey}&SecretKey=${esmsSecretKey}&Content=${encodeURIComponent(message)}&SmsType=2`;
        const res = await fetch(url);
        const data = await res.json();
        if (data.CodeResult === '100') {
          console.log(`[SMS Service] ✅ eSMS đã gửi thành công tới điện thoại ${phoneNumber}, SMSID: ${data.SMSID}`);
          sent = true;
          return { success: true, provider: 'esms', smsId: data.SMSID };
        } else {
          console.error(`[SMS Service] ❌ eSMS gửi thất bại (Code: ${data.CodeResult}):`, data.ErrorMessage);
        }
      } catch (err) {
        console.error(`[SMS Service] ❌ Lỗi kết nối eSMS:`, err.message);
      }
    }

    // 3. Fallback cho môi trường giả lập: Gửi tin nhắn qua ADB emulator
    const adbPath = 'C:\\Users\\Tho\\AppData\\Local\\Android\\Sdk\\platform-tools\\adb.exe';
    exec(`"${adbPath}" emu sms send "${phoneNumber}" "${message}"`, (err) => {
      if (!err) {
        console.log(`[SMS Service] SMS giả lập đã gửi tới máy ảo Android cho SĐT ${phoneNumber}: ${otp}`);
      }
    });

    return {
      success: sent,
      provider: sent ? 'remote_carrier' : 'local_dev',
      message: sent ? 'Đã gửi SMS tới điện thoại thật' : 'Chưa cấu hình khóa tổng đài SMS viễn thông'
    };
  }
}

module.exports = SmsService;
