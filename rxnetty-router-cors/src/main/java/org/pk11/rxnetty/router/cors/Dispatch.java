package org.pk11.rxnetty.router.cors;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import org.pk11.rxnetty.router.Router;

import io.netty.handler.codec.http.HttpMethod;
import io.reactivex.netty.protocol.http.server.HttpServerRequest;
import io.reactivex.netty.protocol.http.server.HttpServerResponse;
import io.reactivex.netty.protocol.http.server.RequestHandler;
import rx.Observable;

import static org.pk11.rxnetty.router.Dispatch.using;
import static rx.Observable.just;

public class Dispatch<I, O> implements RequestHandler<I, O> {

  public static final Collection<HttpMethod> ALL_METHODS = Collections.emptyList();
  public static final Set<String> ANY_HOST = Collections.emptySet();
  public static final Duration DEFAULT_MAX_AGE = Duration.of(10, ChronoUnit.SECONDS);

  private final CorsSettings settings;
  private final Router<I, O> router;
  private final org.pk11.rxnetty.router.Dispatch<I, O> delegate;

  public static <I, O> Dispatch<I, O> usingCors(CorsSettings settings,
                                                Router<I, O> route) {
    return new Dispatch<>(settings, route, using(route));
  }

  private Dispatch(CorsSettings settings, Router<I, O> router, org.pk11.rxnetty.router.Dispatch<I, O> delegate) {
    this.settings = settings;
    this.router = router;
    this.delegate = delegate;
  }

  @Override
  public Observable<Void> handle(HttpServerRequest<I> request, HttpServerResponse<O> response) {
    String origin = request.getHeader("Origin");
    boolean isCors = request.containsHeader("Origin") && notSameOrigin(request, origin);

    if (isCors) {
      Collection<HttpMethod> methods = router.getMethodsFor(request.getDecodedPath());
      if (!methods.isEmpty()) {
        if (originNotAllowed(origin)) {
          return response.sendHeaders();
        }

        addSharedHeaders(response, origin);

        if (request.getHttpMethod().equals(HttpMethod.OPTIONS)) {
          addPreflightOnlyHeaders(request, response);
          if (!methods.contains(HttpMethod.OPTIONS)) {

            return response.writeString(
              just(String.join(", ", methods.stream().map(m -> m.asciiName()).sorted().collect(Collectors.toList())))
            );
          }
        } else {
          addSimpleOnlyHeaders(response);
        }
      }
    }

    return delegate.handle(request, response);
  }

  private void addSimpleOnlyHeaders(HttpServerResponse<O> response) {
    if (settings.exposedHeaders.length() > 0) {
      response.setHeader("Access-Control-Expose-Headers", settings.exposedHeaders);
    }
  }

  private void addPreflightOnlyHeaders(HttpServerRequest<I> request, HttpServerResponse<O> response) {
    if (settings.maxAge != null) {
      response.setHeader("Access-Control-Max-Age", settings.maxAge.get(ChronoUnit.SECONDS));
    }
    if (request.containsHeader("Access-Control-Request-Headers") || settings.allowedHeaders.length() > 0) {
      response.setHeader("Access-Control-Allow-Headers", settings.allowedHeaders);
    }
    response.setHeader("Access-Control-Allow-Methods", settings.allowedMethods);
  }

  private void addSharedHeaders(HttpServerResponse<O> response, String origin) {
    response.setHeader("Access-Control-Allow-Origin", settings.allowedOrigins.isEmpty() ? "*" : origin);

    if (settings.allowedCredentials) {
      response.setHeader("Access-Control-Allow-Credentials", "true");
    }
  }

  private boolean originNotAllowed(String origin) {
    return !settings.allowedOrigins.isEmpty() && !settings.allowedOrigins.contains(origin);
  }

  private boolean notSameOrigin(HttpServerRequest<I> request, String origin) {
    return !origin.endsWith(request.getHeader("Host"));
  }

  public static class CorsSettings {
    private final boolean allowedCredentials;
    private final CharSequence allowedHeaders;
    private final CharSequence allowedMethods;
    private final Set<String> allowedOrigins;
    private final CharSequence exposedHeaders;
    private final Duration maxAge;

    public CorsSettings() {
      allowedMethods = "*";
      allowedCredentials = false;
      allowedHeaders = "";
      allowedOrigins = Collections.emptySet();
      exposedHeaders = "";
      maxAge = null;
    }

    private CorsSettings(boolean allowedCredentials,
                         CharSequence allowedHeaders,
                         CharSequence allowedMethods,
                         Set<String> allowedOrigins,
                         CharSequence exposedHeaders,
                         Duration maxAge) {
      this.allowedCredentials = allowedCredentials;
      this.allowedHeaders = allowedHeaders;
      this.allowedMethods = allowedMethods;
      this.allowedOrigins = allowedOrigins;
      this.exposedHeaders = exposedHeaders;
      this.maxAge = maxAge;
    }

    public CorsSettings allowCredential(boolean newValue) {
      return new CorsSettings(
        newValue,
        allowedHeaders,
        allowedMethods,
        allowedOrigins,
        exposedHeaders,
        maxAge
      );
    }

    public CorsSettings allowHeader(String header) {
      return new CorsSettings(
        allowedCredentials,
        allowedHeaders.length() == 0 ? header : allowedHeaders + ", " + header,
        allowedMethods,
        allowedOrigins,
        exposedHeaders,
        maxAge
      );
    }

    public CorsSettings allowMethod(HttpMethod method) {
      return new CorsSettings(
        allowedCredentials,
        allowedHeaders,
        allowedMethods.equals("*") ? method.asciiName() : allowedMethods + ", " + method.asciiName(),
        allowedOrigins,
        exposedHeaders,
        maxAge
      );
    }

    public CorsSettings allowOrigin(String origin) {
      Set<String> newOrigins = new HashSet<>();
      newOrigins.addAll(allowedOrigins);
      newOrigins.add(origin);
      return new CorsSettings(
        allowedCredentials,
        allowedHeaders,
        allowedMethods,
        Collections.unmodifiableSet(newOrigins),
        exposedHeaders,
        maxAge
      );
    }

    public CorsSettings exposeHeader(String header) {
      return new CorsSettings(
        allowedCredentials,
        allowedHeaders,
        allowedMethods,
        allowedOrigins,
        exposedHeaders.length() == 0 ? header : exposedHeaders + ", " + header,
        maxAge
      );
    }

    public CorsSettings maxAge(Duration newDuration) {
      return new CorsSettings(
        allowedCredentials,
        allowedHeaders,
        allowedMethods,
        allowedOrigins,
        exposedHeaders,
        newDuration
      );
    }
  }

}
