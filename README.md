# bones

A work-in-progress CQRS/Rest frameworky thing. Backed by [Onyx](https://github.com/onyx-platform/onyx).
Basically an implementation of the design mentioned here: [From REST to CQRS](https://www.youtube.com/watch?v=qDNPQo9UmJA)


## Usage

See `src/userspace/core.clj` for intended usage example


## So far so good

1. First create some users in memory with this line `#_(map create-user users)` in the `http.clj` file
2. Then start the system by evaluating `(system/start-system sys)` in the `userspace/core.clj` file
3. Then login with "jerry:jerry" as below to get a token
4. Then setup an SSE event listener
5. Then post to a command
6. See the output on the event listener


```bash

curl localhost:3000/login -X POST -H "Accept: application/edn" -H "Content-Type: application/edn" -d '{:username "jerry" :password "jerry"}'

{:token "eyJhbGciOiJBMjU2S1ciLCJ0eXAiOiJKV1MiLCJlbmMiOiJBMTI4R0NNIn0.3P4Xc_6tWAvituAEjfoL_E6XQBdMj-dj.k5y63h1m8TaEq9z4.mGHxq44UDhdGImxa3uGePgH24PNp_FqNhPhesogii2McEEQUInOoW6z4geyoz7AMsp6YrXlakQ.zdCqFcxi6vcYDXayi-RmpQ"}bones (master *)>
```

```bash

curl localhost:3000/api/events?topic=userspace.jobs..wat-output -v -H "AUTHORIZATION: Token eyJhbGciOiJBMjU2S1ciLCJ0eXAiOiJKV1MiLCJlbmMiOiJBMTI4R0NNIn0.3P4Xc_6tWAvituAEjfoL_E6XQBdMj-dj.k5y63h1m8TaEq9z4.mGHxq44UDhdGImxa3uGePgH24PNp_FqNhPhesogii2McEEQUInOoW6z4geyoz7AMsp6YrXlakQ.zdCqFcxi6vcYDXayi-RmpQ"

*   Trying ::1...
* Connected to localhost (::1) port 3000 (#0)
> GET /api/events?topic=userspace.jobs..wat-output HTTP/1.1
> Host: localhost:3000
> User-Agent: curl/7.43.0
> Accept: */*
> AUTHORIZATION: Token eyJhbGciOiJBMjU2S1ciLCJ0eXAiOiJKV1MiLCJlbmMiOiJBMTI4R0NNIn0.3P4Xc_6tWAvituAEjfoL_E6XQBdMj-dj.k5y63h1m8TaEq9z4.mGHxq44UDhdGImxa3uGePgH24PNp_FqNhPhesogii2McEEQUInOoW6z4geyoz7AMsp6YrXlakQ.zdCqFcxi6vcYDXayi-RmpQ
>
< HTTP/1.1 200 OK
< Content-Type: text/event-stream
< Cache-Control: no-cache
< Server: Aleph/0.4.0
< Connection: Keep-Alive
< Date: Thu, 31 Dec 2015 12:34:28 GMT
< transfer-encoding: chunked
<
event: userspace.jobs..wat-output
data: {:processed true, :input {:weight-kg 0, :name "string", :_kafka-key 2}, :output {:a "a hammer"}}

```


```bash
curl -X POST --header "Content-Type: application/json" --header "Accept: application/json" --header "AUTHORIZATION: Token eyJhbGciOiJBMjU2S1ciLCJ0eXAiOiJKV1MiLCJlbmMiOiJBMTI4R0NNIn0.3P4Xc_6tWAvituAEjfoL_E6XQBdMj-dj.k5y63h1m8TaEq9z4.mGHxq44UDhdGImxa3uGePgH24PNp_FqNhPhesogii2McEEQUInOoW6z4geyoz7AMsp6YrXlakQ.zdCqFcxi6vcYDXayi-RmpQ" -d "{
  \"message\": {
    \"weight-kg\": 0,
    \"name\": \"string\"
  }
  }" "http://localhost:3000/api/command/userspace.jobs..wat"

{"topic":"userspace.jobs..wat-input","partition":0,"offset":1}

```


## License

Copyright © 2015 ct

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
