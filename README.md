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

- [bones.http](https://github.com/teaforthecat/bones-http) CQRS WebServer
- [bones.client](https://github.com/teaforthecat/bones-client) CLJS Client to bones.http
- [bones.editable](https://github.com/teaforthecat/bones-editable) Re-Frame form system, intefaces with bones.client
- [bones.stream](https://github.com/teaforthecat/bones-stream) Simple Onyx Pipeline, backend to bones.http
- [bones.conf](https://github.com/teaforthecat/bones-conf) Merges configuration files, binds above components

##### Examples:

- [todomvc](https://github.com/teaforthecat/bones-todomvc) 
_front-end only_

  This todomvc, quite possibly, has the fewest lines.

- [app using kafka as db](https://github.com/teaforthecat/weather-report) 
_all components combined_

  [Demo Video (no audio, sorry)](https://www.youtube.com/watch?v=dGQRDSRACB4)
  Be sure to check this app out, it has been the target app that these libraries
  have been built around. It mostly resembles what an app would look like.
  


##### In play:

The design goal is to basically combine these awesome projects.

- [re-frame](https://github.com/Day8/re-frame).
- [yada](https://github.com/juxt/yada).
- [Onyx](https://github.com/onyx-platform/onyx).

They share the values of data streaming and data as configuration; data all the
way down, so to speak. So, you might ask, why not just use them directly? The
intent of this framework is to provide sensible and helpful defaults, minimize
the required configuration, and ensure those projects work well together.
Hopefully, providing enough benefit, while also getting out of the way.


##### Influenced by:

- [From REST to CQRS](https://www.youtube.com/watch?v=qDNPQo9UmJA)
- [Designing with Data](https://www.youtube.com/watch?v=kP8wImz-x4w)
- [Everything Will Flow](https://www.youtube.com/watch?v=1bNOO3xxMc0)
- [Inside Onyx](https://www.youtube.com/watch?v=KVByn_kp2fQ)

##### Aspiring to be like:

- [Hoplon](http://hoplon.io/)
- [Untangled](http://untangled-web.github.io/untangled/)


The plan is for this frameworky thing to embrace systems by offering support
for Kafka and Redis; two integral parts of of this web app architecture. It
would make sense to help with the provisioning of these systems, so maybe
another library could come about doing that.




## Architecture


![Bones Architecture](https://github.com/teaforthecat/bones/blob/e37d984b7b04a54c52603fb3ef9aa05a3c467fc8/Bones%20Architecture%20-%20Reactive%20Stack.png)


## License

Copyright © 2015 Chris Thompson

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
