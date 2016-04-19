package org.pk11.rxnetty.router;

import io.netty.handler.codec.http.HttpMethod;
import io.reactivex.netty.protocol.http.server.RequestHandler;

/**
 * Creates a jauter.Router using netty's HttpMethod
 */
public class Router<I, O> extends
		jauter.Router<HttpMethod, RequestHandler<I, O>, Router<I, O>> {
	@Override
	protected Router<I, O> getThis() {
		return this;
	}

	@Override
	protected HttpMethod CONNECT() {
		return HttpMethod.CONNECT;
	}

	@Override
	protected HttpMethod DELETE() {
		return HttpMethod.DELETE;
	}

	@Override
	protected HttpMethod GET() {
		return HttpMethod.GET;
	}

	@Override
	protected HttpMethod HEAD() {
		return HttpMethod.HEAD;
	}

	@Override
	protected HttpMethod OPTIONS() {
		return HttpMethod.OPTIONS;
	}

	@Override
	protected HttpMethod PATCH() {
		return HttpMethod.PATCH;
	}

	@Override
	protected HttpMethod POST() {
		return HttpMethod.POST;
	}

	@Override
	protected HttpMethod PUT() {
		return HttpMethod.PUT;
	}

	@Override
	protected HttpMethod TRACE() {
		return HttpMethod.TRACE;
	}

}
