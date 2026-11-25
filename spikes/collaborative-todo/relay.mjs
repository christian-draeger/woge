import { createHash } from "node:crypto";
import { once } from "node:events";
import http from "node:http";
import net from "node:net";
import { Transform, pipeline } from "node:stream";
import { setTimeout as delay } from "node:timers/promises";

function browserActor(req) {
    return (req.headers.cookie || "").match(/(?:^|;\s*)study_actor=(alice|bob)(?:;|$)/)?.[1];
}

export async function openRelay(port, halfRtt) {
    const readers = new Map();
    const sockets = new Set();
    const blocked = new Set();
    const revoked = new Set();
    const requests = [];
    let uncertain;
    let downstreamBytes = 0;
    let peakReaders = 0;

    function track(actor, close) {
        if (!readers.has(actor)) readers.set(actor, new Set());
        const current = readers.get(actor);
        current.add(close);
        peakReaders = Math.max(peakReaders, liveCount());
        return () => current.delete(close);
    }

    function liveCount() {
        return [...readers.values()].reduce((total, active) => total + active.size, 0);
    }

    const countBytes = () => new Transform({ transform(chunk, _encoding, callback) {
        downstreamBytes += chunk.length;
        setTimeout(callback, halfRtt, null, chunk);
    } });

    function denied(actor) {
        return !actor || revoked.has(actor);
    }

    const proxy = http.createServer(async (req, res) => {
        const actor = browserActor(req);
        const url = new URL(req.url, "http://127.0.0.1");
        const live = url.searchParams.has("_woge_live");
        const record = { actor: actor || "anonymous", method: req.method, path: url.pathname, live };
        if (req.method === "POST") {
            record.fetchMode = req.headers["sec-fetch-mode"] || null;
            const turboId = req.headers["x-turbo-request-id"];
            record.turboRequest = typeof turboId === "string"
                ? createHash("sha256").update(turboId).digest("hex").slice(0, 12)
                : null;
        }
        requests.push(record);
        await delay(halfRtt);
        if (denied(actor) || (live && blocked.has(actor))) {
            record.status = denied(actor) ? 403 : 503;
            res.writeHead(record.status).end();
            return;
        }
        if (res.destroyed) return;
        const upstream = http.request({ hostname: "127.0.0.1", port, path: req.url, method: req.method,
            headers: { ...req.headers, "accept-encoding": "identity" } });
        res.once("close", () => upstream.destroy());
        upstream.once("error", () => { if (!res.headersSent && !res.destroyed) res.writeHead(502).end(); });
        const shouldDrop = uncertain === actor && req.method === "POST";
        if (shouldDrop) uncertain = undefined;
        upstream.once("response", async source => {
            record.status = source.statusCode;
            if (shouldDrop) {
                source.resume();
                await once(source, "end");
                record.responseDropped = true;
                res.destroy();
                return;
            }
            await delay(halfRtt);
            if (res.destroyed) return;
            if (live && source.statusCode === 200) {
                const release = track(actor, () => res.destroy());
                res.once("close", release);
            }
            res.writeHead(source.statusCode, source.headers);
            res.flushHeaders();
            pipeline(source, countBytes(), res, () => {});
        });
        req.pipe(upstream);
    });

    proxy.on("upgrade", async (req, socket, head) => {
        const actor = browserActor(req);
        const record = { actor: actor || "anonymous", method: "UPGRADE", path: req.url, live: true };
        requests.push(record);
        await delay(halfRtt);
        if (denied(actor) || blocked.has(actor)) {
            record.status = denied(actor) ? 403 : 503;
            socket.end(`HTTP/1.1 ${record.status} Denied\r\nConnection: close\r\n\r\n`);
            return;
        }
        if (socket.destroyed) return;
        const peer = net.connect(port, "127.0.0.1");
        socket.once("close", () => peer.destroy());
        socket.on("error", () => peer.destroy());
        peer.on("error", () => socket.destroy());
        peer.once("close", () => socket.destroy());
        await once(peer, "connect").catch(() => null);
        if (peer.destroyed || socket.destroyed) return;
        const headers = [];
        for (let index = 0; index < req.rawHeaders.length; index += 2) {
            headers.push(`${req.rawHeaders[index]}: ${req.rawHeaders[index + 1]}`);
        }
        peer.write(`${req.method} ${req.url} HTTP/${req.httpVersion}\r\n${headers.join("\r\n")}\r\n\r\n`);
        if (head.length) peer.write(head);
        peer.once("data", chunk => {
            record.status = Number(chunk.toString("latin1", 0, 15).split(" ")[1]);
            if (record.status === 101) socket.once("close", track(actor, () => socket.destroy()));
        });
        socket.pipe(new Transform({ transform(chunk, _encoding, callback) {
            setTimeout(callback, halfRtt, null, chunk);
        } })).pipe(peer);
        peer.pipe(countBytes()).pipe(socket);
    });

    proxy.on("connection", socket => {
        sockets.add(socket);
        socket.once("close", () => sockets.delete(socket));
    });
    proxy.listen(0, "127.0.0.1");
    await once(proxy, "listening");
    return {
        origin: `http://127.0.0.1:${proxy.address().port}`,
        requests,
        stats: () => ({ downstreamBytes, peakReaders, readers: liveCount() }),
        active: actor => readers.get(actor)?.size || 0,
        disconnect: actor => {
            blocked.add(actor);
            readers.get(actor)?.forEach(close => close());
        },
        reconnect: actor => blocked.delete(actor),
        revoke: actor => revoked.add(actor),
        dropNextPost: actor => { uncertain = actor; },
        close: () => {
            proxy.close();
            sockets.forEach(socket => socket.destroy());
        },
    };
}
