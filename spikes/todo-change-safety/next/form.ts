import type { NextApiRequest, NextApiResponse } from 'next';

type TodoInput = { title: string };

type Reply = { saved: TodoInput } | { error: string };

function readTodo(body: unknown): TodoInput | undefined {
    if (typeof body !== 'object' || body === null || !('title' in body)) return;
    if (typeof body.title !== 'string' || body.title.trim() === '') return;
    return { title: body.title };
}

// A bounded Pages Router handler. The observation ends before persistence.
export function addTodo(request: NextApiRequest, response: NextApiResponse<Reply>) {
    const todo = readTodo(request.body);
    if (!todo) return response.status(400).json({ error: 'Enter a title.' });
    return response.status(200).json({ saved: todo });
}
