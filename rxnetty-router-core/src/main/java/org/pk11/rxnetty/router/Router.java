package org.pk11.rxnetty.router;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.stream.Collectors;

import io.netty.handler.codec.http.HttpMethod;
import io.reactivex.netty.protocol.http.server.RequestHandler;

/**
 * Creates a jauter.Router using netty's HttpMethod
 */
public class Router<I, O> extends jauter.Router<HttpMethod, RequestHandler<I, O>, Router<I, O>> {

	private static final Collection<HttpMethod> ALL_METHODS = Collections.unmodifiableList(
		Arrays.asList(
			HttpMethod.CONNECT,
			HttpMethod.DELETE,
			HttpMethod.GET,
			HttpMethod.HEAD,
			HttpMethod.PATCH,
			HttpMethod.POST,
			HttpMethod.PUT,
			HttpMethod.TRACE
		)
	);

	public Collection<HttpMethod> getMethodsFor(String path) {
		if (anyMethodRouter.route(path) != null) {
			return ALL_METHODS;
		}
		return routers.entrySet().stream()
			.filter(e -> e.getValue().route(path) != null)
			.map(e -> e.getKey())
			.collect(Collectors.toList());
	}

	/**
	 * Allow a Routable to inject routing information into the route at runtime,
   * such as from a data- or plugin-generated routes.
   *
	 * @return this router with the changes from the Routable applied
   */
	public Router<I, O> register(Routable<I, O> routable) {
		routable.registerWith(this);
		return getThis();
	}

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
