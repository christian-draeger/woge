# Quick-add feature recipe

This bounded [#155](https://github.com/christian-draeger/woge/issues/155) experiment adds a small
form to the standalone Spring Boot Todo application. It asks whether creating one complete
feature would benefit from a generator.

Open `/quick-add`, enter a title and submit. The server validates and saves through the existing
Todo repository. A valid submission returns 303 to the existing list. An invalid submission returns
the form with its text and linked field error. New routes live beside the existing application.
The page uses ordinary HTML with JavaScript disabled.

## Replay the experiment

Use the [standalone Todo source](../../examples/todo-spring-boot/README.md) from
`ab4028eaf88df1d48523583abc2aa0e9e91cf3e4`, copied into a separate application directory. Use JDK 21
and the local Maven repository from the [fixed beta pilot](https://github.com/christian-draeger/woge/issues/50).
From the Woge checkout, apply the fixed recipe to that copy.

```sh
recipe="$PWD/spikes/vertical-feature-recipe/quick-add.patch"
git -C /path/to/todo apply --check "$recipe"
git -C /path/to/todo apply "$recipe"
cd /path/to/todo
./gradlew -PwogeVersion=0.1.0-beta.1 -PwogeRepository=/absolute/path/to/pilot/repository check bootJar
./gradlew -PwogeVersion=0.1.0-beta.1 -PwogeRepository=/absolute/path/to/pilot/repository bootRun
```

The new portable page, decoder, action and validation are in `QuickAdd.kt`. The Spring handler
bindings are in `QuickAddRoutes.kt`. The HTTP test is in `QuickAddApplicationTest.kt`. Edit these
application-owned files directly. Generated route and action descriptors keep their existing KSP
ownership under `build/generated/ksp`. The application's checked-in `AGENTS.md` points to the same
public form and routing APIs used by this recipe.

## Observations

| Observation | Hand-written slice | Fixed recipe |
| --- | --- | --- |
| Application source | Two production files with 96 nonblank lines and one test with 61 nonblank lines | Three byte-identical files |
| Existing files and dependencies touched | Zero | Zero |
| Application concepts | Typed route and page, typed form decoder and action, field errors, HTML form, redirect and Spring bindings | Same concepts |
| Customization | The page title and visible heading are ordinary source | Updating both strings and the HTTP assertion changed three lines in two files and passed |
| Reapplication | Keep and edit the application's files | A collision failed before writing the two absent files. Reapplying also preserved all customized source checksums. |

The first manual build surfaced a bean name collision between the configuration class and its router.
Giving the router a separate bean name fixed it. The final manual copy, recipe copy and customized
copy all passed `check bootJar` against `0.1.0-beta.1`. The HTTP test starts a real loopback WebFlux
server on an ephemeral port and checks HTML without scripts, a linked validation error with preserved
text, cross-origin rejection, one successful write, escaped display text and the 303 redirect.

This experiment records file and edit counts. Comparative authoring time and independent reader
comprehension remain unmeasured. Keep the fixed recipe as executable evidence and revisit generator
design if repeated feature work shows a shared specification and update need.
[ADR 0119](../../docs/adr/0119-defer-application-feature-generator.md)
