import { filters } from "../../lib/shared.js";
import { snapshot } from "../../lib/store.js";

export default function board(req, res) {
    const filter = req.query.filter ?? "all";
    res.setHeader("Cache-Control", "no-store");
    if (req.method !== "GET") return res.setHeader("Allow", "GET").status(405).end();
    if (!filters.includes(filter)) return res.status(400).end();
    return res.status(200).json(snapshot(filter));
}
