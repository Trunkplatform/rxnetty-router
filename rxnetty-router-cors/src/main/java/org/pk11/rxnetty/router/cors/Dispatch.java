package org.pk11.rxnetty.router.cors;

import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.util.AsciiString;
import io.reactivex.netty.protocol.http.server.HttpServerRequest;
import io.reactivex.netty.protocol.http.server.HttpServerResponse;
import io.reactivex.netty.protocol.http.server.RequestHandler;
import org.pk11.rxnetty.router.Router;
import rx.Observable;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.pk11.rxnetty.router.Dispatch.using;
import static rx.Observable.just;

public class Dispatch<I, O> implements RequestHandler<I, O> {

  private final CorsSettings settings;
  private final org.pk11.rxnetty.router.Dispatch<I, O> delegate;

  public static <I, O> Dispatch<I, O> usingCors(
    CorsSettings settings,
    Router<I, O> route
  ) {
    Collection<String> optionPaths = route.getPaths();
    optionPaths.forEach(
      path ->
        route.OPTIONS(path, getOptionsHandler(settings, route.getMethodsFor(path)))
    );
    return new Dispatch<>(settings, using(route));
  }

  private static <I, O> RequestHandler<I, O> getOptionsHandler(CorsSettings settings, Collection<HttpMethod> methods) {
    List<String> availableMethods =
      methods.stream().map(HttpMethod::asciiName).map(AsciiString::toString).sorted().collect(Collectors.toList());
    List<String> allowedMethods = new ArrayList<>(availableMethods);
    if (!settings.allowedMethods.isEmpty()) {
      allowedMethods.retainAll(settings.allowedMethods);
    }

    Collections.sort(availableMethods);
    Collections.sort(allowedMethods);

    String availableMethodsString = String.join(", ", availableMethods);
    String allowedMethodsString = String.join(", ", allowedMethods);
    return (request, response) ->
      settings.authorisationHandler.allow(request)
        .defaultIfEmpty(false)
        .flatMap(
          allow -> {
            if (allow) {
              if (isCors(request)) {
                addPreflightOnlyHeaders(request, response, settings, allowedMethodsString);
              }
              settings.headers.entrySet().forEach(
                header ->
                  response.setHeader(header.getKey(), header.getValue())
              );
              return response.writeString(just(availableMethodsString));
            } else {
              response.setStatus(HttpResponseStatus.UNAUTHORIZED);
              return response.sendHeaders();
            }
          }
        );
  }

  private static boolean isCors(HttpServerRequest<?> request) {
    return request.containsHeader("Origin");
  }

  private Dispatch(CorsSettings settings, org.pk11.rxnetty.router.Dispatch<I, O> delegate) {
    this.settings = settings;
    this.delegate = delegate;
  }

  @Override
  public Observable<Void> handle(HttpServerRequest<I> request, HttpServerResponse<O> response) {
    if (isCors(request)) {
      String origin = request.getHeader("Origin");
      if (originNotAllowed(origin)) {
        return response.sendHeaders();
      }
      addSharedHeaders(response, origin);
      addSimpleOnlyHeaders(response);
    }

    return delegate.handle(request, response);
  }

  private void addSimpleOnlyHeaders(HttpServerResponse<O> response) {
    if (settings.exposedHeaders.length() > 0) {
      response.setHeader("Access-Control-Expose-Headers", settings.exposedHeaders);
    }
  }

  private static void addPreflightOnlyHeaders(
    HttpServerRequest<?> request,
    HttpServerResponse<?> response,
    CorsSettings settings,
    String allowedMethodsString
  ) {
    if (settings.maxAge != null) {
      response.setHeader("Access-Control-Max-Age", settings.maxAge.get(ChronoUnit.SECONDS));
    }
    if (request.containsHeader("Access-Control-Request-Headers") || settings.allowedHeaders.length() > 0) {
      response.setHeader("Access-Control-Allow-Headers", settings.allowedHeaders);
    }
    response.setHeader("Access-Control-Allow-Methods", allowedMethodsString);
  }

  private void addSharedHeaders(HttpServerResponse<O> response, String origin) {
    response.setHeader("Access-Control-Allow-Origin", settings.allowedOrigins.isEmpty() ? "*" : origin);

    if (settings.allowCredentials) {
      response.setHeader("Access-Control-Allow-Credentials", "true");
    }
  }

  private boolean originNotAllowed(String origin) {
    return !settings.allowedOrigins.isEmpty() && !settings.allowedOrigins.contains(origin);
  }

  public static class CorsSettings {
    private final boolean allowCredentials;
    private final CharSequence allowedHeaders;
    private final Set<String> allowedMethods;
    private final Set<String> allowedOrigins;
    private final CharSequence exposedHeaders;
    private final Duration maxAge;
    private final Map<String, String> headers;
    private final AuthorisationHandler authorisationHandler;

    public CorsSettings() {
      allowedMethods = Collections.emptySet();
      allowCredentials = false;
      allowedHeaders = "";
      allowedOrigins = Collections.emptySet();
      exposedHeaders = "Cache-Control, " +
        "Content-Language, " +
        "Content-Type, " +
        "Expires, " +
        "Last-Modified, " +
        "Pragma";
      maxAge = null;
      headers = Collections.emptyMap();
      authorisationHandler = request -> just(true);
    }

    private CorsSettings(
      boolean allowCredentials,
      CharSequence allowedHeaders,
      Set<String> allowedMethods,
      Set<String> allowedOrigins,
      CharSequence exposedHeaders,
      Duration maxAge,
      Map<String, String> headers,
      AuthorisationHandler authorisationHandler
    ) {
      this.allowCredentials = allowCredentials;
      this.allowedHeaders = allowedHeaders;
      this.allowedMethods = allowedMethods;
      this.allowedOrigins = allowedOrigins;
      this.exposedHeaders = exposedHeaders;
      this.maxAge = maxAge;
      this.headers = headers;
      this.authorisationHandler = authorisationHandler;
    }

    public CorsSettings allowCredential(boolean newValue) {
      return new CorsSettings(
        newValue,
        allowedHeaders,
        allowedMethods,
        allowedOrigins,
        exposedHeaders,
        maxAge,
        headers,
        authorisationHandler
      );
    }

    public CorsSettings allowHeader(String header) {
      return new CorsSettings(
        allowCredentials,
        allowedHeaders.length() == 0 ? header : allowedHeaders + ", " + header,
        allowedMethods,
        allowedOrigins,
        exposedHeaders,
        maxAge,
        headers,
        authorisationHandler
      );
    }

    public CorsSettings allowMethod(HttpMethod method) {
      Set<String> newAllowedMethods = new HashSet<>();
      newAllowedMethods.addAll(allowedMethods);
      newAllowedMethods.add(method.asciiName().toString());
      return new CorsSettings(
        allowCredentials,
        allowedHeaders,
        newAllowedMethods,
        allowedOrigins,
        exposedHeaders,
        maxAge,
        headers,
        authorisationHandler
      );
    }

    public CorsSettings allowOrigin(String origin) {
      Set<String> newOrigins = new HashSet<>();
      newOrigins.addAll(allowedOrigins);
      newOrigins.add(origin);
      return new CorsSettings(
        allowCredentials,
        allowedHeaders,
        allowedMethods,
        Collections.unmodifiableSet(newOrigins),
        exposedHeaders,
        maxAge,
        headers,
        authorisationHandler
      );
    }

    public CorsSettings exposeHeader(String header) {
      return new CorsSettings(
        allowCredentials,
        allowedHeaders,
        allowedMethods,
        allowedOrigins,
        exposedHeaders.length() == 0 ? header : exposedHeaders + ", " + header,
        maxAge,
        headers,
        authorisationHandler
      );
    }

    public CorsSettings maxAge(Duration newDuration) {
      return new CorsSettings(
        allowCredentials,
        allowedHeaders,
        allowedMethods,
        allowedOrigins,
        exposedHeaders,
        newDuration,
        headers,
        authorisationHandler
      );
    }

    public CorsSettings withHeader(String name, String value) {
      Map<String, String> newHeaders = new HashMap<>(headers);
      newHeaders.put(name, value);
      return new CorsSettings(
        allowCredentials,
        allowedHeaders,
        allowedMethods,
        allowedOrigins,
        exposedHeaders,
        maxAge,
        newHeaders,
        authorisationHandler
      );
    }

    public CorsSettings withAuthorisation(AuthorisationHandler authorisationHandler) {
      return new CorsSettings(
        allowCredentials,
        allowedHeaders,
        allowedMethods,
        allowedOrigins,
        exposedHeaders,
        maxAge,
        headers,
        authorisationHandler
      );
    }
  }

  public interface AuthorisationHandler {
    Observable<Boolean> allow(HttpServerRequest<?> request);
  }
}
