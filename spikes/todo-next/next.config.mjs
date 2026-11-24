export default {
    output: "standalone",
    poweredByHeader: false,
    async rewrites() {
        return [{ source: "/woge-actions/add-todo", destination: "/api/add" }];
    },
};
