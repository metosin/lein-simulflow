# lein-simulflow [![Travis CI status](https://secure.travis-ci.org/metosin/lein-simulflow.png)](http://travis-ci.org/#!/metosin/lein-simulflow/builds)

Combine several lein auto tasks for leaner workflow.

*NOTE: Very early version, not usable*

```
“Daydreaming is the first awakening of what we call simulflow. It is
an essential tool of rational thought. With it you can clear the mind for
better thinking.”
```
– Frank Herbert, _Heretics of Dune_

## Usage

Check [example project.clj](./example/project.clj).

~~Put `[lein-simulflow "0.1.0-SNAPSHOT"]` into the `:plugins` vector of your
project.clj.~~

```bash
$ lein simulflow
```

## Overview

While doing modern project with both Clojure and ClojureScript your workflow
might require running several lein tasks parallel.
This means you would have to open several terminals and run tasks like following:

```bash
$ lein less auto # or gulp, compass, etc.
$ lein cljx auto
$ lein cljsbuild auto dev # or lein fighweel dev
$ lein midje :autotest
```

Or you could use [lein-pdo](https://github.com/Raynes/lein-pdo):

```bash
$ lein cljx once # Because pdo might run cljsbuild before cljx is ready
$ lein pdo cljx once, cljsbuild auto dev, less auto, midje :autotest
```

Lein-simulflow will run your long running tasks in in one tasks and takes
care of task dependencies:

```bash
$ lein simulflow
```

The difference between those is not only the number of commands to start the
development env but also number of JVM's they launch. Each lein task will
start a JVM to run the lein and many plugins (e.g. cljsbuild, cljs, midje, repl)
start another JVM to run code in envinroment of the project.
`lein trampoline` could be used to stop the lein JVM after the project JVM has
started.

|   | Number of JVM's |
|---| --------------- |
| Plain      | 7 |
| Trampoline | 4 |
| Pdo        | 4 |
| Simulflow  | 2, 1 _with trampoline_ |

*Note:* It could be that running all plugins in one JVM causes problems.

![Screenshot](./screenshot.png)

For notes about implementation and how this relates to other, similar,
plugins check [this](./doc/notes.md).

## Features

- Works only with custom [wrappers](./support/src/simulflow/wrappers.clj):
  - Cljsbuild, cljx, less
- Watch for file changes using Java 7 API (uses inotify or similar OS provded API)
  - Means also that not every plugin has to implement file watching again
- Tasks can depend on other tasks
  - Cljsbuild requires that Cljx has written the cljs sources
- Tasks run in parallel where possible
  - Eg. cljx and less
- Prepend output from tasks with the task name (by capturing \*out\*)
- Runs all tasks in one project JVM
  - Saves memory in comparison to running e.g. lein cljx and lein cljsbuild seperately

## TODO

- [ ] Test how well the `java.nio.file.WatchService` works for OS X

## NOTES

- Might be this feature should be built into Leinigen and plugins would
implement some API to provide long running tasks.

## Contact

Ping `Deraen` on Freenode irc.

I would like to hear if you have thoughts about how to solve the problem
of many long running lein tasks, about this implementation or if you
think that that the problem is non-existent.
