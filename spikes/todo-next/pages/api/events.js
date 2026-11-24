import { filters } from "../../lib/shared.js";
import { onChange } from "../../lib/store.js";

export default function events(req, res) {
    if (req.method !== "GET") return res.setHeader("Allow", "GET").status(405).end();
    if (!filters.includes(req.query.filter ?? "all")) return res.status(400).end();

    res.writeHead(200, {
        "Content-Type": "text/event-stream",
        "Cache-Control": "no-store, no-transform",
        "X-Accel-Buffering": "no",
    });
    res.flushHeaders();
    const send = () => res.write("event: todo-change\ndata: changed\n\n");
    const stop = onChange(send);
    res.on("close", stop);
    send();
}
