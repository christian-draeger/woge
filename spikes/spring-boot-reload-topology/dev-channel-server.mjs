import http from 'node:http';

const devClient = String.raw`
(() => {
  const root = document.documentElement;
  let newestBuild = Number(root.dataset.buildId || '0');
  let newestGeneration = Number(root.dataset.serverGeneration || '0');
  const source = new EventSource('/__woge/events');

  source.addEventListener('server-ready', (event) => {
    const update = JSON.parse(event.data);
    if (update.buildId <= newestBuild || update.generation <= newestGeneration) return;
    newestBuild = update.buildId;
    newestGeneration = update.generation;
    sessionStorage.setItem('woge-browser-event-at', String(performance.timeOrigin + performance.now()));
    location.reload();
  });

  source.addEventListener('css-ready', (event) => {
    const update = JSON.parse(event.data);
    if (update.buildId <= newestBuild) return;
    newestBuild = update.buildId;
    const stylesheet = document.querySelector('link[data-woge-stylesheet]');
    stylesheet.href = '/styles.css?revision=' + encodeURIComponent(update.revision);
    root.dataset.buildId = String(update.buildId);
    root.dataset.cssRevision = update.revision;
    root.dispatchEvent(new CustomEvent('woge:css-applied'));
  });

  addEventListener('beforeunload', () => source.close(), { once: true });
})();
`;

export async function createDevChannelServer(port = 0) {
  let eventId = 0;
  let documentState = { buildId: 1, generation: 1, marker: 'generation-1' };
  const clients = new Set();
  const reconnectHeaders = [];

  const server = http.createServer(async (request, response) => {
    const url = new URL(request.url, 'http://127.0.0.1');
    if (request.method === 'GET' && url.pathname === '/app') {
      response.writeHead(200, { 'content-type': 'text/html; charset=utf-8', 'cache-control': 'no-store' });
      response.end(`<!doctype html>
<html data-build-id="${documentState.buildId}" data-server-generation="${documentState.generation}" data-css-revision="1">
<head><link rel="stylesheet" data-woge-stylesheet href="/styles.css?revision=1"><script src="/__woge/client.js" defer></script></head>
<body><main><h1>${documentState.marker}</h1></main></body></html>`);
      return;
    }
    if (request.method === 'GET' && url.pathname === '/styles.css') {
      response.writeHead(200, { 'content-type': 'text/css; charset=utf-8', 'cache-control': 'no-store' });
      response.end(`:root { --woge-test-revision: "${url.searchParams.get('revision') || '1'}"; }`);
      return;
    }
    if (request.method === 'GET' && url.pathname === '/__woge/client.js') {
      response.writeHead(200, { 'content-type': 'text/javascript; charset=utf-8', 'cache-control': 'no-store' });
      response.end(devClient);
      return;
    }
    if (request.method === 'GET' && url.pathname === '/__woge/events') {
      const lastEventId = request.headers['last-event-id'] || '';
      reconnectHeaders.push(lastEventId);
      response.writeHead(200, {
        'content-type': 'text/event-stream',
        'cache-control': 'no-cache',
        connection: 'keep-alive',
      });
      response.write(': connected\n\n');
      clients.add(response);
      request.on('close', () => clients.delete(response));
      return;
    }
    if (request.method === 'POST' && url.pathname === '/__woge/publish') {
      const chunks = [];
      for await (const chunk of request) chunks.push(chunk);
      const update = JSON.parse(Buffer.concat(chunks).toString('utf8'));
      eventId += 1;
      if (
        update.type === 'server-ready' &&
        update.buildId > documentState.buildId &&
        update.generation > documentState.generation
      ) {
        documentState = {
          buildId: update.buildId,
          generation: update.generation,
          marker: update.marker,
        };
      }
      const frame = `id: ${eventId}\nevent: ${update.type}\ndata: ${JSON.stringify(update)}\n\n`;
      for (const client of clients) client.write(frame);
      response.writeHead(204).end();
      return;
    }
    if (request.method === 'POST' && url.pathname === '/__woge/disconnect') {
      for (const client of clients) client.end();
      clients.clear();
      response.writeHead(204).end();
      return;
    }
    if (request.method === 'GET' && url.pathname === '/__woge/status') {
      response.writeHead(200, { 'content-type': 'application/json' });
      response.end(JSON.stringify({ connected: clients.size, reconnectHeaders }));
      return;
    }
    response.writeHead(404).end();
  });

  await new Promise((resolve, reject) => {
    server.once('error', reject);
    server.listen(port, '127.0.0.1', resolve);
  });
  const address = server.address();
  const baseUrl = `http://127.0.0.1:${address.port}`;
  return {
    baseUrl,
    async close() {
      for (const client of clients) client.end();
      await new Promise((resolve) => server.close(resolve));
    },
  };
}
