# OGNL Script Plugin for Fess

[![Java CI with Maven](https://github.com/codelibs/fess-script-ognl/actions/workflows/maven.yml/badge.svg)](https://github.com/codelibs/fess-script-ognl/actions/workflows/maven.yml)

A script engine plugin for [Fess](https://fess.codelibs.org/), the open-source enterprise search
server, that evaluates [OGNL](https://github.com/orphan-oss/ognl) (Object-Graph Navigation
Language) expressions.

## Overview

Fess lets an administrator enter a small script or expression in several places — computing a
field value during crawling, deciding a document boost, or running a scheduled job. Fess 15.9
evaluates these with a JavaScript engine by default, and ships Groovy as a bundled plugin. This
plugin adds `ognl` as a further alternative for the places that just need a **short expression**,
not a script.

OGNL has no statements, no control flow, and no `return`; an OGNL expression is a single value
expression such as `title + " - " + siteName` or `content.length() > 0`. That constraint is the
point: an OGNL expression is easy to read at a glance, cannot loop or branch, and (in `strict`
mode) can be bounded to a known set of classes and members. Use it for field-mapping, boost and
path-mapping expressions. For a scheduled job — or anything else that needs statements, a loop or
a `return` — use one of the scripting engines instead.

## Where OGNL Can Be Used

Fess evaluates scripts in five places, and `ognl` can be selected in all five:

| Place | How |
| --- | --- |
| Data store crawling | Add the handler parameter `script_type=ognl` to the data crawling config |
| Web/File crawling field scripts | Add the config parameter `config.script.type=ognl` to the crawling config, and write the field templates as `field.script.<field>=<expression>` |
| Document boost (Boost Document Rule) | Set the rule's own **Script Type** to `ognl`. This is per rule — each boost rule chooses its own engine |
| Path mapping | Prefix the **Replacement** with `ognl:`. The expression's result becomes the whole replacement URL; the original URL and the regex `Matcher` are bound as `#url` and `#matcher` |
| Scheduler jobs | Set the job's **Script Type** to `ognl`. Possible, but rarely what you want — see below |

### Scheduler jobs: possible, but usually the wrong tool

A scheduled job whose Script Type is `ognl` really is evaluated by this engine, and its result is
written to the job log. But a scheduled job is normally a *procedure*, and OGNL has no statements,
no control flow and no `return`. Every job Fess ships (Default Crawler, Suggest Indexer, Log
Purger, Thumbnail Generator, and the rest) is written against an engine that has those; none of
them can be rewritten as a single OGNL expression.

> **Note for readers of older documentation.** Before Fess 15.9, the scheduler ignored the Script
> Type field and always used Groovy, and path mapping was tied to the Groovy engine. Both are
> fixed in 15.9: the scheduler honours the field, and path mapping resolves whatever engine name
> precedes the colon.

## Installation

### Prerequisites

- Fess 15.9.0 or later
- Java 21 or later

### Download

Fess plugins are distributed through the CodeLibs Maven repository, not Maven Central.
Download the plugin JAR from
[maven.codelibs.org](https://maven.codelibs.org/org/codelibs/fess/fess-script-ognl/).

### Plugin Installation

1. Download `fess-script-ognl-{version}.jar`
2. Copy the JAR file to your Fess plugin directory (`$FESS_HOME/app/WEB-INF/plugin/`)
3. Restart the Fess server
4. The `ognl` script engine is now available wherever a script type can be selected (see
   [Where OGNL Can Be Used](#where-ognl-can-be-used) above)

For general plugin installation steps, see the
[Plugin](https://fess.codelibs.org/15.8/admin/plugin-guide.html) section of the Fess
Administration guide.

## Configuration

The engine reads the following settings once, at Fess startup, from `system.properties` or from
the JVM system property `-Dfess.system.<key>` (`system.properties` takes precedence — the JVM
system property is consulted only when the key is absent from `system.properties`). **Changing
any of these requires a Fess restart** — they are not re-read at evaluation time.

| Key | Default | Notes |
| --- | --- | --- |
| `script.ognl.mode` | `compat` | `compat` or `strict`; see below |
| `script.ognl.cache.size` | `1000` | Maximum number of parsed expressions cached; clamped to a minimum of `1` |
| `script.ognl.max.log.length` | `200` | Maximum length of script text included in warning/error log messages; clamped to a minimum of `3` |
| `script.ognl.expression.max.length` | `4000` | Maximum number of characters an expression may contain; longer expressions are rejected (logged, evaluate to `null`); clamped to a minimum of `1` |
| `script.ognl.allowed.classes` | see [strict mode](#compat-vs-strict-mode) | Comma-separated class/package allow list, used only in `strict` mode |
| `script.ognl.denied.packages` | see [strict mode](#compat-vs-strict-mode) | Comma-separated declaring-class deny list, used only in `strict` mode |

A malformed numeric value (not a valid integer) is ignored and the default is used, with a
warning logged. A value below the documented minimum is clamped up to that minimum, also with a
warning logged.

## compat vs strict Mode

| | `compat` (default) | `strict` |
| --- | --- | --- |
| Fess DI container | Bound into the expression as `container` | Not bound — `container` is not reachable |
| Class allow list | Not applied — `@any.Class@method(...)` can reach any class | Applied — only classes matching `script.ognl.allowed.classes` resolve |
| Member deny list | Not applied | Applied — members declared on a class matching `script.ognl.denied.packages` are not callable |
| Purpose | Preserves historical, pre-plugin-hardening behavior | Bounds what an expression can reach out to |

`compat` is the default and matches how this plugin has always behaved: it exists so existing data
store and crawler configurations keep working unchanged.

Setting `script.ognl.mode=strict` applies a class allow list (default: `java.lang.Math`,
`java.lang.String`, `java.lang.Boolean`, `java.lang.Integer`, `java.lang.Long`, `java.lang.Float`,
`java.lang.Double`, `java.lang.Number`, `java.util.Date`, `java.util.Arrays`, `java.util.List`,
`java.util.Map`, `java.util.Set`, `java.util.Collections`, `java.math.BigDecimal`, `java.time`
(and its sub-packages), `org.codelibs.core.lang.StringUtil`, `org.codelibs.fess.util.DocumentUtil`,
`org.codelibs.fess.taglib.FessFunctions`) and a declaring-class deny list (default: `java.io`,
`java.nio`, `java.net`, `java.lang.reflect`, `java.lang.invoke`, `java.lang.System`,
`java.lang.Class`, `java.lang.Runtime`, `java.lang.ProcessBuilder`, `java.lang.Process`,
`java.lang.Thread`, `java.lang.ClassLoader`, `javax.script`, `jdk.`, `sun.`, `org.lastaflute.di`).

If `script.ognl.mode` is set to anything other than `compat` or `strict` (including a typo), the
engine falls back to `compat` mode and logs a warning. The effective mode — along with the cache
size and expression max length actually in effect — is logged at `INFO` level on startup; check
that line to confirm `strict` mode actually took effect.

**`strict` mode is not a sandbox for the objects an expression is handed — it only bounds what an
expression can reach out to.** The class allow list controls which classes `@some.Class@method(...)`
can resolve, and the deny list blocks members declared on the listed classes/packages. Neither
list restricts what an expression can do with objects already present in its parameter map:
`org.codelibs.*` is not on the deny list, so if a caller puts a Fess helper or entity object into
the parameter map, every public method on that object remains callable from the expression, in
both modes. Treat `strict` mode as narrowing the blast radius of `@Class@method(...)` calls and
reflection-adjacent members — not as isolating the expression from the data it was given.

## Writing Expressions

Data store crawling and field-mapping configs use the `fieldName=expression` form, one per line,
where `expression` is evaluated once per document per field:

```
title=title
content=content.trim()
url=url
digest=content.length() > 200 ? content.substring(0, 200) + "..." : content
```

Parameters are available in an expression both as a bare name and with the OGNL context-variable
prefix — `name` and `#name` resolve to the same value:

```
title=name
title=#name
```

## Differences from Groovy

OGNL is not a Groovy subset; expressions that look like Groovy can silently do the wrong thing.
Watch for:

- **No `return`.** An OGNL expression's value is the expression's value; `return x` is not valid
  OGNL syntax and fails to parse.
- **No statements or control flow.** No `if`/`for`/`while`, no multi-statement blocks. Use the
  ternary operator (`cond ? a : b`) for conditionals.
- **No Elvis operator (`?:`).** `value ?: "default"` is not supported.
- **No safe navigation (`?.`).** `a?.b` is not supported; a null-checking ternary
  (`a != null ? a.b : null`) is the equivalent.
- **Groovy-style string interpolation is not an error — it is worse.** `"x${id}"` is valid OGNL
  syntax, but OGNL does not interpolate it: it evaluates to the literal string `x${id}`, including
  the unexpanded `${id}`. There is no exception or warning to catch the mistake; the field is
  simply indexed with the wrong text.
- **Numeric types differ from Groovy's.** `10 / 3` evaluates to the `Integer` `3` (integer
  division, like Java), and `3.14` evaluates to a `Double`. Groovy would give a `BigDecimal` for
  the literal and for the division. If an expression computes a boost value or is compared to a
  `BigDecimal`, this difference in type and in truncation behavior matters.

## Audit Logging

When Fess's audit log is enabled (`script.audit.log.enabled`, on by default), script execution is
recorded. Other engines log every evaluation. This engine does not: it logs the **first**
successful evaluation and the **first** failed evaluation of each distinct expression text, and
stays silent on repeats of the same text. This is intentional — an expression that runs once per
document per field would otherwise flood the audit log with repeats of the same text and add
nothing.

> **Only the web application process is audited.** Audit records come from Fess's
> `activityHelper`, which is a web application component; the crawler process has neither that
> component nor an audit appender. So a scheduled job with Script Type `ognl` is recorded, while
> data store, field-mapping and boost expressions — which all run inside the crawler process —
> produce **no audit records at all**. Do not treat an empty audit log as evidence that a crawl's
> expressions did not run.

"Failed evaluation" here means an expression that parsed successfully but failed while it ran
(for example, `1 / 0`). An expression that fails to **parse** is not audited at all, at any point:
parse failures are not added to the parsed-expression cache, so there is no cached entry to track
dedup state against, and auditing them would flood the log with the same parse error on every
evaluation of that expression instead of deduplicating it.

This guarantee is scoped to the parsed-expression cache, not to the lifetime of the process: an
expression is deduplicated only while it remains in the cache described by
`script.ognl.cache.size`. If it is evicted (the cache is bounded and least-recently-used entries
are dropped once it is full) or the engine is reinitialized (e.g., a restart), the next evaluation
of that same expression text is treated as new and logged again.

## Nested Classes and the Class Allow List

`script.ognl.allowed.classes` matches on a dot-separated prefix: an entry like `java.time` allows
`java.time.LocalDate` and any class under the `java.time` package. It does **not** allow nested
(inner) classes reachable through an enclosing class or package prefix — a nested class's binary
name uses `$`, not `.` (for example, `java.util.Map$Entry`), so a prefix such as `java.util.Map`
does not admit `Map.Entry`. If an expression needs a specific nested class in `strict` mode, add
its full binary name (with `$`) to `script.ognl.allowed.classes` explicitly.

## Why OGNL 3.4.7

This plugin pins to OGNL 3.4.7 deliberately; do not bump it without checking this first. OGNL
3.4.9 and later removed the null-chain short-circuit in property access: evaluating `a.b.c` when
`a.b` is `null` returns `null` on 3.4.7, but **throws** on 3.4.9+. Fess data store expressions
routinely read fields that may be absent on a given document, producing exactly this pattern of
null intermediate values. Upgrading past 3.4.7 would turn ordinary missing fields into warnings
and dropped values across otherwise unremarkable documents.

## License

This project is licensed under the Apache License 2.0 - see the [LICENSE](LICENSE) file for
details.
