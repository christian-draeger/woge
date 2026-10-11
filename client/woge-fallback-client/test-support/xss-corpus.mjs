import { readFile } from "node:fs/promises";

const CORPUS_URL = new URL("../../../testing/xss-corpus/payloads.tsv", import.meta.url);

export async function readXssCorpus() {
  const source = await readFile(CORPUS_URL, "utf8");
  return source.split(/\r?\n/)
    .filter((line) => line.length > 0 && !line.startsWith("#"))
    .map((line) => {
      const separator = line.indexOf("\t");
      if (separator < 1) throw new Error("Invalid shared XSS corpus row");
      return { category: line.slice(0, separator), payload: line.slice(separator + 1) };
    });
}
