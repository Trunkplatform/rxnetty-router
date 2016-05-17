rxnetty-router
==============

rxnetty-router-core [ ![Download](https://api.bintray.com/packages/trunkplatform/trunk-java-oss/rxnetty-router-core/images/download.svg) ](https://bintray.com/trunkplatform/trunk-java-oss/rxnetty-router-core/_latestVersion)

rxnetty-router-cors [ ![Download](https://api.bintray.com/packages/trunkplatform/trunk-java-oss/rxnetty-router-cors/images/download.svg) ](https://bintray.com/trunkplatform/trunk-java-oss/rxnetty-router-cors/_latestVersion)

[![Build Status](https://snap-ci.com/Trunkplatform/rxnetty-router/branch/master/build_image)](https://snap-ci.com/Trunkplatform/rxnetty-router/branch/master)

A tiny HTTP router for [RxNetty] (https://github.com/ReactiveX/RxNetty). 

rxnetty-router currently requires java8 and it's using [jauter] (https://github.com/sinetja/jauter) under the hood.

rxnetty-router-cors supplies a minimal CORS implementation. See
[DispatchTest](https://github.com/Trunkplatform/rxnetty-router/blob/master/rxnetty-router-cors/src/test/java/org/pk11/rxnetty/router/cors/DispatchTest.java)
for details

How to Install
==============
maven:
```
<repositories>
    TBD
</repositories>

<dependencies>
    <dependency>
        <groupId>org.pk11.rxnetty</groupId>
        <artifactId>rxnetty-router-core</artifactId>
        <version>1.0.0</version>
    </dependency>
</dependencies>
<dependencies>
    <dependency>
        <groupId>org.pk11.rxnetty</groupId>
        <artifactId>rxnetty-router-cors</artifactId>
        <version>1.0.0</version>
    </dependency>
</dependencies>
```

Core Example
=======

```java
import static org.pk11.rxnetty.router.Dispatch.using;
import static org.pk11.rxnetty.router.Dispatch.withParams;
(...)
HttpServer<ByteBuf, ByteBuf> server = HttpServer.newServer().start(
  using(
    new Router<ByteBuf, ByteBuf>()
      .GET("/hello", new HelloHandler())
      .GET("/article/:id", withParams((params, request, response)->{
        response.setStatus(HttpResponseStatus.OK);
        response.writeString("params:"+ params.get("id"));
        return response.close();
      }))
      .GET("/public/:*", new ClassPathFileRequestHandler("www"))
      .notFound(new Handler404())
  )
);

```

See [RouterTest](https://github.com/Trunkplatform/rxnetty-router/blob/master/rxnetty-router-core/src/test/java/org/pk11/rxnetty/router/RouterTest.java) for a full example.

CORS Example
=======

```java
import org.pk11.rxnetty.router.cors.Dispatch.CorsSettings;
import static org.pk11.rxnetty.router.cors.Dispatch.usingCors;
import static org.pk11.rxnetty.router.Dispatch.withParams;
(...)
HttpServer<ByteBuf, ByteBuf> server = HttpServer.newServer().start(
  usingCors(
    new CorsSettings(),
    new Router<ByteBuf, ByteBuf>()
      .register(router -> router.POST("/hello", new HelloHandler()))
      .GET("/hello", new HelloHandler())
      .GET("/article/:id", withParams((params, request, response) -> {
        response.setStatus(HttpResponseStatus.OK);
        response.writeString("params:"+ params.get("id"));
        return response.close();
      }))
      .GET("/public/:*", new ClassPathFileRequestHandler("www"))
      .notFound(new Handler404())
  )
);

```

See [DispatchTest](https://github.com/Trunkplatform/rxnetty-router/blob/master/rxnetty-router-cors/src/test/java/org/pk11/rxnetty/router/cors/DispatchTest.java) for a full example.

