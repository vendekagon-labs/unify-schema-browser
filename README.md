# Unify Schema Browser

This is a browser based schema documentarion and graph for Datomic schema. It's
derived from the [Alzabo code base](https://github.com/CANDELbio/alzabo) from
CANDELBio and is now included in the
[Unify](https://github.com/vendekagon-labs/unify)
local system.

The schema browser requires Unify metamodel schema annotations to construct a
graph of schema relations.

# Browser internals: alzabo

Alzabo does a number of different tasks centered around a simple schema format for graph databases.

- Defines an .edn schema format, with semantics similar to RDF.
- Tool to convert the [CANDEL schema](https://github.com/ParkerICI/pret/tree/master/resources/schema) into Alzabo format
- Tool to generate Datomic schemas from Alzabo format
- Tool to generate HTML documentation from Alzabo schemas
- A Clojurescript applet to do autocompletion over Alzabo schemas (appears as part of HTML doc)

## Schema format

Schemas are represented as EDN maps. See [an example](test/resources/schema/rawsugar.edn) or the [schema spec](src/cljc/org/parkerici/alzabo/schema.cljc).

`:title` a string
`:version` a string
`:unify.kinds` a map of kind names (keywords) to kind definitions (see below)
`:enums` A map of enum names (keywords) to sequence of enum values (also keywords, generally namespaced)

A kind definition is a map with attributes:
`:fields`: a map of field names (keywords) to field definitions
`:doc` a string
`:reference?` a boolean indicating that this is a reference class (TODO rather too CANDEL specific, maybe generalize)

A field definition is a map with attributes:
`:type` can be:
 - a keyword, either a kind name, a primitive
 - a vector of types (defines a Datomic heterogenous tuple)
 - a map of the form `{:* <type>}` (defines a Datomic homogenous tuple)
   Default is `:string`
`:doc` a string
`:cardinality` Either `:one` (default) or `:many`
`:unique` Either `:identity` or `:value`, see [Datomic doc](https://docs.datomic.com/on-prem/schema.html#operational-schema-attributes) for details.
`:unique-id` (deprecated) `true` means the same as `:unique :identity`
 `:attribute` the datomic or sparql attribute corresponding to the field 

The defined primitives are `#{:string :boolean :float :double :long :bigint :bigdec :instant :keyword :uuid}`. 

## Installation

To generate documentation, you need graphviz installed. On the Mac, you can do this with

    $ brew install graphviz


## Usage

Current supported usage executes in the browser and provides a small service API
layer. Use of the schema browser in Unify is wrapped via container ops and util
script wrappers.

# License

Additions in this repo from 2024 on:

Apache 2.0, Copyright Vendekagon Labs, LLC

Original: Apache 2.0, Alzabo is the work of Mike Travers,
copyright Parker Institute for Cancer Immunotherapy
