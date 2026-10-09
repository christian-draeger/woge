import fs from 'node:fs/promises';
import net from 'node:net';
import path from 'node:path';
import { expect, test } from '@playwright/test';
import { createDevChannelServer } from '../dev-channel-server.mjs';

async function publish(request, baseUrl, update) {
  const response = await request.post(`${baseUrl}/__woge/publish`, { data: update });
  expect(response.status()).toBe(204);
}

async function waitForConnections(request, baseUrl, expected) {
  await expect.poll(async () => {
    const response = await request.get(`${baseUrl}/__woge/status`);
    return (await response.json()).connected;
  }).toBe(expected);
}

function percentile(values, quantile) {
  const sorted = [...values].sort((left, right) => left - right);
  return sorted[Math.max(0, Math.ceil(sorted.length * quantile) - 1)];
}

test('SSE keeps multiple tabs ordered, reconnecting and visibly synchronized', async ({ browser, request }) => {
  const server = await createDevChannelServer();
  const context = await browser.newContext();
  const first = await context.newPage();
  const second = await context.newPage();
  const timings = [];

  try {
    await Promise.all([first.goto(`${server.baseUrl}/app`), second.goto(`${server.baseUrl}/app`)]);
    await waitForConnections(request, server.baseUrl, 2);

    for (let generation = 2; generation <= 6; generation += 1) {
      const startedAt = Date.now();
      await publish(request, server.baseUrl, {
        type: 'server-ready',
        buildId: generation,
        generation,
        marker: `generation-${generation}`,
      });
      await Promise.all([
        expect(first.locator('h1')).toHaveText(`generation-${generation}`),
        expect(second.locator('h1')).toHaveText(`generation-${generation}`),
      ]);
      timings.push(Date.now() - startedAt);
      await waitForConnections(request, server.baseUrl, 2);
    }

    await publish(request, server.baseUrl, {
      type: 'server-ready',
      buildId: 5,
      generation: 5,
      marker: 'stale-generation',
    });
    await expect(first.locator('h1')).toHaveText('generation-6');
    await expect(second.locator('h1')).toHaveText('generation-6');

    const firstUrl = first.url();
    await publish(request, server.baseUrl, {
      type: 'css-ready',
      buildId: 7,
      revision: 'css-7',
    });
    await Promise.all([
      expect.poll(() => first.locator('html').getAttribute('data-css-revision')).toBe('css-7'),
      expect.poll(() => second.locator('html').getAttribute('data-css-revision')).toBe('css-7'),
    ]);
    expect(first.url()).toBe(firstUrl);

    await request.post(`${server.baseUrl}/__woge/disconnect`);
    await waitForConnections(request, server.baseUrl, 2);
    const status = await (await request.get(`${server.baseUrl}/__woge/status`)).json();
    expect(status.reconnectHeaders.some((header) => header !== '')).toBe(true);

    await publish(request, server.baseUrl, {
      type: 'server-ready',
      buildId: 8,
      generation: 8,
      marker: 'generation-8',
    });
    await publish(request, server.baseUrl, {
      type: 'server-ready',
      buildId: 9,
      generation: 9,
      marker: 'generation-9',
    });
    await Promise.all([
      expect(first.locator('h1')).toHaveText('generation-9'),
      expect(second.locator('h1')).toHaveText('generation-9'),
    ]);

    const output = {
      samples: timings.length,
      browserEventToVisibleP50Ms: percentile(timings, 0.5),
      browserEventToVisibleP95Ms: percentile(timings, 0.95),
    };
    const outputDirectory = path.resolve('build');
    await fs.mkdir(outputDirectory, { recursive: true });
    await fs.writeFile(
      path.join(outputDirectory, 'browser-measurements.json'),
      `${JSON.stringify(output, null, 2)}\n`,
    );
  } finally {
    await context.close();
    await server.close();
  }
});

test('the lifecycle endpoint fails fast on a port conflict', async () => {
  const blocker = net.createServer();
  await new Promise((resolve) => blocker.listen(0, '127.0.0.1', resolve));
  const address = blocker.address();
  try {
    await expect(createDevChannelServer(address.port)).rejects.toMatchObject({ code: 'EADDRINUSE' });
  } finally {
    await new Promise((resolve) => blocker.close(resolve));
  }
});
