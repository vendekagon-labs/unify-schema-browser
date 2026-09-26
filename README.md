# Unify Schema Browser

Renders a [Unify](https://github.com/vendekagon-labs/unify) schema (Datomic
schema plus Unify metamodel annotations) as a static documentation site:

- an overview page with schema stats and an interactive kind graph (hover to
  trace a kind's relationships, drag to pan, pinch or Ctrl/⌘+scroll to zoom)
- a page per kind: attributes, types, cardinality, uniqueness, docs, parent and
  children, identity attribute, and every attribute that references it
- a page per enumeration: values, and the attributes that use it
- search across kinds, attributes, enumerations and values (press `/`)
- light and dark themes, optional branding

The output has no runtime dependencies and no server-side component. Each
schema version renders to one self-contained directory that works from
`file://`, any static host, or an S3-backed proxy, under any URL prefix:

```
<out-dir>/<name>/<version>/
  index.html  <kind>.html  <enum>.html
  assets/          css, js, search index, brand logo
  schema.edn       the browser model
  manifest.json    name, version, counts, generated_at, renderer, brand
```

`manifest.json` is intended for tooling that lists published versions.

## Requirements

- Java 17+
- [Graphviz](https://graphviz.org/) `dot` on the PATH (`brew install graphviz`,
  `apt-get install graphviz`), used to lay out the kind graph

## Usage

Build the standalone jar, which is also copied to `package/`:

    clojure -T:build uber

Then render a schema directory (containing `schema.edn`, `metamodel.edn` and
`enums.edn`) or a live database:

    package/render-schema schema/ rendered/
    package/render-schema --brand brand.edn schema/ rendered/
    package/render-schema datomic:dev://localhost:4334/my-db rendered/

From source, without building: `clojure -M:render [--brand brand.edn] <source> <out-dir>`.

### Branding

Branding is optional. Without `--brand`, pages carry no logo or brand name. A
brand is an EDN file:

```clojure
{:name        "Example Commons"         ; required
 :url         "https://example.org/"    ; logo / footer link
 :logo        "logo.svg"                ; svg/png/jpg/webp, relative to this file
 :logo-plate? true                      ; light plate behind the logo in dark mode
 :accent      "#0073a8"                 ; link and highlight color, light theme
 :accent-dark "#3fc0f2"}                ; ... dark theme
```

The logo is copied into each rendered version's `assets/`.

## Render service

The local Unify system (docker compose) runs this as a service that renders a
database's schema on request and serves the result:

    clojure -M:service          # or the Dockerfile image
    curl -X POST localhost:8999/render/<db-name>

Environment: `BASE_DATOMIC_URI` (required), `SERVICE_PORT` (default 8999), and
`SCHEMA_BROWSER_BRAND` (optional path to a brand EDN).

## Development

    clojure -M:test

# License

Apache License 2.0. See [LICENSE](LICENSE) and [NOTICE](NOTICE).

Unify Schema Browser is derived from [Alzabo](https://github.com/CANDELbio/alzabo),
the work of Mike Travers, copyright Parker Institute for Cancer Immunotherapy.
Modifications and additions from 2023 onward are copyright Vendekagon Labs, LLC.
