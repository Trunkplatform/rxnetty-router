package org.pk11.rxnetty.router.cors;

import java.nio.charset.Charset;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.TimeUnit;

import org.junit.Test;
import org.pk11.rxnetty.router.Router;
import org.pk11.rxnetty.router.RouterTest;
import org.pk11.rxnetty.router.cors.CorsDispatcher.CorsSettings;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.reactivex.netty.protocol.http.client.HttpClientRequest;
import io.reactivex.netty.protocol.http.client.HttpClientResponse;
import io.reactivex.netty.protocol.http.server.HttpServer;

import static io.reactivex.netty.protocol.http.client.HttpClient.newClient;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class CorsDispatcherTest {

  public HttpServer<ByteBuf, ByteBuf> newServer(CorsSettings settings) {
    return HttpServer.newServer().start(
      CorsDispatcher.usingCors(
        settings,
        new Router<ByteBuf, ByteBuf>()
          .GET("/hello", new RouterTest.HelloHandler())
          .notFound(new RouterTest.Handler404())
      )
    );
  }

  private HttpClientRequest<ByteBuf, ByteBuf> getClient(HttpServer<ByteBuf, ByteBuf> server) {
    return newClient("localhost", server.getServerPort())
      .readTimeOut(30, TimeUnit.SECONDS)
      .createGet("/hello");
  }

  private HttpClientRequest<ByteBuf, ByteBuf> optionsClient(HttpServer<ByteBuf, ByteBuf> server) {
    return newClient("localhost", server.getServerPort())
      .readTimeOut(30, TimeUnit.SECONDS)
      .createOptions("/hello");
  }

  private String getContent(HttpClientResponse<ByteBuf> response) {
    return response.getContent()
      .map(b -> b.toString(Charset.defaultCharset()))
      .reduce("", (acc, s) -> acc + s)
      .toBlocking()
      .single();
  }

  @Test
  public void shouldIgnoreNonCorsRequest() throws Exception {
    HttpServer<ByteBuf, ByteBuf> server = newServer(new CorsSettings());
    HttpClientResponse<ByteBuf> response = getClient(server)
      .toBlocking()
      .first();

    String content = getContent(response);

    assertFalse(response.containsHeader("Access-Control-Allow-Origin"));
    assertFalse(response.containsHeader("Access-Control-Allow-Methods"));
    assertFalse(response.containsHeader("Access-Control-Allow-Headers"));
    assertFalse(response.containsHeader("Access-Control-Allow-Credentials"));
    assertFalse(response.containsHeader("Access-Control-Max-Age"));
    assertFalse(response.containsHeader("Access-Control-Expose-Headers"));

    assertEquals(HttpResponseStatus.OK, response.getStatus());

    assertEquals("Hello!", content);

    server.shutdown();
  }

  @Test
  public void shouldIgnoreCorsRequestWithSameOrigin() throws Exception {
    HttpServer<ByteBuf, ByteBuf> server = newServer(new CorsSettings());
    HttpClientResponse<ByteBuf> response = getClient(server)
      .setHeader("Origin", "http://localhost:" + server.getServerPort())
      .toBlocking()
      .first();

    String content = getContent(response);

    assertFalse(response.containsHeader("Access-Control-Allow-Origin"));
    assertFalse(response.containsHeader("Access-Control-Allow-Methods"));
    assertFalse(response.containsHeader("Access-Control-Allow-Headers"));
    assertFalse(response.containsHeader("Access-Control-Allow-Credentials"));
    assertFalse(response.containsHeader("Access-Control-Max-Age"));
    assertFalse(response.containsHeader("Access-Control-Expose-Headers"));

    assertEquals(HttpResponseStatus.OK, response.getStatus());

    assertEquals("Hello!", content);

    server.shutdown();
  }

  @Test
  public void shouldDefaultToAnyOriginForGivenSimpleRequest() throws Exception {
    HttpServer<ByteBuf, ByteBuf> server = newServer(new CorsSettings());
    HttpClientResponse<ByteBuf> response = getClient(server)
      .setHeader("Origin", "http://foo")
      .toBlocking()
      .first();

    String content = getContent(response);

    assertEquals("*", response.getHeader("Access-Control-Allow-Origin"));

    assertEquals(HttpResponseStatus.OK, response.getStatus());

    assertEquals("Hello!", content);

    server.shutdown();
  }

  @Test
  public void shouldReturnEmptyForMismatchedOriginGivenSimpleRequest() throws Exception {
    HttpServer<ByteBuf, ByteBuf> server = newServer(
      new CorsSettings()
      .allowOrigin("http://bar")
    );
    HttpClientResponse<ByteBuf> response = getClient(server)
      .setHeader("Origin", "http://foo")
      .toBlocking()
      .first();

    String content = getContent(response);

    assertFalse(response.containsHeader("Access-Control-Allow-Origin"));

    assertEquals(HttpResponseStatus.OK, response.getStatus());

    assertEquals("", content);

    server.shutdown();
  }

  @Test
  public void shouldReturnOriginForMatchedOriginGivenSimpleRequest() throws Exception {
    HttpServer<ByteBuf, ByteBuf> server = newServer(
      new CorsSettings()
        .allowOrigin("http://bar")
        .allowOrigin("http://foo")
    );
    HttpClientResponse<ByteBuf> response = getClient(server)
      .setHeader("Origin", "http://foo")
      .toBlocking()
      .first();

    String content = getContent(response);

    assertEquals("http://foo", response.getHeader("Access-Control-Allow-Origin"));

    assertEquals(HttpResponseStatus.OK, response.getStatus());

    assertEquals("Hello!", content);

    server.shutdown();
  }

  @Test
  public void shouldDefaultToNoAllowCredentialsGivenSimpleRequest() throws Exception {
    HttpServer<ByteBuf, ByteBuf> server = newServer(new CorsSettings());
    HttpClientResponse<ByteBuf> response = getClient(server)
      .setHeader("Origin", "http://foo")
      .toBlocking()
      .first();

    assertFalse(response.containsHeader("Access-Control-Allow-Credentials"));

    server.shutdown();
  }

  @Test
  public void shouldReturnTrueWhenAllowCredentialsGivenSimpleRequest() throws Exception {
    HttpServer<ByteBuf, ByteBuf> server = newServer(
      new CorsSettings()
        .allowCredential(true)
    );
    HttpClientResponse<ByteBuf> response = getClient(server)
      .setHeader("Origin", "http://foo")
      .toBlocking()
      .first();

    assertEquals("true", response.getHeader("Access-Control-Allow-Credentials"));

    server.shutdown();
  }

  @Test
  public void shouldDefaultToNotExposeHeadersGivenSimpleRequest() throws Exception {
    HttpServer<ByteBuf, ByteBuf> server = newServer(new CorsSettings());
    HttpClientResponse<ByteBuf> response = getClient(server)
      .setHeader("Origin", "http://foo")
      .toBlocking()
      .first();

    assertFalse(response.containsHeader("Access-Control-Expose-Headers"));

    server.shutdown();
  }

  @Test
  public void shouldReturnSingleExposedHeaderGivenSimpleRequest() throws Exception {
    HttpServer<ByteBuf, ByteBuf> server = newServer(
      new CorsSettings()
        .exposeHeader("header-1")
    );
    HttpClientResponse<ByteBuf> response = getClient(server)
      .setHeader("Origin", "http://foo")
      .toBlocking()
      .first();

    assertEquals("header-1", response.getHeader("Access-Control-Expose-Headers"));

    server.shutdown();
  }

  @Test
  public void shouldReturnMultipleExposedHeadersGivenSimpleRequest() throws Exception {
    HttpServer<ByteBuf, ByteBuf> server = newServer(
      new CorsSettings()
        .exposeHeader("header-1")
        .exposeHeader("header-2")
    );
    HttpClientResponse<ByteBuf> response = getClient(server)
      .setHeader("Origin", "http://foo")
      .toBlocking()
      .first();

    assertEquals("header-1, header-2", response.getHeader("Access-Control-Expose-Headers"));

    server.shutdown();
  }

  @Test
  public void shouldDefaultToAnyOriginForGivenPreflightRequest() throws Exception {
    HttpServer<ByteBuf, ByteBuf> server = newServer(new CorsSettings());
    HttpClientResponse<ByteBuf> response = optionsClient(server)
      .setHeader("Origin", "http://foo")
      .toBlocking()
      .first();

    assertEquals("*", response.getHeader("Access-Control-Allow-Origin"));

    assertEquals(HttpResponseStatus.OK, response.getStatus());

    server.shutdown();
  }

  @Test
  public void shouldReturnEmptyForMismatchedOriginGivenPreflightRequest() throws Exception {
    HttpServer<ByteBuf, ByteBuf> server = newServer(
      new CorsSettings()
        .allowOrigin("http://bar")
    );
    HttpClientResponse<ByteBuf> response = optionsClient(server)
      .setHeader("Origin", "http://foo")
      .toBlocking()
      .first();

    String content = getContent(response);

    assertFalse(response.containsHeader("Access-Control-Allow-Origin"));

    assertEquals(HttpResponseStatus.OK, response.getStatus());

    assertEquals("", content);

    server.shutdown();
  }

  @Test
  public void shouldReturnOriginForMatchedOriginGivenPreflightRequest() throws Exception {
    HttpServer<ByteBuf, ByteBuf> server = newServer(
      new CorsSettings()
        .allowOrigin("http://foo")
        .allowOrigin("http://bar")
    );
    HttpClientResponse<ByteBuf> response = optionsClient(server)
      .setHeader("Origin", "http://foo")
      .toBlocking()
      .first();

    assertEquals("http://foo", response.getHeader("Access-Control-Allow-Origin"));

    assertEquals(HttpResponseStatus.OK, response.getStatus());

    server.shutdown();
  }

  @Test
  public void shouldDefaultToAllMethodsForGivenPreflightRequest() throws Exception {
    HttpServer<ByteBuf, ByteBuf> server = newServer(new CorsSettings());
    HttpClientResponse<ByteBuf> response = optionsClient(server)
      .setHeader("Origin", "http://foo")
      .toBlocking()
      .first();

    assertEquals("*", response.getHeader("Access-Control-Allow-Methods"));

    server.shutdown();
  }

  @Test
  public void shouldReturnSingleMethodGivenPreflightRequest() throws Exception {
    HttpServer<ByteBuf, ByteBuf> server = newServer(
      new CorsSettings()
        .allowMethod(HttpMethod.GET)
    );
    HttpClientResponse<ByteBuf> response = optionsClient(server)
      .setHeader("Origin", "http://foo")
      .toBlocking()
      .first();

    assertEquals("GET", response.getHeader("Access-Control-Allow-Methods"));

    server.shutdown();
  }

  @Test
  public void shouldReturnMultipleMethodsGivenPreflightRequest() throws Exception {
    HttpServer<ByteBuf, ByteBuf> server = newServer(
      new CorsSettings()
        .allowMethod(HttpMethod.GET)
        .allowMethod(HttpMethod.DELETE)
    );
    HttpClientResponse<ByteBuf> response = optionsClient(server)
      .setHeader("Origin", "http://foo")
      .toBlocking()
      .first();

    assertEquals("GET, DELETE", response.getHeader("Access-Control-Allow-Methods"));

    server.shutdown();
  }

  @Test
  public void shouldDefaultToNoAllowHeadersWhenNotRequestedGivenPreflightRequest() throws Exception {
    HttpServer<ByteBuf, ByteBuf> server = newServer(new CorsSettings());
    HttpClientResponse<ByteBuf> response = optionsClient(server)
      .setHeader("Origin", "http://foo")
      .toBlocking()
      .first();

    assertFalse(response.containsHeader("Access-Control-Allow-Headers"));

    server.shutdown();
  }

  @Test
  public void shouldDefaultToEmptyAllowHeadersWhenRequestedGivenPreflightRequest() throws Exception {
    HttpServer<ByteBuf, ByteBuf> server = newServer(new CorsSettings());
    HttpClientResponse<ByteBuf> response = optionsClient(server)
      .setHeader("Access-Control-Request-Headers", "foo")
      .setHeader("Origin", "http://foo")
      .toBlocking()
      .first();

    assertEquals("", response.getHeader("Access-Control-Allow-Headers"));

    server.shutdown();
  }

  @Test
  public void shouldReturnSingleAllowHeaderWhenNotRequestedGivenPreflightRequest() throws Exception {
    HttpServer<ByteBuf, ByteBuf> server = newServer(
      new CorsSettings()
        .allowHeader("header-1")
    );
    HttpClientResponse<ByteBuf> response = optionsClient(server)
      .setHeader("Origin", "http://foo")
      .toBlocking()
      .first();

    assertEquals("header-1", response.getHeader("Access-Control-Allow-Headers"));

    server.shutdown();
  }

  @Test
  public void shouldReturnSingleAllowHeaderWhenRequestedGivenPreflightRequest() throws Exception {
    HttpServer<ByteBuf, ByteBuf> server = newServer(
      new CorsSettings()
        .allowHeader("header-1")
    );
    HttpClientResponse<ByteBuf> response = optionsClient(server)
      .setHeader("Access-Control-Request-Headers", "foo")
      .setHeader("Origin", "http://foo")
      .toBlocking()
      .first();

    assertEquals("header-1", response.getHeader("Access-Control-Allow-Headers"));

    server.shutdown();
  }

  @Test
  public void shouldReturnMultipleAllowHeadersWhenNotRequestedGivenPreflightRequest() throws Exception {
    HttpServer<ByteBuf, ByteBuf> server = newServer(
      new CorsSettings()
        .allowHeader("header-1")
        .allowHeader("header-2")
    );
    HttpClientResponse<ByteBuf> response = optionsClient(server)
      .setHeader("Origin", "http://foo")
      .toBlocking()
      .first();

    assertEquals("header-1, header-2", response.getHeader("Access-Control-Allow-Headers"));

    server.shutdown();
  }

  @Test
  public void shouldReturnMultipleAllowHeadersWhenRequestedGivenPreflightRequest() throws Exception {
    HttpServer<ByteBuf, ByteBuf> server = newServer(
      new CorsSettings()
        .allowHeader("header-1")
        .allowHeader("header-2")
    );
    HttpClientResponse<ByteBuf> response = optionsClient(server)
      .setHeader("Access-Control-Request-Headers", "foo")
      .setHeader("Origin", "http://foo")
      .toBlocking()
      .first();

    assertEquals("header-1, header-2", response.getHeader("Access-Control-Allow-Headers"));

    server.shutdown();
  }

  @Test
  public void shouldDefaultToNoAllowCredentialsGivenPreflightRequest() throws Exception {
    HttpServer<ByteBuf, ByteBuf> server = newServer(
      new CorsSettings()
    );
    HttpClientResponse<ByteBuf> response = optionsClient(server)
      .setHeader("Origin", "http://foo")
      .toBlocking()
      .first();

    assertFalse(response.containsHeader("Access-Control-Allow-Credentials"));

    server.shutdown();
  }

  @Test
  public void shouldReturnTrueWhenAllowCredtialsGivenPreflightRequest() throws Exception {
    HttpServer<ByteBuf, ByteBuf> server = newServer(
      new CorsSettings()
        .allowCredential(true)
    );
    HttpClientResponse<ByteBuf> response = optionsClient(server)
      .setHeader("Origin", "http://foo")
      .toBlocking()
      .first();

    assertEquals("true", response.getHeader("Access-Control-Allow-Credentials"));

    server.shutdown();
  }

  @Test
  public void shouldDefaultToNoMaxAgeGivenPreflightRequest() throws Exception {
    HttpServer<ByteBuf, ByteBuf> server = newServer(
      new CorsSettings()
    );
    HttpClientResponse<ByteBuf> response = optionsClient(server)
      .setHeader("Origin", "http://foo")
      .toBlocking()
      .first();

    assertFalse(response.containsHeader("Access-Control-Max-Age"));

    server.shutdown();
  }

  @Test
  public void shouldReturnGivenMaxAgeInSecondsGivenPreflightRequest() throws Exception {
    HttpServer<ByteBuf, ByteBuf> server = newServer(
      new CorsSettings()
        .maxAge(Duration.of(50, ChronoUnit.MINUTES))
    );
    HttpClientResponse<ByteBuf> response = optionsClient(server)
      .setHeader("Origin", "http://foo")
      .toBlocking()
      .first();

    assertEquals("3000", response.getHeader("Access-Control-Max-Age"));

    server.shutdown();
  }

  @Test
  public void should404OnPreflightWithNoRoute() throws Exception {
    HttpServer<ByteBuf, ByteBuf> server = newServer(new CorsSettings());
    HttpClientResponse<ByteBuf> response = newClient("localhost", server.getServerPort())
      .readTimeOut(30, TimeUnit.SECONDS)
      .createOptions("/foo")
      .setHeader("Origin", "http://foo")
      .toBlocking()
      .first();

    String content = getContent(response);

    assertFalse(response.containsHeader("Access-Control-Allow-Origin"));
    assertFalse(response.containsHeader("Access-Control-Allow-Methods"));
    assertFalse(response.containsHeader("Access-Control-Allow-Headers"));
    assertFalse(response.containsHeader("Access-Control-Allow-Credentials"));
    assertFalse(response.containsHeader("Access-Control-Max-Age"));
    assertFalse(response.containsHeader("Access-Control-Expose-Headers"));

    assertEquals(HttpResponseStatus.NOT_FOUND, response.getStatus());

    assertEquals("Not found!", content);

    server.shutdown();
  }

  @Test
  public void should404OnSimpleWithNoRoute() throws Exception {
    HttpServer<ByteBuf, ByteBuf> server = newServer(new CorsSettings());
    HttpClientResponse<ByteBuf> response = newClient("localhost", server.getServerPort())
      .readTimeOut(30, TimeUnit.SECONDS)
      .createGet("/foo")
      .setHeader("Origin", "http://foo")
      .toBlocking()
      .first();

    String content = getContent(response);

    assertFalse(response.containsHeader("Access-Control-Allow-Origin"));
    assertFalse(response.containsHeader("Access-Control-Allow-Methods"));
    assertFalse(response.containsHeader("Access-Control-Allow-Headers"));
    assertFalse(response.containsHeader("Access-Control-Allow-Credentials"));
    assertFalse(response.containsHeader("Access-Control-Max-Age"));
    assertFalse(response.containsHeader("Access-Control-Expose-Headers"));

    assertEquals(HttpResponseStatus.NOT_FOUND, response.getStatus());

    assertEquals("Not found!", content);

    server.shutdown();
  }

  @Test
  public void shouldGenerateOptionsResponseIfNoOptionsRoute() throws Exception {
    HttpServer<ByteBuf, ByteBuf> server = HttpServer.newServer().start(
      CorsDispatcher.usingCors(
        new CorsSettings(),
        new Router<ByteBuf, ByteBuf>()
          .POST("/hello", new RouterTest.HelloHandler())
          .DELETE("/hello", new RouterTest.HelloHandler())
          .GET("/hello", new RouterTest.HelloHandler())
          .notFound(new RouterTest.Handler404())
      )
    );
    HttpClientResponse<ByteBuf> response = newClient("localhost", server.getServerPort())
      .readTimeOut(30, TimeUnit.SECONDS)
      .createOptions("/hello")
      .setHeader("Origin", "http://foo")
      .toBlocking()
      .first();

    String content = getContent(response);

    assertEquals("DELETE, GET, POST", content);

    server.shutdown();
  }

  @Test
  public void shouldAppendToProvidedOptionsResponse() throws Exception {
    HttpServer<ByteBuf, ByteBuf> server = HttpServer.newServer().start(
      CorsDispatcher.usingCors(
        new CorsSettings(),
        new Router<ByteBuf, ByteBuf>()
          .GET("/hello", new RouterTest.HelloHandler())
          .POST("/hello", new RouterTest.HelloHandler())
          .OPTIONS("/hello", new RouterTest.HelloHandler())
          .notFound(new RouterTest.Handler404())
      )
    );
    HttpClientResponse<ByteBuf> response = newClient("localhost", server.getServerPort())
      .readTimeOut(30, TimeUnit.SECONDS)
      .createOptions("/hello")
      .setHeader("Origin", "http://foo")
      .toBlocking()
      .first();

    String content = getContent(response);

    assertEquals("Hello!", content);

    server.shutdown();
  }
}
