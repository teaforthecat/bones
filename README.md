# ![icon](https://cdn.rawgit.com/teaforthecat/bones/master/icon.svg)


A work-in-progress CQRS/Rest frameworky thing.

The goal is to provide an easy way to get started writing clojure apps that will
be immediately scalable. Embracing async and streaming throughout, bottlenecks and
processing complications are mitigated. Embracing a data-oriented API the user
is empowered to manage a small or massive project _(theoretically; there hasn't
been a massive project built with it, yet ;)_.

The components of this framework are designed to be stand-alone libraries that
are also designed to fit together. The intent is for the programmer to focus on
data specification and behavior, while the framework handles the plumbing
aspects. These libraries will connect the user's handler functions with some data
validation along the way.

There could potentially be a library that composes the component libraries. For
now, they assist with the plumbing of building an app.


##### These are the components:

- [bones.http](https://github.com/teaforthecat/bones-http)
- [bones.client](https://github.com/teaforthecat/bones-client)
- [bones.editable](https://github.com/teaforthecat/bones-editable)
- [bones.conf](https://github.com/teaforthecat/bones-conf)

##### Examples:

- [todomvc](https://github.com/teaforthecat/bones-todomvc) 
_front-end only_

  This todomvc, quite possibly, has the fewest lines.

- [app using kafka as db](https://github.com/teaforthecat/weather-report) 
_all components combined_

  Be sure to check this app out, it has been the target app that these libraries
  have been built around. It mostly resembles what an app would look like.


##### In play:

The design goal is to basically combine these libraries and add a data api on top:

- [re-frame](https://github.com/Day8/re-frame).
- [yada](https://github.com/juxt/yada).
- [Onyx](https://github.com/onyx-platform/onyx).


##### Influenced by:

- [From REST to CQRS](https://www.youtube.com/watch?v=qDNPQo9UmJA)
- [Designing with Data](https://www.youtube.com/watch?v=kP8wImz-x4w)
- [Everything Will Flow](https://www.youtube.com/watch?v=1bNOO3xxMc0)

##### Aspiring to be:

- [Hoplon](http://hoplon.io/)
- [Untangled](http://untangled-web.github.io/untangled/)


##### Yet to be built:

There does exist a tiny library called `bones.jobs` but I'd like to expand that
to support the combination of Onyx and the `bones.http` library. (see diagram below)

- [bones.stream](#) (Onyx Wrapper)

The plan is for this frameworky thing to embrace systems by offering support
for Kafka and Redis; two integral parts of of this web app architecture. It
would make sense to help with the provisioning of these systems, so maybe
another library could come about doing that.




## Architecture


![Bones Architecture](https://precursorapp.com/document/Bones-Architecture-17592205334814.svg?auth-token=)


## License

Copyright © 2015 Chris Thompson

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
