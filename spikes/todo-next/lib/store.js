import { EventEmitter } from "node:events";
import { validTitle } from "./shared.js";

const key = Symbol.for("todo-next-study.store");
const store = globalThis[key] ??= {
    rows: Array.from({ length: 10 }, (_, index) => ({
        id: index + 1, title: `Todo <${index + 1}> & Kotlin`, completed: false,
    })),
    nextId: 11,
    events: new EventEmitter(),
};

export function snapshot(filter) {
    return {
        filter,
        total: store.rows.length,
        remaining: store.rows.filter(todo => !todo.completed).length,
        visible: store.rows.filter(todo => filter === "all" || todo.completed === (filter === "completed")),
    };
}

export function add(title) {
    if (!validTitle(title)) throw new Error("Invalid Todo title");
    store.rows.push({ id: store.nextId++, title: title.trim(), completed: false });
    store.events.emit("changed");
}

export function onChange(listener) {
    store.events.on("changed", listener);
    return () => store.events.off("changed", listener);
}
