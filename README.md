# bones

A work-in-progress CQRS/Rest frameworky thing. Backed by [Onyx](https://github.com/onyx-platform/onyx).
Basically an implementation of the design mentioned here: [From REST to CQRS](https://www.youtube.com/watch?v=qDNPQo9UmJA)

## Prerequisites

Leiningen 2.6.1

Leiningen plugin: git-deps
Add this to your `~/.lein/profiles.clj`
```
{:user
 {:plugins [
            [lein-git-deps "0.0.1-SNAPSHOT"]
```

Required Services:
- Riak
- Redis

These services are managed and run on or forked from the java process started by
this project in development mode:
- Aeron
- Zookeeper
- Kafka


## Usage

See `src/userspace/core.clj` for intended usage example


## So far so good
After starting the repl
### Swagger UI

visit `localhost:3000`


## UI
### Testing the form
After starting the repl

1. `(bootup)` ;; starts onyx for back end
2. `(start)` ;; starts figwheel for front end
3. visit localhost:3000 to see the swagger api
4. or go straight to the spa at localhost:3449
5. login with "jerry:jerry"
6. click "Yes" to see that the counter works
7. click "Add who" fill in some values and click submit
8. see the state of the form go through these phases `new -> received -> processed`. It will be pretty fast. If you don't see `proccessed` there was a problem.

## Command Line version (for looks only)

1. `(bootup)` ;; starts onyx for back end
2. `(start)` ;; starts figwheel for front end
3. Then login with "jerry:jerry" as below to get a token
4. Then setup an SSE event listener
5. Then post to a command
6. See the output on the event listener

Get an api token using seed data
```bash

curl localhost:3000/login -X POST -H "Accept: application/edn" -H "Content-Type: application/edn" -d '{:username "jerry" :password "jerry"}'

{:token "eyJhbGciOiJBMjU2S1ciLCJ0eXAiOiJKV1MiLCJlbmMiOiJBMTI4R0NNIn0.3P4Xc_6tWAvituAEjfoL_E6XQBdMj-dj.k5y63h1m8TaEq9z4.mGHxq44UDhdGImxa3uGePgH24PNp_FqNhPhesogii2McEEQUInOoW6z4geyoz7AMsp6YrXlakQ.zdCqFcxi6vcYDXayi-RmpQ"}
export TOKEN=eyJhbGciOiJBMjU2S1ciLCJ0eXAiOiJKV1MiLCJlbmMiOiJBMTI4R0NNIn0.3P4Xc_6tWAvituAEjfoL_E6XQBdMj-dj.k5y63h1m8TaEq9z4.mGHxq44UDhdGImxa3uGePgH24PNp_FqNhPhesogii2McEEQUInOoW6z4geyoz7AMsp6YrXlakQ.zdCqFcxi6vcYDXayi-RmpQ
```

-Create an SSE connection and listen for the output of onyx jobs on a kafka topic-
This isn't supported anymore. I left it here so you could see what the data
looks like. We're using a Websocket connection to Redis instead and I don't have
a command line version of that connection.

```bash

curl localhost:3000/api/events?topic=userspace.jobs-output -v -H "AUTHORIZATION: Token ${TOKEN}"
*   Trying ::1...
* Connected to localhost (::1) port 3000 (#0)
> GET /api/events?topic=userspace.jobs-output HTTP/1.1
> Host: localhost:3000
> User-Agent: curl/7.43.0
> Accept: */*
> AUTHORIZATION: Token eyJhbGciOiJBMjU2S1ciLCJ0eXAiOiJKV1MiLCJlbmMiOiJBMTI4R0NNIn0.viPsYU4tiIbiMw8cWG2K_XxvrWxPd3-4.gd1yeetv-_LfhOG5.tcVTc6dTcZMjTja6MrAbSS7rzYtlnJr4ddrG6NggaImemUROMmHjTKwhGybqAaYYbgf42K4vfw.16eGt0IpAW1Y_FleXdBtyg
>
< HTTP/1.1 200 OK
< Content-Type: text/event-stream
< Cache-Control: no-cache
< Access-Control-Allow-Origin: http://localhost:3449
< Access-Control-Allow-Headers: Content-Type,AUTHORIZATION
< Access-Control-Allow-Methods: GET,POST,OPTIONS
< Access-Control-Allow-Credentials: true
< Server: Aleph/0.4.0
< Connection: Keep-Alive
< Date: Fri, 05 Feb 2016 17:35:23 GMT
< transfer-encoding: chunked
<
data: {:command :userspace.jobs/wat, :job-sym :userspace.jobs/wat, :uuid nil, :output {:a "a hammer"}, :input {:weight-kg 500, :name "hello"}}

```

Post a command and see that it has a kafka offset
```bash
curl -X POST --header "Content-Type: application/json" --header "Accept: application/json" --header "AUTHORIZATION: Token ${TOKEN}" -d "{
  \"message\": {
    \"weight-kg\": 500,
    \"name\": \"hello\"
  }
  }" "http://localhost:3000/api/command/userspace.jobs..wat"

 {"topic":"userspace.jobs..wat-input","partition":0,"offset":1}

```

### Testing CORS (for notes only)
```
curl 'http://localhost:3000/login' -X OPTIONS -H 'Access-Control-Request-Method: POST' -H 'Origin: http://localhost:3449' -H 'Accept-Encoding: gzip, deflate, sdch' -H 'Accept-Language: en-US,en;q=0.8' -H 'User-Agent: Mozilla/5.0 (Macintosh; Intel Mac OS X 10_10_5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/47.0.2526.106 Safari/537.36' -H 'Accept: */*' -H 'Referer: http://localhost:3449/' -H 'Connection: keep-alive' -H 'DNT: 1' -H 'Access-Control-Request-Headers: content-type' --compressed -v
```

### Errors

Hopefully you will be able to see the full lifecycle of a
request to the `:processed` state. If you do see errors the only answer right I
have right now is to restart the whole repl.



## Intended Design
[![Precursor](https://precursorapp.com/document/Bones-Intended-Architecture-17592202986237.svg?auth-token=)](https://precursorapp.com/document/Bones-Intended-Architecture-17592202986237)




## clojurescript testing

    lein doo phantom test


## License

Copyright © 2015 ct

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
