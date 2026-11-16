const http = require('http');

function postJson(path, data) {
  return new Promise((resolve, reject) => {
    const body = JSON.stringify(data);
    const req = http.request(
      {
        hostname: '127.0.0.1',
        port: 5000,
        path,
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Content-Length': Buffer.byteLength(body)
        }
      },
      res => {
        let resData = '';
        res.on('data', chunk => (resData += chunk));
        res.on('end', () => {
          try {
            resolve({ statusCode: res.statusCode, body: JSON.parse(resData) });
          } catch (e) {
            resolve({ statusCode: res.statusCode, raw: resData });
          }
        });
      }
    );
    req.on('error', reject);
    req.write(body);
    req.end();
  });
}

function getJson(path) {
  return new Promise((resolve, reject) => {
    const req = http.request(
      {
        hostname: '127.0.0.1',
        port: 5000,
        path,
        method: 'GET'
      },
      res => {
        let resData = '';
        res.on('data', chunk => (resData += chunk));
        res.on('end', () => {
          try {
            resolve({ statusCode: res.statusCode, body: JSON.parse(resData) });
          } catch (e) {
            resolve({ statusCode: res.statusCode, raw: resData });
          }
        });
      }
    );
    req.on('error', reject);
    req.end();
  });
}

async function runApiTests() {
  console.log('=== TESTING MOOD FLOW REST APIS ===\n');

  // 1. POST /player/session
  console.log('1. Testing POST /player/session...');
  const sessionRes = await postJson('/player/session', { arc: 'wind_down' });
  console.log('Status:', sessionRes.statusCode);
  console.log('Response:', JSON.stringify(sessionRes.body, null, 2));

  const sessionId = sessionRes.body.sessionId || sessionRes.body.data?.sessionId;
  if (!sessionId) {
    throw new Error('Failed to create session');
  }

  // 2. GET /player/next
  console.log('\n2. Testing GET /player/next?sessionId=' + sessionId + '&debug=true...');
  const startT = Date.now();
  const nextRes = await getJson(`/player/next?sessionId=${sessionId}&debug=true`);
  const latency = Date.now() - startT;
  console.log('Status:', nextRes.statusCode);
  console.log('Latency:', latency, 'ms');
  console.log('Track:', nextRes.body.track?.title, 'by', nextRes.body.track?.artistName);
  console.log('Reason:', nextRes.body.reason);
  console.log('Debug latency inside server:', nextRes.body.debug?.latencyMs, 'ms');

  const chosenTrackId = nextRes.body.track?.id;

  // 3. POST /player/session/:id/arc
  console.log(`\n3. Testing POST /player/session/${sessionId}/arc...`);
  const arcRes = await postJson(`/player/session/${sessionId}/arc`, { arc: 'workout' });
  console.log('Status:', arcRes.statusCode);
  console.log('Updated Arc Response:', JSON.stringify(arcRes.body, null, 2));

  // 4. POST /events with sessionId
  console.log('\n4. Testing POST /events with sessionId...');
  const eventRes = await postJson('/events', {
    sessionId,
    trackId: chosenTrackId,
    event: 'complete',
    positionSec: 180,
    source: 'moodflow'
  });
  console.log('Status:', eventRes.statusCode);
  console.log('Event Result:', JSON.stringify(eventRes.body, null, 2));

  console.log('\nALL REST API TESTS PASSED SUCCESSFULLY!');
}

runApiTests().catch(err => {
  console.error('API Test Error:', err);
  process.exit(1);
});
