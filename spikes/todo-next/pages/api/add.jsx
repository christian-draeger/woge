import { renderToStaticMarkup } from "react-dom/server";
import { Todo } from "../../lib/todo.jsx";
import { filters, validTitle } from "../../lib/shared.js";
import { add, snapshot } from "../../lib/store.js";

export const config = { api: { bodyParser: { sizeLimit: "16kb" } } };

export default function action(req, res) {
    res.setHeader("Cache-Control", "no-store");
    res.setHeader("Vary", "Accept");
    if (req.method !== "POST") return res.setHeader("Allow", "POST").status(405).end();
    if (req.headers.origin !== `http://${req.headers.host}`) return res.status(403).end();
    if (req.headers["content-type"]?.split(";")[0] !== "application/x-www-form-urlencoded") {
        return res.status(415).end();
    }

    const { title = "", filter = "all" } = req.body ?? {};
    if (typeof title !== "string" || !filters.includes(filter)) return res.status(400).end();
    const enhanced = req.headers.accept === "application/json";
    if (!validTitle(title)) {
        if (enhanced) return res.status(400).json({ validation: true });
        const html = renderToStaticMarkup(
            <html lang="en">
                <head><meta charSet="utf-8" /><title>Todos</title><link rel="stylesheet" href="/todo.css" /></head>
                <body><Todo initial={snapshot(filter)} initialTitle={title} initialError /></body>
            </html>,
        );
        return res.status(400).setHeader("Content-Type", "text/html; charset=utf-8").send("<!doctype html>" + html);
    }

    add(title);
    if (enhanced) return res.status(200).json({ snapshot: snapshot(filter), status: "Todo added." });
    return res.redirect(303, `/?filter=${filter}`);
}
