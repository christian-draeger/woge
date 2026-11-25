# Invoice desk on Ktor

Use this loopback example to enter, correct and review synthetic invoices with small text receipts.
It uses Woge's typed routes and forms, server-rendered HTML, multipart decoding and the optional
upload progress component. Ktor serves the application with a file-backed H2 store. The application
owns its business rules, translations and edit revisions. The same form and action have HTTP contract
tests on Ktor and Spring WebFlux.

## Run and try an invoice

Use JDK 21 and run from the Woge checkout.

```sh
./gradlew :woge-invoice-desk:run
```

Open `http://127.0.0.1:8080/`. The process prints its address on startup. Set `WOGE_INVOICE_PORT`
for a different port or `WOGE_INVOICE_DATA` for a different local data directory. The default data
directory is `build/invoice-desk-data` relative to the application's working directory. Set the
same absolute data directory when restarting with a different launcher or working directory.

1. Use an English (US), German (Germany), or Arabic (Egypt) browser language preference and reload.
   The form shows the selected locale's number and calendar formats. Arabic uses a right-to-left
   layout with the local numbering system.
2. Enter a total, a service date, a localized due date and a local payment time. For English (US),
   use `90071992547409.93`, `2026-10-11`, `11/18/2026`, and `2026-10-25T03:30`. Payment time uses
   Europe/Berlin. `2026-10-25T02:30` is ambiguous and the server rejects it.
3. Enter two amounts in each allocation group. For English (US), use `1.20`, `2.30`, `3.40`, and
   `4.50`. Choose a synthetic UTF-8 text file up to 2 KiB in the services group and save.
4. Open the same invoice in two tabs. Change the total in each and save the first. Submitting the
   second with its older revision leaves the saved invoice intact. The native response shows a
   conflict and keeps the stale draft visible. Use the reload link to fetch the saved version.
5. Try a field validation error with JavaScript on and off. With enhanced upload, the status message
   appears and the file remains selected. A deliberate native submit shows the translated field
   errors. Select the file again after that page loads. Editing a saved invoice with no new file
   retains its existing receipt.

Use only synthetic data. The example listens on loopback, uses a shared local store and provides
no account or invoice-level authorization. Its schema uses two fixed allocation groups, and the
editor displays receipt size without serving receipt downloads.

## Replay the packaged browser pilot

Install Node 24 for the browser driver. The application package itself runs on Java.

```sh
npm --prefix client/woge-fallback-client ci --ignore-scripts
npm --prefix client/woge-fallback-client exec -- playwright install chromium
./gradlew :woge-invoice-desk:check :woge-invoice-desk:installDist
node client/woge-fallback-client/scripts/test-saved-invoices.mjs --distribution \
  examples/invoice-desk/build/install/woge-invoice-desk/bin/woge-invoice-desk
```

The driver opens six real Chromium sessions across three locales and both JavaScript modes. It
also cancels a held upload, verifies a two-editor conflict and relaunches the distribution on
the same disposable database. To run the optional browser contract against both HTTP host bindings,
set `WOGE_INVOICE_BROWSER=true` when running `:woge-invoice-desk:test`.

[Integration guide](../../docs/guides/enhanced-uploads-and-dynamic-forms.md#save-and-edit-a-localized-invoice-with-a-receipt)
