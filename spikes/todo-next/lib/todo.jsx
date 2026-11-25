import { useCallback, useEffect, useRef, useState } from "react";
import { addPath, filters, maxTitleLength } from "./shared.js";

export function Todo({ initial, initialTitle = "", initialError = false }) {
    const filter = initial.filter;
    const [board, setBoard] = useState(initial);
    const [title, setTitle] = useState(initialTitle);
    const [invalid, setInvalid] = useState(initialError);
    const [status, setStatus] = useState("");
    const [alert, setAlert] = useState("");
    const [busy, setBusy] = useState(false);
    const activity = useRef({ write: false, dirty: false, read: null, disposed: false });
    const query = `filter=${filter}`;

    const refresh = useCallback(async function refreshBoard() {
        const state = activity.current;
        state.dirty = true;
        if (state.write || state.read || state.disposed) return;
        const controller = new AbortController();
        state.read = controller;
        state.dirty = false;
        try {
            const response = await fetch(`/api/board?${query}`, { signal: controller.signal });
            if (!response.ok) throw new Error("Board request failed");
            const latest = await response.json();
            if (state.write || controller.signal.aborted) state.dirty = true;
            else setBoard(latest);
        } catch (error) {
            if (error.name === "AbortError") state.dirty = true;
            else setAlert("Live updates are unavailable. Reload to refresh.");
        } finally {
            state.read = null;
            if (state.dirty && !state.write && !state.disposed) void refreshBoard();
        }
    }, [query]);

    useEffect(() => {
        const state = activity.current;
        state.disposed = false;
        const stream = new EventSource(`/api/events?${query}`);
        stream.addEventListener("todo-change", refresh);
        return () => {
            state.disposed = true;
            stream.close();
            state.read?.abort();
        };
    }, [query, refresh]);

    async function submit(event) {
        event.preventDefault();
        const state = activity.current;
        if (state.write) return;
        state.write = true;
        setBusy(true);
        state.read?.abort();
        const submitted = title;
        try {
            const response = await fetch(addPath, {
                method: "POST",
                headers: { Accept: "application/json" },
                body: new URLSearchParams({ title: submitted, filter }),
            });
            if (response.status === 400 && (await response.json()).validation) {
                setInvalid(true);
                requestAnimationFrame(() => document.getElementById("todo-errors")?.focus());
            } else if (response.ok) {
                const result = await response.json();
                setBoard(result.snapshot);
                setStatus(result.status);
                setInvalid(false);
                setTitle(current => current === submitted ? "" : current);
            } else {
                throw new Error("Add request failed");
            }
            setAlert("");
        } catch {
            setAlert("Could not confirm the result. Reload before submitting again.");
        } finally {
            state.write = false;
            setBusy(false);
            if (state.dirty) void refresh();
        }
    }

    return <main>
        <h1>Todos</h1>
        <nav aria-label="Filter todos">
            {filters.map(choice => <a key={choice} href={`/?filter=${choice}`}
                aria-current={choice === filter ? "page" : undefined}>
                {choice[0].toUpperCase() + choice.slice(1)}
            </a>)}
        </nav>
        <div id="todo-form">
            {invalid && <div id="todo-errors" tabIndex={-1}>
                <h2>Check your todo</h2><p><a href="#todo-title">Enter a title of 1 to 120 characters.</a></p>
            </div>}
            <form id="add-todo" method="post" action={addPath} onSubmit={submit}>
                <label htmlFor="todo-title">New todo</label>
                <input id="todo-title" name="title" required maxLength={maxTitleLength} value={title}
                    onChange={event => setTitle(event.target.value)} aria-invalid={invalid || undefined}
                    aria-describedby={invalid ? "todo-errors" : undefined} />
                <input type="hidden" name="filter" value={filter} />
                <button disabled={busy}>Add todo</button>
            </form>
        </div>
        <div id="todo-board">
            <p id="todo-count">{board.remaining} remaining · {board.total} total</p>
            <ul>{board.visible.map(todo => <li key={todo.id} data-todo-id={todo.id}>
                <span>{todo.title}</span>{todo.completed ? " (completed)" : ""}
            </li>)}</ul>
        </div>
        <p id="todo-status" role="status">{status}</p>
        <p id="todo-alert" role="alert">{alert}</p>
    </main>;
}
