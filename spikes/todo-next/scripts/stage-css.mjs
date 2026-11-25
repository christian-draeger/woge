import { copyFile, mkdir } from "node:fs/promises";

const publicDirectory = new URL("../public/", import.meta.url);
await mkdir(publicDirectory, { recursive: true });
await copyFile(
    new URL("../../../examples/todo-spring-boot/src/main/resources/static/todo.css", import.meta.url),
    new URL("todo.css", publicDirectory),
);
