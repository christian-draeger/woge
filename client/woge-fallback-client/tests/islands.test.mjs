import assert from "node:assert/strict";
import test from "node:test";
import { parseIslandManifest, parseIslandProps } from "../src/islands.js";

const entry = { url: "/assets/note.js?v=build1", mount: "mount", propsVersion: 1 };
test("typed manifest accepts only local versioned ES modules and constrained exports", () => {
  const result = parseIslandManifest(JSON.stringify({ note: entry }));
  assert.deepEqual(result.get("note"), entry);
  assert.ok(Object.isFrozen(result.get("note")));
  for (const url of ["https://evil.example/note.js", "//evil.example/note.js", "javascript:evil", "note.js",
    "/note.js?v=x\n", "/note.js", "/note.js?v=x#evil", "/../note.js?v=x", "/%2f/note.js?v=x", "/\\evil.js?v=x"]) {
    assert.throws(() => parseIslandManifest(JSON.stringify({ note: { ...entry, url } })));
  }
  for (const value of [null, [], { "bad name": entry }, { note: { ...entry, mount: "evil()" } },
    { note: { ...entry, propsVersion: 0 } }, { note: { ...entry, mount: null } }]) {
    assert.throws(() => parseIslandManifest(JSON.stringify(value)));
  }
});
test("props are inert versioned string fields, never evaluated", () => {
  const props = parseIslandProps('{"version":1,"value":{"text":"</script><img onerror=evil>","maxLength":"120"}}', 1);
  assert.equal(props.text, "</script><img onerror=evil>");
  assert.ok(Object.isFrozen(props));
  for (const text of ['{"version":2,"value":{}}', '{"version":1,"value":[]}',
    '{"version":1,"value":{"value":1}}', '{"version":1,"value":null}', 'alert(1)']) {
    assert.throws(() => parseIslandProps(text, 1));
  }
});
