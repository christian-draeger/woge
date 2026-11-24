import Head from "next/head";
import { Todo } from "../lib/todo.jsx";
import { filters } from "../lib/shared.js";
import { snapshot } from "../lib/store.js";

export function getServerSideProps({ query, res }) {
    const filter = query.filter ?? "all";
    res.setHeader("Cache-Control", "no-store");
    if (!filters.includes(filter)) {
        res.statusCode = 400;
        return { props: { initial: null } };
    }
    return { props: { initial: snapshot(filter) } };
}

export default function Home({ initial }) {
    return <>
        <Head>
            <title>Todos</title>
            <link rel="stylesheet" href="/todo.css" />
        </Head>
        {initial ? <Todo initial={initial} /> : <h1>Invalid filter</h1>}
    </>;
}
