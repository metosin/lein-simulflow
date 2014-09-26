# lein-simulflow

```
“Daydreaming is the first awakening of what we call simulflow. It is
an essential tool of rational thought. With it you can clear the mind for
better thinking.”
```
– Frank Herbert, _Heretics of Dune_

## Overview

While doing modern project with both Clojure and ClojureScript your workflow
might require running several lein tasks parallal, e.g. cljx, midje, cljsbuild.

For notes about implementation, check [this](./docs/notes.md).

## Usage

FIXME: Use this for user-level plugins:

Put `[lein-simulflow "0.1.0-SNAPSHOT"]` into the `:plugins` vector of your
`:user` profile, or if you are on Leiningen 1.x do `lein plugin install
lein-simulflow 0.1.0-SNAPSHOT`.

FIXME: Use this for project-level plugins:

Put `[lein-simulflow "0.1.0-SNAPSHOT"]` into the `:plugins` vector of your
project.clj.

FIXME: and add an example usage that actually makes sense:

    $ lein simulflow
