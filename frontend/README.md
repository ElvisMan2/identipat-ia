# Frontend

This project was generated using [Angular CLI](https://github.com/angular/angular-cli) version 20.3.34.

## Development server

To start a local development server, run:

```bash
ng serve
```

Once the server is running, open your browser and navigate to `http://localhost:4200/`. The application will automatically reload whenever you modify any of the source files.

## Code scaffolding

Angular CLI includes powerful code scaffolding tools. To generate a new component, run:

```bash
ng generate component component-name
```

For a complete list of available schematics (such as `components`, `directives`, or `pipes`), run:

```bash
ng generate --help
```

## Building

To build the project run:

```bash
ng build
```

This will compile your project and store the build artifacts in the `dist/` directory. By default, the production build optimizes your application for performance and speed.

## Diagnostic PDF download

The result page generates the PDF locally using lazily loaded pdfmake, for both real
analyses and the demo. Downloading does not request `/analyses/{id}/report.pdf` and
does not transmit diagnostic content to an external service. Loading the diagnostic
JSON and its existing access checks remain unchanged.

The PDF contains selectable/searchable native text on portrait A4 pages (210 x
297 mm), with 12 mm margins and automatic pagination. It exports the rendered
diagnostic title, date, summary, visible diagnostic sections, warnings and
disclaimer. The application banner, footer, controls, temporary errors and
next-steps modal are excluded. Colors and hierarchy follow the result page,
with layout adapted to A4 rather than a pixel-exact screenshot or mobile layout.

IBM Plex Sans and Space Grotesk static fonts are bundled in `public/fonts` with
their redistribution licenses. They are loaded from the application's base URL
only when exporting; PDF generation does not depend on Google Fonts.

The DOM-to-PDF adapter is intentionally limited to the result page's semantic
blocks. When adding a new diagnostic block, update the adapter and its tests
alongside the result template. Unsupported blocks fail visibly instead of
silently producing an incomplete report.

## Running unit tests

To execute unit tests with the [Karma](https://karma-runner.github.io) test runner, use the following command:

```bash
ng test
```

## Running end-to-end tests

For end-to-end (e2e) testing, run:

```bash
ng e2e
```

Angular CLI does not come with an end-to-end testing framework by default. You can choose one that suits your needs.

## Additional Resources

For more information on using the Angular CLI, including detailed command references, visit the [Angular CLI Overview and Command Reference](https://angular.dev/tools/cli) page.
