# Collaborative Todo comparison

This fixture compares the packaged Woge Todo after [#276](https://github.com/christian-draeger/woge/issues/276)
with independently run Rails, Turbo and Action Cable. See the [workload manifest](workload.md) for
runtime versions, observable outcomes and the boundaries of the study. The dated
[comparison results](../../docs/performance/collaborative-todo-2026-11-25.md) include the
raw rounds and findings for both HTTP and the enhanced path.

## Run both packages

At the root with JDK 21, Node 24, Ruby 4.0.6 and Bundler 4.0.16:

```sh
./gradlew publishScaffoldArtifacts -PwogeVersion=0.1.0-collaboration-study.1
./gradlew -p examples/todo-spring-boot bootJar \
  -PwogeVersion=0.1.0-collaboration-study.1 \
  -PwogeRepository="$PWD/build/scaffold-maven-repository"
(cd spikes/collaborative-todo/hotwire && bundle install)
(cd client/woge-fallback-client && npm ci && npx playwright install chromium)
node spikes/collaborative-todo/measure.mjs build/collaborative-todo
```

The browser harness starts each app on loopback and uses a test-only admission relay. It
restarts the application to clear the store between rounds. Run `STUDY_ROUNDS=1` for a pilot
or use `STUDY_HALF_RTT` to set the delay in each direction in milliseconds. `STUDY_CONTENDERS`
accepts a comma-separated selection of `woge`, `hotwire` and `hotwire-stable-form`. It writes all
attempts including negative outcomes to `rounds.json`. Inspect `failure` and `scenarios` before
interpreting other measurements. No database service, external dependency at runtime or
production credentials are needed.

The Rails arm uses standard ERB, form helpers, Turbo page morphing, native 303 redirects and a
signed `turbo_stream_from` Action Cable subscription. The Rails middleware session cookie is
separate from the study relay's synthetic browser identity. Rails `async` and the in-memory
repositories deliberately keep both sides single process. The opt-in Hotwire arm also uses a
permanent Add form and a [small event handler](hotwire/public/stable-form.js) to let its own
submission render the server's reply.
