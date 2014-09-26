# lein-simulflow [![Travis CI status](https://secure.travis-ci.org/metosin/lein-simulflow.png)](http://travis-ci.org/#!/metosin/lein-simulflow/builds)

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

Put `[lein-simulflow "0.1.0-SNAPSHOT"]` into the `:plugins` vector of your
project.clj.

```bash
$ lein simulflow
```
