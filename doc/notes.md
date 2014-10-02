# Notes

## Requirements

- Run multiple lein tasks on one process
- Describe how tasks depend on each other so they can be (optionally?) run parallel
- The plugin should take care of listening for file changes
  - Should use OS provided file notify service through Java NIO API instead of polling for changes
  - Currently every lein plugin implements the file change watching themselves...
- Following tasks should work:
  - [Cljx](https://github.com/lynaghk/cljx/)
  - [Midje](https://github.com/marick/lein-midje)
  - [Cljsbuild](https://github.com/emezeske/lein-cljsbuild)
  - [Less](https://github.com/montoux/lein-less)
- Support LiveReload of JS and CSS
  - Maybe through [Figwheel](https://github.com/bhauman/lein-figwheel)

## Existing implementations

There are already some lein plugins which seek to implement the same functionality.

Name | Notes
-----|------
[lein-pdo](https://github.com/Raynes/lein-pdo)         | <ul><li>Every task responsible for listening for file changes</li><li>Not possible to define that Cljsbuild can be run only after Cljx is finished</li></ul>
[lein-watch](https://github.com/runoshun/lein-watch)   | <ul><li>Uses watchtower to poll for file changes</li></ul>

## Libraries which might help

### File changes

Name | Notes
-----|------
[dirwatch](https://github.com/juxt/dirwatch)         | Simple
[ojo](https://github.com/rplevy/ojo)                 | Advanced
[nio.file](https://github.com/ToBeReplaced/nio.file) |
[nio2](https://github.com/juergenhoetzel/clj-nio2)   |

## Parallelization

- Start up
  1. Cljx, less, css
  1. After cljx is finished: cljs, midje
- Cljs source is changed
  1. Cljs
  2. After result JS is written, livereload
- Cljx source is changed
  1. Cljx
  2. After cljx is finished: cljs, midje
  3. After cljs has written JS: livereload

- Should maybe take example from Gulp
- File events received during execution of tasks should be buffered ([gulp-batch](https://github.com/floatdrop/gulp-batch))
