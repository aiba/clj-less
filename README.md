# clj-less

A Clojure library for compiling less files to CSS.

It reuses a rhino context so subsequent compiles are fast.

All calls to clj-less.core/less are synchronized around a single
atom, so they are potentially blocking.

Some parts ported from java to clojure from the official Java less
compiler: https://github.com/marceloverdijk/lesscss-java

## Usage

FIXME

## License

Copyright © 2012 Aaron Iba

Distributed under the Eclipse Public License, the same as Clojure.
