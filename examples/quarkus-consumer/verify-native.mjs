import assert from "node:assert/strict";

const origin = new URL(process.argv[2]);
const timeout = 10_000;

const home = await get("/");
const html = await home.text();
assert.match(html, /<h1>Welcome to the garden club<\/h1>/);
const style = html.match(/href="(\/assets\/static-page\/site\.css)"/)[1];
const css = await get(style);
assert.match(css.headers.get("content-type"), /^text\/css/);
assert.match(await css.text(), /font-family: system-ui, sans-serif/);

const forecast = await get("/forecast");
const shell = await forecast.text();
assert.match(shell, /<h1>Weekend forecast<\/h1>/);
const route = shell.match(/(\/forecast\/woge-patches\/[a-f\d-]+)"/)[1];
const stream = await get(route);
assert.equal(stream.headers.get("content-type"), "application/vnd.woge.patch-stream; version=1");
assert.equal(stream.headers.get("transfer-encoding"), "chunked");
const frames = Buffer.from(await stream.arrayBuffer());
assert.equal(frames.subarray(0, 4).toString(), "WOGE");
assert.match(frames.toString(), /"operation":"replace"/);
assert.match(frames.toString(), /Saturday: sunny[\s\S]*Sunday: light rain/);
assert.ok(frames.toString().endsWith('{"patches":1}'));

const complete = await get("/forecast?complete=true");
assert.match(await complete.text(), /Saturday: sunny[\s\S]*Sunday: light rain/);
console.log("Quarkus Native Image served HTML, classpath CSS, a complete page and framed forecast patches");

async function get(path) {
    const result = await fetch(new URL(path, origin), { signal: AbortSignal.timeout(timeout) });
    assert.equal(result.status, 200, path);
    return result;
}
