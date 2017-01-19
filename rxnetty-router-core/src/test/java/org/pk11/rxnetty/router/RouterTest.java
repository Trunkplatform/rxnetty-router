package org.pk11.rxnetty.router;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.reactivex.netty.protocol.http.client.HttpClientResponse;
import io.reactivex.netty.protocol.http.server.HttpServer;
import io.reactivex.netty.protocol.http.server.HttpServerRequest;
import io.reactivex.netty.protocol.http.server.HttpServerResponse;
import io.reactivex.netty.protocol.http.server.RequestHandler;
import io.reactivex.netty.protocol.http.server.file.ClassPathFileRequestHandler;
import org.junit.Assert;
import org.junit.Test;
import rx.Observable;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static io.reactivex.netty.protocol.http.client.HttpClient.newClient;
import static org.pk11.rxnetty.router.Dispatch.using;
import static org.pk11.rxnetty.router.Dispatch.withParams;
import static rx.Observable.just;

public class RouterTest {

	public static class HelloHandler implements RequestHandler<ByteBuf, ByteBuf> {
		public Observable<Void> handle(HttpServerRequest<ByteBuf> request, HttpServerResponse<ByteBuf> response) {
			response.setStatus(HttpResponseStatus.OK);
			return response.writeString(just("Hello!"));
		}
	}

	public static class Handler404 implements RequestHandler<ByteBuf, ByteBuf> {
		public Observable<Void> handle(HttpServerRequest<ByteBuf> request, HttpServerResponse<ByteBuf> response) {
			response.setStatus(HttpResponseStatus.NOT_FOUND);
			return response.writeString(just("Not found!"));
		}
	}

	public HttpServer<ByteBuf, ByteBuf> newServer() {
		return HttpServer.newServer().start(
			using(
				new Router<ByteBuf, ByteBuf>()
					.GET("/hello", new HelloHandler())
					.GET("/article/:id", withParams( (params, request, response)->{
						response.setStatus(HttpResponseStatus.OK);
						return response.writeString(just("params:"+ params.get("id")));
					}))
					.GET("/public/:*", new ClassPathFileRequestHandler("www"))
					.notFound(new Handler404())
			)
		);
	}

	@Test
	public void shouldReturnHello() throws Exception {
		final CountDownLatch finishLatch = new CountDownLatch(1);
		HttpServer<ByteBuf, ByteBuf> server = newServer();

		HttpClientResponse<ByteBuf> response = newClient("localhost", server.getServerPort())
			.createGet("/hello")
			.doAfterTerminate(finishLatch::countDown)
			.toBlocking()
			.toFuture()
			.get(10, TimeUnit.SECONDS);
		finishLatch.await(1, TimeUnit.MINUTES);
		Assert.assertTrue(response.getStatus().code() == 200);
		server.shutdown();
	}

	@Test
	public void shouldReturnAsset() throws Exception {
		final CountDownLatch finishLatch = new CountDownLatch(1);
		HttpServer<ByteBuf, ByteBuf> server = newServer();

		HttpClientResponse<ByteBuf> response = newClient("localhost", server.getServerPort())
			.createGet("/public/index.html")
			.finallyDo(
				() ->
					finishLatch.countDown()
			)
			.toBlocking()
			.toFuture()
			.get(10, TimeUnit.SECONDS);
		finishLatch.await(1, TimeUnit.MINUTES);
		Assert.assertTrue(response.getStatus().code() == 200);
		server.shutdown();
	}

	@Test
	public void shouldCaptureParam() throws Exception {
		HttpServer<ByteBuf, ByteBuf> server = newServer();

		final List<String> result = new ArrayList<>();
		newClient("localhost", server.getServerPort())
			.createGet("/article/yay")
			.flatMap(
				response ->
					response.getContent().map(
						byteBuf ->
							byteBuf.toString(Charset.defaultCharset())
					)
			)
			.toBlocking()
			.forEach(t1 -> result.add(t1));
		Assert.assertTrue(result.get(0).equals("params:yay"));
		server.shutdown();
	}

	@Test
	public void shouldReturn404ForWrongAssetLink() throws Exception {
		final CountDownLatch finishLatch = new CountDownLatch(1);
		HttpServer<ByteBuf, ByteBuf> server = newServer();

		HttpClientResponse<ByteBuf> response = newClient("localhost", server.getServerPort())
				.createGet("/public/index.html1")
			.finallyDo(
				() ->
					finishLatch.countDown()
			)
			.toBlocking()
			.toFuture()
			.get(10, TimeUnit.SECONDS);
		finishLatch.await(1, TimeUnit.MINUTES);
		Assert.assertTrue(response.getStatus().code() == 404);
		server.shutdown();
	}

	@Test
	public void shouldReturn404ForWrongResource() throws Exception {
		final CountDownLatch finishLatch = new CountDownLatch(1);
		HttpServer<ByteBuf, ByteBuf> server = newServer();

		HttpClientResponse<ByteBuf> response = newClient("localhost", server.getServerPort())
				.createGet("sdfsdfd").finallyDo(
				() ->
					finishLatch.countDown()
			)
			.toBlocking()
			.toFuture()
			.get(10, TimeUnit.SECONDS);
		finishLatch.await(1, TimeUnit.MINUTES);
		Assert.assertTrue(response.getStatus().code() == 404);
		server.shutdown();
	}

	@Test
	public void shouldRegisterNewRoutes() throws Exception {
		final CountDownLatch finishLatch = new CountDownLatch(1);
		Routable<ByteBuf, ByteBuf> helloRoutable = router -> router.GET("/hello", new HelloHandler());
		HttpServer<ByteBuf, ByteBuf> server = HttpServer.newServer().start(
			using(
				new Router<ByteBuf, ByteBuf>()
					.register(helloRoutable)
			)
		);

		HttpClientResponse<ByteBuf> response = newClient("localhost", server.getServerPort())
			.createGet("/hello")
			.finallyDo(
				() ->
					finishLatch.countDown()
			)
			.toBlocking()
			.toFuture()
			.get(10, TimeUnit.SECONDS);
		finishLatch.await(1, TimeUnit.MINUTES);
		Assert.assertTrue(response.getStatus().code() == 200);
		server.shutdown();
	}
}
