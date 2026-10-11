# Malformed-input regression fixtures

Each `.hex` file contains one wire input as hexadecimal bytes. Keep fixtures small, deterministic,
and safe to replay in both the JVM protocol tests and the fallback-client Node tests. Add a matching
expected error assertion in both test suites when a fuzz run discovers a crash, hang, or parser
regression; never store user data or executable payloads here.

Run the normal bounded fuzz pass with `./gradlew :woge-protocol:test` and
`npm run test:unit --prefix client/woge-fallback-client`. For a longer local/nightly pass, set
`WOGE_FUZZ_ITERATIONS=50000` for both commands, or use
`./gradlew -Pwoge.fuzz.iterations=50000 :woge-protocol:test` for the JVM suite. Seeds are fixed so
failures are reproducible. Every fuzz test has a timeout.
