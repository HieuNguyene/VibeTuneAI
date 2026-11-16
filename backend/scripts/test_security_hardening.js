const crypto = require('crypto');
const fs = require('fs');
const path = require('path');
const assert = require('assert');
const mongoose = require('mongoose');
const jwt = require('jsonwebtoken');
const WebSocket = require('ws');
const config = require('../src/config/environment');
const { User, Song, PlaySession, ListeningHistory, Playlist } = require('../src/models');
const AuthService = require('../src/services/authService');
const { verifyGoogleIdToken } = require('../src/services/googleAuthVerifier');

const BASE = `http://127.0.0.1:${config.port}`;
const API = `${BASE}/api/v1`;

let passed = 0;
let failed = 0;

async function test(name, fn) {
  try {
    await fn();
    passed++;
    console.log(`PASS: ${name}`);
  } catch (err) {
    failed++;
    console.error(`FAIL: ${name} -> ${err.stack || err.message}`);
  }
}

async function httpJson(method, urlOrPath, { token = null, body = undefined, headers = {} } = {}) {
  const url = urlOrPath.startsWith('http') ? urlOrPath : `${API}${urlOrPath}`;
  const hdrs = { 'X-Test-Suite': 'true', ...headers };
  if (token) hdrs.Authorization = `Bearer ${token}`;
  if (body !== undefined) hdrs['Content-Type'] = 'application/json';
  const res = await fetch(url, {
    method,
    headers: hdrs,
    body: body !== undefined ? JSON.stringify(body) : undefined
  });
  let data = null;
  try { data = await res.json(); } catch (_) {}
  return { status: res.status, data };
}

function signRs256Token(privateKey, headerExtra, payload) {
  const headerB64 = Buffer.from(JSON.stringify({ alg: 'RS256', typ: 'JWT', ...headerExtra })).toString('base64url');
  const payloadB64 = Buffer.from(JSON.stringify(payload)).toString('base64url');
  const sig = crypto.sign('RSA-SHA256', Buffer.from(`${headerB64}.${payloadB64}`), privateKey).toString('base64url');
  return `${headerB64}.${payloadB64}.${sig}`;
}

function openWsExpectingClose(wsUrl, timeoutMs = 2500) {
  return new Promise((resolve) => {
    const ws = new WebSocket(wsUrl);
    let receivedConnected = false;
    const timer = setTimeout(() => {
      try { ws.terminate(); } catch (_) {}
      resolve({ closed: false, receivedConnected, code: null });
    }, timeoutMs);

    ws.on('message', (raw) => {
      try {
        const msg = JSON.parse(raw.toString());
        if (msg.event === 'CONNECTED') receivedConnected = true;
      } catch (_) {}
    });

    ws.on('close', (code) => {
      clearTimeout(timer);
      resolve({ closed: true, receivedConnected, code });
    });

    ws.on('error', () => {
      clearTimeout(timer);
      resolve({ closed: true, receivedConnected, code: 1006 });
    });
  });
}

(async () => {
  await mongoose.connect(config.mongodbUri);
  const ts = Date.now();
  const createdUserIds = [];
  const createdSessionIds = [];
  const createdHistoryIds = [];
  const createdPlaylistIds = [];
  const uploadedFiles = [];

  try {
    // Prepare isolated test users (A, B, Inactive) + obtain real Admin token
    const regA = await httpJson('POST', '/auth/register', {
      body: { username: `sec_a_${ts}`, email: `sec_a_${ts}@test.com`, password: 'Password123!', displayName: 'Sec User A' }
    });
    const regB = await httpJson('POST', '/auth/register', {
      body: { username: `sec_b_${ts}`, email: `sec_b_${ts}@test.com`, password: 'Password123!', displayName: 'Sec User B' }
    });
    const regInactive = await httpJson('POST', '/auth/register', {
      body: { username: `sec_in_${ts}`, email: `sec_in_${ts}@test.com`, password: 'Password123!', displayName: 'Sec Inactive' }
    });

    const userAId = regA.data?.data?.user?._id;
    const userBId = regB.data?.data?.user?._id;
    const inactiveId = regInactive.data?.data?.user?._id;
    const tokenA = regA.data?.data?.token;
    const tokenB = regB.data?.data?.token;
    const tokenInactive = regInactive.data?.data?.token;
    [userAId, userBId, inactiveId].filter(Boolean).forEach((id) => createdUserIds.push(id));

    await User.findByIdAndUpdate(inactiveId, { $set: { isActive: false } });

    const adminLogin = await httpJson('POST', '/auth/login', {
      body: { identifier: config.adminEmail, password: config.adminPassword }
    });
    const adminToken = adminLogin.data?.data?.token;
    const adminUser = adminLogin.data?.data?.user;
    assert(adminLogin.status === 200 && adminToken && adminUser?.role === 'admin', 'Admin login must succeed');

    const sampleSong = await Song.findOne({ isPublic: { $ne: false } }).lean();
    assert(sampleSong, 'Need sample song in DB');

    // ================= 1. ADMIN RBAC TESTS =================
    await test('RBAC: Unauthenticated GET /admin/summary -> 401', async () => {
      const r = await httpJson('GET', '/admin/summary');
      assert.strictEqual(r.status, 401);
    });

    await test('RBAC: Unauthenticated POST /admin/upload-file -> 401', async () => {
      const r = await httpJson('POST', '/admin/upload-file', { body: { fileName: 'x.png', base64Data: 'AAAA' } });
      assert.strictEqual(r.status, 401);
    });

    await test('RBAC: Normal user GET /admin/summary & /admin/data/users & POST /admin/notifications -> 403', async () => {
      const [r1, r2, r3] = await Promise.all([
        httpJson('GET', '/admin/summary', { token: tokenA }),
        httpJson('GET', '/admin/data/users', { token: tokenA }),
        httpJson('POST', '/admin/notifications', { token: tokenA, body: { title: 'Hack', content: 'Hack' } })
      ]);
      assert.strictEqual(r1.status, 403);
      assert.strictEqual(r2.status, 403);
      assert.strictEqual(r3.status, 403);
    });

    await test('RBAC: Admin JWT GET /admin/summary -> 200 & rejects invalid user role + self-delete', async () => {
      const rSum = await httpJson('GET', '/admin/summary', { token: adminToken });
      assert.strictEqual(rSum.status, 200);
      const badRole = await httpJson('POST', '/admin/users', {
        token: adminToken,
        body: { username: `badrole_${ts}`, password: 'Password123!', displayName: 'BadRole', role: 'superadmin' }
      });
      assert.strictEqual(badRole.status, 400);
      const selfDel = await httpJson('DELETE', `/admin/users/${adminUser._id}`, { token: adminToken });
      assert.strictEqual(selfDel.status, 400);
    });

    // ================= 2. GOOGLE ID TOKEN VERIFICATION TESTS =================
    await test('GOOGLE: Rejects legacy spoof { googleId, email } without idToken -> 400', async () => {
      const r = await httpJson('POST', '/auth/google', {
        body: { googleId: 'fake_gid', email: 'victim@gmail.com', displayName: 'Fake' }
      });
      assert.strictEqual(r.status, 400);
    });

    await test('GOOGLE: Rejects unsigned/forged JWT on POST /auth/google -> 401', async () => {
      const fakeJwt = [
        Buffer.from(JSON.stringify({ alg: 'none', kid: '1' })).toString('base64url'),
        Buffer.from(JSON.stringify({ iss: 'https://accounts.google.com', sub: '123', email: 'v@gmail.com', email_verified: true, exp: Math.floor(Date.now() / 1000) + 3600 })).toString('base64url'),
        'forgedsig'
      ].join('.');
      const r = await httpJson('POST', '/auth/google', { body: { idToken: fakeJwt } });
      assert.strictEqual(r.status, 401);
    });

    await test('GOOGLE: Cryptographic RS256 verifier accepts valid signature & rejects tampered/expired/unverified/aud-mismatch', async () => {
      const { publicKey, privateKey } = crypto.generateKeyPairSync('rsa', { modulusLength: 2048 });
      const jwk = { ...publicKey.export({ format: 'jwk' }), kid: 'test-kid-1', alg: 'RS256', use: 'sig' };
      const resolver = async (kid) => (kid === 'test-kid-1' ? jwk : null);

      const now = Math.floor(Date.now() / 1000);
      const gEmail = `sec_g_${ts}@gmail.com`;
      const validJwt = signRs256Token(privateKey, { kid: 'test-kid-1' }, {
        iss: 'https://accounts.google.com',
        aud: 'test-client-id.apps.googleusercontent.com',
        sub: `sub_${ts}`,
        email: gEmail,
        email_verified: true,
        name: 'Verified Google User',
        picture: 'https://lh3.googleusercontent.com/a/test',
        iat: now,
        exp: now + 3600
      });

      const prevClients = [...config.googleClientIds];
      try {
        config.googleClientIds = ['test-client-id.apps.googleusercontent.com'];
        const result = await AuthService.loginWithGoogle({ idToken: validJwt }, { keyResolver: resolver });
        if (result?.user?._id) createdUserIds.push(result.user._id);
        assert.strictEqual(result.user.email, gEmail);
        assert(result.token);

        // Tampered payload
        const [h, , s] = validJwt.split('.');
        const tamperedPayload = Buffer.from(JSON.stringify({
          iss: 'https://accounts.google.com',
          aud: 'test-client-id.apps.googleusercontent.com',
          sub: 'victim_sub',
          email: 'victim@gmail.com',
          email_verified: true,
          iat: now,
          exp: now + 3600
        })).toString('base64url');
        await assert.rejects(() => verifyGoogleIdToken(`${h}.${tamperedPayload}.${s}`, { keyResolver: resolver }), /Chữ ký/);

        // Expired token
        const expiredJwt = signRs256Token(privateKey, { kid: 'test-kid-1' }, {
          iss: 'https://accounts.google.com',
          aud: 'test-client-id.apps.googleusercontent.com',
          sub: 'sub_exp',
          email: 'exp@gmail.com',
          email_verified: true,
          iat: now - 7200,
          exp: now - 600
        });
        await assert.rejects(() => verifyGoogleIdToken(expiredJwt, { keyResolver: resolver }), /hết hạn/);

        // Unverified email
        const unverifiedJwt = signRs256Token(privateKey, { kid: 'test-kid-1' }, {
          iss: 'https://accounts.google.com',
          aud: 'test-client-id.apps.googleusercontent.com',
          sub: 'sub_unver',
          email: 'unver@gmail.com',
          email_verified: false,
          iat: now,
          exp: now + 3600
        });
        await assert.rejects(() => verifyGoogleIdToken(unverifiedJwt, { keyResolver: resolver }), /xác minh/);

        // Wrong audience
        const wrongAudJwt = signRs256Token(privateKey, { kid: 'test-kid-1' }, {
          iss: 'https://accounts.google.com',
          aud: 'attacker-client-id',
          sub: 'sub_aud',
          email: 'aud@gmail.com',
          email_verified: true,
          iat: now,
          exp: now + 3600
        });
        await assert.rejects(() => verifyGoogleIdToken(wrongAudJwt, { keyResolver: resolver }), /Audience/);
      } finally {
        config.googleClientIds = prevClients;
      }
    });

    // ================= 3. IDOR PROTECTION TESTS =================
    await test('IDOR: /history* blocks unauthenticated even with ?userId=victim -> 401 & cross-user isolation', async () => {
      const hDoc = await ListeningHistory.create({
        userId: userAId,
        songId: sampleSong._id,
        listenDuration: 60,
        completed: true,
        playbackSource: 'home_feed',
        timestamp: new Date()
      });
      createdHistoryIds.push(hDoc._id);

      const unauthGet = await httpJson('GET', `/history?userId=${userAId}`);
      assert.strictEqual(unauthGet.status, 401);
      const unauthDel = await httpJson('DELETE', `/history/${hDoc._id}?userId=${userAId}`);
      assert.strictEqual(unauthDel.status, 401);

      const bDelA = await httpJson('DELETE', `/history/${hDoc._id}`, { token: tokenB });
      assert.strictEqual(bDelA.status, 200);
      assert.strictEqual(bDelA.data?.data?.deletedCount, 0);
      const stillExists = await ListeningHistory.findById(hDoc._id).lean();
      assert(stillExists, 'User B must NOT be able to delete User A history');
    });

    await test('IDOR: PlaySession owned by A rejects unauth (401) and user B (403) on /next, /arc, /events', async () => {
      const sessRes = await httpJson('POST', `${BASE}/player/session`, {
        token: tokenA,
        body: { firstTrackId: sampleSong._id, arc: 'focus', userId: userBId } // userId spoof must be ignored!
      });
      assert.strictEqual(sessRes.status, 201);
      const sessionId = sessRes.data?.sessionId;
      createdSessionIds.push(sessionId);

      const sessDoc = await PlaySession.findById(sessionId).lean();
      assert.strictEqual(String(sessDoc.userId), String(userAId), 'PlaySession owner must come from JWT, not body.userId');

      const [unauthNext, bNext, unauthArc, bArc, unauthEv, bEv] = await Promise.all([
        httpJson('GET', `${BASE}/player/next?sessionId=${sessionId}`),
        httpJson('GET', `${BASE}/player/next?sessionId=${sessionId}`, { token: tokenB }),
        httpJson('POST', `${BASE}/player/session/${sessionId}/arc`, { body: { arc: 'workout' } }),
        httpJson('POST', `${BASE}/player/session/${sessionId}/arc`, { token: tokenB, body: { arc: 'workout' } }),
        httpJson('POST', `${BASE}/events`, { body: { sessionId, trackId: sampleSong._id, event: 'play' } }),
        httpJson('POST', `${BASE}/events`, { token: tokenB, body: { sessionId, trackId: sampleSong._id, event: 'play' } })
      ]);

      assert.strictEqual(unauthNext.status, 401);
      assert.strictEqual(bNext.status, 403);
      assert.strictEqual(unauthArc.status, 401);
      assert.strictEqual(bArc.status, 403);
      assert.strictEqual(unauthEv.status, 401);
      assert.strictEqual(bEv.status, 403);
    });

    await test('IDOR: POST /interactions ignores spoofed body.userId for anonymous callers', async () => {
      const anonInter = await httpJson('POST', `${BASE}/interactions`, {
        body: { songId: sampleSong._id, interactionType: 'LIKE', userId: userAId }
      });
      assert.strictEqual(anonInter.status, 200);
      assert.strictEqual(anonInter.data?.message, 'Ignored anonymous interaction');
    });

    // ================= 4. WEBSOCKET AUTH & ADMIN BROADCAST ISOLATION =================
    await test('WS: Unauthenticated, ?userId=, invalid JWT, and inactive user connections closed with 1008', async () => {
      const [noAuth, spoofUid, badJwt, inactiveWs] = await Promise.all([
        openWsExpectingClose(`ws://127.0.0.1:${config.port}/ws`),
        openWsExpectingClose(`ws://127.0.0.1:${config.port}/ws?userId=${userAId}`),
        openWsExpectingClose(`ws://127.0.0.1:${config.port}/ws?token=invalid.jwt.token`),
        openWsExpectingClose(`ws://127.0.0.1:${config.port}/ws?token=${tokenInactive}`)
      ]);
      assert(noAuth.closed && !noAuth.receivedConnected, 'Unauth WS must close without CONNECTED');
      assert(spoofUid.closed && !spoofUid.receivedConnected, '?userId= WS must close without CONNECTED');
      assert(badJwt.closed && !badJwt.receivedConnected, 'Invalid JWT WS must close without CONNECTED');
      assert(inactiveWs.closed && !inactiveWs.receivedConnected, 'Inactive user WS must close without CONNECTED');
    });

    await test('WS: Normal user does NOT receive ADMIN_SUMMARY_UPDATE; Admin DOES receive it', async () => {
      const userEvents = [];
      const adminEvents = [];

      const wsUser = new WebSocket(`ws://127.0.0.1:${config.port}/ws?token=${tokenA}`);
      const wsAdmin = new WebSocket(`ws://127.0.0.1:${config.port}/ws?token=${adminToken}`);

      await Promise.all([
        new Promise((res, rej) => { wsUser.on('open', res); wsUser.on('error', rej); }),
        new Promise((res, rej) => { wsAdmin.on('open', res); wsAdmin.on('error', rej); })
      ]);

      wsUser.on('message', (m) => { try { userEvents.push(JSON.parse(m.toString())); } catch (_) {} });
      wsAdmin.on('message', (m) => { try { adminEvents.push(JSON.parse(m.toString())); } catch (_) {} });

      // Attempt SUBSCRIBE hijack on user WS (should be ignored)
      wsUser.send(JSON.stringify({ type: 'SUBSCRIBE', userId: userBId }));

      await new Promise((r) => setTimeout(r, 150));

      // Trigger ADMIN_SUMMARY_UPDATE via single-user notification to userB (NOT userA!)
      await httpJson('POST', '/admin/notifications', {
        token: adminToken,
        body: { targetType: 'single', targetUserIds: [userBId], title: `Sec Test ${ts}`, content: 'Only User B' }
      });

      await new Promise((r) => setTimeout(r, 350));
      wsUser.close();
      wsAdmin.close();

      assert(!userEvents.some((e) => e.event === 'SUBSCRIBE_SUCCESS'), 'SUBSCRIBE hijack must not work');
      assert(!userEvents.some((e) => e.event === 'NEW_NOTIFICATION'), 'User A must not hijack User B notification');
      assert(!userEvents.some((e) => e.event === 'ADMIN_SUMMARY_UPDATE'), 'Normal user must NOT receive ADMIN_SUMMARY_UPDATE');
      assert(adminEvents.some((e) => e.event === 'ADMIN_SUMMARY_UPDATE'), 'Admin WS MUST receive ADMIN_SUMMARY_UPDATE');
    });

    // ================= 5. UPLOAD FILTERING & URL SANITIZATION =================
    await test('UPLOAD: Rejects .html/.svg/.js, path traversal, target mismatch, spoofed magic bytes, unsafe URLs', async () => {
      const validPng = 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAgAAAAICAIAAABLbSncAAAADklEQVQI12P4z8BQDwAEgAF/QualzQAAAABJRU5ErkJggg==';
      const fakePngHtml = 'data:image/png;base64,' + Buffer.from('<html><script>alert(1)</script></html>').toString('base64');
      const svgPayload = 'data:image/svg+xml;base64,' + Buffer.from('<svg onload="alert(1)"/>').toString('base64');

      const badUploads = [
        { fileName: 'xss.html', base64Data: validPng, target: 'cover' },
        { fileName: 'xss.svg', base64Data: svgPayload, target: 'cover' },
        { fileName: '../escape.png', base64Data: validPng, target: 'cover' },
        { fileName: 'fake.png', base64Data: fakePngHtml, target: 'cover' },
        { fileName: 'wrong_target.mp3', base64Data: validPng, target: 'cover' },
        { fileName: 'wrong_target2.png', base64Data: validPng, target: 'audio' },
        { fileName: 'conflict.png', base64Data: validPng, target: 'audio', type: 'cover' }
      ];

      for (const payload of badUploads) {
        const res = await httpJson('POST', '/admin/upload-file', { token: adminToken, body: payload });
        assert.strictEqual(res.status, 400, `Expected 400 for ${payload.fileName}, got ${res.status}`);
      }

      // Bad playlist base64 & unsafe javascript: URL
      const badPlB64 = await httpJson('POST', '/playlists', {
        token: tokenA,
        body: { title: `Bad Cover ${ts}`, coverImageBase64: fakePngHtml }
      });
      assert.strictEqual(badPlB64.status, 400);

      const badPlUrl = await httpJson('POST', '/playlists', {
        token: tokenA,
        body: { title: `XSS Url ${ts}`, coverImageUrl: 'javascript:alert(1)' }
      });
      assert.strictEqual(badPlUrl.status, 400);

      // Valid PNG upload + valid playlist PNG cover still work
      const okUp = await httpJson('POST', '/admin/upload-file', {
        token: adminToken,
        body: { fileName: `sec_ok_${ts}.png`, base64Data: validPng, target: 'cover' }
      });
      assert.strictEqual(okUp.status, 200);
      if (okUp.data?.data?.url) uploadedFiles.push(okUp.data.data.url);

      const okPl = await httpJson('POST', '/playlists', {
        token: tokenA,
        body: { title: `Good Cover ${ts}`, coverImageBase64: validPng }
      });
      assert.strictEqual(okPl.status, 201);
      if (okPl.data?.data?._id) createdPlaylistIds.push(okPl.data.data._id);
      if (okPl.data?.data?.coverImageUrl?.startsWith('/uploads/')) uploadedFiles.push(okPl.data.data.coverImageUrl);
    });

    console.log(`\n=== SECURITY HARDENING SUMMARY: ${passed} PASSED, ${failed} FAILED ===`);
  } finally {
    const models = require('../src/models');
    if (createdPlaylistIds.length > 0) {
      await models.PlaylistTrack.deleteMany({ playlistId: { $in: createdPlaylistIds } });
      await Playlist.deleteMany({ _id: { $in: createdPlaylistIds } });
    }
    if (createdHistoryIds.length > 0) {
      await ListeningHistory.deleteMany({ _id: { $in: createdHistoryIds } });
    }
    if (createdSessionIds.length > 0) {
      await PlaySession.deleteMany({ _id: { $in: createdSessionIds } });
    }
    if (createdUserIds.length > 0) {
      await Promise.all([
        models.UserPreference.deleteMany({ userId: { $in: createdUserIds } }),
        models.UserFavorite.deleteMany({ userId: { $in: createdUserIds } }),
        ListeningHistory.deleteMany({ userId: { $in: createdUserIds } }),
        models.ListeningEvent.deleteMany({ userId: { $in: createdUserIds } }),
        models.UserInteraction.deleteMany({ userId: { $in: createdUserIds } }),
        PlaySession.deleteMany({ userId: { $in: createdUserIds } }),
        models.Notification.deleteMany({ $or: [{ recipientId: { $in: createdUserIds } }, { senderId: { $in: createdUserIds } }] }),
        User.deleteMany({ _id: { $in: createdUserIds } })
      ]);
    }
    for (const rel of uploadedFiles) {
      const fp = path.join(__dirname, '../public', rel.replace(/^\/+/, ''));
      if (fs.existsSync(fp)) fs.unlinkSync(fp);
    }
    await mongoose.disconnect();
    if (failed > 0) process.exit(1);
  }
})();
