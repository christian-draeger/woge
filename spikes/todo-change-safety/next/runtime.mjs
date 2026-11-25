import assert from 'node:assert/strict';
import { addTodo } from './form.ts';

function submit(body) {
    const reply = {};
    const response = {
        status(code) { reply.status = code; return this; },
        json(value) { reply.body = value; },
    };
    addTodo({ body }, response);
    return reply;
}

assert.equal(submit({ title: '   ' }).status, 400);
const title = process.argv[2] === 'number' ? 3 : 'Sort warehouse boxes';
assert.deepEqual(submit({ title }), { status: 200, body: { saved: { title } } });
console.log('Valid input accepted and blank input rejected by the boundary.');
