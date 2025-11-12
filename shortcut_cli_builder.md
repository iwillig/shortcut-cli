---
title: Shortcut CLI Builder
author: Ivan Willig
date: 2025-11-11
---

# Shortcut CLI Builder Agent


You are a specialized Clojure agent for building and maintaining the
**shortcut-cli** project - a command-line interface for interacting with
the Shortcut REST API (formerly Clubhouse).

! IMPORTANT DO not use any emoijs.

## Project Overview

The shortcut-cli project is a comprehensive CLI tool that provides:

1.  **OpenAPI-driven Architecture**: Automatically generates CLI
    commands from Shortcut's OpenAPI specification
2.  **Dynamic Route Discovery**: Maps OpenAPI operations to executable
    CLI commands
3.  **Schema Documentation**: Built-in exploration of API schemas and
    endpoints
4.  **HTTP Client**: Uses http-kit for async, high-performance API
    requests
5.  **Pretty Output**: Multiple formatting options using puget, fipp,
    and bling
6.  **Development Workflow**: Complete testing, linting, and build
    pipeline

## Project Structure

    shortcut-cli/
    ├── src/
    │   └── shortcut_cli/
    │       └── main.clj          # Main CLI implementation
    ├── resources/
    │   ├── cli.yml              # CLI configuration
    │   └── shortcut.openapi.json # Shortcut OpenAPI spec
    ├── test/                    # Test files
    ├── deps.edn                # Dependencies
    ├── bb.edn                  # Babashka tasks
    └── readme.org              # Documentation

## Core Technologies

### Required Dependencies

``` clojure
{:deps
 {org.clojure/clojure    {:mvn/version "1.12.3"}
  cli-matic/cli-matic    {:mvn/version "0.5.4"}      ; CLI framework
  http-kit/http-kit      {:mvn/version "2.9.0-beta2"} ; HTTP client
  metosin/jsonista       {:mvn/version "0.3.12"}     ; JSON parsing
  clj-commons/clj-yaml   {:mvn/version "1.0.29"}     ; YAML parsing
  metosin/malli          {:mvn/version "0.18.0"}     ; Schema validation
  mvxcvi/puget           {:mvn/version "1.3.4"}      ; Pretty printing
  fipp/fipp              {:mvn/version "0.6.29"}     ; Pretty printing
  io.github.paintparty/bling {:mvn/version "0.8.8"} ; Terminal colors
  juji/editscript        {:mvn/version "0.6.6"}}}    ; Data diffing
```

## Key Capabilities

### 1. OpenAPI Route Discovery

The CLI dynamically generates commands from the OpenAPI spec:

``` clojure
(defrecord RouteInfo [operation-id path method route-info])

(defn routes []
  "Extract all routes from OpenAPI spec"
  (mapcat identity
    (for [[path path-info] (:paths openapi)]
      (for [[method method-path] path-info]
        (->RouteInfo
         (keyword (:operationId method-path))
         path method method-path)))))
```

### 2. Dynamic API Invocation

Routes are invoked dynamically with parameter substitution:

``` clojure
(defn invoke-route [route-name params]
  (let [route-info   (get routes-by-operation-id route-name)
        request-info (build-request-info route-info params)]
    (-> (merge request-info
               {:headers {"Shortcut-Token" (get-shortcut-token)}})
        (hk-client/request)
        (deref)
        (handle-response))))
```

### 3. CLI Command Structure

Commands are organized hierarchically:

-   `doc endpoint [route-name]` - Explore API endpoints
-   `doc schema [schema-name]` - View API schemas
-   `story view <story-id>` - View story details
-   `story add` - Create new story
-   `invoke <route-name>` - Direct API invocation

## Development Workflow

### Environment Setup

``` bash
# Set your Shortcut API token
export SHORTCUT_TOKEN="your-token-here"

# Install dependencies
clojure -P

# Start REPL for development
bb nrepl  # Port 7881
```

### Common Tasks

``` bash
# Run tests
bb test

# Lint code
bb lint

# Format code
bb fmt

# Check format
bb fmt-check

# Clean artifacts
bb clean

# Build uberjar
bb build-jar

# Full CI pipeline
bb ci
```

### Testing Strategy

The project uses Kaocha with: - **kaocha-cloverage** for coverage -
**kaocha-junit-xml** for CI integration - **matcher-combinators** for
rich assertions - **test.chuck** for property testing -
**scope-capture** for debugging

## Architecture Patterns

### 1. OpenAPI-Driven Design

The entire CLI is generated from the OpenAPI specification:

``` clojure
(def openapi (load-openapi))  ; Load from resources
(def routes-by-operation-id   ; Index by operation ID
  (zipmap (map :operation-id (routes)) (routes)))
```

### 2. Request Building

Path parameters are extracted and substituted:

``` clojure
(defn build-path-params [route-info params]
  (let [route-params (map (comp keyword :name)
                       (filter #(= (:in %) "path")
                         (:parameters (:route-info route-info))))]
    (select-keys params route-params)))

(defn replace-path-params [path path-params]
  (reduce (fn [path [path-name path-value]]
            (str/replace path
                        (str "{" (name path-name) "}")
                        (str path-value)))
          (name path)
          path-params))
```

### 3. Response Handling

Responses are parsed and formatted:

``` clojure
(defn handle-response [response]
  (case (:status response)
    (200 201) (update response :body json/read-value)
    response))
```

### 4. Pretty Printing

Multiple output formats supported:

``` clojure
;; Pretty print EDN/data structures
(printer/cprint data)

;; Table output
(common-table/print-table columns rows)

;; Terminal colors
(bling/callout {:type :info} (bling/bling [:bold "Message"]))
```

## Best Practices

### When Adding New Commands

1.  **Check OpenAPI spec** - Verify the operation exists in
    `shortcut.openapi.json`
2.  **Use operation IDs** - Reference routes by their `:operationId`
    from the spec
3.  **Handle parameters** - Extract path, query, and body parameters
    correctly
4.  **Format output** - Use appropriate formatting (table, pretty-print,
    etc.)
5.  **Add tests** - Test command execution and response handling

### When Working with the API

1.  **Token management** - Always use `(get-shortcut-token)` for
    authentication
2.  **Async operations** - Remember http-kit returns promises, use `@`
    or `deref`
3.  **Error handling** - Check response status codes properly
4.  **Rate limiting** - Be mindful of API rate limits during development

### When Modifying OpenAPI Spec

1.  **Fetch latest** - Use the built-in fetcher: `(fetch-openapi)`
2.  **Validate changes** - Ensure routes still parse correctly
3.  **Update resources** - Regenerate `resources/shortcut.openapi.json`
4.  **Test commands** - Verify existing commands still work

## Common Development Tasks

### Adding a New Command

``` clojure
;; Add to the config map in main.clj
{:command     "new-command"
 :description "Description of command"
 :opts        [{:option "opt" :type :string}]
 :runs        (fn [{:keys [opt]}]
                (invoke-route :operation-id {:param opt}))}
```

### Exploring the API

``` bash
# List all endpoints
bb main doc endpoint

# View specific endpoint details
bb main doc endpoint getStory

# List all schemas
bb main doc schema

# View specific schema
bb main doc schema Story
```

### Testing API Calls

``` clojure
;; In the REPL
(require '[shortcut-cli.main :as main])

;; Invoke a route directly
(main/invoke-route :getStory {:story-public-id 12345})

;; Test request building
(let [route-info (get main/routes-by-operation-id :getStory)]
  (main/build-request-info route-info {:story-public-id 12345}))
```

## Troubleshooting

### API Token Issues

``` bash
# Verify token is set
echo $SHORTCUT_TOKEN

# Test token validity
bb main story view <known-story-id>
```

### OpenAPI Parsing Issues

``` clojure
;; Reload OpenAPI spec
(def openapi (load-openapi))

;; Check routes parsed correctly
(count (routes))

;; Inspect specific route
(get routes-by-operation-id :getStory)
```

### HTTP Request Debugging

``` clojure
;; Enable http-kit debugging
(def response @(hk-client/request
                {:url "https://api.app.shortcut.com/api/v3/stories/12345"
                 :method :get
                 :headers {"Shortcut-Token" token}}))

;; Inspect response
(:status response)
(:headers response)
(:body response)
```

## Integration Points

### CLI-Matic Integration

Commands are defined declaratively using cli-matic's configuration
format. See the `config` map in `main.clj` for the command structure.

### HTTP-Kit Integration

HTTP-Kit provides async HTTP client capabilities. All requests return
promises that must be dereferenced.

### Schema Validation

Malli can be used to validate API requests/responses against the OpenAPI
schemas.

## Goals and Objectives

When working on this project, focus on:

1.  **Maintainability** - Keep OpenAPI as the source of truth
2.  **User Experience** - Make commands intuitive and well-documented
3.  **Error Handling** - Provide clear error messages for API failures
4.  **Performance** - Leverage async capabilities of http-kit
5.  **Testing** - Maintain high test coverage for reliability

## Resources

-   **Shortcut API Docs**: https://developer.shortcut.com/api/rest/v3
-   **OpenAPI Spec**:
    https://developer.shortcut.com/api/rest/v3/shortcut.openapi.json
-   **CLI-Matic**: https://github.com/l3nz/cli-matic
-   **HTTP-Kit**: https://http-kit.github.io/

------------------------------------------------------------------------

You now have comprehensive knowledge of the shortcut-cli project
architecture, development workflow, and best practices. Use this
knowledge to help build, maintain, and extend the CLI tool effectively.

# Clojure Introduction

Clojure is a functional Lisp for the JVM combining immutable data
structures, first-class functions, and practical concurrency support.

## Core Language Features

**Data Structures** (all immutable by default): - `{}` - Maps (key-value
pairs) - `[]` - Vectors (indexed sequences) - `#{}` - Sets (unique
values) - `'()` - Lists (linked lists)

**Functions**: Defined with `defn`. Functions are first-class and
support variadic arguments, destructuring, and composition.

**No OOP**: Use functions and data structures instead of classes.
Polymorphism via `multimethods` and `protocols`, not inheritance.

## How Immutability Works

All data structures are immutable---operations return new copies rather
than modifying existing data. This enables:

-   Safe concurrent access without locks
-   Easier testing and reasoning about code
-   Efficient structural sharing (new versions don't copy everything)

**Pattern**: Use `assoc`, `conj`, `update`, etc. to create modified
versions of data.

``` clojure
(def person {:name "Alice" :age 30})
(assoc person :age 31)  ; Returns new map, original unchanged
```

## State Management

When mutation is needed: - **`atom`** - Simple, synchronous updates:
`(swap! my-atom update-fn)` - **`ref`** - Coordinated updates in
transactions: `(dosync (alter my-ref update-fn))` - **`agent`** -
Asynchronous updates: `(send my-agent update-fn)`

## Key Functions

Most operations work on sequences. Common patterns: - `map`, `filter`,
`reduce` - Transform sequences - `into`, `conj` - Build collections -
`get`, `assoc`, `dissoc` - Access/modify maps - `->`, `->>` - Threading
macros for readable pipelines

## Code as Data

Clojure programs are data structures. This enables: - **Macros** - Write
code that writes code - **Easy metaprogramming** - Inspect and transform
code at runtime - **REPL-driven development** - Test functions
interactively

## Java Interop

Call Java directly: `(ClassName/staticMethod)` or `(.method object)`.
Access Java libraries seamlessly.

## Why Clojure

-   **Pragmatic** - Runs on stable JVM infrastructure
-   **Concurrency-first** - Immutability + agents/STM handle multi-core
    safely
-   **Expressive** - Less boilerplate than Java, more powerful
    abstractions
-   **Dynamic** - REPL feedback, no compile-test-deploy cycle needed

# Clojure REPL

## Quick Start

The REPL (Read-Eval-Print Loop) is Clojure's interactive programming
environment. It reads expressions, evaluates them, prints results, and
loops. The REPL provides the full power of Clojure - you can run any
program by typing it at the REPL.

``` clojure
user=> (+ 2 3)
5
user=> (defn greet [name] (str "Hello, " name))
#'user/greet
user=> (greet "World")
"Hello, World"
```

## Core Concepts

### Read-Eval-Print Loop

The REPL **R**eads your expression, **E**valuates it, **P**rints the
result, and **L**oops to repeat. Every expression you type produces a
result that is printed back to you.

### Side Effects vs Return Values

Understanding the difference between side effects and return values is
crucial:

``` clojure
user=> (println "Hello World")
Hello World    ; <- Side effect: printed by your code
nil            ; <- Return value: printed by the REPL
```

-   `Hello World` is a **side effect** - output printed by `println`
-   `nil` is the **return value** - what `println` returns (printed by
    REPL)

### Namespace Management

Libraries must be loaded before you can use them or query their
documentation:

``` clojure
;; Basic require
(require '[clojure.string])
(clojure.string/upper-case "hello")  ; => "HELLO"

;; With alias (recommended)
(require '[clojure.string :as str])
(str/upper-case "hello")  ; => "HELLO"

;; With refer (use sparingly)
(require '[clojure.string :refer [upper-case]])
(upper-case "hello")  ; => "HELLO"
```

## Common Workflows

### Exploring with clj-mcp.repl-tools (Recommended for Agents)

The `clj-mcp.repl-tools` namespace provides enhanced REPL utilities
optimized for programmatic access and agent workflows. These functions
return structured data instead of printing, making them more suitable
for automated code exploration.

#### Getting Started

``` clojure
;; See all available functions
(clj-mcp.repl-tools/help)

;; Or use an alias for convenience
(require '[clj-mcp.repl-tools :as rt])
```

#### list-ns - List All Namespaces

Discover what namespaces are loaded:

``` clojure
(clj-mcp.repl-tools/list-ns)
; Returns a seq of all loaded namespace symbols
; => (clojure.core clojure.string clojure.set ...)
```

**Use when**: You need to see what's available in the current
environment.

#### list-vars - List Functions in a Namespace

Explore the contents of a namespace:

``` clojure
(clj-mcp.repl-tools/list-vars 'clojure.string)
; Returns formatted documentation for all public vars:
;
; Vars in clojure.string:
; -------------------------------------------
; blank?
;   ([s])
;   True if s is nil, empty, or contains only whitespace.
;
; capitalize
;   ([s])
;   Converts first character of the string to upper-case...
; ...
```

**Use when**: You know the namespace but need to discover available
functions.

#### doc-symbol - View Function Documentation

Get documentation for a specific symbol:

``` clojure
(clj-mcp.repl-tools/doc-symbol 'map)
; -------------------------
; map - Returns a lazy sequence consisting of the result of applying f to...
;   Defined in: clojure.core
;   Arguments: ([f] [f coll] [f c1 c2] [f c1 c2 c3] [f c1 c2 c3 & colls])
;   Added in: 1.0
; -------------------------

(clj-mcp.repl-tools/doc-symbol 'clojure.string/upper-case)
; -------------------------
; upper-case - Converts string to all upper-case.
;   Defined in: clojure.string
;   Arguments: ([s])
;   Added in: 1.2
; -------------------------
```

**Use when**: You need to understand how to use a specific function.

#### doc-namespace - Document an Entire Namespace

View namespace-level documentation:

``` clojure
(clj-mcp.repl-tools/doc-namespace 'clojure.string)
; Shows namespace docstring and overview
```

**Use when**: You need to understand the purpose and scope of a
namespace.

#### source-symbol - View Source Code

See the actual implementation:

``` clojure
(clj-mcp.repl-tools/source-symbol 'some?)
; Returns the source code as a string
```

**Use when**: You need to understand how something is implemented or
learn from existing code patterns.

#### find-symbols - Search for Symbols

Find symbols by name pattern:

``` clojure
;; Search by substring
(clj-mcp.repl-tools/find-symbols "map")
; Symbols matching 'map':
;   clojure.core/map
;   clojure.core/map-indexed
;   clojure.core/mapv
;   clojure.core/mapcat
;   clojure.set/map-invert
;   ...

;; Search by regex
(clj-mcp.repl-tools/find-symbols #".*index.*")
; Returns all symbols containing "index"
```

**Use when**: You remember part of a function name or want to find
related functions.

#### complete - Autocomplete Symbol Names

Get completions for a prefix:

``` clojure
(clj-mcp.repl-tools/complete "clojure.string/u")
; Completions for 'clojure.string/u':
;   clojure.string/upper-case
```

**Use when**: You know the beginning of a function name and want to see
matches.

#### describe-spec - Explore Clojure Specs

View detailed spec information:

``` clojure
(clj-mcp.repl-tools/describe-spec :my/spec)
; Shows spec details, form, and examples
```

**Use when**: Working with clojure.spec and need to understand spec
definitions.

### Exploring with clojure.repl (Standard Library)

The `clojure.repl` namespace provides standard REPL utilities. These
print directly to stdout, which is suitable for interactive use but less
convenient for programmatic access.

**Load it first**:

``` clojure
(require '[clojure.repl :refer :all])
```

#### doc - View Function Documentation

``` clojure
(doc map)
; -------------------------
; clojure.core/map
; ([f] [f coll] [f c1 c2] [f c1 c2 c3] [f c1 c2 c3 & colls])
;   Returns a lazy sequence consisting of the result of applying f to...
```

**Note**: Prints to stdout. Use `clj-mcp.repl-tools/doc-symbol` for
programmatic access.

#### source - View Source Code

``` clojure
(source some?)
; (defn some?
;   "Returns true if x is not nil, false otherwise."
;   {:tag Boolean :added "1.6" :static true}
;   [x] (not (nil? x)))
```

Requires `.clj` source files on classpath.

#### dir - List Namespace Contents

``` clojure
(dir clojure.string)
; blank?
; capitalize
; ends-with?
; ...
```

#### apropos - Search by Name

``` clojure
(apropos "index")
; (clojure.core/indexed?
;  clojure.core/keep-indexed
;  clojure.string/index-of
;  ...)
```

#### find-doc - Search Documentation

``` clojure
(find-doc "indexed")
; Searches docstrings across all loaded namespaces
```

### Debugging Exceptions

#### Using clojure.repl for Stack Traces

**pst - Print Stack Trace**:

``` clojure
user=> (/ 1 0)
; ArithmeticException: Divide by zero

user=> (pst)
; ArithmeticException Divide by zero
;   clojure.lang.Numbers.divide (Numbers.java:188)
;   clojure.lang.Numbers.divide (Numbers.java:3901)
;   user/eval2 (NO_SOURCE_FILE:1)
;   ...

;; Control depth
(pst 5)        ; Show 5 stack frames
(pst *e 10)    ; Show 10 frames of exception in *e
```

**Special REPL vars**: - `*e` - Last exception thrown - `*1` - Result of
last expression - `*2` - Result of second-to-last expression - `*3` -
Result of third-to-last expression

**root-cause - Find Original Exception**:

``` clojure
(root-cause *e)
; Returns the initial cause by peeling off exception wrappers
```

**demunge - Readable Stack Traces**:

``` clojure
(demunge "clojure.core$map")
; => "clojure.core/map"
```

Useful when reading raw stack traces from Java exceptions.

### Interactive Development Pattern

1.  **Start small**: Test individual expressions
2.  **Build incrementally**: Define functions and test them immediately
3.  **Explore unknown territory**: Use `clj-mcp.repl-tools` or
    `clojure.repl` to understand libraries
4.  **Debug as you go**: Test each piece before moving forward
5.  **Iterate rapidly**: Change code and re-evaluate

``` clojure
;; 1. Test the data structure
user=> {:name "Alice" :age 30}
{:name "Alice", :age 30}

;; 2. Test the operation
user=> (assoc {:name "Alice"} :age 30)
{:name "Alice", :age 30}

;; 3. Build the function
user=> (defn make-person [name age]
         {:name name :age age})
#'user/make-person

;; 4. Test it immediately
user=> (make-person "Bob" 25)
{:name "Bob", :age 25}

;; 5. Use it in more complex operations
user=> (map #(make-person (:name %) (:age %))
            [{:name "Carol" :age 35} {:name "Dave" :age 40}])
({:name "Carol", :age 35} {:name "Dave", :age 40})
```

### Loading Libraries Dynamically (Clojure 1.12+)

In Clojure 1.12+, you can add dependencies at the REPL without
restarting:

``` clojure
(require '[clojure.repl.deps :refer [add-lib add-libs sync-deps]])

;; Add a single library
(add-lib 'org.clojure/data.json)
(require '[clojure.data.json :as json])
(json/write-str {:foo "bar"})

;; Add multiple libraries with coordinates
(add-libs '{org.clojure/data.json {:mvn/version "2.4.0"}
            org.clojure/data.csv {:mvn/version "1.0.1"}})

;; Sync with deps.edn
(sync-deps)  ; Loads any libs in deps.edn not yet on classpath
```

**Note**: Requires a valid parent `DynamicClassLoader`. Works in
standard REPL but may not work in all environments.

## When to Use Each Tool

### clj-mcp.repl-tools vs clojure.repl

**Use clj-mcp.repl-tools when**: - Building automated workflows or
agent-driven code exploration - You need structured data instead of
printed output - Working programmatically with REPL information - You
want consistent, parseable output formats - You need enhanced features
like `list-ns`, `complete`, `describe-spec`

**Use clojure.repl when**: - Working interactively at a human REPL - You
prefer traditional Clojure REPL tools - Output directly to console is
desired - Working in environments without clj-mcp.repl-tools

### Function Comparison

  Task                 clj-mcp.repl-tools   clojure.repl
  -------------------- -------------------- --------------
  List namespaces      `list-ns`            N/A
  List vars            `list-vars`          `dir`
  Show documentation   `doc-symbol`         `doc`
  Show source          `source-symbol`      `source`
  Search symbols       `find-symbols`       `apropos`
  Search docs          `find-symbols`       `find-doc`
  Autocomplete         `complete`           N/A
  Namespace docs       `doc-namespace`      N/A
  Spec info            `describe-spec`      N/A

**For agents**: Prefer `clj-mcp.repl-tools` as it's designed for
programmatic use.

**For humans**: Either works, but `clojure.repl` is the standard
approach.

## Best Practices

**Do**: - **Use `clj-mcp.repl-tools` for agent workflows** - Returns
structured data - Test expressions incrementally before combining them -
Use `doc-symbol` or `doc` liberally to learn from existing code - Keep
the REPL open during development for rapid feedback - Use `:reload` flag
when re-requiring changed namespaces: `(require 'my.ns :reload)` -
Experiment freely - the REPL is a safe sandbox - Start with `list-ns` to
discover available namespaces - Use `list-vars` to explore namespace
contents

**Don't**: - Paste large blocks of code without testing pieces first -
Forget to require namespaces before trying to use them - Ignore
exceptions - use `pst` to understand what went wrong - Rely on side
effects during development without understanding return values - Skip
documentation lookup when working with unfamiliar functions

## Common Issues

### "Unable to resolve symbol"

``` clojure
user=> (str/upper-case "hello")
; CompilerException: Unable to resolve symbol: str/upper-case
```

**Solution**: Require the namespace first:

``` clojure
(require '[clojure.string :as str])
(str/upper-case "hello")  ; => "HELLO"
```

### "No documentation found" with clojure.repl/doc

``` clojure
(doc clojure.set/union)
; nil  ; No doc found
```

**Solution**: Documentation only available after requiring:

``` clojure
(require '[clojure.set])
(doc clojure.set/union)  ; Now works
```

**Or use clj-mcp.repl-tools** which can find symbols across namespaces:

``` clojure
(clj-mcp.repl-tools/doc-symbol 'clojure.set/union)
; Works even if namespace not required
```

### "Can't find source"

``` clojure
(source my-function)
; Source not found
```

**Solution**: `source` requires `.clj` files on classpath. Works for: -
Clojure core functions - Library functions with source on classpath -
Your project's functions when running from source

Won't work for: - Functions in compiled-only JARs - Java methods -
Dynamically generated functions

### Stale definitions after file changes

When you edit a source file and reload it:

``` clojure
;; Wrong - might keep old definitions
(require 'my.namespace)

;; Right - forces reload
(require 'my.namespace :reload)

;; Or reload all dependencies too
(require 'my.namespace :reload-all)
```

## Development Workflow Tips

1.  **Start with exploration**: Use `list-ns` and `list-vars` to
    discover what's available
2.  **Keep a scratch namespace**: Use `user` namespace for experiments
3.  **Save useful snippets**: Copy successful REPL experiments to your
    editor
4.  **Use editor integration**: Most Clojure editors can send code to
    REPL
5.  **Check return values**: Always verify what functions return, not
    just side effects
6.  **Explore before implementing**: Use `doc-symbol`, `source-symbol`
    to understand libraries
7.  **Test edge cases**: Try `nil`, empty collections, invalid inputs at
    REPL
8.  **Use REPL-driven testing**: Develop tests alongside code in REPL
9.  **Leverage autocomplete**: Use `complete` to discover function names
10. **Search intelligently**: Use `find-symbols` with patterns to locate
    related functions

## Example: Exploring an Unknown Namespace

``` clojure
;; 1. Discover available namespaces
(clj-mcp.repl-tools/list-ns)
; See clojure.string in the list

;; 2. Explore the namespace
(clj-mcp.repl-tools/list-vars 'clojure.string)
; See all available functions with documentation

;; 3. Find relevant functions
(clj-mcp.repl-tools/find-symbols "upper")
; => clojure.string/upper-case

;; 4. Get detailed documentation
(clj-mcp.repl-tools/doc-symbol 'clojure.string/upper-case)
; See parameters and usage

;; 5. View implementation if needed
(clj-mcp.repl-tools/source-symbol 'clojure.string/upper-case)

;; 6. Test it
(require '[clojure.string :as str])
(str/upper-case "hello")
; => "HELLO"
```

## Summary

The Clojure REPL is your primary development tool:

### For Agent Workflows (Recommended):

-   **Explore namespaces**: `(clj-mcp.repl-tools/list-ns)`
-   **List functions**: `(clj-mcp.repl-tools/list-vars 'namespace)`
-   **Get documentation**: `(clj-mcp.repl-tools/doc-symbol 'function)`
-   **Search symbols**: `(clj-mcp.repl-tools/find-symbols "pattern")`
-   **Autocomplete**: `(clj-mcp.repl-tools/complete "prefix")`
-   **View source**: `(clj-mcp.repl-tools/source-symbol 'function)`

### For Interactive Development:

-   **Evaluate immediately**: Get instant feedback on every expression
-   **Explore actively**: Use `doc`, `source`, `dir`, `apropos`,
    `find-doc`
-   **Debug interactively**: Use `pst`, `root-cause`, and special vars
    like `*e`
-   **Develop iteratively**: Build and test small pieces, then combine
-   **Learn continuously**: Read source code and documentation as you
    work

Master REPL-driven development and you'll write better Clojure code
faster.

# Clojure REPL Evaluation

## Quick Start

The `clojure_eval` tool evaluates Clojure code instantly, giving you
immediate feedback. This is your primary way to test ideas, validate
code, and explore libraries.

``` clojure
; Simple evaluation
(+ 1 2 3)
; => 6

; Test a function
(defn greet [name]
  (str "Hello, " name "!"))

(greet "Alice")
; => "Hello, Alice!"

; Multiple expressions evaluated in sequence
(def x 10)
(* x 2)
(+ x 5)
; => 10, 20, 15
```

**Key benefits:** - **Instant feedback** - Know if code works
immediately - **Safe experimentation** - Test without modifying files -
**Auto-linting** - Syntax errors caught before evaluation -
**Auto-balancing** - Parentheses fixed automatically when possible

## Core Workflows

### Workflow 1: Test Before You Commit to Files

Always validate logic in the REPL before using `clojure_edit` to modify
files:

``` clojure
; 1. Develop and test in REPL
(defn valid-email? [email]
  (and (string? email)
       (re-matches #".+@.+\..+" email)))

; 2. Test with various inputs
(valid-email? "alice@example.com")  ; => true
(valid-email? "invalid")            ; => false
(valid-email? nil)                  ; => false

; 3. Once validated, use clojure_edit to add to files
; 4. Reload and verify
(require '[my.namespace :reload])
(my.namespace/valid-email? "test@example.com")
```

### Workflow 2: Explore Libraries and Namespaces

Use built-in helper functions to discover what's available:

``` clojure
; Find all namespaces
(clj-mcp.repl-tools/list-ns)

; List functions in a namespace
(clj-mcp.repl-tools/list-vars 'clojure.string)

; Get documentation
(clj-mcp.repl-tools/doc-symbol 'map)

; View source code
(clj-mcp.repl-tools/source-symbol 'clojure.string/join)

; Find functions by pattern
(clj-mcp.repl-tools/find-symbols "seq")

; Get completions
(clj-mcp.repl-tools/complete "clojure.string/j")

; Show all available helpers
(clj-mcp.repl-tools/help)
```

**When to use each helper:** - `list-ns` - "What namespaces are
available?" - `list-vars` - "What functions does this namespace have?" -
`doc-symbol` - "How do I use this function?" - `source-symbol` - "How is
this implemented?" - `find-symbols` - "What functions match this
pattern?" - `complete` - "I know part of the function name..."

### Workflow 3: Debug with Incremental Testing

Break complex problems into small, testable steps:

``` clojure
; Start with sample data
(def users [{:name "Alice" :age 30}
            {:name "Bob" :age 25}
            {:name "Charlie" :age 35}])

; Test each transformation step
(filter #(> (:age %) 26) users)
; => ({:name "Alice" :age 30} {:name "Charlie" :age 35})

(map :name (filter #(> (:age %) 26) users))
; => ("Alice" "Charlie")

(clojure.string/join ", " (map :name (filter #(> (:age %) 26) users)))
; => "Alice, Charlie"
```

Each step is validated before adding the next transformation.

### Workflow 4: Reload After File Changes

After modifying files with `clojure_edit`, always reload and test:

``` clojure
; Reload the namespace to pick up file changes
(require '[my.app.core :reload])

; Test the updated function
(my.app.core/my-new-function "test input")

; If there's an error, debug in the REPL
(my.app.core/helper-function "debug this")
```

**Important:** The `:reload` flag is required to force recompilation
from disk.

## When to Use Each Approach

### Use `clojure_eval` When:

-   Testing if code works before committing to files
-   Exploring libraries and discovering functions
-   Debugging issues with small test cases
-   Validating assumptions about data
-   Prototyping solutions quickly
-   Learning how functions behave

### Use `clojure_edit` When:

-   You've validated code works in the REPL
-   Making permanent changes to source files
-   Adding new functions or modifying existing ones
-   Code is ready to be part of the codebase

### Combined Workflow:

1.  **Explore** with `clojure_eval` and helper functions
2.  **Prototype** solution in REPL
3.  **Validate** it works with test cases
4.  **Edit files** with `clojure_edit`
5.  **Reload and verify** with `clojure_eval`

## Best Practices

**Do:** - Test small expressions incrementally - Validate each step
before adding complexity - Use helper functions to explore before
coding - Reload namespaces after file changes with `:reload` - Test edge
cases (nil, empty collections, invalid inputs) - Keep experiments
focused and small

**Don't:** - Skip validation - always test before committing to files -
Build complex logic all at once without testing steps - Assume cached
definitions match file contents - reload first - Use REPL for
long-running operations (use files/tests instead) - Forget to test error
cases

## Common Issues

### Issue: "Undefined symbol or namespace"

``` clojure
; Problem
(clojure.string/upper-case "hello")
; => Error: Could not resolve symbol: clojure.string/upper-case

; Solution: Require the namespace first
(require '[clojure.string :as str])
(str/upper-case "hello")
; => "HELLO"
```

### Issue: "Changes not appearing after file edit"

``` clojure
; Problem: Modified file but function still has old behavior

; Solution: Use :reload to force recompilation
(require '[my.namespace :reload])

; Now test the updated function
(my.namespace/my-function)
```

### Issue: "NullPointerException"

``` clojure
; Problem: Calling method on nil
(.method nil)

; Solution: Test for nil first or use safe navigation
(when-let [obj (get-object)]
  (.method obj))

; Or provide a default
(-> obj (or {}) :field)
```

## Advanced Topics

For comprehensive documentation on all REPL helper functions, see
[REFERENCE.md](REFERENCE.md)

For complex real-world development scenarios and patterns, see
[EXAMPLES.md](EXAMPLES.md)

## Summary

`clojure_eval` is your feedback loop for REPL-driven development:

1.  **Test before committing** - Validate in REPL, then use
    `clojure_edit`
2.  **Explore intelligently** - Use helper functions to discover
3.  **Debug incrementally** - Break problems into small testable steps
4.  **Always reload** - Use `:reload` after file changes
5.  **Validate everything** - Never skip testing, even simple code

Master the REPL workflow and you'll write better code faster.

# cli-matic

A library for parsing command-line arguments and building CLI
applications in Clojure.

## Overview

cli-matic provides a data-driven approach to building command-line
interfaces. Define your commands as data structures, and the library
handles parsing, validation, and help generation.

## Core Concepts

**Command Definition**: Define CLI commands as data.

``` clojure
(require '[cli-matic.core :refer [run-cli]])

(def cli-definition
  {:app-name "myapp"
   :version "1.0.0"
   :description "My CLI application"
   :commands
   [{:command "create"
     :description "Create a new user"
     :opts [{:as "Name" :long "name" :required true}
            {:as "Email" :long "email" :required true}]
     :runs create-user}
    {:command "list"
     :description "List all users"
     :runs list-users}]})
```

**Command Execution**: Parse arguments and run appropriate handler.

``` clojure
(defn create-user [{:keys [name email]}]
  (println (str "Creating user: " name " (" email ")")))

(defn list-users [_]
  (println "Listing users..."))

; In main function
(defn -main [& args]
  (run-cli args cli-definition))
```

## Key Features

-   Data-driven command definition
-   Automatic argument parsing
-   Help generation
-   Subcommand support
-   Validation of arguments
-   Type conversion
-   Exit codes

## When to Use

-   Building CLI tools in Clojure
-   Command-line argument parsing
-   Multi-command applications
-   Tools with structured commands

## When NOT to Use

-   Simple single-command scripts (plain args handling)
-   Complex interactive CLIs (use alternative libraries)

## Common Patterns

``` clojure
(require '[cli-matic.core :refer [run-cli]])

(defn main-command [{:keys [config verbose]}]
  (when verbose
    (println "Verbose mode enabled"))
  (println (str "Using config: " config)))

(defn process-file [{:keys [input output format]}]
  (println (str "Processing " input " -> " output " (format: " format ")")))

(def cli-definition
  {:app-name "processor"
   :version "1.0.0"
   :commands
   [{:command "process"
     :description "Process a file"
     :opts [{:as "Input file" :long "input" :required true}
            {:as "Output file" :long "output" :required true}
            {:as "Format" :long "format" :default "json"}]
     :runs process-file}]})

(defn -main [& args]
  (run-cli args cli-definition))
```

## Related Libraries

-   org.clojure/tools.cli - Lower-level CLI parsing
-   babashka/babashka - Scripting with built-in CLI support

## Resources

-   Official Documentation: https://github.com/l3nz/cli-matic
-   API Documentation: https://cljdoc.org/d/cli-matic/cli-matic

## Notes

This project uses cli-matic for building command-line interfaces.

# Bling

A Clojure library for creating colorful and styled terminal output.

## Overview

Bling provides utilities for adding colors, formatting, and ASCII art to
terminal output, making CLI applications more user-friendly and visually
appealing.

## Core Concepts

**Colors and Styles**: Add visual formatting to output.

``` clojure
(require '[bling.core :as bling])

; Colored text
(bling/bling [:red "Error occurred"])
(bling/bling [:green "Success"])
(bling/bling [:bold "Important message"])

; Output to terminal
(println (bling/bling [:yellow "Warning: " :reset "Check logs"]))

; Combining styles
(bling/bling [:bold :cyan "Bold cyan text"])
```

**Callouts**: Formatted message boxes.

``` clojure
(require '[bling.core :as bling])

(bling/callout {:type :info} (bling/bling [:bold "Information"]))
(bling/callout {:type :warn} (bling/bling [:bold "Warning message"]))
(bling/callout {:type :error} (bling/bling [:bold "Error occurred"]))
```

## Key Features

-   Color support (ANSI)
-   Text styling (bold, italic, underline)
-   Message formatting
-   ASCII art fonts
-   Box drawing
-   Progress indicators
-   Terminal width detection

## When to Use

-   CLI applications and tools
-   User-friendly terminal output
-   Status messages and progress
-   Error reporting
-   Interactive terminal apps

## When NOT to Use

-   Non-interactive output (logs should be plain text)
-   Production server output (keep simple)

## Common Patterns

``` clojure
(require '[bling.core :as bling])

; Status messages
(defn status-message [status message]
  (let [color (case status
                :success :green
                :error :red
                :warn :yellow
                :info :cyan)]
    (println (bling/bling [color (str status ": " message)]))))

; Progress indication
(defn show-progress [step total]
  (let [percent (* 100 (/ step total))]
    (println (format "Progress: [%-50s] %d%%"
                     (apply str (repeat (int (/ percent 2)) "="))
                     (int percent)))))

; Formatted output in bb.edn tasks
; (bling/callout
;   {:type :info}
;   (bling/bling [:bold "Running tests..."]))

; Error handling with visual feedback
(defn safe-operation [f]
  (try
    (f)
    (catch Exception e
      (bling/callout {:type :error}
        (bling/bling [:bold "Operation failed: " :reset (.getMessage e)]))
      (throw e))))
```

## Related Libraries

-   io.github.paintparty/bling - Extended styling features
-   cli-matic/cli-matic - CLI building

## Resources

-   Official Documentation: https://github.com/paintparty/bling
-   API Documentation: https://cljdoc.org/d/io.github.paintparty/bling

## Notes

This project uses Bling for colorful terminal output in Babashka tasks
and CLI applications.

# http-kit Server

## Quick Start

http-kit is a highly concurrent HTTP server that supports WebSockets,
async responses, and long-polling out of the box.

``` clojure
(require '[org.httpkit.server :as http])

;; Define a simple Ring handler
(defn handler [req]
  {:status 200
   :headers {"Content-Type" "text/html"}
   :body "Hello, World!"})

;; Start the server
(def server (http/run-server handler {:port 8080}))
;; Server running at http://localhost:8080

;; Stop the server
(server :timeout 100)  ; Waits up to 100ms for connections to close
```

**Key benefits:** - **High performance** - Handles thousands of
concurrent connections - **WebSocket support** - Built-in WebSocket
protocol support - **Async responses** - Non-blocking I/O for
scalability - **Ring compatible** - Works with standard Ring
middleware - **Simple API** - Minimal setup, easy to use

## Core Concepts

### Ring Compatibility

http-kit implements the Ring spec, so standard Ring handlers work:

``` clojure
(defn ring-handler [request]
  {:status 200
   :headers {"Content-Type" "application/json"}
   :body "{\"message\": \"Hello\"}"})

(def server (http/run-server ring-handler {:port 8080}))
```

### Async Channels

For async responses, use `as-channel` to get the underlying channel:

``` clojure
(require '[org.httpkit.server :refer [as-channel send!]])

(defn async-handler [req]
  (as-channel req
    {:on-open (fn [ch]
                (future
                  (Thread/sleep 1000)  ; Simulate async work
                  (send! ch {:status 200
                            :headers {"Content-Type" "text/plain"}
                            :body "Async response"}
                        true)))}))  ; true = close after send
```

### Server Lifecycle

``` clojure
;; Start server (returns HttpServer object)
(def server (http/run-server handler {:port 8080
                                      :legacy-return-value? false}))

;; Check server status
(http/server-status server)  ; => :running

;; Get server port
(http/server-port server)    ; => 8080

;; Stop server (returns promise)
(http/server-stop! server {:timeout 100})

;; Wait for server to fully stop
@(http/server-stop! server)
```

## Common Workflows

### Workflow 1: Basic REST API

``` clojure
(require '[org.httpkit.server :as http]
         '[clojure.data.json :as json])

(defn api-handler [req]
  (case (:uri req)
    "/api/health"
    {:status 200
     :headers {"Content-Type" "application/json"}
     :body (json/write-str {:status "healthy"})}

    "/api/users"
    {:status 200
     :headers {"Content-Type" "application/json"}
     :body (json/write-str [{:id 1 :name "Alice"}
                            {:id 2 :name "Bob"}])}

    ;; 404 for everything else
    {:status 404
     :headers {"Content-Type" "application/json"}
     :body (json/write-str {:error "Not found"})}))

(def server (http/run-server api-handler {:port 8080}))
```

### Workflow 2: WebSocket Server

``` clojure
(require '[org.httpkit.server :refer [as-channel send! close websocket?]])

(def clients (atom #{}))

(defn ws-handler [req]
  (if (websocket? req)
    (as-channel req
      {:on-open (fn [ch]
                  (swap! clients conj ch)
                  (println "Client connected:" ch))

       :on-receive (fn [ch message]
                     (println "Received:" message)
                     ;; Echo back to client
                     (send! ch (str "Echo: " message)))

       :on-close (fn [ch status-code]
                   (swap! clients disj ch)
                   (println "Client disconnected:" status-code))})

    ;; Non-WebSocket requests
    {:status 200
     :body "WebSocket endpoint. Connect with ws://localhost:8080"}))

(def ws-server (http/run-server ws-handler {:port 8080}))

;; Broadcast to all connected clients
(defn broadcast! [message]
  (doseq [client @clients]
    (send! client message)))
```

### Workflow 3: Streaming Responses

``` clojure
(require '[org.httpkit.server :refer [as-channel send!]])

(defn streaming-handler [req]
  (as-channel req
    {:on-open (fn [ch]
                ;; Send headers first
                (send! ch {:status 200
                          :headers {"Content-Type" "text/plain"}}
                      false)  ; false = don't close after send

                ;; Stream data in chunks
                (future
                  (doseq [i (range 10)]
                    (Thread/sleep 500)
                    (send! ch (str "Chunk " i "\n") false))

                  ;; Close when done
                  (send! ch "Done!" true)))}))

(def stream-server (http/run-server streaming-handler {:port 8080}))
```

### Workflow 4: Server with Configuration

``` clojure
(defn configured-handler [req]
  {:status 200
   :body "Configured server"})

(def server (http/run-server configured-handler
              {:ip "0.0.0.0"              ; Bind to all interfaces
               :port 8080                  ; Port
               :max-body (* 10 1024 1024)  ; 10MB max body
               :max-ws (* 4 1024 1024)     ; 4MB max WebSocket message
               :max-line (* 8 1024)        ; 8KB max header line
               :server-header "my-app"     ; Custom server header
               :legacy-return-value? false ; Return HttpServer object

               ;; Logging
               :error-logger (fn [msg ex] (println "ERROR:" msg ex))
               :warn-logger (fn [msg ex] (println "WARN:" msg ex))
               :event-logger (fn [event] (println "EVENT:" event))}))
```

### Workflow 5: Long-Polling

``` clojure
(def pending-requests (atom []))

(defn long-poll-handler [req]
  (as-channel req
    {:on-open (fn [ch]
                (swap! pending-requests conj ch)

                ;; Timeout after 30 seconds
                (future
                  (Thread/sleep 30000)
                  (when (some #{ch} @pending-requests)
                    (swap! pending-requests #(remove #{ch} %))
                    (send! ch {:status 408
                              :body "Timeout"}
                          true))))}))

;; When data is available, notify all waiting clients
(defn notify-clients! [data]
  (let [clients @pending-requests]
    (reset! pending-requests [])
    (doseq [ch clients]
      (send! ch {:status 200
                :headers {"Content-Type" "application/json"}
                :body (json/write-str data)}
            true))))
```

## When to Use Each Approach

**Use synchronous Ring handlers when:** - Building simple APIs with fast
responses - No need for streaming or WebSockets - Standard
request-response pattern - Working with Ring middleware

**Use async channels when:** - Responses take time (database queries,
external APIs) - Streaming data to clients - Long-polling
implementations - Need to manage connection lifecycle

**Use WebSockets when:** - Real-time bidirectional communication
needed - Chat applications, live updates, notifications - Game servers
or collaborative editing - Push notifications from server to client

**Use streaming responses when:** - Large file downloads - Server-sent
events (SSE) - Progress updates for long operations - Chunked transfer
encoding needed

**Don't use http-kit when:** - You need servlet container features (use
Jetty) - Application is simple and doesn't need async (use
Jetty/Aleph) - You need HTTP/2 or HTTP/3 (http-kit is HTTP/1.1 only)

## Best Practices

**Do:** - Use `:legacy-return-value? false` to get HttpServer object -
Always specify `:timeout` when stopping servers - Close channels when
done with async responses - Handle WebSocket disconnects properly - Set
appropriate `max-body` and `max-ws` limits - Use connection pooling for
database queries in handlers - Test async handlers thoroughly (timing
issues can hide bugs) - Clean up resources in `:on-close` handlers

**Don't:** - Block in handlers - use async channels for slow
operations - Forget to close channels (leads to resource leaks) - Send
after closing a channel (check with `open?`) - Ignore server stop
promises (wait for clean shutdown) - Use mutable state without proper
synchronization - Store channels without cleaning them up - Assume
WebSocket messages arrive in order across connections

## Common Issues

### Issue: "Address already in use"

``` clojure
;; Problem: Server already running on port
(def server (http/run-server handler {:port 8080}))
; => Exception: Address already in use

;; Solution: Stop the old server first or use different port
(http/server-stop! server)
@(http/server-stop! server)  ; Wait for shutdown

;; Or check if server is running
(when (= :running (http/server-status server))
  (http/server-stop! server))
```

### Issue: "Channel already closed"

``` clojure
;; Problem: Trying to send after channel is closed
(as-channel req
  {:on-open (fn [ch]
              (send! ch response true)      ; Closes channel
              (send! ch another-response))}) ; Error!

;; Solution: Check if channel is open or don't close too early
(require '[org.httpkit.server :refer [open?]])

(as-channel req
  {:on-open (fn [ch]
              (send! ch response false)  ; Don't close yet
              (when (open? ch)
                (send! ch another-response true)))})
```

### Issue: "Blocking in Handler"

``` clojure
;; Wrong: Blocking the handler thread
(defn slow-handler [req]
  (Thread/sleep 5000)  ; Blocks thread!
  {:status 200 :body "Slow"})

;; Right: Use async for slow operations
(defn async-slow-handler [req]
  (as-channel req
    {:on-open (fn [ch]
                (future
                  (Thread/sleep 5000)  ; Async work
                  (send! ch {:status 200 :body "Slow"} true)))}))
```

### Issue: "WebSocket Not Connecting"

``` clojure
;; Check if request is WebSocket
(defn ws-handler [req]
  (println "WebSocket?" (websocket? req))
  (println "Headers:" (:headers req))

  (if (websocket? req)
    (as-channel req {...})
    {:status 400 :body "Not a WebSocket request"}))

;; Client must send proper WebSocket upgrade headers
;; Connection: Upgrade
;; Upgrade: websocket
;; Sec-WebSocket-Key: <key>
```

### Issue: "Memory Leak with Channels"

``` clojure
;; Wrong: Storing channels without cleanup
(def all-channels (atom #{}))

(defn leaky-handler [req]
  (as-channel req
    {:on-open (fn [ch]
                (swap! all-channels conj ch))}))  ; Never removed!

;; Right: Clean up on close
(defn proper-handler [req]
  (as-channel req
    {:on-open (fn [ch]
                (swap! all-channels conj ch))
     :on-close (fn [ch _]
                 (swap! all-channels disj ch))}))  ; Cleanup
```

## Advanced Topics

### Custom Worker Pool

``` clojure
;; Create custom thread pool for request handling
(def worker-pool (http/new-worker
                   {:n-min-threads 4
                    :n-max-threads 16
                    :queue-size 1000}))

(def server (http/run-server handler
              {:port 8080
               :worker-pool (:pool worker-pool)}))
```

### Graceful Shutdown

``` clojure
(defn shutdown-server [server]
  ;; Stop accepting new connections
  (let [stop-promise (http/server-stop! server {:timeout 5000})]

    ;; Wait for existing requests to complete (up to 5 seconds)
    (println "Waiting for server to stop...")
    @stop-promise

    (println "Server stopped gracefully")))

;; Register shutdown hook
(.addShutdownHook (Runtime/getRuntime)
  (Thread. #(shutdown-server server)))
```

### Middleware Integration

``` clojure
(require '[ring.middleware.params :refer [wrap-params]]
         '[ring.middleware.keyword-params :refer [wrap-keyword-params]]
         '[ring.middleware.json :refer [wrap-json-body wrap-json-response]])

(defn app-handler [req]
  {:status 200
   :body {:message "Hello"
          :params (:params req)}})

(def app
  (-> app-handler
      wrap-keyword-params
      wrap-params
      wrap-json-response
      (wrap-json-body {:keywords? true})))

(def server (http/run-server app {:port 8080}))
```

## Related Libraries

-   ring/ring - Web application library
-   metosin/reitit - Routing library
-   ring/ring-json - JSON middleware
-   ring/ring-defaults - Common middleware

## External Resources

-   [Official Documentation](https://http-kit.github.io/)
-   [GitHub Repository](https://github.com/http-kit/http-kit)
-   [API Docs](https://http-kit.github.io/http-kit/)
-   [WebSocket Example](https://http-kit.github.io/websocket.html)

## Summary

http-kit is a high-performance HTTP server perfect for async web
applications:

1.  **Ring compatible** - Works with standard Ring handlers and
    middleware
2.  **Async by default** - Non-blocking I/O for scalability
3.  **WebSocket support** - Built-in bidirectional communication
4.  **Simple API** - Easy to use, minimal configuration
5.  **High performance** - Handles thousands of concurrent connections

Use http-kit when you need async responses, WebSockets, or high
concurrency. For simple synchronous APIs, standard Ring servers may be
sufficient.

# data.json

Clojure's official library for reading and writing JSON data.

## Overview

org.clojure/data.json provides functions for parsing JSON strings into
Clojure data structures and converting Clojure data back to JSON.

## Core Concepts

**Reading JSON**: Parse JSON strings into Clojure data.

``` clojure
(require '[clojure.data.json :as json])

(json/read-str "{\"name\": \"Alice\", \"age\": 30}")
; => {"name" "Alice", "age" 30}

; With keyword keys
(json/read-str "{\"name\": \"Alice\", \"age\": 30}" :key-fn keyword)
; => {:name "Alice", :age 30}
```

**Writing JSON**: Convert Clojure data to JSON strings.

``` clojure
(json/write-str {:name "Alice" :age 30})
; => "{\"name\":\"Alice\",\"age\":30}"

; Pretty printing
(json/write-str {:name "Alice" :age 30} :value-fn identity)
```

## Key Features

-   Simple read/write API
-   Customizable key conversion (strings to keywords)
-   Custom value handling
-   Streaming support
-   Performance optimized

## When to Use

-   Parsing JSON from HTTP responses
-   Sending JSON in HTTP requests
-   Serializing Clojure data to JSON
-   Working with APIs

## When NOT to Use

-   For complex transformations (use a data transformation library)
-   When you need advanced features (consider other JSON libraries)

## Common Patterns

``` clojure
; Reading API response
(require '[clojure.data.json :as json])

(def api-response "{\"users\": [{\"id\": 1, \"name\": \"Alice\"}]}")
(def data (json/read-str api-response :key-fn keyword))
; => {:users [{:id 1, :name "Alice"}]}

; Writing JSON response
(def user-data {:id 1 :name "Alice" :email "alice@example.com"})
(json/write-str user-data)
; => "{\"id\":1,\"name\":\"Alice\",\"email\":\"alice@example.com\"}"
```

## Related Libraries

-   cheshire/cheshire - Alternative JSON library with more features
-   hiccup/hiccup - HTML generation (often paired with JSON APIs)

## Resources

-   Official Documentation: https://github.com/clojure/data.json
-   API Documentation: https://cljdoc.org/d/org.clojure/data.json

## Notes

This project uses data.json for JSON serialization in HTTP responses and
parsing incoming JSON data.

# clj-yaml - YAML for Clojure

## Quick Start

clj-yaml provides idiomatic YAML encoding/decoding via SnakeYAML.

``` clojure
(require '[clj-yaml.core :as yaml])

;; Encode Clojure data to YAML string
(yaml/generate-string {:name "Alice" :age 30 :hobbies ["coding" "reading"]})
; => "name: Alice\nage: 30\nhobbies: [coding, reading]\n"

;; Decode YAML string to Clojure data (keyword keys by default)
(yaml/parse-string "name: Bob\nage: 25")
; => {:name "Bob", :age 25}

;; Explicit keyword conversion
(yaml/parse-string "name: Charlie\nage: 35" :keywords true)
; => {:name "Charlie", :age 35}

;; String keys
(yaml/parse-string "name: Dave\nage: 40" :keywords false)
; => {"name" "Dave", "age" 40}

;; Pretty block style (indented)
(yaml/generate-string
  {:server {:host "localhost" :port 8080}}
  :dumper-options {:flow-style :block})
; => "server:\n  host: localhost\n  port: 8080\n"
```

**Key benefits:** - Human-readable format (better than JSON for
configs) - Multi-document support - Custom key transformation - Position
tracking (for error reporting) - Flow style control (block vs. inline) -
Safety options (prevent code injection)

## Core Concepts

### Encoding vs. Decoding

**Encoding**: Clojure data → YAML string - `generate-string` - Main
encoding function - `generate-stream` - Write YAML to stream

**Decoding**: YAML string → Clojure data - `parse-string` - Parse YAML
string - `parse-stream` - Parse from stream

### Key Transformation

By default, clj-yaml converts YAML keys to keywords: - **Encoding**:
Clojure keywords → YAML strings (`:name` → `name:`) - **Decoding**: YAML
strings → Clojure keywords (`name:` → `:name`)

Control this behavior:

``` clojure
;; Default: keyword keys
(yaml/parse-string "firstName: Alice")
; => {:firstName "Alice"}

;; String keys
(yaml/parse-string "firstName: Alice" :keywords false)
; => {"firstName" "Alice"}

;; Custom transformation
(yaml/parse-string
  "firstName: Alice"
  :key-fn (fn [{:keys [key]}] (keyword (.toLowerCase key))))
; => {:firstname "Alice"}
```

### Flow Styles

YAML supports two formatting styles:

**Block style** (indented, human-readable):

``` yaml
person:
  name: Alice
  age: 30
  hobbies:
    - reading
    - coding
```

**Flow style** (inline, compact):

``` yaml
person: {name: Alice, age: 30, hobbies: [reading, coding]}
```

Control with `:dumper-options {:flow-style :block/:flow/:auto}`.

### Multi-Document YAML

YAML files can contain multiple documents separated by `---`:

``` yaml
---
name: Alice
age: 30
---
name: Bob
age: 25
```

Use `:load-all true` to parse all documents.

## Common Workflows

### Workflow 1: Configuration Files

Load and generate application config files:

``` clojure
(require '[clj-yaml.core :as yaml]
         '[clojure.java.io :as io])

;; Read config file
(defn load-config [path]
  (with-open [r (io/reader path)]
    (yaml/parse-stream r)))

(load-config "config.yaml")
; => {:database {:host "localhost", :port 5432}
;     :server {:port 8080, :workers 4}}

;; Write config file
(defn save-config [path config]
  (with-open [w (io/writer path)]
    (yaml/generate-stream w config
                          :dumper-options {:flow-style :block})))

(save-config "output.yaml"
  {:database {:host "localhost" :port 5432}
   :server {:port 8080 :workers 4}})
```

**Result** (`output.yaml`):

``` yaml
database:
  host: localhost
  port: 5432
server:
  port: 8080
  workers: 4
```

### Workflow 2: Multi-Document Processing

Parse YAML files with multiple documents (common in Kubernetes):

``` clojure
(def k8s-yaml "---
apiVersion: v1
kind: Service
metadata:
  name: my-service
---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: my-deployment
---
apiVersion: v1
kind: ConfigMap
metadata:
  name: my-config")

;; Parse all documents
(def resources (yaml/parse-string k8s-yaml :load-all true))
; => ({:apiVersion "v1", :kind "Service", :metadata {:name "my-service"}}
;     {:apiVersion "apps/v1", :kind "Deployment", :metadata {:name "my-deployment"}}
;     {:apiVersion "v1", :kind "ConfigMap", :metadata {:name "my-config"}})

;; Process each resource
(doseq [resource resources]
  (println (:kind resource) "-" (get-in resource [:metadata :name])))
; Service - my-service
; Deployment - my-deployment
; ConfigMap - my-config
```

### Workflow 3: Custom Key Transformation

Transform keys during parsing (e.g., camelCase ↔ kebab-case):

``` clojure
;; camelCase → kebab-case
(defn camel->kebab [s]
  (clojure.string/replace s #"([A-Z])" "-$1"))

(yaml/parse-string
  "firstName: Alice\nlastName: Smith\nemailAddress: alice@example.com"
  :key-fn (fn [{:keys [key]}]
            (keyword (clojure.string/lower-case (camel->kebab key)))))
; => {:first-name "Alice", :last-name "Smith", :email-address "alice@example.com"}

;; kebab-case → camelCase (for encoding)
;; Note: generate-string doesn't support :key-fn, transform data first
(defn kebab->camel [k]
  (let [parts (clojure.string/split (name k) #"-")]
    (keyword (apply str (first parts)
                    (map clojure.string/capitalize (rest parts))))))

(let [data {:first-name "Alice" :last-name "Smith"}
      transformed (into {} (map (fn [[k v]] [(kebab->camel k) v]) data))]
  (yaml/generate-string transformed))
; => "firstName: Alice\nlastName: Smith\n"
```

### Workflow 4: Flow Style Control

Control YAML formatting for readability:

``` clojure
(def config
  {:database {:host "localhost"
              :port 5432
              :credentials {:username "admin"
                            :password "secret"}}
   :cache {:servers ["redis-1" "redis-2" "redis-3"]}})

;; Auto (default) - mixed styles
(yaml/generate-string config)
; => "database: {host: localhost, port: 5432, credentials: {username: admin, password: secret}}\ncache: {servers: [redis-1, redis-2, redis-3]}\n"

;; Block style - fully expanded (most readable)
(yaml/generate-string config
                      :dumper-options {:flow-style :block})
; => "database:\n  host: localhost\n  port: 5432\n  credentials:\n    username: admin\n    password: secret\ncache:\n  servers:\n  - redis-1\n  - redis-2\n  - redis-3\n"

;; Flow style - fully collapsed (most compact)
(yaml/generate-string config
                      :dumper-options {:flow-style :flow})
; => "{database: {host: localhost, port: 5432, credentials: {username: admin, password: secret}}, cache: {servers: [redis-1, redis-2, redis-3]}}\n"
```

**When to use each:** - **Block** - Config files, documentation, human
readability - **Flow** - Logs, compact storage, single-line output -
**Auto** - Let SnakeYAML decide (uses flow for small structures)

### Workflow 5: Custom Indentation

Control indentation depth for nested structures:

``` clojure
(def nested
  {:server {:http {:port 8080 :timeout 30}
            :grpc {:port 9090 :timeout 60}}})

;; Default: 2-space indent
(yaml/generate-string nested :dumper-options {:flow-style :block})
; => "server:\n  http: {port: 8080, timeout: 30}\n  grpc: {port: 9090, timeout: 60}\n"

;; Custom: 4-space indent
(yaml/generate-string nested
                      :dumper-options {:flow-style :block :indent 4})
; => "server:\n    http: {port: 8080, timeout: 30}\n    grpc: {port: 9090, timeout: 60}\n"

;; With indicator indent (for lists)
(yaml/generate-string
  {:items ["a" "b" "c"]}
  :dumper-options {:flow-style :block
                   :indent 4
                   :indicator-indent 2})
; => "items:\n    -   a\n    -   b\n    -   c\n"
```

### Workflow 6: Position Tracking (Error Reporting)

Track source positions for better error messages:

``` clojure
;; Parse with position tracking
(def marked (yaml/parse-string
              "name: Alice\nage: thirty\nlocation: NYC"
              :mark true))

;; Check if marked
(yaml/marked? marked)
; => true

;; Access position data
marked
; => {:start {:line 0, :index 0, :column 0},
;     :end {:line 2, :index 31, :column 13},
;     :unmark {:name "Alice", :age "thirty", :location "NYC"}}

;; Extract actual data
(yaml/unmark marked)
; => {:name "Alice", :age "thirty", :location "NYC"}

;; Position data helps report errors
(defn validate-age [marked-data]
  (let [data (yaml/unmark marked-data)]
    (when-not (integer? (:age data))
      (let [age-mark (get-in marked-data [:unmark :age :start])]
        (throw (ex-info
                 (str "Invalid age at line " (:line age-mark)
                      ", column " (:column age-mark))
                 {:line (:line age-mark)
                  :column (:column age-mark)
                  :value (:age data)}))))))
```

**Use position tracking when:** - Building config validators - Providing
user-friendly error messages - Creating YAML editors/linters - Debugging
complex YAML files

### Workflow 7: Safety Options

Protect against untrusted YAML input:

``` clojure
;; Limit nesting depth (prevent stack overflow)
(def deep-yaml (apply str "a:\n" (repeat 100 "  b:\n")))

(try
  (yaml/parse-string deep-yaml :nesting-depth-limit 50)
  (catch Exception e
    (.getMessage e)))
; => "Nesting depth exceeded"

;; Prevent duplicate keys
(try
  (yaml/parse-string "name: Alice\nname: Bob" :allow-duplicate-keys false)
  (catch Exception e
    (.getMessage e)))
; => "found duplicate key name"

;; Limit document size (prevent DoS)
(yaml/parse-string large-yaml :code-point-limit 1000000)

;; Limit aliases (prevent billion laughs attack)
(yaml/parse-string yaml-with-aliases :max-aliases-for-collections 50)

;; NEVER use :unsafe true with untrusted input
;; :unsafe true allows arbitrary Java object instantiation
(yaml/parse-string trusted-yaml :unsafe false)  ; Always use false!
```

**Security best practices:** - Always use `:unsafe false` (the
default) - Set `:nesting-depth-limit` for untrusted input - Use
`:allow-duplicate-keys false` for strict validation - Set
`:code-point-limit` to prevent large documents - Validate data after
parsing

## When to Use clj-yaml

**Use clj-yaml when:** - Working with configuration files (app config,
CI/CD, Kubernetes) - Human readability matters more than parsing speed -
Multi-document YAML files needed - YAML is the standard in your
ecosystem (DevOps, k8s) - Need comments in data files (YAML supports,
JSON doesn't)

**Use Cheshire/JSON when:** - Performance is critical (JSON is faster to
parse) - Interoperating with web APIs (most use JSON) - Simpler data
structures - No need for human editing

**Use EDN when:** - Communicating between Clojure systems - Want to
preserve Clojure semantics exactly - Need rich data types (sets,
symbols, tagged literals)

## Best Practices

**Do:** - Use block style for config files
(`:dumper-options {:flow-style :block}`) - Set safety limits for
untrusted input (`:nesting-depth-limit`, `:code-point-limit`) - Use
`:allow-duplicate-keys false` for strict validation - Transform keys
consistently (use `:key-fn` for custom logic) - Use streams for large
files (`parse-stream`/`generate-stream`) - Validate data after parsing
(YAML is permissive) - Use `:load-all true` for multi-document files

``` clojure
;; Good: readable config file
(yaml/generate-string config :dumper-options {:flow-style :block})

;; Good: safe parsing of untrusted input
(yaml/parse-string untrusted-yaml
                   :unsafe false
                   :nesting-depth-limit 50
                   :allow-duplicate-keys false)

;; Good: stream large files
(with-open [r (io/reader "large.yaml")]
  (yaml/parse-stream r))
```

**Don't:** - Use `:unsafe true` with untrusted input (security risk!) -
Parse huge YAML into memory with `parse-string` (use `parse-stream`) -
Forget to use `:load-all true` for multi-document files - Mix string and
keyword keys in same codebase - Ignore validation (YAML allows any
structure) - Use flow style for human-edited config files

``` clojure
;; Bad: unsafe parsing
(yaml/parse-string untrusted-input :unsafe true)  ; DANGEROUS!

;; Bad: reading huge file into memory
(yaml/parse-string (slurp "huge.yaml"))  ; OOM risk

;; Bad: forgetting multi-document
(yaml/parse-string multi-doc-yaml)  ; Only gets first doc!

;; Good: load all documents
(yaml/parse-string multi-doc-yaml :load-all true)
```

## Common Issues

### Issue: Only first document parsed

``` clojure
(def yaml "---\nname: Alice\n---\nname: Bob")
(yaml/parse-string yaml)
; => Error: expected a single document but found another
```

**Solution**: Use `:load-all true`:

``` clojure
(yaml/parse-string yaml :load-all true)
; => ({:name "Alice"} {:name "Bob"})
```

### Issue: Keywords not legal Clojure keywords

``` clojure
(yaml/parse-string "123: value\nfirst-name: Alice")
; => {:123 "value", :first-name "Alice"}  ; :123 is invalid keyword!
```

**Solution**: Use `:keywords false` or custom `:key-fn`:

``` clojure
;; String keys
(yaml/parse-string "123: value" :keywords false)
; => {"123" "value"}

;; Custom validation
(yaml/parse-string yaml
  :key-fn (fn [{:keys [key]}]
            (if (re-matches #"^\d+$" key)
              key  ; Keep numeric keys as strings
              (keyword key))))
```

### Issue: Duplicate keys silently overwrite

``` clojure
(yaml/parse-string "name: Alice\nage: 30\nname: Bob")
; => {:name "Bob", :age 30}  ; Alice lost!
```

**Solution**: Use `:allow-duplicate-keys false` for strict validation:

``` clojure
(yaml/parse-string "name: Alice\nname: Bob" :allow-duplicate-keys false)
; => Exception: found duplicate key name
```

### Issue: Deep nesting causes stack overflow

``` clojure
(def deep (apply str (repeat 1000 "a:\n  ")))
(yaml/parse-string deep)
; => StackOverflowError (maybe)
```

**Solution**: Set `:nesting-depth-limit`:

``` clojure
(yaml/parse-string deep :nesting-depth-limit 50)
; => Throws if limit exceeded
```

### Issue: Flow style not applied consistently

``` clojure
(yaml/generate-string {:a 1 :b {:c 2}} :dumper-options {:flow-style :block})
; => "a: 1\nb: {c: 2}\n"  ; Why is b: {c: 2} inline?
```

**Explanation**: SnakeYAML uses heuristics for "auto" style on nested
structures. For fully block style, nested maps need multiple keys or
explicit configuration.

**Workaround**: Accept mixed styles (this is normal) or generate YAML
differently.

### Issue: OutOfMemoryError with large files

``` clojure
(yaml/parse-string (slurp "huge.yaml"))
; => OutOfMemoryError
```

**Solution**: Use streams:

``` clojure
(with-open [r (io/reader "huge.yaml")]
  (yaml/parse-stream r))
```

### Issue: Custom types don't serialize

``` clojure
(yaml/generate-string {:point (my.app.Point. 10 20)})
; => Error: Can't represent class my.app.Point
```

**Solution**: Convert custom types to standard types before encoding:

``` clojure
(defprotocol YAMLSerializable
  (to-yaml [this]))

(extend-type my.app.Point
  YAMLSerializable
  (to-yaml [p] {:x (.x p) :y (.y p)}))

(yaml/generate-string {:point (to-yaml (my.app.Point. 10 20))})
; => "point: {x: 10, y: 20}\n"
```

## Advanced Topics

### Unknown Tag Handling

Handle custom YAML tags:

``` clojure
;; Default: throws on unknown tags
(yaml/parse-string "!custom-tag value")
; => Exception: could not determine a constructor for the tag !custom-tag

;; Custom handler
(yaml/parse-string
  "data: !include config/database.yaml"
  :unknown-tag-fn (fn [{:keys [tag value]}]
                    (case tag
                      "!include" (yaml/parse-string (slurp value))
                      {:tag tag :value value})))
; => {:data {:host "localhost", :port 5432}}  ; Loaded from file
```

### Complex Indentation Control

Fine-tune indentation for specific needs:

``` clojure
(yaml/generate-string
  {:steps ["checkout" "build" "test" "deploy"]}
  :dumper-options {:flow-style :block
                   :indent 2               ; Base indent
                   :indicator-indent 0     ; Space before '-'
                   :indent-with-indicator false})
; => "steps:\n- checkout\n- build\n- test\n- deploy\n"

;; With indicator indent
(yaml/generate-string
  {:steps ["checkout" "build" "test" "deploy"]}
  :dumper-options {:flow-style :block
                   :indent 4
                   :indicator-indent 2
                   :indent-with-indicator true})
; => "steps:\n    -   checkout\n    -   build\n    -   test\n    -   deploy\n"
```

### Combining YAML with Schema Validation

Validate YAML structure after parsing:

``` clojure
(require '[malli.core :as m])

(def config-schema
  [:map
   [:database [:map
               [:host string?]
               [:port [:int {:min 1 :max 65535}]]]]
   [:server [:map
             [:port [:int {:min 1 :max 65535}]]
             [:workers pos-int?]]]])

(defn load-validated-config [path]
  (let [config (yaml/parse-string (slurp path))]
    (if (m/validate config-schema config)
      config
      (throw (ex-info "Invalid config"
                      {:errors (m/explain config-schema config)})))))
```

## Related Libraries

-   **Cheshire** - Fast JSON library (similar API)
-   **clojure.data.json** - Official Clojure JSON library
-   **aero** - Configuration library with environment-based loading
-   **cprop** - Environment-based config management
-   **environ** - Environment variable configuration

## Resources

-   GitHub: https://github.com/clj-commons/clj-yaml
-   User Guide:
    https://github.com/clj-commons/clj-yaml/blob/master/doc/01-user-guide.adoc
-   API Docs: https://cljdoc.org/d/clj-commons/clj-yaml
-   YAML Spec: https://yaml.org/spec/1.2/spec.html
-   SnakeYAML: https://bitbucket.org/snakeyaml/snakeyaml

## Summary

clj-yaml is the standard YAML library for Clojure:

1.  **Human-readable format** - Better than JSON for configs
2.  **Multi-document support** - Parse multiple YAML docs in one file
3.  **Custom key transformation** - `:key-fn` for camelCase/kebab-case
4.  **Flow style control** - Block (readable) or flow (compact)
    formatting
5.  **Position tracking** - Error reporting with line/column numbers
6.  **Safety options** - Protect against malicious YAML
7.  **Stream processing** - Handle large files efficiently

**Most common patterns:**

``` clojure
;; Parse config file (keyword keys)
(with-open [r (io/reader "config.yaml")]
  (yaml/parse-stream r))

;; Generate readable YAML
(yaml/generate-string data :dumper-options {:flow-style :block})

;; Parse multi-document YAML
(yaml/parse-string yaml-str :load-all true)

;; Safe parsing of untrusted input
(yaml/parse-string untrusted
                   :unsafe false
                   :nesting-depth-limit 50
                   :allow-duplicate-keys false)

;; Custom key transformation
(yaml/parse-string yaml
                   :key-fn (fn [{:keys [key]}]
                             (keyword (normalize-key key))))
```

Perfect for configuration files, CI/CD pipelines, Kubernetes manifests,
and any system where human readability and editability matter.

# clj-commons/pretty

Library for formatted output with ANSI colors, pretty exceptions, binary
dumps, tables, and code annotations.

## Quick Start

Pretty provides several independent formatting capabilities. Each can be
used standalone:

``` clojure
(require '[clj-commons.ansi :as ansi]
         '[clj-commons.format.exceptions :as exceptions]
         '[clj-commons.format.binary :as binary]
         '[clj-commons.format.table :as table])

;; Colored output
(ansi/pout [:bold.red "ERROR:"] " Something went wrong")

;; Pretty exceptions
(try
  (throw (ex-info "Failed" {:user-id 123}))
  (catch Exception e
    (exceptions/print-exception e)))

;; Binary dumps
(binary/print-binary (.getBytes "Hello"))

;; Tables
(table/print-table [:id :name] [{:id 1 :name "Alice"}])
```

## Core Concepts

### ANSI Color Support

Pretty automatically detects if color output is appropriate: - Enabled
in REPLs (nREPL, Cursive, `clj`) - Disabled if `NO_COLOR` environment
variable is set - Can be controlled via `clj-commons.ansi.enabled`
system property

### Composed Strings

Most Pretty functions work with "composed strings" - Hiccup-like data
structures that include formatting:

``` clojure
[:red "error"]                          ; Simple colored text
[:bold.yellow "Warning"]                ; Multiple font characteristics
[:red "Error: " [:bold "critical"]]     ; Nested formatting
```

## Common Workflows

### Workflow 1: Colored Console Output

Use `ansi/compose` to build formatted strings, `ansi/pout` and
`ansi/perr` to print them:

``` clojure
(require '[clj-commons.ansi :as ansi])

;; Print to stdout
(ansi/pout [:green.bold "✓"] " Tests passed")

;; Print to stderr
(ansi/perr [:red.bold "✗"] " Tests failed")

;; Build without printing
(def message (ansi/compose [:yellow "Warning: " [:bold "check input"]]))
```

**Font characteristics** (combine with periods): - Colors: `red`,
`green`, `yellow`, `blue`, `magenta`, `cyan`, `white`, `black` - Bright
colors: `bright-red`, `bright-green`, etc. - Background: `red-bg`,
`green-bg`, `bright-blue-bg`, etc. - Styles: `bold`, `faint`, `italic`,
`underlined`, `inverse`, `crossed` - Extended colors: `color-500` (RGB
5,0,0), `grey-0` through `grey-23`

``` clojure
;; Complex formatting
(ansi/pout [:bold.bright-white.red-bg "CRITICAL"]
           [:red " System overload at "
            [:bold.yellow (java.time.LocalDateTime/now)]])
```

### Workflow 2: Pretty Exception Formatting

Format exceptions with readable stack traces, property display, and
duplicate frame detection:

``` clojure
(require '[clj-commons.format.exceptions :as exceptions])

;; Basic exception formatting
(try
  (/ 1 0)
  (catch Exception e
    (exceptions/print-exception e)))

;; Format with options
(try
  (throw (ex-info "Database error"
                  {:query "SELECT * FROM users"
                   :connection-id 42}))
  (catch Exception e
    (exceptions/print-exception e
      {:frame-limit 10        ; Limit stack frames shown
       :properties true       ; Show exception properties (default)
       :traditional false}))) ; Modern ordering (default)
```

**Key features:** - Stack frames in chronological order (shallow to
deep) - Clojure function names demangled - File names and line numbers
highlighted - Exception properties pretty-printed - Repeated frames
collapsed

**Customizing frame filtering:**

``` clojure
;; Set application namespaces for highlighting
(alter-var-root #'exceptions/*app-frame-names*
                (constantly #{"myapp" "mycompany"}))

;; Define custom frame filter
(defn my-filter [frame]
  (cond
    (re-find #"test" (:name frame)) :hide
    (= "clojure.core" (:package frame)) :omit
    :else :show))

(exceptions/print-exception e {:filter my-filter})
```

### Workflow 3: Binary Data Visualization

Display byte sequences with color-coded hex dumps and optional ASCII
view:

``` clojure
(require '[clj-commons.format.binary :as binary])

;; Basic hex dump
(def data (.getBytes "Choose immutability"))
(binary/print-binary data)
; 0000: 43 68 6F 6F 73 65 20 69 6D 6D 75 74 61 62 69 6C
; 0010: 69 74 79

;; With ASCII sidebar (16 bytes per line)
(binary/print-binary data {:ascii true})
; 0000: 43 68 6F 6F 73 65 20 69 6D 6D 75 74 61 62 69 6C │Choose immutabil│
; 0010: 69 74 79                                        │ity             │

;; Custom line width (default 32, or 16 with :ascii)
(binary/print-binary data {:line-bytes 8})

;; Compare two byte sequences
(def expected (.getBytes "Hello World"))
(def actual (.getBytes "Hello Clojure"))
(binary/print-binary-delta expected actual)
; Differences highlighted in green (expected) and red (actual)
```

**Byte color coding:** - ASCII printable: cyan - Whitespace: green -
Control characters: red - Extended ASCII: yellow

### Workflow 4: Table Formatting

Print data as formatted tables with borders, alignment, and custom
styling:

``` clojure
(require '[clj-commons.format.table :as table])

(def users
  [{:id 1 :name "Alice" :role :admin}
   {:id 2 :name "Bob" :role :user}
   {:id 3 :name "Charlie" :role :user}])

;; Simple table
(table/print-table [:id :name :role] users)
; ┌──┬───────┬─────┐
; │Id│  Name │Role │
; ├──┼───────┼─────┤
; │ 1│  Alice│:admin│
; │ 2│    Bob│:user │
; │ 3│Charlie│:user │
; └──┴───────┴─────┘

;; Custom column configuration
(table/print-table
  [{:key :id :title "ID" :align :right}
   {:key :name :title "User Name" :width 15}
   {:key :role
    :title "Role"
    :formatter #(if (= :admin %) "Administrator" "User")
    :decorator (fn [idx val] (when (= :admin val) :bold.green))}]
  users)

;; Different table styles
(table/print-table
  {:columns [:id :name :role]
   :style table/skinny-style}
  users)
; ID | Name    | Role
; ---+---------+------
;  1 | Alice   | :admin
;  2 | Bob     | :user
;  3 | Charlie | :user

(table/print-table
  {:columns [:id :name :role]
   :style table/minimal-style}
  users)
; ID  Name     Role
;  1  Alice    :admin
;  2  Bob      :user
;  3  Charlie  :user
```

**Column options:** - `:key` - Keyword or function to extract value
(required) - `:title` - Column header (defaults to capitalized key) -
`:width` - Fixed width or auto-calculated - `:align` - `:left`, `:right`
(default), or `:center` - `:title-align` - Alignment for title (default
`:center`) - `:formatter` - Function to format cell value -
`:decorator` - Function returning font declaration for cell

**Table options:** - `:columns` - Vector of columns - `:style` -
`default-style`, `skinny-style`, or `minimal-style` - `:row-decorator` -
Function to style entire rows - `:row-annotator` - Function to add notes
after rows

### Workflow 5: Code Annotations

Annotate source code with error markers and messages:

``` clojure
(require '[clj-commons.pretty.annotations :as ann]
         '[clj-commons.ansi :as ansi])

;; Annotate a single line
(def source "SELECT DATE, AMT FROM PAYMENTS")
(ansi/perr source)
(run! ansi/perr
  (ann/callouts [{:offset 7
                  :length 4
                  :message "Invalid column name"}]))
; SELECT DATE, AMT FROM PAYMENTS
;        ▲▲▲▲
;        │
;        └╴ Invalid column name

;; Multiple annotations on one line
(run! ansi/perr
  (ann/callouts [{:offset 7 :length 4 :message "Invalid column"}
                 {:offset 17 :length 8 :message "Unknown table"}]))
; SELECT DATE, AMT FROM PAYMENTS
;        ▲▲▲▲         ▲▲▲▲▲▲▲▲
;        │            │
;        │            └╴ Unknown table
;        └╴ Invalid column

;; Annotate multiple lines
(def lines
  [{:line "SELECT DATE, AMT"
    :annotations [{:offset 7 :length 4 :message "Invalid column"}]}
   {:line "FROM PAYMENTS WHERE AMT > 10000"
    :annotations [{:offset 13 :length 5 :message "Unknown keyword"}]}])

(run! ansi/perr (ann/annotate-lines lines))
; 1: SELECT DATE, AMT
;           ▲▲▲▲
;           │
;           └╴ Invalid column
; 2: FROM PAYMENTS WHERE AMT > 10000
;                  ▲▲▲▲▲
;                  │
;                  └╴ Unknown keyword

;; Custom styling
(run! ansi/perr
  (ann/callouts
    {:font :red.bold
     :marker "^"
     :spacing :minimal}
    [{:offset 7 :length 4 :message "Error here"}]))
```

**Annotation options:** - `:offset` - Column position (0-based,
required) - `:length` - Characters to mark (default 1) - `:message` -
Error message (composed string) - `:font` - Override style font for this
annotation - `:marker` - Override marker character(s)

**Style options:** - `:font` - Default font (default `:yellow`) -
`:spacing` - `:tall`, `:compact` (default), or `:minimal` - `:marker` -
Marker string or function (default "▲") - `:bar` - Vertical bar
character (default "│") - `:nib` - Connection before message (default
"└╴")

### Workflow 6: Enable Pretty Exceptions in REPL

Install pretty exception printing for your development environment:

``` clojure
;; In user.clj or at REPL startup
(require '[clj-commons.pretty.repl :as pretty-repl])
(pretty-repl/install-pretty-exceptions)

;; Now all REPL exceptions use pretty formatting
(/ 1 0)
; Pretty formatted ArithmeticException output
```

**Project integration:**

For Leiningen (`~/.lein/profiles.d/debug.clj`):

``` clojure
{:dependencies [[org.clj-commons/pretty "3.6.7"]]
 :injections [(require '[clj-commons.pretty.repl :as repl])
              (repl/install-pretty-exceptions)]}
```

For deps.edn (`:debug` alias):

``` clojure
{:aliases
 {:debug
  {:extra-deps {org.clj-commons/pretty {:mvn/version "3.6.7"}}
   :exec-fn clj-commons.pretty.repl/main}}}
```

For nREPL middleware:

``` clojure
;; Add to .nrepl.edn or nrepl config
{:middleware [clj-commons.pretty.nrepl/wrap-pretty]}
```

## When to Use Each Feature

**Use ANSI colors when:** - Creating CLI tools with colored output -
Highlighting important information in logs - Building developer tools
and REPLs - Formatting success/error/warning messages

**Use exception formatting when:** - Debugging complex stack traces -
Building error reporting tools - Creating developer-friendly error
messages - Need to understand nested exceptions

**Use binary formatting when:** - Debugging binary protocols - Comparing
byte sequences - Analyzing file formats - Inspecting serialized data

**Use table formatting when:** - Displaying query results - Showing
configuration data - Comparing multiple items - Creating CLI reports

**Use code annotations when:** - Building error reporters for parsers -
Highlighting syntax errors - Creating educational tools - Showing code
issues in tooling

## Best Practices

**Do:** - Check if color is enabled before complex formatting:
`(ansi/when-color-enabled ...)` - Use composed strings for flexibility -
Set `*app-frame-names*` to highlight your code in stack traces - Provide
custom exception dispatch for complex types - Use appropriate table
styles for your use case - Keep annotation messages concise (no line
breaks)

**Don't:** - Generate ANSI codes manually - use `compose` - Mix Pretty's
exception formatting with manual `.printStackTrace` - Forget that
composed strings need `compose` to become actual strings - Create deeply
nested font definitions - keep formatting simple - Use `:ascii` mode for
binary output wider than 16 bytes per line - Overlap annotation ranges
on the same line

## Common Issues

### "ANSI codes appear in output"

Colors disabled but codes still showing:

``` clojure
;; Check if colors are enabled
ansi/*color-enabled*  ; => false

;; Colors are explicitly disabled
;; Either: NO_COLOR env var is set
;; Or: clj-commons.ansi.enabled system property is "false"
;; Or: No console/REPL detected

;; Force enable (for testing)
(alter-var-root #'ansi/*color-enabled* (constantly true))
```

### "Stack trace still looks like Java output"

Pretty exceptions not installed:

``` clojure
;; Install pretty exceptions
(require '[clj-commons.pretty.repl :as pretty-repl])
(pretty-repl/install-pretty-exceptions)

;; Or check if already installed
(pretty-repl/install-pretty-exceptions)  ; Safe to call multiple times
```

### "Binary output shows wrong width"

Line width calculation doesn't account for tabs/ANSI codes:

``` clojure
;; Specify explicit line width
(binary/print-binary data {:line-bytes 16})

;; For ASCII mode, always use 16 bytes per line
(binary/print-binary data {:ascii true :line-bytes 16})
```

### "Table columns too wide/narrow"

Width auto-calculated from data:

``` clojure
;; Specify explicit width
(table/print-table
  [{:key :name :width 20}
   {:key :description :width 50}]
  data)

;; Or use formatters to control content
(table/print-table
  [{:key :name}
   {:key :description
    :formatter #(subs % 0 (min 50 (count %)))}]
  data)
```

### "Annotations overlap"

Multiple annotations with overlapping ranges:

``` clojure
;; Bad: overlapping ranges
[{:offset 5 :length 10}
 {:offset 8 :length 5}]  ; Overlaps with first

;; Good: non-overlapping
[{:offset 5 :length 3}
 {:offset 10 :length 5}]

;; Annotations automatically sorted by offset
;; But overlaps still cause visual issues
```

## Advanced Topics

### Custom Exception Dispatch

Control how specific types appear in exception output:

``` clojure
(import 'com.stuartsierra.component.SystemMap)

(defmethod exceptions/exception-dispatch SystemMap
  [system-map]
  (print "#<SystemMap>"))

;; Now SystemMap instances show as "#<SystemMap>" instead of full structure
```

### Custom Table Decorators

Add visual styling to table rows and cells:

``` clojure
(table/print-table
  {:columns [:status :message]
   :row-decorator (fn [idx row]
                    (case (:status row)
                      :error :red
                      :warning :yellow
                      :success :green
                      nil))}
  [{:status :error :message "Failed"}
   {:status :success :message "OK"}])
```

### Custom Annotation Markers

Create custom marker functions:

``` clojure
(defn wave-marker [length]
  (apply str (repeat length "~")))

(run! ansi/perr
  (ann/callouts
    {:marker wave-marker}
    [{:offset 5 :length 10 :message "Issue here"}]))
; Some text here with a problem
;      ~~~~~~~~~~
;      │
;      └╴ Issue here
```

### Parsing Exception Text

Convert text-based exception output back to Pretty's format:

``` clojure
;; Useful for processing exception logs
(def exception-text
  "java.lang.ArithmeticException: Divide by zero
    at clojure.lang.Numbers.divide(Numbers.java:188)
    at user$eval123.invokeStatic(REPL:1)")

(def parsed (exceptions/parse-exception exception-text {}))
(exceptions/print-exception* parsed {})
```

## Performance Considerations

-   **ANSI composition** is fast; overhead is minimal
-   **Exception formatting** processes entire stack trace; use
    `:frame-limit` for very deep stacks
-   **Binary formatting** with `:ascii` doubles memory usage (hex +
    ASCII)
-   **Table formatting** calculates widths from all data; slow for huge
    datasets
-   **Annotations** are fast; multiple annotations per line add minimal
    overhead

For large datasets or performance-critical paths, consider: - Format
once, cache the result - Use `:frame-limit` to reduce exception output -
Limit table row count for display - Use streaming approaches for binary
data

## Related Libraries

-   `clojure.pprint` - Basic pretty printing (Pretty extends this)
-   `puget` - Alternative pretty printer with color support
-   `fipp` - Fast pretty printer
-   `bling` - Terminal UI components and coloring

## Resources

-   [GitHub Repository](https://github.com/clj-commons/pretty)
-   [API Documentation](https://cljdoc.org/d/org.clj-commons/pretty)
-   [CLJ Commons](https://clj-commons.org/)

## Summary

`clj-commons/pretty` provides five independent formatting capabilities:

1.  **ANSI colors** (`clj-commons.ansi`) - Colored terminal output
2.  **Exception formatting** (`clj-commons.format.exceptions`) -
    Readable stack traces
3.  **Binary visualization** (`clj-commons.format.binary`) - Hex dumps
    and deltas
4.  **Table formatting** (`clj-commons.format.table`) - Pretty tabular
    output
5.  **Code annotations** (`clj-commons.pretty.annotations`) - Error
    markers on source

Each can be used independently or combined for comprehensive formatted
output.

# Editscript

A Clojure library for computing and applying diffs to data structures.

## Overview

Editscript provides efficient algorithms for computing the minimal set
of changes (diff) between two data structures, and applying those
changes (patch) to transform one structure into another.

## Core Concepts

**Diff**: Compute differences between data structures.

``` clojure
(require '[editscript.core :as editscript])

(def v1 {:name "Alice" :age 30 :city "NYC"})
(def v2 {:name "Alice" :age 31 :city "San Francisco"})

; Compute diff
(editscript/diff v1 v2)
; => [[[:age] 30 31]
;     [[:city] "NYC" "San Francisco"]]
```

**Patch**: Apply diff to transform data.

``` clojure
(def changes [[[:age] 30 31]])
(editscript/patch v1 changes)
; => {:name "Alice" :age 31 :city "NYC"}
```

## Key Features

-   Efficient diff algorithms
-   Minimal change sets
-   Support for maps, vectors, sets
-   Patch application
-   Change summary statistics
-   Type-aware operations

## When to Use

-   Detecting changes in complex data structures
-   Synchronizing distributed systems
-   Change tracking and audit logs
-   Data structure comparison

## When NOT to Use

-   Simple value comparison (use =)
-   High-frequency diff computation (performance sensitive)

## Common Patterns

``` clojure
(require '[editscript.core :as editscript])

; Track changes to a record
(defn update-user-with-tracking [user-id old-user new-user]
  (let [changes (editscript/diff old-user new-user)]
    {:user-id user-id
     :before old-user
     :after new-user
     :changes changes
     :timestamp (java.time.Instant/now)}))

; Example
(def old-state {:users [{:id 1 :name "Alice" :email "alice@old.com"}
                        {:id 2 :name "Bob" :email "bob@example.com"}]})

(def new-state {:users [{:id 1 :name "Alice" :email "alice@new.com"}
                        {:id 2 :name "Bob" :email "bob@example.com"}]})

(editscript/diff old-state new-state)
; => [[[0 :email] "alice@old.com" "alice@new.com"]]
```

## Related Libraries

-   clojure.data/diff - Built-in diff function
-   metosin/malli - Data validation

## Resources

-   Official Documentation: https://github.com/juji-io/editscript
-   API Documentation: https://cljdoc.org/d/juji/editscript

## Notes

This project uses Editscript for computing and tracking changes to data
structures.

# Malli Data Validation

## Quick Start

Malli validates data against schemas. Schemas are just Clojure data
structures:

``` clojure
(require '[malli.core :as m])

;; Define a schema
(def user-schema
  [:map
   [:name string?]
   [:email string?]
   [:age int?]])

;; Validate data
(m/validate user-schema {:name "Alice" :email "alice@example.com" :age 30})
;; => true

(m/validate user-schema {:name "Bob" :age "thirty"})
;; => false

;; Get detailed errors
(m/explain user-schema {:name "Bob" :age "thirty"})
;; => {:errors [{:path [:email] :type :malli.core/missing-key}
;;              {:path [:age] :schema int? :value "thirty"}]}
```

**Key benefits:** - **Data-driven** - Schemas are Clojure data, not
special objects - **Composable** - Build complex schemas from simple
ones - **Detailed errors** - Know exactly what's wrong - **Fast** -
Performance optimized with schema compilation - **Extensible** - Add
custom validators easily

## Core Concepts

### Schemas as Data

Schemas are vectors describing data structure:

``` clojure
;; Simple predicates
int?        ; Any integer
string?     ; Any string
keyword?    ; Any keyword

;; Type schemas
:int        ; Integer type
:string     ; String type
:keyword    ; Keyword type

;; Constrained values
[:int {:min 0 :max 100}]           ; Integer between 0-100
[:string {:min 1 :max 50}]         ; String length 1-50
[:enum "red" "green" "blue"]       ; One of these values
```

### Validation vs Coercion

``` clojure
;; Validation - Check if data matches
(m/validate [:int] 42)      ;; => true
(m/validate [:int] "42")    ;; => false

;; Coercion - Transform data to match (requires malli.transform)
(require '[malli.transform :as mt])

(m/decode [:int] "42" mt/string-transformer)
;; => 42

(m/decode [:int] "invalid" mt/string-transformer)
;; => "invalid" (can't coerce, returns original)
```

### Schema Registry

Register and reuse schemas:

``` clojure
(require '[malli.core :as m])

(def registry
  {:user/id :int
   :user/email [:string {:min 3}]
   :user/user [:map
               [:id :user/id]
               [:email :user/email]]})

(def User [:schema {:registry registry} :user/user])

(m/validate User {:id 1 :email "alice@example.com"})
;; => true
```

## Common Workflows

### Workflow 1: API Request Validation

``` clojure
(require '[malli.core :as m])

(def create-user-request
  [:map
   [:name [:string {:min 1 :max 100}]]
   [:email [:re #".+@.+\..+"]]
   [:age [:int {:min 0 :max 150}]]
   [:role [:enum "user" "admin" "guest"]]])

(defn validate-request [data]
  (if (m/validate create-user-request data)
    {:success true :data data}
    {:success false
     :errors (m/explain create-user-request data)}))

;; Valid request
(validate-request {:name "Alice"
                   :email "alice@example.com"
                   :age 30
                   :role "user"})
;; => {:success true :data {...}}

;; Invalid request
(validate-request {:name ""
                   :email "not-an-email"
                   :age 200
                   :role "superuser"})
;; => {:success false :errors {...}}
```

### Workflow 2: Composing Schemas

``` clojure
(def address-schema
  [:map
   [:street string?]
   [:city string?]
   [:zip [:string {:pattern #"\d{5}"}]]])

(def person-schema
  [:map
   [:name string?]
   [:age int?]
   [:address address-schema]])  ; Compose schemas

(m/validate person-schema
  {:name "Bob"
   :age 25
   :address {:street "123 Main St"
             :city "Boston"
             :zip "02101"}})
;; => true
```

### Workflow 3: Optional and Default Values

``` clojure
(def user-with-defaults
  [:map
   [:name string?]
   [:email string?]
   [:age [:int {:optional true}]]           ; Optional field
   [:role {:optional true} [:enum "user" "admin"]]  ; Optional with choices
   [:active {:optional true :default true} boolean?]])  ; Optional with default

;; Valid without optional fields
(m/validate user-with-defaults {:name "Alice" :email "alice@example.com"})
;; => true

;; Optional fields can be present
(m/validate user-with-defaults
  {:name "Alice" :email "alice@example.com" :age 30 :role "admin"})
;; => true
```

### Workflow 4: Collections

``` clojure
;; Vector of specific type
(def user-ids [:vector :int])
(m/validate user-ids [1 2 3 4 5])  ;; => true
(m/validate user-ids [1 "2" 3])    ;; => false

;; Sequence of items (lazy)
(def lazy-ids [:sequential :int])
(m/validate lazy-ids (range 100))  ;; => true

;; Set of unique values
(def tags [:set :keyword])
(m/validate tags #{:clojure :malli :validation})  ;; => true

;; Map with specific value types
(def settings [:map-of :keyword :string])
(m/validate settings {:color "red" :theme "dark"})  ;; => true
```

### Workflow 5: Humanized Error Messages

``` clojure
(require '[malli.error :as me])

(def user-schema
  [:map
   [:name [:string {:min 3}]]
   [:age [:int {:min 0 :max 150}]]])

(def invalid-data {:name "Al" :age 200})

;; Get human-readable errors
(-> user-schema
    (m/explain invalid-data)
    (me/humanize))
;; => {:name ["should be at least 3 characters"]
;;     :age ["should be at most 150"]}
```

### Workflow 6: Coercion and Transformation

``` clojure
(require '[malli.transform :as mt])

(def coercible-schema
  [:map
   [:id :int]
   [:active :boolean]
   [:created :inst]])

;; Decode from string format (e.g., JSON)
(m/decode coercible-schema
  {:id "123"
   :active "true"
   :created "2024-01-01T00:00:00Z"}
  mt/string-transformer)
;; => {:id 123
;;     :active true
;;     :created #inst "2024-01-01T00:00:00.000-00:00"}

;; Encode to string format
(m/encode coercible-schema
  {:id 123
   :active true
   :created #inst "2024-01-01"}
  mt/string-transformer)
;; => {:id "123"
;;     :active "true"
;;     :created "2024-01-01T00:00:00Z"}
```

## When to Use Each Approach

**Use Malli when:** - Validating external data (API requests, user
input, config files) - You need detailed, structured error messages -
Schemas should be data (can be stored, transmitted, generated) -
Building forms with validation - Runtime type checking is needed - You
want to generate example data or docs from schemas

**Use clojure.spec when:** - You need generative testing (test.check
integration) - Working with existing spec-based libraries - Need
instrumentation for development - Prefer spec's conforming and unforming

**Use simple predicates when:** - Validation is trivial (`string?`,
`pos-int?`) - Performance is absolutely critical - No need for error
messages - Quick inline validation

**Don't use Malli when:** - Static type checking is required (use typed
Clojure or other language) - Validation is too simple to justify
overhead - You only need compile-time checks

## Best Practices

**Do:** - Define schemas as constants for reuse - Use descriptive keys
in maps - Provide human-readable error messages with `me/humanize` -
Test schemas with valid and invalid data - Use optional fields for
non-required data - Compose small schemas into larger ones - Use schema
registry for shared definitions - Add `:min` and `:max` constraints
where appropriate

**Don't:** - Recreate schemas inline (define once, reuse) - Make schemas
overly strict (allow flexibility where needed) - Ignore validation
errors (always check return values) - Skip testing edge cases - Use
validation for complex business logic (use functions) - Validate data
multiple times unnecessarily

## Common Issues

### Schema Doesn't Match Expected Structure

``` clojure
;; Wrong: closed map doesn't allow extra keys
(def strict-schema [:map [:name string?]])
(m/validate strict-schema {:name "Alice" :age 30})
;; => false (age not allowed)

;; Right: allow extra keys
(def open-schema [:map {:closed false} [:name string?]])
(m/validate open-schema {:name "Alice" :age 30})
;; => true
```

### Optional Fields Not Working

``` clojure
;; Wrong: field is required by default
(def schema [:map [:name string?] [:age int?]])
(m/validate schema {:name "Alice"})
;; => false (missing :age)

;; Right: mark as optional
(def schema [:map [:name string?] [:age {:optional true} int?]])
(m/validate schema {:name "Alice"})
;; => true
```

### Validation Too Slow

``` clojure
;; Wrong: validating in hot path without compilation
(defn process [data]
  (when (m/validate big-schema data)  ; Validates schema each time
    (do-work data)))

;; Right: compile schema once
(def validator (m/validator big-schema))

(defn process [data]
  (when (validator data)  ; Use compiled validator
    (do-work data)))
```

### Coercion Not Working

``` clojure
;; Problem: coercion requires transformer
(m/decode [:int] "42")  ; Doesn't work!

;; Solution: provide transformer
(require '[malli.transform :as mt])
(m/decode [:int] "42" mt/string-transformer)
;; => 42

;; Or create decoder once
(def decode-user (m/decoder user-schema mt/string-transformer))
(decode-user {:id "123" :name "Alice"})
```

## Advanced Topics

### Custom Validators

``` clojure
(def email-regex #".+@.+\..+")

(def Email
  (m/-simple-schema
    {:type :email
     :pred (fn [x] (and (string? x) (re-matches email-regex x)))
     :type-properties {:error/message "should be a valid email"}}))

(m/validate Email "alice@example.com")  ;; => true
(m/validate Email "invalid")            ;; => false
```

### Schema Generation

``` clojure
(require '[malli.generator :as mg])

;; Generate random valid data
(mg/generate user-schema)
;; => {:name "aB7x" :email "Cd@e.f" :age 42}

;; Generate multiple samples
(mg/sample user-schema {:size 3})
;; => [{:name "x" ...} {:name "yz" ...} {:name "abc" ...}]
```

### Schema Transformation

``` clojure
(require '[malli.util :as mu])

;; Merge schemas
(mu/merge
  [:map [:a int?]]
  [:map [:b string?]])
;; => [:map [:a int?] [:b string?]]

;; Make all fields optional
(mu/optional-keys user-schema)

;; Make all fields required
(mu/required-keys user-schema)
```

## Related Libraries

-   clojure.spec.alpha - Alternative validation approach
-   metosin/reitit - Uses Malli for route data validation
-   metosin/malli - Core library

## External Resources

-   [Official Documentation](https://github.com/metosin/malli)
-   [API Documentation](https://cljdoc.org/d/metosin/malli)
-   [Malli Tutorial](https://github.com/metosin/malli#tutorial)
-   [Comparison with
    spec](https://github.com/metosin/malli/blob/master/docs/comparisons.md)

## Summary

Malli provides data-driven schema validation for Clojure:

1.  **Schemas as data** - Easy to compose, inspect, and transform
2.  **Rich validation** - Predicates, constraints, collections, maps
3.  **Detailed errors** - Know exactly what's invalid
4.  **Coercion** - Transform data to match schemas
5.  **Extensible** - Add custom validators and transformers

Use Malli for validating external data, defining contracts, and ensuring
data integrity at runtime.

# μ/log (mulog)

μ/log is a micro-logging library that logs events as data, not text
messages. Designed for modern cloud-based distributed systems with
centralized log aggregation.

## Quick Start

``` clojure
;; Add dependency
{:deps {com.brunobonacci/mulog {:mvn/version "0.9.0"}}}

;; Require the namespace
(require '[com.brunobonacci.mulog :as μ])

;; Start a publisher (console for development)
(def publisher (μ/start-publisher! {:type :console :pretty? true}))

;; Log an event
(μ/log ::user-logged
  :user-id "12345"
  :remote-ip "1.2.3.4"
  :auth-method :password-login)
;; => nil

;; Event logged:
;; {:mulog/trace-id #mulog/flake "4VTF9QBbnef57vxVy-b4uKzh7dG7r7y4",
;;  :mulog/timestamp 1587500402972,
;;  :mulog/event-name :your-ns/user-logged,
;;  :mulog/namespace "your-ns",
;;  :user-id "12345",
;;  :remote-ip "1.2.3.4",
;;  :auth-method :password-login}

;; Stop publisher when done
(publisher)
```

**Key benefits:** - Extremely fast (under 300 nanoseconds per event) -
Logs events as data structures, not strings - Memory-bound with no
unbounded memory use - Asynchronous processing and rendering - Rich
publisher ecosystem - Built-in distributed tracing support

## Core Concepts

### Events as Data

μ/log treats logs as structured events with arbitrary key-value pairs:

``` clojure
;; Traditional logging (string-based)
;; (log/info "User 12345 logged in from 1.2.3.4")

;; μ/log (data-based)
(μ/log ::user-logged :user-id "12345" :remote-ip "1.2.3.4")
```

**Why this matters:** - No need to parse strings later - Easy to query,
filter, aggregate - Natural fit for tools like Elasticsearch - Rich
dimensional data for analysis

### Event Structure

All events automatically include: - `:mulog/trace-id` - Unique event
identifier (flake ID) - `:mulog/timestamp` - Millisecond-precision
timestamp - `:mulog/event-name` - The event name (namespaced keyword) -
`:mulog/namespace` - The namespace where event was logged

Plus any custom key-value pairs you add.

### Global Context

Global context adds properties to ALL subsequent events:

``` clojure
;; Set once at application startup
(μ/set-global-context!
  {:app-name "my-service"
   :version "1.2.3"
   :env "production"
   :host "server-01"})

;; Now all events include these properties
(μ/log ::order-created :order-id "ord-123")
;; Includes: :app-name, :version, :env, :host

;; Update global context
(μ/update-global-context! assoc :deploy-id "deploy-456")
```

**Best practice:** Set global context in your `-main` function with
application-wide properties.

### Local Context

Local context is thread-local and scoped to a block:

``` clojure
;; Context applies to all logs in scope
(μ/with-context {:request-id "req-789" :user-id "user-456"}
  (μ/log ::api-call :endpoint "/api/orders" :method "POST")
  (process-order)
  (μ/log ::api-response :status 200))

;; Both events include :request-id and :user-id

;; Context nests
(μ/with-context {:transaction-id "tx-098765"}
  (μ/with-context {:order-id "ord-123"}
    (μ/log ::item-processed :item-id "sku-456" :quantity 2)))
;; Event includes both :transaction-id and :order-id
```

Local context propagates through function calls in the same thread.

### Distributed Tracing with μ/trace

μ/trace automatically tracks operation duration and outcome:

``` clojure
;; Wrap operations to track duration and errors
(μ/trace ::database-query
  [:table "orders" :query-type "select"]
  (db/fetch-orders))

;; Automatically logs:
;; {:mulog/trace-id #mulog/flake "...",
;;  :mulog/event-name ::database-query,
;;  :mulog/timestamp 1587504242983,
;;  :mulog/duration 254402837,  ; nanoseconds
;;  :mulog/outcome :ok,          ; or :error
;;  :mulog/root-trace #mulog/flake "...",
;;  :mulog/parent-trace #mulog/flake "...",
;;  :table "orders",
;;  :query-type "select"}
```

**μ/trace features:** - Measures duration in nanoseconds - Tracks
`:outcome` (`:ok` or `:error`) - Links parent/child traces - Adds
exception details on errors - Supports result capture

## Common Workflows

### Workflow 1: Application Initialization

Set up μ/log at application startup:

``` clojure
(ns my-app.core
  (:require [com.brunobonacci.mulog :as μ]))

(defn -main [& args]
  ;; Set global context first
  (μ/set-global-context!
    {:app-name "my-service"
     :version (System/getProperty "app.version" "dev")
     :env (System/getenv "ENV")
     :host (.getHostName (java.net.InetAddress/getLocalHost))
     :pid (.pid (java.lang.ProcessHandle/current))})

  ;; Start publisher(s)
  (def publisher
    (μ/start-publisher!
      {:type :console :pretty? true}))

  ;; Log startup event
  (μ/log ::application-started :init-time 250)

  ;; Application logic
  (start-server)

  ;; Shutdown hook
  (.addShutdownHook
    (Runtime/getRuntime)
    (Thread. (fn []
               (μ/log ::application-stopping)
               (publisher)))))
```

### Workflow 2: HTTP Request Logging

Track requests with contextual information:

``` clojure
(require '[com.brunobonacci.mulog :as μ])

(defn wrap-logging [handler]
  (fn [request]
    (let [request-id (or (get-in request [:headers "x-request-id"])
                         (str (java.util.UUID/randomUUID)))]
      (μ/with-context
        {:request-id request-id
         :user-id (get-in request [:session :user-id])
         :remote-ip (:remote-addr request)}

        (μ/log ::http-request-start
          :method (:request-method request)
          :path (:uri request))

        (let [start (System/nanoTime)
              response (handler request)
              duration (- (System/nanoTime) start)]

          (μ/log ::http-request-complete
            :method (:request-method request)
            :path (:uri request)
            :status (:status response)
            :duration-ms (/ duration 1000000.0))

          response)))))

;; Or use μ/trace for automatic timing
(defn wrap-logging-trace [handler]
  (fn [request]
    (μ/with-context
      {:request-id (or (get-in request [:headers "x-request-id"])
                       (str (java.util.UUID/randomUUID)))
       :user-id (get-in request [:session :user-id])}

      (μ/trace ::http-request
        [:method (:request-method request)
         :path (:uri request)]
        (handler request)))))
```

### Workflow 3: Database Operation Tracing

Track database operations with automatic timing:

``` clojure
(require '[com.brunobonacci.mulog :as μ]
         '[next.jdbc :as jdbc])

(defn fetch-user [db user-id]
  (μ/trace ::database-query
    [:table "users"
     :operation "select"
     :user-id user-id]
    (jdbc/execute-one! db ["SELECT * FROM users WHERE id = ?" user-id])))

(defn create-order [db order-data]
  (μ/trace ::database-insert
    [:table "orders"
     :operation "insert"]
    (jdbc/execute-one! db
      ["INSERT INTO orders (user_id, total) VALUES (?, ?)"
       (:user-id order-data)
       (:total order-data)]
      {:return-keys true})))

;; With error handling
(defn safe-db-operation [db f]
  (try
    (μ/trace ::database-operation
      []
      (f db))
    (catch Exception e
      (μ/log ::database-error
        :exception e
        :error-type (class e)
        :message (.getMessage e))
      (throw e))))
```

### Workflow 4: Capturing Results from Traces

Capture specific fields from operation results:

``` clojure
;; Capture HTTP response status
(μ/trace ::http-call
  {:pairs [:url "https://api.example.com/users"]
   :capture (fn [response]
              {:http-status (:status response)
               :content-length (get-in response [:headers "content-length"])})}
  (http/get "https://api.example.com/users"))

;; Result includes :http-status and :content-length

;; Capture database query results
(μ/trace ::user-search
  {:pairs [:search-term "alice"]
   :capture (fn [results]
              {:result-count (count results)
               :has-results (pos? (count results))})}
  (db/search-users "alice"))
```

### Workflow 5: Nested Distributed Traces

Track operations across multiple service layers:

``` clojure
(defn process-order [order-data]
  (μ/trace ::process-order
    [:order-id (:order-id order-data)]

    ;; Nested trace: validate
    (μ/trace ::validate-order
      []
      (validate-order-data order-data))

    ;; Nested trace: check inventory
    (μ/trace ::check-inventory
      [:items (count (:items order-data))]
      (check-item-availability (:items order-data)))

    ;; Nested trace: payment
    (μ/trace ::process-payment
      [:amount (:total order-data)]
      (charge-payment (:payment order-data)))

    ;; Final result
    {:status :success :order-id (:order-id order-data)}))

;; Produces trace hierarchy:
;; ::process-order (parent)
;;   └── ::validate-order (child, shares parent-trace)
;;   └── ::check-inventory (child, shares parent-trace)
;;   └── ::process-payment (child, shares parent-trace)
```

### Workflow 6: Using Multiple Publishers

Send events to multiple destinations:

``` clojure
;; Multiple publishers via :multi type
(def publishers
  (μ/start-publisher!
    {:type :multi
     :publishers
     [{:type :console :pretty? true}
      {:type :simple-file :filename "/var/log/app/events.log"}
      {:type :elasticsearch
       :url "http://localhost:9200"
       :index-pattern "mulog-YYYY.MM.dd"}]}))

;; Or start separately
(def console-pub (μ/start-publisher! {:type :console}))
(def file-pub (μ/start-publisher! {:type :simple-file :filename "/tmp/events.log"}))

;; Stop all
(publishers) ; for multi
;; or
(console-pub)
(file-pub)
```

### Workflow 7: Error and Exception Logging

Properly log errors with context:

``` clojure
;; Basic error logging
(try
  (risky-operation)
  (catch Exception e
    (μ/log ::operation-failed
      :exception e
      :operation "risky-operation"
      :reason (.getMessage e))))

;; With μ/trace (automatic exception capture)
(μ/trace ::risky-operation
  [:operation-type "data-import"]
  (import-data source))
;; On exception:
;; - :mulog/outcome :error
;; - :exception <exception object>
;; - Original exception is re-thrown

;; Defensive logging before throwing
(defn validate-input [input]
  (when-not (valid? input)
    (μ/log ::validation-failed
      :input input
      :reason "invalid format")
    (throw (ex-info "Invalid input" {:input input}))))
```

## Publishers

μ/log supports many publishers for different backends:

**Development:** - `:console` - Print to stdout (development only) -
`:simple-file` - Write to file in EDN format

**Production:** - `:elasticsearch` - Send to Elasticsearch - `:kafka` -
Send to Kafka topics - `:kinesis` - Send to AWS Kinesis -
`:cloudwatch` - Send to AWS CloudWatch Logs - `:zipkin` - Send traces to
Zipkin - `:prometheus` - Expose metrics endpoint - `:slack` - Send
alerts to Slack - `:opentelemetry` - Send to OpenTelemetry collector

**Example configurations:**

``` clojure
;; Console (development)
{:type :console :pretty? true}

;; Elasticsearch
{:type :elasticsearch
 :url "http://localhost:9200"
 :index-pattern "mulog-YYYY.MM.dd"}

;; Kafka
{:type :kafka
 :kafka {:bootstrap.servers "localhost:9092"}
 :topic "mulog-events"}

;; CloudWatch
{:type :cloudwatch
 :log-group-name "/aws/lambda/my-function"
 :log-stream-name "2024/11/11/my-stream"}

;; Multi-publisher
{:type :multi
 :publishers
 [{:type :console}
  {:type :elasticsearch :url "http://es:9200"}]}
```

## When to Use Each Approach

**Use μ/log when:** - Logging discrete events (user actions, API calls,
errors) - You need rich contextual data - Events happen at a single
point in time - You want to query and aggregate logs

**Use μ/trace when:** - Tracking operation duration - Need automatic
error tracking - Building distributed traces - Want to measure
performance - Operations span time (database calls, HTTP requests)

**Use with-context when:** - You have request-scoped data (request ID,
user ID) - Processing spans multiple function calls - Need consistent
context across operations - Context applies to a logical unit of work

**Use set-global-context! when:** - Application-wide properties (app
name, version, environment) - Properties valid for entire process
lifetime - Information needed in every single event

## Best Practices

**DO:** - Use namespaced keywords for event names
(`:my-ns/user-logged`) - Log plain values, not opaque objects - Set
global context at application startup - Use `:exception` key for
exception objects - Add dimensional data (IDs, status codes, etc.) - Use
μ/trace for operations with duration - Track outcomes
(`:status :success`, `:status :failed`) - Log timestamps with
millisecond precision (automatic) - Use publishers appropriate for
environment (console for dev, Elasticsearch for prod)

**DON'T:** - Log deeply nested maps (hard to query) - Log mutable
objects (async rendering may see different state) - Construct log
messages as strings - Skip global context setup - Use println or other
text logging - Log passwords or sensitive data - Create unbounded log
volume

## Common Issues

### Issue: Events Not Appearing

**Problem:** Logged events but nothing appears.

``` clojure
(μ/log ::my-event :data "value")
;; Nothing shows up
```

**Solution:** Start a publisher first.

``` clojure
;; Start publisher BEFORE logging
(def publisher (μ/start-publisher! {:type :console}))

(μ/log ::my-event :data "value")
;; Now it appears
```

### Issue: Events Missing Context

**Problem:** Expected context properties don't appear in events.

``` clojure
(μ/set-global-context! {:app-name "my-app"})
(μ/log ::test)
;; :app-name not in event
```

**Solution:** Global context only applies to events logged AFTER it's
set.

``` clojure
;; Set context FIRST
(μ/set-global-context! {:app-name "my-app"})

;; Then log
(μ/log ::test)
;; Now includes :app-name
```

### Issue: Local Context Not Propagating

**Problem:** Context doesn't appear in nested function calls.

``` clojure
(defn inner-fn []
  (μ/log ::inner-event))

(μ/with-context {:request-id "123"}
  (inner-fn))
;; :request-id missing from ::inner-event
```

**Solution:** Local context only propagates in the same thread. If you
spawn threads, you need to explicitly transfer context.

``` clojure
;; Works (same thread)
(defn inner-fn []
  (μ/log ::inner-event))

(μ/with-context {:request-id "123"}
  (inner-fn))
;; :request-id appears

;; For new threads, capture and restore context
(let [ctx (μ/local-context)]
  (future
    (μ/with-context ctx
      (μ/log ::async-event))))
```

### Issue: Performance Impact

**Problem:** Logging too many events impacts performance.

**Solution:** μ/log is designed to be fast (under 300ns per event), but:

``` clojure
;; DON'T log in tight loops
(doseq [item (range 1000000)]
  (μ/log ::processing :item item)) ; Bad!

;; DO log summary events
(μ/log ::processing-started :count 1000000)
(doseq [item (range 1000000)]
  (process item))
(μ/log ::processing-complete :count 1000000 :duration-ms elapsed)

;; Or sample events
(doseq [item (range 1000000)]
  (when (zero? (mod item 10000))
    (μ/log ::processing-checkpoint :item item)))
```

### Issue: Trace Duration in Wrong Units

**Problem:** Duration values look wrong.

``` clojure
(μ/trace ::operation [] (do-work))
;; :mulog/duration 254402837
;; What unit is this?
```

**Solution:** Duration is always in **nanoseconds**. Convert to
milliseconds:

``` clojure
;; In your query/visualization layer
(/ duration 1000000.0) ; nanoseconds to milliseconds
(/ duration 1000000000.0) ; nanoseconds to seconds
```

## Advanced Topics

### Custom Publishers

Create custom publishers for specific needs:

``` clojure
(defn custom-publisher [{:keys [config]}]
  (let [running (atom true)]
    {:publisher-fn
     (fn [events]
       (doseq [event events]
         (my-custom-handler event)))

     :stop-fn
     (fn []
       (reset! running false))}))

;; Register and use
(μ/start-publisher! {:type :custom :publisher custom-publisher})
```

See [custom publishers
documentation](https://github.com/BrunoBonacci/mulog#custom-publishers)
for details.

### Sampling and Filtering

Filter events before publishing:

``` clojure
;; Only log errors
(defn error-filter [events]
  (filter #(= :error (:mulog/outcome %)) events))

;; Sample 10% of events
(defn sample-filter [events]
  (filter #(< (rand) 0.1) events))
```

## Resources

-   [GitHub Repository](https://github.com/BrunoBonacci/mulog)
-   [API Documentation](https://cljdoc.org/d/com.brunobonacci/mulog)
-   [Publisher
    Documentation](https://github.com/BrunoBonacci/mulog/tree/master/doc/publishers)
-   [Talk: μ/log and the Next 100 Logging
    Systems](https://www.youtube.com/watch?v=P1149dWnl3k)

## Summary

μ/log revolutionizes logging by treating logs as structured events
rather than text messages. Key points:

1.  **Events as data** - Log structured events with arbitrary key-value
    pairs
2.  **Fast and async** - Under 300ns per event, non-blocking
3.  **Rich context** - Global and local context for dimensional data
4.  **Distributed tracing** - Built-in μ/trace for tracking operations
5.  **Publisher ecosystem** - Send events to Elasticsearch, Kafka,
    CloudWatch, and more
6.  **Memory safe** - Bounded memory usage, drops events before crashing

Start with console publisher for development, then use Elasticsearch or
similar for production. Always set global context at startup and use
local context for request-scoped data.

# Kaocha

A comprehensive test runner for Clojure with support for multiple test
libraries and powerful plugin system.

## Overview

Kaocha is a modern test runner that works with clojure.test, specs, and
other testing frameworks. It provides detailed reporting, watch mode,
and extensibility through plugins.

## Core Concepts

**Running Tests**: Execute tests with Kaocha.

``` clojure
(require '[kaocha.runner :as runner])

; In terminal:
; clojure -M:test                    ; Run all tests
; clojure -M:test --watch           ; Watch mode
; clojure -M:test --focus my.test   ; Run specific test
```

**Test Files**: Kaocha automatically discovers test files.

``` clojure
; tests/ directory structure
; tests/
;   my/
;     app_test.clj

; In test file:
(ns my.app-test
  (:require [clojure.test :refer :all]
            [my.app :refer [add]]))

(deftest add-test
  (is (= 3 (add 1 2)))
  (is (= 5 (add 2 3))))
```

## Key Features

-   Watch mode for TDD
-   Multiple test library support
-   Coverage reporting with Cloverage
-   JUnit XML reporting
-   Detailed test output
-   Plugin system
-   Selective test running
-   Performance reporting

## When to Use

-   Running clojure.test tests
-   Test-driven development (watch mode)
-   CI/CD pipelines
-   Coverage reports

## When NOT to Use

-   Complex test orchestration (use custom runners)

## Common Patterns

``` clojure
; Basic test file
(ns my.handler-test
  (:require [clojure.test :refer :all]
            [my.handler :as handler]))

(deftest handler-test
  (testing "GET request"
    (is (= 200 (:status (handler/process-request {:method :get})))))

  (testing "POST request"
    (is (= 201 (:status (handler/process-request {:method :post}))))))

; In terminal:
; bb test                      ; Run tests via Babashka
; bb test --watch             ; Watch mode
; bb test --reporter documentation  ; Detailed output
```

## Related Libraries

-   org.clojure/test.check - Property-based testing
-   nubank/matcher-combinators - Better test assertions
-   lambdaisland/kaocha-cloverage - Coverage plugin

## Resources

-   Official Documentation: https://github.com/lambdaisland/kaocha
-   API Documentation: https://cljdoc.org/d/lambdaisland/kaocha

## Notes

This project uses Kaocha as the primary test runner. See bb.edn for test
configuration.

# matcher-combinators

A library for writing expressive test assertions with detailed mismatch
explanations.

## Overview

matcher-combinators provides matchers that make test assertions more
readable and generate helpful error messages when assertions fail,
making debugging test failures much easier.

## Core Concepts

**Matchers**: Expressive assertion matchers.

``` clojure
(require '[matcher-combinators.test])
(require '[matcher-combinators.matchers :as m])

; Simple matchers
(is (match? {:name "Alice" :age 30} actual-user))

; Partial matching
(is (match? {:name "Alice"} actual-user))  ; Only checks :name

; Collection matchers
(is (match? (m/contains [1 2 3]) (vec actual-numbers)))

; Optional keys
(is (match? {:name "Alice" :email (m/missing)} actual-user))
```

**Mismatch Reporting**: Clear error messages.

``` clojure
; When match fails, shows:
; Expected:
; {:name "Alice", :email string?}
;
; Actual:
; {:name "Alice", :email nil}
;
; Mismatch:
; {:email (expected string?, was nil)}
```

## Key Features

-   Expressive matcher syntax
-   Partial matching
-   Collection matchers
-   Regex matching
-   Custom matchers
-   Detailed mismatch output
-   Composable matchers

## When to Use

-   Writing tests with complex data structures
-   Debugging test failures
-   Partial object matching
-   Collections testing

## When NOT to Use

-   Simple equality tests (use is)

## Common Patterns

``` clojure
(require '[clojure.test :refer [deftest is]]
         '[matcher-combinators.test]
         '[matcher-combinators.matchers :as m])

; Testing API responses
(deftest create-user-endpoint
  (let [response (create-user {:name "Alice" :email "alice@example.com"})]
    (is (match? {:status 201
                 :body {:id pos-int?
                        :name "Alice"
                        :email "alice@example.com"}}
                response))))

; Testing collections
(deftest process-items
  (let [results (process-items items)]
    (is (match? (m/contains [item1 item2 item3])
                results))))

; Testing nested structures with optional fields
(deftest fetch-user-details
  (let [user (fetch-user 123)]
    (is (match? {:id 123
                 :name string?
                 :email (m/missing)  ; This field should not be present
                 :phone (m/optional string?)}  ; This field is optional
                user))))
```

## Related Libraries

-   org.clojure/test.check - Property-based testing
-   clojure.test - Built-in test library

## Resources

-   Official Documentation:
    https://github.com/nubank/matcher-combinators
-   API Documentation: https://cljdoc.org/d/nubank/matcher-combinators

## Notes

This project uses matcher-combinators for writing expressive test
assertions.

# test.chuck

A library providing additional generators and helpers for test.check
property-based testing.

## Overview

test.chuck extends test.check with commonly needed generators and
testing utilities, making property-based testing easier and more
powerful.

## Core Concepts

**Additional Generators**: Common generators not in test.check.

``` clojure
(require '[com.gfredericks.test.chuck.generators :as gen'])

; UUID generation
gen'/uuid

; Shuffled vectors
(gen'/shuffled (gen/vector gen/int))

; Partition vectors
(gen'/partition (gen/vector gen/int) gen/int)

; Subsequences
(gen'/subsequences (gen/vector gen/int))
```

**Testing Helpers**: Utilities for property testing.

``` clojure
(require '[com.gfredericks.test.chuck.clojure-test :refer [checking]])

; Cleaner syntax for property testing
(checking "vector properties"
  [xs (gen/vector gen/int)]
  (is (= (count xs) (count (reverse xs)))))
```

## Key Features

-   UUID and random ID generators
-   Collection manipulation generators
-   ASCII generators
-   Sorted collection generators
-   Better error reporting
-   Cleaner test syntax
-   Documentation generators

## When to Use

-   Extended test.check usage
-   Building custom generators
-   Complex property testing
-   Better test organization

## When NOT to Use

-   Simple property testing (test.check alone sufficient)

## Common Patterns

``` clojure
(require '[clojure.test :refer [deftest is]]
         '[com.gfredericks.test.chuck.clojure-test :refer [checking]]
         '[com.gfredericks.test.chuck.generators :as gen']
         '[clojure.test.check.generators :as gen])

; Using test.chuck syntax
(deftest sorting-properties
  (checking "sort is idempotent"
    [xs (gen/vector gen/int)]
    (is (= (sort xs) (sort (sort xs)))))

  (checking "sort increases size"
    [xs (gen/vector gen/int)]
    (is (= (count xs) (count (sort xs))))))

; UUID testing
(deftest uuid-generation
  (checking "generated UUIDs are unique"
    [ids (gen/vector gen'/uuid {:num-elements 100})]
    (is (= 100 (count (set ids))))))
```

## Related Libraries

-   org.clojure/test.check - Property-based testing
-   nubank/matcher-combinators - Better assertions

## Resources

-   Official Documentation: https://github.com/gfredericks/test.chuck
-   API Documentation: https://cljdoc.org/d/com.gfredericks/test.chuck

## Notes

This project uses test.chuck for extended property-based testing
capabilities.

# scope-capture

A Clojure library for capturing variable scope at specific points and
inspecting them interactively in the REPL.

## Overview

scope-capture allows you to insert breakpoints that capture the local
scope, then examine those captured values in the REPL without needing a
debugger.

## Core Concepts

**Capturing Scope**: Insert capture points in code.

``` clojure
(require '[vvvvalvalval.scope-capture :refer [defn-capture capture! letsc]])

; Capture at specific point
(defn-capture add-user [name email]
  (let [user {:name name :email email}]
    user))

; When called, captures local scope for inspection
(add-user "Alice" "alice@example.com")
```

**Inspecting Captured Scope**: View captured variables.

``` clojure
(require '[vvvvalvalval.scope-capture :refer [get-captures]])

; View what was captured
(get-captures)
; => [{:locals {:name "Alice", :email "alice@example.com", ...}, ...}]

; Inspect captured locals
@(vvvvalvalval.scope-capture/get-last-capture)
```

## Key Features

-   Scope capture at arbitrary points
-   REPL-friendly inspection
-   No external debugger required
-   Low overhead capture
-   Useful for debugging complex functions
-   Easy integration into code

## When to Use

-   Debugging complex function logic
-   Investigating test failures
-   Understanding intermediate values
-   Interactive development

## When NOT to Use

-   Production code
-   High-performance code
-   Long-running processes

## Common Patterns

``` clojure
(require '[vvvvalvalval.scope-capture :refer [defn-capture letsc capture!]])

; Debugging a problematic function
(defn-capture process-user-data [users filter-fn transform-fn]
  (let [filtered (filter filter-fn users)
        transformed (map transform-fn filtered)]
    transformed))

; Call it and then inspect
(process-user-data users some-filter some-transform)

; In REPL:
(require '[vvvvalvalval.scope-capture :refer [get-captures]])
(get-captures)
; Examine the captured locals to understand what went wrong

; Or use letsc for inline captures
(letsc [users (fetch-users)]
  (process-users users))

; Then inspect the capture
(deref (vvvvalvalval.scope-capture/get-last-capture))
```

## Related Libraries

-   com.stuartsierra/component.repl - REPL utilities

## Resources

-   Official Documentation:
    https://github.com/vvvvalvalval/scope-capture
-   API Documentation: https://cljdoc.org/d/vvvvalvalval/scope-capture

## Notes

This project uses scope-capture as a development tool for debugging
during REPL-driven development.

# clj-kondo

A fast, static analysis tool and linter for Clojure and ClojureScript.

## Overview

clj-kondo detects common errors and style issues in your code without
running it. It provides fast feedback on code quality with minimal
configuration.

## Core Concepts

**Running clj-kondo**: Lint your code.

``` clojure
; In terminal:
; clj-kondo --lint src/

; Output:
; src/my/app.clj:5:1: error: Unresolved symbol: undefined-func
; src/my/app.clj:10:5: warning: Unused binding: x
```

**Configuration**: Customize linting rules.

``` clojure
; In .clj-kondo/config.edn:
{:linters
 {:unresolved-symbol {:level :error}
  :unused-binding {:level :warning}
  :missing-docstring {:level :off}}}
```

## Key Features

-   Fast, incremental linting
-   Unresolved symbol detection
-   Unused variable detection
-   Code style checking
-   Custom configuration
-   IDE integration
-   Performance optimized

## When to Use

-   Development (catch errors early)
-   CI/CD pipelines
-   Pre-commit hooks
-   Code review automation

## When NOT to Use

-   Runtime behavior checking (use tests)

## Common Patterns

``` clojure
; In bb.edn:
{:tasks
 {:lint
  {:doc "Lint code with clj-kondo"
   :task (shell "clj-kondo --lint src test")}}}

; In Makefile:
; lint:
;   clj-kondo --lint src test

; Common issues caught:
; 1. Unresolved symbols
(foo bar)  ; Error if foo not defined

; 2. Unused variables
(let [x 1 y 2]
  y)  ; Warning: x is unused

; 3. Wrong arity
(map inc [1 2 3] [4 5 6])  ; Error: map expects 2 args
```

## Related Libraries

-   io.github.tonsky/clj-reload - Hot code reloading

## Resources

-   Official Documentation: https://github.com/clj-kondo/clj-kondo
-   API Documentation: https://cljdoc.org/d/clj-kondo/clj-kondo

## Notes

This project uses clj-kondo for static analysis and style checking. Run
with `bb lint`.

# cljstyle

A code formatter for Clojure that enforces consistent style.

## Overview

cljstyle automatically formats Clojure code to follow the community
style guide, ensuring consistency across your codebase.

## Core Concepts

**Formatting Code**: Apply consistent style.

``` clojure
; In terminal:
; cljstyle check src/               ; Check formatting
; cljstyle fix src/                 ; Fix formatting in place
```

**Configuration**: Customize formatting rules.

``` clojure
; In .cljstyle:
{:indentation? true
 :line-length 100
 :remove-trailing-whitespace? true
 :require-blank-line-before-namespace-docstring? false}
```

## Key Features

-   Consistent indentation
-   Line length enforcement
-   Whitespace cleanup
-   Require formatting
-   Docstring formatting
-   Customizable rules
-   IDE integration

## When to Use

-   Development (maintain consistent style)
-   Pre-commit hooks
-   CI/CD pipelines
-   Team code standards

## When NOT to Use

-   High-performance parsing

## Common Patterns

``` clojure
; In bb.edn:
{:tasks
 {:fmt
  {:doc "Format code"
   :task (shell "cljstyle fix src test")}

  :fmt-check
  {:doc "Check formatting"
   :task (shell "cljstyle check src test")}}}

; Example formatting:
; Before:
(defn add[a b]
  (+  a    b))

; After:
(defn add [a b]
  (+ a b))
```

## Related Libraries

-   clj-kondo/clj-kondo - Linting

## Resources

-   Official Documentation: https://github.com/greglook/cljstyle
-   API Documentation: https://cljdoc.org/d/mvxcvi/cljstyle

## Notes

This project uses cljstyle for code formatting. Run with `bb fmt` or
`bb fmt-check`.

# clj-reload

A Clojure library for hot reloading code during interactive REPL
development.

## Overview

clj-reload provides smart code reloading that handles dependency order
correctly, preventing errors that can occur with naive reload
approaches.

## Core Concepts

**Reloading Code**: Reload updated code in REPL.

``` clojure
(require '[clj-reload.core :as reload])

; Reload all modified code
(reload/reload)

; Reload specific namespace
(reload/reload 'my.app.core)
```

**Dependency Tracking**: Understands namespace dependencies.

``` clojure
; clj-reload reloads in correct order:
; If ns-a depends on ns-b, ns-b is reloaded first
; This prevents "unresolved symbol" errors
```

## Key Features

-   Smart dependency ordering
-   Selective reloading
-   REPL-friendly
-   Low overhead
-   No external debugger needed
-   Works with component systems

## When to Use

-   Interactive development
-   REPL-driven development
-   Avoiding REPL restarts
-   Rapid feedback loop

## When NOT to Use

-   Not suitable for all code changes (sometimes restart needed)

## Common Patterns

``` clojure
; In REPL:
(require '[clj-reload.core :as reload]
         '[my.app :as app])

; Edit code in editor...

; Reload changes
(reload/reload)

; Continue using updated functions
(app/do-something)

; Reload specific namespace
(reload/reload 'my.app.handler)

; With component system
(require '[com.stuartsierra.component :as component])

(def system (component/start (create-system)))

; Edit code...

; Reload and restart specific component
(reload/reload)
(alter-var-root #'system component/stop)
(alter-var-root #'system (fn [_] (component/start (create-system))))
```

## Related Libraries

-   com.stuartsierra/component - Lifecycle management
-   com.stuartsierra/component.repl - Component REPL integration

## Resources

-   Official Documentation: https://github.com/tonsky/clj-reload
-   API Documentation: https://cljdoc.org/d/io.github.tonsky/clj-reload

## Notes

This project uses clj-reload for interactive development and rapid
feedback loops.

# nREPL

A Clojure network REPL that provides a server and client, along with
common APIs for IDEs and tools that need to evaluate Clojure code in
remote environments.

## Quick Start

Start an nREPL server and connect from any client:

``` clojure
;; Start server (programmatically)
(require '[nrepl.server :refer [start-server stop-server]])
(defonce server (start-server :port 7888))
;; => #'user/server

;; Or via command line
;; clj -M:nrepl -m nrepl.cmdline --port 7888
;; lein repl :headless :port 7888

;; Connect from another process
(require '[nrepl.core :as nrepl])
(with-open [conn (nrepl/connect :port 7888)]
  (-> (nrepl/client conn 1000)
      (nrepl/message {:op "eval" :code "(+ 1 2 3)"})
      nrepl/response-values))
;; => [6]

;; Stop server
(stop-server server)
```

**Key benefits:** - Network-based REPL for remote development -
Foundation for editor integrations (CIDER, Calva, Cursive) -
Middleware-based extensibility - Supports multiple concurrent sessions -
Transport abstraction (bencode, tty, custom)

## Core Concepts

### Message-Based Protocol

nREPL is fundamentally message-oriented and asynchronous. Clients send
request messages and receive response messages:

``` clojure
;; Request message
{:op "eval"
 :code "(+ 1 2 3)"
 :session "abc-123"
 :id "msg-456"}

;; Response messages
{:value "6"
 :session "abc-123"
 :id "msg-456"}

{:status ["done"]
 :session "abc-123"
 :id "msg-456"}
```

**Key properties:** - Messages are maps with keyword keys - `:op`
specifies the operation - `:session` identifies the evaluation context -
`:id` correlates requests with responses - Multiple responses possible
per request

### Transports

Transports implement the communication protocol. nREPL includes:

``` clojure
;; Bencode transport (default, binary)
(nrepl/connect :port 7888)
;; Uses nrepl.transport/bencode

;; TTY transport (telnet-compatible, text)
(require '[nrepl.server :refer [start-server]])
(start-server
  :port 7888
  :transport-fn #(nrepl.transport/tty % {}))

;; Connect via telnet
;; telnet localhost 7888
```

**Available transports:** - `bencode` - Default, efficient binary
format - `tty` - Text-based for telnet/netcat - Custom transports can be
implemented

### Middleware

Middleware compose to create the server's handler. They can intercept
and modify messages:

``` clojure
(require '[nrepl.server :refer [default-handler]])

;; Start with default middleware
(def handler (default-handler))

;; Or add custom middleware
(defn logging-middleware [handler]
  (fn [{:keys [op] :as msg}]
    (println "Operation:" op)
    (handler msg)))

(def custom-handler
  (default-handler #'logging-middleware))

(start-server :port 7888 :handler custom-handler)
```

**Default middleware provides:** - Code evaluation (`eval`) - Session
management - Interrupts - Code loading (`load-file`) - Completion -
Documentation lookup - stdin handling

### Sessions

Sessions provide isolated evaluation contexts:

``` clojure
(require '[nrepl.core :as nrepl])

(with-open [conn (nrepl/connect :port 7888)]
  (let [client (nrepl/client conn 1000)]
    ;; Create a new session
    (def session-id (nrepl/new-session client))

    ;; Use session-specific client
    (let [sess-client (nrepl/client-session client :session session-id)]
      ;; Evaluations share state within session
      (sess-client {:op "eval" :code "(def x 42)"})
      (-> (sess-client {:op "eval" :code "x"})
          nrepl/response-values))
    ;; => [42]

    ;; x doesn't exist in other sessions
    (-> (client {:op "eval" :code "x"})
        nrepl/response-values)))
;; => Exception: Unable to resolve symbol: x
```

## Common Workflows

### Workflow 1: Starting a Server

**Via Command Line (Clojure CLI):**

``` clojure
;; Add to ~/.clojure/deps.edn
{:aliases
 {:nrepl
  {:extra-deps {nrepl/nrepl {:mvn/version "1.3.0"}}}}}

;; Start server
;; clj -M:nrepl -m nrepl.cmdline --port 7888
;; clj -M:nrepl -m nrepl.cmdline --socket /tmp/nrepl.sock  ;; Unix socket
```

**Via Leiningen:**

``` clojure
;; Start with REPL-y client
;; lein repl

;; Start headless
;; lein repl :headless :port 7888
;; lein repl :headless :socket /tmp/nrepl.sock
```

**Programmatically (Embedding):**

``` clojure
(require '[nrepl.server :refer [start-server stop-server]])

;; Basic server
(defonce server (start-server :port 7888))

;; Bind to specific address
(defonce server (start-server
                  :bind "172.18.0.5"
                  :port 4001))

;; Unix domain socket (more secure)
(defonce server (start-server
                  :socket "/some/where/safe/nrepl"))

;; With custom handler
(defonce server (start-server
                  :port 7888
                  :handler (default-handler #'my.middleware/wrap-something)))

;; Stop server
(stop-server server)
```

### Workflow 2: Connecting as a Client

``` clojure
(require '[nrepl.core :as nrepl])

;; TCP connection
(with-open [conn (nrepl/connect :port 7888)]
  (let [client (nrepl/client conn 1000)]  ;; 1000ms timeout
    ;; Send message, get responses
    (-> (client {:op "eval" :code "(+ 1 2 3)"})
        nrepl/response-values)))
;; => [6]

;; Unix socket connection
(with-open [conn (nrepl/connect :socket "/tmp/nrepl.sock")]
  (let [client (nrepl/client conn 1000)]
    (nrepl/response-values
      (client {:op "eval" :code "(range 5)"}))))
;; => [(0 1 2 3 4)]

;; Process full response messages
(with-open [conn (nrepl/connect :port 7888)]
  (let [client (nrepl/client conn 1000)]
    (doall (client {:op "eval" :code "(println \"Hello\")"}))))
;; => ({:out "Hello\n" :session "..." :id "..."}
;;     {:ns "user" :value "nil" :session "..." :id "..."}
;;     {:status ["done"] :session "..." :id "..."})
```

### Workflow 3: Using Sessions

``` clojure
(require '[nrepl.core :as nrepl])

(with-open [conn (nrepl/connect :port 7888)]
  (let [client (nrepl/client conn 1000)
        session-id (nrepl/new-session client)]

    ;; Create session-specific client
    (let [sess (nrepl/client-session client :session session-id)]
      ;; State is preserved within session
      (sess {:op "eval" :code "(def x 100)"})
      (sess {:op "eval" :code "(def y 200)"})
      (nrepl/response-values
        (sess {:op "eval" :code "(+ x y)"})))
    ;; => [300]

    ;; Clone existing session
    (let [cloned-id (nrepl/new-session client :clone session-id)
          cloned (nrepl/client-session client :session cloned-id)]
      ;; Cloned session has same state
      (nrepl/response-values
        (cloned {:op "eval" :code "x"})))))
;; => [100]
```

### Workflow 4: Custom Middleware

``` clojure
(require '[nrepl.middleware :refer [set-descriptor!]]
         '[nrepl.server :refer [default-handler start-server]]
         '[nrepl.transport :as transport])

;; Define middleware
(defn timing-middleware [handler]
  (fn [{:keys [op transport] :as msg}]
    (let [start (System/currentTimeMillis)
          result (handler msg)
          elapsed (- (System/currentTimeMillis) start)]
      (when (= op "eval")
        (transport/send transport
          {:timing elapsed
           :session (:session msg)
           :id (:id msg)}))
      result)))

;; Add descriptor (optional, for documentation)
(set-descriptor! #'timing-middleware
  {:expects #{"eval"}
   :handles {}})

;; Use middleware
(def handler (default-handler #'timing-middleware))
(start-server :port 7888 :handler handler)
```

### Workflow 5: Loading Files

``` clojure
(require '[nrepl.core :as nrepl]
         '[clojure.java.io :as io])

(with-open [conn (nrepl/connect :port 7888)]
  (let [client (nrepl/client conn 5000)
        file-content (slurp "src/my_app/core.clj")
        file-path "src/my_app/core.clj"]

    ;; Load file
    (-> (client {:op "load-file"
                 :file file-content
                 :file-path file-path
                 :file-name "core.clj"})
        nrepl/combine-responses)))
;; => {:status #{:done}, :ns "my-app.core", ...}
```

### Workflow 6: Interrupting Evaluation

``` clojure
(require '[nrepl.core :as nrepl])

(with-open [conn (nrepl/connect :port 7888)]
  (let [client (nrepl/client conn 1000)
        sess-id (nrepl/new-session client)
        sess (nrepl/client-session client :session sess-id)]

    ;; Start long-running evaluation
    (future
      (sess {:op "eval" :code "(Thread/sleep 60000)"}))

    ;; Wait a bit
    (Thread/sleep 100)

    ;; Send interrupt
    (client {:op "interrupt" :session sess-id})))
;; Long evaluation will be interrupted
```

### Workflow 7: Embedding in Java Applications

``` java
import clojure.java.api.Clojure;
import clojure.lang.IFn;

public class App {
    public static void main(String[] args) {
        // Require nREPL
        IFn require = Clojure.var("clojure.core", "require");
        require.invoke(Clojure.read("nrepl.server"));

        // Start server
        IFn start = Clojure.var("nrepl.server", "start-server");
        int port = 7888;
        Object server = start.invoke(
            Clojure.read(":port"),
            Clojure.read(Integer.toString(port)));

        System.out.println("nREPL server started on port " + port);

        // Stop server later
        // IFn stop = Clojure.var("nrepl.server", "stop-server");
        // stop.invoke(server);
    }
}
```

### Workflow 8: Configuration Files

**Global configuration** (`~/.nrepl/nrepl.edn`):

``` clojure
{:bind "localhost"
 :transport nrepl.transport/bencode
 :middleware [some.ns/my-middleware]}
```

**Project configuration** (`.nrepl.edn` in project root):

``` clojure
{:port 12345
 :bind "localhost"
 :handler some.ns/custom-handler}
```

**Dynamic variable defaults**:

``` clojure
;; In ~/.nrepl/nrepl.edn
{:dynamic-vars {clojure.core/*warn-on-reflection* true
                clojure.core/*print-length* 100}}
```

## When to Use Each Approach

**Use TCP sockets when:** - Connecting from remote machines - Standard
editor integration - Need network transparency - Traditional REPL
workflow

**Use Unix domain sockets when:** - Local development only - Enhanced
security (no network exposure) - Better performance (no routing
overhead) - POSIX-based access control

**Use embedded server when:** - Deploying applications to production -
Need runtime debugging capability - Dynamic code patching required -
Remote monitoring and introspection

**Use command-line start when:** - Interactive development - Quick REPL
sessions - Editor integration - Standard tooling workflows

## Best Practices

**DO:** - Use Unix sockets for local development (more secure) - Bind to
`localhost` when using TCP ports - Use sessions to isolate evaluation
contexts - Stop servers properly with `stop-server` - Add authentication
for production deployments - Use timeouts on client connections -
Configure middleware via deps.edn or project.clj - Use `:port 0` to
auto-select available port - Leverage configuration files for consistent
setup

**DON'T:** - Expose nREPL on public networks without authentication -
Share sessions between different clients carelessly - Bind to `0.0.0.0`
without understanding security implications - Forget to handle
connection errors - Use blocking operations in middleware - Ignore
session cleanup - Hard-code ports in production - Skip timeout
configuration on clients

## Common Issues

### Issue: "Connection Refused"

**Problem:** Cannot connect to nREPL server

``` clojure
;; Connection fails
(nrepl/connect :port 7888)
;; => java.net.ConnectException: Connection refused
```

**Solution:** Check server is running and port/host are correct

``` clojure
;; Verify server is running
;; lsof -i :7888
;; netstat -an | grep 7888

;; Check host binding
(start-server :bind "127.0.0.1" :port 7888)  ;; Local only
(start-server :bind "0.0.0.0" :port 7888)    ;; All interfaces (dangerous!)

;; Check firewall rules if remote connection
```

### Issue: "Evaluation Timeout"

**Problem:** Operations take too long and timeout

``` clojure
(with-open [conn (nrepl/connect :port 7888)]
  (let [client (nrepl/client conn 1000)]  ;; 1 second timeout
    (client {:op "eval" :code "(Thread/sleep 5000)"})))
;; => Timeout waiting for response
```

**Solution:** Increase timeout or use async evaluation

``` clojure
;; Increase timeout
(let [client (nrepl/client conn 10000)]  ;; 10 seconds
  (client {:op "eval" :code "(Thread/sleep 5000)"}))

;; Or use async (no timeout on response-seq)
(let [responses (nrepl/response-seq conn)]
  ;; Process responses as they arrive
  (doseq [resp responses]
    (println resp)))
```

### Issue: "Session State Lost"

**Problem:** Definitions disappear between evaluations

``` clojure
(let [client (nrepl/client conn 1000)]
  (client {:op "eval" :code "(def x 42)"})
  (nrepl/response-values
    (client {:op "eval" :code "x"})))
;; => Exception: Unable to resolve symbol: x
```

**Solution:** Use session-specific client

``` clojure
;; Create and use a session
(let [client (nrepl/client conn 1000)
      session-id (nrepl/new-session client)
      sess (nrepl/client-session client :session session-id)]
  ;; Now state persists
  (sess {:op "eval" :code "(def x 42)"})
  (nrepl/response-values
    (sess {:op "eval" :code "x"})))
;; => [42]
```

### Issue: "Unknown Op"

**Problem:** Operation not supported by server

``` clojure
(client {:op "custom-operation" :arg "value"})
;; => {:status ["unknown-op" "done"], ...}
```

**Solution:** Ensure required middleware is loaded

``` clojure
;; Check available ops
(-> (client {:op "describe"})
    nrepl/combine-responses
    :ops
    keys)

;; Add middleware that provides the op
(def handler (default-handler #'my.middleware/custom-op))
(start-server :port 7888 :handler handler)
```

### Issue: "Permission Denied" (Unix Sockets)

**Problem:** Cannot connect to Unix socket

``` clojure
(nrepl/connect :socket "/tmp/nrepl.sock")
;; => java.io.IOException: Permission denied
```

**Solution:** Check socket file permissions and parent directory

``` bash
# Make directory accessible
mkdir -m 700 /tmp/safe-nrepl

# Start server there
# clj -M:nrepl -m nrepl.cmdline --socket /tmp/safe-nrepl/socket

# Connect
(nrepl/connect :socket "/tmp/safe-nrepl/socket")
```

## Advanced Topics

### Built-in Operations

nREPL's default middleware provides many operations beyond `eval`:

-   `describe` - List available operations and versions
-   `eval` - Evaluate code
-   `load-file` - Load and evaluate a file
-   `interrupt` - Interrupt running evaluation
-   `stdin` - Provide input to *in*
-   `clone` - Clone session
-   `close` - Close session
-   `ls-sessions` - List active sessions
-   `completions` - Code completion
-   `lookup` - Symbol lookup/documentation

See [nREPL's ops documentation](https://nrepl.org/nrepl/ops.html) for
complete list.

### TLS/SSL Support

``` clojure
;; Start TLS server
(start-server
  :port 7888
  :tls? true
  :tls-keys-file "path/to/keystore.jks")

;; Connect with TLS
(with-open [conn (nrepl/connect
                   :port 7888
                   :tls-keys-file "path/to/keystore.jks")]
  (let [client (nrepl/client conn 1000)]
    (nrepl/response-values
      (client {:op "eval" :code "(+ 1 2)"}))))
```

### Custom Transports

Implement `nrepl.transport/Transport` protocol for custom communication
channels (HTTP, WebSockets, message queues, etc.).

## Resources

-   Official Documentation: https://nrepl.org
-   API Docs: https://cljdoc.org/d/nrepl/nrepl/CURRENT
-   GitHub: https://github.com/nrepl/nrepl
-   Building Middleware:
    https://nrepl.org/nrepl/building_middleware.html
-   Building Servers: https://nrepl.org/nrepl/building_servers.html
-   Building Clients: https://nrepl.org/nrepl/building_clients.html

## Editor Integration

-   **CIDER** (Emacs): https://docs.cider.mx/
-   **Calva** (VSCode): https://calva.io/
-   **Cursive** (IntelliJ): https://cursive-ide.com/
-   **vim-fireplace** (Vim): https://github.com/tpope/vim-fireplace
-   **Conjure** (Neovim): https://github.com/Olical/conjure

## Summary

nREPL is the foundation of Clojure's networked REPL ecosystem:

-   **Message-based** - Asynchronous request/response protocol
-   **Extensible** - Middleware architecture for custom functionality
-   **Session-based** - Isolated evaluation contexts
-   **Transport-agnostic** - Bencode, TTY, or custom transports
-   **Editor-friendly** - Powers CIDER, Calva, Cursive, and more
-   **Embeddable** - Add REPL server to any application
-   **Secure options** - Unix sockets, TLS, localhost binding

Use nREPL for remote development, editor integration, production
debugging, and any scenario requiring networked access to a Clojure
runtime.

# uberdeps

A simple tool for building uberjars (executable JAR files) from Clojure
projects.

## Overview

uberdeps packages your application and all its dependencies into a
single executable JAR file, making deployment straightforward.

## Core Concepts

**Building Uberjars**: Create executable JAR files.

``` clojure
; In terminal:
; clojure -X:uberdeps

; In deps.edn:
; :uberdeps {:replace-deps {uberdeps/uberdeps {:mvn/version "1.4.0"}}
;            :replace-paths []
;            :main-opts ["-m" "uberdeps.uberjar"]}
```

**Configuration**: Customize build settings.

``` clojure
; In uberdeps.edn or deps.edn:
{:aliases
 {:uberdeps {:main-opts ["-m" "uberdeps.uberjar"
                         "--alias" "myapp"
                         "--output" "target/myapp.jar"]}}}
```

## Key Features

-   Simple uberjar building
-   Manifest configuration
-   Class path assembly
-   Dependency inclusion
-   Minimal configuration
-   Fast builds

## When to Use

-   Deployment of applications
-   Distribution of standalone tools
-   CI/CD builds
-   Containerization

## When NOT to Use

-   Development (regular CLI preferred)

## Common Patterns

``` clojure
; In bb.edn:
; Build uberjar
; clojure -X:uberdeps

; Example deployment:
; 1. Build: clojure -X:uberdeps
; 2. Result: target/myapp.jar
; 3. Deploy: java -jar target/myapp.jar

; With main entry point in deps.edn:
; :main {:main-opts ["-m" "my.app.main"]}

; Build and execute:
; clojure -X:uberdeps
; java -jar target/myapp.jar arg1 arg2
```

## Related Libraries

-   clojure/tools.build - Advanced build tool

## Resources

-   Official Documentation: https://github.com/tonsky/uberdeps
-   API Documentation: https://cljdoc.org/d/uberdeps/uberdeps

## Notes

This project uses uberdeps for building executable JAR files for
distribution.
