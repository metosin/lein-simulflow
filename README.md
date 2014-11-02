# lein-simulflow [![Travis CI status](https://secure.travis-ci.org/metosin/lein-simulflow.png)](http://travis-ci.org/#!/metosin/lein-simulflow/builds)

Combine several long running ~~lein~~ tasks for leaner workflow.

*NOTE: Very early version, not usable.*<br>
*NOTE: Might be over-engineered.*

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

For now, install [output-to-chan](https://github.com/Deraen/output-to-chan), support and plugin using `lein install`.

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

Lein-simulflow will start your long running tasks with one command and takes
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

|   | Number of JVMs |
|---| --------------- |
| Plain      | 7 |
| Trampoline | 4 |
| Pdo        | 4 |
| Simulflow  | 2, _(1 with trampoline)_ |

*Note: It could be that running all plugins in one JVM causes problems.*

![Screenshot](./screenshot.png)

## Features

- Works only with custom [wrappers](./support/src/simulflow/wrappers.clj):
  - Cljsbuild, cljx, less
- Watch for file changes using Java 7 API (uses inotify or similar OS provided API)
  - Means also that not every plugin has to implement file watching again
- Tasks can depend on other tasks
  - Cljsbuild requires that Cljx has written the cljs sources
- Tasks run in parallel where possible
  - Eg. cljx and less
- Prepend output from tasks with the task name (by capturing `*out*`)
- Runs all tasks in one project JVM
  - Saves memory in comparison to running e.g. lein cljx and lein cljsbuild seperately

## TODO

- [ ] Test how well the `java.nio.file.WatchService` works for OS X
- [ ] Filter file events based on regex (or fn)
  - E.g. to filter out Vim temp files

## NOTES

- Might be this feature should be built into Leiningen and plugins would
implement some API to provide long running tasks.
- If we are already running all tasks on project JVM, we might as well run them from repl...
- Not sure if this best implemented as lein plugin. `project.clj`
is already quite complicated has too much "config magic".
  - I think the situation is similar to [Grunt JS Task Runner](http://gruntjs.com/)
  which many have replaced with [Gulp](http://gulpjs.com/).
    - [Gruntfile](https://github.com/gruntjs/grunt/blob/master/Gruntfile.js).
    Config mostly defined on one big JS map with every plugin having top-level key on config.
    - [gulpfile](https://github.com/gulpjs/gulp/#sample-gulpfilejs).
    Tasks are composited from functions returning transform streams. One can use
    higher-order functions to create parametrized tasks.

## Contact

Ping `Deraen` on Freenode irc.

I would like to hear if you have thoughts about how to solve the problem
of many long running lein tasks, about this implementation or if you
think that the problem is non-existent.
