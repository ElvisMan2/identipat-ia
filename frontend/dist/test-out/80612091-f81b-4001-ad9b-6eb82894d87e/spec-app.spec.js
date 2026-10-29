import {
  ApplicationRef,
  ChangeDetectorRef,
  CommonModule,
  Component,
  DOCUMENT,
  DestroyRef,
  Directive,
  ElementRef,
  EnvironmentInjector,
  EventEmitter,
  FactoryTarget,
  Host,
  Inject,
  Injectable,
  InjectionToken,
  Injector,
  Input,
  NgModule,
  NgZone,
  Observable,
  Optional,
  Output,
  PendingTasks,
  Renderer2,
  ResourceImpl,
  RuntimeError,
  Self,
  SkipSelf,
  Subject,
  TestBed,
  XhrFactory,
  __async,
  __commonJS,
  __decorate,
  __esm,
  __spreadProps,
  __spreadValues,
  afterNextRender,
  assertInInjectionContext,
  booleanAttribute,
  catchError,
  computed,
  concatMap,
  core_exports,
  encapsulateResourceError,
  filter,
  finalize,
  forkJoin,
  formatRuntimeError,
  forwardRef,
  from,
  getDOM,
  init_common,
  init_core,
  init_esm,
  init_operators,
  init_testing,
  init_tslib_es6,
  init_xhr,
  inject,
  isPromise,
  isSubscribable,
  linkedSignal,
  makeEnvironmentProviders,
  map,
  of,
  parseCookieValue,
  provideZonelessChangeDetection,
  runInInjectionContext,
  signal,
  switchMap,
  throwError,
  untracked,
  ɵɵngDeclareClassMetadata,
  ɵɵngDeclareDirective,
  ɵɵngDeclareFactory,
  ɵɵngDeclareInjectable,
  ɵɵngDeclareInjector,
  ɵɵngDeclareNgModule
} from "./chunk-R6J4HXXC.js";

// node_modules/@angular/common/fesm2022/module.mjs
function assertValidHeaders(headers) {
  for (const [key, value] of Object.entries(headers)) {
    if (!(typeof value === "string" || typeof value === "number") && !Array.isArray(value)) {
      throw new Error(`Unexpected value of the \`${key}\` header provided. Expecting either a string, a number or an array, but got: \`${value}\`.`);
    }
  }
}
function paramParser(rawParams, codec) {
  const map2 = /* @__PURE__ */ new Map();
  if (rawParams.length > 0) {
    const params = rawParams.replace(/^\?/, "").split("&");
    params.forEach((param) => {
      const eqIdx = param.indexOf("=");
      const [key, val] = eqIdx == -1 ? [codec.decodeKey(param), ""] : [codec.decodeKey(param.slice(0, eqIdx)), codec.decodeValue(param.slice(eqIdx + 1))];
      const list = map2.get(key) || [];
      list.push(val);
      map2.set(key, list);
    });
  }
  return map2;
}
function standardEncoding(v) {
  return encodeURIComponent(v).replace(STANDARD_ENCODING_REGEX, (s, t) => STANDARD_ENCODING_REPLACEMENTS[t] ?? s);
}
function valueToString(value) {
  return `${value}`;
}
function mightHaveBody(method) {
  switch (method) {
    case "DELETE":
    case "GET":
    case "HEAD":
    case "OPTIONS":
    case "JSONP":
      return false;
    default:
      return true;
  }
}
function isArrayBuffer(value) {
  return typeof ArrayBuffer !== "undefined" && value instanceof ArrayBuffer;
}
function isBlob(value) {
  return typeof Blob !== "undefined" && value instanceof Blob;
}
function isFormData(value) {
  return typeof FormData !== "undefined" && value instanceof FormData;
}
function isUrlSearchParams(value) {
  return typeof URLSearchParams !== "undefined" && value instanceof URLSearchParams;
}
function addBody(options, body) {
  return {
    body,
    headers: options.headers,
    context: options.context,
    observe: options.observe,
    params: options.params,
    reportProgress: options.reportProgress,
    responseType: options.responseType,
    withCredentials: options.withCredentials,
    credentials: options.credentials,
    transferCache: options.transferCache,
    timeout: options.timeout,
    keepalive: options.keepalive,
    priority: options.priority,
    cache: options.cache,
    mode: options.mode,
    redirect: options.redirect,
    integrity: options.integrity,
    referrer: options.referrer
  };
}
function getResponseUrl$1(response) {
  if (response.url) {
    return response.url;
  }
  const xRequestUrl = X_REQUEST_URL_HEADER.toLocaleLowerCase();
  return response.headers.get(xRequestUrl);
}
function noop() {
}
function warningOptionsMessage(req) {
  if (req.credentials && req.withCredentials) {
    console.warn(formatRuntimeError(2819, `Angular detected that a \`HttpClient\` request has both \`withCredentials: true\` and \`credentials: '${req.credentials}'\` options. The \`withCredentials\` option is overriding the explicit \`credentials\` setting to 'include'. Consider removing \`withCredentials\` and using \`credentials: '${req.credentials}'\` directly for clarity.`));
  }
}
function silenceSuperfluousUnhandledPromiseRejection(promise) {
  promise.then(noop, noop);
}
function interceptorChainEndFn(req, finalHandlerFn) {
  return finalHandlerFn(req);
}
function adaptLegacyInterceptorToChain(chainTailFn, interceptor) {
  return (initialRequest, finalHandlerFn) => interceptor.intercept(initialRequest, {
    handle: (downstreamRequest) => chainTailFn(downstreamRequest, finalHandlerFn)
  });
}
function chainedInterceptorFn(chainTailFn, interceptorFn, injector) {
  return (initialRequest, finalHandlerFn) => runInInjectionContext(injector, () => interceptorFn(initialRequest, (downstreamRequest) => chainTailFn(downstreamRequest, finalHandlerFn)));
}
function legacyInterceptorFnFactory() {
  let chain = null;
  return (req, handler) => {
    if (chain === null) {
      const interceptors = inject(HTTP_INTERCEPTORS, { optional: true }) ?? [];
      chain = interceptors.reduceRight(adaptLegacyInterceptorToChain, interceptorChainEndFn);
    }
    const pendingTasks = inject(PendingTasks);
    const contributeToStability = inject(REQUESTS_CONTRIBUTE_TO_STABILITY);
    if (contributeToStability) {
      const removeTask = pendingTasks.add();
      return chain(req, handler).pipe(finalize(removeTask));
    } else {
      return chain(req, handler);
    }
  };
}
function jsonpCallbackContext() {
  if (typeof window === "object") {
    return window;
  }
  return {};
}
function jsonpInterceptorFn(req, next) {
  if (req.method === "JSONP") {
    return inject(JsonpClientBackend).handle(req);
  }
  return next(req);
}
function getResponseUrl(xhr) {
  if ("responseURL" in xhr && xhr.responseURL) {
    return xhr.responseURL;
  }
  if (X_REQUEST_URL_REGEXP.test(xhr.getAllResponseHeaders())) {
    return xhr.getResponseHeader(X_REQUEST_URL_HEADER);
  }
  return null;
}
function validateXhrCompatibility(req) {
  const unsupportedOptions = [
    {
      property: "keepalive",
      errorCode: 2813
    },
    {
      property: "cache",
      errorCode: 2814
    },
    {
      property: "priority",
      errorCode: 2815
    },
    {
      property: "mode",
      errorCode: 2816
    },
    {
      property: "redirect",
      errorCode: 2817
    },
    {
      property: "credentials",
      errorCode: 2818
    },
    {
      property: "integrity",
      errorCode: 2820
    },
    {
      property: "referrer",
      errorCode: 2821
    }
  ];
  for (const { property, errorCode } of unsupportedOptions) {
    if (req[property]) {
      console.warn(formatRuntimeError(errorCode, `Angular detected that a \`HttpClient\` request with the \`${property}\` option was sent using XHR, which does not support it. To use the \`${property}\` option, enable Fetch API support by passing \`withFetch()\` as an argument to \`provideHttpClient()\`.`));
    }
  }
}
function xsrfInterceptorFn(req, next) {
  if (!inject(XSRF_ENABLED) || req.method === "GET" || req.method === "HEAD" || ABSOLUTE_URL_REGEX.test(req.url)) {
    return next(req);
  }
  const token = inject(HttpXsrfTokenExtractor).getToken();
  const headerName = inject(XSRF_HEADER_NAME);
  if (token != null && !req.headers.has(headerName)) {
    req = req.clone({ headers: req.headers.set(headerName, token) });
  }
  return next(req);
}
function makeHttpFeature(kind, providers) {
  return {
    \u0275kind: kind,
    \u0275providers: providers
  };
}
function provideHttpClient(...features) {
  if (ngDevMode) {
    const featureKinds = new Set(features.map((f) => f.\u0275kind));
    if (featureKinds.has(HttpFeatureKind.NoXsrfProtection) && featureKinds.has(HttpFeatureKind.CustomXsrfConfiguration)) {
      throw new Error(`Configuration error: found both withXsrfConfiguration() and withNoXsrfProtection() in the same call to provideHttpClient(), which is a contradiction.`);
    }
    if (featureKinds.has(HttpFeatureKind.RequestsMadeViaParent) && featureKinds.has(HttpFeatureKind.Fetch)) {
      throw new Error(`Configuration error: withRequestsMadeViaParent() cannot be combined with withFetch() in the same call to provideHttpClient().`);
    }
  }
  const providers = [
    HttpClient,
    HttpXhrBackend,
    HttpInterceptorHandler,
    { provide: HttpHandler, useExisting: HttpInterceptorHandler },
    {
      provide: HttpBackend,
      useFactory: () => {
        return inject(FETCH_BACKEND, { optional: true }) ?? inject(HttpXhrBackend);
      }
    },
    {
      provide: HTTP_INTERCEPTOR_FNS,
      useValue: xsrfInterceptorFn,
      multi: true
    },
    { provide: XSRF_ENABLED, useValue: true },
    { provide: HttpXsrfTokenExtractor, useClass: HttpXsrfCookieExtractor }
  ];
  for (const feature of features) {
    providers.push(...feature.\u0275providers);
  }
  return makeEnvironmentProviders(providers);
}
function withInterceptorsFromDi() {
  return makeHttpFeature(HttpFeatureKind.LegacyInterceptors, [
    {
      provide: LEGACY_INTERCEPTOR_FN,
      useFactory: legacyInterceptorFnFactory
    },
    {
      provide: HTTP_INTERCEPTOR_FNS,
      useExisting: LEGACY_INTERCEPTOR_FN,
      multi: true
    }
  ]);
}
function withXsrfConfiguration({ cookieName, headerName }) {
  const providers = [];
  if (cookieName !== void 0) {
    providers.push({ provide: XSRF_COOKIE_NAME, useValue: cookieName });
  }
  if (headerName !== void 0) {
    providers.push({ provide: XSRF_HEADER_NAME, useValue: headerName });
  }
  return makeHttpFeature(HttpFeatureKind.CustomXsrfConfiguration, providers);
}
function withNoXsrfProtection() {
  return makeHttpFeature(HttpFeatureKind.NoXsrfProtection, [
    {
      provide: XSRF_ENABLED,
      useValue: false
    }
  ]);
}
function withJsonpSupport() {
  return makeHttpFeature(HttpFeatureKind.JsonpSupport, [
    JsonpClientBackend,
    { provide: JsonpCallbackContext, useFactory: jsonpCallbackContext },
    { provide: HTTP_INTERCEPTOR_FNS, useValue: jsonpInterceptorFn, multi: true }
  ]);
}
var HttpHandler, HttpBackend, HttpHeaders, HttpUrlEncodingCodec, STANDARD_ENCODING_REGEX, STANDARD_ENCODING_REPLACEMENTS, HttpParams, HttpContext, CONTENT_TYPE_HEADER, ACCEPT_HEADER, X_REQUEST_URL_HEADER, TEXT_CONTENT_TYPE, JSON_CONTENT_TYPE, ACCEPT_HEADER_VALUE, HttpRequest, HttpEventType, HttpResponseBase, HttpHeaderResponse, HttpResponse, HttpErrorResponse, HTTP_STATUS_CODE_OK, HTTP_STATUS_CODE_NO_CONTENT, HttpStatusCode, HttpClient, XSSI_PREFIX$1, FETCH_BACKEND, FetchBackend, FetchFactory, HTTP_INTERCEPTORS, HTTP_INTERCEPTOR_FNS, HTTP_ROOT_INTERCEPTOR_FNS, REQUESTS_CONTRIBUTE_TO_STABILITY, fetchBackendWarningDisplayed, HttpInterceptorHandler, nextRequestId, foreignDocument, JSONP_ERR_NO_CALLBACK, JSONP_ERR_WRONG_METHOD, JSONP_ERR_WRONG_RESPONSE_TYPE, JSONP_ERR_HEADERS_NOT_SUPPORTED, JSONP_ERR_UNSAFE_URL, JsonpCallbackContext, JsonpClientBackend, JsonpInterceptor, XSSI_PREFIX, X_REQUEST_URL_REGEXP, HttpXhrBackend, XSRF_ENABLED, XSRF_DEFAULT_COOKIE_NAME, XSRF_COOKIE_NAME, XSRF_DEFAULT_HEADER_NAME, XSRF_HEADER_NAME, HttpXsrfTokenExtractor, HttpXsrfCookieExtractor, ABSOLUTE_URL_REGEX, HttpXsrfInterceptor, HttpFeatureKind, LEGACY_INTERCEPTOR_FN, HttpClientXsrfModule, HttpClientModule, HttpClientJsonpModule;
var init_module = __esm({
  "node_modules/@angular/common/fesm2022/module.mjs"() {
    "use strict";
    init_core();
    init_core();
    init_operators();
    init_esm();
    init_xhr();
    HttpHandler = class {
    };
    HttpBackend = class {
    };
    HttpHeaders = class _HttpHeaders {
      /**
       * Internal map of lowercase header names to values.
       */
      headers;
      /**
       * Internal map of lowercased header names to the normalized
       * form of the name (the form seen first).
       */
      normalizedNames = /* @__PURE__ */ new Map();
      /**
       * Complete the lazy initialization of this object (needed before reading).
       */
      lazyInit;
      /**
       * Queued updates to be materialized the next initialization.
       */
      lazyUpdate = null;
      /**  Constructs a new HTTP header object with the given values.*/
      constructor(headers) {
        if (!headers) {
          this.headers = /* @__PURE__ */ new Map();
        } else if (typeof headers === "string") {
          this.lazyInit = () => {
            this.headers = /* @__PURE__ */ new Map();
            headers.split("\n").forEach((line) => {
              const index = line.indexOf(":");
              if (index > 0) {
                const name = line.slice(0, index);
                const value = line.slice(index + 1).trim();
                this.addHeaderEntry(name, value);
              }
            });
          };
        } else if (typeof Headers !== "undefined" && headers instanceof Headers) {
          this.headers = /* @__PURE__ */ new Map();
          headers.forEach((value, name) => {
            this.addHeaderEntry(name, value);
          });
        } else {
          this.lazyInit = () => {
            if (typeof ngDevMode === "undefined" || ngDevMode) {
              assertValidHeaders(headers);
            }
            this.headers = /* @__PURE__ */ new Map();
            Object.entries(headers).forEach(([name, values]) => {
              this.setHeaderEntries(name, values);
            });
          };
        }
      }
      /**
       * Checks for existence of a given header.
       *
       * @param name The header name to check for existence.
       *
       * @returns True if the header exists, false otherwise.
       */
      has(name) {
        this.init();
        return this.headers.has(name.toLowerCase());
      }
      /**
       * Retrieves the first value of a given header.
       *
       * @param name The header name.
       *
       * @returns The value string if the header exists, null otherwise
       */
      get(name) {
        this.init();
        const values = this.headers.get(name.toLowerCase());
        return values && values.length > 0 ? values[0] : null;
      }
      /**
       * Retrieves the names of the headers.
       *
       * @returns A list of header names.
       */
      keys() {
        this.init();
        return Array.from(this.normalizedNames.values());
      }
      /**
       * Retrieves a list of values for a given header.
       *
       * @param name The header name from which to retrieve values.
       *
       * @returns A string of values if the header exists, null otherwise.
       */
      getAll(name) {
        this.init();
        return this.headers.get(name.toLowerCase()) || null;
      }
      /**
       * Appends a new value to the existing set of values for a header
       * and returns them in a clone of the original instance.
       *
       * @param name The header name for which to append the values.
       * @param value The value to append.
       *
       * @returns A clone of the HTTP headers object with the value appended to the given header.
       */
      append(name, value) {
        return this.clone({ name, value, op: "a" });
      }
      /**
       * Sets or modifies a value for a given header in a clone of the original instance.
       * If the header already exists, its value is replaced with the given value
       * in the returned object.
       *
       * @param name The header name.
       * @param value The value or values to set or override for the given header.
       *
       * @returns A clone of the HTTP headers object with the newly set header value.
       */
      set(name, value) {
        return this.clone({ name, value, op: "s" });
      }
      /**
       * Deletes values for a given header in a clone of the original instance.
       *
       * @param name The header name.
       * @param value The value or values to delete for the given header.
       *
       * @returns A clone of the HTTP headers object with the given value deleted.
       */
      delete(name, value) {
        return this.clone({ name, value, op: "d" });
      }
      maybeSetNormalizedName(name, lcName) {
        if (!this.normalizedNames.has(lcName)) {
          this.normalizedNames.set(lcName, name);
        }
      }
      init() {
        if (!!this.lazyInit) {
          if (this.lazyInit instanceof _HttpHeaders) {
            this.copyFrom(this.lazyInit);
          } else {
            this.lazyInit();
          }
          this.lazyInit = null;
          if (!!this.lazyUpdate) {
            this.lazyUpdate.forEach((update) => this.applyUpdate(update));
            this.lazyUpdate = null;
          }
        }
      }
      copyFrom(other) {
        other.init();
        for (const [key, values] of other.headers.entries()) {
          this.headers.set(key, values);
          this.normalizedNames.set(key, other.normalizedNames.get(key));
        }
      }
      clone(update) {
        const clone = new _HttpHeaders();
        clone.lazyInit = !!this.lazyInit && this.lazyInit instanceof _HttpHeaders ? this.lazyInit : this;
        clone.lazyUpdate = (this.lazyUpdate || []).concat([update]);
        return clone;
      }
      applyUpdate(update) {
        const key = update.name.toLowerCase();
        switch (update.op) {
          case "a":
          case "s":
            let value = update.value;
            if (typeof value === "string") {
              value = [value];
            }
            if (value.length === 0) {
              return;
            }
            this.maybeSetNormalizedName(update.name, key);
            const base = update.op === "a" ? (this.headers.get(key) || []).slice() : [];
            base.push(...value);
            this.headers.set(key, base);
            break;
          case "d":
            const toDelete = update.value;
            if (toDelete === void 0) {
              this.headers.delete(key);
              this.normalizedNames.delete(key);
            } else {
              const valuesToDelete = Array.isArray(toDelete) ? toDelete : [toDelete];
              let existing = this.headers.get(key);
              if (!existing) {
                return;
              }
              existing = existing.filter((value2) => valuesToDelete.indexOf(value2) === -1);
              if (existing.length === 0) {
                this.headers.delete(key);
                this.normalizedNames.delete(key);
              } else {
                this.headers.set(key, existing);
              }
            }
            break;
        }
      }
      addHeaderEntry(name, value) {
        const key = name.toLowerCase();
        this.maybeSetNormalizedName(name, key);
        if (this.headers.has(key)) {
          this.headers.get(key).push(value);
        } else {
          this.headers.set(key, [value]);
        }
      }
      setHeaderEntries(name, values) {
        const headerValues = (Array.isArray(values) ? values : [values]).map((value) => value.toString());
        const key = name.toLowerCase();
        this.headers.set(key, headerValues);
        this.maybeSetNormalizedName(name, key);
      }
      /**
       * @internal
       */
      forEach(fn) {
        this.init();
        Array.from(this.normalizedNames.keys()).forEach((key) => fn(this.normalizedNames.get(key), this.headers.get(key)));
      }
    };
    HttpUrlEncodingCodec = class {
      /**
       * Encodes a key name for a URL parameter or query-string.
       * @param key The key name.
       * @returns The encoded key name.
       */
      encodeKey(key) {
        return standardEncoding(key);
      }
      /**
       * Encodes the value of a URL parameter or query-string.
       * @param value The value.
       * @returns The encoded value.
       */
      encodeValue(value) {
        return standardEncoding(value);
      }
      /**
       * Decodes an encoded URL parameter or query-string key.
       * @param key The encoded key name.
       * @returns The decoded key name.
       */
      decodeKey(key) {
        return decodeURIComponent(key);
      }
      /**
       * Decodes an encoded URL parameter or query-string value.
       * @param value The encoded value.
       * @returns The decoded value.
       */
      decodeValue(value) {
        return decodeURIComponent(value);
      }
    };
    STANDARD_ENCODING_REGEX = /%(\d[a-f0-9])/gi;
    STANDARD_ENCODING_REPLACEMENTS = {
      "40": "@",
      "3A": ":",
      "24": "$",
      "2C": ",",
      "3B": ";",
      "3D": "=",
      "3F": "?",
      "2F": "/"
    };
    HttpParams = class _HttpParams {
      map;
      encoder;
      updates = null;
      cloneFrom = null;
      constructor(options = {}) {
        this.encoder = options.encoder || new HttpUrlEncodingCodec();
        if (options.fromString) {
          if (options.fromObject) {
            throw new RuntimeError(2805, ngDevMode && "Cannot specify both fromString and fromObject.");
          }
          this.map = paramParser(options.fromString, this.encoder);
        } else if (!!options.fromObject) {
          this.map = /* @__PURE__ */ new Map();
          Object.keys(options.fromObject).forEach((key) => {
            const value = options.fromObject[key];
            const values = Array.isArray(value) ? value.map(valueToString) : [valueToString(value)];
            this.map.set(key, values);
          });
        } else {
          this.map = null;
        }
      }
      /**
       * Reports whether the body includes one or more values for a given parameter.
       * @param param The parameter name.
       * @returns True if the parameter has one or more values,
       * false if it has no value or is not present.
       */
      has(param) {
        this.init();
        return this.map.has(param);
      }
      /**
       * Retrieves the first value for a parameter.
       * @param param The parameter name.
       * @returns The first value of the given parameter,
       * or `null` if the parameter is not present.
       */
      get(param) {
        this.init();
        const res = this.map.get(param);
        return !!res ? res[0] : null;
      }
      /**
       * Retrieves all values for a  parameter.
       * @param param The parameter name.
       * @returns All values in a string array,
       * or `null` if the parameter not present.
       */
      getAll(param) {
        this.init();
        return this.map.get(param) || null;
      }
      /**
       * Retrieves all the parameters for this body.
       * @returns The parameter names in a string array.
       */
      keys() {
        this.init();
        return Array.from(this.map.keys());
      }
      /**
       * Appends a new value to existing values for a parameter.
       * @param param The parameter name.
       * @param value The new value to add.
       * @return A new body with the appended value.
       */
      append(param, value) {
        return this.clone({ param, value, op: "a" });
      }
      /**
       * Constructs a new body with appended values for the given parameter name.
       * @param params parameters and values
       * @return A new body with the new value.
       */
      appendAll(params) {
        const updates = [];
        Object.keys(params).forEach((param) => {
          const value = params[param];
          if (Array.isArray(value)) {
            value.forEach((_value) => {
              updates.push({ param, value: _value, op: "a" });
            });
          } else {
            updates.push({ param, value, op: "a" });
          }
        });
        return this.clone(updates);
      }
      /**
       * Replaces the value for a parameter.
       * @param param The parameter name.
       * @param value The new value.
       * @return A new body with the new value.
       */
      set(param, value) {
        return this.clone({ param, value, op: "s" });
      }
      /**
       * Removes a given value or all values from a parameter.
       * @param param The parameter name.
       * @param value The value to remove, if provided.
       * @return A new body with the given value removed, or with all values
       * removed if no value is specified.
       */
      delete(param, value) {
        return this.clone({ param, value, op: "d" });
      }
      /**
       * Serializes the body to an encoded string, where key-value pairs (separated by `=`) are
       * separated by `&`s.
       */
      toString() {
        this.init();
        return this.keys().map((key) => {
          const eKey = this.encoder.encodeKey(key);
          return this.map.get(key).map((value) => eKey + "=" + this.encoder.encodeValue(value)).join("&");
        }).filter((param) => param !== "").join("&");
      }
      clone(update) {
        const clone = new _HttpParams({ encoder: this.encoder });
        clone.cloneFrom = this.cloneFrom || this;
        clone.updates = (this.updates || []).concat(update);
        return clone;
      }
      init() {
        if (this.map === null) {
          this.map = /* @__PURE__ */ new Map();
        }
        if (this.cloneFrom !== null) {
          this.cloneFrom.init();
          for (const [key, values] of this.cloneFrom.map.entries()) {
            this.map.set(key, values);
          }
          this.updates.forEach((update) => {
            switch (update.op) {
              case "a":
              case "s":
                const base = update.op === "a" ? (this.map.get(update.param) || []).slice() : [];
                base.push(valueToString(update.value));
                this.map.set(update.param, base);
                break;
              case "d":
                if (update.value !== void 0) {
                  const base2 = (this.map.get(update.param) || []).slice();
                  const idx = base2.indexOf(valueToString(update.value));
                  if (idx !== -1) {
                    base2.splice(idx, 1);
                  }
                  if (base2.length > 0) {
                    this.map.set(update.param, base2);
                  } else {
                    this.map.delete(update.param);
                  }
                } else {
                  this.map.delete(update.param);
                  break;
                }
            }
          });
          this.cloneFrom = this.updates = null;
        }
      }
    };
    HttpContext = class {
      map = /* @__PURE__ */ new Map();
      /**
       * Store a value in the context. If a value is already present it will be overwritten.
       *
       * @param token The reference to an instance of `HttpContextToken`.
       * @param value The value to store.
       *
       * @returns A reference to itself for easy chaining.
       */
      set(token, value) {
        this.map.set(token, value);
        return this;
      }
      /**
       * Retrieve the value associated with the given token.
       *
       * @param token The reference to an instance of `HttpContextToken`.
       *
       * @returns The stored value or default if one is defined.
       */
      get(token) {
        if (!this.map.has(token)) {
          this.map.set(token, token.defaultValue());
        }
        return this.map.get(token);
      }
      /**
       * Delete the value associated with the given token.
       *
       * @param token The reference to an instance of `HttpContextToken`.
       *
       * @returns A reference to itself for easy chaining.
       */
      delete(token) {
        this.map.delete(token);
        return this;
      }
      /**
       * Checks for existence of a given token.
       *
       * @param token The reference to an instance of `HttpContextToken`.
       *
       * @returns True if the token exists, false otherwise.
       */
      has(token) {
        return this.map.has(token);
      }
      /**
       * @returns a list of tokens currently stored in the context.
       */
      keys() {
        return this.map.keys();
      }
    };
    CONTENT_TYPE_HEADER = "Content-Type";
    ACCEPT_HEADER = "Accept";
    X_REQUEST_URL_HEADER = "X-Request-URL";
    TEXT_CONTENT_TYPE = "text/plain";
    JSON_CONTENT_TYPE = "application/json";
    ACCEPT_HEADER_VALUE = `${JSON_CONTENT_TYPE}, ${TEXT_CONTENT_TYPE}, */*`;
    HttpRequest = class _HttpRequest {
      url;
      /**
       * The request body, or `null` if one isn't set.
       *
       * Bodies are not enforced to be immutable, as they can include a reference to any
       * user-defined data type. However, interceptors should take care to preserve
       * idempotence by treating them as such.
       */
      body = null;
      /**
       * Outgoing headers for this request.
       */
      headers;
      /**
       * Shared and mutable context that can be used by interceptors
       */
      context;
      /**
       * Whether this request should be made in a way that exposes progress events.
       *
       * Progress events are expensive (change detection runs on each event) and so
       * they should only be requested if the consumer intends to monitor them.
       *
       * Note: The `FetchBackend` doesn't support progress report on uploads.
       */
      reportProgress = false;
      /**
       * Whether this request should be sent with outgoing credentials (cookies).
       */
      withCredentials = false;
      /**
       *  The credentials mode of the request, which determines how cookies and HTTP authentication are handled.
       *  This can affect whether cookies are sent with the request, and how authentication is handled.
       */
      credentials;
      /**
       * When using the fetch implementation and set to `true`, the browser will not abort the associated request if the page that initiated it is unloaded before the request is complete.
       */
      keepalive = false;
      /**
       * Controls how the request will interact with the browser's HTTP cache.
       * This affects whether a response is retrieved from the cache, how it is stored, or if it bypasses the cache altogether.
       */
      cache;
      /**
       * Indicates the relative priority of the request. This may be used by the browser to decide the order in which requests are dispatched and resources fetched.
       */
      priority;
      /**
       * The mode of the request, which determines how the request will interact with the browser's security model.
       * This can affect things like CORS (Cross-Origin Resource Sharing) and same-origin policies.
       */
      mode;
      /**
       * The redirect mode of the request, which determines how redirects are handled.
       * This can affect whether the request follows redirects automatically, or if it fails when a redirect occurs.
       */
      redirect;
      /**
       * The referrer of the request, which can be used to indicate the origin of the request.
       * This is useful for security and analytics purposes.
       * Value is a same-origin URL, "about:client", or the empty string, to set request's referrer.
       */
      referrer;
      /**
       * The integrity metadata of the request, which can be used to ensure the request is made with the expected content.
       * A cryptographic hash of the resource to be fetched by request
       */
      integrity;
      /**
       * The expected response type of the server.
       *
       * This is used to parse the response appropriately before returning it to
       * the requestee.
       */
      responseType = "json";
      /**
       * The outgoing HTTP request method.
       */
      method;
      /**
       * Outgoing URL parameters.
       *
       * To pass a string representation of HTTP parameters in the URL-query-string format,
       * the `HttpParamsOptions`' `fromString` may be used. For example:
       *
       * ```ts
       * new HttpParams({fromString: 'angular=awesome'})
       * ```
       */
      params;
      /**
       * The outgoing URL with all URL parameters set.
       */
      urlWithParams;
      /**
       * The HttpTransferCache option for the request
       */
      transferCache;
      /**
       * The timeout for the backend HTTP request in ms.
       */
      timeout;
      constructor(method, url, third, fourth) {
        this.url = url;
        this.method = method.toUpperCase();
        let options;
        if (mightHaveBody(this.method) || !!fourth) {
          this.body = third !== void 0 ? third : null;
          options = fourth;
        } else {
          options = third;
        }
        if (options) {
          this.reportProgress = !!options.reportProgress;
          this.withCredentials = !!options.withCredentials;
          this.keepalive = !!options.keepalive;
          if (!!options.responseType) {
            this.responseType = options.responseType;
          }
          if (options.headers) {
            this.headers = options.headers;
          }
          if (options.context) {
            this.context = options.context;
          }
          if (options.params) {
            this.params = options.params;
          }
          if (options.priority) {
            this.priority = options.priority;
          }
          if (options.cache) {
            this.cache = options.cache;
          }
          if (options.credentials) {
            this.credentials = options.credentials;
          }
          if (typeof options.timeout === "number") {
            if (options.timeout < 1 || !Number.isInteger(options.timeout)) {
              throw new RuntimeError(2822, ngDevMode ? "`timeout` must be a positive integer value" : "");
            }
            this.timeout = options.timeout;
          }
          if (options.mode) {
            this.mode = options.mode;
          }
          if (options.redirect) {
            this.redirect = options.redirect;
          }
          if (options.integrity) {
            this.integrity = options.integrity;
          }
          if (options.referrer !== void 0) {
            this.referrer = options.referrer;
          }
          this.transferCache = options.transferCache;
        }
        this.headers ??= new HttpHeaders();
        this.context ??= new HttpContext();
        if (!this.params) {
          this.params = new HttpParams();
          this.urlWithParams = url;
        } else {
          const params = this.params.toString();
          if (params.length === 0) {
            this.urlWithParams = url;
          } else {
            const qIdx = url.indexOf("?");
            const sep = qIdx === -1 ? "?" : qIdx < url.length - 1 ? "&" : "";
            this.urlWithParams = url + sep + params;
          }
        }
      }
      /**
       * Transform the free-form body into a serialized format suitable for
       * transmission to the server.
       */
      serializeBody() {
        if (this.body === null) {
          return null;
        }
        if (typeof this.body === "string" || isArrayBuffer(this.body) || isBlob(this.body) || isFormData(this.body) || isUrlSearchParams(this.body)) {
          return this.body;
        }
        if (this.body instanceof HttpParams) {
          return this.body.toString();
        }
        if (typeof this.body === "object" || typeof this.body === "boolean" || Array.isArray(this.body)) {
          return JSON.stringify(this.body);
        }
        return this.body.toString();
      }
      /**
       * Examine the body and attempt to infer an appropriate MIME type
       * for it.
       *
       * If no such type can be inferred, this method will return `null`.
       */
      detectContentTypeHeader() {
        if (this.body === null) {
          return null;
        }
        if (isFormData(this.body)) {
          return null;
        }
        if (isBlob(this.body)) {
          return this.body.type || null;
        }
        if (isArrayBuffer(this.body)) {
          return null;
        }
        if (typeof this.body === "string") {
          return TEXT_CONTENT_TYPE;
        }
        if (this.body instanceof HttpParams) {
          return "application/x-www-form-urlencoded;charset=UTF-8";
        }
        if (typeof this.body === "object" || typeof this.body === "number" || typeof this.body === "boolean") {
          return JSON_CONTENT_TYPE;
        }
        return null;
      }
      clone(update = {}) {
        const method = update.method || this.method;
        const url = update.url || this.url;
        const responseType = update.responseType || this.responseType;
        const keepalive = update.keepalive ?? this.keepalive;
        const priority = update.priority || this.priority;
        const cache = update.cache || this.cache;
        const mode = update.mode || this.mode;
        const redirect = update.redirect || this.redirect;
        const credentials = update.credentials || this.credentials;
        const referrer = update.referrer ?? this.referrer;
        const integrity = update.integrity || this.integrity;
        const transferCache = update.transferCache ?? this.transferCache;
        const timeout = update.timeout ?? this.timeout;
        const body = update.body !== void 0 ? update.body : this.body;
        const withCredentials = update.withCredentials ?? this.withCredentials;
        const reportProgress = update.reportProgress ?? this.reportProgress;
        let headers = update.headers || this.headers;
        let params = update.params || this.params;
        const context = update.context ?? this.context;
        if (update.setHeaders !== void 0) {
          headers = Object.keys(update.setHeaders).reduce((headers2, name) => headers2.set(name, update.setHeaders[name]), headers);
        }
        if (update.setParams) {
          params = Object.keys(update.setParams).reduce((params2, param) => params2.set(param, update.setParams[param]), params);
        }
        return new _HttpRequest(method, url, body, {
          params,
          headers,
          context,
          reportProgress,
          responseType,
          withCredentials,
          transferCache,
          keepalive,
          cache,
          priority,
          timeout,
          mode,
          redirect,
          credentials,
          referrer,
          integrity
        });
      }
    };
    (function(HttpEventType2) {
      HttpEventType2[HttpEventType2["Sent"] = 0] = "Sent";
      HttpEventType2[HttpEventType2["UploadProgress"] = 1] = "UploadProgress";
      HttpEventType2[HttpEventType2["ResponseHeader"] = 2] = "ResponseHeader";
      HttpEventType2[HttpEventType2["DownloadProgress"] = 3] = "DownloadProgress";
      HttpEventType2[HttpEventType2["Response"] = 4] = "Response";
      HttpEventType2[HttpEventType2["User"] = 5] = "User";
    })(HttpEventType || (HttpEventType = {}));
    HttpResponseBase = class {
      /**
       * All response headers.
       */
      headers;
      /**
       * Response status code.
       */
      status;
      /**
       * Textual description of response status code, defaults to OK.
       *
       * Do not depend on this.
       */
      statusText;
      /**
       * URL of the resource retrieved, or null if not available.
       */
      url;
      /**
       * Whether the status code falls in the 2xx range.
       */
      ok;
      /**
       * Type of the response, narrowed to either the full response or the header.
       */
      type;
      /**
       * Indicates whether the HTTP response was redirected during the request.
       * This property is only available when using the Fetch API using `withFetch()`
       * When using the default XHR Request this property will be `undefined`
       */
      redirected;
      /**
       * Super-constructor for all responses.
       *
       * The single parameter accepted is an initialization hash. Any properties
       * of the response passed there will override the default values.
       */
      constructor(init, defaultStatus = 200, defaultStatusText = "OK") {
        this.headers = init.headers || new HttpHeaders();
        this.status = init.status !== void 0 ? init.status : defaultStatus;
        this.statusText = init.statusText || defaultStatusText;
        this.url = init.url || null;
        this.redirected = init.redirected;
        this.ok = this.status >= 200 && this.status < 300;
      }
    };
    HttpHeaderResponse = class _HttpHeaderResponse extends HttpResponseBase {
      /**
       * Create a new `HttpHeaderResponse` with the given parameters.
       */
      constructor(init = {}) {
        super(init);
      }
      type = HttpEventType.ResponseHeader;
      /**
       * Copy this `HttpHeaderResponse`, overriding its contents with the
       * given parameter hash.
       */
      clone(update = {}) {
        return new _HttpHeaderResponse({
          headers: update.headers || this.headers,
          status: update.status !== void 0 ? update.status : this.status,
          statusText: update.statusText || this.statusText,
          url: update.url || this.url || void 0
        });
      }
    };
    HttpResponse = class _HttpResponse extends HttpResponseBase {
      /**
       * The response body, or `null` if one was not returned.
       */
      body;
      /**
       * Construct a new `HttpResponse`.
       */
      constructor(init = {}) {
        super(init);
        this.body = init.body !== void 0 ? init.body : null;
      }
      type = HttpEventType.Response;
      clone(update = {}) {
        return new _HttpResponse({
          body: update.body !== void 0 ? update.body : this.body,
          headers: update.headers || this.headers,
          status: update.status !== void 0 ? update.status : this.status,
          statusText: update.statusText || this.statusText,
          url: update.url || this.url || void 0,
          redirected: update.redirected ?? this.redirected
        });
      }
    };
    HttpErrorResponse = class extends HttpResponseBase {
      name = "HttpErrorResponse";
      message;
      error;
      /**
       * Errors are never okay, even when the status code is in the 2xx success range.
       */
      ok = false;
      constructor(init) {
        super(init, 0, "Unknown Error");
        if (this.status >= 200 && this.status < 300) {
          this.message = `Http failure during parsing for ${init.url || "(unknown url)"}`;
        } else {
          this.message = `Http failure response for ${init.url || "(unknown url)"}: ${init.status} ${init.statusText}`;
        }
        this.error = init.error || null;
      }
    };
    HTTP_STATUS_CODE_OK = 200;
    HTTP_STATUS_CODE_NO_CONTENT = 204;
    (function(HttpStatusCode2) {
      HttpStatusCode2[HttpStatusCode2["Continue"] = 100] = "Continue";
      HttpStatusCode2[HttpStatusCode2["SwitchingProtocols"] = 101] = "SwitchingProtocols";
      HttpStatusCode2[HttpStatusCode2["Processing"] = 102] = "Processing";
      HttpStatusCode2[HttpStatusCode2["EarlyHints"] = 103] = "EarlyHints";
      HttpStatusCode2[HttpStatusCode2["Ok"] = 200] = "Ok";
      HttpStatusCode2[HttpStatusCode2["Created"] = 201] = "Created";
      HttpStatusCode2[HttpStatusCode2["Accepted"] = 202] = "Accepted";
      HttpStatusCode2[HttpStatusCode2["NonAuthoritativeInformation"] = 203] = "NonAuthoritativeInformation";
      HttpStatusCode2[HttpStatusCode2["NoContent"] = 204] = "NoContent";
      HttpStatusCode2[HttpStatusCode2["ResetContent"] = 205] = "ResetContent";
      HttpStatusCode2[HttpStatusCode2["PartialContent"] = 206] = "PartialContent";
      HttpStatusCode2[HttpStatusCode2["MultiStatus"] = 207] = "MultiStatus";
      HttpStatusCode2[HttpStatusCode2["AlreadyReported"] = 208] = "AlreadyReported";
      HttpStatusCode2[HttpStatusCode2["ImUsed"] = 226] = "ImUsed";
      HttpStatusCode2[HttpStatusCode2["MultipleChoices"] = 300] = "MultipleChoices";
      HttpStatusCode2[HttpStatusCode2["MovedPermanently"] = 301] = "MovedPermanently";
      HttpStatusCode2[HttpStatusCode2["Found"] = 302] = "Found";
      HttpStatusCode2[HttpStatusCode2["SeeOther"] = 303] = "SeeOther";
      HttpStatusCode2[HttpStatusCode2["NotModified"] = 304] = "NotModified";
      HttpStatusCode2[HttpStatusCode2["UseProxy"] = 305] = "UseProxy";
      HttpStatusCode2[HttpStatusCode2["Unused"] = 306] = "Unused";
      HttpStatusCode2[HttpStatusCode2["TemporaryRedirect"] = 307] = "TemporaryRedirect";
      HttpStatusCode2[HttpStatusCode2["PermanentRedirect"] = 308] = "PermanentRedirect";
      HttpStatusCode2[HttpStatusCode2["BadRequest"] = 400] = "BadRequest";
      HttpStatusCode2[HttpStatusCode2["Unauthorized"] = 401] = "Unauthorized";
      HttpStatusCode2[HttpStatusCode2["PaymentRequired"] = 402] = "PaymentRequired";
      HttpStatusCode2[HttpStatusCode2["Forbidden"] = 403] = "Forbidden";
      HttpStatusCode2[HttpStatusCode2["NotFound"] = 404] = "NotFound";
      HttpStatusCode2[HttpStatusCode2["MethodNotAllowed"] = 405] = "MethodNotAllowed";
      HttpStatusCode2[HttpStatusCode2["NotAcceptable"] = 406] = "NotAcceptable";
      HttpStatusCode2[HttpStatusCode2["ProxyAuthenticationRequired"] = 407] = "ProxyAuthenticationRequired";
      HttpStatusCode2[HttpStatusCode2["RequestTimeout"] = 408] = "RequestTimeout";
      HttpStatusCode2[HttpStatusCode2["Conflict"] = 409] = "Conflict";
      HttpStatusCode2[HttpStatusCode2["Gone"] = 410] = "Gone";
      HttpStatusCode2[HttpStatusCode2["LengthRequired"] = 411] = "LengthRequired";
      HttpStatusCode2[HttpStatusCode2["PreconditionFailed"] = 412] = "PreconditionFailed";
      HttpStatusCode2[HttpStatusCode2["PayloadTooLarge"] = 413] = "PayloadTooLarge";
      HttpStatusCode2[HttpStatusCode2["UriTooLong"] = 414] = "UriTooLong";
      HttpStatusCode2[HttpStatusCode2["UnsupportedMediaType"] = 415] = "UnsupportedMediaType";
      HttpStatusCode2[HttpStatusCode2["RangeNotSatisfiable"] = 416] = "RangeNotSatisfiable";
      HttpStatusCode2[HttpStatusCode2["ExpectationFailed"] = 417] = "ExpectationFailed";
      HttpStatusCode2[HttpStatusCode2["ImATeapot"] = 418] = "ImATeapot";
      HttpStatusCode2[HttpStatusCode2["MisdirectedRequest"] = 421] = "MisdirectedRequest";
      HttpStatusCode2[HttpStatusCode2["UnprocessableEntity"] = 422] = "UnprocessableEntity";
      HttpStatusCode2[HttpStatusCode2["Locked"] = 423] = "Locked";
      HttpStatusCode2[HttpStatusCode2["FailedDependency"] = 424] = "FailedDependency";
      HttpStatusCode2[HttpStatusCode2["TooEarly"] = 425] = "TooEarly";
      HttpStatusCode2[HttpStatusCode2["UpgradeRequired"] = 426] = "UpgradeRequired";
      HttpStatusCode2[HttpStatusCode2["PreconditionRequired"] = 428] = "PreconditionRequired";
      HttpStatusCode2[HttpStatusCode2["TooManyRequests"] = 429] = "TooManyRequests";
      HttpStatusCode2[HttpStatusCode2["RequestHeaderFieldsTooLarge"] = 431] = "RequestHeaderFieldsTooLarge";
      HttpStatusCode2[HttpStatusCode2["UnavailableForLegalReasons"] = 451] = "UnavailableForLegalReasons";
      HttpStatusCode2[HttpStatusCode2["InternalServerError"] = 500] = "InternalServerError";
      HttpStatusCode2[HttpStatusCode2["NotImplemented"] = 501] = "NotImplemented";
      HttpStatusCode2[HttpStatusCode2["BadGateway"] = 502] = "BadGateway";
      HttpStatusCode2[HttpStatusCode2["ServiceUnavailable"] = 503] = "ServiceUnavailable";
      HttpStatusCode2[HttpStatusCode2["GatewayTimeout"] = 504] = "GatewayTimeout";
      HttpStatusCode2[HttpStatusCode2["HttpVersionNotSupported"] = 505] = "HttpVersionNotSupported";
      HttpStatusCode2[HttpStatusCode2["VariantAlsoNegotiates"] = 506] = "VariantAlsoNegotiates";
      HttpStatusCode2[HttpStatusCode2["InsufficientStorage"] = 507] = "InsufficientStorage";
      HttpStatusCode2[HttpStatusCode2["LoopDetected"] = 508] = "LoopDetected";
      HttpStatusCode2[HttpStatusCode2["NotExtended"] = 510] = "NotExtended";
      HttpStatusCode2[HttpStatusCode2["NetworkAuthenticationRequired"] = 511] = "NetworkAuthenticationRequired";
    })(HttpStatusCode || (HttpStatusCode = {}));
    HttpClient = class _HttpClient {
      handler;
      constructor(handler) {
        this.handler = handler;
      }
      /**
       * Constructs an observable for a generic HTTP request that, when subscribed,
       * fires the request through the chain of registered interceptors and on to the
       * server.
       *
       * You can pass an `HttpRequest` directly as the only parameter. In this case,
       * the call returns an observable of the raw `HttpEvent` stream.
       *
       * Alternatively you can pass an HTTP method as the first parameter,
       * a URL string as the second, and an options hash containing the request body as the third.
       * See `addBody()`. In this case, the specified `responseType` and `observe` options determine the
       * type of returned observable.
       *   * The `responseType` value determines how a successful response body is parsed.
       *   * If `responseType` is the default `json`, you can pass a type interface for the resulting
       * object as a type parameter to the call.
       *
       * The `observe` value determines the return type, according to what you are interested in
       * observing.
       *   * An `observe` value of events returns an observable of the raw `HttpEvent` stream, including
       * progress events by default.
       *   * An `observe` value of response returns an observable of `HttpResponse<T>`,
       * where the `T` parameter depends on the `responseType` and any optionally provided type
       * parameter.
       *   * An `observe` value of body returns an observable of `<T>` with the same `T` body type.
       *
       */
      request(first, url, options = {}) {
        let req;
        if (first instanceof HttpRequest) {
          req = first;
        } else {
          let headers = void 0;
          if (options.headers instanceof HttpHeaders) {
            headers = options.headers;
          } else {
            headers = new HttpHeaders(options.headers);
          }
          let params = void 0;
          if (!!options.params) {
            if (options.params instanceof HttpParams) {
              params = options.params;
            } else {
              params = new HttpParams({ fromObject: options.params });
            }
          }
          req = new HttpRequest(first, url, options.body !== void 0 ? options.body : null, {
            headers,
            context: options.context,
            params,
            reportProgress: options.reportProgress,
            // By default, JSON is assumed to be returned for all calls.
            responseType: options.responseType || "json",
            withCredentials: options.withCredentials,
            transferCache: options.transferCache,
            keepalive: options.keepalive,
            priority: options.priority,
            cache: options.cache,
            mode: options.mode,
            redirect: options.redirect,
            credentials: options.credentials,
            referrer: options.referrer,
            integrity: options.integrity,
            timeout: options.timeout
          });
        }
        const events$ = of(req).pipe(concatMap((req2) => this.handler.handle(req2)));
        if (first instanceof HttpRequest || options.observe === "events") {
          return events$;
        }
        const res$ = events$.pipe(filter((event) => event instanceof HttpResponse));
        switch (options.observe || "body") {
          case "body":
            switch (req.responseType) {
              case "arraybuffer":
                return res$.pipe(map((res) => {
                  if (res.body !== null && !(res.body instanceof ArrayBuffer)) {
                    throw new RuntimeError(2806, ngDevMode && "Response is not an ArrayBuffer.");
                  }
                  return res.body;
                }));
              case "blob":
                return res$.pipe(map((res) => {
                  if (res.body !== null && !(res.body instanceof Blob)) {
                    throw new RuntimeError(2807, ngDevMode && "Response is not a Blob.");
                  }
                  return res.body;
                }));
              case "text":
                return res$.pipe(map((res) => {
                  if (res.body !== null && typeof res.body !== "string") {
                    throw new RuntimeError(2808, ngDevMode && "Response is not a string.");
                  }
                  return res.body;
                }));
              case "json":
              default:
                return res$.pipe(map((res) => res.body));
            }
          case "response":
            return res$;
          default:
            throw new RuntimeError(2809, ngDevMode && `Unreachable: unhandled observe type ${options.observe}}`);
        }
      }
      /**
       * Constructs an observable that, when subscribed, causes the configured
       * `DELETE` request to execute on the server. See the individual overloads for
       * details on the return type.
       *
       * @param url     The endpoint URL.
       * @param options The HTTP options to send with the request.
       *
       */
      delete(url, options = {}) {
        return this.request("DELETE", url, options);
      }
      /**
       * Constructs an observable that, when subscribed, causes the configured
       * `GET` request to execute on the server. See the individual overloads for
       * details on the return type.
       */
      get(url, options = {}) {
        return this.request("GET", url, options);
      }
      /**
       * Constructs an observable that, when subscribed, causes the configured
       * `HEAD` request to execute on the server. The `HEAD` method returns
       * meta information about the resource without transferring the
       * resource itself. See the individual overloads for
       * details on the return type.
       */
      head(url, options = {}) {
        return this.request("HEAD", url, options);
      }
      /**
       * Constructs an `Observable` that, when subscribed, causes a request with the special method
       * `JSONP` to be dispatched via the interceptor pipeline.
       * The [JSONP pattern](https://en.wikipedia.org/wiki/JSONP) works around limitations of certain
       * API endpoints that don't support newer,
       * and preferable [CORS](https://developer.mozilla.org/en-US/docs/Web/HTTP/CORS) protocol.
       * JSONP treats the endpoint API as a JavaScript file and tricks the browser to process the
       * requests even if the API endpoint is not located on the same domain (origin) as the client-side
       * application making the request.
       * The endpoint API must support JSONP callback for JSONP requests to work.
       * The resource API returns the JSON response wrapped in a callback function.
       * You can pass the callback function name as one of the query parameters.
       * Note that JSONP requests can only be used with `GET` requests.
       *
       * @param url The resource URL.
       * @param callbackParam The callback function name.
       *
       */
      jsonp(url, callbackParam) {
        return this.request("JSONP", url, {
          params: new HttpParams().append(callbackParam, "JSONP_CALLBACK"),
          observe: "body",
          responseType: "json"
        });
      }
      /**
       * Constructs an `Observable` that, when subscribed, causes the configured
       * `OPTIONS` request to execute on the server. This method allows the client
       * to determine the supported HTTP methods and other capabilities of an endpoint,
       * without implying a resource action. See the individual overloads for
       * details on the return type.
       */
      options(url, options = {}) {
        return this.request("OPTIONS", url, options);
      }
      /**
       * Constructs an observable that, when subscribed, causes the configured
       * `PATCH` request to execute on the server. See the individual overloads for
       * details on the return type.
       */
      patch(url, body, options = {}) {
        return this.request("PATCH", url, addBody(options, body));
      }
      /**
       * Constructs an observable that, when subscribed, causes the configured
       * `POST` request to execute on the server. The server responds with the location of
       * the replaced resource. See the individual overloads for
       * details on the return type.
       */
      post(url, body, options = {}) {
        return this.request("POST", url, addBody(options, body));
      }
      /**
       * Constructs an observable that, when subscribed, causes the configured
       * `PUT` request to execute on the server. The `PUT` method replaces an existing resource
       * with a new set of values.
       * See the individual overloads for details on the return type.
       */
      put(url, body, options = {}) {
        return this.request("PUT", url, addBody(options, body));
      }
      static \u0275fac = \u0275\u0275ngDeclareFactory({ minVersion: "12.0.0", version: "20.3.29", ngImport: core_exports, type: _HttpClient, deps: [{ token: HttpHandler }], target: FactoryTarget.Injectable });
      static \u0275prov = \u0275\u0275ngDeclareInjectable({ minVersion: "12.0.0", version: "20.3.29", ngImport: core_exports, type: _HttpClient });
    };
    \u0275\u0275ngDeclareClassMetadata({ minVersion: "12.0.0", version: "20.3.29", ngImport: core_exports, type: HttpClient, decorators: [{
      type: Injectable
    }], ctorParameters: () => [{ type: HttpHandler }] });
    XSSI_PREFIX$1 = /^\)\]\}',?\n/;
    FETCH_BACKEND = new InjectionToken(typeof ngDevMode === "undefined" || ngDevMode ? "FETCH_BACKEND" : "");
    FetchBackend = class _FetchBackend {
      // We use an arrow function to always reference the current global implementation of `fetch`.
      // This is helpful for cases when the global `fetch` implementation is modified by external code,
      // see https://github.com/angular/angular/issues/57527.
      fetchImpl = inject(FetchFactory, { optional: true })?.fetch ?? ((...args) => globalThis.fetch(...args));
      ngZone = inject(NgZone);
      destroyRef = inject(DestroyRef);
      handle(request) {
        return new Observable((observer) => {
          const aborter = new AbortController();
          this.doRequest(request, aborter.signal, observer).then(noop, (error) => observer.error(new HttpErrorResponse({ error })));
          let timeoutId;
          if (request.timeout) {
            timeoutId = this.ngZone.runOutsideAngular(() => setTimeout(() => {
              if (!aborter.signal.aborted) {
                aborter.abort(new DOMException("signal timed out", "TimeoutError"));
              }
            }, request.timeout));
          }
          return () => {
            if (timeoutId !== void 0) {
              clearTimeout(timeoutId);
            }
            aborter.abort();
          };
        });
      }
      doRequest(request, signal2, observer) {
        return __async(this, null, function* () {
          const init = this.createRequestInit(request);
          let response;
          try {
            const fetchPromise = this.ngZone.runOutsideAngular(() => this.fetchImpl(request.urlWithParams, __spreadValues({ signal: signal2 }, init)));
            silenceSuperfluousUnhandledPromiseRejection(fetchPromise);
            observer.next({ type: HttpEventType.Sent });
            response = yield fetchPromise;
          } catch (error) {
            observer.error(new HttpErrorResponse({
              error,
              status: error.status ?? 0,
              statusText: error.statusText,
              url: request.urlWithParams,
              headers: error.headers
            }));
            return;
          }
          const headers = new HttpHeaders(response.headers);
          const statusText = response.statusText;
          const url = getResponseUrl$1(response) ?? request.urlWithParams;
          let status = response.status;
          let body = null;
          if (request.reportProgress) {
            observer.next(new HttpHeaderResponse({ headers, status, statusText, url }));
          }
          if (response.body) {
            const contentLength = response.headers.get("content-length");
            const chunks = [];
            const reader = response.body.getReader();
            let receivedLength = 0;
            let decoder;
            let partialText;
            const reqZone = typeof Zone !== "undefined" && Zone.current;
            let canceled = false;
            yield this.ngZone.runOutsideAngular(() => __async(this, null, function* () {
              while (true) {
                if (this.destroyRef.destroyed) {
                  yield reader.cancel();
                  canceled = true;
                  break;
                }
                const { done, value } = yield reader.read();
                if (done) {
                  break;
                }
                chunks.push(value);
                receivedLength += value.length;
                if (request.reportProgress) {
                  partialText = request.responseType === "text" ? (partialText ?? "") + (decoder ??= new TextDecoder()).decode(value, { stream: true }) : void 0;
                  const reportProgress = () => observer.next({
                    type: HttpEventType.DownloadProgress,
                    total: contentLength ? +contentLength : void 0,
                    loaded: receivedLength,
                    partialText
                  });
                  reqZone ? reqZone.run(reportProgress) : reportProgress();
                }
              }
            }));
            if (canceled) {
              observer.complete();
              return;
            }
            const chunksAll = this.concatChunks(chunks, receivedLength);
            try {
              const contentType = response.headers.get(CONTENT_TYPE_HEADER) ?? "";
              body = this.parseBody(request, chunksAll, contentType, status);
            } catch (error) {
              observer.error(new HttpErrorResponse({
                error,
                headers: new HttpHeaders(response.headers),
                status: response.status,
                statusText: response.statusText,
                url: getResponseUrl$1(response) ?? request.urlWithParams
              }));
              return;
            }
          }
          if (status === 0) {
            status = body ? HTTP_STATUS_CODE_OK : 0;
          }
          const ok = status >= 200 && status < 300;
          const redirected = response.redirected;
          if (ok) {
            observer.next(new HttpResponse({
              body,
              headers,
              status,
              statusText,
              url,
              redirected
            }));
            observer.complete();
          } else {
            observer.error(new HttpErrorResponse({
              error: body,
              headers,
              status,
              statusText,
              url,
              redirected
            }));
          }
        });
      }
      parseBody(request, binContent, contentType, status) {
        switch (request.responseType) {
          case "json":
            const text = new TextDecoder().decode(binContent).replace(XSSI_PREFIX$1, "");
            if (text === "") {
              return null;
            }
            try {
              return JSON.parse(text);
            } catch (e) {
              if (status < 200 || status >= 300) {
                return text;
              }
              throw e;
            }
          case "text":
            return new TextDecoder().decode(binContent);
          case "blob":
            return new Blob([binContent], { type: contentType });
          case "arraybuffer":
            return binContent.buffer;
        }
      }
      createRequestInit(req) {
        const headers = {};
        let credentials;
        credentials = req.credentials;
        if (req.withCredentials) {
          (typeof ngDevMode === "undefined" || ngDevMode) && warningOptionsMessage(req);
          credentials = "include";
        }
        req.headers.forEach((name, values) => headers[name] = values.join(","));
        if (!req.headers.has(ACCEPT_HEADER)) {
          headers[ACCEPT_HEADER] = ACCEPT_HEADER_VALUE;
        }
        if (!req.headers.has(CONTENT_TYPE_HEADER)) {
          const detectedType = req.detectContentTypeHeader();
          if (detectedType !== null) {
            headers[CONTENT_TYPE_HEADER] = detectedType;
          }
        }
        return {
          body: req.serializeBody(),
          method: req.method,
          headers,
          credentials,
          keepalive: req.keepalive,
          cache: req.cache,
          priority: req.priority,
          mode: req.mode,
          redirect: req.redirect,
          referrer: req.referrer,
          integrity: req.integrity
        };
      }
      concatChunks(chunks, totalLength) {
        const chunksAll = new Uint8Array(totalLength);
        let position = 0;
        for (const chunk of chunks) {
          chunksAll.set(chunk, position);
          position += chunk.length;
        }
        return chunksAll;
      }
      static \u0275fac = \u0275\u0275ngDeclareFactory({ minVersion: "12.0.0", version: "20.3.29", ngImport: core_exports, type: _FetchBackend, deps: [], target: FactoryTarget.Injectable });
      static \u0275prov = \u0275\u0275ngDeclareInjectable({ minVersion: "12.0.0", version: "20.3.29", ngImport: core_exports, type: _FetchBackend });
    };
    \u0275\u0275ngDeclareClassMetadata({ minVersion: "12.0.0", version: "20.3.29", ngImport: core_exports, type: FetchBackend, decorators: [{
      type: Injectable
    }] });
    FetchFactory = class {
    };
    HTTP_INTERCEPTORS = new InjectionToken(ngDevMode ? "HTTP_INTERCEPTORS" : "");
    HTTP_INTERCEPTOR_FNS = new InjectionToken(ngDevMode ? "HTTP_INTERCEPTOR_FNS" : "");
    HTTP_ROOT_INTERCEPTOR_FNS = new InjectionToken(ngDevMode ? "HTTP_ROOT_INTERCEPTOR_FNS" : "");
    REQUESTS_CONTRIBUTE_TO_STABILITY = new InjectionToken(ngDevMode ? "REQUESTS_CONTRIBUTE_TO_STABILITY" : "", { providedIn: "root", factory: () => true });
    fetchBackendWarningDisplayed = false;
    HttpInterceptorHandler = class _HttpInterceptorHandler extends HttpHandler {
      backend;
      injector;
      chain = null;
      pendingTasks = inject(PendingTasks);
      contributeToStability = inject(REQUESTS_CONTRIBUTE_TO_STABILITY);
      constructor(backend, injector) {
        super();
        this.backend = backend;
        this.injector = injector;
        if ((typeof ngDevMode === "undefined" || ngDevMode) && !fetchBackendWarningDisplayed) {
          const isTestingBackend = this.backend.isTestingBackend;
          if (false) {
            fetchBackendWarningDisplayed = true;
            injector.get(Console).warn(formatRuntimeError(2801, "Angular detected that `HttpClient` is not configured to use `fetch` APIs. It's strongly recommended to enable `fetch` for applications that use Server-Side Rendering for better performance and compatibility. To enable `fetch`, add the `withFetch()` to the `provideHttpClient()` call at the root of the application."));
          }
        }
      }
      handle(initialRequest) {
        if (this.chain === null) {
          const parentHandler = this.injector.get(HttpHandler, null, { skipSelf: true });
          const isDelegating = parentHandler !== null && this.backend === parentHandler;
          const rootInterceptorFns = this.injector.get(HTTP_ROOT_INTERCEPTOR_FNS, [], isDelegating ? { self: true } : void 0);
          const dedupedInterceptorFns = Array.from(/* @__PURE__ */ new Set([...this.injector.get(HTTP_INTERCEPTOR_FNS), ...rootInterceptorFns]));
          this.chain = dedupedInterceptorFns.reduceRight((nextSequencedFn, interceptorFn) => chainedInterceptorFn(nextSequencedFn, interceptorFn, this.injector), interceptorChainEndFn);
        }
        if (this.contributeToStability) {
          const removeTask = this.pendingTasks.add();
          return this.chain(initialRequest, (downstreamRequest) => this.backend.handle(downstreamRequest)).pipe(finalize(removeTask));
        } else {
          return this.chain(initialRequest, (downstreamRequest) => this.backend.handle(downstreamRequest));
        }
      }
      static \u0275fac = \u0275\u0275ngDeclareFactory({ minVersion: "12.0.0", version: "20.3.29", ngImport: core_exports, type: _HttpInterceptorHandler, deps: [{ token: HttpBackend }, { token: EnvironmentInjector }], target: FactoryTarget.Injectable });
      static \u0275prov = \u0275\u0275ngDeclareInjectable({ minVersion: "12.0.0", version: "20.3.29", ngImport: core_exports, type: _HttpInterceptorHandler });
    };
    \u0275\u0275ngDeclareClassMetadata({ minVersion: "12.0.0", version: "20.3.29", ngImport: core_exports, type: HttpInterceptorHandler, decorators: [{
      type: Injectable
    }], ctorParameters: () => [{ type: HttpBackend }, { type: EnvironmentInjector }] });
    nextRequestId = 0;
    JSONP_ERR_NO_CALLBACK = "JSONP injected script did not invoke callback.";
    JSONP_ERR_WRONG_METHOD = "JSONP requests must use JSONP request method.";
    JSONP_ERR_WRONG_RESPONSE_TYPE = "JSONP requests must use Json response type.";
    JSONP_ERR_HEADERS_NOT_SUPPORTED = "JSONP requests do not support headers.";
    JSONP_ERR_UNSAFE_URL = "JSONP requests only support absolute URLs with HTTP(S) protocols.";
    JsonpCallbackContext = class {
    };
    JsonpClientBackend = class _JsonpClientBackend {
      callbackMap;
      document;
      /**
       * A resolved promise that can be used to schedule microtasks in the event handlers.
       */
      resolvedPromise = Promise.resolve();
      constructor(callbackMap, document2) {
        this.callbackMap = callbackMap;
        this.document = document2;
      }
      /**
       * Get the name of the next callback method, by incrementing the global `nextRequestId`.
       */
      nextCallback() {
        return `ng_jsonp_callback_${nextRequestId++}`;
      }
      /**
       * Processes a JSONP request and returns an event stream of the results.
       * @param req The request object.
       * @returns An observable of the response events.
       *
       */
      handle(req) {
        if (req.method !== "JSONP") {
          throw new RuntimeError(2810, ngDevMode && JSONP_ERR_WRONG_METHOD);
        } else if (req.responseType !== "json") {
          throw new RuntimeError(2811, ngDevMode && JSONP_ERR_WRONG_RESPONSE_TYPE);
        }
        if (req.headers.keys().length > 0) {
          throw new RuntimeError(2812, ngDevMode && JSONP_ERR_HEADERS_NOT_SUPPORTED);
        }
        if (!this.isAllowedJsonpUrl(req.urlWithParams)) {
          throw new RuntimeError(2826, ngDevMode && JSONP_ERR_UNSAFE_URL);
        }
        return new Observable((observer) => {
          const callback = this.nextCallback();
          const url = req.urlWithParams.replace(/=JSONP_CALLBACK(&|$)/, `=${callback}$1`);
          const node = this.document.createElement("script");
          node.src = url;
          let body = null;
          let finished = false;
          this.callbackMap[callback] = (data) => {
            delete this.callbackMap[callback];
            body = data;
            finished = true;
          };
          const cleanup = () => {
            node.removeEventListener("load", onLoad);
            node.removeEventListener("error", onError);
            node.remove();
            delete this.callbackMap[callback];
          };
          const onLoad = () => {
            this.resolvedPromise.then(() => {
              cleanup();
              if (!finished) {
                observer.error(new HttpErrorResponse({
                  url,
                  status: 0,
                  statusText: "JSONP Error",
                  error: new Error(JSONP_ERR_NO_CALLBACK)
                }));
                return;
              }
              observer.next(new HttpResponse({
                body,
                status: HTTP_STATUS_CODE_OK,
                statusText: "OK",
                url
              }));
              observer.complete();
            });
          };
          const onError = (error) => {
            cleanup();
            observer.error(new HttpErrorResponse({
              error,
              status: 0,
              statusText: "JSONP Error",
              url
            }));
          };
          node.addEventListener("load", onLoad);
          node.addEventListener("error", onError);
          this.document.body.appendChild(node);
          observer.next({ type: HttpEventType.Sent });
          return () => {
            if (!finished) {
              this.removeListeners(node);
            }
            cleanup();
          };
        });
      }
      removeListeners(script) {
        foreignDocument ??= this.document.implementation.createHTMLDocument();
        foreignDocument.adoptNode(script);
      }
      isAllowedJsonpUrl(url) {
        return /^https?:\/\//i.test(url);
      }
      static \u0275fac = \u0275\u0275ngDeclareFactory({ minVersion: "12.0.0", version: "20.3.29", ngImport: core_exports, type: _JsonpClientBackend, deps: [{ token: JsonpCallbackContext }, { token: DOCUMENT }], target: FactoryTarget.Injectable });
      static \u0275prov = \u0275\u0275ngDeclareInjectable({ minVersion: "12.0.0", version: "20.3.29", ngImport: core_exports, type: _JsonpClientBackend });
    };
    \u0275\u0275ngDeclareClassMetadata({ minVersion: "12.0.0", version: "20.3.29", ngImport: core_exports, type: JsonpClientBackend, decorators: [{
      type: Injectable
    }], ctorParameters: () => [{ type: JsonpCallbackContext }, { type: void 0, decorators: [{
      type: Inject,
      args: [DOCUMENT]
    }] }] });
    JsonpInterceptor = class _JsonpInterceptor {
      injector;
      constructor(injector) {
        this.injector = injector;
      }
      /**
       * Identifies and handles a given JSONP request.
       * @param initialRequest The outgoing request object to handle.
       * @param next The next interceptor in the chain, or the backend
       * if no interceptors remain in the chain.
       * @returns An observable of the event stream.
       */
      intercept(initialRequest, next) {
        return runInInjectionContext(this.injector, () => jsonpInterceptorFn(initialRequest, (downstreamRequest) => next.handle(downstreamRequest)));
      }
      static \u0275fac = \u0275\u0275ngDeclareFactory({ minVersion: "12.0.0", version: "20.3.29", ngImport: core_exports, type: _JsonpInterceptor, deps: [{ token: EnvironmentInjector }], target: FactoryTarget.Injectable });
      static \u0275prov = \u0275\u0275ngDeclareInjectable({ minVersion: "12.0.0", version: "20.3.29", ngImport: core_exports, type: _JsonpInterceptor });
    };
    \u0275\u0275ngDeclareClassMetadata({ minVersion: "12.0.0", version: "20.3.29", ngImport: core_exports, type: JsonpInterceptor, decorators: [{
      type: Injectable
    }], ctorParameters: () => [{ type: EnvironmentInjector }] });
    XSSI_PREFIX = /^\)\]\}',?\n/;
    X_REQUEST_URL_REGEXP = RegExp(`^${X_REQUEST_URL_HEADER}:`, "m");
    HttpXhrBackend = class _HttpXhrBackend {
      xhrFactory;
      constructor(xhrFactory) {
        this.xhrFactory = xhrFactory;
      }
      /**
       * Processes a request and returns a stream of response events.
       * @param req The request object.
       * @returns An observable of the response events.
       */
      handle(req) {
        if (req.method === "JSONP") {
          throw new RuntimeError(-2800, (typeof ngDevMode === "undefined" || ngDevMode) && `Cannot make a JSONP request without JSONP support. To fix the problem, either add the \`withJsonpSupport()\` call (if \`provideHttpClient()\` is used) or import the \`HttpClientJsonpModule\` in the root NgModule.`);
        }
        ngDevMode && validateXhrCompatibility(req);
        const xhrFactory = this.xhrFactory;
        const source = (
          // Note that `ɵloadImpl` is never defined in client bundles and can be
          // safely dropped whenever we're running in the browser.
          // This branching is redundant.
          // The `ngServerMode` guard also enables tree-shaking of the `from()`
          // function from the common bundle, as it's only used in server code.
          false ? from(xhrFactory.\u0275loadImpl()) : of(null)
        );
        return source.pipe(switchMap(() => {
          return new Observable((observer) => {
            const xhr = xhrFactory.build();
            xhr.open(req.method, req.urlWithParams);
            if (req.withCredentials) {
              xhr.withCredentials = true;
            }
            req.headers.forEach((name, values) => xhr.setRequestHeader(name, values.join(",")));
            if (!req.headers.has(ACCEPT_HEADER)) {
              xhr.setRequestHeader(ACCEPT_HEADER, ACCEPT_HEADER_VALUE);
            }
            if (!req.headers.has(CONTENT_TYPE_HEADER)) {
              const detectedType = req.detectContentTypeHeader();
              if (detectedType !== null) {
                xhr.setRequestHeader(CONTENT_TYPE_HEADER, detectedType);
              }
            }
            if (req.timeout) {
              xhr.timeout = req.timeout;
            }
            if (req.responseType) {
              const responseType = req.responseType.toLowerCase();
              xhr.responseType = responseType !== "json" ? responseType : "text";
            }
            const reqBody = req.serializeBody();
            let headerResponse = null;
            const partialFromXhr = () => {
              if (headerResponse !== null) {
                return headerResponse;
              }
              const statusText = xhr.statusText || "OK";
              const headers = new HttpHeaders(xhr.getAllResponseHeaders());
              const url = getResponseUrl(xhr) || req.url;
              headerResponse = new HttpHeaderResponse({ headers, status: xhr.status, statusText, url });
              return headerResponse;
            };
            const onLoad = () => {
              let { headers, status, statusText, url } = partialFromXhr();
              let body = null;
              if (status !== HTTP_STATUS_CODE_NO_CONTENT) {
                body = typeof xhr.response === "undefined" ? xhr.responseText : xhr.response;
              }
              if (status === 0) {
                status = !!body ? HTTP_STATUS_CODE_OK : 0;
              }
              let ok = status >= 200 && status < 300;
              if (req.responseType === "json" && typeof body === "string") {
                const originalBody = body;
                body = body.replace(XSSI_PREFIX, "");
                try {
                  body = body !== "" ? JSON.parse(body) : null;
                } catch (error) {
                  body = originalBody;
                  if (ok) {
                    ok = false;
                    body = { error, text: body };
                  }
                }
              }
              if (ok) {
                observer.next(new HttpResponse({
                  body,
                  headers,
                  status,
                  statusText,
                  url: url || void 0
                }));
                observer.complete();
              } else {
                observer.error(new HttpErrorResponse({
                  // The error in this case is the response body (error from the server).
                  error: body,
                  headers,
                  status,
                  statusText,
                  url: url || void 0
                }));
              }
            };
            const onError = (error) => {
              const { url } = partialFromXhr();
              const res = new HttpErrorResponse({
                error,
                status: xhr.status || 0,
                statusText: xhr.statusText || "Unknown Error",
                url: url || void 0
              });
              observer.error(res);
            };
            let onTimeout = onError;
            if (req.timeout) {
              onTimeout = (_) => {
                const { url } = partialFromXhr();
                const res = new HttpErrorResponse({
                  error: new DOMException("Request timed out", "TimeoutError"),
                  status: xhr.status || 0,
                  statusText: xhr.statusText || "Request timeout",
                  url: url || void 0
                });
                observer.error(res);
              };
            }
            let sentHeaders = false;
            const onDownProgress = (event) => {
              if (!sentHeaders) {
                observer.next(partialFromXhr());
                sentHeaders = true;
              }
              let progressEvent = {
                type: HttpEventType.DownloadProgress,
                loaded: event.loaded
              };
              if (event.lengthComputable) {
                progressEvent.total = event.total;
              }
              if (req.responseType === "text" && !!xhr.responseText) {
                progressEvent.partialText = xhr.responseText;
              }
              observer.next(progressEvent);
            };
            const onUpProgress = (event) => {
              let progress = {
                type: HttpEventType.UploadProgress,
                loaded: event.loaded
              };
              if (event.lengthComputable) {
                progress.total = event.total;
              }
              observer.next(progress);
            };
            xhr.addEventListener("load", onLoad);
            xhr.addEventListener("error", onError);
            xhr.addEventListener("timeout", onTimeout);
            xhr.addEventListener("abort", onError);
            if (req.reportProgress) {
              xhr.addEventListener("progress", onDownProgress);
              if (reqBody !== null && xhr.upload) {
                xhr.upload.addEventListener("progress", onUpProgress);
              }
            }
            xhr.send(reqBody);
            observer.next({ type: HttpEventType.Sent });
            return () => {
              xhr.removeEventListener("error", onError);
              xhr.removeEventListener("abort", onError);
              xhr.removeEventListener("load", onLoad);
              xhr.removeEventListener("timeout", onTimeout);
              if (req.reportProgress) {
                xhr.removeEventListener("progress", onDownProgress);
                if (reqBody !== null && xhr.upload) {
                  xhr.upload.removeEventListener("progress", onUpProgress);
                }
              }
              if (xhr.readyState !== xhr.DONE) {
                xhr.abort();
              }
            };
          });
        }));
      }
      static \u0275fac = \u0275\u0275ngDeclareFactory({ minVersion: "12.0.0", version: "20.3.29", ngImport: core_exports, type: _HttpXhrBackend, deps: [{ token: XhrFactory }], target: FactoryTarget.Injectable });
      static \u0275prov = \u0275\u0275ngDeclareInjectable({ minVersion: "12.0.0", version: "20.3.29", ngImport: core_exports, type: _HttpXhrBackend });
    };
    \u0275\u0275ngDeclareClassMetadata({ minVersion: "12.0.0", version: "20.3.29", ngImport: core_exports, type: HttpXhrBackend, decorators: [{
      type: Injectable
    }], ctorParameters: () => [{ type: XhrFactory }] });
    XSRF_ENABLED = new InjectionToken(ngDevMode ? "XSRF_ENABLED" : "");
    XSRF_DEFAULT_COOKIE_NAME = "XSRF-TOKEN";
    XSRF_COOKIE_NAME = new InjectionToken(ngDevMode ? "XSRF_COOKIE_NAME" : "", {
      providedIn: "root",
      factory: () => XSRF_DEFAULT_COOKIE_NAME
    });
    XSRF_DEFAULT_HEADER_NAME = "X-XSRF-TOKEN";
    XSRF_HEADER_NAME = new InjectionToken(ngDevMode ? "XSRF_HEADER_NAME" : "", {
      providedIn: "root",
      factory: () => XSRF_DEFAULT_HEADER_NAME
    });
    HttpXsrfTokenExtractor = class {
    };
    HttpXsrfCookieExtractor = class _HttpXsrfCookieExtractor {
      doc;
      cookieName;
      lastCookieString = "";
      lastToken = null;
      /**
       * @internal for testing
       */
      parseCount = 0;
      constructor(doc, cookieName) {
        this.doc = doc;
        this.cookieName = cookieName;
      }
      getToken() {
        if (false) {
          return null;
        }
        const cookieString = this.doc.cookie || "";
        if (cookieString !== this.lastCookieString) {
          this.parseCount++;
          this.lastToken = parseCookieValue(cookieString, this.cookieName);
          this.lastCookieString = cookieString;
        }
        return this.lastToken;
      }
      static \u0275fac = \u0275\u0275ngDeclareFactory({ minVersion: "12.0.0", version: "20.3.29", ngImport: core_exports, type: _HttpXsrfCookieExtractor, deps: [{ token: DOCUMENT }, { token: XSRF_COOKIE_NAME }], target: FactoryTarget.Injectable });
      static \u0275prov = \u0275\u0275ngDeclareInjectable({ minVersion: "12.0.0", version: "20.3.29", ngImport: core_exports, type: _HttpXsrfCookieExtractor });
    };
    \u0275\u0275ngDeclareClassMetadata({ minVersion: "12.0.0", version: "20.3.29", ngImport: core_exports, type: HttpXsrfCookieExtractor, decorators: [{
      type: Injectable
    }], ctorParameters: () => [{ type: void 0, decorators: [{
      type: Inject,
      args: [DOCUMENT]
    }] }, { type: void 0, decorators: [{
      type: Inject,
      args: [XSRF_COOKIE_NAME]
    }] }] });
    ABSOLUTE_URL_REGEX = /^(?:https?:)?\/\//i;
    HttpXsrfInterceptor = class _HttpXsrfInterceptor {
      injector;
      constructor(injector) {
        this.injector = injector;
      }
      intercept(initialRequest, next) {
        return runInInjectionContext(this.injector, () => xsrfInterceptorFn(initialRequest, (downstreamRequest) => next.handle(downstreamRequest)));
      }
      static \u0275fac = \u0275\u0275ngDeclareFactory({ minVersion: "12.0.0", version: "20.3.29", ngImport: core_exports, type: _HttpXsrfInterceptor, deps: [{ token: EnvironmentInjector }], target: FactoryTarget.Injectable });
      static \u0275prov = \u0275\u0275ngDeclareInjectable({ minVersion: "12.0.0", version: "20.3.29", ngImport: core_exports, type: _HttpXsrfInterceptor });
    };
    \u0275\u0275ngDeclareClassMetadata({ minVersion: "12.0.0", version: "20.3.29", ngImport: core_exports, type: HttpXsrfInterceptor, decorators: [{
      type: Injectable
    }], ctorParameters: () => [{ type: EnvironmentInjector }] });
    (function(HttpFeatureKind2) {
      HttpFeatureKind2[HttpFeatureKind2["Interceptors"] = 0] = "Interceptors";
      HttpFeatureKind2[HttpFeatureKind2["LegacyInterceptors"] = 1] = "LegacyInterceptors";
      HttpFeatureKind2[HttpFeatureKind2["CustomXsrfConfiguration"] = 2] = "CustomXsrfConfiguration";
      HttpFeatureKind2[HttpFeatureKind2["NoXsrfProtection"] = 3] = "NoXsrfProtection";
      HttpFeatureKind2[HttpFeatureKind2["JsonpSupport"] = 4] = "JsonpSupport";
      HttpFeatureKind2[HttpFeatureKind2["RequestsMadeViaParent"] = 5] = "RequestsMadeViaParent";
      HttpFeatureKind2[HttpFeatureKind2["Fetch"] = 6] = "Fetch";
    })(HttpFeatureKind || (HttpFeatureKind = {}));
    LEGACY_INTERCEPTOR_FN = new InjectionToken(ngDevMode ? "LEGACY_INTERCEPTOR_FN" : "");
    HttpClientXsrfModule = class _HttpClientXsrfModule {
      /**
       * Disable the default XSRF protection.
       */
      static disable() {
        return {
          ngModule: _HttpClientXsrfModule,
          providers: [withNoXsrfProtection().\u0275providers]
        };
      }
      /**
       * Configure XSRF protection.
       * @param options An object that can specify either or both
       * cookie name or header name.
       * - Cookie name default is `XSRF-TOKEN`.
       * - Header name default is `X-XSRF-TOKEN`.
       *
       */
      static withOptions(options = {}) {
        return {
          ngModule: _HttpClientXsrfModule,
          providers: withXsrfConfiguration(options).\u0275providers
        };
      }
      static \u0275fac = \u0275\u0275ngDeclareFactory({ minVersion: "12.0.0", version: "20.3.29", ngImport: core_exports, type: _HttpClientXsrfModule, deps: [], target: FactoryTarget.NgModule });
      static \u0275mod = \u0275\u0275ngDeclareNgModule({ minVersion: "14.0.0", version: "20.3.29", ngImport: core_exports, type: _HttpClientXsrfModule });
      static \u0275inj = \u0275\u0275ngDeclareInjector({ minVersion: "12.0.0", version: "20.3.29", ngImport: core_exports, type: _HttpClientXsrfModule, providers: [
        HttpXsrfInterceptor,
        { provide: HTTP_INTERCEPTORS, useExisting: HttpXsrfInterceptor, multi: true },
        { provide: HttpXsrfTokenExtractor, useClass: HttpXsrfCookieExtractor },
        withXsrfConfiguration({
          cookieName: XSRF_DEFAULT_COOKIE_NAME,
          headerName: XSRF_DEFAULT_HEADER_NAME
        }).\u0275providers,
        { provide: XSRF_ENABLED, useValue: true }
      ] });
    };
    \u0275\u0275ngDeclareClassMetadata({ minVersion: "12.0.0", version: "20.3.29", ngImport: core_exports, type: HttpClientXsrfModule, decorators: [{
      type: NgModule,
      args: [{
        providers: [
          HttpXsrfInterceptor,
          { provide: HTTP_INTERCEPTORS, useExisting: HttpXsrfInterceptor, multi: true },
          { provide: HttpXsrfTokenExtractor, useClass: HttpXsrfCookieExtractor },
          withXsrfConfiguration({
            cookieName: XSRF_DEFAULT_COOKIE_NAME,
            headerName: XSRF_DEFAULT_HEADER_NAME
          }).\u0275providers,
          { provide: XSRF_ENABLED, useValue: true }
        ]
      }]
    }] });
    HttpClientModule = class _HttpClientModule {
      static \u0275fac = \u0275\u0275ngDeclareFactory({ minVersion: "12.0.0", version: "20.3.29", ngImport: core_exports, type: _HttpClientModule, deps: [], target: FactoryTarget.NgModule });
      static \u0275mod = \u0275\u0275ngDeclareNgModule({ minVersion: "14.0.0", version: "20.3.29", ngImport: core_exports, type: _HttpClientModule });
      static \u0275inj = \u0275\u0275ngDeclareInjector({ minVersion: "12.0.0", version: "20.3.29", ngImport: core_exports, type: _HttpClientModule, providers: [provideHttpClient(withInterceptorsFromDi())] });
    };
    \u0275\u0275ngDeclareClassMetadata({ minVersion: "12.0.0", version: "20.3.29", ngImport: core_exports, type: HttpClientModule, decorators: [{
      type: NgModule,
      args: [{
        /**
         * Configures the dependency injector where it is imported
         * with supporting services for HTTP communications.
         */
        providers: [provideHttpClient(withInterceptorsFromDi())]
      }]
    }] });
    HttpClientJsonpModule = class _HttpClientJsonpModule {
      static \u0275fac = \u0275\u0275ngDeclareFactory({ minVersion: "12.0.0", version: "20.3.29", ngImport: core_exports, type: _HttpClientJsonpModule, deps: [], target: FactoryTarget.NgModule });
      static \u0275mod = \u0275\u0275ngDeclareNgModule({ minVersion: "14.0.0", version: "20.3.29", ngImport: core_exports, type: _HttpClientJsonpModule });
      static \u0275inj = \u0275\u0275ngDeclareInjector({ minVersion: "12.0.0", version: "20.3.29", ngImport: core_exports, type: _HttpClientJsonpModule, providers: [withJsonpSupport().\u0275providers] });
    };
    \u0275\u0275ngDeclareClassMetadata({ minVersion: "12.0.0", version: "20.3.29", ngImport: core_exports, type: HttpClientJsonpModule, decorators: [{
      type: NgModule,
      args: [{
        providers: [withJsonpSupport().\u0275providers]
      }]
    }] });
  }
});

// node_modules/@angular/common/fesm2022/http.mjs
function makeHttpResourceFn(responseType) {
  return function httpResource2(request, options) {
    if (ngDevMode && !options?.injector) {
      assertInInjectionContext(httpResource2);
    }
    const injector = options?.injector ?? inject(Injector);
    return new HttpResourceImpl(injector, () => normalizeRequest(request, responseType), options?.defaultValue, options?.parse, options?.equal);
  };
}
function normalizeRequest(request, responseType) {
  let unwrappedRequest = typeof request === "function" ? request() : request;
  if (unwrappedRequest === void 0) {
    return void 0;
  } else if (typeof unwrappedRequest === "string") {
    unwrappedRequest = { url: unwrappedRequest };
  }
  const headers = unwrappedRequest.headers instanceof HttpHeaders ? unwrappedRequest.headers : new HttpHeaders(unwrappedRequest.headers);
  const params = unwrappedRequest.params instanceof HttpParams ? unwrappedRequest.params : new HttpParams({ fromObject: unwrappedRequest.params });
  return new HttpRequest(unwrappedRequest.method ?? "GET", unwrappedRequest.url, unwrappedRequest.body ?? null, {
    headers,
    params,
    reportProgress: unwrappedRequest.reportProgress,
    withCredentials: unwrappedRequest.withCredentials,
    keepalive: unwrappedRequest.keepalive,
    cache: unwrappedRequest.cache,
    priority: unwrappedRequest.priority,
    mode: unwrappedRequest.mode,
    redirect: unwrappedRequest.redirect,
    responseType,
    context: unwrappedRequest.context,
    transferCache: unwrappedRequest.transferCache,
    credentials: unwrappedRequest.credentials,
    referrer: unwrappedRequest.referrer,
    integrity: unwrappedRequest.integrity,
    timeout: unwrappedRequest.timeout
  });
}
var httpResource, HttpResourceImpl, HTTP_TRANSFER_CACHE_ORIGIN_MAP, CACHE_OPTIONS;
var init_http = __esm({
  "node_modules/@angular/common/fesm2022/http.mjs"() {
    "use strict";
    init_module();
    init_module();
    init_core();
    httpResource = (() => {
      const jsonFn = makeHttpResourceFn("json");
      jsonFn.arrayBuffer = makeHttpResourceFn("arraybuffer");
      jsonFn.blob = makeHttpResourceFn("blob");
      jsonFn.text = makeHttpResourceFn("text");
      return jsonFn;
    })();
    HttpResourceImpl = class extends ResourceImpl {
      client;
      _headers = linkedSignal(...ngDevMode ? [{
        debugName: "_headers",
        source: this.extRequest,
        computation: () => void 0
      }] : [{
        source: this.extRequest,
        computation: () => void 0
      }]);
      _progress = linkedSignal(...ngDevMode ? [{
        debugName: "_progress",
        source: this.extRequest,
        computation: () => void 0
      }] : [{
        source: this.extRequest,
        computation: () => void 0
      }]);
      _statusCode = linkedSignal(...ngDevMode ? [{
        debugName: "_statusCode",
        source: this.extRequest,
        computation: () => void 0
      }] : [{
        source: this.extRequest,
        computation: () => void 0
      }]);
      headers = computed(() => this.status() === "resolved" || this.status() === "error" ? this._headers() : void 0, ...ngDevMode ? [{ debugName: "headers" }] : []);
      progress = this._progress.asReadonly();
      statusCode = this._statusCode.asReadonly();
      constructor(injector, request, defaultValue, parse, equal) {
        super(request, ({ params: request2, abortSignal }) => {
          let sub;
          const onAbort = () => sub.unsubscribe();
          abortSignal.addEventListener("abort", onAbort);
          const stream = signal({ value: void 0 }, ...ngDevMode ? [{ debugName: "stream" }] : []);
          let resolve;
          const promise = new Promise((r) => resolve = r);
          const send = (value) => {
            stream.set(value);
            resolve?.(stream);
            resolve = void 0;
          };
          sub = this.client.request(request2).subscribe({
            next: (event) => {
              switch (event.type) {
                case HttpEventType.Response:
                  this._headers.set(event.headers);
                  this._statusCode.set(event.status);
                  try {
                    send({ value: parse ? parse(event.body) : event.body });
                  } catch (error) {
                    send({ error: encapsulateResourceError(error) });
                  }
                  break;
                case HttpEventType.DownloadProgress:
                  this._progress.set(event);
                  break;
              }
            },
            error: (error) => {
              if (error instanceof HttpErrorResponse) {
                this._headers.set(error.headers);
                this._statusCode.set(error.status);
              }
              send({ error });
              abortSignal.removeEventListener("abort", onAbort);
            },
            complete: () => {
              if (resolve) {
                send({
                  error: new RuntimeError(991, ngDevMode && "Resource completed before producing a value")
                });
              }
              abortSignal.removeEventListener("abort", onAbort);
            }
          });
          return promise;
        }, defaultValue, equal, injector);
        this.client = injector.get(HttpClient);
      }
      set(value) {
        super.set(value);
        this._headers.set(void 0);
        this._progress.set(void 0);
        this._statusCode.set(void 0);
      }
    };
    HTTP_TRANSFER_CACHE_ORIGIN_MAP = new InjectionToken(ngDevMode ? "HTTP_TRANSFER_CACHE_ORIGIN_MAP" : "");
    CACHE_OPTIONS = new InjectionToken(ngDevMode ? "HTTP_TRANSFER_STATE_CACHE_OPTIONS" : "");
  }
});

// node_modules/@angular/common/fesm2022/http/testing.mjs
function _toArrayBufferBody(body) {
  if (typeof ArrayBuffer === "undefined") {
    throw new Error("ArrayBuffer responses are not supported on this platform.");
  }
  if (body instanceof ArrayBuffer) {
    return body;
  }
  throw new Error("Automatic conversion to ArrayBuffer is not supported for response type.");
}
function _toBlob(body) {
  if (typeof Blob === "undefined") {
    throw new Error("Blob responses are not supported on this platform.");
  }
  if (body instanceof Blob) {
    return body;
  }
  if (ArrayBuffer && body instanceof ArrayBuffer) {
    return new Blob([body]);
  }
  throw new Error("Automatic conversion to Blob is not supported for response type.");
}
function _toJsonBody(body, format = "JSON") {
  if (typeof ArrayBuffer !== "undefined" && body instanceof ArrayBuffer) {
    throw new Error(`Automatic conversion to ${format} is not supported for ArrayBuffers.`);
  }
  if (typeof Blob !== "undefined" && body instanceof Blob) {
    throw new Error(`Automatic conversion to ${format} is not supported for Blobs.`);
  }
  if (typeof body === "string" || typeof body === "number" || typeof body === "object" || typeof body === "boolean" || Array.isArray(body)) {
    return body;
  }
  throw new Error(`Automatic conversion to ${format} is not supported for response type.`);
}
function _toTextBody(body) {
  if (typeof body === "string") {
    return body;
  }
  if (typeof ArrayBuffer !== "undefined" && body instanceof ArrayBuffer) {
    throw new Error("Automatic conversion to text is not supported for ArrayBuffers.");
  }
  if (typeof Blob !== "undefined" && body instanceof Blob) {
    throw new Error("Automatic conversion to text is not supported for Blobs.");
  }
  return JSON.stringify(_toJsonBody(body, "text"));
}
function _maybeConvertBody(responseType, body) {
  if (body === null) {
    return null;
  }
  switch (responseType) {
    case "arraybuffer":
      return _toArrayBufferBody(body);
    case "blob":
      return _toBlob(body);
    case "json":
      return _toJsonBody(body);
    case "text":
      return _toTextBody(body);
    default:
      throw new Error(`Unsupported responseType: ${responseType}`);
  }
}
function describeRequest(testRequest) {
  const url = testRequest.request.urlWithParams;
  const method = testRequest.request.method;
  return `${method} ${url}`;
}
function provideHttpClientTesting() {
  return [
    HttpClientTestingBackend,
    { provide: HttpBackend, useExisting: HttpClientTestingBackend },
    { provide: HttpTestingController, useExisting: HttpClientTestingBackend },
    { provide: REQUESTS_CONTRIBUTE_TO_STABILITY, useValue: false }
  ];
}
var HttpTestingController, TestRequest, HttpClientTestingBackend, HttpClientTestingModule;
var init_testing2 = __esm({
  "node_modules/@angular/common/fesm2022/http/testing.mjs"() {
    "use strict";
    init_core();
    init_core();
    init_esm();
    init_module();
    HttpTestingController = class {
    };
    TestRequest = class {
      request;
      observer;
      /**
       * Whether the request was cancelled after it was sent.
       */
      get cancelled() {
        return this._cancelled;
      }
      /**
       * @internal set by `HttpClientTestingBackend`
       */
      _cancelled = false;
      constructor(request, observer) {
        this.request = request;
        this.observer = observer;
      }
      /**
       * Resolve the request by returning a body plus additional HTTP information (such as response
       * headers) if provided.
       * If the request specifies an expected body type, the body is converted into the requested type.
       * Otherwise, the body is converted to `JSON` by default.
       *
       * Both successful and unsuccessful responses can be delivered via `flush()`.
       */
      flush(body, opts = {}) {
        if (this.cancelled) {
          throw new Error(`Cannot flush a cancelled request.`);
        }
        const url = this.request.urlWithParams;
        const headers = opts.headers instanceof HttpHeaders ? opts.headers : new HttpHeaders(opts.headers);
        body = _maybeConvertBody(this.request.responseType, body);
        let statusText = opts.statusText;
        let status = opts.status !== void 0 ? opts.status : HttpStatusCode.Ok;
        if (opts.status === void 0) {
          if (body === null) {
            status = HttpStatusCode.NoContent;
            statusText ||= "No Content";
          } else {
            statusText ||= "OK";
          }
        }
        if (statusText === void 0) {
          throw new Error("statusText is required when setting a custom status.");
        }
        if (status >= 200 && status < 300) {
          this.observer.next(new HttpResponse({ body, headers, status, statusText, url }));
          this.observer.complete();
        } else {
          this.observer.error(new HttpErrorResponse({ error: body, headers, status, statusText, url }));
        }
      }
      error(error, opts = {}) {
        if (this.cancelled) {
          throw new Error(`Cannot return an error for a cancelled request.`);
        }
        const headers = opts.headers instanceof HttpHeaders ? opts.headers : new HttpHeaders(opts.headers);
        this.observer.error(new HttpErrorResponse({
          error,
          headers,
          status: opts.status || 0,
          statusText: opts.statusText || "",
          url: this.request.urlWithParams
        }));
      }
      /**
       * Deliver an arbitrary `HttpEvent` (such as a progress event) on the response stream for this
       * request.
       */
      event(event) {
        if (this.cancelled) {
          throw new Error(`Cannot send events to a cancelled request.`);
        }
        this.observer.next(event);
      }
    };
    HttpClientTestingBackend = class _HttpClientTestingBackend {
      /**
       * List of pending requests which have not yet been expected.
       */
      open = [];
      /**
       * Used when checking if we need to throw the NOT_USING_FETCH_BACKEND_IN_SSR error
       */
      isTestingBackend = true;
      /**
       * Handle an incoming request by queueing it in the list of open requests.
       */
      handle(req) {
        return new Observable((observer) => {
          const testReq = new TestRequest(req, observer);
          this.open.push(testReq);
          observer.next({ type: HttpEventType.Sent });
          return () => {
            testReq._cancelled = true;
          };
        });
      }
      /**
       * Helper function to search for requests in the list of open requests.
       */
      _match(match) {
        if (typeof match === "string") {
          return this.open.filter((testReq) => testReq.request.urlWithParams === match);
        } else if (typeof match === "function") {
          return this.open.filter((testReq) => match(testReq.request));
        } else {
          return this.open.filter((testReq) => (!match.method || testReq.request.method === match.method.toUpperCase()) && (!match.url || testReq.request.urlWithParams === match.url));
        }
      }
      /**
       * Search for requests in the list of open requests, and return all that match
       * without asserting anything about the number of matches.
       */
      match(match) {
        const results = this._match(match);
        results.forEach((result) => {
          const index = this.open.indexOf(result);
          if (index !== -1) {
            this.open.splice(index, 1);
          }
        });
        return results;
      }
      /**
       * Expect that a single outstanding request matches the given matcher, and return
       * it.
       *
       * Requests returned through this API will no longer be in the list of open requests,
       * and thus will not match twice.
       */
      expectOne(match, description) {
        description ||= this.descriptionFromMatcher(match);
        const matches = this.match(match);
        if (matches.length > 1) {
          throw new Error(`Expected one matching request for criteria "${description}", found ${matches.length} requests.`);
        }
        if (matches.length === 0) {
          let message = `Expected one matching request for criteria "${description}", found none.`;
          if (this.open.length > 0) {
            const requests = this.open.map(describeRequest).join(", ");
            message += ` Requests received are: ${requests}.`;
          }
          throw new Error(message);
        }
        return matches[0];
      }
      /**
       * Expect that no outstanding requests match the given matcher, and throw an error
       * if any do.
       */
      expectNone(match, description) {
        description ||= this.descriptionFromMatcher(match);
        const matches = this.match(match);
        if (matches.length > 0) {
          throw new Error(`Expected zero matching requests for criteria "${description}", found ${matches.length}.`);
        }
      }
      /**
       * Validate that there are no outstanding requests.
       */
      verify(opts = {}) {
        let open = this.open;
        if (opts.ignoreCancelled) {
          open = open.filter((testReq) => !testReq.cancelled);
        }
        if (open.length > 0) {
          const requests = open.map(describeRequest).join(", ");
          throw new Error(`Expected no open requests, found ${open.length}: ${requests}`);
        }
      }
      descriptionFromMatcher(matcher) {
        if (typeof matcher === "string") {
          return `Match URL: ${matcher}`;
        } else if (typeof matcher === "object") {
          const method = matcher.method || "(any)";
          const url = matcher.url || "(any)";
          return `Match method: ${method}, URL: ${url}`;
        } else {
          return `Match by function: ${matcher.name}`;
        }
      }
      static \u0275fac = \u0275\u0275ngDeclareFactory({ minVersion: "12.0.0", version: "20.3.29", ngImport: core_exports, type: _HttpClientTestingBackend, deps: [], target: FactoryTarget.Injectable });
      static \u0275prov = \u0275\u0275ngDeclareInjectable({ minVersion: "12.0.0", version: "20.3.29", ngImport: core_exports, type: _HttpClientTestingBackend });
    };
    \u0275\u0275ngDeclareClassMetadata({ minVersion: "12.0.0", version: "20.3.29", ngImport: core_exports, type: HttpClientTestingBackend, decorators: [{
      type: Injectable
    }] });
    HttpClientTestingModule = class _HttpClientTestingModule {
      static \u0275fac = \u0275\u0275ngDeclareFactory({ minVersion: "12.0.0", version: "20.3.29", ngImport: core_exports, type: _HttpClientTestingModule, deps: [], target: FactoryTarget.NgModule });
      static \u0275mod = \u0275\u0275ngDeclareNgModule({ minVersion: "14.0.0", version: "20.3.29", ngImport: core_exports, type: _HttpClientTestingModule, imports: [HttpClientModule] });
      static \u0275inj = \u0275\u0275ngDeclareInjector({ minVersion: "12.0.0", version: "20.3.29", ngImport: core_exports, type: _HttpClientTestingModule, providers: [provideHttpClientTesting()], imports: [HttpClientModule] });
    };
    \u0275\u0275ngDeclareClassMetadata({ minVersion: "12.0.0", version: "20.3.29", ngImport: core_exports, type: HttpClientTestingModule, decorators: [{
      type: NgModule,
      args: [{
        imports: [HttpClientModule],
        providers: [provideHttpClientTesting()]
      }]
    }] });
  }
});

// angular:jit:template:src\app\app.html
var app_default;
var init_app = __esm({
  "angular:jit:template:src\\app\\app.html"() {
    app_default = `@if (view() === 'legal') {\r
  <main class="legal-page">\r
    <article class="legal-content">\r
      <p class="eyebrow"></p>\r
      <h1>T\xE9rminos de uso y Pol\xEDtica de Privacidad</h1>\r
      <p class="legal-updated">\xDAltima actualizaci\xF3n: 18 de octubre de 2026</p>\r
\r
      <section class="legal-section">\r
        <h2>1. T\xE9rminos de uso</h2>\r
        <p>Bienvenido a IDENTIPAT IA, una herramienta de orientaci\xF3n preliminar sobre alternativas de protecci\xF3n de la propiedad intelectual.</p>\r
        <h3>1.1 Uso del servicio</h3>\r
        <p>Al acceder o utilizar IDENTIPAT IA, declaras que la informaci\xF3n proporcionada es verdadera y que utilizar\xE1s el servicio de forma l\xEDcita y responsable. El servicio est\xE1 dirigido a personas mayores de edad o a quienes cuenten con la autorizaci\xF3n correspondiente.</p>\r
        <h3>1.2 Alcance de la orientaci\xF3n</h3>\r
        <p>Los resultados son informativos y orientativos. No constituyen asesor\xEDa legal, dictamen profesional, resoluci\xF3n oficial ni garantizan el otorgamiento de un derecho de propiedad intelectual. Para una evaluaci\xF3n definitiva, consulta a un profesional especializado o a la autoridad competente.</p>\r
        <h3>1.3 Responsabilidades del usuario</h3>\r
        <p>Debes revisar la informaci\xF3n antes de tomar decisiones y evitar enviar datos de terceros sin autorizaci\xF3n. No debes intentar afectar la disponibilidad, seguridad o funcionamiento del servicio.</p>\r
        <h3>1.4 Disponibilidad y cambios</h3>\r
        <p>Podemos actualizar, suspender o modificar el servicio cuando sea necesario por razones t\xE9cnicas, operativas o legales. Tambi\xE9n podemos actualizar estos t\xE9rminos; la versi\xF3n vigente ser\xE1 la publicada en esta p\xE1gina.</p>\r
      </section>\r
\r
      <section class="legal-section">\r
        <h2>2. Pol\xEDtica de Privacidad</h2>\r
        <h3>2.1 Informaci\xF3n que recopilamos</h3>\r
        <p>Podemos recopilar los datos de identificaci\xF3n, contacto, perfil y la descripci\xF3n de la creaci\xF3n que ingreses para permitir el acceso, prestar el servicio y generar una orientaci\xF3n personalizada.</p>\r
        <h3>2.2 Finalidades</h3>\r
        <p>Usamos la informaci\xF3n para identificarte dentro de una sesi\xF3n, procesar tus solicitudes, generar resultados, mantener la seguridad, atender incidencias y mejorar la calidad del servicio.</p>\r
        <h3>2.3 Tratamiento y protecci\xF3n</h3>\r
        <p>Aplicamos medidas t\xE9cnicas y organizativas razonables para proteger la informaci\xF3n frente a accesos no autorizados, p\xE9rdida o alteraci\xF3n. El acceso est\xE1 limitado al personal y proveedores que lo necesitan para operar el servicio.</p>\r
        <h3>2.4 Conservaci\xF3n y derechos</h3>\r
        <p>Conservamos la informaci\xF3n durante el tiempo necesario para cumplir las finalidades descritas y las obligaciones aplicables. Puedes solicitar informaci\xF3n, actualizaci\xF3n, rectificaci\xF3n o eliminaci\xF3n de tus datos mediante los canales de contacto habilitados por IDENTIPAT IA.</p>\r
        <h3>2.5 Proveedores tecnol\xF3gicos</h3>\r
        <p>El servicio puede utilizar proveedores tecnol\xF3gicos para el procesamiento t\xE9cnico de la informaci\xF3n. Estos proveedores deben tratar los datos \xFAnicamente para prestar el servicio y bajo obligaciones de confidencialidad y seguridad.</p>\r
        <h3>2.6 Contacto</h3>\r
        <p>Para consultas sobre estos t\xE9rminos o sobre el tratamiento de tus datos, utiliza los canales oficiales de contacto de IDENTIPAT IA.</p>\r
      </section>\r
    </article>\r
  </main>\r
} @else if (view() === 'admin') {\r
  <main class="admin-page">\r
    <header class="topbar admin-bar">\r
      <div><a class="brand" href="/" (click)="$event.preventDefault(); logout()">IDENTIPAT IA</a><span>Panel administrativo</span></div>\r
      <div class="result-actions">\r
        <button class="outline-button" type="button" (click)="openPredictions()" [disabled]="navigationPending()">Predicciones</button>\r
        <button class="outline-button" type="button" (click)="logout()">Cerrar sesi\xF3n</button>\r
      </div>\r
    </header>\r
\r
    @if (navigationPending(); as pending) {\r
      <section class="access-progress-modal" role="status" aria-live="polite">\r
        <div class="access-progress">\r
          <span class="analysis-spinner" aria-hidden="true"></span>\r
          <div>\r
            <h2>{{ pending === 'admin' ? 'Cargando panel de administraci\xF3n' : 'Cargando formulario de predicciones' }}</h2>\r
            <p>Estamos preparando la pantalla solicitada.</p>\r
          </div>\r
        </div>\r
      </section>\r
    }\r
\r
    <section class="admin-title">\r
      <p class="eyebrow">Administraci\xF3n</p>\r
      <h1>{{ adminSection() === 'users' ? 'Gesti\xF3n de usuarios' : adminSection() === 'predictions' ? 'Predicciones' : adminSection() === 'profile' ? 'Perfil' : 'Configuraciones' }}</h1>\r
      <p>\r
        @if (adminSection() === 'users') { Consulta, registra y actualiza la informaci\xF3n de los usuarios. }\r
        @else if (adminSection() === 'predictions') { Consulta el historial de predicciones realizadas en el sistema. }\r
        @else if (adminSection() === 'profile') { Revisa y actualiza tu informaci\xF3n de administrador. }\r
        @else { Administra las opciones del sistema. }\r
      </p>\r
    </section>\r
\r
    <nav class="admin-tabs" aria-label="Secciones del panel administrativo">\r
      <button type="button" [class.active]="adminSection() === 'predictions'" (click)="setAdminSection('predictions')">Predicciones</button>\r
      <button type="button" [class.active]="adminSection() === 'users'" (click)="setAdminSection('users')">Gesti\xF3n de usuarios</button>\r
      <button type="button" [class.active]="adminSection() === 'profile'" (click)="setAdminSection('profile')">Perfil</button>\r
      <button type="button" [class.active]="adminSection() === 'settings'" (click)="setAdminSection('settings')">Configuraciones</button>\r
    </nav>\r
\r
    @if (adminSection() === 'predictions') {\r
    <section class="layout">\r
      <article class="panel table-panel">\r
        <div class="table-head">\r
          <h2>Historial de predicciones</h2>\r
          <div class="table-actions">\r
            <button class="ghost" type="button" (click)="downloadAnalysesCsv()" [disabled]="!analyses().length">Descargar CSV</button>\r
            <button class="ghost" type="button" (click)="loadAnalyses()" [disabled]="analysesLoading()">{{ analysesLoading() ? 'Cargando...' : 'Refrescar' }}</button>\r
          </div>\r
        </div>\r
        @if (analysesError()) { <p class="error" role="alert">{{ analysesError() }}</p> }\r
        <div class="table-wrap">\r
          <table class="predictions-table">\r
            <thead><tr><th>Fecha</th><th>Usuario</th><th>Estado</th><th>Predicci\xF3n</th></tr></thead>\r
            <tbody>\r
              @for (analysis of analyses(); track analysis.analysisId) {\r
                <tr>\r
                  <td class="prediction-date">{{ analysis.requestedAt | date:'dd/MM/yyyy, HH:mm' }}</td>\r
                  <td>\r
                    <strong>{{ analysis.firstName }} {{ analysis.paternalLastName }} {{ analysis.maternalLastName }}</strong>\r
                    <span class="user-reference">Usuario #{{ analysis.userId }}</span>\r
                  </td>\r
                  <td><span class="status-badge" [attr.data-status]="analysis.status">{{ analysisStatusLabel(analysis.status) }}</span></td>\r
                  <td class="prediction-summary">{{ analysis.summary || 'Resultado a\xFAn no disponible.' }}</td>\r
                </tr>\r
              } @empty {\r
                @if (!analysesLoading()) { <tr><td colspan="4" class="empty">No hay predicciones registradas.</td></tr> }\r
              }\r
            </tbody>\r
          </table>\r
        </div>\r
      </article>\r
    </section>\r
    }\r
\r
    @if (adminSection() === 'users') {\r
    <section class="layout">\r
      <article class="panel table-panel">\r
        <div class="table-head">\r
          <div class="table-actions">\r
            <label class="user-search">Buscar\r
              <input type="search" [value]="userSearch()" (input)="updateUserSearch($event)" (keydown.enter)="$event.preventDefault(); searchUsers()" placeholder="Nombre, documento, correo..." aria-label="Buscar usuarios" />\r
              <button class="ghost" type="button" (click)="searchUsers()" [disabled]="loading()">Buscar</button>\r
            </label>\r
            <label class="page-size">Estado\r
              <select [value]="userStatus()" (change)="changeUserStatus($event)" [disabled]="loading()">\r
                <option value="">Todos</option>\r
                <option value="A">Activos</option>\r
                <option value="I">Inactivos</option>\r
              </select>\r
            </label>\r
            <label class="page-size">Tipo\r
              <select [value]="userType()" (change)="changeUserType($event)" [disabled]="loading()">\r
                <option value="">Todos</option>\r
                <option value="STANDARD">Est\xE1ndar</option>\r
                <option value="ADMIN">Administradores</option>\r
              </select>\r
            </label>\r
            <label class="page-size">Mostrar\r
              <select [value]="userPageSize()" (change)="changeUserPageSize($event)">\r
                <option value="10">10</option>\r
                <option value="20">20</option>\r
                <option value="50">50</option>\r
                <option value="100">100</option>\r
              </select>\r
            </label>\r
            <button class="ghost" type="button" (click)="downloadUsersCsv()" [disabled]="downloadingUsers()">{{ downloadingUsers() ? 'Descargando...' : 'Descargar CSV' }}</button>\r
            <button class="ghost" type="button" (click)="loadUsers()" [disabled]="loading()">{{ loading() ? 'Cargando...' : 'Refrescar' }}</button>\r
          </div>\r
        </div>\r
        @if (errorMessage()) { <p class="error" role="alert">{{ errorMessage() }}</p> }\r
        <div class="table-wrap">\r
          <table>\r
            <thead><tr><th>ID</th><th>Nombre completo</th><th>Tipo de documento</th><th>N\xFAmero de documento</th><th>G\xE9nero</th><th>Correo</th><th>Tipo</th><th>Profesi\xF3n</th><th>Estado</th><th>Acciones</th></tr></thead>\r
            <tbody>\r
              @for (user of users(); track user.userId) {\r
                <tr [class.editing]="isEditing(user)">\r
                  <td>{{ user.userId }}</td><td>{{ user.firstName }} {{ user.paternalLastName }} {{ user.maternalLastName }}</td>\r
                  <td>{{ user.doiType }}</td><td>{{ user.doi }}</td><td>{{ user.gender }}</td><td>{{ user.email }}</td>\r
                  <td>{{ user.userType?.toUpperCase() === 'ADMIN' ? 'Administrador' : '' }}</td><td>{{ user.profession }}</td><td>{{ user.status }}</td>\r
                  <td class="row-actions">\r
                    <button type="button" class="ghost" (click)="startEdit(user)">Editar</button>\r
                  </td>\r
                </tr>\r
              } @empty { <tr><td colspan="10" class="empty">No hay usuarios registrados.</td></tr> }\r
            </tbody>\r
          </table>\r
        </div>\r
        <nav class="pagination" aria-label="Paginaci\xF3n de usuarios">\r
          <span>P\xE1gina {{ userPage() + 1 }} de {{ userTotalPages() || 1 }} \xB7 {{ userTotalElements() }} usuarios</span>\r
          <div class="table-actions">\r
            <button class="ghost" type="button" (click)="changeUserPage(userPage() - 1)" [disabled]="loading() || userPage() === 0">Anterior</button>\r
            <button class="ghost" type="button" (click)="changeUserPage(userPage() + 1)" [disabled]="loading() || userPage() >= userTotalPages() - 1">Siguiente</button>\r
          </div>\r
        </nav>\r
      </article>\r
    </section>\r
    }\r
\r
    @if (adminSection() === 'profile') {\r
    <section class="layout">\r
      <article class="panel">\r
        <h2>Mi perfil</h2>\r
        @if (!currentAdmin()) {\r
          <p class="empty-state">No se pudo cargar tu informaci\xF3n. Refresca la secci\xF3n de usuarios e int\xE9ntalo nuevamente.</p>\r
        } @else {\r
          <div class="profile-readonly">\r
            <p><strong>Documento:</strong> {{ currentAdmin()?.doiType }} {{ currentAdmin()?.doi }}</p>\r
            <p><strong>Tipo de usuario:</strong> Administrador</p>\r
            <p><strong>Estado:</strong> {{ isInactive(currentAdmin()!) ? 'Inactivo' : 'Activo' }}</p>\r
          </div>\r
          <form [formGroup]="profileForm" (ngSubmit)="updateProfile()" class="form-grid registration-form">\r
            <label>Nombres <input type="text" formControlName="firstName" maxlength="30" /></label>\r
            <label>Apellido paterno <input type="text" formControlName="paternalLastName" maxlength="30" /></label>\r
            <label>Apellido materno <input type="text" formControlName="maternalLastName" maxlength="30" /></label>\r
            <label>Fecha de nacimiento <input type="date" formControlName="birthDate" min="1926-01-01" max="2020-12-31" /></label>\r
            <label>G\xE9nero\r
              <select formControlName="gender"><option value="" disabled>Seleccione</option><option value="MALE">Masculino</option><option value="FEMALE">Femenino</option></select>\r
            </label>\r
            <label>Correo electr\xF3nico <input type="email" formControlName="email" /></label>\r
            <label>Tel\xE9fono <input type="text" formControlName="phone" inputmode="numeric" maxlength="9" pattern="[0-9]*" (input)="sanitizePhone($event, profileForm.controls.phone)" /></label>\r
            <label>Celular <input type="tel" formControlName="mobilePhone" inputmode="tel" maxlength="15" (input)="sanitizeMobilePhone($event, profileForm.controls.mobilePhone)" /></label>\r
            <label>Profesi\xF3n <input type="text" formControlName="profession" /></label>\r
            @if (profileMessage()) {\r
              <p [class]="profileMessage().includes('correctamente') ? 'success-message' : 'error'" role="status">{{ profileMessage() }}</p>\r
            }\r
            <div class="actions">\r
              <button type="submit" [disabled]="savingProfile()">{{ savingProfile() ? 'Guardando...' : 'Actualizar mis datos' }}</button>\r
            </div>\r
          </form>\r
          <h2 style="margin-top: 1.5rem">Seguridad</h2>\r
          <form [formGroup]="securityForm" (ngSubmit)="updateAdminPassword()" class="form-grid registration-form">\r
            <label>Contrase\xF1a nueva\r
              <input id="profile-password" [type]="showAdminPassword() ? 'text' : 'password'" formControlName="password" autocomplete="new-password" />\r
            </label>\r
            <label>Confirmar contrase\xF1a nueva\r
              <input id="profile-confirm-password" [type]="showAdminPassword() ? 'text' : 'password'" formControlName="confirmPassword" autocomplete="new-password" />\r
              @if (securityForm.hasError('passwordMismatch') && securityForm.controls.confirmPassword.touched) {\r
                <span class="field-error" role="alert">Las contrase\xF1as no coinciden.</span>\r
              }\r
            </label>\r
            <label class="terms-checkbox">\r
              <span><input type="checkbox" [checked]="showAdminPassword()" (change)="toggleAdminPasswordVisibility()" /> Mostrar contrase\xF1a</span>\r
            </label>\r
            @if (passwordMessage()) {\r
              <p class="error" role="alert">{{ passwordMessage() }}</p>\r
            }\r
            <div class="actions">\r
              <button type="submit" [disabled]="savingPassword()">{{ savingPassword() ? 'Guardando...' : 'Actualizar contrase\xF1a' }}</button>\r
            </div>\r
          </form>\r
          @if (passwordSuccessModal()) {\r
            <div class="modal-backdrop" (click)="closePasswordSuccessModal()" (keydown.escape)="closePasswordSuccessModal()">\r
              <article class="panel" role="dialog" aria-modal="true" aria-labelledby="password-success-title" (click)="$event.stopPropagation()" style="width: min(420px, 100%); text-align: center">\r
                <h2 id="password-success-title">Contrase\xF1a actualizada</h2>\r
                <p role="status">La contrase\xF1a se actualiz\xF3 correctamente.</p>\r
                <div class="actions" style="justify-content: center">\r
                  <button type="button" (click)="closePasswordSuccessModal()">Cerrar</button>\r
                </div>\r
              </article>\r
            </div>\r
          }\r
        }\r
      </article>\r
    </section>\r
    }\r
\r
    @if (adminSection() === 'settings') {\r
    <section class="layout">\r
      <article class="panel table-panel">\r
        <div class="table-head">\r
          <h2>Disclaimer Institucional</h2>\r
        </div>\r
        @if (settingsLoading()) {\r
          <p role="status">Cargando disclaimers...</p>\r
        } @else if (settingsError()) {\r
          <p class="error" role="alert">{{ settingsError() }}</p>\r
          <button class="ghost" type="button" (click)="loadReportDisclaimers()">Reintentar</button>\r
        } @else if (latestReportDisclaimer(); as disclaimer) {\r
          <div class="report-disclaimer-details">\r
            <label for="disclaimer-title">T\xEDtulo del disclaimer</label>\r
            <input id="disclaimer-title" type="text" [value]="disclaimerTitle()" (input)="updateDisclaimerTitle($event)" [disabled]="savingDisclaimerDraft() || activatingDisclaimer()" />\r
            <label for="disclaimer-content">Contenido del disclaimer</label>\r
            <textarea\r
              id="disclaimer-content"\r
              class="report-disclaimer-editor"\r
              [value]="disclaimerContent()"\r
              (input)="updateDisclaimerContent($event)"\r
              [disabled]="savingDisclaimerDraft() || activatingDisclaimer()"\r
              maxlength="5000"\r
              rows="14"\r
            ></textarea>\r
            @if (disclaimerDraftMessage()) {\r
              <p class="success-message" role="status">{{ disclaimerDraftMessage() }}</p>\r
            }\r
            @if (disclaimerDraftError()) {\r
              <p class="error" role="alert">{{ disclaimerDraftError() }}</p>\r
            }\r
            <div class="actions">\r
              <button type="button" (click)="saveDisclaimerDraft()" [disabled]="!hasUnsavedDisclaimerChanges() || savingDisclaimerDraft()">\r
                {{ savingDisclaimerDraft() ? 'Guardando...' : 'Guardar borrador' }}\r
              </button>\r
              @if (savedDisclaimerDraft(); as draft) {\r
                <button type="button" class="outline-button" (click)="openPublishDisclaimerModal()" [disabled]="hasUnsavedDisclaimerChanges() || activatingDisclaimer() || savingDisclaimerDraft()">\r
                  {{ activatingDisclaimer() ? 'Publicando...' : 'Publicar borrador' }}\r
                </button>\r
              }\r
            </div>\r
            @if (disclaimerActivationMessage()) {\r
              <p class="success-message" role="status">{{ disclaimerActivationMessage() }}</p>\r
            }\r
            @if (disclaimerActivationError()) {\r
              <p class="error" role="alert">{{ disclaimerActivationError() }}</p>\r
            }\r
          </div>\r
        }\r
        @if (settingsLoaded() && !latestReportDisclaimer() && !settingsError()) {\r
          <p class="empty-state">No hay disclaimers registrados.</p>\r
        }\r
        @if (showPublishDisclaimerModal(); as publishModal) {\r
          @if (savedDisclaimerDraft(); as draft) {\r
            <div class="modal-backdrop" (click)="closePublishDisclaimerModal()" (keydown.escape)="closePublishDisclaimerModal()">\r
              <article class="panel modal-panel" role="dialog" aria-modal="true" aria-labelledby="publish-disclaimer-title" (click)="$event.stopPropagation()" style="text-align: center">\r
                <h2 id="publish-disclaimer-title">Publicar disclaimer</h2>\r
                <p>\xBFConfirmas publicar este borrador? Reemplazar\xE1 el disclaimer activo actual.</p>\r
                @if (disclaimerActivationError()) {\r
                  <p class="error" role="alert">{{ disclaimerActivationError() }}</p>\r
                }\r
                <div class="actions" style="justify-content: center">\r
                  <button type="button" class="ghost" (click)="closePublishDisclaimerModal()" [disabled]="activatingDisclaimer()">Cancelar</button>\r
                  <button type="button" (click)="publishDisclaimerDraft()" [disabled]="activatingDisclaimer()">\r
                    {{ activatingDisclaimer() ? 'Publicando...' : 'Confirmar publicaci\xF3n' }}\r
                  </button>\r
                </div>\r
              </article>\r
            </div>\r
          }\r
        }\r
      </article>\r
    </section>\r
    }\r
\r
    @if (showEditModal()) {\r
      <div class="modal-backdrop" (click)="cancelEdit()">\r
        <article class="panel modal-panel" (click)="$event.stopPropagation()">\r
          <h2>Editar usuario</h2>\r
          <form [formGroup]="userForm" (ngSubmit)="submit()" class="form-grid">\r
            <label>Nombres <input type="text" formControlName="firstName" maxlength="25" placeholder="Juan" /></label>\r
            <label>Apellido paterno <input type="text" formControlName="paternalLastName" maxlength="25" placeholder="P\xE9rez" /></label>\r
            <label>Apellido materno <input type="text" formControlName="maternalLastName" maxlength="25" placeholder="G\xF3mez" /></label>\r
            <label>Tipo de documento\r
              <select formControlName="doiType"><option value="" disabled>Seleccione</option><option value="DNI">DNI</option><option value="CE">CE</option></select>\r
            </label>\r
              <label>Tipo de usuario\r
                <select [value]="editingUserType()" (change)="changeEditingUserType($event)">\r
                  <option value="STANDARD">Est\xE1ndar</option>\r
                  <option value="ADMIN">Administrador</option>\r
                </select>\r
              </label>\r
            <label>N\xFAmero de documento <input type="text" formControlName="doi" [attr.maxlength]="userForm.controls.doiType.value === 'DNI' ? 8 : 11" placeholder="12345678" /></label>\r
            <label>Fecha de nacimiento <input type="date" formControlName="birthDate" min="1926-01-01" max="2020-12-31" /></label>\r
            <label>G\xE9nero\r
              <select formControlName="gender"><option value="" disabled>Seleccione</option><option value="MALE">Masculino</option><option value="FEMALE">Femenino</option></select>\r
            </label>\r
            <label>Correo electr\xF3nico <input type="email" formControlName="email" placeholder="juan@example.com" /></label>\r
            <label>Tel\xE9fono <input type="text" formControlName="phone" inputmode="numeric" maxlength="9" pattern="[0-9]*" (input)="sanitizePhone($event, userForm.controls.phone)" placeholder="01456789" /></label>\r
            <label>Celular <input type="tel" formControlName="mobilePhone" inputmode="tel" maxlength="15" (input)="sanitizeMobilePhone($event, userForm.controls.mobilePhone)" placeholder="987654321" /></label>\r
            <label>Profesi\xF3n <input type="text" formControlName="profession" placeholder="Ingeniero" /></label>\r
            @if (editErrorMessage()) { <p class="error" role="alert">{{ editErrorMessage() }}</p> }\r
            <div class="actions">\r
              <button type="button" (click)="submit()" [disabled]="saving()">{{ saving() ? 'Guardando...' : 'Actualizar' }}</button>\r
              <button type="button" class="danger" (click)="deleteEditingUser()" [disabled]="saving()">Eliminar</button>\r
              <button type="button" class="ghost" (click)="cancelEdit()" [disabled]="saving()">Cancelar</button>\r
            </div>\r
          </form>\r
        </article>\r
      </div>\r
    }\r
\r
    <footer class="trust-strip" aria-label="Caracter\xEDsticas del servicio">\r
      <div><strong>Privado</strong><span>Tus datos bajo control</span></div>\r
      <div><strong>Orientativo</strong><span>No reemplaza una resoluci\xF3n oficial de Indecopi</span></div>\r
      <div><strong>Especializado</strong><span>Enfocado en propiedad intelectual</span></div>\r
    </footer>\r
  </main>\r
} @else if (view() === 'standard-analysis') {\r
  <main class="analysis-page">\r
    <header class="topbar">\r
      <a class="brand" href="/" (click)="$event.preventDefault(); backHome()">IDENTIPAT IA</a>\r
      <div class="result-actions">\r
        @if (adminAuthenticated()) {\r
          <button class="outline-button" type="button" (click)="openAdminPanel()" [disabled]="navigationPending()">Panel de administraci\xF3n</button>\r
        }\r
        <button class="outline-button" type="button" (click)="exitAnalysis()" [disabled]="navigationPending()">Salir</button>\r
      </div>\r
    </header>\r
\r
    @if (navigationPending(); as pending) {\r
      <section class="access-progress-modal" role="status" aria-live="polite">\r
        <div class="access-progress">\r
          <span class="analysis-spinner" aria-hidden="true"></span>\r
          <div>\r
            <h2>{{ pending === 'admin' ? 'Cargando panel de administraci\xF3n' : pending === 'predictions' ? 'Cargando predicciones' : 'Saliendo' }}</h2>\r
            <p>{{ pending === 'exit' ? 'Estamos cerrando la sesi\xF3n.' : 'Estamos preparando la pantalla solicitada.' }}</p>\r
          </div>\r
        </div>\r
      </section>\r
    }\r
\r
    @if (exitError()) { <p class="error" role="alert">{{ exitError() }}</p> }\r
\r
    <section class="analysis-shell">\r
      <div class="analysis-heading">\r
        <p class="eyebrow">An\xE1lisis de tu creaci\xF3n</p>\r
        <h1>Cu\xE9ntanos qu\xE9 quieres proteger</h1>\r
        <p>Describe tu creaci\xF3n con el mayor detalle posible. Esta informaci\xF3n ser\xE1 la base para orientarte sobre sus alternativas de protecci\xF3n.</p>\r
      </div>\r
\r
      <form [formGroup]="analysisForm" (ngSubmit)="submitAnalysis()" class="analysis-form">\r
        <label for="description">Descripci\xF3n de la creaci\xF3n</label>\r
        @if (selectedPdf(); as pdf) {\r
          <div class="pdf-selection" role="status" aria-label="Archivo PDF seleccionado" style="height: 188px; position: relative; display: grid; place-items: center; border: 1px solid var(--line); border-radius: 5px;">\r
            <button type="button" aria-label="Quitar archivo PDF" title="Quitar archivo PDF" (click)="removeSelectedPdf()" style="position: absolute; top: 8px; right: 8px; z-index: 1; width: 32px; height: 32px; padding: 5px; border: 0; border-radius: 50%; background: #fff; cursor: pointer;">\r
              <img src="close.png" alt="" style="display: block; width: 22px; height: 22px; object-fit: contain;" />\r
            </button>\r
            <img src="/Icon_pdf_file.svg.webp" alt="" style="width: 72px; height: 72px;" />\r
            <span style="max-width: 90%; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;">{{ pdf.name }}</span>\r
          </div>\r
        } @else if (selectedAudio(); as audio) {\r
          <div class="pdf-selection" role="status" aria-label="Grabaci\xF3n de audio seleccionada" style="position: relative; display: grid; place-items: center; gap: .65rem; height: 188px; padding: 1rem; border: 1px solid var(--line); border-radius: 5px; background: #fff;">\r
            <button type="button" aria-label="Quitar grabaci\xF3n de audio" title="Quitar grabaci\xF3n de audio" (click)="removeSelectedAudio()" style="position: absolute; top: 8px; right: 8px; z-index: 1; width: 32px; height: 32px; padding: 5px; border: 0; border-radius: 50%; background: #fff; cursor: pointer;"><img src="close.png" alt="" style="display: block; width: 22px; height: 22px; object-fit: contain;" /></button>\r
            <img src="/audio.png" alt="Grabaci\xF3n de audio" style="display: block; width: 96px; height: 96px; object-fit: contain; background: #fff;" />\r
            <span>Grabaci\xF3n de voz</span>\r
          </div>\r
        } @else {\r
          <textarea id="description" formControlName="description" rows="10" placeholder="Escribe aqu\xED la descripci\xF3n de tu creaci\xF3n..."></textarea>\r
        }\r
        @if (!selectedPdf() && !selectedAudio() && analysisForm.controls.description.touched && analysisForm.controls.description.invalid) {\r
          <p class="field-error">La descripci\xF3n debe tener entre 20 y 20 000 caracteres.</p>\r
        }\r
        <div class="input-actions">\r
          <button class="voice-button" type="button" [class.recording]="recordingAudio()" [attr.aria-label]="recordingAudio() ? 'Detener grabaci\xF3n' : 'Grabar descripci\xF3n por voz'" (click)="toggleAudioRecording()">{{ recordingAudio() ? 'Detener grabaci\xF3n' : 'Grabar voz' }}</button>\r
          <input #pdfInput type="file" accept="application/pdf" hidden (change)="onPdfSelected($event)" />\r
          <button class="pdf-button" type="button" aria-label="Adjuntar archivo PDF" (click)="pdfInput.click()">Adjuntar PDF</button>\r
        </div>\r
        <div class="analysis-actions">\r
          <span [class.success-message]="analysisMessage().startsWith('An\xE1lisis recibido')" [class.error]="analysisMessage().startsWith('No se pudo')">{{ analysisMessage() }}</span>\r
          <button class="primary-button" type="submit" [disabled]="submittingAnalysis()">{{ submittingAnalysis() ? 'Enviando...' : 'Iniciar an\xE1lisis' }}</button>\r
        </div>\r
      </form>\r
      <p class="analysis-disclaimer">Identipat es una herramienta de Inteligencia artificial y puede cometer errores, verifica la informaci\xF3n importante.</p>\r
\r
      @if (submittingAnalysis()) {\r
        <section class="analysis-progress-modal" role="status" aria-live="polite">\r
          <div class="analysis-progress">\r
            <span class="analysis-spinner" aria-hidden="true"></span>\r
            <div>\r
              <h2>Analizando con IA</h2>\r
              <p>Estamos revisando la informaci\xF3n de tu creaci\xF3n. Este proceso puede tardar unos segundos.</p>\r
            </div>\r
          </div>\r
        </section>\r
      }\r
\r
      @if (recordingAudio()) {\r
        <section role="dialog" aria-modal="true" aria-labelledby="audio-recording-title" style="position: fixed; inset: 0; z-index: 35; display: grid; place-items: center; padding: 1rem; background: rgb(15 26 24 / .58);">\r
          <div style="display: grid; justify-items: center; gap: .75rem; width: min(360px, 100%); padding: 2rem; border: 1px solid var(--line); background: #fff; box-shadow: 0 18px 50px rgb(24 51 47 / .18); text-align: center;">\r
            <img src="/microphone.png" alt="Micr\xF3fono" style="width: 96px; height: 96px; object-fit: contain;" />\r
            <h2 id="audio-recording-title" style="margin: 0; font-size: 1.35rem;">Grabando voz</h2>\r
            <p aria-live="polite" style="margin: 0; color: var(--teal-dark); font-family: 'Space Grotesk', sans-serif; font-size: 2rem; font-weight: 700; letter-spacing: .06em;">{{ recordingTimeLabel() }}</p>\r
            <button class="primary-button" type="button" (click)="stopAudioRecording()" style="width: 100%; margin-top: .5rem;">Detener grabaci\xF3n</button>\r
          </div>\r
        </section>\r
      }\r
    </section>\r
\r
    <footer class="trust-strip" aria-label="Caracter\xEDsticas del servicio">\r
      <div><strong>Privado</strong><span>Tus datos bajo control</span></div>\r
      <div><strong>Orientativo</strong><span>No reemplaza una resoluci\xF3n oficial de Indecopi</span></div>\r
      <div><strong>Especializado</strong><span>Enfocado en propiedad intelectual</span></div>\r
    </footer>\r
  </main>\r
} @else if (view() === 'standard-result') {\r
  <main class="analysis-page result-page">\r
    <header class="topbar">\r
      <a class="brand" href="/" (click)="$event.preventDefault(); backHome()">IDENTIPAT IA</a>\r
      <div class="result-actions">\r
        <button class="outline-button" type="button" (click)="startNewAnalysis()">Nuevo an\xE1lisis</button>\r
        <button class="outline-button" type="button" (click)="exitResult()" [disabled]="navigationPending()">Salir</button>\r
      </div>\r
    </header>\r
\r
    @if (navigationPending(); as pending) {\r
      <section class="access-progress-modal" role="status" aria-live="polite">\r
        <div class="access-progress">\r
          <span class="analysis-spinner" aria-hidden="true"></span>\r
          <div>\r
            <h2>{{ pending === 'exit' ? 'Saliendo' : 'Cargando predicciones' }}</h2>\r
            <p>{{ pending === 'exit' ? 'Estamos cerrando la sesi\xF3n.' : 'Estamos preparando la pantalla solicitada.' }}</p>\r
          </div>\r
        </div>\r
      </section>\r
    }\r
\r
    @if (exitError()) { <p class="error" role="alert">{{ exitError() }}</p> }\r
\r
    <section class="analysis-shell">\r
      @if (analysisResult()?.status === 'COMPLETED' && analysisResult()?.result; as result) {\r
        <section class="analysis-result" aria-live="polite">\r
          <div class="result-heading">\r
            <div>\r
              <p class="eyebrow">Resultado del an\xE1lisis</p>\r
              <h1>Orientaci\xF3n sobre tu creaci\xF3n</h1>\r
            </div>\r
            <div class="result-download-actions">\r
              <span class="result-status">An\xE1lisis completado</span>\r
              <button class="outline-button" type="button" (click)="downloadReportPdf()" [disabled]="downloadingReportPdf()">\r
                {{ downloadingReportPdf() ? 'Descargando PDF...' : 'Descargar reporte PDF' }}\r
              </button>\r
            </div>\r
          </div>\r
          @if (reportPdfError()) { <p class="error" role="alert">{{ reportPdfError() }}</p> }\r
\r
          <article class="result-section result-summary">\r
            <h2>Resumen</h2>\r
            <p>{{ result.summary }}</p>\r
          </article>\r
\r
          <article class="result-section">\r
            <h2>Evaluaci\xF3n legal</h2>\r
            <p>Screening de posibles restricciones conforme a la Decisi\xF3n 486.</p>\r
            <div class="legal-screening">\r
              <div class="screening-item">\r
                <h3>Art\xEDculo 15</h3>\r
                <p class="result-outcome">{{ applicabilityLabel(result.patentScreening.article15.assessment) }}</p>\r
                <p>{{ result.patentScreening.article15.rationale }}</p>\r
              </div>\r
              <div class="screening-item">\r
                <h3>Art\xEDculo 20</h3>\r
                <p class="result-outcome">{{ applicabilityLabel(result.patentScreening.article20.assessment) }}</p>\r
                <p>{{ result.patentScreening.article20.rationale }}</p>\r
              </div>\r
            </div>\r
          </article>\r
\r
          <article class="result-section">\r
            <h2>Acceso a recursos gen\xE9ticos</h2>\r
            <p class="result-outcome">{{ applicabilityLabel(result.geneticResourceAccess.assessment) }}</p>\r
            <p>{{ result.geneticResourceAccess.rationale }}</p>\r
            @if (result.geneticResourceAccess.missingInformation.length) {\r
              <h3>Informaci\xF3n faltante</h3>\r
              <ul>\r
                @for (item of result.geneticResourceAccess.missingInformation; track item) { <li>{{ item }}</li> }\r
              </ul>\r
            }\r
          </article>\r
\r
          <article class="result-section">\r
            <h2>Modalidades de protecci\xF3n</h2>\r
            <div class="protection-options">\r
              @for (option of result.protectionOptions; track option.type) {\r
                <div class="protection-option">\r
                  <div class="option-heading">\r
                    <h3>{{ protectionTypeLabel(option.type) }}</h3>\r
                    <span [attr.data-applicability]="option.applicability">{{ applicabilityLabel(option.applicability) }}</span>\r
                  </div>\r
                  <p class="protected-subject"><strong>Materia protegible:</strong> {{ option.protectedSubjectMatter }}</p>\r
                  <p>{{ option.rationale }}</p>\r
                </div>\r
              }\r
            </div>\r
          </article>\r
\r
          <div class="result-columns">\r
            <article class="result-section">\r
              <h2>Observaciones</h2>\r
              <ul>\r
                @for (observation of result.observations; track observation) { <li>{{ observation }}</li> }\r
              </ul>\r
            </article>\r
            <article class="result-section warning-section">\r
              <h2>Advertencias</h2>\r
              <ul>\r
                @for (warning of result.warnings; track warning) { <li>{{ warning }}</li> }\r
              </ul>\r
            </article>\r
          </div>\r
\r
          <aside class="report-warning" role="note">\r
            <strong>Advertencia:</strong> el presente an\xE1lisis ha sido realizado con una herramienta de Inteligencia Artificial y puede cometer errores, por lo cual la opini\xF3n es de naturaleza orientativa y no es vinculante ante una eventual solicitud de registro de la creaci\xF3n analizada.\r
          </aside>\r
        </section>\r
      }\r
\r
      @if (analysisResult()?.status === 'FAILED' && analysisResult()?.failure; as failure) {\r
        <section class="analysis-failure" role="alert">\r
          <p class="eyebrow">Resultado del an\xE1lisis</p>\r
          <h1>No se pudo completar el an\xE1lisis</h1>\r
          <p>{{ failure.message }}</p>\r
        </section>\r
      }\r
    </section>\r
\r
    <footer class="trust-strip" aria-label="Caracter\xEDsticas del servicio">\r
      <div><strong>Privado</strong><span>Tus datos bajo control</span></div>\r
      <div><strong>Orientativo</strong><span>No reemplaza una resoluci\xF3n oficial de Indecopi</span></div>\r
      <div><strong>Especializado</strong><span>Enfocado en propiedad intelectual</span></div>\r
    </footer>\r
  </main>\r
} @else {\r
  <main class="home-page">\r
    <header class="topbar">\r
      <a class="brand" href="/">IDENTIPAT IA</a>\r
    </header>\r
\r
    <section class="hero">\r
      <div class="hero-copy">\r
        <p class="eyebrow">Orientaci\xF3n en propiedad intelectual</p>\r
        <h1>Descubre c\xF3mo proteger la propiedad intelectual de tu creaci\xF3n</h1>\r
        <p class="intro">IDENTIPAT IA analiza la descripci\xF3n de tu invenci\xF3n con inteligencia artificial para orientarte sobre su potencial de protecci\xF3n y la modalidad m\xE1s adecuada: patente, modelo de utilidad, dise\xF1o industrial, signo distintivo o derechos de autor.</p>\r
\r
        <div class="access-block">\r
          @if (view() === 'registration') {\r
            <span class="section-label">Nuevo usuario</span>\r
            <h2>Completa tu registro</h2>\r
            <form [formGroup]="userForm" (ngSubmit)="registerStandard()" class="registration-form" autocomplete="off" name="identipat-registration">\r
              <label>Nombres <input type="text" name="firstName" autocomplete="given-name" formControlName="firstName" maxlength="25" placeholder="Adrian Jes\xFAs" /></label>\r
              <label>Apellido paterno <input type="text" name="paternalLastName" autocomplete="family-name" formControlName="paternalLastName" maxlength="25" placeholder="Lizarbe" /></label>\r
              <label>Apellido materno <input type="text" name="maternalLastName" autocomplete="additional-name" formControlName="maternalLastName" maxlength="25" placeholder="Estrada" /></label>\r
              <label>Tipo de documento\r
                <select name="doiType" autocomplete="off" formControlName="doiType"><option value="DNI">DNI</option><option value="CE">CE</option></select>\r
              </label>\r
              <label>N\xFAmero de documento <input type="text" name="document-number" autocomplete="off" formControlName="doi" inputmode="numeric" [attr.maxlength]="userForm.controls.doiType.value === 'DNI' ? 8 : 11" (input)="userForm.controls.doiType.value === 'DNI' && sanitizeDni($event, userForm.controls.doi)" /></label>\r
              <label>Fecha de nacimiento <input type="date" name="birthDate" autocomplete="bday" formControlName="birthDate" min="1926-01-01" max="2020-12-31" /></label>\r
              <label>G\xE9nero\r
                <select name="gender" autocomplete="sex" formControlName="gender"><option value="" disabled>Seleccione</option><option value="MALE">Masculino</option><option value="FEMALE">Femenino</option></select>\r
              </label>\r
              <label>Correo electr\xF3nico <input type="email" name="email" autocomplete="email" formControlName="email" placeholder="micorreo@ejemplo.com" /></label>\r
              <label>Tel\xE9fono <input type="text" name="phone" autocomplete="tel" formControlName="phone" inputmode="numeric" maxlength="9" pattern="[0-9]*" (input)="sanitizePhone($event, userForm.controls.phone)" placeholder="01654321" /></label>\r
              <label>Celular <input type="tel" name="mobilePhone" autocomplete="tel" formControlName="mobilePhone" inputmode="tel" maxlength="15" (input)="sanitizeMobilePhone($event, userForm.controls.mobilePhone)" placeholder="912345678" /></label>\r
              <label class="registration-wide">Profesi\xF3n / Ocupaci\xF3n <input type="text" name="profession" autocomplete="organization-title" formControlName="profession" placeholder="Ingeniero" /></label>\r
              <label class="registration-wide terms-checkbox">\r
                <span><input type="checkbox" formControlName="termsAccepted" /> Acepto los <a href="/terminos-y-privacidad" target="_blank" rel="noopener noreferrer">T\xE9rminos de uso y la Pol\xEDtica de Privacidad</a></span>\r
              </label>\r
              @if (accessMessage()) { <p class="access-message registration-wide" role="alert">{{ accessMessage() }}</p> }\r
              <div class="registration-actions registration-wide">\r
                <button class="primary-button" type="submit" [disabled]="saving()">{{ saving() ? 'Registrando...' : 'Registrarme' }}</button>\r
                <button class="outline-button" type="button" (click)="cancelRegistration()" [disabled]="saving()">Volver</button>\r
              </div>\r
            </form>\r
          } @else {\r
            <span class="section-label">{{ view() === 'admin-login' ? 'Acceso seguro' : 'Comienza aqu\xED' }}</span>\r
            <h2>{{ view() === 'admin-login' ? 'Ingresa como administrador' : 'Identif\xEDcate para continuar' }}</h2>\r
            <form [formGroup]="accessForm" (ngSubmit)="view() === 'admin-login' ? loginAdmin() : identifyDocument()">\r
              <div class="document-fields">\r
                <label>Tipo\r
                  <select formControlName="doiType" aria-label="Tipo de documento"><option value="DNI">DNI</option><option value="CE">CE</option></select>\r
                </label>\r
                <label>N\xFAmero de documento\r
                  <input type="text" inputmode="numeric" autocomplete="username" formControlName="doi" [attr.maxlength]="accessForm.controls.doiType.value === 'DNI' ? 8 : 11" placeholder="Ingresa tu documento" />\r
                </label>\r
              </div>\r
              @if (accessForm.controls.doi.touched && accessForm.controls.doi.invalid) { <p class="field-error">Para DNI ingresa 8 d\xEDgitos; para CE, entre 6 y 11.</p> }\r
              @if (view() === 'admin-login') {\r
                <label class="password-field">Contrase\xF1a\r
                  <input type="password" autocomplete="current-password" [formControl]="loginForm.controls.password" placeholder="Ingresa tu contrase\xF1a" />\r
                </label>\r
              }\r
              @if (accessMessage()) { <p class="access-message" [class.access-error]="accessMessage() === 'Usuario no registrado'" role="status">{{ accessMessage() }}</p> }\r
              <button class="primary-button" type="submit" [disabled]="checkingDocument() || authenticating()">\r
                @if (view() === 'admin-login') { {{ authenticating() ? 'Verificando...' : 'Acceder como administrador' }} }\r
                @else { {{ checkingDocument() ? 'Verificando...' : 'Ingresar' }} }\r
              </button>\r
            </form>\r
            @if (view() !== 'admin-login') {\r
              <button class="outline-button access-register-button" type="button" (click)="openRegistration()">Registrarme</button>\r
            }\r
          }\r
        </div>\r
        @if (registrationModal(); as registrationStatus) {\r
          <section class="registration-progress-modal" role="status" aria-live="polite">\r
            <div class="registration-progress">\r
              @if (registrationStatus === 'registering') {\r
                <span class="analysis-spinner" aria-hidden="true"></span>\r
                <div>\r
                  <h2>Registrando usuario</h2>\r
                  <p>Estamos registrando tu informaci\xF3n.</p>\r
                </div>\r
              } @else {\r
                <span class="registration-success-icon" aria-hidden="true">\u2713</span>\r
                <div>\r
                  <h2>Usuario registrado con \xE9xito</h2>\r
                  <p>Tu registro se complet\xF3 correctamente.</p>\r
                </div>\r
              }\r
            </div>\r
          </section>\r
        }\r
        @if (authenticating() || (checkingDocument() && standardAccessPending())) {\r
          <section class="access-progress-modal" role="status" aria-live="polite">\r
            <div class="access-progress">\r
              <span class="analysis-spinner" aria-hidden="true"></span>\r
              <div>\r
                <h2>Accediendo</h2>\r
                <p>Estamos verificando tus datos.</p>\r
              </div>\r
            </div>\r
          </section>\r
        }\r
        @if (view() !== 'registration') {\r
          <p class="privacy-note">Al registrarte o acceder, aceptas los <a href="/terminos-y-privacidad" target="_blank" rel="noopener noreferrer">T\xE9rminos de uso y Pol\xEDtica de Privacidad de Identipat IA</a></p>\r
        }\r
      </div>\r
\r
      <figure class="hero-visual">\r
        <img src="https://images.unsplash.com/photo-1554224155-6726b3ff858f?auto=format&fit=crop&w=1200&q=85" alt="Persona revisando documentos de propiedad intelectual" />\r
        <figcaption>Identifica el potencial de protecci\xF3n de tu creaci\xF3n.</figcaption>\r
      </figure>\r
    </section>\r
\r
    <section class="trust-strip" aria-label="Caracter\xEDsticas del servicio">\r
      <div><strong>Privado</strong><span>Tus datos bajo control</span></div>\r
      <div><strong>Orientativo</strong><span>No reemplaza una resoluci\xF3n oficial de Indecopi</span></div>\r
      <div><strong>Especializado</strong><span>Enfocado en propiedad intelectual</span></div>\r
    </section>\r
  </main>\r
}`;
  }
});

// angular:jit:style:src\app\app.css
var app_default2;
var init_app2 = __esm({
  "angular:jit:style:src\\app\\app.css"() {
    app_default2 = '/* src/app/app.css */\n:host {\n  --ink: #18332f;\n  --muted: #5c6d69;\n  --teal: #146c63;\n  --teal-dark: #0d504a;\n  --mint: #dceee8;\n  --coral: #df765f;\n  --paper: #fbfaf6;\n  --line: #cfdbd7;\n  --danger: #a83232;\n  display: block;\n  min-height: 100dvh;\n  color: var(--ink);\n  background: var(--paper);\n}\nbutton,\ninput,\nselect {\n  font: inherit;\n}\n.home-page {\n  min-height: 100dvh;\n  background:\n    linear-gradient(\n      90deg,\n      rgb(20 108 99 / .05) 1px,\n      transparent 1px),\n    linear-gradient(rgb(20 108 99 / .05) 1px, transparent 1px),\n    var(--paper);\n  background-size: 40px 40px;\n}\n.topbar {\n  height: 76px;\n  display: flex;\n  align-items: center;\n  justify-content: space-between;\n  gap: 1rem;\n  padding: 0 max(1.25rem, calc((100vw - 1180px) / 2));\n  border-bottom: 1px solid var(--line);\n  background: rgb(251 250 246 / .94);\n}\n.brand {\n  color: var(--ink);\n  font-family: "Space Grotesk", sans-serif;\n  font-size: 1.1rem;\n  font-weight: 700;\n  letter-spacing: .08em;\n  text-decoration: none;\n}\n.outline-button {\n  border: 1px solid var(--teal);\n  border-radius: 6px;\n  padding: .68rem 1rem;\n  color: var(--teal-dark);\n  background: transparent;\n  font-weight: 600;\n  cursor: pointer;\n}\n.legal-page {\n  min-height: 100dvh;\n  background:\n    linear-gradient(\n      135deg,\n      rgb(20 108 99 / .07),\n      transparent 42%),\n    var(--paper);\n}\n.legal-back {\n  text-decoration: none;\n}\n.legal-content {\n  width: min(820px, calc(100% - 2.5rem));\n  margin: 0 auto;\n  padding: clamp(3rem, 8vw, 6rem) 0;\n}\n.legal-content h1 {\n  max-width: 720px;\n  margin: 0;\n  font-size: clamp(2.2rem, 5vw, 4rem);\n  line-height: 1.05;\n}\n.legal-updated {\n  margin: 1rem 0 2.5rem;\n  color: var(--muted);\n  font-size: .85rem;\n}\n.legal-section {\n  margin-top: 2rem;\n  padding-top: 2rem;\n  border-top: 1px solid var(--line);\n}\n.legal-section h2 {\n  margin: 0 0 1.25rem;\n  font-size: 1.45rem;\n}\n.legal-section h3 {\n  margin: 1.5rem 0 .45rem;\n  font-size: 1rem;\n}\n.legal-section p {\n  margin: 0;\n  color: var(--muted);\n  line-height: 1.75;\n}\n.hero {\n  width: min(1180px, calc(100% - 2.5rem));\n  min-height: calc(100dvh - 168px);\n  margin: 0 auto;\n  padding: clamp(2.5rem, 6vw, 5rem) 0 3rem;\n  display: grid;\n  grid-template-columns: minmax(0, 1.05fr) minmax(320px, .95fr);\n  align-items: center;\n  gap: clamp(2rem, 6vw, 5rem);\n}\n.eyebrow {\n  margin: 0 0 .75rem;\n  color: var(--coral);\n  font-size: .76rem;\n  font-weight: 700;\n  letter-spacing: .12em;\n  text-transform: uppercase;\n}\n.hero h1 {\n  max-width: 720px;\n  margin: 0;\n  font-size: clamp(2.35rem, 5.3vw, 4.8rem);\n  line-height: 1.02;\n  letter-spacing: 0;\n}\n.intro {\n  max-width: 630px;\n  margin: 1.4rem 0 2rem;\n  color: var(--muted);\n  font-size: 1.08rem;\n  line-height: 1.7;\n}\n.access-block {\n  max-width: 650px;\n  padding: 1.35rem;\n  border-left: 4px solid var(--coral);\n  background: #fff;\n  box-shadow: 0 18px 50px rgb(24 51 47 / .1);\n}\n.section-label {\n  color: var(--teal);\n  font-size: .76rem;\n  font-weight: 700;\n  text-transform: uppercase;\n}\n.access-block h2 {\n  margin: .25rem 0 1rem;\n  font-size: 1.25rem;\n}\n.document-fields {\n  display: grid;\n  grid-template-columns: 120px minmax(0, 1fr);\n  gap: .75rem;\n}\n.registration-form {\n  display: grid;\n  grid-template-columns: repeat(2, minmax(0, 1fr));\n  gap: .75rem;\n}\n.registration-wide {\n  grid-column: 1 / -1;\n}\n.registration-actions {\n  display: flex;\n  align-items: center;\n  gap: .75rem;\n}\n.registration-actions .primary-button {\n  width: auto;\n  margin-top: 0;\n}\n.terms-checkbox {\n  display: block;\n}\n.terms-checkbox span {\n  display: flex;\n  align-items: center;\n  gap: .4rem;\n  font-weight: 600;\n}\n.terms-checkbox input {\n  width: auto;\n  min-height: auto;\n}\n.terms-checkbox a {\n  color: var(--teal-dark);\n}\nlabel {\n  display: grid;\n  gap: .38rem;\n  color: var(--ink);\n  font-size: .82rem;\n  font-weight: 600;\n}\ninput,\nselect {\n  width: 100%;\n  min-height: 46px;\n  border: 1px solid var(--line);\n  border-radius: 5px;\n  padding: .68rem .75rem;\n  outline: none;\n  color: var(--ink);\n  background: #fff;\n  transition: border-color 150ms ease, box-shadow 150ms ease;\n}\ninput:focus,\nselect:focus {\n  border-color: var(--teal);\n  box-shadow: 0 0 0 3px rgb(20 108 99 / .13);\n}\n.registration-form input.ng-invalid.ng-touched:not([type=checkbox]),\n.registration-form select.ng-invalid.ng-touched,\n.form-grid input.ng-invalid.ng-touched:not([type=checkbox]),\n.form-grid select.ng-invalid.ng-touched {\n  border-color: var(--danger);\n  background: #fff5f3;\n  box-shadow: 0 0 0 3px rgb(168 50 50 / .12);\n}\n.registration-form .terms-checkbox input.ng-invalid.ng-touched {\n  outline: 2px solid var(--danger);\n}\ntextarea {\n  width: 100%;\n  resize: vertical;\n  border: 1px solid var(--line);\n  border-radius: 5px;\n  padding: .8rem;\n  outline: none;\n  color: var(--ink);\n  background: #fff;\n  font: inherit;\n  line-height: 1.55;\n  transition: border-color 150ms ease, box-shadow 150ms ease;\n}\ntextarea:focus {\n  border-color: var(--teal);\n  box-shadow: 0 0 0 3px rgb(20 108 99 / .13);\n}\n.input-actions {\n  display: flex;\n  justify-content: flex-end;\n  gap: .6rem;\n  margin: .65rem 0 .35rem;\n}\n.voice-button,\n.pdf-button {\n  border: 1px solid var(--teal);\n  border-radius: 5px;\n  padding: .58rem .85rem;\n  color: var(--teal-dark);\n  background: transparent;\n  font-weight: 600;\n  cursor: pointer;\n}\n.password-field {\n  margin-top: .8rem;\n}\n.primary-button {\n  width: 100%;\n  min-height: 48px;\n  margin-top: 1rem;\n  border: 0;\n  border-radius: 5px;\n  padding: .75rem 1rem;\n  color: #fff;\n  background: var(--teal);\n  font-weight: 700;\n  cursor: pointer;\n}\n.primary-button:hover {\n  background: var(--teal-dark);\n}\n.access-register-button {\n  width: 100%;\n  margin-top: .65rem;\n}\nbutton:disabled {\n  opacity: .62;\n  cursor: not-allowed;\n}\n.field-error,\n.access-message {\n  margin: .6rem 0 0;\n  font-size: .86rem;\n}\n.field-error {\n  color: var(--danger);\n}\n.access-message {\n  border-left: 3px solid var(--teal);\n  padding: .55rem .75rem;\n  color: var(--teal-dark);\n  background: var(--mint);\n}\n.access-message.access-error {\n  border-left-color: var(--danger);\n  color: var(--danger);\n  background: #fbe8e5;\n}\n.privacy-note {\n  margin: .9rem 0 0;\n  color: var(--muted);\n  font-size: .78rem;\n}\n.hero-visual {\n  position: relative;\n  margin: 0;\n  min-height: 610px;\n  overflow: hidden;\n  background: var(--mint);\n}\n.hero-visual::before {\n  content: "";\n  position: absolute;\n  inset: 18px;\n  z-index: 1;\n  border: 1px solid rgb(255 255 255 / .75);\n  pointer-events: none;\n}\n.hero-visual img {\n  width: 100%;\n  height: 610px;\n  display: block;\n  object-fit: cover;\n}\n.hero-visual figcaption {\n  position: absolute;\n  right: 0;\n  bottom: 0;\n  z-index: 2;\n  width: min(82%, 340px);\n  padding: 1rem 1.3rem;\n  color: #fff;\n  background: var(--teal-dark);\n  font-family: "Space Grotesk", sans-serif;\n}\n.trust-strip {\n  min-height: 92px;\n  display: grid;\n  grid-template-columns: repeat(3, 1fr);\n  padding: 0 max(1.25rem, calc((100vw - 1180px) / 2));\n  color: #fff;\n  background: var(--ink);\n}\n.trust-strip div {\n  display: grid;\n  align-content: center;\n  gap: .2rem;\n  padding: 1rem 2rem;\n  border-right: 1px solid rgb(255 255 255 / .18);\n}\n.trust-strip div:first-child {\n  padding-left: 0;\n}\n.trust-strip strong {\n  font-family: "Space Grotesk", sans-serif;\n}\n.trust-strip span {\n  color: #bfd2cd;\n  font-size: .8rem;\n}\n.analysis-page {\n  min-height: 100dvh;\n  background:\n    linear-gradient(\n      135deg,\n      rgb(20 108 99 / .08),\n      transparent 45%),\n    var(--paper);\n}\n.analysis-shell {\n  width: min(860px, calc(100% - 2.5rem));\n  margin: 0 auto;\n  padding: clamp(3rem, 8vw, 7rem) 0;\n}\n.analysis-heading {\n  max-width: 700px;\n}\n.analysis-heading h1 {\n  margin: 0;\n  font-size: clamp(2.25rem, 5vw, 4rem);\n  line-height: 1.05;\n}\n.analysis-heading p:last-child {\n  max-width: 650px;\n  margin: 1.25rem 0 2rem;\n  color: var(--muted);\n  font-size: 1.05rem;\n  line-height: 1.7;\n}\n.analysis-form {\n  padding: clamp(1.25rem, 4vw, 2rem);\n  border-left: 4px solid var(--coral);\n  background: #fff;\n  box-shadow: 0 18px 50px rgb(24 51 47 / .1);\n}\n.analysis-form label {\n  margin-bottom: .45rem;\n  font-size: .9rem;\n}\n.analysis-disclaimer {\n  margin: .8rem 0 0;\n  color: var(--muted);\n  font-size: .78rem;\n  line-height: 1.5;\n}\n.consent-check {\n  display: flex;\n  grid-template-columns: auto 1fr;\n  align-items: start;\n  gap: .55rem;\n  margin-top: .8rem;\n  font-weight: 400;\n}\n.consent-check input {\n  width: 1rem;\n  min-height: 1rem;\n  margin-top: .15rem;\n  accent-color: var(--teal);\n}\n.consent-check span {\n  color: var(--muted);\n  line-height: 1.45;\n}\n.analysis-actions {\n  display: flex;\n  align-items: center;\n  justify-content: space-between;\n  gap: 1rem;\n  margin-top: 1rem;\n}\n.analysis-actions span {\n  color: var(--muted);\n  font-size: .8rem;\n}\n.analysis-actions .primary-button {\n  width: auto;\n  min-width: 160px;\n  margin-top: 0;\n}\n.analysis-progress-modal {\n  position: fixed;\n  inset: 0;\n  z-index: 30;\n  display: grid;\n  place-items: center;\n  padding: 1rem;\n  background: rgb(15 26 24 / .55);\n}\n.analysis-progress {\n  display: flex;\n  align-items: center;\n  gap: 1rem;\n  width: min(520px, 100%);\n  padding: 1.25rem;\n  border: 1px solid var(--line);\n  background: #fff;\n  box-shadow: 0 18px 50px rgb(24 51 47 / .18);\n}\n.registration-progress-modal {\n  position: fixed;\n  inset: 0;\n  z-index: 30;\n  display: grid;\n  place-items: center;\n  padding: 1rem;\n  background: rgb(15 26 24 / .55);\n}\n.registration-progress {\n  display: flex;\n  align-items: center;\n  gap: 1rem;\n  width: min(520px, 100%);\n  padding: 1.25rem;\n  border: 1px solid var(--line);\n  background: #fff;\n  box-shadow: 0 18px 50px rgb(24 51 47 / .18);\n}\n.registration-progress h2 {\n  margin: 0;\n  font-size: 1.15rem;\n}\n.registration-progress p {\n  margin: .3rem 0 0;\n  color: var(--muted);\n  line-height: 1.5;\n}\n.registration-success-icon {\n  display: grid;\n  width: 2rem;\n  height: 2rem;\n  flex: 0 0 auto;\n  place-items: center;\n  border-radius: 50%;\n  color: #fff;\n  background: #17643a;\n  font-size: 1.35rem;\n  font-weight: 700;\n}\n.access-progress-modal {\n  position: fixed;\n  inset: 0;\n  z-index: 30;\n  display: grid;\n  place-items: center;\n  padding: 1rem;\n  background: rgb(15 26 24 / .55);\n}\n.access-progress {\n  display: flex;\n  align-items: center;\n  gap: 1rem;\n  width: min(520px, 100%);\n  padding: 1.25rem;\n  border: 1px solid var(--line);\n  background: #fff;\n  box-shadow: 0 18px 50px rgb(24 51 47 / .18);\n}\n.access-progress h2 {\n  margin: 0;\n  font-size: 1.15rem;\n}\n.access-progress p {\n  margin: .3rem 0 0;\n  color: var(--muted);\n  line-height: 1.5;\n}\n.analysis-spinner {\n  width: 2rem;\n  height: 2rem;\n  flex: 0 0 auto;\n  border: 3px solid var(--mint);\n  border-top-color: var(--teal);\n  border-radius: 50%;\n  animation: analysis-spin .8s linear infinite;\n}\n.analysis-progress h2,\n.analysis-failure h2 {\n  margin: 0;\n  font-size: 1.15rem;\n}\n.analysis-progress p {\n  margin: .3rem 0 0;\n  color: var(--muted);\n  line-height: 1.5;\n}\n@keyframes analysis-spin {\n  to {\n    transform: rotate(360deg);\n  }\n}\n.analysis-result {\n  margin-top: 2rem;\n  padding-top: 2rem;\n  border-top: 1px solid var(--line);\n}\n.result-heading {\n  display: flex;\n  align-items: end;\n  flex-wrap: wrap;\n  justify-content: space-between;\n  gap: .75rem;\n}\n.result-heading h1 {\n  margin: 0;\n  font-size: clamp(1.7rem, 4vw, 2.6rem);\n}\n.result-status {\n  padding: .35rem .6rem;\n  color: #17643a;\n  background: #dff3e6;\n  font-size: .78rem;\n  font-weight: 700;\n}\n.result-download-actions {\n  display: flex;\n  align-items: center;\n  flex-wrap: wrap;\n  gap: .6rem;\n}\n.result-section {\n  margin-top: 1.25rem;\n  padding: 1.25rem;\n  border: 1px solid var(--line);\n  background: #fff;\n}\n.result-section h2 {\n  margin: 0 0 .65rem;\n  font-size: 1.05rem;\n}\n.result-section h3 {\n  margin: 1rem 0 .45rem;\n  font-size: .92rem;\n}\n.result-section p {\n  margin: 0;\n  color: var(--muted);\n  line-height: 1.65;\n}\n.result-outcome {\n  display: inline-block;\n  margin-bottom: .65rem !important;\n  color: var(--teal-dark) !important;\n  font-weight: 700;\n}\n.legal-screening {\n  display: grid;\n  gap: 1rem;\n  margin-top: 1rem;\n}\n.screening-item {\n  padding: 1rem;\n  background: #f4f7f6;\n}\n.screening-item h3 {\n  margin-top: 0;\n}\n.protection-options {\n  display: grid;\n  gap: .75rem;\n}\n.protection-option {\n  padding: 1rem;\n  background: #f4f7f6;\n}\n.option-heading {\n  display: flex;\n  align-items: center;\n  justify-content: space-between;\n  gap: .75rem;\n}\n.option-heading h3 {\n  margin: 0;\n  font-size: .95rem;\n}\n.option-heading span {\n  padding: .25rem .45rem;\n  color: #36524d;\n  background: #e6eeec;\n  font-size: .72rem;\n  font-weight: 700;\n  white-space: nowrap;\n}\n.option-heading span[data-applicability=POSSIBLE] {\n  color: #17643a;\n  background: #dff3e6;\n}\n.option-heading span[data-applicability=UNLIKELY] {\n  color: #765018;\n  background: #fff0cf;\n}\n.protection-option p {\n  margin-top: .5rem;\n  font-size: .9rem;\n}\n.protection-option .protected-subject {\n  color: var(--ink);\n  font-weight: 600;\n}\n.result-columns {\n  display: grid;\n  grid-template-columns: repeat(2, minmax(0, 1fr));\n  gap: 1.25rem;\n}\n.result-section ul {\n  display: grid;\n  gap: .7rem;\n  margin: 0;\n  padding-left: 1.15rem;\n  color: var(--muted);\n  line-height: 1.55;\n}\n.warning-section {\n  border-color: #ead6a7;\n  background: #fffaf0;\n}\n.report-warning {\n  margin-top: 1.25rem;\n  padding: 1rem 1.25rem;\n  border: 1px solid #ead6a7;\n  background: #fffaf0;\n  color: var(--muted);\n  line-height: 1.55;\n}\n.report-warning strong {\n  color: var(--ink);\n}\n.analysis-failure {\n  margin-top: 1.25rem;\n  padding: 1.25rem;\n  color: #7a2424;\n  background: #fff0ee;\n}\n.analysis-failure p {\n  margin: .45rem 0 0;\n  line-height: 1.5;\n}\n.admin-page {\n  min-height: 100dvh;\n  display: flex;\n  flex-direction: column;\n  background: #f4f7f6;\n}\n.admin-page > .trust-strip {\n  width: 100%;\n  margin-top: auto;\n}\n.admin-bar {\n  background: #fff;\n}\n.admin-bar div {\n  display: grid;\n}\n.admin-bar .result-actions {\n  display: flex;\n}\n.admin-bar span {\n  color: var(--muted);\n  font-size: .72rem;\n}\n.admin-title,\n.layout,\n.admin-tabs {\n  width: min(1380px, calc(100% - 2rem));\n  margin-inline: auto;\n}\n.admin-title {\n  padding: 2rem 0 1.25rem;\n}\n.admin-title h1 {\n  margin: 0;\n  font-size: clamp(1.9rem, 4vw, 2.8rem);\n}\n.admin-title > p:last-child {\n  margin: .35rem 0 0;\n  color: var(--muted);\n}\n.admin-tabs {\n  display: flex;\n  gap: .5rem;\n  margin-bottom: 1rem;\n  border-bottom: 1px solid var(--line);\n}\n.admin-tabs button {\n  border: 0;\n  background: none;\n  padding: .75rem 1rem;\n  font-weight: 600;\n  color: var(--muted);\n  cursor: pointer;\n  border-bottom: 2px solid transparent;\n}\n.admin-tabs button.active {\n  color: var(--teal-dark);\n  border-bottom-color: var(--teal);\n}\n.layout {\n  display: grid;\n  grid-template-columns: minmax(0, 1fr);\n  gap: 1rem;\n}\n.panel {\n  border: 1px solid var(--line);\n  border-radius: 7px;\n  padding: 1rem;\n  background: #fff;\n  box-shadow: 0 10px 25px rgb(24 51 47 / .06);\n}\n.panel h2 {\n  margin-top: 0;\n  font-size: 1.2rem;\n}\n.empty-state {\n  color: var(--muted);\n}\n.profile-readonly {\n  display: grid;\n  gap: .25rem;\n  margin-bottom: 1rem;\n  color: var(--muted);\n}\n.profile-readonly p {\n  margin: 0;\n}\n.form-grid {\n  display: grid;\n  grid-template-columns: repeat(2, minmax(0, 1fr));\n  gap: .75rem;\n}\n.form-grid > .error,\n.form-grid > .actions {\n  grid-column: 1/-1;\n}\n.actions,\n.row-actions,\n.table-head,\n.table-actions {\n  display: flex;\n  align-items: center;\n  gap: .55rem;\n}\n.result-actions {\n  display: flex;\n  align-items: center;\n  gap: .55rem;\n}\n.actions {\n  margin-top: .4rem;\n}\n.table-head {\n  justify-content: space-between;\n}\n.table-head h2 {\n  margin-bottom: 0;\n}\n.user-search {\n  display: flex;\n  align-items: center;\n  gap: .4rem;\n  color: var(--muted);\n}\n.user-search input {\n  width: min(18rem, 34vw);\n  min-height: 36px;\n  padding: .4rem .55rem;\n}\n.page-size {\n  display: flex;\n  align-items: center;\n  gap: .4rem;\n  color: var(--muted);\n  white-space: nowrap;\n}\n.page-size select {\n  width: auto;\n  min-height: 36px;\n  padding: .4rem 1.8rem .4rem .55rem;\n}\n.pagination {\n  display: flex;\n  align-items: center;\n  justify-content: space-between;\n  gap: .75rem;\n  margin-top: .9rem;\n  color: var(--muted);\n  font-size: .86rem;\n}\n.actions button,\n.ghost,\n.danger {\n  border: 0;\n  border-radius: 5px;\n  padding: .62rem .9rem;\n  color: #fff;\n  background: var(--teal);\n  font-weight: 600;\n  cursor: pointer;\n}\n.ghost {\n  color: var(--teal-dark);\n  background: var(--mint);\n}\n.danger {\n  color: var(--danger);\n  background: #f9dfdc;\n}\n.modal-backdrop {\n  position: fixed;\n  inset: 0;\n  background: rgb(15 26 24 / .55);\n  display: grid;\n  place-items: center;\n  padding: 1rem;\n  z-index: 20;\n}\n.modal-panel {\n  width: min(760px, 100%);\n  max-height: 90vh;\n  overflow-y: auto;\n}\n.table-wrap {\n  overflow-x: auto;\n}\ntable {\n  width: 100%;\n  border-collapse: collapse;\n}\nth,\ntd {\n  padding: .72rem .4rem;\n  border-bottom: 1px solid var(--line);\n  text-align: left;\n  font-size: .86rem;\n}\nth {\n  color: var(--muted);\n  font-size: .72rem;\n  text-transform: uppercase;\n}\ntr.editing {\n  background: #eef8f5;\n}\n.empty {\n  padding: 2rem;\n  color: var(--muted);\n  text-align: center;\n}\n.error {\n  padding: .65rem;\n  color: #7a2424;\n  background: #fff0ee;\n}\n.success-message {\n  margin: 0;\n  padding: .65rem;\n  color: var(--teal-dark);\n  background: var(--mint);\n}\n.predictions-table {\n  min-width: 760px;\n}\n.predictions-table strong,\n.user-reference {\n  display: block;\n}\n.prediction-date {\n  white-space: nowrap;\n}\n.user-reference {\n  margin-top: .18rem;\n  color: var(--muted);\n  font-size: .75rem;\n}\n.prediction-summary {\n  min-width: 280px;\n  line-height: 1.45;\n}\n.status-badge {\n  display: inline-block;\n  border-radius: 999px;\n  padding: .3rem .55rem;\n  color: #36524d;\n  background: #e6eeec;\n  font-size: .72rem;\n  font-weight: 700;\n  white-space: nowrap;\n}\n.status-badge[data-status=COMPLETED] {\n  color: #17643a;\n  background: #dff3e6;\n}\n.status-badge[data-status=FAILED] {\n  color: var(--danger);\n  background: #f9dfdc;\n}\n.status-badge[data-status=ANALYZING],\n.status-badge[data-status=PREPROCESSING] {\n  color: #765018;\n  background: #fff0cf;\n}\n@media (max-width: 920px) {\n  .hero {\n    grid-template-columns: 1fr;\n    padding-top: 2.5rem;\n  }\n  .hero-visual,\n  .hero-visual img {\n    height: 420px;\n    min-height: 420px;\n  }\n  .layout {\n    grid-template-columns: 1fr;\n  }\n}\n@media (max-width: 620px) {\n  .table-head {\n    align-items: flex-start;\n    flex-direction: column;\n  }\n  .table-actions {\n    flex-wrap: wrap;\n    justify-content: flex-start;\n  }\n  .user-search {\n    flex-wrap: wrap;\n  }\n  .user-search input {\n    width: min(18rem, 62vw);\n  }\n  .topbar {\n    height: 68px;\n    padding-inline: 1rem;\n  }\n  .outline-button {\n    padding: .55rem .65rem;\n    font-size: .78rem;\n  }\n  .hero {\n    width: min(100% - 2rem, 1180px);\n    gap: 2.25rem;\n  }\n  .hero h1 {\n    font-size: 2.45rem;\n  }\n  .document-fields {\n    grid-template-columns: 92px minmax(0, 1fr);\n  }\n  .registration-form {\n    grid-template-columns: 1fr;\n  }\n  .form-grid {\n    grid-template-columns: 1fr;\n  }\n  .registration-wide {\n    grid-column: auto;\n  }\n  .access-block {\n    padding: 1rem;\n  }\n  .analysis-shell {\n    width: min(100% - 2rem, 860px);\n    padding-top: 2.5rem;\n  }\n  .analysis-actions {\n    align-items: stretch;\n    flex-direction: column;\n  }\n  .analysis-actions .primary-button {\n    width: 100%;\n  }\n  .result-columns {\n    grid-template-columns: 1fr;\n  }\n  .option-heading {\n    align-items: start;\n    flex-direction: column;\n  }\n  .pagination {\n    align-items: stretch;\n    flex-direction: column;\n  }\n  .pagination .table-actions {\n    justify-content: space-between;\n  }\n  .trust-strip {\n    grid-template-columns: 1fr;\n    padding: 0 1rem;\n  }\n  .trust-strip div,\n  .trust-strip div:first-child {\n    padding: 1rem 0;\n    border-right: 0;\n    border-bottom: 1px solid rgb(255 255 255 / .18);\n  }\n}\n/*# sourceMappingURL=app.css.map */\n';
  }
});

// node_modules/@angular/forms/fesm2022/forms.mjs
function _isAndroid() {
  const userAgent = getDOM() ? getDOM().getUserAgent() : "";
  return /android (\d+)/.test(userAgent.toLowerCase());
}
function isEmptyInputValue(value) {
  return value == null || lengthOrSize(value) === 0;
}
function lengthOrSize(value) {
  if (value == null) {
    return null;
  } else if (Array.isArray(value) || typeof value === "string") {
    return value.length;
  } else if (value instanceof Set) {
    return value.size;
  }
  return null;
}
function minValidator(min) {
  return (control) => {
    if (control.value == null || min == null) {
      return null;
    }
    const value = parseFloat(control.value);
    return !isNaN(value) && value < min ? { "min": { "min": min, "actual": control.value } } : null;
  };
}
function maxValidator(max) {
  return (control) => {
    if (control.value == null || max == null) {
      return null;
    }
    const value = parseFloat(control.value);
    return !isNaN(value) && value > max ? { "max": { "max": max, "actual": control.value } } : null;
  };
}
function requiredValidator(control) {
  return isEmptyInputValue(control.value) ? { "required": true } : null;
}
function requiredTrueValidator(control) {
  return control.value === true ? null : { "required": true };
}
function emailValidator(control) {
  if (isEmptyInputValue(control.value)) {
    return null;
  }
  return EMAIL_REGEXP.test(control.value) ? null : { "email": true };
}
function minLengthValidator(minLength) {
  return (control) => {
    const length = control.value?.length ?? lengthOrSize(control.value);
    if (length === null || length === 0) {
      return null;
    }
    return length < minLength ? { "minlength": { "requiredLength": minLength, "actualLength": length } } : null;
  };
}
function maxLengthValidator(maxLength) {
  return (control) => {
    const length = control.value?.length ?? lengthOrSize(control.value);
    if (length !== null && length > maxLength) {
      return { "maxlength": { "requiredLength": maxLength, "actualLength": length } };
    }
    return null;
  };
}
function patternValidator(pattern) {
  if (!pattern)
    return nullValidator;
  let regex;
  let regexStr;
  if (typeof pattern === "string") {
    regexStr = "";
    if (pattern.charAt(0) !== "^")
      regexStr += "^";
    regexStr += pattern;
    if (pattern.charAt(pattern.length - 1) !== "$")
      regexStr += "$";
    regex = new RegExp(regexStr);
  } else {
    regexStr = pattern.toString();
    regex = pattern;
  }
  return (control) => {
    if (isEmptyInputValue(control.value)) {
      return null;
    }
    const value = control.value;
    return regex.test(value) ? null : { "pattern": { "requiredPattern": regexStr, "actualValue": value } };
  };
}
function nullValidator(control) {
  return null;
}
function isPresent(o) {
  return o != null;
}
function toObservable(value) {
  const obs = isPromise(value) ? from(value) : value;
  if ((typeof ngDevMode === "undefined" || ngDevMode) && !isSubscribable(obs)) {
    let errorMessage = `Expected async validator to return Promise or Observable.`;
    if (typeof value === "object") {
      errorMessage += " Are you using a synchronous validator where an async validator is expected?";
    }
    throw new RuntimeError(-1101, errorMessage);
  }
  return obs;
}
function mergeErrors(arrayOfErrors) {
  let res = {};
  arrayOfErrors.forEach((errors) => {
    res = errors != null ? __spreadValues(__spreadValues({}, res), errors) : res;
  });
  return Object.keys(res).length === 0 ? null : res;
}
function executeValidators(control, validators) {
  return validators.map((validator) => validator(control));
}
function isValidatorFn(validator) {
  return !validator.validate;
}
function normalizeValidators(validators) {
  return validators.map((validator) => {
    return isValidatorFn(validator) ? validator : ((c) => validator.validate(c));
  });
}
function compose(validators) {
  if (!validators)
    return null;
  const presentValidators = validators.filter(isPresent);
  if (presentValidators.length == 0)
    return null;
  return function(control) {
    return mergeErrors(executeValidators(control, presentValidators));
  };
}
function composeValidators(validators) {
  return validators != null ? compose(normalizeValidators(validators)) : null;
}
function composeAsync(validators) {
  if (!validators)
    return null;
  const presentValidators = validators.filter(isPresent);
  if (presentValidators.length == 0)
    return null;
  return function(control) {
    const observables = executeValidators(control, presentValidators).map(toObservable);
    return forkJoin(observables).pipe(map(mergeErrors));
  };
}
function composeAsyncValidators(validators) {
  return validators != null ? composeAsync(normalizeValidators(validators)) : null;
}
function mergeValidators(controlValidators, dirValidator) {
  if (controlValidators === null)
    return [dirValidator];
  return Array.isArray(controlValidators) ? [...controlValidators, dirValidator] : [controlValidators, dirValidator];
}
function getControlValidators(control) {
  return control._rawValidators;
}
function getControlAsyncValidators(control) {
  return control._rawAsyncValidators;
}
function makeValidatorsArray(validators) {
  if (!validators)
    return [];
  return Array.isArray(validators) ? validators : [validators];
}
function hasValidator(validators, validator) {
  return Array.isArray(validators) ? validators.includes(validator) : validators === validator;
}
function addValidators(validators, currentValidators) {
  const current = makeValidatorsArray(currentValidators);
  const validatorsToAdd = makeValidatorsArray(validators);
  validatorsToAdd.forEach((v) => {
    if (!hasValidator(current, v)) {
      current.push(v);
    }
  });
  return current;
}
function removeValidators(validators, currentValidators) {
  return makeValidatorsArray(currentValidators).filter((v) => !hasValidator(validators, v));
}
function controlParentException(nameOrIndex) {
  return new RuntimeError(1050, `formControlName must be used with a parent formGroup directive. You'll want to add a formGroup
      directive and pass it an existing FormGroup instance (you can create one in your class).

      ${describeFormControl(nameOrIndex)}

    Example:

    ${formControlNameExample}`);
}
function describeFormControl(nameOrIndex) {
  if (nameOrIndex == null || nameOrIndex === "") {
    return "";
  }
  const valueType = typeof nameOrIndex === "string" ? "name" : "index";
  return `Affected Form Control ${valueType}: "${nameOrIndex}"`;
}
function ngModelGroupException() {
  return new RuntimeError(1051, `formControlName cannot be used with an ngModelGroup parent. It is only compatible with parents
      that also have a "form" prefix: formGroupName, formArrayName, or formGroup.

      Option 1:  Update the parent to be formGroupName (reactive form strategy)

      ${formGroupNameExample}

      Option 2: Use ngModel instead of formControlName (template-driven strategy)

      ${ngModelGroupExample}`);
}
function missingFormException() {
  return new RuntimeError(1052, `formGroup expects a FormGroup instance. Please pass one in.

      Example:

      ${formControlNameExample}`);
}
function groupParentException() {
  return new RuntimeError(1053, `formGroupName must be used with a parent formGroup directive.  You'll want to add a formGroup
    directive and pass it an existing FormGroup instance (you can create one in your class).

    Example:

    ${formGroupNameExample}`);
}
function arrayParentException() {
  return new RuntimeError(1054, `formArrayName must be used with a parent formGroup directive.  You'll want to add a formGroup
      directive and pass it an existing FormGroup instance (you can create one in your class).

      Example:

      ${formArrayNameExample}`);
}
function ngModelWarning(directiveName) {
  return `
  It looks like you're using ngModel on the same form field as ${directiveName}.
  Support for using the ngModel input property and ngModelChange event with
  reactive form directives has been deprecated in Angular v6 and will be removed
  in a future version of Angular.

  For more information on this, see our API docs here:
  https://angular.io/api/forms/${directiveName === "formControl" ? "FormControlDirective" : "FormControlName"}#use-with-ngmodel
  `;
}
function describeKey(isFormGroup, key) {
  return isFormGroup ? `with name: '${key}'` : `at index: ${key}`;
}
function noControlsError(isFormGroup) {
  return `
    There are no form controls registered with this ${isFormGroup ? "group" : "array"} yet. If you're using ngModel,
    you may want to check next tick (e.g. use setTimeout).
  `;
}
function missingControlError(isFormGroup, key) {
  return `Cannot find form control ${describeKey(isFormGroup, key)}`;
}
function missingControlValueError(isFormGroup, key) {
  return `Must supply a value for form control ${describeKey(isFormGroup, key)}`;
}
function pickValidators(validatorOrOpts) {
  return (isOptionsObj(validatorOrOpts) ? validatorOrOpts.validators : validatorOrOpts) || null;
}
function coerceToValidator(validator) {
  return Array.isArray(validator) ? composeValidators(validator) : validator || null;
}
function pickAsyncValidators(asyncValidator, validatorOrOpts) {
  if (typeof ngDevMode === "undefined" || ngDevMode) {
    if (isOptionsObj(validatorOrOpts) && asyncValidator) {
      console.warn(asyncValidatorsDroppedWithOptsWarning);
    }
  }
  return (isOptionsObj(validatorOrOpts) ? validatorOrOpts.asyncValidators : asyncValidator) || null;
}
function coerceToAsyncValidator(asyncValidator) {
  return Array.isArray(asyncValidator) ? composeAsyncValidators(asyncValidator) : asyncValidator || null;
}
function isOptionsObj(validatorOrOpts) {
  return validatorOrOpts != null && !Array.isArray(validatorOrOpts) && typeof validatorOrOpts === "object";
}
function assertControlPresent(parent, isGroup, key) {
  const controls = parent.controls;
  const collection = isGroup ? Object.keys(controls) : controls;
  if (!collection.length) {
    throw new RuntimeError(1e3, typeof ngDevMode === "undefined" || ngDevMode ? noControlsError(isGroup) : "");
  }
  if (!controls[key]) {
    throw new RuntimeError(1001, typeof ngDevMode === "undefined" || ngDevMode ? missingControlError(isGroup, key) : "");
  }
}
function assertAllValuesPresent(control, isGroup, value) {
  control._forEachChild((_, key) => {
    if (value[key] === void 0) {
      throw new RuntimeError(1002, typeof ngDevMode === "undefined" || ngDevMode ? missingControlValueError(isGroup, key) : "");
    }
  });
}
function validateFormGroupControls(controls) {
  const invalidKeys = Object.keys(controls).filter((key) => key.includes("."));
  if (invalidKeys.length > 0) {
    console.warn(`FormGroup keys cannot include \`.\`, please replace the keys for: ${invalidKeys.join(",")}.`);
  }
}
function controlPath(name, parent) {
  return [...parent.path, name];
}
function setUpControl(control, dir, callSetDisabledState = setDisabledStateDefault) {
  if (typeof ngDevMode === "undefined" || ngDevMode) {
    if (!control)
      _throwError(dir, "Cannot find control with");
    if (!dir.valueAccessor)
      _throwMissingValueAccessorError(dir);
  }
  setUpValidators(control, dir);
  dir.valueAccessor.writeValue(control.value);
  if (control.disabled || callSetDisabledState === "always") {
    dir.valueAccessor.setDisabledState?.(control.disabled);
  }
  setUpViewChangePipeline(control, dir);
  setUpModelChangePipeline(control, dir);
  setUpBlurPipeline(control, dir);
  setUpDisabledChangeHandler(control, dir);
}
function cleanUpControl(control, dir, validateControlPresenceOnChange = true) {
  const noop2 = () => {
    if (validateControlPresenceOnChange && (typeof ngDevMode === "undefined" || ngDevMode)) {
      _noControlError(dir);
    }
  };
  if (dir.valueAccessor) {
    dir.valueAccessor.registerOnChange(noop2);
    dir.valueAccessor.registerOnTouched(noop2);
  }
  cleanUpValidators(control, dir);
  if (control) {
    dir._invokeOnDestroyCallbacks();
    control._registerOnCollectionChange(() => {
    });
  }
}
function registerOnValidatorChange(validators, onChange) {
  validators.forEach((validator) => {
    if (validator.registerOnValidatorChange)
      validator.registerOnValidatorChange(onChange);
  });
}
function setUpDisabledChangeHandler(control, dir) {
  if (dir.valueAccessor.setDisabledState) {
    const onDisabledChange = (isDisabled) => {
      dir.valueAccessor.setDisabledState(isDisabled);
    };
    control.registerOnDisabledChange(onDisabledChange);
    dir._registerOnDestroy(() => {
      control._unregisterOnDisabledChange(onDisabledChange);
    });
  }
}
function setUpValidators(control, dir) {
  const validators = getControlValidators(control);
  if (dir.validator !== null) {
    control.setValidators(mergeValidators(validators, dir.validator));
  } else if (typeof validators === "function") {
    control.setValidators([validators]);
  }
  const asyncValidators = getControlAsyncValidators(control);
  if (dir.asyncValidator !== null) {
    control.setAsyncValidators(mergeValidators(asyncValidators, dir.asyncValidator));
  } else if (typeof asyncValidators === "function") {
    control.setAsyncValidators([asyncValidators]);
  }
  const onValidatorChange = () => control.updateValueAndValidity();
  registerOnValidatorChange(dir._rawValidators, onValidatorChange);
  registerOnValidatorChange(dir._rawAsyncValidators, onValidatorChange);
}
function cleanUpValidators(control, dir) {
  let isControlUpdated = false;
  if (control !== null) {
    if (dir.validator !== null) {
      const validators = getControlValidators(control);
      if (Array.isArray(validators) && validators.length > 0) {
        const updatedValidators = validators.filter((validator) => validator !== dir.validator);
        if (updatedValidators.length !== validators.length) {
          isControlUpdated = true;
          control.setValidators(updatedValidators);
        }
      }
    }
    if (dir.asyncValidator !== null) {
      const asyncValidators = getControlAsyncValidators(control);
      if (Array.isArray(asyncValidators) && asyncValidators.length > 0) {
        const updatedAsyncValidators = asyncValidators.filter((asyncValidator) => asyncValidator !== dir.asyncValidator);
        if (updatedAsyncValidators.length !== asyncValidators.length) {
          isControlUpdated = true;
          control.setAsyncValidators(updatedAsyncValidators);
        }
      }
    }
  }
  const noop2 = () => {
  };
  registerOnValidatorChange(dir._rawValidators, noop2);
  registerOnValidatorChange(dir._rawAsyncValidators, noop2);
  return isControlUpdated;
}
function setUpViewChangePipeline(control, dir) {
  dir.valueAccessor.registerOnChange((newValue) => {
    control._pendingValue = newValue;
    control._pendingChange = true;
    control._pendingDirty = true;
    if (control.updateOn === "change")
      updateControl(control, dir);
  });
}
function setUpBlurPipeline(control, dir) {
  dir.valueAccessor.registerOnTouched(() => {
    control._pendingTouched = true;
    if (control.updateOn === "blur" && control._pendingChange)
      updateControl(control, dir);
    if (control.updateOn !== "submit")
      control.markAsTouched();
  });
}
function updateControl(control, dir) {
  if (control._pendingDirty)
    control.markAsDirty();
  control.setValue(control._pendingValue, { emitModelToViewChange: false });
  dir.viewToModelUpdate(control._pendingValue);
  control._pendingChange = false;
}
function setUpModelChangePipeline(control, dir) {
  const onChange = (newValue, emitModelEvent) => {
    dir.valueAccessor.writeValue(newValue);
    if (emitModelEvent)
      dir.viewToModelUpdate(newValue);
  };
  control.registerOnChange(onChange);
  dir._registerOnDestroy(() => {
    control._unregisterOnChange(onChange);
  });
}
function setUpFormContainer(control, dir) {
  if (control == null && (typeof ngDevMode === "undefined" || ngDevMode))
    _throwError(dir, "Cannot find control with");
  setUpValidators(control, dir);
}
function cleanUpFormContainer(control, dir) {
  return cleanUpValidators(control, dir);
}
function _noControlError(dir) {
  return _throwError(dir, "There is no FormControl instance attached to form control element with");
}
function _throwError(dir, message) {
  const messageEnd = _describeControlLocation(dir);
  throw new Error(`${message} ${messageEnd}`);
}
function _describeControlLocation(dir) {
  const path = dir.path;
  if (path && path.length > 1)
    return `path: '${path.join(" -> ")}'`;
  if (path?.[0])
    return `name: '${path}'`;
  return "unspecified name attribute";
}
function _throwMissingValueAccessorError(dir) {
  const loc = _describeControlLocation(dir);
  throw new RuntimeError(-1203, `No value accessor for form control ${loc}.`);
}
function _throwInvalidValueAccessorError(dir) {
  const loc = _describeControlLocation(dir);
  throw new RuntimeError(1200, `Value accessor was not provided as an array for form control with ${loc}. Check that the \`NG_VALUE_ACCESSOR\` token is configured as a \`multi: true\` provider.`);
}
function isPropertyUpdated(changes, viewModel) {
  if (!changes.hasOwnProperty("model"))
    return false;
  const change = changes["model"];
  if (change.isFirstChange())
    return true;
  return !Object.is(viewModel, change.currentValue);
}
function isBuiltInAccessor(valueAccessor) {
  return Object.getPrototypeOf(valueAccessor.constructor) === BuiltInControlValueAccessor;
}
function syncPendingControls(form, directives) {
  form._syncPendingControls();
  directives.forEach((dir) => {
    const control = dir.control;
    if (control.updateOn === "submit" && control._pendingChange) {
      dir.viewToModelUpdate(control._pendingValue);
      control._pendingChange = false;
    }
  });
}
function selectValueAccessor(dir, valueAccessors) {
  if (!valueAccessors)
    return null;
  if (!Array.isArray(valueAccessors) && (typeof ngDevMode === "undefined" || ngDevMode))
    _throwInvalidValueAccessorError(dir);
  let defaultAccessor = void 0;
  let builtinAccessor = void 0;
  let customAccessor = void 0;
  valueAccessors.forEach((v) => {
    if (v.constructor === DefaultValueAccessor) {
      defaultAccessor = v;
    } else if (isBuiltInAccessor(v)) {
      if (builtinAccessor && (typeof ngDevMode === "undefined" || ngDevMode))
        _throwError(dir, "More than one built-in value accessor matches form control with");
      builtinAccessor = v;
    } else {
      if (customAccessor && (typeof ngDevMode === "undefined" || ngDevMode))
        _throwError(dir, "More than one custom value accessor matches form control with");
      customAccessor = v;
    }
  });
  if (customAccessor)
    return customAccessor;
  if (builtinAccessor)
    return builtinAccessor;
  if (defaultAccessor)
    return defaultAccessor;
  if (typeof ngDevMode === "undefined" || ngDevMode) {
    _throwError(dir, "No valid value accessor for form control with");
  }
  return null;
}
function removeListItem$1(list, el) {
  const index = list.indexOf(el);
  if (index > -1)
    list.splice(index, 1);
}
function _ngModelWarning(name, type, instance, warningConfig) {
  if (warningConfig === "never")
    return;
  if ((warningConfig === null || warningConfig === "once") && !type._ngModelWarningSentOnce || warningConfig === "always" && !instance._ngModelWarningSent) {
    console.warn(ngModelWarning(name));
    type._ngModelWarningSentOnce = true;
    instance._ngModelWarningSent = true;
  }
}
function removeListItem(list, el) {
  const index = list.indexOf(el);
  if (index > -1)
    list.splice(index, 1);
}
function isFormControlState(formState) {
  return typeof formState === "object" && formState !== null && Object.keys(formState).length === 2 && "value" in formState && "disabled" in formState;
}
function modelParentException() {
  return new RuntimeError(1350, `
    ngModel cannot be used to register form controls with a parent formGroup directive.  Try using
    formGroup's partner directive "formControlName" instead.  Example:

    ${formControlNameExample}

    Or, if you'd like to avoid registering this form control, indicate that it's standalone in ngModelOptions:

    Example:

    ${ngModelWithFormGroupExample}`);
}
function formGroupNameException() {
  return new RuntimeError(1351, `
    ngModel cannot be used to register form controls with a parent formGroupName or formArrayName directive.

    Option 1: Use formControlName instead of ngModel (reactive strategy):

    ${formGroupNameExample}

    Option 2:  Update ngModel's parent be ngModelGroup (template-driven strategy):

    ${ngModelGroupExample}`);
}
function missingNameException() {
  return new RuntimeError(1352, `If ngModel is used within a form tag, either the name attribute must be set or the form
    control must be defined as 'standalone' in ngModelOptions.

    Example 1: <input [(ngModel)]="person.firstName" name="first">
    Example 2: <input [(ngModel)]="person.firstName" [ngModelOptions]="{standalone: true}">`);
}
function modelGroupParentException() {
  return new RuntimeError(1353, `
    ngModelGroup cannot be used with a parent formGroup directive.

    Option 1: Use formGroupName instead of ngModelGroup (reactive strategy):

    ${formGroupNameExample}

    Option 2:  Use a regular form tag instead of the formGroup directive (template-driven strategy):

    ${ngModelGroupExample}`);
}
function checkParentType$1(parent) {
  if (!(parent instanceof NgModelGroup) && parent instanceof AbstractFormGroupDirective) {
    throw formGroupNameException();
  } else if (!(parent instanceof NgModelGroup) && !(parent instanceof NgForm)) {
    throw modelParentException();
  }
}
function throwNameError() {
  throw new RuntimeError(1202, `
      If you define both a name and a formControlName attribute on your radio button, their values
      must match. Ex: <input type="radio" formControlName="food" name="food">
    `);
}
function hasInvalidParent(parent) {
  return !(parent instanceof FormGroupName) && !(parent instanceof FormGroupDirective) && !(parent instanceof FormArrayName);
}
function checkParentType(parent, name) {
  if (!(parent instanceof FormGroupName) && parent instanceof AbstractFormGroupDirective) {
    throw ngModelGroupException();
  } else if (!(parent instanceof FormGroupName) && !(parent instanceof FormGroupDirective) && !(parent instanceof FormArrayName)) {
    throw controlParentException(name);
  }
}
function _buildValueString$1(id, value) {
  if (id == null)
    return `${value}`;
  if (value && typeof value === "object")
    value = "Object";
  return `${id}: ${value}`.slice(0, 50);
}
function _extractId$1(valueString) {
  return valueString.split(":")[0];
}
function _buildValueString(id, value) {
  if (id == null)
    return `${value}`;
  if (typeof value === "string")
    value = `'${value}'`;
  if (value && typeof value === "object")
    value = "Object";
  return `${id}: ${value}`.slice(0, 50);
}
function _extractId(valueString) {
  return valueString.split(":")[0];
}
function toInteger(value) {
  return typeof value === "number" ? value : parseInt(value, 10);
}
function toFloat(value) {
  return typeof value === "number" ? value : parseFloat(value);
}
function isAbstractControlOptions(options) {
  return !!options && (options.asyncValidators !== void 0 || options.validators !== void 0 || options.updateOn !== void 0);
}
var BaseControlValueAccessor, BuiltInControlValueAccessor, NG_VALUE_ACCESSOR, CHECKBOX_VALUE_ACCESSOR, CheckboxControlValueAccessor, DEFAULT_VALUE_ACCESSOR, COMPOSITION_BUFFER_MODE, DefaultValueAccessor, NG_VALIDATORS, NG_ASYNC_VALIDATORS, EMAIL_REGEXP, Validators, AbstractControlDirective, ControlContainer, NgControl, AbstractControlStatus, ngControlStatusHost, ngGroupStatusHost, NgControlStatus, NgControlStatusGroup, formControlNameExample, formGroupNameExample, formArrayNameExample, ngModelGroupExample, ngModelWithFormGroupExample, disabledAttrWarning, asyncValidatorsDroppedWithOptsWarning, VALID, INVALID, PENDING, DISABLED, ControlEvent, ValueChangeEvent, PristineChangeEvent, TouchedChangeEvent, StatusChangeEvent, FormSubmittedEvent, FormResetEvent, AbstractControl, FormGroup, FormRecord, CALL_SET_DISABLED_STATE, setDisabledStateDefault, formDirectiveProvider$1, resolvedPromise$1, NgForm, FormControl, isFormControl, AbstractFormGroupDirective, modelGroupProvider, NgModelGroup, formControlBinding$1, resolvedPromise, NgModel, \u0275NgNoValidate, NUMBER_VALUE_ACCESSOR, NumberValueAccessor, RADIO_VALUE_ACCESSOR, RadioControlRegistry, RadioControlValueAccessor, RANGE_VALUE_ACCESSOR, RangeValueAccessor, NG_MODEL_WITH_FORM_CONTROL_WARNING, formControlBinding, FormControlDirective, formDirectiveProvider, FormGroupDirective, formGroupNameProvider, FormGroupName, formArrayNameProvider, FormArrayName, controlNameBinding, FormControlName, SELECT_VALUE_ACCESSOR, SelectControlValueAccessor, NgSelectOption, SELECT_MULTIPLE_VALUE_ACCESSOR, SelectMultipleControlValueAccessor, \u0275NgSelectMultipleOption, AbstractValidatorDirective, MAX_VALIDATOR, MaxValidator, MIN_VALIDATOR, MinValidator, REQUIRED_VALIDATOR, CHECKBOX_REQUIRED_VALIDATOR, RequiredValidator, CheckboxRequiredValidator, EMAIL_VALIDATOR, EmailValidator, MIN_LENGTH_VALIDATOR, MinLengthValidator, MAX_LENGTH_VALIDATOR, MaxLengthValidator, PATTERN_VALIDATOR, PatternValidator, SHARED_FORM_DIRECTIVES, TEMPLATE_DRIVEN_DIRECTIVES, REACTIVE_DRIVEN_DIRECTIVES, \u0275InternalFormsSharedModule, FormArray, FormBuilder, NonNullableFormBuilder, UntypedFormBuilder, FormsModule, ReactiveFormsModule;
var init_forms = __esm({
  "node_modules/@angular/forms/fesm2022/forms.mjs"() {
    "use strict";
    init_core();
    init_core();
    init_common();
    init_esm();
    init_operators();
    BaseControlValueAccessor = class _BaseControlValueAccessor {
      _renderer;
      _elementRef;
      /**
       * The registered callback function called when a change or input event occurs on the input
       * element.
       * @docs-private
       */
      onChange = (_) => {
      };
      /**
       * The registered callback function called when a blur event occurs on the input element.
       * @docs-private
       */
      onTouched = () => {
      };
      constructor(_renderer, _elementRef) {
        this._renderer = _renderer;
        this._elementRef = _elementRef;
      }
      /**
       * Helper method that sets a property on a target element using the current Renderer
       * implementation.
       * @docs-private
       */
      setProperty(key, value) {
        this._renderer.setProperty(this._elementRef.nativeElement, key, value);
      }
      /**
       * Registers a function called when the control is touched.
       * @docs-private
       */
      registerOnTouched(fn) {
        this.onTouched = fn;
      }
      /**
       * Registers a function called when the control value changes.
       * @docs-private
       */
      registerOnChange(fn) {
        this.onChange = fn;
      }
      /**
       * Sets the "disabled" property on the range input element.
       * @docs-private
       */
      setDisabledState(isDisabled) {
        this.setProperty("disabled", isDisabled);
      }
      static \u0275fac = \u0275\u0275ngDeclareFactory({ minVersion: "12.0.0", version: "20.3.29", ngImport: core_exports, type: _BaseControlValueAccessor, deps: [{ token: Renderer2 }, { token: ElementRef }], target: FactoryTarget.Directive });
      static \u0275dir = \u0275\u0275ngDeclareDirective({ minVersion: "14.0.0", version: "20.3.29", type: _BaseControlValueAccessor, isStandalone: true, ngImport: core_exports });
    };
    \u0275\u0275ngDeclareClassMetadata({ minVersion: "12.0.0", version: "20.3.29", ngImport: core_exports, type: BaseControlValueAccessor, decorators: [{
      type: Directive
    }], ctorParameters: () => [{ type: Renderer2 }, { type: ElementRef }] });
    BuiltInControlValueAccessor = class _BuiltInControlValueAccessor extends BaseControlValueAccessor {
      static \u0275fac = \u0275\u0275ngDeclareFactory({ minVersion: "12.0.0", version: "20.3.29", ngImport: core_exports, type: _BuiltInControlValueAccessor, deps: null, target: FactoryTarget.Directive });
      static \u0275dir = \u0275\u0275ngDeclareDirective({ minVersion: "14.0.0", version: "20.3.29", type: _BuiltInControlValueAccessor, isStandalone: true, usesInheritance: true, ngImport: core_exports });
    };
    \u0275\u0275ngDeclareClassMetadata({ minVersion: "12.0.0", version: "20.3.29", ngImport: core_exports, type: BuiltInControlValueAccessor, decorators: [{
      type: Directive
    }] });
    NG_VALUE_ACCESSOR = new InjectionToken(ngDevMode ? "NgValueAccessor" : "");
    CHECKBOX_VALUE_ACCESSOR = {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => CheckboxControlValueAccessor),
      multi: true
    };
    CheckboxControlValueAccessor = class _CheckboxControlValueAccessor extends BuiltInControlValueAccessor {
      /**
       * Sets the "checked" property on the input element.
       * @docs-private
       */
      writeValue(value) {
        this.setProperty("checked", value);
      }
      static \u0275fac = \u0275\u0275ngDeclareFactory({ minVersion: "12.0.0", version: "20.3.29", ngImport: core_exports, type: _CheckboxControlValueAccessor, deps: null, target: FactoryTarget.Directive });
      static \u0275dir = \u0275\u0275ngDeclareDirective({ minVersion: "14.0.0", version: "20.3.29", type: _CheckboxControlValueAccessor, isStandalone: false, selector: "input[type=checkbox][formControlName],input[type=checkbox][formControl],input[type=checkbox][ngModel]", host: { listeners: { "change": "onChange($any($event.target).checked)", "blur": "onTouched()" } }, providers: [CHECKBOX_VALUE_ACCESSOR], usesInheritance: true, ngImport: core_exports });
    };
    \u0275\u0275ngDeclareClassMetadata({ minVersion: "12.0.0", version: "20.3.29", ngImport: core_exports, type: CheckboxControlValueAccessor, decorators: [{
      type: Directive,
      args: [{
        selector: "input[type=checkbox][formControlName],input[type=checkbox][formControl],input[type=checkbox][ngModel]",
        host: { "(change)": "onChange($any($event.target).checked)", "(blur)": "onTouched()" },
        providers: [CHECKBOX_VALUE_ACCESSOR],
        standalone: false
      }]
    }] });
    DEFAULT_VALUE_ACCESSOR = {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => DefaultValueAccessor),
      multi: true
    };
    COMPOSITION_BUFFER_MODE = new InjectionToken(ngDevMode ? "CompositionEventMode" : "");
    DefaultValueAccessor = class _DefaultValueAccessor extends BaseControlValueAccessor {
      _compositionMode;
      /** Whether the user is creating a composition string (IME events). */
      _composing = false;
      constructor(renderer, elementRef, _compositionMode) {
        super(renderer, elementRef);
        this._compositionMode = _compositionMode;
        if (this._compositionMode == null) {
          this._compositionMode = !_isAndroid();
        }
      }
      /**
       * Sets the "value" property on the input element.
       * @docs-private
       */
      writeValue(value) {
        const normalizedValue = value == null ? "" : value;
        this.setProperty("value", normalizedValue);
      }
      /** @internal */
      _handleInput(value) {
        if (!this._compositionMode || this._compositionMode && !this._composing) {
          this.onChange(value);
        }
      }
      /** @internal */
      _compositionStart() {
        this._composing = true;
      }
      /** @internal */
      _compositionEnd(value) {
        this._composing = false;
        this._compositionMode && this.onChange(value);
      }
      static \u0275fac = \u0275\u0275ngDeclareFactory({ minVersion: "12.0.0", version: "20.3.29", ngImport: core_exports, type: _DefaultValueAccessor, deps: [{ token: Renderer2 }, { token: ElementRef }, { token: COMPOSITION_BUFFER_MODE, optional: true }], target: FactoryTarget.Directive });
      static \u0275dir = \u0275\u0275ngDeclareDirective({ minVersion: "14.0.0", version: "20.3.29", type: _DefaultValueAccessor, isStandalone: false, selector: "input:not([type=checkbox])[formControlName],textarea[formControlName],input:not([type=checkbox])[formControl],textarea[formControl],input:not([type=checkbox])[ngModel],textarea[ngModel],[ngDefaultControl]", host: { listeners: { "input": "_handleInput($any($event.target).value)", "blur": "onTouched()", "compositionstart": "_compositionStart()", "compositionend": "_compositionEnd($any($event.target).value)" } }, providers: [DEFAULT_VALUE_ACCESSOR], usesInheritance: true, ngImport: core_exports });
    };
    \u0275\u0275ngDeclareClassMetadata({ minVersion: "12.0.0", version: "20.3.29", ngImport: core_exports, type: DefaultValueAccessor, decorators: [{
      type: Directive,
      args: [{
        selector: "input:not([type=checkbox])[formControlName],textarea[formControlName],input:not([type=checkbox])[formControl],textarea[formControl],input:not([type=checkbox])[ngModel],textarea[ngModel],[ngDefaultControl]",
        // TODO: vsavkin replace the above selector with the one below it once
        // https://github.com/angular/angular/issues/3011 is implemented
        // selector: '[ngModel],[formControl],[formControlName]',
        host: {
          "(input)": "_handleInput($any($event.target).value)",
          "(blur)": "onTouched()",
          "(compositionstart)": "_compositionStart()",
          "(compositionend)": "_compositionEnd($any($event.target).value)"
        },
        providers: [DEFAULT_VALUE_ACCESSOR],
        standalone: false
      }]
    }], ctorParameters: () => [{ type: Renderer2 }, { type: ElementRef }, { type: void 0, decorators: [{
      type: Optional
    }, {
      type: Inject,
      args: [COMPOSITION_BUFFER_MODE]
    }] }] });
    NG_VALIDATORS = new InjectionToken(ngDevMode ? "NgValidators" : "");
    NG_ASYNC_VALIDATORS = new InjectionToken(ngDevMode ? "NgAsyncValidators" : "");
    EMAIL_REGEXP = /^(?=.{1,254}$)(?=.{1,64}@)[a-zA-Z0-9!#$%&'*+/=?^_`{|}~-]+(?:\.[a-zA-Z0-9!#$%&'*+/=?^_`{|}~-]+)*@[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?(?:\.[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?)*$/;
    Validators = class {
      /**
       * @description
       * Validator that requires the control's value to be greater than or equal to the provided number.
       *
       * @usageNotes
       *
       * ### Validate against a minimum of 3
       *
       * ```ts
       * const control = new FormControl(2, Validators.min(3));
       *
       * console.log(control.errors); // {min: {min: 3, actual: 2}}
       * ```
       *
       * @returns A validator function that returns an error map with the
       * `min` property if the validation check fails, otherwise `null`.
       *
       * @see {@link /api/forms/AbstractControl#updateValueAndValidity updateValueAndValidity}
       *
       */
      static min(min) {
        return minValidator(min);
      }
      /**
       * @description
       * Validator that requires the control's value to be less than or equal to the provided number.
       *
       * @usageNotes
       *
       * ### Validate against a maximum of 15
       *
       * ```ts
       * const control = new FormControl(16, Validators.max(15));
       *
       * console.log(control.errors); // {max: {max: 15, actual: 16}}
       * ```
       *
       * @returns A validator function that returns an error map with the
       * `max` property if the validation check fails, otherwise `null`.
       *
       * @see {@link /api/forms/AbstractControl#updateValueAndValidity updateValueAndValidity}
       *
       */
      static max(max) {
        return maxValidator(max);
      }
      /**
       * @description
       * Validator that requires the control have a non-empty value.
       *
       * @usageNotes
       *
       * ### Validate that the field is non-empty
       *
       * ```ts
       * const control = new FormControl('', Validators.required);
       *
       * console.log(control.errors); // {required: true}
       * ```
       *
       * @returns An error map with the `required` property
       * if the validation check fails, otherwise `null`.
       *
       * @see {@link /api/forms/AbstractControl#updateValueAndValidity updateValueAndValidity}
       *
       */
      static required(control) {
        return requiredValidator(control);
      }
      /**
       * @description
       * Validator that requires the control's value be true. This validator is commonly
       * used for required checkboxes.
       *
       * @usageNotes
       *
       * ### Validate that the field value is true
       *
       * ```ts
       * const control = new FormControl('some value', Validators.requiredTrue);
       *
       * console.log(control.errors); // {required: true}
       * ```
       *
       * @returns An error map that contains the `required` property
       * set to `true` if the validation check fails, otherwise `null`.
       *
       * @see {@link /api/forms/AbstractControl#updateValueAndValidity updateValueAndValidity}
       *
       */
      static requiredTrue(control) {
        return requiredTrueValidator(control);
      }
      /**
       * @description
       * Validator that requires the control's value pass an email validation test.
       *
       * Tests the value using a [regular
       * expression](https://developer.mozilla.org/en-US/docs/Web/JavaScript/Guide/Regular_Expressions)
       * pattern suitable for common use cases. The pattern is based on the definition of a valid email
       * address in the [WHATWG HTML
       * specification](https://html.spec.whatwg.org/multipage/input.html#valid-e-mail-address) with
       * some enhancements to incorporate more RFC rules (such as rules related to domain names and the
       * lengths of different parts of the address).
       *
       * The differences from the WHATWG version include:
       * - Disallow `local-part` (the part before the `@` symbol) to begin or end with a period (`.`).
       * - Disallow `local-part` to be longer than 64 characters.
       * - Disallow the whole address to be longer than 254 characters.
       *
       * If this pattern does not satisfy your business needs, you can use `Validators.pattern()` to
       * validate the value against a different pattern.
       *
       * @usageNotes
       *
       * ### Validate that the field matches a valid email pattern
       *
       * ```ts
       * const control = new FormControl('bad@', Validators.email);
       *
       * console.log(control.errors); // {email: true}
       * ```
       *
       * @returns An error map with the `email` property
       * if the validation check fails, otherwise `null`.
       *
       * @see {@link /api/forms/AbstractControl#updateValueAndValidity updateValueAndValidity}
       *
       */
      static email(control) {
        return emailValidator(control);
      }
      /**
       * @description
       * Validator that requires the number of items in the control's value to be greater than or equal
       * to the provided minimum length. This validator is also provided by default if you use
       * the HTML5 `minlength` attribute. Note that the `minLength` validator is intended to be used
       * only for types that have a numeric `length` or `size` property, such as strings, arrays or
       * sets. The `minLength` validator logic is also not invoked for values when their `length` or
       * `size` property is 0 (for example in case of an empty string or an empty array), to support
       * optional controls. You can use the standard `required` validator if empty values should not be
       * considered valid.
       *
       * @usageNotes
       *
       * ### Validate that the field has a minimum of 3 characters
       *
       * ```ts
       * const control = new FormControl('ng', Validators.minLength(3));
       *
       * console.log(control.errors); // {minlength: {requiredLength: 3, actualLength: 2}}
       * ```
       *
       * ```html
       * <input minlength="5">
       * ```
       *
       * @returns A validator function that returns an error map with the
       * `minlength` property if the validation check fails, otherwise `null`.
       *
       * @see {@link /api/forms/AbstractControl#updateValueAndValidity updateValueAndValidity}
       *
       */
      static minLength(minLength) {
        return minLengthValidator(minLength);
      }
      /**
       * @description
       * Validator that requires the number of items in the control's value to be less than or equal
       * to the provided maximum length. This validator is also provided by default if you use
       * the HTML5 `maxlength` attribute. Note that the `maxLength` validator is intended to be used
       * only for types that have a numeric `length` or `size` property, such as strings, arrays or
       * sets.
       *
       * @usageNotes
       *
       * ### Validate that the field has maximum of 5 characters
       *
       * ```ts
       * const control = new FormControl('Angular', Validators.maxLength(5));
       *
       * console.log(control.errors); // {maxlength: {requiredLength: 5, actualLength: 7}}
       * ```
       *
       * ```html
       * <input maxlength="5">
       * ```
       *
       * @returns A validator function that returns an error map with the
       * `maxlength` property if the validation check fails, otherwise `null`.
       *
       * @see {@link /api/forms/AbstractControl#updateValueAndValidity updateValueAndValidity}
       *
       */
      static maxLength(maxLength) {
        return maxLengthValidator(maxLength);
      }
      /**
       * @description
       * Validator that requires the control's value to match a regex pattern. This validator is also
       * provided by default if you use the HTML5 `pattern` attribute.
       *
       * @usageNotes
       *
       * ### Validate that the field only contains letters or spaces
       *
       * ```ts
       * const control = new FormControl('1', Validators.pattern('[a-zA-Z ]*'));
       *
       * console.log(control.errors); // {pattern: {requiredPattern: '^[a-zA-Z ]*$', actualValue: '1'}}
       * ```
       *
       * ```html
       * <input pattern="[a-zA-Z ]*">
       * ```
       *
       * ### Pattern matching with the global or sticky flag
       *
       * `RegExp` objects created with the `g` or `y` flags that are passed into `Validators.pattern`
       * can produce different results on the same input when validations are run consecutively. This is
       * due to how the behavior of `RegExp.prototype.test` is
       * specified in [ECMA-262](https://tc39.es/ecma262/#sec-regexpbuiltinexec)
       * (`RegExp` preserves the index of the last match when the global or sticky flag is used).
       * Due to this behavior, it is recommended that when using
       * `Validators.pattern` you **do not** pass in a `RegExp` object with either the global or sticky
       * flag enabled.
       *
       * ```ts
       * // Not recommended (since the `g` flag is used)
       * const controlOne = new FormControl('1', Validators.pattern(/foo/g));
       *
       * // Good
       * const controlTwo = new FormControl('1', Validators.pattern(/foo/));
       * ```
       *
       * @param pattern A regular expression to be used as is to test the values, or a string.
       * If a string is passed, the `^` character is prepended and the `$` character is
       * appended to the provided string (if not already present), and the resulting regular
       * expression is used to test the values.
       *
       * @returns A validator function that returns an error map with the
       * `pattern` property if the validation check fails, otherwise `null`.
       *
       * @see {@link /api/forms/AbstractControl#updateValueAndValidity updateValueAndValidity}
       *
       */
      static pattern(pattern) {
        return patternValidator(pattern);
      }
      /**
       * @description
       * Validator that performs no operation.
       *
       * @see {@link /api/forms/AbstractControl#updateValueAndValidity updateValueAndValidity}
       *
       */
      static nullValidator(control) {
        return nullValidator();
      }
      static compose(validators) {
        return compose(validators);
      }
      /**
       * @description
       * Compose multiple async validators into a single function that returns the union
       * of the individual error objects for the provided control.
       *
       * @returns A validator function that returns an error map with the
       * merged error objects of the async validators if the validation check fails, otherwise `null`.
       *
       * @see {@link /api/forms/AbstractControl#updateValueAndValidity updateValueAndValidity}
       *
       */
      static composeAsync(validators) {
        return composeAsync(validators);
      }
    };
    AbstractControlDirective = class {
      /**
       * @description
       * Reports the value of the control if it is present, otherwise null.
       */
      get value() {
        return this.control ? this.control.value : null;
      }
      /**
       * @description
       * Reports whether the control is valid. A control is considered valid if no
       * validation errors exist with the current value.
       * If the control is not present, null is returned.
       */
      get valid() {
        return this.control ? this.control.valid : null;
      }
      /**
       * @description
       * Reports whether the control is invalid, meaning that an error exists in the input value.
       * If the control is not present, null is returned.
       */
      get invalid() {
        return this.control ? this.control.invalid : null;
      }
      /**
       * @description
       * Reports whether a control is pending, meaning that async validation is occurring and
       * errors are not yet available for the input value. If the control is not present, null is
       * returned.
       */
      get pending() {
        return this.control ? this.control.pending : null;
      }
      /**
       * @description
       * Reports whether the control is disabled, meaning that the control is disabled
       * in the UI and is exempt from validation checks and excluded from aggregate
       * values of ancestor controls. If the control is not present, null is returned.
       */
      get disabled() {
        return this.control ? this.control.disabled : null;
      }
      /**
       * @description
       * Reports whether the control is enabled, meaning that the control is included in ancestor
       * calculations of validity or value. If the control is not present, null is returned.
       */
      get enabled() {
        return this.control ? this.control.enabled : null;
      }
      /**
       * @description
       * Reports the control's validation errors. If the control is not present, null is returned.
       */
      get errors() {
        return this.control ? this.control.errors : null;
      }
      /**
       * @description
       * Reports whether the control is pristine, meaning that the user has not yet changed
       * the value in the UI. If the control is not present, null is returned.
       */
      get pristine() {
        return this.control ? this.control.pristine : null;
      }
      /**
       * @description
       * Reports whether the control is dirty, meaning that the user has changed
       * the value in the UI. If the control is not present, null is returned.
       */
      get dirty() {
        return this.control ? this.control.dirty : null;
      }
      /**
       * @description
       * Reports whether the control is touched, meaning that the user has triggered
       * a `blur` event on it. If the control is not present, null is returned.
       */
      get touched() {
        return this.control ? this.control.touched : null;
      }
      /**
       * @description
       * Reports the validation status of the control. Possible values include:
       * 'VALID', 'INVALID', 'DISABLED', and 'PENDING'.
       * If the control is not present, null is returned.
       */
      get status() {
        return this.control ? this.control.status : null;
      }
      /**
       * @description
       * Reports whether the control is untouched, meaning that the user has not yet triggered
       * a `blur` event on it. If the control is not present, null is returned.
       */
      get untouched() {
        return this.control ? this.control.untouched : null;
      }
      /**
       * @description
       * Returns a multicasting observable that emits a validation status whenever it is
       * calculated for the control. If the control is not present, null is returned.
       */
      get statusChanges() {
        return this.control ? this.control.statusChanges : null;
      }
      /**
       * @description
       * Returns a multicasting observable of value changes for the control that emits every time the
       * value of the control changes in the UI or programmatically.
       * If the control is not present, null is returned.
       */
      get valueChanges() {
        return this.control ? this.control.valueChanges : null;
      }
      /**
       * @description
       * Returns an array that represents the path from the top-level form to this control.
       * Each index is the string name of the control on that level.
       */
      get path() {
        return null;
      }
      /**
       * Contains the result of merging synchronous validators into a single validator function
       * (combined using `Validators.compose`).
       */
      _composedValidatorFn;
      /**
       * Contains the result of merging asynchronous validators into a single validator function
       * (combined using `Validators.composeAsync`).
       */
      _composedAsyncValidatorFn;
      /**
       * Set of synchronous validators as they were provided while calling `setValidators` function.
       * @internal
       */
      _rawValidators = [];
      /**
       * Set of asynchronous validators as they were provided while calling `setAsyncValidators`
       * function.
       * @internal
       */
      _rawAsyncValidators = [];
      /**
       * Sets synchronous validators for this directive.
       * @internal
       */
      _setValidators(validators) {
        this._rawValidators = validators || [];
        this._composedValidatorFn = composeValidators(this._rawValidators);
      }
      /**
       * Sets asynchronous validators for this directive.
       * @internal
       */
      _setAsyncValidators(validators) {
        this._rawAsyncValidators = validators || [];
        this._composedAsyncValidatorFn = composeAsyncValidators(this._rawAsyncValidators);
      }
      /**
       * @description
       * Synchronous validator function composed of all the synchronous validators registered with this
       * directive.
       */
      get validator() {
        return this._composedValidatorFn || null;
      }
      /**
       * @description
       * Asynchronous validator function composed of all the asynchronous validators registered with
       * this directive.
       */
      get asyncValidator() {
        return this._composedAsyncValidatorFn || null;
      }
      /*
       * The set of callbacks to be invoked when directive instance is being destroyed.
       */
      _onDestroyCallbacks = [];
      /**
       * Internal function to register callbacks that should be invoked
       * when directive instance is being destroyed.
       * @internal
       */
      _registerOnDestroy(fn) {
        this._onDestroyCallbacks.push(fn);
      }
      /**
       * Internal function to invoke all registered "on destroy" callbacks.
       * Note: calling this function also clears the list of callbacks.
       * @internal
       */
      _invokeOnDestroyCallbacks() {
        this._onDestroyCallbacks.forEach((fn) => fn());
        this._onDestroyCallbacks = [];
      }
      /**
       * @description
       * Resets the control with the provided value if the control is present.
       */
      reset(value = void 0) {
        if (this.control)
          this.control.reset(value);
      }
      /**
       * @description
       * Reports whether the control with the given path has the error specified.
       *
       * @param errorCode The code of the error to check
       * @param path A list of control names that designates how to move from the current control
       * to the control that should be queried for errors.
       *
       * @usageNotes
       * For example, for the following `FormGroup`:
       *
       * ```ts
       * form = new FormGroup({
       *   address: new FormGroup({ street: new FormControl() })
       * });
       * ```
       *
       * The path to the 'street' control from the root form would be 'address' -> 'street'.
       *
       * It can be provided to this method in one of two formats:
       *
       * 1. An array of string control names, e.g. `['address', 'street']`
       * 1. A period-delimited list of control names in one string, e.g. `'address.street'`
       *
       * If no path is given, this method checks for the error on the current control.
       *
       * @returns whether the given error is present in the control at the given path.
       *
       * If the control is not present, false is returned.
       */
      hasError(errorCode, path) {
        return this.control ? this.control.hasError(errorCode, path) : false;
      }
      /**
       * @description
       * Reports error data for the control with the given path.
       *
       * @param errorCode The code of the error to check
       * @param path A list of control names that designates how to move from the current control
       * to the control that should be queried for errors.
       *
       * @usageNotes
       * For example, for the following `FormGroup`:
       *
       * ```ts
       * form = new FormGroup({
       *   address: new FormGroup({ street: new FormControl() })
       * });
       * ```
       *
       * The path to the 'street' control from the root form would be 'address' -> 'street'.
       *
       * It can be provided to this method in one of two formats:
       *
       * 1. An array of string control names, e.g. `['address', 'street']`
       * 1. A period-delimited list of control names in one string, e.g. `'address.street'`
       *
       * @returns error data for that particular error. If the control or error is not present,
       * null is returned.
       */
      getError(errorCode, path) {
        return this.control ? this.control.getError(errorCode, path) : null;
      }
    };
    ControlContainer = class extends AbstractControlDirective {
      /**
       * @description
       * The name for the control
       */
      name;
      /**
       * @description
       * The top-level form directive for the control.
       */
      get formDirective() {
        return null;
      }
      /**
       * @description
       * The path to this group.
       */
      get path() {
        return null;
      }
    };
    NgControl = class extends AbstractControlDirective {
      /**
       * @description
       * The parent form for the control.
       *
       * @internal
       */
      _parent = null;
      /**
       * @description
       * The name for the control
       */
      name = null;
      /**
       * @description
       * The value accessor for the control
       */
      valueAccessor = null;
    };
    AbstractControlStatus = class {
      _cd;
      constructor(cd) {
        this._cd = cd;
      }
      get isTouched() {
        this._cd?.control?._touched?.();
        return !!this._cd?.control?.touched;
      }
      get isUntouched() {
        return !!this._cd?.control?.untouched;
      }
      get isPristine() {
        this._cd?.control?._pristine?.();
        return !!this._cd?.control?.pristine;
      }
      get isDirty() {
        return !!this._cd?.control?.dirty;
      }
      get isValid() {
        this._cd?.control?._status?.();
        return !!this._cd?.control?.valid;
      }
      get isInvalid() {
        return !!this._cd?.control?.invalid;
      }
      get isPending() {
        return !!this._cd?.control?.pending;
      }
      get isSubmitted() {
        this._cd?._submitted?.();
        return !!this._cd?.submitted;
      }
    };
    ngControlStatusHost = {
      "[class.ng-untouched]": "isUntouched",
      "[class.ng-touched]": "isTouched",
      "[class.ng-pristine]": "isPristine",
      "[class.ng-dirty]": "isDirty",
      "[class.ng-valid]": "isValid",
      "[class.ng-invalid]": "isInvalid",
      "[class.ng-pending]": "isPending"
    };
    ngGroupStatusHost = __spreadProps(__spreadValues({}, ngControlStatusHost), {
      "[class.ng-submitted]": "isSubmitted"
    });
    NgControlStatus = class _NgControlStatus extends AbstractControlStatus {
      constructor(cd) {
        super(cd);
      }
      static \u0275fac = \u0275\u0275ngDeclareFactory({ minVersion: "12.0.0", version: "20.3.29", ngImport: core_exports, type: _NgControlStatus, deps: [{ token: NgControl, self: true }], target: FactoryTarget.Directive });
      static \u0275dir = \u0275\u0275ngDeclareDirective({ minVersion: "14.0.0", version: "20.3.29", type: _NgControlStatus, isStandalone: false, selector: "[formControlName],[ngModel],[formControl]", host: { properties: { "class.ng-untouched": "isUntouched", "class.ng-touched": "isTouched", "class.ng-pristine": "isPristine", "class.ng-dirty": "isDirty", "class.ng-valid": "isValid", "class.ng-invalid": "isInvalid", "class.ng-pending": "isPending" } }, usesInheritance: true, ngImport: core_exports });
    };
    \u0275\u0275ngDeclareClassMetadata({ minVersion: "12.0.0", version: "20.3.29", ngImport: core_exports, type: NgControlStatus, decorators: [{
      type: Directive,
      args: [{
        selector: "[formControlName],[ngModel],[formControl]",
        host: ngControlStatusHost,
        standalone: false
      }]
    }], ctorParameters: () => [{ type: NgControl, decorators: [{
      type: Self
    }] }] });
    NgControlStatusGroup = class _NgControlStatusGroup extends AbstractControlStatus {
      constructor(cd) {
        super(cd);
      }
      static \u0275fac = \u0275\u0275ngDeclareFactory({ minVersion: "12.0.0", version: "20.3.29", ngImport: core_exports, type: _NgControlStatusGroup, deps: [{ token: ControlContainer, optional: true, self: true }], target: FactoryTarget.Directive });
      static \u0275dir = \u0275\u0275ngDeclareDirective({ minVersion: "14.0.0", version: "20.3.29", type: _NgControlStatusGroup, isStandalone: false, selector: "[formGroupName],[formArrayName],[ngModelGroup],[formGroup],form:not([ngNoForm]),[ngForm]", host: { properties: { "class.ng-untouched": "isUntouched", "class.ng-touched": "isTouched", "class.ng-pristine": "isPristine", "class.ng-dirty": "isDirty", "class.ng-valid": "isValid", "class.ng-invalid": "isInvalid", "class.ng-pending": "isPending", "class.ng-submitted": "isSubmitted" } }, usesInheritance: true, ngImport: core_exports });
    };
    \u0275\u0275ngDeclareClassMetadata({ minVersion: "12.0.0", version: "20.3.29", ngImport: core_exports, type: NgControlStatusGroup, decorators: [{
      type: Directive,
      args: [{
        selector: "[formGroupName],[formArrayName],[ngModelGroup],[formGroup],form:not([ngNoForm]),[ngForm]",
        host: ngGroupStatusHost,
        standalone: false
      }]
    }], ctorParameters: () => [{ type: ControlContainer, decorators: [{
      type: Optional
    }, {
      type: Self
    }] }] });
    formControlNameExample = `
  <div [formGroup]="myGroup">
    <input formControlName="firstName">
  </div>

  In your class:

  this.myGroup = new FormGroup({
      firstName: new FormControl()
  });`;
    formGroupNameExample = `
  <div [formGroup]="myGroup">
      <div formGroupName="person">
        <input formControlName="firstName">
      </div>
  </div>

  In your class:

  this.myGroup = new FormGroup({
      person: new FormGroup({ firstName: new FormControl() })
  });`;
    formArrayNameExample = `
  <div [formGroup]="myGroup">
    <div formArrayName="cities">
      <div *ngFor="let city of cityArray.controls; index as i">
        <input [formControlName]="i">
      </div>
    </div>
  </div>

  In your class:

  this.cityArray = new FormArray([new FormControl('SF')]);
  this.myGroup = new FormGroup({
    cities: this.cityArray
  });`;
    ngModelGroupExample = `
  <form>
      <div ngModelGroup="person">
        <input [(ngModel)]="person.name" name="firstName">
      </div>
  </form>`;
    ngModelWithFormGroupExample = `
  <div [formGroup]="myGroup">
      <input formControlName="firstName">
      <input [(ngModel)]="showMoreControls" [ngModelOptions]="{standalone: true}">
  </div>
`;
    disabledAttrWarning = `
  It looks like you're using the disabled attribute with a reactive form directive. If you set disabled to true
  when you set up this control in your component class, the disabled attribute will actually be set in the DOM for
  you. We recommend using this approach to avoid 'changed after checked' errors.

  Example:
  // Specify the \`disabled\` property at control creation time:
  form = new FormGroup({
    first: new FormControl({value: 'Nancy', disabled: true}, Validators.required),
    last: new FormControl('Drew', Validators.required)
  });

  // Controls can also be enabled/disabled after creation:
  form.get('first')?.enable();
  form.get('last')?.disable();
`;
    asyncValidatorsDroppedWithOptsWarning = `
  It looks like you're constructing using a FormControl with both an options argument and an
  async validators argument. Mixing these arguments will cause your async validators to be dropped.
  You should either put all your validators in the options object, or in separate validators
  arguments. For example:

  // Using validators arguments
  fc = new FormControl(42, Validators.required, myAsyncValidator);

  // Using AbstractControlOptions
  fc = new FormControl(42, {validators: Validators.required, asyncValidators: myAV});

  // Do NOT mix them: async validators will be dropped!
  fc = new FormControl(42, {validators: Validators.required}, /* Oops! */ myAsyncValidator);
`;
    VALID = "VALID";
    INVALID = "INVALID";
    PENDING = "PENDING";
    DISABLED = "DISABLED";
    ControlEvent = class {
    };
    ValueChangeEvent = class extends ControlEvent {
      value;
      source;
      constructor(value, source) {
        super();
        this.value = value;
        this.source = source;
      }
    };
    PristineChangeEvent = class extends ControlEvent {
      pristine;
      source;
      constructor(pristine, source) {
        super();
        this.pristine = pristine;
        this.source = source;
      }
    };
    TouchedChangeEvent = class extends ControlEvent {
      touched;
      source;
      constructor(touched, source) {
        super();
        this.touched = touched;
        this.source = source;
      }
    };
    StatusChangeEvent = class extends ControlEvent {
      status;
      source;
      constructor(status, source) {
        super();
        this.status = status;
        this.source = source;
      }
    };
    FormSubmittedEvent = class extends ControlEvent {
      source;
      constructor(source) {
        super();
        this.source = source;
      }
    };
    FormResetEvent = class extends ControlEvent {
      source;
      constructor(source) {
        super();
        this.source = source;
      }
    };
    AbstractControl = class {
      /** @internal */
      _pendingDirty = false;
      /**
       * Indicates that a control has its own pending asynchronous validation in progress.
       * It also stores if the control should emit events when the validation status changes.
       *
       * @internal
       */
      _hasOwnPendingAsyncValidator = null;
      /** @internal */
      _pendingTouched = false;
      /** @internal */
      _onCollectionChange = () => {
      };
      /** @internal */
      _updateOn;
      _parent = null;
      _asyncValidationSubscription;
      /**
       * Contains the result of merging synchronous validators into a single validator function
       * (combined using `Validators.compose`).
       *
       * @internal
       */
      _composedValidatorFn;
      /**
       * Contains the result of merging asynchronous validators into a single validator function
       * (combined using `Validators.composeAsync`).
       *
       * @internal
       */
      _composedAsyncValidatorFn;
      /**
       * Synchronous validators as they were provided:
       *  - in `AbstractControl` constructor
       *  - as an argument while calling `setValidators` function
       *  - while calling the setter on the `validator` field (e.g. `control.validator = validatorFn`)
       *
       * @internal
       */
      _rawValidators;
      /**
       * Asynchronous validators as they were provided:
       *  - in `AbstractControl` constructor
       *  - as an argument while calling `setAsyncValidators` function
       *  - while calling the setter on the `asyncValidator` field (e.g. `control.asyncValidator =
       * asyncValidatorFn`)
       *
       * @internal
       */
      _rawAsyncValidators;
      /**
       * The current value of the control.
       *
       * * For a `FormControl`, the current value.
       * * For an enabled `FormGroup`, the values of enabled controls as an object
       * with a key-value pair for each member of the group.
       * * For a disabled `FormGroup`, the values of all controls as an object
       * with a key-value pair for each member of the group.
       * * For a `FormArray`, the values of enabled controls as an array.
       *
       */
      value;
      /**
       * Initialize the AbstractControl instance.
       *
       * @param validators The function or array of functions that is used to determine the validity of
       *     this control synchronously.
       * @param asyncValidators The function or array of functions that is used to determine validity of
       *     this control asynchronously.
       */
      constructor(validators, asyncValidators) {
        this._assignValidators(validators);
        this._assignAsyncValidators(asyncValidators);
      }
      /**
       * Returns the function that is used to determine the validity of this control synchronously.
       * If multiple validators have been added, this will be a single composed function.
       * See `Validators.compose()` for additional information.
       */
      get validator() {
        return this._composedValidatorFn;
      }
      set validator(validatorFn) {
        this._rawValidators = this._composedValidatorFn = validatorFn;
      }
      /**
       * Returns the function that is used to determine the validity of this control asynchronously.
       * If multiple validators have been added, this will be a single composed function.
       * See `Validators.compose()` for additional information.
       */
      get asyncValidator() {
        return this._composedAsyncValidatorFn;
      }
      set asyncValidator(asyncValidatorFn) {
        this._rawAsyncValidators = this._composedAsyncValidatorFn = asyncValidatorFn;
      }
      /**
       * The parent control.
       */
      get parent() {
        return this._parent;
      }
      /**
       * The validation status of the control.
       *
       * @see {@link FormControlStatus}
       *
       * These status values are mutually exclusive, so a control cannot be
       * both valid AND invalid or invalid AND disabled.
       */
      get status() {
        return untracked(this.statusReactive);
      }
      set status(v) {
        untracked(() => this.statusReactive.set(v));
      }
      /** @internal */
      _status = computed(() => this.statusReactive(), ...ngDevMode ? [{ debugName: "_status" }] : []);
      statusReactive = signal(void 0, ...ngDevMode ? [{ debugName: "statusReactive" }] : []);
      /**
       * A control is `valid` when its `status` is `VALID`.
       *
       * @see {@link AbstractControl.status}
       *
       * @returns True if the control has passed all of its validation tests,
       * false otherwise.
       */
      get valid() {
        return this.status === VALID;
      }
      /**
       * A control is `invalid` when its `status` is `INVALID`.
       *
       * @see {@link AbstractControl.status}
       *
       * @returns True if this control has failed one or more of its validation checks,
       * false otherwise.
       */
      get invalid() {
        return this.status === INVALID;
      }
      /**
       * A control is `pending` when its `status` is `PENDING`.
       *
       * @see {@link AbstractControl.status}
       *
       * @returns True if this control is in the process of conducting a validation check,
       * false otherwise.
       */
      get pending() {
        return this.status == PENDING;
      }
      /**
       * A control is `disabled` when its `status` is `DISABLED`.
       *
       * Disabled controls are exempt from validation checks and
       * are not included in the aggregate value of their ancestor
       * controls.
       *
       * @see {@link AbstractControl.status}
       *
       * @returns True if the control is disabled, false otherwise.
       */
      get disabled() {
        return this.status === DISABLED;
      }
      /**
       * A control is `enabled` as long as its `status` is not `DISABLED`.
       *
       * @returns True if the control has any status other than 'DISABLED',
       * false if the status is 'DISABLED'.
       *
       * @see {@link AbstractControl.status}
       *
       */
      get enabled() {
        return this.status !== DISABLED;
      }
      /**
       * An object containing any errors generated by failing validation,
       * or null if there are no errors.
       */
      errors;
      /**
       * A control is `pristine` if the user has not yet changed
       * the value in the UI.
       *
       * @returns True if the user has not yet changed the value in the UI; compare `dirty`.
       * Programmatic changes to a control's value do not mark it dirty.
       */
      get pristine() {
        return untracked(this.pristineReactive);
      }
      set pristine(v) {
        untracked(() => this.pristineReactive.set(v));
      }
      /** @internal */
      _pristine = computed(() => this.pristineReactive(), ...ngDevMode ? [{ debugName: "_pristine" }] : []);
      pristineReactive = signal(true, ...ngDevMode ? [{ debugName: "pristineReactive" }] : []);
      /**
       * A control is `dirty` if the user has changed the value
       * in the UI.
       *
       * @returns True if the user has changed the value of this control in the UI; compare `pristine`.
       * Programmatic changes to a control's value do not mark it dirty.
       */
      get dirty() {
        return !this.pristine;
      }
      /**
       * True if the control is marked as `touched`.
       *
       * A control is marked `touched` once the user has triggered
       * a `blur` event on it.
       */
      get touched() {
        return untracked(this.touchedReactive);
      }
      set touched(v) {
        untracked(() => this.touchedReactive.set(v));
      }
      /** @internal */
      _touched = computed(() => this.touchedReactive(), ...ngDevMode ? [{ debugName: "_touched" }] : []);
      touchedReactive = signal(false, ...ngDevMode ? [{ debugName: "touchedReactive" }] : []);
      /**
       * True if the control has not been marked as touched
       *
       * A control is `untouched` if the user has not yet triggered
       * a `blur` event on it.
       */
      get untouched() {
        return !this.touched;
      }
      /**
       * Exposed as observable, see below.
       *
       * @internal
       */
      _events = new Subject();
      /**
       * A multicasting observable that emits an event every time the state of the control changes.
       * It emits for value, status, pristine or touched changes.
       *
       * **Note**: On value change, the emit happens right after a value of this control is updated. The
       * value of a parent control (for example if this FormControl is a part of a FormGroup) is updated
       * later, so accessing a value of a parent control (using the `value` property) from the callback
       * of this event might result in getting a value that has not been updated yet. Subscribe to the
       * `events` of the parent control instead.
       * For other event types, the events are emitted after the parent control has been updated.
       *
       * @see [Unified control state change events](guide/forms/reactive-forms#unified-control-state-change-events)
       */
      events = this._events.asObservable();
      /**
       * A multicasting observable that emits an event every time the value of the control changes, in
       * the UI or programmatically. It also emits an event each time you call enable() or disable()
       * without passing along {emitEvent: false} as a function argument.
       *
       * **Note**: the emit happens right after a value of this control is updated. The value of a
       * parent control (for example if this FormControl is a part of a FormGroup) is updated later, so
       * accessing a value of a parent control (using the `value` property) from the callback of this
       * event might result in getting a value that has not been updated yet. Subscribe to the
       * `valueChanges` event of the parent control instead.
       */
      valueChanges;
      /**
       * A multicasting observable that emits an event every time the validation `status` of the control
       * recalculates.
       *
       * @see {@link FormControlStatus}
       * @see {@link AbstractControl.status}
       */
      statusChanges;
      /**
       * Reports the update strategy of the `AbstractControl` (meaning
       * the event on which the control updates itself).
       * Possible values: `'change'` | `'blur'` | `'submit'`
       * Default value: `'change'`
       */
      get updateOn() {
        return this._updateOn ? this._updateOn : this.parent ? this.parent.updateOn : "change";
      }
      /**
       * Sets the synchronous validators that are active on this control.  Calling
       * this overwrites any existing synchronous validators.
       *
       * When you add or remove a validator at run time, you must call
       * `updateValueAndValidity()` for the new validation to take effect.
       *
       * If you want to add a new validator without affecting existing ones, consider
       * using `addValidators()` method instead.
       */
      setValidators(validators) {
        this._assignValidators(validators);
      }
      /**
       * Sets the asynchronous validators that are active on this control. Calling this
       * overwrites any existing asynchronous validators.
       *
       * When you add or remove a validator at run time, you must call
       * `updateValueAndValidity()` for the new validation to take effect.
       *
       * If you want to add a new validator without affecting existing ones, consider
       * using `addAsyncValidators()` method instead.
       */
      setAsyncValidators(validators) {
        this._assignAsyncValidators(validators);
      }
      /**
       * Add a synchronous validator or validators to this control, without affecting other validators.
       *
       * When you add or remove a validator at run time, you must call
       * `updateValueAndValidity()` for the new validation to take effect.
       *
       * Adding a validator that already exists will have no effect. If duplicate validator functions
       * are present in the `validators` array, only the first instance would be added to a form
       * control.
       *
       * @param validators The new validator function or functions to add to this control.
       */
      addValidators(validators) {
        this.setValidators(addValidators(validators, this._rawValidators));
      }
      /**
       * Add an asynchronous validator or validators to this control, without affecting other
       * validators.
       *
       * When you add or remove a validator at run time, you must call
       * `updateValueAndValidity()` for the new validation to take effect.
       *
       * Adding a validator that already exists will have no effect.
       *
       * @param validators The new asynchronous validator function or functions to add to this control.
       */
      addAsyncValidators(validators) {
        this.setAsyncValidators(addValidators(validators, this._rawAsyncValidators));
      }
      /**
       * Remove a synchronous validator from this control, without affecting other validators.
       * Validators are compared by function reference; you must pass a reference to the exact same
       * validator function as the one that was originally set. If a provided validator is not found,
       * it is ignored.
       *
       * @usageNotes
       *
       * ### Reference to a ValidatorFn
       *
       * ```
       * // Reference to the RequiredValidator
       * const ctrl = new FormControl<string | null>('', Validators.required);
       * ctrl.removeValidators(Validators.required);
       *
       * // Reference to anonymous function inside MinValidator
       * const minValidator = Validators.min(3);
       * const ctrl = new FormControl<string | null>('', minValidator);
       * expect(ctrl.hasValidator(minValidator)).toEqual(true)
       * expect(ctrl.hasValidator(Validators.min(3))).toEqual(false)
       *
       * ctrl.removeValidators(minValidator);
       * ```
       *
       * When you add or remove a validator at run time, you must call
       * `updateValueAndValidity()` for the new validation to take effect.
       *
       * @param validators The validator or validators to remove.
       */
      removeValidators(validators) {
        this.setValidators(removeValidators(validators, this._rawValidators));
      }
      /**
       * Remove an asynchronous validator from this control, without affecting other validators.
       * Validators are compared by function reference; you must pass a reference to the exact same
       * validator function as the one that was originally set. If a provided validator is not found, it
       * is ignored.
       *
       * When you add or remove a validator at run time, you must call
       * `updateValueAndValidity()` for the new validation to take effect.
       *
       * @param validators The asynchronous validator or validators to remove.
       */
      removeAsyncValidators(validators) {
        this.setAsyncValidators(removeValidators(validators, this._rawAsyncValidators));
      }
      /**
       * Check whether a synchronous validator function is present on this control. The provided
       * validator must be a reference to the exact same function that was provided.
       *
       * @usageNotes
       *
       * ### Reference to a ValidatorFn
       *
       * ```
       * // Reference to the RequiredValidator
       * const ctrl = new FormControl<number | null>(0, Validators.required);
       * expect(ctrl.hasValidator(Validators.required)).toEqual(true)
       *
       * // Reference to anonymous function inside MinValidator
       * const minValidator = Validators.min(3);
       * const ctrl = new FormControl<number | null>(0, minValidator);
       * expect(ctrl.hasValidator(minValidator)).toEqual(true)
       * expect(ctrl.hasValidator(Validators.min(3))).toEqual(false)
       * ```
       *
       * @param validator The validator to check for presence. Compared by function reference.
       * @returns Whether the provided validator was found on this control.
       */
      hasValidator(validator) {
        return hasValidator(this._rawValidators, validator);
      }
      /**
       * Check whether an asynchronous validator function is present on this control. The provided
       * validator must be a reference to the exact same function that was provided.
       *
       * @param validator The asynchronous validator to check for presence. Compared by function
       *     reference.
       * @returns Whether the provided asynchronous validator was found on this control.
       */
      hasAsyncValidator(validator) {
        return hasValidator(this._rawAsyncValidators, validator);
      }
      /**
       * Empties out the synchronous validator list.
       *
       * When you add or remove a validator at run time, you must call
       * `updateValueAndValidity()` for the new validation to take effect.
       *
       */
      clearValidators() {
        this.validator = null;
      }
      /**
       * Empties out the async validator list.
       *
       * When you add or remove a validator at run time, you must call
       * `updateValueAndValidity()` for the new validation to take effect.
       *
       */
      clearAsyncValidators() {
        this.asyncValidator = null;
      }
      markAsTouched(opts = {}) {
        const changed = this.touched === false;
        this.touched = true;
        const sourceControl = opts.sourceControl ?? this;
        if (this._parent && !opts.onlySelf) {
          this._parent.markAsTouched(__spreadProps(__spreadValues({}, opts), { sourceControl }));
        }
        if (changed && opts.emitEvent !== false) {
          this._events.next(new TouchedChangeEvent(true, sourceControl));
        }
      }
      /**
       * Marks the control and all its descendant controls as `dirty`.
       * @see {@link markAsDirty()}
       *
       * @param opts Configuration options that determine how the control propagates changes
       * and emits events after marking is applied.
       * * `emitEvent`: When true or not supplied (the default), the `events`
       * observable emits a `PristineChangeEvent` with the `pristine` property being `false`.
       * When false, no events are emitted.
       */
      markAllAsDirty(opts = {}) {
        this.markAsDirty({ onlySelf: true, emitEvent: opts.emitEvent, sourceControl: this });
        this._forEachChild((control) => control.markAllAsDirty(opts));
      }
      /**
       * Marks the control and all its descendant controls as `touched`.
       * @see {@link markAsTouched()}
       *
       * @param opts Configuration options that determine how the control propagates changes
       * and emits events after marking is applied.
       * * `emitEvent`: When true or not supplied (the default), the `events`
       * observable emits a `TouchedChangeEvent` with the `touched` property being `true`.
       * When false, no events are emitted.
       */
      markAllAsTouched(opts = {}) {
        this.markAsTouched({ onlySelf: true, emitEvent: opts.emitEvent, sourceControl: this });
        this._forEachChild((control) => control.markAllAsTouched(opts));
      }
      markAsUntouched(opts = {}) {
        const changed = this.touched === true;
        this.touched = false;
        this._pendingTouched = false;
        const sourceControl = opts.sourceControl ?? this;
        this._forEachChild((control) => {
          control.markAsUntouched({ onlySelf: true, emitEvent: opts.emitEvent, sourceControl });
        });
        if (this._parent && !opts.onlySelf) {
          this._parent._updateTouched(opts, sourceControl);
        }
        if (changed && opts.emitEvent !== false) {
          this._events.next(new TouchedChangeEvent(false, sourceControl));
        }
      }
      markAsDirty(opts = {}) {
        const changed = this.pristine === true;
        this.pristine = false;
        const sourceControl = opts.sourceControl ?? this;
        if (this._parent && !opts.onlySelf) {
          this._parent.markAsDirty(__spreadProps(__spreadValues({}, opts), { sourceControl }));
        }
        if (changed && opts.emitEvent !== false) {
          this._events.next(new PristineChangeEvent(false, sourceControl));
        }
      }
      markAsPristine(opts = {}) {
        const changed = this.pristine === false;
        this.pristine = true;
        this._pendingDirty = false;
        const sourceControl = opts.sourceControl ?? this;
        this._forEachChild((control) => {
          control.markAsPristine({ onlySelf: true, emitEvent: opts.emitEvent });
        });
        if (this._parent && !opts.onlySelf) {
          this._parent._updatePristine(opts, sourceControl);
        }
        if (changed && opts.emitEvent !== false) {
          this._events.next(new PristineChangeEvent(true, sourceControl));
        }
      }
      markAsPending(opts = {}) {
        this.status = PENDING;
        const sourceControl = opts.sourceControl ?? this;
        if (opts.emitEvent !== false) {
          this._events.next(new StatusChangeEvent(this.status, sourceControl));
          this.statusChanges.emit(this.status);
        }
        if (this._parent && !opts.onlySelf) {
          this._parent.markAsPending(__spreadProps(__spreadValues({}, opts), { sourceControl }));
        }
      }
      disable(opts = {}) {
        const skipPristineCheck = this._parentMarkedDirty(opts.onlySelf);
        this.status = DISABLED;
        this.errors = null;
        this._forEachChild((control) => {
          control.disable(__spreadProps(__spreadValues({}, opts), { onlySelf: true }));
        });
        this._updateValue();
        const sourceControl = opts.sourceControl ?? this;
        if (opts.emitEvent !== false) {
          this._events.next(new ValueChangeEvent(this.value, sourceControl));
          this._events.next(new StatusChangeEvent(this.status, sourceControl));
          this.valueChanges.emit(this.value);
          this.statusChanges.emit(this.status);
        }
        this._updateAncestors(__spreadProps(__spreadValues({}, opts), { skipPristineCheck }), this);
        this._onDisabledChange.forEach((changeFn) => changeFn(true));
      }
      /**
       * Enables the control. This means the control is included in validation checks and
       * the aggregate value of its parent. Its status recalculates based on its value and
       * its validators.
       *
       * By default, if the control has children, all children are enabled.
       *
       * @see {@link AbstractControl.status}
       *
       * @param opts Configure options that control how the control propagates changes and
       * emits events when marked as untouched
       * * `onlySelf`: When true, mark only this control. When false or not supplied,
       * marks all direct ancestors. Default is false.
       * * `emitEvent`: When true or not supplied (the default), the `statusChanges`,
       * `valueChanges` and `events`
       * observables emit events with the latest status and value when the control is enabled.
       * When false, no events are emitted.
       */
      enable(opts = {}) {
        const skipPristineCheck = this._parentMarkedDirty(opts.onlySelf);
        this.status = VALID;
        this._forEachChild((control) => {
          control.enable(__spreadProps(__spreadValues({}, opts), { onlySelf: true }));
        });
        this.updateValueAndValidity({ onlySelf: true, emitEvent: opts.emitEvent });
        this._updateAncestors(__spreadProps(__spreadValues({}, opts), { skipPristineCheck }), this);
        this._onDisabledChange.forEach((changeFn) => changeFn(false));
      }
      _updateAncestors(opts, sourceControl) {
        if (this._parent && !opts.onlySelf) {
          this._parent.updateValueAndValidity(opts);
          if (!opts.skipPristineCheck) {
            this._parent._updatePristine({}, sourceControl);
          }
          this._parent._updateTouched({}, sourceControl);
        }
      }
      /**
       * Sets the parent of the control
       *
       * @param parent The new parent.
       */
      setParent(parent) {
        this._parent = parent;
      }
      /**
       * The raw value of this control. For most control implementations, the raw value will include
       * disabled children.
       */
      getRawValue() {
        return this.value;
      }
      updateValueAndValidity(opts = {}) {
        this._setInitialStatus();
        this._updateValue();
        if (this.enabled) {
          const shouldHaveEmitted = this._cancelExistingSubscription();
          this.errors = this._runValidator();
          this.status = this._calculateStatus();
          if (this.status === VALID || this.status === PENDING) {
            this._runAsyncValidator(shouldHaveEmitted, opts.emitEvent);
          }
        }
        const sourceControl = opts.sourceControl ?? this;
        if (opts.emitEvent !== false) {
          this._events.next(new ValueChangeEvent(this.value, sourceControl));
          this._events.next(new StatusChangeEvent(this.status, sourceControl));
          this.valueChanges.emit(this.value);
          this.statusChanges.emit(this.status);
        }
        if (this._parent && !opts.onlySelf) {
          this._parent.updateValueAndValidity(__spreadProps(__spreadValues({}, opts), { sourceControl }));
        }
      }
      /** @internal */
      _updateTreeValidity(opts = { emitEvent: true }) {
        this._forEachChild((ctrl) => ctrl._updateTreeValidity(opts));
        this.updateValueAndValidity({ onlySelf: true, emitEvent: opts.emitEvent });
      }
      _setInitialStatus() {
        this.status = this._allControlsDisabled() ? DISABLED : VALID;
      }
      _runValidator() {
        return this.validator ? this.validator(this) : null;
      }
      _runAsyncValidator(shouldHaveEmitted, emitEvent) {
        if (this.asyncValidator) {
          this.status = PENDING;
          this._hasOwnPendingAsyncValidator = {
            emitEvent: emitEvent !== false,
            shouldHaveEmitted: shouldHaveEmitted !== false
          };
          const obs = toObservable(this.asyncValidator(this));
          this._asyncValidationSubscription = obs.subscribe((errors) => {
            this._hasOwnPendingAsyncValidator = null;
            this.setErrors(errors, { emitEvent, shouldHaveEmitted });
          });
        }
      }
      _cancelExistingSubscription() {
        if (this._asyncValidationSubscription) {
          this._asyncValidationSubscription.unsubscribe();
          const shouldHaveEmitted = (this._hasOwnPendingAsyncValidator?.emitEvent || this._hasOwnPendingAsyncValidator?.shouldHaveEmitted) ?? false;
          this._hasOwnPendingAsyncValidator = null;
          return shouldHaveEmitted;
        }
        return false;
      }
      setErrors(errors, opts = {}) {
        this.errors = errors;
        this._updateControlsErrors(opts.emitEvent !== false, this, opts.shouldHaveEmitted);
      }
      /**
       * Retrieves a child control given the control's name or path.
       *
       * @param path A dot-delimited string or array of string/number values that define the path to the
       * control. If a string is provided, passing it as a string literal will result in improved type
       * information. Likewise, if an array is provided, passing it `as const` will cause improved type
       * information to be available.
       *
       * @usageNotes
       * ### Retrieve a nested control
       *
       * For example, to get a `name` control nested within a `person` sub-group:
       *
       * * `this.form.get('person.name');`
       *
       * -OR-
       *
       * * `this.form.get(['person', 'name'] as const);` // `as const` gives improved typings
       *
       * ### Retrieve a control in a FormArray
       *
       * When accessing an element inside a FormArray, you can use an element index.
       * For example, to get a `price` control from the first element in an `items` array you can use:
       *
       * * `this.form.get('items.0.price');`
       *
       * -OR-
       *
       * * `this.form.get(['items', 0, 'price']);`
       */
      get(path) {
        let currPath = path;
        if (currPath == null)
          return null;
        if (!Array.isArray(currPath))
          currPath = currPath.split(".");
        if (currPath.length === 0)
          return null;
        return currPath.reduce((control, name) => control && control._find(name), this);
      }
      /**
       * @description
       * Reports error data for the control with the given path.
       *
       * @param errorCode The code of the error to check
       * @param path A list of control names that designates how to move from the current control
       * to the control that should be queried for errors.
       *
       * @usageNotes
       * For example, for the following `FormGroup`:
       *
       * ```ts
       * form = new FormGroup({
       *   address: new FormGroup({ street: new FormControl() })
       * });
       * ```
       *
       * The path to the 'street' control from the root form would be 'address' -> 'street'.
       *
       * It can be provided to this method in one of two formats:
       *
       * 1. An array of string control names, e.g. `['address', 'street']`
       * 1. A period-delimited list of control names in one string, e.g. `'address.street'`
       *
       * @returns error data for that particular error. If the control or error is not present,
       * null is returned.
       */
      getError(errorCode, path) {
        const control = path ? this.get(path) : this;
        return control && control.errors ? control.errors[errorCode] : null;
      }
      /**
       * @description
       * Reports whether the control with the given path has the error specified.
       *
       * @param errorCode The code of the error to check
       * @param path A list of control names that designates how to move from the current control
       * to the control that should be queried for errors.
       *
       * @usageNotes
       * For example, for the following `FormGroup`:
       *
       * ```ts
       * form = new FormGroup({
       *   address: new FormGroup({ street: new FormControl() })
       * });
       * ```
       *
       * The path to the 'street' control from the root form would be 'address' -> 'street'.
       *
       * It can be provided to this method in one of two formats:
       *
       * 1. An array of string control names, e.g. `['address', 'street']`
       * 1. A period-delimited list of control names in one string, e.g. `'address.street'`
       *
       * If no path is given, this method checks for the error on the current control.
       *
       * @returns whether the given error is present in the control at the given path.
       *
       * If the control is not present, false is returned.
       */
      hasError(errorCode, path) {
        return !!this.getError(errorCode, path);
      }
      /**
       * Retrieves the top-level ancestor of this control.
       */
      get root() {
        let x = this;
        while (x._parent) {
          x = x._parent;
        }
        return x;
      }
      /** @internal */
      _updateControlsErrors(emitEvent, changedControl, shouldHaveEmitted) {
        this.status = this._calculateStatus();
        if (emitEvent) {
          this.statusChanges.emit(this.status);
        }
        if (emitEvent || shouldHaveEmitted) {
          this._events.next(new StatusChangeEvent(this.status, changedControl));
        }
        if (this._parent) {
          this._parent._updateControlsErrors(emitEvent, changedControl, shouldHaveEmitted);
        }
      }
      /** @internal */
      _initObservables() {
        this.valueChanges = new EventEmitter();
        this.statusChanges = new EventEmitter();
      }
      _calculateStatus() {
        if (this._allControlsDisabled())
          return DISABLED;
        if (this.errors)
          return INVALID;
        if (this._hasOwnPendingAsyncValidator || this._anyControlsHaveStatus(PENDING))
          return PENDING;
        if (this._anyControlsHaveStatus(INVALID))
          return INVALID;
        return VALID;
      }
      /** @internal */
      _anyControlsHaveStatus(status) {
        return this._anyControls((control) => control.status === status);
      }
      /** @internal */
      _anyControlsDirty() {
        return this._anyControls((control) => control.dirty);
      }
      /** @internal */
      _anyControlsTouched() {
        return this._anyControls((control) => control.touched);
      }
      /** @internal */
      _updatePristine(opts, changedControl) {
        const newPristine = !this._anyControlsDirty();
        const changed = this.pristine !== newPristine;
        this.pristine = newPristine;
        if (this._parent && !opts.onlySelf) {
          this._parent._updatePristine(opts, changedControl);
        }
        if (changed) {
          this._events.next(new PristineChangeEvent(this.pristine, changedControl));
        }
      }
      /** @internal */
      _updateTouched(opts = {}, changedControl) {
        this.touched = this._anyControlsTouched();
        this._events.next(new TouchedChangeEvent(this.touched, changedControl));
        if (this._parent && !opts.onlySelf) {
          this._parent._updateTouched(opts, changedControl);
        }
      }
      /** @internal */
      _onDisabledChange = [];
      /** @internal */
      _registerOnCollectionChange(fn) {
        this._onCollectionChange = fn;
      }
      /** @internal */
      _setUpdateStrategy(opts) {
        if (isOptionsObj(opts) && opts.updateOn != null) {
          this._updateOn = opts.updateOn;
        }
      }
      /**
       * Check to see if parent has been marked artificially dirty.
       *
       * @internal
       */
      _parentMarkedDirty(onlySelf) {
        const parentDirty = this._parent && this._parent.dirty;
        return !onlySelf && !!parentDirty && !this._parent._anyControlsDirty();
      }
      /** @internal */
      _find(name) {
        return null;
      }
      /**
       * Internal implementation of the `setValidators` method. Needs to be separated out into a
       * different method, because it is called in the constructor and it can break cases where
       * a control is extended.
       */
      _assignValidators(validators) {
        this._rawValidators = Array.isArray(validators) ? validators.slice() : validators;
        this._composedValidatorFn = coerceToValidator(this._rawValidators);
      }
      /**
       * Internal implementation of the `setAsyncValidators` method. Needs to be separated out into a
       * different method, because it is called in the constructor and it can break cases where
       * a control is extended.
       */
      _assignAsyncValidators(validators) {
        this._rawAsyncValidators = Array.isArray(validators) ? validators.slice() : validators;
        this._composedAsyncValidatorFn = coerceToAsyncValidator(this._rawAsyncValidators);
      }
    };
    FormGroup = class extends AbstractControl {
      /**
       * Creates a new `FormGroup` instance.
       *
       * @param controls A collection of child controls. The key for each child is the name
       * under which it is registered.
       *
       * @param validatorOrOpts A synchronous validator function, or an array of
       * such functions, or an `AbstractControlOptions` object that contains validation functions
       * and a validation trigger.
       *
       * @param asyncValidator A single async validator or array of async validator functions
       *
       */
      constructor(controls, validatorOrOpts, asyncValidator) {
        super(pickValidators(validatorOrOpts), pickAsyncValidators(asyncValidator, validatorOrOpts));
        (typeof ngDevMode === "undefined" || ngDevMode) && validateFormGroupControls(controls);
        this.controls = controls;
        this._initObservables();
        this._setUpdateStrategy(validatorOrOpts);
        this._setUpControls();
        this.updateValueAndValidity({
          onlySelf: true,
          // If `asyncValidator` is present, it will trigger control status change from `PENDING` to
          // `VALID` or `INVALID`. The status should be broadcasted via the `statusChanges` observable,
          // so we set `emitEvent` to `true` to allow that during the control creation process.
          emitEvent: !!this.asyncValidator
        });
      }
      controls;
      registerControl(name, control) {
        if (this.controls[name])
          return this.controls[name];
        this.controls[name] = control;
        control.setParent(this);
        control._registerOnCollectionChange(this._onCollectionChange);
        return control;
      }
      addControl(name, control, options = {}) {
        this.registerControl(name, control);
        this.updateValueAndValidity({ emitEvent: options.emitEvent });
        this._onCollectionChange();
      }
      /**
       * Remove a control from this group. In a strongly-typed group, required controls cannot be
       * removed.
       *
       * This method also updates the value and validity of the control.
       *
       * @param name The control name to remove from the collection
       * @param options Specifies whether this FormGroup instance should emit events after a
       *     control is removed.
       * * `emitEvent`: When true or not supplied (the default), both the `statusChanges` and
       * `valueChanges` observables emit events with the latest status and value when the control is
       * removed. When false, no events are emitted.
       */
      removeControl(name, options = {}) {
        if (this.controls[name])
          this.controls[name]._registerOnCollectionChange(() => {
          });
        delete this.controls[name];
        this.updateValueAndValidity({ emitEvent: options.emitEvent });
        this._onCollectionChange();
      }
      setControl(name, control, options = {}) {
        if (this.controls[name])
          this.controls[name]._registerOnCollectionChange(() => {
          });
        delete this.controls[name];
        if (control)
          this.registerControl(name, control);
        this.updateValueAndValidity({ emitEvent: options.emitEvent });
        this._onCollectionChange();
      }
      contains(controlName) {
        return this.controls.hasOwnProperty(controlName) && this.controls[controlName].enabled;
      }
      /**
       * Sets the value of the `FormGroup`. It accepts an object that matches
       * the structure of the group, with control names as keys.
       *
       * @usageNotes
       * ### Set the complete value for the form group
       *
       * ```ts
       * const form = new FormGroup({
       *   first: new FormControl(),
       *   last: new FormControl()
       * });
       *
       * console.log(form.value);   // {first: null, last: null}
       *
       * form.setValue({first: 'Nancy', last: 'Drew'});
       * console.log(form.value);   // {first: 'Nancy', last: 'Drew'}
       * ```
       *
       * @throws When strict checks fail, such as setting the value of a control
       * that doesn't exist or if you exclude a value of a control that does exist.
       *
       * @param value The new value for the control that matches the structure of the group.
       * @param options Configuration options that determine how the control propagates changes
       * and emits events after the value changes.
       * The configuration options are passed to the {@link AbstractControl#updateValueAndValidity
       * updateValueAndValidity} method.
       *
       * * `onlySelf`: When true, each change only affects this control, and not its parent. Default is
       * false.
       * * `emitEvent`: When true or not supplied (the default), both the `statusChanges` and
       * `valueChanges`
       * observables emit events with the latest status and value when the control value is updated.
       * When false, no events are emitted.
       */
      setValue(value, options = {}) {
        assertAllValuesPresent(this, true, value);
        Object.keys(value).forEach((name) => {
          assertControlPresent(this, true, name);
          this.controls[name].setValue(value[name], {
            onlySelf: true,
            emitEvent: options.emitEvent
          });
        });
        this.updateValueAndValidity(options);
      }
      /**
       * Patches the value of the `FormGroup`. It accepts an object with control
       * names as keys, and does its best to match the values to the correct controls
       * in the group.
       *
       * It accepts both super-sets and sub-sets of the group without throwing an error.
       *
       * @usageNotes
       * ### Patch the value for a form group
       *
       * ```ts
       * const form = new FormGroup({
       *    first: new FormControl(),
       *    last: new FormControl()
       * });
       * console.log(form.value);   // {first: null, last: null}
       *
       * form.patchValue({first: 'Nancy'});
       * console.log(form.value);   // {first: 'Nancy', last: null}
       * ```
       *
       * @param value The object that matches the structure of the group.
       * @param options Configuration options that determine how the control propagates changes and
       * emits events after the value is patched.
       * * `onlySelf`: When true, each change only affects this control and not its parent. Default is
       * true.
       * * `emitEvent`: When true or not supplied (the default), both the `statusChanges` and
       * `valueChanges` observables emit events with the latest status and value when the control value
       * is updated. When false, no events are emitted. The configuration options are passed to
       * the {@link AbstractControl#updateValueAndValidity updateValueAndValidity} method.
       */
      patchValue(value, options = {}) {
        if (value == null)
          return;
        Object.keys(value).forEach((name) => {
          const control = this.controls[name];
          if (control) {
            control.patchValue(
              /* Guaranteed to be present, due to the outer forEach. */
              value[name],
              { onlySelf: true, emitEvent: options.emitEvent }
            );
          }
        });
        this.updateValueAndValidity(options);
      }
      /**
       * Resets the `FormGroup`, marks all descendants `pristine` and `untouched` and sets
       * the value of all descendants to their default values, or null if no defaults were provided.
       *
       * You reset to a specific form state by passing in a map of states
       * that matches the structure of your form, with control names as keys. The state
       * is a standalone value or a form state object with both a value and a disabled
       * status.
       *
       * @param value Resets the control with an initial value,
       * or an object that defines the initial value and disabled state.
       *
       * @param options Configuration options that determine how the control propagates changes
       * and emits events when the group is reset.
       * * `onlySelf`: When true, each change only affects this control, and not its parent. Default is
       * false.
       * * `emitEvent`: When true or not supplied (the default), both the `statusChanges` and
       * `valueChanges`
       * observables emit events with the latest status and value when the control is reset.
       * When false, no events are emitted.
       * The configuration options are passed to the {@link AbstractControl#updateValueAndValidity
       * updateValueAndValidity} method.
       *
       * @usageNotes
       *
       * ### Reset the form group values
       *
       * ```ts
       * const form = new FormGroup({
       *   first: new FormControl('first name'),
       *   last: new FormControl('last name')
       * });
       *
       * console.log(form.value);  // {first: 'first name', last: 'last name'}
       *
       * form.reset({ first: 'name', last: 'last name' });
       *
       * console.log(form.value);  // {first: 'name', last: 'last name'}
       * ```
       *
       * ### Reset the form group values and disabled status
       *
       * ```ts
       * const form = new FormGroup({
       *   first: new FormControl('first name'),
       *   last: new FormControl('last name')
       * });
       *
       * form.reset({
       *   first: {value: 'name', disabled: true},
       *   last: 'last'
       * });
       *
       * console.log(form.value);  // {last: 'last'}
       * console.log(form.get('first').status);  // 'DISABLED'
       * ```
       */
      reset(value = {}, options = {}) {
        this._forEachChild((control, name) => {
          control.reset(value ? value[name] : null, {
            onlySelf: true,
            emitEvent: options.emitEvent
          });
        });
        this._updatePristine(options, this);
        this._updateTouched(options, this);
        this.updateValueAndValidity(options);
        if (options?.emitEvent !== false) {
          this._events.next(new FormResetEvent(this));
        }
      }
      /**
       * The aggregate value of the `FormGroup`, including any disabled controls.
       *
       * Retrieves all values regardless of disabled status.
       */
      getRawValue() {
        return this._reduceChildren({}, (acc, control, name) => {
          acc[name] = control.getRawValue();
          return acc;
        });
      }
      /** @internal */
      _syncPendingControls() {
        let subtreeUpdated = this._reduceChildren(false, (updated, child) => {
          return child._syncPendingControls() ? true : updated;
        });
        if (subtreeUpdated)
          this.updateValueAndValidity({ onlySelf: true });
        return subtreeUpdated;
      }
      /** @internal */
      _forEachChild(cb) {
        Object.keys(this.controls).forEach((key) => {
          const control = this.controls[key];
          control && cb(control, key);
        });
      }
      /** @internal */
      _setUpControls() {
        this._forEachChild((control) => {
          control.setParent(this);
          control._registerOnCollectionChange(this._onCollectionChange);
        });
      }
      /** @internal */
      _updateValue() {
        this.value = this._reduceValue();
      }
      /** @internal */
      _anyControls(condition) {
        for (const [controlName, control] of Object.entries(this.controls)) {
          if (this.contains(controlName) && condition(control)) {
            return true;
          }
        }
        return false;
      }
      /** @internal */
      _reduceValue() {
        let acc = {};
        return this._reduceChildren(acc, (acc2, control, name) => {
          if (control.enabled || this.disabled) {
            acc2[name] = control.value;
          }
          return acc2;
        });
      }
      /** @internal */
      _reduceChildren(initValue, fn) {
        let res = initValue;
        this._forEachChild((control, name) => {
          res = fn(res, control, name);
        });
        return res;
      }
      /** @internal */
      _allControlsDisabled() {
        for (const controlName of Object.keys(this.controls)) {
          if (this.controls[controlName].enabled) {
            return false;
          }
        }
        return Object.keys(this.controls).length > 0 || this.disabled;
      }
      /** @internal */
      _find(name) {
        return this.controls.hasOwnProperty(name) ? this.controls[name] : null;
      }
    };
    FormRecord = class extends FormGroup {
    };
    CALL_SET_DISABLED_STATE = new InjectionToken(typeof ngDevMode === "undefined" || ngDevMode ? "CallSetDisabledState" : "", {
      providedIn: "root",
      factory: () => setDisabledStateDefault
    });
    setDisabledStateDefault = "always";
    formDirectiveProvider$1 = {
      provide: ControlContainer,
      useExisting: forwardRef(() => NgForm)
    };
    resolvedPromise$1 = (() => Promise.resolve())();
    NgForm = class _NgForm extends ControlContainer {
      callSetDisabledState;
      /**
       * @description
       * Returns whether the form submission has been triggered.
       */
      get submitted() {
        return untracked(this.submittedReactive);
      }
      /** @internal */
      _submitted = computed(() => this.submittedReactive(), ...ngDevMode ? [{ debugName: "_submitted" }] : []);
      submittedReactive = signal(false, ...ngDevMode ? [{ debugName: "submittedReactive" }] : []);
      _directives = /* @__PURE__ */ new Set();
      /**
       * @description
       * The `FormGroup` instance created for this form.
       */
      form;
      /**
       * @description
       * Event emitter for the "ngSubmit" event
       */
      ngSubmit = new EventEmitter();
      /**
       * @description
       * Tracks options for the `NgForm` instance.
       *
       * **updateOn**: Sets the default `updateOn` value for all child `NgModels` below it
       * unless explicitly set by a child `NgModel` using `ngModelOptions`). Defaults to 'change'.
       * Possible values: `'change'` | `'blur'` | `'submit'`.
       *
       */
      options;
      constructor(validators, asyncValidators, callSetDisabledState) {
        super();
        this.callSetDisabledState = callSetDisabledState;
        this.form = new FormGroup({}, composeValidators(validators), composeAsyncValidators(asyncValidators));
      }
      /** @docs-private */
      ngAfterViewInit() {
        this._setUpdateStrategy();
      }
      /**
       * @description
       * The directive instance.
       */
      get formDirective() {
        return this;
      }
      /**
       * @description
       * The internal `FormGroup` instance.
       */
      get control() {
        return this.form;
      }
      /**
       * @description
       * Returns an array representing the path to this group. Because this directive
       * always lives at the top level of a form, it is always an empty array.
       */
      get path() {
        return [];
      }
      /**
       * @description
       * Returns a map of the controls in this group.
       */
      get controls() {
        return this.form.controls;
      }
      /**
       * @description
       * Method that sets up the control directive in this group, re-calculates its value
       * and validity, and adds the instance to the internal list of directives.
       *
       * @param dir The `NgModel` directive instance.
       */
      addControl(dir) {
        resolvedPromise$1.then(() => {
          const container = this._findContainer(dir.path);
          dir.control = container.registerControl(dir.name, dir.control);
          setUpControl(dir.control, dir, this.callSetDisabledState);
          dir.control.updateValueAndValidity({ emitEvent: false });
          this._directives.add(dir);
        });
      }
      /**
       * @description
       * Retrieves the `FormControl` instance from the provided `NgModel` directive.
       *
       * @param dir The `NgModel` directive instance.
       */
      getControl(dir) {
        return this.form.get(dir.path);
      }
      /**
       * @description
       * Removes the `NgModel` instance from the internal list of directives
       *
       * @param dir The `NgModel` directive instance.
       */
      removeControl(dir) {
        resolvedPromise$1.then(() => {
          const container = this._findContainer(dir.path);
          if (container) {
            container.removeControl(dir.name);
          }
          this._directives.delete(dir);
        });
      }
      /**
       * @description
       * Adds a new `NgModelGroup` directive instance to the form.
       *
       * @param dir The `NgModelGroup` directive instance.
       */
      addFormGroup(dir) {
        resolvedPromise$1.then(() => {
          const container = this._findContainer(dir.path);
          const group = new FormGroup({});
          setUpFormContainer(group, dir);
          container.registerControl(dir.name, group);
          group.updateValueAndValidity({ emitEvent: false });
        });
      }
      /**
       * @description
       * Removes the `NgModelGroup` directive instance from the form.
       *
       * @param dir The `NgModelGroup` directive instance.
       */
      removeFormGroup(dir) {
        resolvedPromise$1.then(() => {
          const container = this._findContainer(dir.path);
          if (container) {
            container.removeControl(dir.name);
          }
        });
      }
      /**
       * @description
       * Retrieves the `FormGroup` for a provided `NgModelGroup` directive instance
       *
       * @param dir The `NgModelGroup` directive instance.
       */
      getFormGroup(dir) {
        return this.form.get(dir.path);
      }
      /**
       * Sets the new value for the provided `NgControl` directive.
       *
       * @param dir The `NgControl` directive instance.
       * @param value The new value for the directive's control.
       */
      updateModel(dir, value) {
        resolvedPromise$1.then(() => {
          const ctrl = this.form.get(dir.path);
          ctrl.setValue(value);
        });
      }
      /**
       * @description
       * Sets the value for this `FormGroup`.
       *
       * @param value The new value
       */
      setValue(value) {
        this.control.setValue(value);
      }
      /**
       * @description
       * Method called when the "submit" event is triggered on the form.
       * Triggers the `ngSubmit` emitter to emit the "submit" event as its payload.
       *
       * @param $event The "submit" event object
       */
      onSubmit($event) {
        this.submittedReactive.set(true);
        syncPendingControls(this.form, this._directives);
        this.ngSubmit.emit($event);
        this.form._events.next(new FormSubmittedEvent(this.control));
        return $event?.target?.method === "dialog";
      }
      /**
       * @description
       * Method called when the "reset" event is triggered on the form.
       */
      onReset() {
        this.resetForm();
      }
      /**
       * @description
       * Resets the form to an initial value and resets its submitted status.
       *
       * @param value The new value for the form.
       */
      resetForm(value = void 0) {
        this.form.reset(value);
        this.submittedReactive.set(false);
      }
      _setUpdateStrategy() {
        if (this.options && this.options.updateOn != null) {
          this.form._updateOn = this.options.updateOn;
        }
      }
      _findContainer(path) {
        path.pop();
        return path.length ? this.form.get(path) : this.form;
      }
      static \u0275fac = \u0275\u0275ngDeclareFactory({ minVersion: "12.0.0", version: "20.3.29", ngImport: core_exports, type: _NgForm, deps: [{ token: NG_VALIDATORS, optional: true, self: true }, { token: NG_ASYNC_VALIDATORS, optional: true, self: true }, { token: CALL_SET_DISABLED_STATE, optional: true }], target: FactoryTarget.Directive });
      static \u0275dir = \u0275\u0275ngDeclareDirective({ minVersion: "14.0.0", version: "20.3.29", type: _NgForm, isStandalone: false, selector: "form:not([ngNoForm]):not([formGroup]),ng-form,[ngForm]", inputs: { options: ["ngFormOptions", "options"] }, outputs: { ngSubmit: "ngSubmit" }, host: { listeners: { "submit": "onSubmit($event)", "reset": "onReset()" } }, providers: [formDirectiveProvider$1], exportAs: ["ngForm"], usesInheritance: true, ngImport: core_exports });
    };
    \u0275\u0275ngDeclareClassMetadata({ minVersion: "12.0.0", version: "20.3.29", ngImport: core_exports, type: NgForm, decorators: [{
      type: Directive,
      args: [{
        selector: "form:not([ngNoForm]):not([formGroup]),ng-form,[ngForm]",
        providers: [formDirectiveProvider$1],
        host: { "(submit)": "onSubmit($event)", "(reset)": "onReset()" },
        outputs: ["ngSubmit"],
        exportAs: "ngForm",
        standalone: false
      }]
    }], ctorParameters: () => [{ type: void 0, decorators: [{
      type: Optional
    }, {
      type: Self
    }, {
      type: Inject,
      args: [NG_VALIDATORS]
    }] }, { type: void 0, decorators: [{
      type: Optional
    }, {
      type: Self
    }, {
      type: Inject,
      args: [NG_ASYNC_VALIDATORS]
    }] }, { type: void 0, decorators: [{
      type: Optional
    }, {
      type: Inject,
      args: [CALL_SET_DISABLED_STATE]
    }] }], propDecorators: { options: [{
      type: Input,
      args: ["ngFormOptions"]
    }] } });
    FormControl = class FormControl2 extends AbstractControl {
      /** @publicApi */
      defaultValue = null;
      /** @internal */
      _onChange = [];
      /** @internal */
      _pendingValue;
      /** @internal */
      _pendingChange = false;
      constructor(formState = null, validatorOrOpts, asyncValidator) {
        super(pickValidators(validatorOrOpts), pickAsyncValidators(asyncValidator, validatorOrOpts));
        this._applyFormState(formState);
        this._setUpdateStrategy(validatorOrOpts);
        this._initObservables();
        this.updateValueAndValidity({
          onlySelf: true,
          // If `asyncValidator` is present, it will trigger control status change from `PENDING` to
          // `VALID` or `INVALID`.
          // The status should be broadcasted via the `statusChanges` observable, so we set
          // `emitEvent` to `true` to allow that during the control creation process.
          emitEvent: !!this.asyncValidator
        });
        if (isOptionsObj(validatorOrOpts) && (validatorOrOpts.nonNullable || validatorOrOpts.initialValueIsDefault)) {
          if (isFormControlState(formState)) {
            this.defaultValue = formState.value;
          } else {
            this.defaultValue = formState;
          }
        }
      }
      setValue(value, options = {}) {
        this.value = this._pendingValue = value;
        if (this._onChange.length && options.emitModelToViewChange !== false) {
          this._onChange.forEach((changeFn) => changeFn(this.value, options.emitViewToModelChange !== false));
        }
        this.updateValueAndValidity(options);
      }
      patchValue(value, options = {}) {
        this.setValue(value, options);
      }
      reset(formState = this.defaultValue, options = {}) {
        this._applyFormState(formState);
        this.markAsPristine(options);
        this.markAsUntouched(options);
        this.setValue(this.value, options);
        this._pendingChange = false;
        if (options?.emitEvent !== false) {
          this._events.next(new FormResetEvent(this));
        }
      }
      /**  @internal */
      _updateValue() {
      }
      /**  @internal */
      _anyControls(condition) {
        return false;
      }
      /**  @internal */
      _allControlsDisabled() {
        return this.disabled;
      }
      registerOnChange(fn) {
        this._onChange.push(fn);
      }
      /** @internal */
      _unregisterOnChange(fn) {
        removeListItem(this._onChange, fn);
      }
      registerOnDisabledChange(fn) {
        this._onDisabledChange.push(fn);
      }
      /** @internal */
      _unregisterOnDisabledChange(fn) {
        removeListItem(this._onDisabledChange, fn);
      }
      /** @internal */
      _forEachChild(cb) {
      }
      /** @internal */
      _syncPendingControls() {
        if (this.updateOn === "submit") {
          if (this._pendingDirty)
            this.markAsDirty();
          if (this._pendingTouched)
            this.markAsTouched();
          if (this._pendingChange) {
            this.setValue(this._pendingValue, { onlySelf: true, emitModelToViewChange: false });
            return true;
          }
        }
        return false;
      }
      _applyFormState(formState) {
        if (isFormControlState(formState)) {
          this.value = this._pendingValue = formState.value;
          formState.disabled ? this.disable({ onlySelf: true, emitEvent: false }) : this.enable({ onlySelf: true, emitEvent: false });
        } else {
          this.value = this._pendingValue = formState;
        }
      }
    };
    isFormControl = (control) => control instanceof FormControl;
    AbstractFormGroupDirective = class _AbstractFormGroupDirective extends ControlContainer {
      /**
       * @description
       * The parent control for the group
       *
       * @internal
       */
      _parent;
      /** @docs-private */
      ngOnInit() {
        this._checkParentType();
        this.formDirective.addFormGroup(this);
      }
      /** @docs-private */
      ngOnDestroy() {
        if (this.formDirective) {
          this.formDirective.removeFormGroup(this);
        }
      }
      /**
       * @description
       * The `FormGroup` bound to this directive.
       */
      get control() {
        return this.formDirective.getFormGroup(this);
      }
      /**
       * @description
       * The path to this group from the top-level directive.
       */
      get path() {
        return controlPath(this.name == null ? this.name : this.name.toString(), this._parent);
      }
      /**
       * @description
       * The top-level directive for this group if present, otherwise null.
       */
      get formDirective() {
        return this._parent ? this._parent.formDirective : null;
      }
      /** @internal */
      _checkParentType() {
      }
      static \u0275fac = \u0275\u0275ngDeclareFactory({ minVersion: "12.0.0", version: "20.3.29", ngImport: core_exports, type: _AbstractFormGroupDirective, deps: null, target: FactoryTarget.Directive });
      static \u0275dir = \u0275\u0275ngDeclareDirective({ minVersion: "14.0.0", version: "20.3.29", type: _AbstractFormGroupDirective, isStandalone: false, usesInheritance: true, ngImport: core_exports });
    };
    \u0275\u0275ngDeclareClassMetadata({ minVersion: "12.0.0", version: "20.3.29", ngImport: core_exports, type: AbstractFormGroupDirective, decorators: [{
      type: Directive,
      args: [{
        standalone: false
      }]
    }] });
    modelGroupProvider = {
      provide: ControlContainer,
      useExisting: forwardRef(() => NgModelGroup)
    };
    NgModelGroup = class _NgModelGroup extends AbstractFormGroupDirective {
      /**
       * @description
       * Tracks the name of the `NgModelGroup` bound to the directive. The name corresponds
       * to a key in the parent `NgForm`.
       */
      name = "";
      constructor(parent, validators, asyncValidators) {
        super();
        this._parent = parent;
        this._setValidators(validators);
        this._setAsyncValidators(asyncValidators);
      }
      /** @internal */
      _checkParentType() {
        if (!(this._parent instanceof _NgModelGroup) && !(this._parent instanceof NgForm) && (typeof ngDevMode === "undefined" || ngDevMode)) {
          throw modelGroupParentException();
        }
      }
      static \u0275fac = \u0275\u0275ngDeclareFactory({ minVersion: "12.0.0", version: "20.3.29", ngImport: core_exports, type: _NgModelGroup, deps: [{ token: ControlContainer, host: true, skipSelf: true }, { token: NG_VALIDATORS, optional: true, self: true }, { token: NG_ASYNC_VALIDATORS, optional: true, self: true }], target: FactoryTarget.Directive });
      static \u0275dir = \u0275\u0275ngDeclareDirective({ minVersion: "14.0.0", version: "20.3.29", type: _NgModelGroup, isStandalone: false, selector: "[ngModelGroup]", inputs: { name: ["ngModelGroup", "name"] }, providers: [modelGroupProvider], exportAs: ["ngModelGroup"], usesInheritance: true, ngImport: core_exports });
    };
    \u0275\u0275ngDeclareClassMetadata({ minVersion: "12.0.0", version: "20.3.29", ngImport: core_exports, type: NgModelGroup, decorators: [{
      type: Directive,
      args: [{
        selector: "[ngModelGroup]",
        providers: [modelGroupProvider],
        exportAs: "ngModelGroup",
        standalone: false
      }]
    }], ctorParameters: () => [{ type: ControlContainer, decorators: [{
      type: Host
    }, {
      type: SkipSelf
    }] }, { type: void 0, decorators: [{
      type: Optional
    }, {
      type: Self
    }, {
      type: Inject,
      args: [NG_VALIDATORS]
    }] }, { type: void 0, decorators: [{
      type: Optional
    }, {
      type: Self
    }, {
      type: Inject,
      args: [NG_ASYNC_VALIDATORS]
    }] }], propDecorators: { name: [{
      type: Input,
      args: ["ngModelGroup"]
    }] } });
    formControlBinding$1 = {
      provide: NgControl,
      useExisting: forwardRef(() => NgModel)
    };
    resolvedPromise = (() => Promise.resolve())();
    NgModel = class _NgModel extends NgControl {
      _changeDetectorRef;
      callSetDisabledState;
      control = new FormControl();
      // At runtime we coerce arbitrary values assigned to the "disabled" input to a "boolean".
      // This is not reflected in the type of the property because outside of templates, consumers
      // should only deal with booleans. In templates, a string is allowed for convenience and to
      // match the native "disabled attribute" semantics which can be observed on input elements.
      // This static member tells the compiler that values of type "string" can also be assigned
      // to the input in a template.
      /** @docs-private */
      static ngAcceptInputType_isDisabled;
      /** @internal */
      _registered = false;
      /**
       * Internal reference to the view model value.
       * @docs-private
       */
      viewModel;
      /**
       * @description
       * Tracks the name bound to the directive. If a parent form exists, it
       * uses this name as a key to retrieve this control's value.
       */
      name = "";
      /**
       * @description
       * Tracks whether the control is disabled.
       */
      isDisabled;
      /**
       * @description
       * Tracks the value bound to this directive.
       */
      model;
      /**
       * @description
       * Tracks the configuration options for this `ngModel` instance.
       *
       * **name**: An alternative to setting the name attribute on the form control element. See
       * the [example](api/forms/NgModel#using-ngmodel-on-a-standalone-control) for using `NgModel`
       * as a standalone control.
       *
       * **standalone**: When set to true, the `ngModel` will not register itself with its parent form,
       * and acts as if it's not in the form. Defaults to false. If no parent form exists, this option
       * has no effect.
       *
       * **updateOn**: Defines the event upon which the form control value and validity update.
       * Defaults to 'change'. Possible values: `'change'` | `'blur'` | `'submit'`.
       *
       */
      options;
      /**
       * @description
       * Event emitter for producing the `ngModelChange` event after
       * the view model updates.
       */
      update = new EventEmitter();
      constructor(parent, validators, asyncValidators, valueAccessors, _changeDetectorRef, callSetDisabledState) {
        super();
        this._changeDetectorRef = _changeDetectorRef;
        this.callSetDisabledState = callSetDisabledState;
        this._parent = parent;
        this._setValidators(validators);
        this._setAsyncValidators(asyncValidators);
        this.valueAccessor = selectValueAccessor(this, valueAccessors);
      }
      /** @docs-private */
      ngOnChanges(changes) {
        this._checkForErrors();
        if (!this._registered || "name" in changes) {
          if (this._registered) {
            this._checkName();
            if (this.formDirective) {
              const oldName = changes["name"].previousValue;
              this.formDirective.removeControl({ name: oldName, path: this._getPath(oldName) });
            }
          }
          this._setUpControl();
        }
        if ("isDisabled" in changes) {
          this._updateDisabled(changes);
        }
        if (isPropertyUpdated(changes, this.viewModel)) {
          this._updateValue(this.model);
          this.viewModel = this.model;
        }
      }
      /** @docs-private */
      ngOnDestroy() {
        this.formDirective && this.formDirective.removeControl(this);
      }
      /**
       * @description
       * Returns an array that represents the path from the top-level form to this control.
       * Each index is the string name of the control on that level.
       */
      get path() {
        return this._getPath(this.name);
      }
      /**
       * @description
       * The top-level directive for this control if present, otherwise null.
       */
      get formDirective() {
        return this._parent ? this._parent.formDirective : null;
      }
      /**
       * @description
       * Sets the new value for the view model and emits an `ngModelChange` event.
       *
       * @param newValue The new value emitted by `ngModelChange`.
       */
      viewToModelUpdate(newValue) {
        this.viewModel = newValue;
        this.update.emit(newValue);
      }
      _setUpControl() {
        this._setUpdateStrategy();
        this._isStandalone() ? this._setUpStandalone() : this.formDirective.addControl(this);
        this._registered = true;
      }
      _setUpdateStrategy() {
        if (this.options && this.options.updateOn != null) {
          this.control._updateOn = this.options.updateOn;
        }
      }
      _isStandalone() {
        return !this._parent || !!(this.options && this.options.standalone);
      }
      _setUpStandalone() {
        setUpControl(this.control, this, this.callSetDisabledState);
        this.control.updateValueAndValidity({ emitEvent: false });
      }
      _checkForErrors() {
        if ((typeof ngDevMode === "undefined" || ngDevMode) && !this._isStandalone()) {
          checkParentType$1(this._parent);
        }
        this._checkName();
      }
      _checkName() {
        if (this.options && this.options.name)
          this.name = this.options.name;
        if (!this._isStandalone() && !this.name && (typeof ngDevMode === "undefined" || ngDevMode)) {
          throw missingNameException();
        }
      }
      _updateValue(value) {
        resolvedPromise.then(() => {
          this.control.setValue(value, { emitViewToModelChange: false });
          this._changeDetectorRef?.markForCheck();
        });
      }
      _updateDisabled(changes) {
        const disabledValue = changes["isDisabled"].currentValue;
        const isDisabled = disabledValue !== 0 && booleanAttribute(disabledValue);
        resolvedPromise.then(() => {
          if (isDisabled && !this.control.disabled) {
            this.control.disable();
          } else if (!isDisabled && this.control.disabled) {
            this.control.enable();
          }
          this._changeDetectorRef?.markForCheck();
        });
      }
      _getPath(controlName) {
        return this._parent ? controlPath(controlName, this._parent) : [controlName];
      }
      static \u0275fac = \u0275\u0275ngDeclareFactory({ minVersion: "12.0.0", version: "20.3.29", ngImport: core_exports, type: _NgModel, deps: [{ token: ControlContainer, host: true, optional: true }, { token: NG_VALIDATORS, optional: true, self: true }, { token: NG_ASYNC_VALIDATORS, optional: true, self: true }, { token: NG_VALUE_ACCESSOR, optional: true, self: true }, { token: ChangeDetectorRef, optional: true }, { token: CALL_SET_DISABLED_STATE, optional: true }], target: FactoryTarget.Directive });
      static \u0275dir = \u0275\u0275ngDeclareDirective({ minVersion: "14.0.0", version: "20.3.29", type: _NgModel, isStandalone: false, selector: "[ngModel]:not([formControlName]):not([formControl])", inputs: { name: "name", isDisabled: ["disabled", "isDisabled"], model: ["ngModel", "model"], options: ["ngModelOptions", "options"] }, outputs: { update: "ngModelChange" }, providers: [formControlBinding$1], exportAs: ["ngModel"], usesInheritance: true, usesOnChanges: true, ngImport: core_exports });
    };
    \u0275\u0275ngDeclareClassMetadata({ minVersion: "12.0.0", version: "20.3.29", ngImport: core_exports, type: NgModel, decorators: [{
      type: Directive,
      args: [{
        selector: "[ngModel]:not([formControlName]):not([formControl])",
        providers: [formControlBinding$1],
        exportAs: "ngModel",
        standalone: false
      }]
    }], ctorParameters: () => [{ type: ControlContainer, decorators: [{
      type: Optional
    }, {
      type: Host
    }] }, { type: void 0, decorators: [{
      type: Optional
    }, {
      type: Self
    }, {
      type: Inject,
      args: [NG_VALIDATORS]
    }] }, { type: void 0, decorators: [{
      type: Optional
    }, {
      type: Self
    }, {
      type: Inject,
      args: [NG_ASYNC_VALIDATORS]
    }] }, { type: void 0, decorators: [{
      type: Optional
    }, {
      type: Self
    }, {
      type: Inject,
      args: [NG_VALUE_ACCESSOR]
    }] }, { type: ChangeDetectorRef, decorators: [{
      type: Optional
    }, {
      type: Inject,
      args: [ChangeDetectorRef]
    }] }, { type: void 0, decorators: [{
      type: Optional
    }, {
      type: Inject,
      args: [CALL_SET_DISABLED_STATE]
    }] }], propDecorators: { name: [{
      type: Input
    }], isDisabled: [{
      type: Input,
      args: ["disabled"]
    }], model: [{
      type: Input,
      args: ["ngModel"]
    }], options: [{
      type: Input,
      args: ["ngModelOptions"]
    }], update: [{
      type: Output,
      args: ["ngModelChange"]
    }] } });
    \u0275NgNoValidate = class _\u0275NgNoValidate {
      static \u0275fac = \u0275\u0275ngDeclareFactory({ minVersion: "12.0.0", version: "20.3.29", ngImport: core_exports, type: _\u0275NgNoValidate, deps: [], target: FactoryTarget.Directive });
      static \u0275dir = \u0275\u0275ngDeclareDirective({ minVersion: "14.0.0", version: "20.3.29", type: _\u0275NgNoValidate, isStandalone: false, selector: "form:not([ngNoForm]):not([ngNativeValidate])", host: { attributes: { "novalidate": "" } }, ngImport: core_exports });
    };
    \u0275\u0275ngDeclareClassMetadata({ minVersion: "12.0.0", version: "20.3.29", ngImport: core_exports, type: \u0275NgNoValidate, decorators: [{
      type: Directive,
      args: [{
        selector: "form:not([ngNoForm]):not([ngNativeValidate])",
        host: { "novalidate": "" },
        standalone: false
      }]
    }] });
    NUMBER_VALUE_ACCESSOR = {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => NumberValueAccessor),
      multi: true
    };
    NumberValueAccessor = class _NumberValueAccessor extends BuiltInControlValueAccessor {
      /**
       * Sets the "value" property on the input element.
       * @docs-private
       */
      writeValue(value) {
        const normalizedValue = value == null ? "" : value;
        this.setProperty("value", normalizedValue);
      }
      /**
       * Registers a function called when the control value changes.
       * @docs-private
       */
      registerOnChange(fn) {
        this.onChange = (value) => {
          fn(value == "" ? null : parseFloat(value));
        };
      }
      static \u0275fac = \u0275\u0275ngDeclareFactory({ minVersion: "12.0.0", version: "20.3.29", ngImport: core_exports, type: _NumberValueAccessor, deps: null, target: FactoryTarget.Directive });
      static \u0275dir = \u0275\u0275ngDeclareDirective({ minVersion: "14.0.0", version: "20.3.29", type: _NumberValueAccessor, isStandalone: false, selector: "input[type=number][formControlName],input[type=number][formControl],input[type=number][ngModel]", host: { listeners: { "input": "onChange($any($event.target).value)", "blur": "onTouched()" } }, providers: [NUMBER_VALUE_ACCESSOR], usesInheritance: true, ngImport: core_exports });
    };
    \u0275\u0275ngDeclareClassMetadata({ minVersion: "12.0.0", version: "20.3.29", ngImport: core_exports, type: NumberValueAccessor, decorators: [{
      type: Directive,
      args: [{
        selector: "input[type=number][formControlName],input[type=number][formControl],input[type=number][ngModel]",
        host: { "(input)": "onChange($any($event.target).value)", "(blur)": "onTouched()" },
        providers: [NUMBER_VALUE_ACCESSOR],
        standalone: false
      }]
    }] });
    RADIO_VALUE_ACCESSOR = {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => RadioControlValueAccessor),
      multi: true
    };
    RadioControlRegistry = class _RadioControlRegistry {
      _accessors = [];
      /**
       * @description
       * Adds a control to the internal registry. For internal use only.
       */
      add(control, accessor) {
        this._accessors.push([control, accessor]);
      }
      /**
       * @description
       * Removes a control from the internal registry. For internal use only.
       */
      remove(accessor) {
        for (let i = this._accessors.length - 1; i >= 0; --i) {
          if (this._accessors[i][1] === accessor) {
            this._accessors.splice(i, 1);
            return;
          }
        }
      }
      /**
       * @description
       * Selects a radio button. For internal use only.
       */
      select(accessor) {
        this._accessors.forEach((c) => {
          if (this._isSameGroup(c, accessor) && c[1] !== accessor) {
            c[1].fireUncheck(accessor.value);
          }
        });
      }
      _isSameGroup(controlPair, accessor) {
        if (!controlPair[0].control)
          return false;
        return controlPair[0]._parent === accessor._control._parent && controlPair[1].name === accessor.name;
      }
      static \u0275fac = \u0275\u0275ngDeclareFactory({ minVersion: "12.0.0", version: "20.3.29", ngImport: core_exports, type: _RadioControlRegistry, deps: [], target: FactoryTarget.Injectable });
      static \u0275prov = \u0275\u0275ngDeclareInjectable({ minVersion: "12.0.0", version: "20.3.29", ngImport: core_exports, type: _RadioControlRegistry, providedIn: "root" });
    };
    \u0275\u0275ngDeclareClassMetadata({ minVersion: "12.0.0", version: "20.3.29", ngImport: core_exports, type: RadioControlRegistry, decorators: [{
      type: Injectable,
      args: [{ providedIn: "root" }]
    }] });
    RadioControlValueAccessor = class _RadioControlValueAccessor extends BuiltInControlValueAccessor {
      _registry;
      _injector;
      /** @internal */
      _state;
      /** @internal */
      _control;
      /** @internal */
      _fn;
      setDisabledStateFired = false;
      /**
       * The registered callback function called when a change event occurs on the input element.
       * Note: we declare `onChange` here (also used as host listener) as a function with no arguments
       * to override the `onChange` function (which expects 1 argument) in the parent
       * `BaseControlValueAccessor` class.
       * @docs-private
       */
      onChange = () => {
      };
      /**
       * @description
       * Tracks the name of the radio input element.
       */
      name;
      /**
       * @description
       * Tracks the name of the `FormControl` bound to the directive. The name corresponds
       * to a key in the parent `FormGroup` or `FormArray`.
       */
      formControlName;
      /**
       * @description
       * Tracks the value of the radio input element
       */
      value;
      callSetDisabledState = inject(CALL_SET_DISABLED_STATE, { optional: true }) ?? setDisabledStateDefault;
      constructor(renderer, elementRef, _registry, _injector) {
        super(renderer, elementRef);
        this._registry = _registry;
        this._injector = _injector;
      }
      /** @docs-private */
      ngOnInit() {
        this._control = this._injector.get(NgControl);
        this._checkName();
        this._registry.add(this._control, this);
      }
      /** @docs-private */
      ngOnDestroy() {
        this._registry.remove(this);
      }
      /**
       * Sets the "checked" property value on the radio input element.
       * @docs-private
       */
      writeValue(value) {
        this._state = value === this.value;
        this.setProperty("checked", this._state);
      }
      /**
       * Registers a function called when the control value changes.
       * @docs-private
       */
      registerOnChange(fn) {
        this._fn = fn;
        this.onChange = () => {
          fn(this.value);
          this._registry.select(this);
        };
      }
      /** @docs-private */
      setDisabledState(isDisabled) {
        if (this.setDisabledStateFired || isDisabled || this.callSetDisabledState === "whenDisabledForLegacyCode") {
          this.setProperty("disabled", isDisabled);
        }
        this.setDisabledStateFired = true;
      }
      /**
       * Sets the "value" on the radio input element and unchecks it.
       *
       * @param value
       */
      fireUncheck(value) {
        this.writeValue(value);
      }
      _checkName() {
        if (this.name && this.formControlName && this.name !== this.formControlName && (typeof ngDevMode === "undefined" || ngDevMode)) {
          throwNameError();
        }
        if (!this.name && this.formControlName)
          this.name = this.formControlName;
      }
      static \u0275fac = \u0275\u0275ngDeclareFactory({ minVersion: "12.0.0", version: "20.3.29", ngImport: core_exports, type: _RadioControlValueAccessor, deps: [{ token: Renderer2 }, { token: ElementRef }, { token: RadioControlRegistry }, { token: Injector }], target: FactoryTarget.Directive });
      static \u0275dir = \u0275\u0275ngDeclareDirective({ minVersion: "14.0.0", version: "20.3.29", type: _RadioControlValueAccessor, isStandalone: false, selector: "input[type=radio][formControlName],input[type=radio][formControl],input[type=radio][ngModel]", inputs: { name: "name", formControlName: "formControlName", value: "value" }, host: { listeners: { "change": "onChange()", "blur": "onTouched()" } }, providers: [RADIO_VALUE_ACCESSOR], usesInheritance: true, ngImport: core_exports });
    };
    \u0275\u0275ngDeclareClassMetadata({ minVersion: "12.0.0", version: "20.3.29", ngImport: core_exports, type: RadioControlValueAccessor, decorators: [{
      type: Directive,
      args: [{
        selector: "input[type=radio][formControlName],input[type=radio][formControl],input[type=radio][ngModel]",
        host: { "(change)": "onChange()", "(blur)": "onTouched()" },
        providers: [RADIO_VALUE_ACCESSOR],
        standalone: false
      }]
    }], ctorParameters: () => [{ type: Renderer2 }, { type: ElementRef }, { type: RadioControlRegistry }, { type: Injector }], propDecorators: { name: [{
      type: Input
    }], formControlName: [{
      type: Input
    }], value: [{
      type: Input
    }] } });
    RANGE_VALUE_ACCESSOR = {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => RangeValueAccessor),
      multi: true
    };
    RangeValueAccessor = class _RangeValueAccessor extends BuiltInControlValueAccessor {
      /**
       * Sets the "value" property on the input element.
       * @docs-private
       */
      writeValue(value) {
        this.setProperty("value", parseFloat(value));
      }
      /**
       * Registers a function called when the control value changes.
       * @docs-private
       */
      registerOnChange(fn) {
        this.onChange = (value) => {
          fn(value == "" ? null : parseFloat(value));
        };
      }
      static \u0275fac = \u0275\u0275ngDeclareFactory({ minVersion: "12.0.0", version: "20.3.29", ngImport: core_exports, type: _RangeValueAccessor, deps: null, target: FactoryTarget.Directive });
      static \u0275dir = \u0275\u0275ngDeclareDirective({ minVersion: "14.0.0", version: "20.3.29", type: _RangeValueAccessor, isStandalone: false, selector: "input[type=range][formControlName],input[type=range][formControl],input[type=range][ngModel]", host: { listeners: { "change": "onChange($any($event.target).value)", "input": "onChange($any($event.target).value)", "blur": "onTouched()" } }, providers: [RANGE_VALUE_ACCESSOR], usesInheritance: true, ngImport: core_exports });
    };
    \u0275\u0275ngDeclareClassMetadata({ minVersion: "12.0.0", version: "20.3.29", ngImport: core_exports, type: RangeValueAccessor, decorators: [{
      type: Directive,
      args: [{
        selector: "input[type=range][formControlName],input[type=range][formControl],input[type=range][ngModel]",
        host: {
          "(change)": "onChange($any($event.target).value)",
          "(input)": "onChange($any($event.target).value)",
          "(blur)": "onTouched()"
        },
        providers: [RANGE_VALUE_ACCESSOR],
        standalone: false
      }]
    }] });
    NG_MODEL_WITH_FORM_CONTROL_WARNING = new InjectionToken(ngDevMode ? "NgModelWithFormControlWarning" : "");
    formControlBinding = {
      provide: NgControl,
      useExisting: forwardRef(() => FormControlDirective)
    };
    FormControlDirective = class _FormControlDirective extends NgControl {
      _ngModelWarningConfig;
      callSetDisabledState;
      /**
       * Internal reference to the view model value.
       * @docs-private
       */
      viewModel;
      /**
       * @description
       * Tracks the `FormControl` instance bound to the directive.
       */
      form;
      /**
       * @description
       * Triggers a warning in dev mode that this input should not be used with reactive forms.
       */
      set isDisabled(isDisabled) {
        if (typeof ngDevMode === "undefined" || ngDevMode) {
          console.warn(disabledAttrWarning);
        }
      }
      // TODO(kara): remove next 4 properties once deprecation period is over
      /** @deprecated as of v6 */
      model;
      /** @deprecated as of v6 */
      update = new EventEmitter();
      /**
       * @description
       * Static property used to track whether any ngModel warnings have been sent across
       * all instances of FormControlDirective. Used to support warning config of "once".
       *
       * @internal
       */
      static _ngModelWarningSentOnce = false;
      /**
       * @description
       * Instance property used to track whether an ngModel warning has been sent out for this
       * particular `FormControlDirective` instance. Used to support warning config of "always".
       *
       * @internal
       */
      _ngModelWarningSent = false;
      constructor(validators, asyncValidators, valueAccessors, _ngModelWarningConfig, callSetDisabledState) {
        super();
        this._ngModelWarningConfig = _ngModelWarningConfig;
        this.callSetDisabledState = callSetDisabledState;
        this._setValidators(validators);
        this._setAsyncValidators(asyncValidators);
        this.valueAccessor = selectValueAccessor(this, valueAccessors);
      }
      /** @docs-private */
      ngOnChanges(changes) {
        if (this._isControlChanged(changes)) {
          const previousForm = changes["form"].previousValue;
          if (previousForm) {
            cleanUpControl(
              previousForm,
              this,
              /* validateControlPresenceOnChange */
              false
            );
          }
          setUpControl(this.form, this, this.callSetDisabledState);
          this.form.updateValueAndValidity({ emitEvent: false });
        }
        if (isPropertyUpdated(changes, this.viewModel)) {
          if (typeof ngDevMode === "undefined" || ngDevMode) {
            _ngModelWarning("formControl", _FormControlDirective, this, this._ngModelWarningConfig);
          }
          this.form.setValue(this.model);
          this.viewModel = this.model;
        }
      }
      /** @docs-private */
      ngOnDestroy() {
        if (this.form) {
          cleanUpControl(
            this.form,
            this,
            /* validateControlPresenceOnChange */
            false
          );
        }
      }
      /**
       * @description
       * Returns an array that represents the path from the top-level form to this control.
       * Each index is the string name of the control on that level.
       */
      get path() {
        return [];
      }
      /**
       * @description
       * The `FormControl` bound to this directive.
       */
      get control() {
        return this.form;
      }
      /**
       * @description
       * Sets the new value for the view model and emits an `ngModelChange` event.
       *
       * @param newValue The new value for the view model.
       */
      viewToModelUpdate(newValue) {
        this.viewModel = newValue;
        this.update.emit(newValue);
      }
      _isControlChanged(changes) {
        return changes.hasOwnProperty("form");
      }
      static \u0275fac = \u0275\u0275ngDeclareFactory({ minVersion: "12.0.0", version: "20.3.29", ngImport: core_exports, type: _FormControlDirective, deps: [{ token: NG_VALIDATORS, optional: true, self: true }, { token: NG_ASYNC_VALIDATORS, optional: true, self: true }, { token: NG_VALUE_ACCESSOR, optional: true, self: true }, { token: NG_MODEL_WITH_FORM_CONTROL_WARNING, optional: true }, { token: CALL_SET_DISABLED_STATE, optional: true }], target: FactoryTarget.Directive });
      static \u0275dir = \u0275\u0275ngDeclareDirective({ minVersion: "14.0.0", version: "20.3.29", type: _FormControlDirective, isStandalone: false, selector: "[formControl]", inputs: { form: ["formControl", "form"], isDisabled: ["disabled", "isDisabled"], model: ["ngModel", "model"] }, outputs: { update: "ngModelChange" }, providers: [formControlBinding], exportAs: ["ngForm"], usesInheritance: true, usesOnChanges: true, ngImport: core_exports });
    };
    \u0275\u0275ngDeclareClassMetadata({ minVersion: "12.0.0", version: "20.3.29", ngImport: core_exports, type: FormControlDirective, decorators: [{
      type: Directive,
      args: [{
        selector: "[formControl]",
        providers: [formControlBinding],
        exportAs: "ngForm",
        standalone: false
      }]
    }], ctorParameters: () => [{ type: void 0, decorators: [{
      type: Optional
    }, {
      type: Self
    }, {
      type: Inject,
      args: [NG_VALIDATORS]
    }] }, { type: void 0, decorators: [{
      type: Optional
    }, {
      type: Self
    }, {
      type: Inject,
      args: [NG_ASYNC_VALIDATORS]
    }] }, { type: void 0, decorators: [{
      type: Optional
    }, {
      type: Self
    }, {
      type: Inject,
      args: [NG_VALUE_ACCESSOR]
    }] }, { type: void 0, decorators: [{
      type: Optional
    }, {
      type: Inject,
      args: [NG_MODEL_WITH_FORM_CONTROL_WARNING]
    }] }, { type: void 0, decorators: [{
      type: Optional
    }, {
      type: Inject,
      args: [CALL_SET_DISABLED_STATE]
    }] }], propDecorators: { form: [{
      type: Input,
      args: ["formControl"]
    }], isDisabled: [{
      type: Input,
      args: ["disabled"]
    }], model: [{
      type: Input,
      args: ["ngModel"]
    }], update: [{
      type: Output,
      args: ["ngModelChange"]
    }] } });
    formDirectiveProvider = {
      provide: ControlContainer,
      useExisting: forwardRef(() => FormGroupDirective)
    };
    FormGroupDirective = class _FormGroupDirective extends ControlContainer {
      callSetDisabledState;
      /**
       * @description
       * Reports whether the form submission has been triggered.
       */
      get submitted() {
        return untracked(this._submittedReactive);
      }
      // TODO(atscott): Remove once invalid API usage is cleaned up internally
      set submitted(value) {
        this._submittedReactive.set(value);
      }
      /** @internal */
      _submitted = computed(() => this._submittedReactive(), ...ngDevMode ? [{ debugName: "_submitted" }] : []);
      _submittedReactive = signal(false, ...ngDevMode ? [{ debugName: "_submittedReactive" }] : []);
      /**
       * Reference to an old form group input value, which is needed to cleanup
       * old instance in case it was replaced with a new one.
       */
      _oldForm;
      /**
       * Callback that should be invoked when controls in FormGroup or FormArray collection change
       * (added or removed). This callback triggers corresponding DOM updates.
       */
      _onCollectionChange = () => this._updateDomValue();
      /**
       * @description
       * Tracks the list of added `FormControlName` instances
       */
      directives = [];
      /**
       * @description
       * Tracks the `FormGroup` bound to this directive.
       */
      form = null;
      /**
       * @description
       * Emits an event when the form submission has been triggered.
       */
      ngSubmit = new EventEmitter();
      constructor(validators, asyncValidators, callSetDisabledState) {
        super();
        this.callSetDisabledState = callSetDisabledState;
        this._setValidators(validators);
        this._setAsyncValidators(asyncValidators);
      }
      /** @docs-private */
      ngOnChanges(changes) {
        if ((typeof ngDevMode === "undefined" || ngDevMode) && !this.form) {
          throw missingFormException();
        }
        if (changes.hasOwnProperty("form")) {
          this._updateValidators();
          this._updateDomValue();
          this._updateRegistrations();
          this._oldForm = this.form;
        }
      }
      /** @docs-private */
      ngOnDestroy() {
        if (this.form) {
          cleanUpValidators(this.form, this);
          if (this.form._onCollectionChange === this._onCollectionChange) {
            this.form._registerOnCollectionChange(() => {
            });
          }
        }
      }
      /**
       * @description
       * Returns this directive's instance.
       */
      get formDirective() {
        return this;
      }
      /**
       * @description
       * Returns the `FormGroup` bound to this directive.
       */
      get control() {
        return this.form;
      }
      /**
       * @description
       * Returns an array representing the path to this group. Because this directive
       * always lives at the top level of a form, it always an empty array.
       */
      get path() {
        return [];
      }
      /**
       * @description
       * Method that sets up the control directive in this group, re-calculates its value
       * and validity, and adds the instance to the internal list of directives.
       *
       * @param dir The `FormControlName` directive instance.
       */
      addControl(dir) {
        const ctrl = this.form.get(dir.path);
        setUpControl(ctrl, dir, this.callSetDisabledState);
        ctrl.updateValueAndValidity({ emitEvent: false });
        this.directives.push(dir);
        return ctrl;
      }
      /**
       * @description
       * Retrieves the `FormControl` instance from the provided `FormControlName` directive
       *
       * @param dir The `FormControlName` directive instance.
       */
      getControl(dir) {
        return this.form.get(dir.path);
      }
      /**
       * @description
       * Removes the `FormControlName` instance from the internal list of directives
       *
       * @param dir The `FormControlName` directive instance.
       */
      removeControl(dir) {
        cleanUpControl(
          dir.control || null,
          dir,
          /* validateControlPresenceOnChange */
          false
        );
        removeListItem$1(this.directives, dir);
      }
      /**
       * Adds a new `FormGroupName` directive instance to the form.
       *
       * @param dir The `FormGroupName` directive instance.
       */
      addFormGroup(dir) {
        this._setUpFormContainer(dir);
      }
      /**
       * Performs the necessary cleanup when a `FormGroupName` directive instance is removed from the
       * view.
       *
       * @param dir The `FormGroupName` directive instance.
       */
      removeFormGroup(dir) {
        this._cleanUpFormContainer(dir);
      }
      /**
       * @description
       * Retrieves the `FormGroup` for a provided `FormGroupName` directive instance
       *
       * @param dir The `FormGroupName` directive instance.
       */
      getFormGroup(dir) {
        return this.form.get(dir.path);
      }
      /**
       * Performs the necessary setup when a `FormArrayName` directive instance is added to the view.
       *
       * @param dir The `FormArrayName` directive instance.
       */
      addFormArray(dir) {
        this._setUpFormContainer(dir);
      }
      /**
       * Performs the necessary cleanup when a `FormArrayName` directive instance is removed from the
       * view.
       *
       * @param dir The `FormArrayName` directive instance.
       */
      removeFormArray(dir) {
        this._cleanUpFormContainer(dir);
      }
      /**
       * @description
       * Retrieves the `FormArray` for a provided `FormArrayName` directive instance.
       *
       * @param dir The `FormArrayName` directive instance.
       */
      getFormArray(dir) {
        return this.form.get(dir.path);
      }
      /**
       * Sets the new value for the provided `FormControlName` directive.
       *
       * @param dir The `FormControlName` directive instance.
       * @param value The new value for the directive's control.
       */
      updateModel(dir, value) {
        const ctrl = this.form.get(dir.path);
        ctrl.setValue(value);
      }
      /**
       * @description
       * Method called with the "submit" event is triggered on the form.
       * Triggers the `ngSubmit` emitter to emit the "submit" event as its payload.
       *
       * @param $event The "submit" event object
       */
      onSubmit($event) {
        this._submittedReactive.set(true);
        syncPendingControls(this.form, this.directives);
        this.ngSubmit.emit($event);
        this.form._events.next(new FormSubmittedEvent(this.control));
        return $event?.target?.method === "dialog";
      }
      /**
       * @description
       * Method called when the "reset" event is triggered on the form.
       */
      onReset() {
        this.resetForm();
      }
      /**
       * @description
       * Resets the form to an initial value and resets its submitted status.
       *
       * @param value The new value for the form, `undefined` by default
       */
      resetForm(value = void 0, options = {}) {
        this.form.reset(value, options);
        this._submittedReactive.set(false);
      }
      /** @internal */
      _updateDomValue() {
        this.directives.forEach((dir) => {
          const oldCtrl = dir.control;
          const newCtrl = this.form.get(dir.path);
          if (oldCtrl !== newCtrl) {
            cleanUpControl(oldCtrl || null, dir);
            if (isFormControl(newCtrl)) {
              setUpControl(newCtrl, dir, this.callSetDisabledState);
              dir.control = newCtrl;
            }
          }
        });
        this.form._updateTreeValidity({ emitEvent: false });
      }
      _setUpFormContainer(dir) {
        const ctrl = this.form.get(dir.path);
        setUpFormContainer(ctrl, dir);
        ctrl.updateValueAndValidity({ emitEvent: false });
      }
      _cleanUpFormContainer(dir) {
        if (this.form) {
          const ctrl = this.form.get(dir.path);
          if (ctrl) {
            const isControlUpdated = cleanUpFormContainer(ctrl, dir);
            if (isControlUpdated) {
              ctrl.updateValueAndValidity({ emitEvent: false });
            }
          }
        }
      }
      _updateRegistrations() {
        this.form._registerOnCollectionChange(this._onCollectionChange);
        if (this._oldForm) {
          this._oldForm._registerOnCollectionChange(() => {
          });
        }
      }
      _updateValidators() {
        setUpValidators(this.form, this);
        if (this._oldForm) {
          cleanUpValidators(this._oldForm, this);
        }
      }
      static \u0275fac = \u0275\u0275ngDeclareFactory({ minVersion: "12.0.0", version: "20.3.29", ngImport: core_exports, type: _FormGroupDirective, deps: [{ token: NG_VALIDATORS, optional: true, self: true }, { token: NG_ASYNC_VALIDATORS, optional: true, self: true }, { token: CALL_SET_DISABLED_STATE, optional: true }], target: FactoryTarget.Directive });
      static \u0275dir = \u0275\u0275ngDeclareDirective({ minVersion: "14.0.0", version: "20.3.29", type: _FormGroupDirective, isStandalone: false, selector: "[formGroup]", inputs: { form: ["formGroup", "form"] }, outputs: { ngSubmit: "ngSubmit" }, host: { listeners: { "submit": "onSubmit($event)", "reset": "onReset()" } }, providers: [formDirectiveProvider], exportAs: ["ngForm"], usesInheritance: true, usesOnChanges: true, ngImport: core_exports });
    };
    \u0275\u0275ngDeclareClassMetadata({ minVersion: "12.0.0", version: "20.3.29", ngImport: core_exports, type: FormGroupDirective, decorators: [{
      type: Directive,
      args: [{
        selector: "[formGroup]",
        providers: [formDirectiveProvider],
        host: { "(submit)": "onSubmit($event)", "(reset)": "onReset()" },
        exportAs: "ngForm",
        standalone: false
      }]
    }], ctorParameters: () => [{ type: void 0, decorators: [{
      type: Optional
    }, {
      type: Self
    }, {
      type: Inject,
      args: [NG_VALIDATORS]
    }] }, { type: void 0, decorators: [{
      type: Optional
    }, {
      type: Self
    }, {
      type: Inject,
      args: [NG_ASYNC_VALIDATORS]
    }] }, { type: void 0, decorators: [{
      type: Optional
    }, {
      type: Inject,
      args: [CALL_SET_DISABLED_STATE]
    }] }], propDecorators: { form: [{
      type: Input,
      args: ["formGroup"]
    }], ngSubmit: [{
      type: Output
    }] } });
    formGroupNameProvider = {
      provide: ControlContainer,
      useExisting: forwardRef(() => FormGroupName)
    };
    FormGroupName = class _FormGroupName extends AbstractFormGroupDirective {
      /**
       * @description
       * Tracks the name of the `FormGroup` bound to the directive. The name corresponds
       * to a key in the parent `FormGroup` or `FormArray`.
       * Accepts a name as a string or a number.
       * The name in the form of a string is useful for individual forms,
       * while the numerical form allows for form groups to be bound
       * to indices when iterating over groups in a `FormArray`.
       */
      name = null;
      constructor(parent, validators, asyncValidators) {
        super();
        this._parent = parent;
        this._setValidators(validators);
        this._setAsyncValidators(asyncValidators);
      }
      /** @internal */
      _checkParentType() {
        if (hasInvalidParent(this._parent) && (typeof ngDevMode === "undefined" || ngDevMode)) {
          throw groupParentException();
        }
      }
      static \u0275fac = \u0275\u0275ngDeclareFactory({ minVersion: "12.0.0", version: "20.3.29", ngImport: core_exports, type: _FormGroupName, deps: [{ token: ControlContainer, host: true, optional: true, skipSelf: true }, { token: NG_VALIDATORS, optional: true, self: true }, { token: NG_ASYNC_VALIDATORS, optional: true, self: true }], target: FactoryTarget.Directive });
      static \u0275dir = \u0275\u0275ngDeclareDirective({ minVersion: "14.0.0", version: "20.3.29", type: _FormGroupName, isStandalone: false, selector: "[formGroupName]", inputs: { name: ["formGroupName", "name"] }, providers: [formGroupNameProvider], usesInheritance: true, ngImport: core_exports });
    };
    \u0275\u0275ngDeclareClassMetadata({ minVersion: "12.0.0", version: "20.3.29", ngImport: core_exports, type: FormGroupName, decorators: [{
      type: Directive,
      args: [{
        selector: "[formGroupName]",
        providers: [formGroupNameProvider],
        standalone: false
      }]
    }], ctorParameters: () => [{ type: ControlContainer, decorators: [{
      type: Optional
    }, {
      type: Host
    }, {
      type: SkipSelf
    }] }, { type: void 0, decorators: [{
      type: Optional
    }, {
      type: Self
    }, {
      type: Inject,
      args: [NG_VALIDATORS]
    }] }, { type: void 0, decorators: [{
      type: Optional
    }, {
      type: Self
    }, {
      type: Inject,
      args: [NG_ASYNC_VALIDATORS]
    }] }], propDecorators: { name: [{
      type: Input,
      args: ["formGroupName"]
    }] } });
    formArrayNameProvider = {
      provide: ControlContainer,
      useExisting: forwardRef(() => FormArrayName)
    };
    FormArrayName = class _FormArrayName extends ControlContainer {
      /** @internal */
      _parent;
      /**
       * @description
       * Tracks the name of the `FormArray` bound to the directive. The name corresponds
       * to a key in the parent `FormGroup` or `FormArray`.
       * Accepts a name as a string or a number.
       * The name in the form of a string is useful for individual forms,
       * while the numerical form allows for form arrays to be bound
       * to indices when iterating over arrays in a `FormArray`.
       */
      name = null;
      constructor(parent, validators, asyncValidators) {
        super();
        this._parent = parent;
        this._setValidators(validators);
        this._setAsyncValidators(asyncValidators);
      }
      /**
       * A lifecycle method called when the directive's inputs are initialized. For internal use only.
       * @throws If the directive does not have a valid parent.
       * @docs-private
       */
      ngOnInit() {
        if (hasInvalidParent(this._parent) && (typeof ngDevMode === "undefined" || ngDevMode)) {
          throw arrayParentException();
        }
        this.formDirective.addFormArray(this);
      }
      /**
       * A lifecycle method called before the directive's instance is destroyed. For internal use only.
       * @docs-private
       */
      ngOnDestroy() {
        this.formDirective?.removeFormArray(this);
      }
      /**
       * @description
       * The `FormArray` bound to this directive.
       */
      get control() {
        return this.formDirective.getFormArray(this);
      }
      /**
       * @description
       * The top-level directive for this group if present, otherwise null.
       */
      get formDirective() {
        return this._parent ? this._parent.formDirective : null;
      }
      /**
       * @description
       * Returns an array that represents the path from the top-level form to this control.
       * Each index is the string name of the control on that level.
       */
      get path() {
        return controlPath(this.name == null ? this.name : this.name.toString(), this._parent);
      }
      static \u0275fac = \u0275\u0275ngDeclareFactory({ minVersion: "12.0.0", version: "20.3.29", ngImport: core_exports, type: _FormArrayName, deps: [{ token: ControlContainer, host: true, optional: true, skipSelf: true }, { token: NG_VALIDATORS, optional: true, self: true }, { token: NG_ASYNC_VALIDATORS, optional: true, self: true }], target: FactoryTarget.Directive });
      static \u0275dir = \u0275\u0275ngDeclareDirective({ minVersion: "14.0.0", version: "20.3.29", type: _FormArrayName, isStandalone: false, selector: "[formArrayName]", inputs: { name: ["formArrayName", "name"] }, providers: [formArrayNameProvider], usesInheritance: true, ngImport: core_exports });
    };
    \u0275\u0275ngDeclareClassMetadata({ minVersion: "12.0.0", version: "20.3.29", ngImport: core_exports, type: FormArrayName, decorators: [{
      type: Directive,
      args: [{
        selector: "[formArrayName]",
        providers: [formArrayNameProvider],
        standalone: false
      }]
    }], ctorParameters: () => [{ type: ControlContainer, decorators: [{
      type: Optional
    }, {
      type: Host
    }, {
      type: SkipSelf
    }] }, { type: void 0, decorators: [{
      type: Optional
    }, {
      type: Self
    }, {
      type: Inject,
      args: [NG_VALIDATORS]
    }] }, { type: void 0, decorators: [{
      type: Optional
    }, {
      type: Self
    }, {
      type: Inject,
      args: [NG_ASYNC_VALIDATORS]
    }] }], propDecorators: { name: [{
      type: Input,
      args: ["formArrayName"]
    }] } });
    controlNameBinding = {
      provide: NgControl,
      useExisting: forwardRef(() => FormControlName)
    };
    FormControlName = class _FormControlName extends NgControl {
      _ngModelWarningConfig;
      _added = false;
      /**
       * Internal reference to the view model value.
       * @internal
       */
      viewModel;
      /**
       * @description
       * Tracks the `FormControl` instance bound to the directive.
       */
      control;
      /**
       * @description
       * Tracks the name of the `FormControl` bound to the directive. The name corresponds
       * to a key in the parent `FormGroup` or `FormArray`.
       * Accepts a name as a string or a number.
       * The name in the form of a string is useful for individual forms,
       * while the numerical form allows for form controls to be bound
       * to indices when iterating over controls in a `FormArray`.
       */
      name = null;
      /**
       * @description
       * Triggers a warning in dev mode that this input should not be used with reactive forms.
       */
      set isDisabled(isDisabled) {
        if (typeof ngDevMode === "undefined" || ngDevMode) {
          console.warn(disabledAttrWarning);
        }
      }
      // TODO(kara): remove next 4 properties once deprecation period is over
      /** @deprecated as of v6 */
      model;
      /** @deprecated as of v6 */
      update = new EventEmitter();
      /**
       * @description
       * Static property used to track whether any ngModel warnings have been sent across
       * all instances of FormControlName. Used to support warning config of "once".
       *
       * @internal
       */
      static _ngModelWarningSentOnce = false;
      /**
       * @description
       * Instance property used to track whether an ngModel warning has been sent out for this
       * particular FormControlName instance. Used to support warning config of "always".
       *
       * @internal
       */
      _ngModelWarningSent = false;
      constructor(parent, validators, asyncValidators, valueAccessors, _ngModelWarningConfig) {
        super();
        this._ngModelWarningConfig = _ngModelWarningConfig;
        this._parent = parent;
        this._setValidators(validators);
        this._setAsyncValidators(asyncValidators);
        this.valueAccessor = selectValueAccessor(this, valueAccessors);
      }
      /** @docs-private */
      ngOnChanges(changes) {
        if (!this._added)
          this._setUpControl();
        if (isPropertyUpdated(changes, this.viewModel)) {
          if (typeof ngDevMode === "undefined" || ngDevMode) {
            _ngModelWarning("formControlName", _FormControlName, this, this._ngModelWarningConfig);
          }
          this.viewModel = this.model;
          this.formDirective.updateModel(this, this.model);
        }
      }
      /** @docs-private */
      ngOnDestroy() {
        if (this.formDirective) {
          this.formDirective.removeControl(this);
        }
      }
      /**
       * @description
       * Sets the new value for the view model and emits an `ngModelChange` event.
       *
       * @param newValue The new value for the view model.
       */
      viewToModelUpdate(newValue) {
        this.viewModel = newValue;
        this.update.emit(newValue);
      }
      /**
       * @description
       * Returns an array that represents the path from the top-level form to this control.
       * Each index is the string name of the control on that level.
       */
      get path() {
        return controlPath(this.name == null ? this.name : this.name.toString(), this._parent);
      }
      /**
       * @description
       * The top-level directive for this group if present, otherwise null.
       */
      get formDirective() {
        return this._parent ? this._parent.formDirective : null;
      }
      _setUpControl() {
        if (typeof ngDevMode === "undefined" || ngDevMode) {
          checkParentType(this._parent, this.name);
        }
        this.control = this.formDirective.addControl(this);
        this._added = true;
      }
      static \u0275fac = \u0275\u0275ngDeclareFactory({ minVersion: "12.0.0", version: "20.3.29", ngImport: core_exports, type: _FormControlName, deps: [{ token: ControlContainer, host: true, optional: true, skipSelf: true }, { token: NG_VALIDATORS, optional: true, self: true }, { token: NG_ASYNC_VALIDATORS, optional: true, self: true }, { token: NG_VALUE_ACCESSOR, optional: true, self: true }, { token: NG_MODEL_WITH_FORM_CONTROL_WARNING, optional: true }], target: FactoryTarget.Directive });
      static \u0275dir = \u0275\u0275ngDeclareDirective({ minVersion: "14.0.0", version: "20.3.29", type: _FormControlName, isStandalone: false, selector: "[formControlName]", inputs: { name: ["formControlName", "name"], isDisabled: ["disabled", "isDisabled"], model: ["ngModel", "model"] }, outputs: { update: "ngModelChange" }, providers: [controlNameBinding], usesInheritance: true, usesOnChanges: true, ngImport: core_exports });
    };
    \u0275\u0275ngDeclareClassMetadata({ minVersion: "12.0.0", version: "20.3.29", ngImport: core_exports, type: FormControlName, decorators: [{
      type: Directive,
      args: [{
        selector: "[formControlName]",
        providers: [controlNameBinding],
        standalone: false
      }]
    }], ctorParameters: () => [{ type: ControlContainer, decorators: [{
      type: Optional
    }, {
      type: Host
    }, {
      type: SkipSelf
    }] }, { type: void 0, decorators: [{
      type: Optional
    }, {
      type: Self
    }, {
      type: Inject,
      args: [NG_VALIDATORS]
    }] }, { type: void 0, decorators: [{
      type: Optional
    }, {
      type: Self
    }, {
      type: Inject,
      args: [NG_ASYNC_VALIDATORS]
    }] }, { type: void 0, decorators: [{
      type: Optional
    }, {
      type: Self
    }, {
      type: Inject,
      args: [NG_VALUE_ACCESSOR]
    }] }, { type: void 0, decorators: [{
      type: Optional
    }, {
      type: Inject,
      args: [NG_MODEL_WITH_FORM_CONTROL_WARNING]
    }] }], propDecorators: { name: [{
      type: Input,
      args: ["formControlName"]
    }], isDisabled: [{
      type: Input,
      args: ["disabled"]
    }], model: [{
      type: Input,
      args: ["ngModel"]
    }], update: [{
      type: Output,
      args: ["ngModelChange"]
    }] } });
    SELECT_VALUE_ACCESSOR = {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => SelectControlValueAccessor),
      multi: true
    };
    SelectControlValueAccessor = class _SelectControlValueAccessor extends BuiltInControlValueAccessor {
      /** @docs-private */
      value;
      /** @internal */
      _optionMap = /* @__PURE__ */ new Map();
      /** @internal */
      _idCounter = 0;
      /**
       * @description
       * Tracks the option comparison algorithm for tracking identities when
       * checking for changes.
       */
      set compareWith(fn) {
        if (typeof fn !== "function" && (typeof ngDevMode === "undefined" || ngDevMode)) {
          throw new RuntimeError(1201, `compareWith must be a function, but received ${JSON.stringify(fn)}`);
        }
        this._compareWith = fn;
      }
      _compareWith = Object.is;
      // We need this because we might be in the process of destroying the root
      // injector, which is marked as destroyed before running destroy hooks.
      // Attempting to use afterNextRender with the node injector would evntually
      // run into that already destroyed injector.
      appRefInjector = inject(ApplicationRef).injector;
      destroyRef = inject(DestroyRef);
      cdr = inject(ChangeDetectorRef);
      _queuedWrite = false;
      /**
       * This is needed to efficiently set the select value when adding/removing options. If
       * writeValue is instead called for every added/removed option, this results in exponentially
       * more _compareValue calls than the number of option elements (issue #41330).
       *
       * Secondly, calling writeValue when rendering individual option elements instead of after they
       * are all rendered caused an issue in Safari and IE 11 where the first option element failed
       * to be deselected when no option matched the select ngModel. This was because Angular would
       * set the select element's value property before appending the option's child text node to the
       * DOM (issue #14505).
       *
       * Finally, this approach is necessary to avoid an issue with delayed element removal when
       * using the animations module (in all browsers). Otherwise when a selected option is removed
       * (so no option matches the ngModel anymore), Angular would change the select element value
       * before actually removing the option from the DOM. Then when the option is finally removed
       * from the DOM, the browser would change the select value to that of the first option, even
       * though it doesn't match the ngModel (issue #18430).
       *
       * @internal
       */
      _writeValueAfterRender() {
        if (this._queuedWrite || this.appRefInjector.destroyed) {
          return;
        }
        this._queuedWrite = true;
        afterNextRender({
          write: () => {
            if (this.destroyRef.destroyed) {
              return;
            }
            this._queuedWrite = false;
            this.writeValue(this.value);
          }
        }, { injector: this.appRefInjector });
      }
      /**
       * Sets the "value" property on the select element.
       * @docs-private
       */
      writeValue(value) {
        this.cdr.markForCheck();
        this.value = value;
        const id = this._getOptionId(value);
        const valueString = _buildValueString$1(id, value);
        this.setProperty("value", valueString);
      }
      /**
       * Registers a function called when the control value changes.
       * @docs-private
       */
      registerOnChange(fn) {
        this.onChange = (valueString) => {
          this.value = this._getOptionValue(valueString);
          fn(this.value);
        };
      }
      /** @internal */
      _registerOption() {
        return (this._idCounter++).toString();
      }
      /** @internal */
      _getOptionId(value) {
        for (const id of this._optionMap.keys()) {
          if (this._compareWith(this._optionMap.get(id), value))
            return id;
        }
        return null;
      }
      /** @internal */
      _getOptionValue(valueString) {
        const id = _extractId$1(valueString);
        return this._optionMap.has(id) ? this._optionMap.get(id) : valueString;
      }
      static \u0275fac = \u0275\u0275ngDeclareFactory({ minVersion: "12.0.0", version: "20.3.29", ngImport: core_exports, type: _SelectControlValueAccessor, deps: null, target: FactoryTarget.Directive });
      static \u0275dir = \u0275\u0275ngDeclareDirective({ minVersion: "14.0.0", version: "20.3.29", type: _SelectControlValueAccessor, isStandalone: false, selector: "select:not([multiple])[formControlName],select:not([multiple])[formControl],select:not([multiple])[ngModel]", inputs: { compareWith: "compareWith" }, host: { listeners: { "change": "onChange($any($event.target).value)", "blur": "onTouched()" } }, providers: [SELECT_VALUE_ACCESSOR], usesInheritance: true, ngImport: core_exports });
    };
    \u0275\u0275ngDeclareClassMetadata({ minVersion: "12.0.0", version: "20.3.29", ngImport: core_exports, type: SelectControlValueAccessor, decorators: [{
      type: Directive,
      args: [{
        selector: "select:not([multiple])[formControlName],select:not([multiple])[formControl],select:not([multiple])[ngModel]",
        host: { "(change)": "onChange($any($event.target).value)", "(blur)": "onTouched()" },
        providers: [SELECT_VALUE_ACCESSOR],
        standalone: false
      }]
    }], propDecorators: { compareWith: [{
      type: Input
    }] } });
    NgSelectOption = class _NgSelectOption {
      _element;
      _renderer;
      _select;
      /**
       * @description
       * ID of the option element
       */
      id;
      constructor(_element, _renderer, _select) {
        this._element = _element;
        this._renderer = _renderer;
        this._select = _select;
        if (this._select)
          this.id = this._select._registerOption();
      }
      /**
       * @description
       * Tracks the value bound to the option element. Unlike the value binding,
       * ngValue supports binding to objects.
       */
      set ngValue(value) {
        if (this._select == null)
          return;
        this._select._optionMap.set(this.id, value);
        this._setElementValue(_buildValueString$1(this.id, value));
        this._select._writeValueAfterRender();
      }
      /**
       * @description
       * Tracks simple string values bound to the option element.
       * For objects, use the `ngValue` input binding.
       */
      set value(value) {
        this._setElementValue(value);
        if (this._select)
          this._select._writeValueAfterRender();
      }
      /** @internal */
      _setElementValue(value) {
        this._renderer.setProperty(this._element.nativeElement, "value", value);
      }
      /** @docs-private */
      ngOnDestroy() {
        if (this._select) {
          this._select._optionMap.delete(this.id);
          this._select._writeValueAfterRender();
        }
      }
      static \u0275fac = \u0275\u0275ngDeclareFactory({ minVersion: "12.0.0", version: "20.3.29", ngImport: core_exports, type: _NgSelectOption, deps: [{ token: ElementRef }, { token: Renderer2 }, { token: SelectControlValueAccessor, host: true, optional: true }], target: FactoryTarget.Directive });
      static \u0275dir = \u0275\u0275ngDeclareDirective({ minVersion: "14.0.0", version: "20.3.29", type: _NgSelectOption, isStandalone: false, selector: "option", inputs: { ngValue: "ngValue", value: "value" }, ngImport: core_exports });
    };
    \u0275\u0275ngDeclareClassMetadata({ minVersion: "12.0.0", version: "20.3.29", ngImport: core_exports, type: NgSelectOption, decorators: [{
      type: Directive,
      args: [{
        selector: "option",
        standalone: false
      }]
    }], ctorParameters: () => [{ type: ElementRef }, { type: Renderer2 }, { type: SelectControlValueAccessor, decorators: [{
      type: Optional
    }, {
      type: Host
    }] }], propDecorators: { ngValue: [{
      type: Input,
      args: ["ngValue"]
    }], value: [{
      type: Input,
      args: ["value"]
    }] } });
    SELECT_MULTIPLE_VALUE_ACCESSOR = {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => SelectMultipleControlValueAccessor),
      multi: true
    };
    SelectMultipleControlValueAccessor = class _SelectMultipleControlValueAccessor extends BuiltInControlValueAccessor {
      /**
       * The current value.
       * @docs-private
       */
      value;
      /** @internal */
      _optionMap = /* @__PURE__ */ new Map();
      /** @internal */
      _idCounter = 0;
      /**
       * @description
       * Tracks the option comparison algorithm for tracking identities when
       * checking for changes.
       */
      set compareWith(fn) {
        if (typeof fn !== "function" && (typeof ngDevMode === "undefined" || ngDevMode)) {
          throw new RuntimeError(1201, `compareWith must be a function, but received ${JSON.stringify(fn)}`);
        }
        this._compareWith = fn;
      }
      _compareWith = Object.is;
      /**
       * Sets the "value" property on one or of more of the select's options.
       * @docs-private
       */
      writeValue(value) {
        this.value = value;
        let optionSelectedStateSetter;
        if (Array.isArray(value)) {
          const ids = value.map((v) => this._getOptionId(v));
          optionSelectedStateSetter = (opt, o) => {
            opt._setSelected(ids.indexOf(o.toString()) > -1);
          };
        } else {
          optionSelectedStateSetter = (opt, o) => {
            opt._setSelected(false);
          };
        }
        this._optionMap.forEach(optionSelectedStateSetter);
      }
      /**
       * Registers a function called when the control value changes
       * and writes an array of the selected options.
       * @docs-private
       */
      registerOnChange(fn) {
        this.onChange = (element) => {
          const selected = [];
          const selectedOptions = element.selectedOptions;
          if (selectedOptions !== void 0) {
            const options = selectedOptions;
            for (let i = 0; i < options.length; i++) {
              const opt = options[i];
              const val = this._getOptionValue(opt.value);
              selected.push(val);
            }
          } else {
            const options = element.options;
            for (let i = 0; i < options.length; i++) {
              const opt = options[i];
              if (opt.selected) {
                const val = this._getOptionValue(opt.value);
                selected.push(val);
              }
            }
          }
          this.value = selected;
          fn(selected);
        };
      }
      /** @internal */
      _registerOption(value) {
        const id = (this._idCounter++).toString();
        this._optionMap.set(id, value);
        return id;
      }
      /** @internal */
      _getOptionId(value) {
        for (const id of this._optionMap.keys()) {
          if (this._compareWith(this._optionMap.get(id)._value, value))
            return id;
        }
        return null;
      }
      /** @internal */
      _getOptionValue(valueString) {
        const id = _extractId(valueString);
        return this._optionMap.has(id) ? this._optionMap.get(id)._value : valueString;
      }
      static \u0275fac = \u0275\u0275ngDeclareFactory({ minVersion: "12.0.0", version: "20.3.29", ngImport: core_exports, type: _SelectMultipleControlValueAccessor, deps: null, target: FactoryTarget.Directive });
      static \u0275dir = \u0275\u0275ngDeclareDirective({ minVersion: "14.0.0", version: "20.3.29", type: _SelectMultipleControlValueAccessor, isStandalone: false, selector: "select[multiple][formControlName],select[multiple][formControl],select[multiple][ngModel]", inputs: { compareWith: "compareWith" }, host: { listeners: { "change": "onChange($event.target)", "blur": "onTouched()" } }, providers: [SELECT_MULTIPLE_VALUE_ACCESSOR], usesInheritance: true, ngImport: core_exports });
    };
    \u0275\u0275ngDeclareClassMetadata({ minVersion: "12.0.0", version: "20.3.29", ngImport: core_exports, type: SelectMultipleControlValueAccessor, decorators: [{
      type: Directive,
      args: [{
        selector: "select[multiple][formControlName],select[multiple][formControl],select[multiple][ngModel]",
        host: { "(change)": "onChange($event.target)", "(blur)": "onTouched()" },
        providers: [SELECT_MULTIPLE_VALUE_ACCESSOR],
        standalone: false
      }]
    }], propDecorators: { compareWith: [{
      type: Input
    }] } });
    \u0275NgSelectMultipleOption = class _\u0275NgSelectMultipleOption {
      _element;
      _renderer;
      _select;
      id;
      /** @internal */
      _value;
      constructor(_element, _renderer, _select) {
        this._element = _element;
        this._renderer = _renderer;
        this._select = _select;
        if (this._select) {
          this.id = this._select._registerOption(this);
        }
      }
      /**
       * @description
       * Tracks the value bound to the option element. Unlike the value binding,
       * ngValue supports binding to objects.
       */
      set ngValue(value) {
        if (this._select == null)
          return;
        this._value = value;
        this._setElementValue(_buildValueString(this.id, value));
        this._select.writeValue(this._select.value);
      }
      /**
       * @description
       * Tracks simple string values bound to the option element.
       * For objects, use the `ngValue` input binding.
       */
      set value(value) {
        if (this._select) {
          this._value = value;
          this._setElementValue(_buildValueString(this.id, value));
          this._select.writeValue(this._select.value);
        } else {
          this._setElementValue(value);
        }
      }
      /** @internal */
      _setElementValue(value) {
        this._renderer.setProperty(this._element.nativeElement, "value", value);
      }
      /** @internal */
      _setSelected(selected) {
        this._renderer.setProperty(this._element.nativeElement, "selected", selected);
      }
      /** @docs-private */
      ngOnDestroy() {
        if (this._select) {
          this._select._optionMap.delete(this.id);
          this._select.writeValue(this._select.value);
        }
      }
      static \u0275fac = \u0275\u0275ngDeclareFactory({ minVersion: "12.0.0", version: "20.3.29", ngImport: core_exports, type: _\u0275NgSelectMultipleOption, deps: [{ token: ElementRef }, { token: Renderer2 }, { token: SelectMultipleControlValueAccessor, host: true, optional: true }], target: FactoryTarget.Directive });
      static \u0275dir = \u0275\u0275ngDeclareDirective({ minVersion: "14.0.0", version: "20.3.29", type: _\u0275NgSelectMultipleOption, isStandalone: false, selector: "option", inputs: { ngValue: "ngValue", value: "value" }, ngImport: core_exports });
    };
    \u0275\u0275ngDeclareClassMetadata({ minVersion: "12.0.0", version: "20.3.29", ngImport: core_exports, type: \u0275NgSelectMultipleOption, decorators: [{
      type: Directive,
      args: [{
        selector: "option",
        standalone: false
      }]
    }], ctorParameters: () => [{ type: ElementRef }, { type: Renderer2 }, { type: SelectMultipleControlValueAccessor, decorators: [{
      type: Optional
    }, {
      type: Host
    }] }], propDecorators: { ngValue: [{
      type: Input,
      args: ["ngValue"]
    }], value: [{
      type: Input,
      args: ["value"]
    }] } });
    AbstractValidatorDirective = class _AbstractValidatorDirective {
      _validator = nullValidator;
      _onChange;
      /**
       * A flag that tracks whether this validator is enabled.
       *
       * Marking it `internal` (vs `protected`), so that this flag can be used in host bindings of
       * directive classes that extend this base class.
       * @internal
       */
      _enabled;
      /** @docs-private */
      ngOnChanges(changes) {
        if (this.inputName in changes) {
          const input = this.normalizeInput(changes[this.inputName].currentValue);
          this._enabled = this.enabled(input);
          this._validator = this._enabled ? this.createValidator(input) : nullValidator;
          if (this._onChange) {
            this._onChange();
          }
        }
      }
      /** @docs-private */
      validate(control) {
        return this._validator(control);
      }
      /** @docs-private */
      registerOnValidatorChange(fn) {
        this._onChange = fn;
      }
      /**
       * @description
       * Determines whether this validator should be active or not based on an input.
       * Base class implementation checks whether an input is defined (if the value is different from
       * `null` and `undefined`). Validator classes that extend this base class can override this
       * function with the logic specific to a particular validator directive.
       */
      enabled(input) {
        return input != null;
      }
      static \u0275fac = \u0275\u0275ngDeclareFactory({ minVersion: "12.0.0", version: "20.3.29", ngImport: core_exports, type: _AbstractValidatorDirective, deps: [], target: FactoryTarget.Directive });
      static \u0275dir = \u0275\u0275ngDeclareDirective({ minVersion: "14.0.0", version: "20.3.29", type: _AbstractValidatorDirective, isStandalone: true, usesOnChanges: true, ngImport: core_exports });
    };
    \u0275\u0275ngDeclareClassMetadata({ minVersion: "12.0.0", version: "20.3.29", ngImport: core_exports, type: AbstractValidatorDirective, decorators: [{
      type: Directive
    }] });
    MAX_VALIDATOR = {
      provide: NG_VALIDATORS,
      useExisting: forwardRef(() => MaxValidator),
      multi: true
    };
    MaxValidator = class _MaxValidator extends AbstractValidatorDirective {
      /**
       * @description
       * Tracks changes to the max bound to this directive.
       */
      max;
      /** @internal */
      inputName = "max";
      /** @internal */
      normalizeInput = (input) => toFloat(input);
      /** @internal */
      createValidator = (max) => maxValidator(max);
      static \u0275fac = \u0275\u0275ngDeclareFactory({ minVersion: "12.0.0", version: "20.3.29", ngImport: core_exports, type: _MaxValidator, deps: null, target: FactoryTarget.Directive });
      static \u0275dir = \u0275\u0275ngDeclareDirective({ minVersion: "14.0.0", version: "20.3.29", type: _MaxValidator, isStandalone: false, selector: "input[type=number][max][formControlName],input[type=number][max][formControl],input[type=number][max][ngModel]", inputs: { max: "max" }, host: { properties: { "attr.max": "_enabled ? max : null" } }, providers: [MAX_VALIDATOR], usesInheritance: true, ngImport: core_exports });
    };
    \u0275\u0275ngDeclareClassMetadata({ minVersion: "12.0.0", version: "20.3.29", ngImport: core_exports, type: MaxValidator, decorators: [{
      type: Directive,
      args: [{
        selector: "input[type=number][max][formControlName],input[type=number][max][formControl],input[type=number][max][ngModel]",
        providers: [MAX_VALIDATOR],
        host: { "[attr.max]": "_enabled ? max : null" },
        standalone: false
      }]
    }], propDecorators: { max: [{
      type: Input
    }] } });
    MIN_VALIDATOR = {
      provide: NG_VALIDATORS,
      useExisting: forwardRef(() => MinValidator),
      multi: true
    };
    MinValidator = class _MinValidator extends AbstractValidatorDirective {
      /**
       * @description
       * Tracks changes to the min bound to this directive.
       */
      min;
      /** @internal */
      inputName = "min";
      /** @internal */
      normalizeInput = (input) => toFloat(input);
      /** @internal */
      createValidator = (min) => minValidator(min);
      static \u0275fac = \u0275\u0275ngDeclareFactory({ minVersion: "12.0.0", version: "20.3.29", ngImport: core_exports, type: _MinValidator, deps: null, target: FactoryTarget.Directive });
      static \u0275dir = \u0275\u0275ngDeclareDirective({ minVersion: "14.0.0", version: "20.3.29", type: _MinValidator, isStandalone: false, selector: "input[type=number][min][formControlName],input[type=number][min][formControl],input[type=number][min][ngModel]", inputs: { min: "min" }, host: { properties: { "attr.min": "_enabled ? min : null" } }, providers: [MIN_VALIDATOR], usesInheritance: true, ngImport: core_exports });
    };
    \u0275\u0275ngDeclareClassMetadata({ minVersion: "12.0.0", version: "20.3.29", ngImport: core_exports, type: MinValidator, decorators: [{
      type: Directive,
      args: [{
        selector: "input[type=number][min][formControlName],input[type=number][min][formControl],input[type=number][min][ngModel]",
        providers: [MIN_VALIDATOR],
        host: { "[attr.min]": "_enabled ? min : null" },
        standalone: false
      }]
    }], propDecorators: { min: [{
      type: Input
    }] } });
    REQUIRED_VALIDATOR = {
      provide: NG_VALIDATORS,
      useExisting: forwardRef(() => RequiredValidator),
      multi: true
    };
    CHECKBOX_REQUIRED_VALIDATOR = {
      provide: NG_VALIDATORS,
      useExisting: forwardRef(() => CheckboxRequiredValidator),
      multi: true
    };
    RequiredValidator = class _RequiredValidator extends AbstractValidatorDirective {
      /**
       * @description
       * Tracks changes to the required attribute bound to this directive.
       */
      required;
      /** @internal */
      inputName = "required";
      /** @internal */
      normalizeInput = booleanAttribute;
      /** @internal */
      createValidator = (input) => requiredValidator;
      /** @docs-private */
      enabled(input) {
        return input;
      }
      static \u0275fac = \u0275\u0275ngDeclareFactory({ minVersion: "12.0.0", version: "20.3.29", ngImport: core_exports, type: _RequiredValidator, deps: null, target: FactoryTarget.Directive });
      static \u0275dir = \u0275\u0275ngDeclareDirective({ minVersion: "14.0.0", version: "20.3.29", type: _RequiredValidator, isStandalone: false, selector: ":not([type=checkbox])[required][formControlName],:not([type=checkbox])[required][formControl],:not([type=checkbox])[required][ngModel]", inputs: { required: "required" }, host: { properties: { "attr.required": '_enabled ? "" : null' } }, providers: [REQUIRED_VALIDATOR], usesInheritance: true, ngImport: core_exports });
    };
    \u0275\u0275ngDeclareClassMetadata({ minVersion: "12.0.0", version: "20.3.29", ngImport: core_exports, type: RequiredValidator, decorators: [{
      type: Directive,
      args: [{
        selector: ":not([type=checkbox])[required][formControlName],:not([type=checkbox])[required][formControl],:not([type=checkbox])[required][ngModel]",
        providers: [REQUIRED_VALIDATOR],
        host: { "[attr.required]": '_enabled ? "" : null' },
        standalone: false
      }]
    }], propDecorators: { required: [{
      type: Input
    }] } });
    CheckboxRequiredValidator = class _CheckboxRequiredValidator extends RequiredValidator {
      /** @internal */
      createValidator = (input) => requiredTrueValidator;
      static \u0275fac = \u0275\u0275ngDeclareFactory({ minVersion: "12.0.0", version: "20.3.29", ngImport: core_exports, type: _CheckboxRequiredValidator, deps: null, target: FactoryTarget.Directive });
      static \u0275dir = \u0275\u0275ngDeclareDirective({ minVersion: "14.0.0", version: "20.3.29", type: _CheckboxRequiredValidator, isStandalone: false, selector: "input[type=checkbox][required][formControlName],input[type=checkbox][required][formControl],input[type=checkbox][required][ngModel]", host: { properties: { "attr.required": '_enabled ? "" : null' } }, providers: [CHECKBOX_REQUIRED_VALIDATOR], usesInheritance: true, ngImport: core_exports });
    };
    \u0275\u0275ngDeclareClassMetadata({ minVersion: "12.0.0", version: "20.3.29", ngImport: core_exports, type: CheckboxRequiredValidator, decorators: [{
      type: Directive,
      args: [{
        selector: "input[type=checkbox][required][formControlName],input[type=checkbox][required][formControl],input[type=checkbox][required][ngModel]",
        providers: [CHECKBOX_REQUIRED_VALIDATOR],
        host: { "[attr.required]": '_enabled ? "" : null' },
        standalone: false
      }]
    }] });
    EMAIL_VALIDATOR = {
      provide: NG_VALIDATORS,
      useExisting: forwardRef(() => EmailValidator),
      multi: true
    };
    EmailValidator = class _EmailValidator extends AbstractValidatorDirective {
      /**
       * @description
       * Tracks changes to the email attribute bound to this directive.
       */
      email;
      /** @internal */
      inputName = "email";
      /** @internal */
      normalizeInput = booleanAttribute;
      /** @internal */
      createValidator = (input) => emailValidator;
      /** @docs-private */
      enabled(input) {
        return input;
      }
      static \u0275fac = \u0275\u0275ngDeclareFactory({ minVersion: "12.0.0", version: "20.3.29", ngImport: core_exports, type: _EmailValidator, deps: null, target: FactoryTarget.Directive });
      static \u0275dir = \u0275\u0275ngDeclareDirective({ minVersion: "14.0.0", version: "20.3.29", type: _EmailValidator, isStandalone: false, selector: "[email][formControlName],[email][formControl],[email][ngModel]", inputs: { email: "email" }, providers: [EMAIL_VALIDATOR], usesInheritance: true, ngImport: core_exports });
    };
    \u0275\u0275ngDeclareClassMetadata({ minVersion: "12.0.0", version: "20.3.29", ngImport: core_exports, type: EmailValidator, decorators: [{
      type: Directive,
      args: [{
        selector: "[email][formControlName],[email][formControl],[email][ngModel]",
        providers: [EMAIL_VALIDATOR],
        standalone: false
      }]
    }], propDecorators: { email: [{
      type: Input
    }] } });
    MIN_LENGTH_VALIDATOR = {
      provide: NG_VALIDATORS,
      useExisting: forwardRef(() => MinLengthValidator),
      multi: true
    };
    MinLengthValidator = class _MinLengthValidator extends AbstractValidatorDirective {
      /**
       * @description
       * Tracks changes to the minimum length bound to this directive.
       */
      minlength;
      /** @internal */
      inputName = "minlength";
      /** @internal */
      normalizeInput = (input) => toInteger(input);
      /** @internal */
      createValidator = (minlength) => minLengthValidator(minlength);
      static \u0275fac = \u0275\u0275ngDeclareFactory({ minVersion: "12.0.0", version: "20.3.29", ngImport: core_exports, type: _MinLengthValidator, deps: null, target: FactoryTarget.Directive });
      static \u0275dir = \u0275\u0275ngDeclareDirective({ minVersion: "14.0.0", version: "20.3.29", type: _MinLengthValidator, isStandalone: false, selector: "[minlength][formControlName],[minlength][formControl],[minlength][ngModel]", inputs: { minlength: "minlength" }, host: { properties: { "attr.minlength": "_enabled ? minlength : null" } }, providers: [MIN_LENGTH_VALIDATOR], usesInheritance: true, ngImport: core_exports });
    };
    \u0275\u0275ngDeclareClassMetadata({ minVersion: "12.0.0", version: "20.3.29", ngImport: core_exports, type: MinLengthValidator, decorators: [{
      type: Directive,
      args: [{
        selector: "[minlength][formControlName],[minlength][formControl],[minlength][ngModel]",
        providers: [MIN_LENGTH_VALIDATOR],
        host: { "[attr.minlength]": "_enabled ? minlength : null" },
        standalone: false
      }]
    }], propDecorators: { minlength: [{
      type: Input
    }] } });
    MAX_LENGTH_VALIDATOR = {
      provide: NG_VALIDATORS,
      useExisting: forwardRef(() => MaxLengthValidator),
      multi: true
    };
    MaxLengthValidator = class _MaxLengthValidator extends AbstractValidatorDirective {
      /**
       * @description
       * Tracks changes to the maximum length bound to this directive.
       */
      maxlength;
      /** @internal */
      inputName = "maxlength";
      /** @internal */
      normalizeInput = (input) => toInteger(input);
      /** @internal */
      createValidator = (maxlength) => maxLengthValidator(maxlength);
      static \u0275fac = \u0275\u0275ngDeclareFactory({ minVersion: "12.0.0", version: "20.3.29", ngImport: core_exports, type: _MaxLengthValidator, deps: null, target: FactoryTarget.Directive });
      static \u0275dir = \u0275\u0275ngDeclareDirective({ minVersion: "14.0.0", version: "20.3.29", type: _MaxLengthValidator, isStandalone: false, selector: "[maxlength][formControlName],[maxlength][formControl],[maxlength][ngModel]", inputs: { maxlength: "maxlength" }, host: { properties: { "attr.maxlength": "_enabled ? maxlength : null" } }, providers: [MAX_LENGTH_VALIDATOR], usesInheritance: true, ngImport: core_exports });
    };
    \u0275\u0275ngDeclareClassMetadata({ minVersion: "12.0.0", version: "20.3.29", ngImport: core_exports, type: MaxLengthValidator, decorators: [{
      type: Directive,
      args: [{
        selector: "[maxlength][formControlName],[maxlength][formControl],[maxlength][ngModel]",
        providers: [MAX_LENGTH_VALIDATOR],
        host: { "[attr.maxlength]": "_enabled ? maxlength : null" },
        standalone: false
      }]
    }], propDecorators: { maxlength: [{
      type: Input
    }] } });
    PATTERN_VALIDATOR = {
      provide: NG_VALIDATORS,
      useExisting: forwardRef(() => PatternValidator),
      multi: true
    };
    PatternValidator = class _PatternValidator extends AbstractValidatorDirective {
      /**
       * @description
       * Tracks changes to the pattern bound to this directive.
       */
      pattern;
      // This input is always defined, since the name matches selector.
      /** @internal */
      inputName = "pattern";
      /** @internal */
      normalizeInput = (input) => input;
      /** @internal */
      createValidator = (input) => patternValidator(input);
      static \u0275fac = \u0275\u0275ngDeclareFactory({ minVersion: "12.0.0", version: "20.3.29", ngImport: core_exports, type: _PatternValidator, deps: null, target: FactoryTarget.Directive });
      static \u0275dir = \u0275\u0275ngDeclareDirective({ minVersion: "14.0.0", version: "20.3.29", type: _PatternValidator, isStandalone: false, selector: "[pattern][formControlName],[pattern][formControl],[pattern][ngModel]", inputs: { pattern: "pattern" }, host: { properties: { "attr.pattern": "_enabled ? pattern : null" } }, providers: [PATTERN_VALIDATOR], usesInheritance: true, ngImport: core_exports });
    };
    \u0275\u0275ngDeclareClassMetadata({ minVersion: "12.0.0", version: "20.3.29", ngImport: core_exports, type: PatternValidator, decorators: [{
      type: Directive,
      args: [{
        selector: "[pattern][formControlName],[pattern][formControl],[pattern][ngModel]",
        providers: [PATTERN_VALIDATOR],
        host: { "[attr.pattern]": "_enabled ? pattern : null" },
        standalone: false
      }]
    }], propDecorators: { pattern: [{
      type: Input
    }] } });
    SHARED_FORM_DIRECTIVES = [
      \u0275NgNoValidate,
      NgSelectOption,
      \u0275NgSelectMultipleOption,
      DefaultValueAccessor,
      NumberValueAccessor,
      RangeValueAccessor,
      CheckboxControlValueAccessor,
      SelectControlValueAccessor,
      SelectMultipleControlValueAccessor,
      RadioControlValueAccessor,
      NgControlStatus,
      NgControlStatusGroup,
      RequiredValidator,
      MinLengthValidator,
      MaxLengthValidator,
      PatternValidator,
      CheckboxRequiredValidator,
      EmailValidator,
      MinValidator,
      MaxValidator
    ];
    TEMPLATE_DRIVEN_DIRECTIVES = [NgModel, NgModelGroup, NgForm];
    REACTIVE_DRIVEN_DIRECTIVES = [
      FormControlDirective,
      FormGroupDirective,
      FormControlName,
      FormGroupName,
      FormArrayName
    ];
    \u0275InternalFormsSharedModule = class _\u0275InternalFormsSharedModule {
      static \u0275fac = \u0275\u0275ngDeclareFactory({ minVersion: "12.0.0", version: "20.3.29", ngImport: core_exports, type: _\u0275InternalFormsSharedModule, deps: [], target: FactoryTarget.NgModule });
      static \u0275mod = \u0275\u0275ngDeclareNgModule({ minVersion: "14.0.0", version: "20.3.29", ngImport: core_exports, type: _\u0275InternalFormsSharedModule, declarations: [
        \u0275NgNoValidate,
        NgSelectOption,
        \u0275NgSelectMultipleOption,
        DefaultValueAccessor,
        NumberValueAccessor,
        RangeValueAccessor,
        CheckboxControlValueAccessor,
        SelectControlValueAccessor,
        SelectMultipleControlValueAccessor,
        RadioControlValueAccessor,
        NgControlStatus,
        NgControlStatusGroup,
        RequiredValidator,
        MinLengthValidator,
        MaxLengthValidator,
        PatternValidator,
        CheckboxRequiredValidator,
        EmailValidator,
        MinValidator,
        MaxValidator
      ], exports: [
        \u0275NgNoValidate,
        NgSelectOption,
        \u0275NgSelectMultipleOption,
        DefaultValueAccessor,
        NumberValueAccessor,
        RangeValueAccessor,
        CheckboxControlValueAccessor,
        SelectControlValueAccessor,
        SelectMultipleControlValueAccessor,
        RadioControlValueAccessor,
        NgControlStatus,
        NgControlStatusGroup,
        RequiredValidator,
        MinLengthValidator,
        MaxLengthValidator,
        PatternValidator,
        CheckboxRequiredValidator,
        EmailValidator,
        MinValidator,
        MaxValidator
      ] });
      static \u0275inj = \u0275\u0275ngDeclareInjector({ minVersion: "12.0.0", version: "20.3.29", ngImport: core_exports, type: _\u0275InternalFormsSharedModule });
    };
    \u0275\u0275ngDeclareClassMetadata({ minVersion: "12.0.0", version: "20.3.29", ngImport: core_exports, type: \u0275InternalFormsSharedModule, decorators: [{
      type: NgModule,
      args: [{
        declarations: SHARED_FORM_DIRECTIVES,
        exports: SHARED_FORM_DIRECTIVES
      }]
    }] });
    FormArray = class extends AbstractControl {
      /**
       * Creates a new `FormArray` instance.
       *
       * @param controls An array of child controls. Each child control is given an index
       * where it is registered.
       *
       * @param validatorOrOpts A synchronous validator function, or an array of
       * such functions, or an `AbstractControlOptions` object that contains validation functions
       * and a validation trigger.
       *
       * @param asyncValidator A single async validator or array of async validator functions
       *
       */
      constructor(controls, validatorOrOpts, asyncValidator) {
        super(pickValidators(validatorOrOpts), pickAsyncValidators(asyncValidator, validatorOrOpts));
        this.controls = controls;
        this._initObservables();
        this._setUpdateStrategy(validatorOrOpts);
        this._setUpControls();
        this.updateValueAndValidity({
          onlySelf: true,
          // If `asyncValidator` is present, it will trigger control status change from `PENDING` to
          // `VALID` or `INVALID`.
          // The status should be broadcasted via the `statusChanges` observable, so we set `emitEvent`
          // to `true` to allow that during the control creation process.
          emitEvent: !!this.asyncValidator
        });
      }
      controls;
      /**
       * Get the `AbstractControl` at the given `index` in the array.
       *
       * @param index Index in the array to retrieve the control. If `index` is negative, it will wrap
       *     around from the back, and if index is greatly negative (less than `-length`), the result is
       * undefined. This behavior is the same as `Array.at(index)`.
       */
      at(index) {
        return this.controls[this._adjustIndex(index)];
      }
      /**
       * Insert a new `AbstractControl` at the end of the array.
       *
       * @param control Form control to be inserted
       * @param options Specifies whether this FormArray instance should emit events after a new
       *     control is added.
       * * `emitEvent`: When true or not supplied (the default), both the `statusChanges` and
       * `valueChanges` observables emit events with the latest status and value when the control is
       * inserted. When false, no events are emitted.
       *
       * NOTE: Pushing to the FormArray will not mark it dirty. If you want to mark if dirty, call `markAsDirty()`.
       */
      push(control, options = {}) {
        if (Array.isArray(control)) {
          control.forEach((ctrl) => {
            this.controls.push(ctrl);
            this._registerControl(ctrl);
          });
        } else {
          this.controls.push(control);
          this._registerControl(control);
        }
        this.updateValueAndValidity({ emitEvent: options.emitEvent });
        this._onCollectionChange();
      }
      /**
       * Insert a new `AbstractControl` at the given `index` in the array.
       *
       * @param index Index in the array to insert the control. If `index` is negative, wraps around
       *     from the back. If `index` is greatly negative (less than `-length`), prepends to the array.
       * This behavior is the same as `Array.splice(index, 0, control)`.
       * @param control Form control to be inserted
       * @param options Specifies whether this FormArray instance should emit events after a new
       *     control is inserted.
       * * `emitEvent`: When true or not supplied (the default), both the `statusChanges` and
       * `valueChanges` observables emit events with the latest status and value when the control is
       * inserted. When false, no events are emitted.
       *
       * NOTE: Inserting to the FormArray will not mark it dirty. If you want to mark if dirty, call `markAsDirty()`.
       */
      insert(index, control, options = {}) {
        this.controls.splice(index, 0, control);
        this._registerControl(control);
        this.updateValueAndValidity({ emitEvent: options.emitEvent });
      }
      /**
       * Remove the control at the given `index` in the array.
       *
       * @param index Index in the array to remove the control.  If `index` is negative, wraps around
       *     from the back. If `index` is greatly negative (less than `-length`), removes the first
       *     element. This behavior is the same as `Array.splice(index, 1)`.
       * @param options Specifies whether this FormArray instance should emit events after a
       *     control is removed.
       * * `emitEvent`: When true or not supplied (the default), both the `statusChanges` and
       * `valueChanges` observables emit events with the latest status and value when the control is
       * removed. When false, no events are emitted.
       *
       * NOTE: Removing the FormArray will not mark it dirty. If you want to mark if dirty, call `markAsDirty()`.
       */
      removeAt(index, options = {}) {
        let adjustedIndex = this._adjustIndex(index);
        if (adjustedIndex < 0)
          adjustedIndex = 0;
        if (this.controls[adjustedIndex])
          this.controls[adjustedIndex]._registerOnCollectionChange(() => {
          });
        this.controls.splice(adjustedIndex, 1);
        this.updateValueAndValidity({ emitEvent: options.emitEvent });
      }
      /**
       * Replace an existing control.
       *
       * @param index Index in the array to replace the control. If `index` is negative, wraps around
       *     from the back. If `index` is greatly negative (less than `-length`), replaces the first
       *     element. This behavior is the same as `Array.splice(index, 1, control)`.
       * @param control The `AbstractControl` control to replace the existing control
       * @param options Specifies whether this FormArray instance should emit events after an
       *     existing control is replaced with a new one.
       * * `emitEvent`: When true or not supplied (the default), both the `statusChanges` and
       * `valueChanges` observables emit events with the latest status and value when the control is
       * replaced with a new one. When false, no events are emitted.
       */
      setControl(index, control, options = {}) {
        let adjustedIndex = this._adjustIndex(index);
        if (adjustedIndex < 0)
          adjustedIndex = 0;
        if (this.controls[adjustedIndex])
          this.controls[adjustedIndex]._registerOnCollectionChange(() => {
          });
        this.controls.splice(adjustedIndex, 1);
        if (control) {
          this.controls.splice(adjustedIndex, 0, control);
          this._registerControl(control);
        }
        this.updateValueAndValidity({ emitEvent: options.emitEvent });
        this._onCollectionChange();
      }
      /**
       * Length of the control array.
       */
      get length() {
        return this.controls.length;
      }
      /**
       * Sets the value of the `FormArray`. It accepts an array that matches
       * the structure of the control.
       *
       * This method performs strict checks, and throws an error if you try
       * to set the value of a control that doesn't exist or if you exclude the
       * value of a control.
       *
       * @usageNotes
       * ### Set the values for the controls in the form array
       *
       * ```ts
       * const arr = new FormArray([
       *   new FormControl(),
       *   new FormControl()
       * ]);
       * console.log(arr.value);   // [null, null]
       *
       * arr.setValue(['Nancy', 'Drew']);
       * console.log(arr.value);   // ['Nancy', 'Drew']
       * ```
       *
       * @param value Array of values for the controls
       * @param options Configure options that determine how the control propagates changes and
       * emits events after the value changes
       *
       * * `onlySelf`: When true, each change only affects this control, and not its parent. Default
       * is false.
       * * `emitEvent`: When true or not supplied (the default), both the `statusChanges` and
       * `valueChanges`
       * observables emit events with the latest status and value when the control value is updated.
       * When false, no events are emitted.
       * The configuration options are passed to the {@link AbstractControl#updateValueAndValidity
       * updateValueAndValidity} method.
       */
      setValue(value, options = {}) {
        assertAllValuesPresent(this, false, value);
        value.forEach((newValue, index) => {
          assertControlPresent(this, false, index);
          this.at(index).setValue(newValue, { onlySelf: true, emitEvent: options.emitEvent });
        });
        this.updateValueAndValidity(options);
      }
      /**
       * Patches the value of the `FormArray`. It accepts an array that matches the
       * structure of the control, and does its best to match the values to the correct
       * controls in the group.
       *
       * It accepts both super-sets and sub-sets of the array without throwing an error.
       *
       * @usageNotes
       * ### Patch the values for controls in a form array
       *
       * ```ts
       * const arr = new FormArray([
       *    new FormControl(),
       *    new FormControl()
       * ]);
       * console.log(arr.value);   // [null, null]
       *
       * arr.patchValue(['Nancy']);
       * console.log(arr.value);   // ['Nancy', null]
       * ```
       *
       * @param value Array of latest values for the controls
       * @param options Configure options that determine how the control propagates changes and
       * emits events after the value changes
       *
       * * `onlySelf`: When true, each change only affects this control, and not its parent. Default
       * is false.
       * * `emitEvent`: When true or not supplied (the default), both the `statusChanges` and
       * `valueChanges` observables emit events with the latest status and value when the control
       * value is updated. When false, no events are emitted. The configuration options are passed to
       * the {@link AbstractControl#updateValueAndValidity updateValueAndValidity} method.
       */
      patchValue(value, options = {}) {
        if (value == null)
          return;
        value.forEach((newValue, index) => {
          if (this.at(index)) {
            this.at(index).patchValue(newValue, { onlySelf: true, emitEvent: options.emitEvent });
          }
        });
        this.updateValueAndValidity(options);
      }
      /**
       * Resets the `FormArray` and all descendants are marked `pristine` and `untouched`, and the
       * value of all descendants to null or null maps.
       *
       * You reset to a specific form state by passing in an array of states
       * that matches the structure of the control. The state is a standalone value
       * or a form state object with both a value and a disabled status.
       *
       * @usageNotes
       * ### Reset the values in a form array
       *
       * ```ts
       * const arr = new FormArray([
       *    new FormControl(),
       *    new FormControl()
       * ]);
       * arr.reset(['name', 'last name']);
       *
       * console.log(arr.value);  // ['name', 'last name']
       * ```
       *
       * ### Reset the values in a form array and the disabled status for the first control
       *
       * ```ts
       * arr.reset([
       *   {value: 'name', disabled: true},
       *   'last'
       * ]);
       *
       * console.log(arr.value);  // ['last']
       * console.log(arr.at(0).status);  // 'DISABLED'
       * ```
       *
       * @param value Array of values for the controls
       * @param options Configure options that determine how the control propagates changes and
       * emits events after the value changes
       *
       * * `onlySelf`: When true, each change only affects this control, and not its parent. Default
       * is false.
       * * `emitEvent`: When true or not supplied (the default), both the `statusChanges` and
       * `valueChanges`
       * observables emit events with the latest status and value when the control is reset.
       * When false, no events are emitted.
       * The configuration options are passed to the {@link AbstractControl#updateValueAndValidity
       * updateValueAndValidity} method.
       */
      reset(value = [], options = {}) {
        this._forEachChild((control, index) => {
          control.reset(value[index], { onlySelf: true, emitEvent: options.emitEvent });
        });
        this._updatePristine(options, this);
        this._updateTouched(options, this);
        this.updateValueAndValidity(options);
        if (options?.emitEvent !== false) {
          this._events.next(new FormResetEvent(this));
        }
      }
      /**
       * The aggregate value of the array, including any disabled controls.
       *
       * Reports all values regardless of disabled status.
       */
      getRawValue() {
        return this.controls.map((control) => control.getRawValue());
      }
      /**
       * Remove all controls in the `FormArray`.
       *
       * @param options Specifies whether this FormArray instance should emit events after all
       *     controls are removed.
       * * `emitEvent`: When true or not supplied (the default), both the `statusChanges` and
       * `valueChanges` observables emit events with the latest status and value when all controls
       * in this FormArray instance are removed. When false, no events are emitted.
       *
       * @usageNotes
       * ### Remove all elements from a FormArray
       *
       * ```ts
       * const arr = new FormArray([
       *    new FormControl(),
       *    new FormControl()
       * ]);
       * console.log(arr.length);  // 2
       *
       * arr.clear();
       * console.log(arr.length);  // 0
       * ```
       *
       * It's a simpler and more efficient alternative to removing all elements one by one:
       *
       * ```ts
       * const arr = new FormArray([
       *    new FormControl(),
       *    new FormControl()
       * ]);
       *
       * while (arr.length) {
       *    arr.removeAt(0);
       * }
       * ```
       */
      clear(options = {}) {
        if (this.controls.length < 1)
          return;
        this._forEachChild((control) => control._registerOnCollectionChange(() => {
        }));
        this.controls.splice(0);
        this.updateValueAndValidity({ emitEvent: options.emitEvent });
      }
      /**
       * Adjusts a negative index by summing it with the length of the array. For very negative
       * indices, the result may remain negative.
       * @internal
       */
      _adjustIndex(index) {
        return index < 0 ? index + this.length : index;
      }
      /** @internal */
      _syncPendingControls() {
        let subtreeUpdated = this.controls.reduce((updated, child) => {
          return child._syncPendingControls() ? true : updated;
        }, false);
        if (subtreeUpdated)
          this.updateValueAndValidity({ onlySelf: true });
        return subtreeUpdated;
      }
      /** @internal */
      _forEachChild(cb) {
        this.controls.forEach((control, index) => {
          cb(control, index);
        });
      }
      /** @internal */
      _updateValue() {
        this.value = this.controls.filter((control) => control.enabled || this.disabled).map((control) => control.value);
      }
      /** @internal */
      _anyControls(condition) {
        return this.controls.some((control) => control.enabled && condition(control));
      }
      /** @internal */
      _setUpControls() {
        this._forEachChild((control) => this._registerControl(control));
      }
      /** @internal */
      _allControlsDisabled() {
        for (const control of this.controls) {
          if (control.enabled)
            return false;
        }
        return this.controls.length > 0 || this.disabled;
      }
      _registerControl(control) {
        control.setParent(this);
        control._registerOnCollectionChange(this._onCollectionChange);
      }
      /** @internal */
      _find(name) {
        return this.at(name) ?? null;
      }
    };
    FormBuilder = class _FormBuilder {
      useNonNullable = false;
      /**
       * @description
       * Returns a FormBuilder in which automatically constructed `FormControl` elements
       * have `{nonNullable: true}` and are non-nullable.
       *
       * **Constructing non-nullable controls**
       *
       * When constructing a control, it will be non-nullable, and will reset to its initial value.
       *
       * ```ts
       * let nnfb = new FormBuilder().nonNullable;
       * let name = nnfb.control('Alex'); // FormControl<string>
       * name.reset();
       * console.log(name); // 'Alex'
       * ```
       *
       * **Constructing non-nullable groups or arrays**
       *
       * When constructing a group or array, all automatically created inner controls will be
       * non-nullable, and will reset to their initial values.
       *
       * ```ts
       * let nnfb = new FormBuilder().nonNullable;
       * let name = nnfb.group({who: 'Alex'}); // FormGroup<{who: FormControl<string>}>
       * name.reset();
       * console.log(name); // {who: 'Alex'}
       * ```
       * **Constructing *nullable* fields on groups or arrays**
       *
       * It is still possible to have a nullable field. In particular, any `FormControl` which is
       * *already* constructed will not be altered. For example:
       *
       * ```ts
       * let nnfb = new FormBuilder().nonNullable;
       * // FormGroup<{who: FormControl<string|null>}>
       * let name = nnfb.group({who: new FormControl('Alex')});
       * name.reset(); console.log(name); // {who: null}
       * ```
       *
       * Because the inner control is constructed explicitly by the caller, the builder has
       * no control over how it is created, and cannot exclude the `null`.
       */
      get nonNullable() {
        const nnfb = new _FormBuilder();
        nnfb.useNonNullable = true;
        return nnfb;
      }
      group(controls, options = null) {
        const reducedControls = this._reduceControls(controls);
        let newOptions = {};
        if (isAbstractControlOptions(options)) {
          newOptions = options;
        } else if (options !== null) {
          newOptions.validators = options.validator;
          newOptions.asyncValidators = options.asyncValidator;
        }
        return new FormGroup(reducedControls, newOptions);
      }
      /**
       * @description
       * Constructs a new `FormRecord` instance. Accepts a single generic argument, which is an object
       * containing all the keys and corresponding inner control types.
       *
       * @param controls A collection of child controls. The key for each child is the name
       * under which it is registered.
       *
       * @param options Configuration options object for the `FormRecord`. The object should have the
       * `AbstractControlOptions` type and might contain the following fields:
       * * `validators`: A synchronous validator function, or an array of validator functions.
       * * `asyncValidators`: A single async validator or array of async validator functions.
       * * `updateOn`: The event upon which the control should be updated (options: 'change' | 'blur'
       * | submit').
       */
      record(controls, options = null) {
        const reducedControls = this._reduceControls(controls);
        return new FormRecord(reducedControls, options);
      }
      /**
       * @description
       * Constructs a new `FormControl` with the given state, validators and options. Sets
       * `{nonNullable: true}` in the options to get a non-nullable control. Otherwise, the
       * control will be nullable. Accepts a single generic argument, which is the type  of the
       * control's value.
       *
       * @param formState Initializes the control with an initial state value, or
       * with an object that contains both a value and a disabled status.
       *
       * @param validatorOrOpts A synchronous validator function, or an array of
       * such functions, or a `FormControlOptions` object that contains
       * validation functions and a validation trigger.
       *
       * @param asyncValidator A single async validator or array of async validator
       * functions.
       *
       * @usageNotes
       *
       * ### Initialize a control as disabled
       *
       * The following example returns a control with an initial value in a disabled state.
       *
       * {@example forms/ts/formBuilder/form_builder_example.ts region='disabled-control'}
       */
      control(formState, validatorOrOpts, asyncValidator) {
        let newOptions = {};
        if (!this.useNonNullable) {
          return new FormControl(formState, validatorOrOpts, asyncValidator);
        }
        if (isAbstractControlOptions(validatorOrOpts)) {
          newOptions = validatorOrOpts;
        } else {
          newOptions.validators = validatorOrOpts;
          newOptions.asyncValidators = asyncValidator;
        }
        return new FormControl(formState, __spreadProps(__spreadValues({}, newOptions), { nonNullable: true }));
      }
      /**
       * Constructs a new `FormArray` from the given array of configurations,
       * validators and options. Accepts a single generic argument, which is the type of each control
       * inside the array.
       *
       * @param controls An array of child controls or control configs. Each child control is given an
       *     index when it is registered.
       *
       * @param validatorOrOpts A synchronous validator function, or an array of such functions, or an
       *     `AbstractControlOptions` object that contains
       * validation functions and a validation trigger.
       *
       * @param asyncValidator A single async validator or array of async validator functions.
       */
      array(controls, validatorOrOpts, asyncValidator) {
        const createdControls = controls.map((c) => this._createControl(c));
        return new FormArray(createdControls, validatorOrOpts, asyncValidator);
      }
      /** @internal */
      _reduceControls(controls) {
        const createdControls = {};
        Object.keys(controls).forEach((controlName) => {
          createdControls[controlName] = this._createControl(controls[controlName]);
        });
        return createdControls;
      }
      /** @internal */
      _createControl(controls) {
        if (controls instanceof FormControl) {
          return controls;
        } else if (controls instanceof AbstractControl) {
          return controls;
        } else if (Array.isArray(controls)) {
          const value = controls[0];
          const validator = controls.length > 1 ? controls[1] : null;
          const asyncValidator = controls.length > 2 ? controls[2] : null;
          return this.control(value, validator, asyncValidator);
        } else {
          return this.control(controls);
        }
      }
      static \u0275fac = \u0275\u0275ngDeclareFactory({ minVersion: "12.0.0", version: "20.3.29", ngImport: core_exports, type: _FormBuilder, deps: [], target: FactoryTarget.Injectable });
      static \u0275prov = \u0275\u0275ngDeclareInjectable({ minVersion: "12.0.0", version: "20.3.29", ngImport: core_exports, type: _FormBuilder, providedIn: "root" });
    };
    \u0275\u0275ngDeclareClassMetadata({ minVersion: "12.0.0", version: "20.3.29", ngImport: core_exports, type: FormBuilder, decorators: [{
      type: Injectable,
      args: [{ providedIn: "root" }]
    }] });
    NonNullableFormBuilder = class _NonNullableFormBuilder {
      static \u0275fac = \u0275\u0275ngDeclareFactory({ minVersion: "12.0.0", version: "20.3.29", ngImport: core_exports, type: _NonNullableFormBuilder, deps: [], target: FactoryTarget.Injectable });
      static \u0275prov = \u0275\u0275ngDeclareInjectable({ minVersion: "12.0.0", version: "20.3.29", ngImport: core_exports, type: _NonNullableFormBuilder, providedIn: "root", useFactory: () => inject(FormBuilder).nonNullable });
    };
    \u0275\u0275ngDeclareClassMetadata({ minVersion: "12.0.0", version: "20.3.29", ngImport: core_exports, type: NonNullableFormBuilder, decorators: [{
      type: Injectable,
      args: [{
        providedIn: "root",
        useFactory: () => inject(FormBuilder).nonNullable
      }]
    }] });
    UntypedFormBuilder = class _UntypedFormBuilder extends FormBuilder {
      group(controlsConfig, options = null) {
        return super.group(controlsConfig, options);
      }
      /**
       * Like `FormBuilder#control`, except the resulting control is untyped.
       */
      control(formState, validatorOrOpts, asyncValidator) {
        return super.control(formState, validatorOrOpts, asyncValidator);
      }
      /**
       * Like `FormBuilder#array`, except the resulting array is untyped.
       */
      array(controlsConfig, validatorOrOpts, asyncValidator) {
        return super.array(controlsConfig, validatorOrOpts, asyncValidator);
      }
      static \u0275fac = \u0275\u0275ngDeclareFactory({ minVersion: "12.0.0", version: "20.3.29", ngImport: core_exports, type: _UntypedFormBuilder, deps: null, target: FactoryTarget.Injectable });
      static \u0275prov = \u0275\u0275ngDeclareInjectable({ minVersion: "12.0.0", version: "20.3.29", ngImport: core_exports, type: _UntypedFormBuilder, providedIn: "root" });
    };
    \u0275\u0275ngDeclareClassMetadata({ minVersion: "12.0.0", version: "20.3.29", ngImport: core_exports, type: UntypedFormBuilder, decorators: [{
      type: Injectable,
      args: [{ providedIn: "root" }]
    }] });
    FormsModule = class _FormsModule {
      /**
       * @description
       * Provides options for configuring the forms module.
       *
       * @param opts An object of configuration options
       * * `callSetDisabledState` Configures whether to `always` call `setDisabledState`, which is more
       * correct, or to only call it `whenDisabled`, which is the legacy behavior.
       */
      static withConfig(opts) {
        return {
          ngModule: _FormsModule,
          providers: [
            {
              provide: CALL_SET_DISABLED_STATE,
              useValue: opts.callSetDisabledState ?? setDisabledStateDefault
            }
          ]
        };
      }
      static \u0275fac = \u0275\u0275ngDeclareFactory({ minVersion: "12.0.0", version: "20.3.29", ngImport: core_exports, type: _FormsModule, deps: [], target: FactoryTarget.NgModule });
      static \u0275mod = \u0275\u0275ngDeclareNgModule({ minVersion: "14.0.0", version: "20.3.29", ngImport: core_exports, type: _FormsModule, declarations: [NgModel, NgModelGroup, NgForm], exports: [\u0275InternalFormsSharedModule, NgModel, NgModelGroup, NgForm] });
      static \u0275inj = \u0275\u0275ngDeclareInjector({ minVersion: "12.0.0", version: "20.3.29", ngImport: core_exports, type: _FormsModule, imports: [\u0275InternalFormsSharedModule] });
    };
    \u0275\u0275ngDeclareClassMetadata({ minVersion: "12.0.0", version: "20.3.29", ngImport: core_exports, type: FormsModule, decorators: [{
      type: NgModule,
      args: [{
        declarations: TEMPLATE_DRIVEN_DIRECTIVES,
        exports: [\u0275InternalFormsSharedModule, TEMPLATE_DRIVEN_DIRECTIVES]
      }]
    }] });
    ReactiveFormsModule = class _ReactiveFormsModule {
      /**
       * @description
       * Provides options for configuring the reactive forms module.
       *
       * @param opts An object of configuration options
       * * `warnOnNgModelWithFormControl` Configures when to emit a warning when an `ngModel`
       * binding is used with reactive form directives.
       * * `callSetDisabledState` Configures whether to `always` call `setDisabledState`, which is more
       * correct, or to only call it `whenDisabled`, which is the legacy behavior.
       */
      static withConfig(opts) {
        return {
          ngModule: _ReactiveFormsModule,
          providers: [
            {
              provide: NG_MODEL_WITH_FORM_CONTROL_WARNING,
              useValue: opts.warnOnNgModelWithFormControl ?? "always"
            },
            {
              provide: CALL_SET_DISABLED_STATE,
              useValue: opts.callSetDisabledState ?? setDisabledStateDefault
            }
          ]
        };
      }
      static \u0275fac = \u0275\u0275ngDeclareFactory({ minVersion: "12.0.0", version: "20.3.29", ngImport: core_exports, type: _ReactiveFormsModule, deps: [], target: FactoryTarget.NgModule });
      static \u0275mod = \u0275\u0275ngDeclareNgModule({ minVersion: "14.0.0", version: "20.3.29", ngImport: core_exports, type: _ReactiveFormsModule, declarations: [FormControlDirective, FormGroupDirective, FormControlName, FormGroupName, FormArrayName], exports: [\u0275InternalFormsSharedModule, FormControlDirective, FormGroupDirective, FormControlName, FormGroupName, FormArrayName] });
      static \u0275inj = \u0275\u0275ngDeclareInjector({ minVersion: "12.0.0", version: "20.3.29", ngImport: core_exports, type: _ReactiveFormsModule, imports: [\u0275InternalFormsSharedModule] });
    };
    \u0275\u0275ngDeclareClassMetadata({ minVersion: "12.0.0", version: "20.3.29", ngImport: core_exports, type: ReactiveFormsModule, decorators: [{
      type: NgModule,
      args: [{
        declarations: [REACTIVE_DRIVEN_DIRECTIVES],
        exports: [\u0275InternalFormsSharedModule, REACTIVE_DRIVEN_DIRECTIVES]
      }]
    }] });
  }
});

// src/environments/environment.ts
var environment;
var init_environment = __esm({
  "src/environments/environment.ts"() {
    "use strict";
    environment = {
      production: false,
      apiBaseUrl: "/identipat-ia"
    };
  }
});

// src/app/services/user.service.ts
var UserService;
var init_user_service = __esm({
  "src/app/services/user.service.ts"() {
    "use strict";
    init_tslib_es6();
    init_core();
    init_http();
    init_environment();
    UserService = class UserService2 {
      http = inject(HttpClient);
      endpoint = `${environment.apiBaseUrl}/users`;
      adminTokenKey = "identipat.admin.accessToken";
      adminDoiKey = "identipat.admin.doi";
      adminDoiTypeKey = "identipat.admin.doiType";
      accessToken = "";
      constructor() {
        this.accessToken = this.sessionStorage()?.getItem(this.adminTokenKey) ?? "";
      }
      identify(doi, doiType) {
        return this.http.post(`${this.endpoint}/identify`, { doi, doiType });
      }
      login(doi, password) {
        return this.http.post(`${this.endpoint}/login`, { doi, password });
      }
      setAccessToken(accessToken, doi, doiType) {
        this.accessToken = accessToken;
        const storage = this.sessionStorage();
        storage?.setItem(this.adminTokenKey, accessToken);
        storage?.setItem(this.adminDoiKey, doi);
        storage?.setItem(this.adminDoiTypeKey, doiType);
      }
      clearAccessToken() {
        this.accessToken = "";
        const storage = this.sessionStorage();
        storage?.removeItem(this.adminTokenKey);
        storage?.removeItem(this.adminDoiKey);
        storage?.removeItem(this.adminDoiTypeKey);
      }
      getAccessToken() {
        return this.accessToken;
      }
      getAdminIdentity() {
        const storage = this.sessionStorage();
        const doi = storage?.getItem(this.adminDoiKey);
        const doiType = storage?.getItem(this.adminDoiTypeKey);
        return this.accessToken && doi && doiType ? { doi, doiType } : null;
      }
      sessionStorage() {
        if (typeof window === "undefined") {
          return null;
        }
        try {
          return window.sessionStorage;
        } catch {
          return null;
        }
      }
      get authenticatedOptions() {
        return { headers: { Authorization: `Bearer ${this.accessToken}` } };
      }
      findAll(page = 0, size = 20, search = "", status = "", userType = "") {
        const params = { page, size };
        if (search.trim()) {
          params["search"] = search.trim();
        }
        if (status) {
          params["status"] = status;
        }
        if (userType) {
          params["userType"] = userType;
        }
        return this.http.get(this.endpoint, __spreadProps(__spreadValues({}, this.authenticatedOptions), {
          params
        }));
      }
      exportAll(search = "", status = "", userType = "") {
        const params = {};
        if (search.trim()) {
          params["search"] = search.trim();
        }
        if (status) {
          params["status"] = status;
        }
        if (userType) {
          params["userType"] = userType;
        }
        return this.http.get(`${this.endpoint}/export`, __spreadProps(__spreadValues({}, this.authenticatedOptions), {
          params,
          observe: "response",
          responseType: "blob"
        }));
      }
      findById(userId) {
        return this.http.get(`${this.endpoint}/${userId}`, this.authenticatedOptions);
      }
      findByDoi(doi) {
        return this.http.get(`${this.endpoint}/doi/${encodeURIComponent(doi)}`, this.authenticatedOptions);
      }
      create(user) {
        return this.http.post(this.endpoint, user);
      }
      update(userId, user) {
        return this.http.put(`${this.endpoint}/admin/${userId}`, user, this.authenticatedOptions);
      }
      delete(userId) {
        return this.http.delete(`${this.endpoint}/${userId}`, this.authenticatedOptions);
      }
      updateProfile(userId, user) {
        return this.http.put(`${this.endpoint}/${userId}`, user, this.authenticatedOptions);
      }
      static ctorParameters = () => [];
    };
    UserService = __decorate([
      Injectable({
        providedIn: "root"
      })
    ], UserService);
  }
});

// src/app/services/admin-report-disclaimer.service.ts
var AdminReportDisclaimerService;
var init_admin_report_disclaimer_service = __esm({
  "src/app/services/admin-report-disclaimer.service.ts"() {
    "use strict";
    init_tslib_es6();
    init_http();
    init_core();
    init_environment();
    AdminReportDisclaimerService = class AdminReportDisclaimerService2 {
      http = inject(HttpClient);
      endpoint = `${environment.apiBaseUrl}/admin/report-disclaimers`;
      getAll(accessToken) {
        return this.http.get(this.endpoint, {
          headers: { Authorization: `Bearer ${accessToken}` }
        });
      }
      createDraft(accessToken, title, content) {
        return this.http.post(this.endpoint, { title, content }, {
          headers: { Authorization: `Bearer ${accessToken}` }
        });
      }
      activate(accessToken, disclaimerId) {
        return this.http.post(`${this.endpoint}/${encodeURIComponent(disclaimerId)}/activate`, null, { headers: { Authorization: `Bearer ${accessToken}` } });
      }
    };
    AdminReportDisclaimerService = __decorate([
      Injectable({
        providedIn: "root"
      })
    ], AdminReportDisclaimerService);
  }
});

// src/app/services/standard-analysis.service.ts
var StandardAnalysisService;
var init_standard_analysis_service = __esm({
  "src/app/services/standard-analysis.service.ts"() {
    "use strict";
    init_tslib_es6();
    init_http();
    init_core();
    init_esm();
    init_environment();
    StandardAnalysisService = class StandardAnalysisService2 {
      http = inject(HttpClient);
      endpoint = environment.apiBaseUrl;
      createTextAnalysis(doi, doiType, description, accessToken = "") {
        if (accessToken) {
          return this.http.post(`${this.endpoint}/analyses/text`, { description }, { headers: { Authorization: `Bearer ${accessToken}` } });
        }
        return this.http.get(`${this.endpoint}/standard-session/csrf`, {
          withCredentials: true
        }).pipe(switchMap((csrf) => this.http.post(`${this.endpoint}/standard-sessions`, { doi, doiType }, { headers: this.csrfHeaders(csrf), withCredentials: true })), switchMap((session) => {
          return this.http.post(`${this.endpoint}/standard-session/consent`, { consentVersion: session.requiredConsentVersion, decision: "ACCEPTED" }, { headers: this.csrfHeadersFromToken(), withCredentials: true }).pipe(switchMap(() => of(session)));
        }), switchMap(() => this.http.post(`${this.endpoint}/analyses/text`, { description }, { headers: this.csrfHeadersFromToken(), withCredentials: true })));
      }
      createPdfAnalysis(doi, doiType, file, accessToken = "") {
        const body = new FormData();
        body.append("file", file, file.name);
        if (accessToken) {
          return this.http.post(`${this.endpoint}/analyses/pdf`, body, { headers: { Authorization: `Bearer ${accessToken}` } });
        }
        return this.http.get(`${this.endpoint}/standard-session/csrf`, {
          withCredentials: true
        }).pipe(switchMap((csrf) => this.http.post(`${this.endpoint}/standard-sessions`, { doi, doiType }, { headers: this.csrfHeaders(csrf), withCredentials: true })), switchMap((session) => this.http.post(`${this.endpoint}/standard-session/consent`, { consentVersion: session.requiredConsentVersion, decision: "ACCEPTED" }, { headers: this.csrfHeadersFromToken(), withCredentials: true }).pipe(switchMap(() => of(session)))), switchMap(() => this.http.post(`${this.endpoint}/analyses/pdf`, body, { headers: this.csrfHeadersFromToken(), withCredentials: true })));
      }
      createAudioAnalysis(doi, doiType, file, accessToken = "") {
        const body = new FormData();
        body.append("file", file, file.name);
        if (accessToken) {
          return this.http.post(`${this.endpoint}/analyses/audio`, body, { headers: { Authorization: `Bearer ${accessToken}` } });
        }
        return this.http.get(`${this.endpoint}/standard-session/csrf`, {
          withCredentials: true
        }).pipe(switchMap((csrf) => this.http.post(`${this.endpoint}/standard-sessions`, { doi, doiType }, { headers: this.csrfHeaders(csrf), withCredentials: true })), switchMap((session) => this.http.post(`${this.endpoint}/standard-session/consent`, { consentVersion: session.requiredConsentVersion, decision: "ACCEPTED" }, { headers: this.csrfHeadersFromToken(), withCredentials: true }).pipe(switchMap(() => of(session)))), switchMap(() => this.http.post(`${this.endpoint}/analyses/audio`, body, { headers: this.csrfHeadersFromToken(), withCredentials: true })));
      }
      getAnalysis(analysisId, accessToken = "") {
        return this.http.get(`${this.endpoint}/analyses/${analysisId}`, accessToken ? { headers: { Authorization: `Bearer ${accessToken}` } } : { withCredentials: true });
      }
      getReportPdf(analysisId, accessToken = "") {
        const url = `${this.endpoint}/analyses/${encodeURIComponent(analysisId)}/report.pdf`;
        return this.http.get(url, accessToken ? { headers: { Authorization: `Bearer ${accessToken}` }, responseType: "blob" } : { withCredentials: true, responseType: "blob" });
      }
      closeStandardSession() {
        return this.http.get(`${this.endpoint}/standard-session/csrf`, {
          withCredentials: true
        }).pipe(switchMap((csrf) => this.http.delete(`${this.endpoint}/standard-session`, {
          headers: { [csrf.headerName]: csrf.token },
          withCredentials: true
        }).pipe(catchError((error) => error instanceof HttpErrorResponse && error.status === 401 ? of(void 0) : throwError(() => error)))));
      }
      csrfToken = "";
      csrfHeaders(csrf) {
        this.csrfToken = csrf.token;
        return new HttpHeaders({ [csrf.headerName]: csrf.token });
      }
      csrfHeadersFromToken() {
        return new HttpHeaders({ "X-XSRF-TOKEN": this.csrfToken });
      }
    };
    StandardAnalysisService = __decorate([
      Injectable({
        providedIn: "root"
      })
    ], StandardAnalysisService);
  }
});

// src/app/app.ts
var adminSettingsReloadKey, App;
var init_app3 = __esm({
  "src/app/app.ts"() {
    "use strict";
    init_tslib_es6();
    init_app();
    init_app2();
    init_common();
    init_common();
    init_core();
    init_forms();
    init_user_service();
    init_admin_report_disclaimer_service();
    init_standard_analysis_service();
    adminSettingsReloadKey = "identipat.admin.reloadSection";
    App = class App2 {
      fb = inject(FormBuilder);
      document = inject(DOCUMENT);
      userService = inject(UserService);
      adminReportDisclaimerService = inject(AdminReportDisclaimerService);
      standardAnalysisService = inject(StandardAnalysisService);
      defaultAdminPassword = "admin";
      users = signal([]);
      userPage = signal(0);
      userPageSize = signal(20);
      userSearch = signal("");
      userStatus = signal("");
      userType = signal("");
      userTotalPages = signal(0);
      userTotalElements = signal(0);
      downloadingUsers = signal(false);
      analyses = signal([]);
      latestReportDisclaimer = signal(null);
      disclaimerTitle = signal("");
      savedDisclaimerTitle = signal("");
      disclaimerContent = signal("");
      savedDisclaimerContent = signal("");
      hasUnsavedDisclaimerChanges = computed(() => this.latestReportDisclaimer() !== null && (this.disclaimerTitle() !== this.savedDisclaimerTitle() || this.disclaimerContent() !== this.savedDisclaimerContent()));
      settingsLoading = signal(false);
      settingsError = signal("");
      settingsLoaded = signal(false);
      savingDisclaimerDraft = signal(false);
      disclaimerDraftMessage = signal("");
      disclaimerDraftError = signal("");
      savedDisclaimerDraft = signal(null);
      activatingDisclaimer = signal(false);
      disclaimerActivationMessage = signal("");
      disclaimerActivationError = signal("");
      showPublishDisclaimerModal = signal(false);
      loading = signal(false);
      analysesLoading = signal(false);
      saving = signal(false);
      registrationModal = signal(null);
      errorMessage = signal("");
      editErrorMessage = signal("");
      analysesError = signal("");
      editingUserId = signal(null);
      editingUserType = signal("STANDARD");
      editingOriginalUserType = signal("STANDARD");
      showAdminPassword = signal(false);
      showEditModal = signal(false);
      adminSection = signal("users");
      view = signal("home");
      checkingDocument = signal(false);
      standardAccessPending = signal(false);
      authenticating = signal(false);
      adminAuthenticated = signal(false);
      navigationPending = signal(null);
      exitError = signal("");
      accessMessage = signal("");
      savingProfile = signal(false);
      profileMessage = signal("");
      savingPassword = signal(false);
      passwordMessage = signal("");
      passwordSuccessModal = signal(false);
      adminProfile = signal(null);
      submittingAnalysis = signal(false);
      analysisMessage = signal("");
      analysisResult = signal(null);
      downloadingReportPdf = signal(false);
      reportPdfError = signal("");
      selectedPdf = signal(null);
      selectedAudio = signal(null);
      recordingAudio = signal(false);
      recordingMilliseconds = signal(0);
      mediaRecorder = null;
      recordingStream = null;
      recordingChunks = [];
      recordingTimer = null;
      recordingStartedAt = 0;
      constructor() {
        if (this.document.location.pathname === "/terminos-y-privacidad") {
          this.view.set("legal");
        }
        this.accessForm.controls.doiType.valueChanges.subscribe(() => this.accessForm.controls.doi.updateValueAndValidity());
        this.userForm.controls.doiType.valueChanges.subscribe(() => this.userForm.controls.doi.updateValueAndValidity());
        const adminIdentity = this.userService.getAdminIdentity();
        if (adminIdentity) {
          this.accessForm.setValue(adminIdentity);
          this.adminAuthenticated.set(true);
          this.adminSection.set(this.consumeAdminSettingsReloadTarget());
          this.view.set("admin");
          this.userService.findByDoi(adminIdentity.doi).subscribe({
            next: (admin) => {
              if (admin.userType?.toUpperCase() !== "ADMIN" || admin.doiType !== adminIdentity.doiType) {
                this.logout();
                return;
              }
              this.adminProfile.set(admin);
              if (this.adminSection() === "profile") {
                this.loadProfileForm();
              }
              this.loadUsers(0);
              if (this.adminSection() === "settings") {
                this.loadReportDisclaimers();
              }
            },
            error: () => this.logout()
          });
        }
      }
      currentAdmin = computed(() => {
        const authenticatedAdmin = this.adminProfile();
        if (authenticatedAdmin) {
          return authenticatedAdmin;
        }
        const { doi, doiType } = this.accessForm.getRawValue();
        return this.users().find((user) => user.userType?.toUpperCase() === "ADMIN" && user.doi === doi && user.doiType === doiType) ?? null;
      });
      accessForm = this.fb.nonNullable.group({
        doiType: ["DNI", [Validators.required]],
        doi: ["", [Validators.required, this.documentValidator.bind(this)]]
      });
      loginForm = this.fb.nonNullable.group({
        password: ["", [Validators.required]]
      });
      userForm = this.fb.nonNullable.group({
        firstName: ["", [Validators.required, Validators.maxLength(25)]],
        paternalLastName: ["", [Validators.required, Validators.maxLength(25)]],
        maternalLastName: ["", [Validators.required, Validators.maxLength(25)]],
        doi: ["", [Validators.required, this.documentValidator.bind(this)]],
        doiType: ["", [Validators.required]],
        birthDate: ["", [Validators.required, this.dateValidator]],
        gender: ["", [Validators.required]],
        email: ["", [Validators.required, Validators.email]],
        phone: ["", [Validators.required, Validators.maxLength(9), Validators.pattern(/^\d+$/)]],
        mobilePhone: ["", [Validators.required, Validators.maxLength(15), this.mobilePhoneValidator]],
        profession: ["", [Validators.required]],
        password: [""],
        termsAccepted: [false, [Validators.requiredTrue]]
      });
      profileForm = this.fb.nonNullable.group({
        firstName: ["", [Validators.required, Validators.maxLength(25)]],
        paternalLastName: ["", [Validators.required, Validators.maxLength(25)]],
        maternalLastName: ["", [Validators.required, Validators.maxLength(25)]],
        birthDate: ["", [Validators.required, this.dateValidator]],
        gender: ["", [Validators.required]],
        email: ["", [Validators.required, Validators.email]],
        phone: ["", [Validators.required, Validators.maxLength(9), Validators.pattern(/^\d+$/)]],
        mobilePhone: ["", [Validators.required, Validators.maxLength(15), this.mobilePhoneValidator]],
        profession: ["", [Validators.required]]
      });
      securityForm = this.fb.nonNullable.group({
        password: [""],
        confirmPassword: [""]
      }, { validators: this.passwordMatchValidator });
      analysisForm = this.fb.nonNullable.group({
        description: ["", [Validators.required, Validators.minLength(20), Validators.maxLength(2e4)]]
      });
      documentValidator(control) {
        if (!control.value) {
          return null;
        }
        const doiType = control.parent?.get("doiType")?.value;
        const pattern = doiType === "DNI" ? /^\d{8}$/ : /^\d{6,11}$/;
        return pattern.test(control.value) ? null : { doiFormat: true };
      }
      passwordMatchValidator(control) {
        const password = control.get("password")?.value ?? "";
        const confirmPassword = control.get("confirmPassword")?.value ?? "";
        return password === confirmPassword ? null : { passwordMismatch: true };
      }
      dateValidator(control) {
        if (!control.value) {
          return null;
        }
        return /^(?:\d{4}-\d{2}-\d{2}|\d{2}\/\d{2}\/\d{4})$/.test(control.value) ? null : { dateFormat: true };
      }
      mobilePhoneValidator(control) {
        if (!control.value) {
          return null;
        }
        const normalized = String(control.value).replace(/\s/g, "");
        const isValid = /^\d{9}$/.test(normalized) || /^\+51\d{9}$/.test(normalized);
        return isValid ? null : { mobilePhoneFormat: true };
      }
      sanitizeMobilePhone(event, control) {
        const input = event.target;
        const hasLeadingPlus = input.value.trimStart().startsWith("+");
        const sanitized = `${hasLeadingPlus ? "+" : ""}${input.value.replace(/\+/g, "").replace(/[^\d ]/g, "").trimStart()}`;
        if (input.value !== sanitized) {
          input.value = sanitized;
          control.setValue(sanitized, { emitEvent: false });
        }
      }
      sanitizePhone(event, control) {
        const input = event.target;
        const sanitized = input.value.replace(/\D/g, "").slice(0, 9);
        if (input.value !== sanitized) {
          input.value = sanitized;
          control.setValue(sanitized, { emitEvent: false });
        }
      }
      sanitizeDni(event, control) {
        const input = event.target;
        const sanitized = input.value.replace(/\D/g, "").slice(0, 8);
        if (input.value !== sanitized) {
          input.value = sanitized;
          control.setValue(sanitized, { emitEvent: false });
        }
      }
      toDateInputValue(date) {
        const match = /^(\d{2})\/(\d{2})\/(\d{4})$/.exec(date);
        return match ? `${match[3]}-${match[2]}-${match[1]}` : date.slice(0, 10);
      }
      toBackendDate(date) {
        const isoMatch = /^(\d{4})-(\d{2})-(\d{2})$/.exec(date);
        return isoMatch ? `${isoMatch[3]}/${isoMatch[2]}/${isoMatch[1]}` : date;
      }
      identifyDocument() {
        if (this.accessForm.invalid) {
          this.accessForm.markAllAsTouched();
          return;
        }
        const { doi, doiType } = this.accessForm.getRawValue();
        const accessStartedAt = Date.now();
        this.checkingDocument.set(true);
        this.standardAccessPending.set(false);
        this.accessMessage.set("");
        this.userService.identify(doi, doiType).subscribe({
          next: ({ registered, passwordRequired, active }) => {
            this.standardAccessPending.set(registered && !passwordRequired);
            this.finishAccess(accessStartedAt, () => {
              if (registered && active === false) {
                this.accessMessage.set("Usuario inactivo");
              } else if (passwordRequired) {
                this.view.set("admin-login");
                this.loginForm.reset({ password: "" });
              } else if (!registered) {
                this.accessMessage.set("Usuario no registrado");
              } else {
                this.analysisForm.reset({ description: "" });
                this.analysisMessage.set("");
                this.view.set("standard-analysis");
                this.scrollToTop();
              }
              this.standardAccessPending.set(false);
              this.checkingDocument.set(false);
            });
          },
          error: () => {
            this.finishAccess(accessStartedAt, () => {
              this.standardAccessPending.set(false);
              this.accessMessage.set("No pudimos verificar el documento. Int\xE9ntalo nuevamente.");
              this.checkingDocument.set(false);
            });
          }
        });
      }
      finishAccess(startedAt, complete) {
        const remainingMinimumTime = Math.max(0, 500 - (Date.now() - startedAt));
        setTimeout(complete, remainingMinimumTime);
      }
      scrollToTop() {
        this.document.defaultView?.scrollTo({ top: 0, left: 0, behavior: "auto" });
      }
      backHome() {
        this.view.set("home");
        this.accessMessage.set("");
        this.loginForm.reset({ password: "" });
      }
      exitSession() {
        this.accessForm.reset({ doiType: "DNI", doi: "" });
        this.analysisForm.reset({ description: "" });
        this.analysisMessage.set("");
        this.analysisResult.set(null);
        this.backHome();
      }
      exitAnalysis() {
        this.exitWithSessionClose(() => this.backHome());
      }
      exitResult() {
        this.exitWithSessionClose(() => this.exitSession());
      }
      exitWithSessionClose(navigate) {
        if (this.adminAuthenticated()) {
          this.scheduleNavigation("exit", navigate);
          return;
        }
        if (this.navigationPending()) {
          return;
        }
        this.navigationPending.set("exit");
        this.exitError.set("");
        this.standardAnalysisService.closeStandardSession().subscribe({
          next: () => {
            this.navigationPending.set(null);
            navigate();
          },
          error: () => {
            this.navigationPending.set(null);
            this.exitError.set("No se pudo cerrar la sesi\xF3n. Comprueba tu conexi\xF3n e int\xE9ntalo nuevamente.");
          }
        });
      }
      openRegistration() {
        this.cancelEdit();
        this.accessMessage.set("");
        this.view.set("registration");
      }
      startNewAnalysis() {
        this.analysisForm.reset({ description: "" });
        this.analysisMessage.set("");
        this.analysisResult.set(null);
        this.view.set("standard-analysis");
        this.scrollToTop();
      }
      cancelRegistration() {
        this.cancelEdit();
        this.backHome();
      }
      loginAdmin() {
        if (this.accessForm.invalid || this.loginForm.invalid) {
          this.accessForm.markAllAsTouched();
          this.loginForm.markAllAsTouched();
          return;
        }
        const { doi } = this.accessForm.getRawValue();
        const { password } = this.loginForm.getRawValue();
        const accessStartedAt = Date.now();
        this.authenticating.set(true);
        this.accessMessage.set("");
        this.userService.login(doi, password).subscribe({
          next: ({ accessToken }) => {
            this.finishAccess(accessStartedAt, () => {
              const { doiType } = this.accessForm.getRawValue();
              this.userService.setAccessToken(accessToken, doi, doiType);
              this.authenticating.set(false);
              this.adminAuthenticated.set(true);
              this.analysisForm.reset({ description: "" });
              this.analysisMessage.set("");
              this.view.set("standard-analysis");
              this.scrollToTop();
            });
          },
          error: () => {
            this.finishAccess(accessStartedAt, () => {
              this.accessMessage.set("Documento o contrase\xF1a incorrectos.");
              this.authenticating.set(false);
            });
          }
        });
      }
      logout() {
        this.userService.clearAccessToken();
        this.adminAuthenticated.set(false);
        this.adminProfile.set(null);
        this.users.set([]);
        this.analyses.set([]);
        this.cancelEdit();
        this.adminSection.set("users");
        this.profileMessage.set("");
        this.backHome();
      }
      openAdminPanel() {
        if (this.navigationPending()) {
          return;
        }
        this.navigationPending.set("admin");
        window.setTimeout(() => {
          this.navigationPending.set(null);
          this.adminSection.set("users");
          this.view.set("admin");
          this.loadUsers(0);
          this.loadAdminProfile();
          this.scrollToTop();
        }, 500);
      }
      openPredictions() {
        this.scheduleNavigation("predictions", () => this.startNewAnalysis());
      }
      scheduleNavigation(destination, navigate) {
        if (this.navigationPending()) {
          return;
        }
        this.navigationPending.set(destination);
        window.setTimeout(() => {
          this.navigationPending.set(null);
          navigate();
        }, 500);
      }
      setAdminSection(section) {
        this.adminSection.set(section);
        if (section === "predictions") {
          this.loadAnalyses();
        } else if (section === "profile") {
          this.loadProfileForm();
        } else if (section === "settings" && !this.settingsLoaded() && !this.settingsLoading()) {
          this.loadReportDisclaimers();
        }
      }
      loadReportDisclaimers() {
        if (this.hasUnsavedDisclaimerChanges() || this.savingDisclaimerDraft() || this.activatingDisclaimer()) {
          return;
        }
        this.settingsLoading.set(true);
        this.settingsError.set("");
        this.adminReportDisclaimerService.getAll(this.userService.getAccessToken()).subscribe({
          next: (disclaimers) => {
            const latestDisclaimer = disclaimers.reduce((latest, disclaimer) => !latest || disclaimer.version > latest.version ? disclaimer : latest, null);
            this.latestReportDisclaimer.set(latestDisclaimer);
            this.disclaimerTitle.set(latestDisclaimer?.title ?? "");
            this.savedDisclaimerTitle.set(latestDisclaimer?.title ?? "");
            this.disclaimerContent.set(latestDisclaimer?.content ?? "");
            this.savedDisclaimerContent.set(latestDisclaimer?.content ?? "");
            this.savedDisclaimerDraft.set(latestDisclaimer?.status === "DRAFT" ? latestDisclaimer : null);
            this.disclaimerDraftMessage.set("");
            this.disclaimerDraftError.set("");
            this.disclaimerActivationMessage.set("");
            this.disclaimerActivationError.set("");
            this.settingsLoaded.set(true);
            this.settingsLoading.set(false);
          },
          error: () => {
            this.settingsError.set("No se pudieron cargar los disclaimers. Int\xE9ntalo nuevamente.");
            this.settingsLoading.set(false);
          }
        });
      }
      updateDisclaimerTitle(event) {
        this.disclaimerTitle.set(event.target.value);
        this.disclaimerDraftMessage.set("");
        this.disclaimerDraftError.set("");
        this.disclaimerActivationMessage.set("");
        this.disclaimerActivationError.set("");
      }
      updateDisclaimerContent(event) {
        this.disclaimerContent.set(event.target.value);
        this.disclaimerDraftMessage.set("");
        this.disclaimerDraftError.set("");
        this.disclaimerActivationMessage.set("");
        this.disclaimerActivationError.set("");
      }
      saveDisclaimerDraft() {
        const latestDisclaimer = this.latestReportDisclaimer();
        if (!latestDisclaimer || !this.hasUnsavedDisclaimerChanges() || this.savingDisclaimerDraft()) {
          return;
        }
        this.savingDisclaimerDraft.set(true);
        this.disclaimerDraftMessage.set("");
        this.disclaimerDraftError.set("");
        this.adminReportDisclaimerService.createDraft(this.userService.getAccessToken(), this.disclaimerTitle(), this.disclaimerContent()).subscribe({
          next: (draft) => {
            this.latestReportDisclaimer.set(draft);
            this.disclaimerTitle.set(draft.title);
            this.savedDisclaimerTitle.set(draft.title);
            this.disclaimerContent.set(draft.content);
            this.savedDisclaimerContent.set(draft.content);
            this.savedDisclaimerDraft.set(draft);
            this.disclaimerDraftMessage.set("Borrador guardado correctamente.");
            this.savingDisclaimerDraft.set(false);
          },
          error: () => {
            this.disclaimerDraftError.set("No se pudo guardar el borrador. Revisa el contenido e int\xE9ntalo nuevamente.");
            this.savingDisclaimerDraft.set(false);
          }
        });
      }
      publishDisclaimerDraft() {
        const draft = this.savedDisclaimerDraft();
        if (!this.showPublishDisclaimerModal() || !draft || this.hasUnsavedDisclaimerChanges() || this.savingDisclaimerDraft() || this.activatingDisclaimer()) {
          return;
        }
        this.activatingDisclaimer.set(true);
        this.disclaimerActivationMessage.set("");
        this.disclaimerActivationError.set("");
        this.adminReportDisclaimerService.activate(this.userService.getAccessToken(), draft.id).subscribe({
          next: (activeDisclaimer) => {
            this.showPublishDisclaimerModal.set(false);
            this.latestReportDisclaimer.set(activeDisclaimer);
            this.disclaimerTitle.set(activeDisclaimer.title);
            this.savedDisclaimerTitle.set(activeDisclaimer.title);
            this.disclaimerContent.set(activeDisclaimer.content);
            this.savedDisclaimerContent.set(activeDisclaimer.content);
            this.savedDisclaimerDraft.set(null);
            this.disclaimerDraftMessage.set("");
            this.disclaimerActivationMessage.set("Disclaimer publicado correctamente.");
            this.activatingDisclaimer.set(false);
            this.scheduleAdminSettingsReload();
          },
          error: () => {
            this.disclaimerActivationError.set("No se pudo publicar el borrador. Int\xE9ntalo nuevamente.");
            this.activatingDisclaimer.set(false);
          }
        });
      }
      openPublishDisclaimerModal() {
        if (!this.savedDisclaimerDraft() || this.hasUnsavedDisclaimerChanges() || this.savingDisclaimerDraft() || this.activatingDisclaimer()) {
          return;
        }
        this.disclaimerActivationError.set("");
        this.showPublishDisclaimerModal.set(true);
      }
      closePublishDisclaimerModal() {
        if (!this.activatingDisclaimer()) {
          this.showPublishDisclaimerModal.set(false);
        }
      }
      consumeAdminSettingsReloadTarget() {
        try {
          const storage = this.document.defaultView?.sessionStorage;
          const section = storage?.getItem(adminSettingsReloadKey);
          storage?.removeItem(adminSettingsReloadKey);
          return section === "settings" ? "settings" : "users";
        } catch {
          return "users";
        }
      }
      scheduleAdminSettingsReload() {
        const browserWindow = this.document.defaultView;
        if (!browserWindow) {
          return;
        }
        browserWindow.setTimeout(() => {
          if (!this.adminAuthenticated()) {
            return;
          }
          try {
            browserWindow.sessionStorage.setItem(adminSettingsReloadKey, "settings");
          } catch {
            return;
          }
          browserWindow.location.reload();
        }, 2e3);
      }
      loadAdminProfile() {
        const { doi, doiType } = this.accessForm.getRawValue();
        this.userService.findByDoi(doi).subscribe({
          next: (admin) => {
            if (admin.userType?.toUpperCase() !== "ADMIN" || admin.doiType !== doiType) {
              return;
            }
            this.adminProfile.set(admin);
            if (this.adminSection() === "profile") {
              this.loadProfileForm();
            }
          }
        });
      }
      loadAnalyses() {
        this.analysesError.set("");
      }
      downloadAnalysesCsv() {
        const headers = ["Fecha", "Usuario", "Estado", "Predicci\xF3n"];
        const rows = this.analyses().map((analysis) => [
          analysis.requestedAt,
          `${analysis.firstName} ${analysis.paternalLastName} ${analysis.maternalLastName} (Usuario #${analysis.userId})`,
          this.analysisStatusLabel(analysis.status),
          analysis.summary || "Resultado a\xFAn no disponible."
        ]);
        const csv = "\uFEFF" + [headers, ...rows].map((row) => row.map((value) => this.csvCell(value)).join(",")).join("\r\n");
        const link = this.document.createElement("a");
        const url = URL.createObjectURL(new Blob([csv], { type: "text/csv;charset=utf-8" }));
        link.href = url;
        link.download = `predicciones-${(/* @__PURE__ */ new Date()).toISOString().slice(0, 10)}.csv`;
        link.click();
        URL.revokeObjectURL(url);
      }
      downloadUsersCsv() {
        this.downloadingUsers.set(true);
        this.errorMessage.set("");
        this.userService.exportAll(this.userSearch(), this.userStatus(), this.userType()).subscribe({
          next: (response) => {
            const blob = response.body;
            if (!blob) {
              this.errorMessage.set("No se pudo descargar el archivo de usuarios.");
              this.downloadingUsers.set(false);
              return;
            }
            const contentDisposition = response.headers.get("Content-Disposition");
            const filename = contentDisposition?.match(/filename="?([^";]+)"?/i)?.[1] ?? `usuarios-${(/* @__PURE__ */ new Date()).toISOString().slice(0, 10)}.csv`;
            const url = URL.createObjectURL(blob);
            const link = this.document.createElement("a");
            link.href = url;
            link.download = filename;
            link.click();
            window.setTimeout(() => URL.revokeObjectURL(url));
            this.downloadingUsers.set(false);
          },
          error: () => {
            this.errorMessage.set("No se pudieron exportar los usuarios.");
            this.downloadingUsers.set(false);
          }
        });
      }
      csvCell(value) {
        const safeValue = /^[=+\-@]/.test(value) ? `'${value}` : value;
        return `"${safeValue.replace(/"/g, '""')}"`;
      }
      analysisStatusLabel(status) {
        const labels = {
          RECEIVED: "Recibida",
          PREPROCESSING: "Procesando",
          ANALYZING: "Analizando",
          COMPLETED: "Completada",
          FAILED: "Fallida"
        };
        return labels[status];
      }
      loadUsers(page = this.userPage()) {
        this.loading.set(true);
        this.errorMessage.set("");
        this.userService.findAll(page, this.userPageSize(), this.userSearch(), this.userStatus(), this.userType()).subscribe({
          next: (response) => {
            this.users.set(response.content);
            this.userPage.set(response.page);
            this.userTotalPages.set(response.totalPages);
            this.userTotalElements.set(response.totalElements);
            this.loading.set(false);
          },
          error: () => {
            this.errorMessage.set("No se pudieron cargar los usuarios.");
            this.loading.set(false);
          }
        });
      }
      changeUserPage(page) {
        if (this.loading() || page < 0 || page >= this.userTotalPages()) {
          return;
        }
        this.loadUsers(page);
      }
      changeUserPageSize(event) {
        const size = Number(event.target.value);
        if (![10, 20, 50, 100].includes(size)) {
          return;
        }
        this.userPageSize.set(size);
        this.loadUsers(0);
      }
      updateUserSearch(event) {
        this.userSearch.set(event.target.value);
      }
      searchUsers() {
        this.loadUsers(0);
      }
      changeEditingUserType(event) {
        const userType = event.target.value;
        if (!["STANDARD", "ADMIN"].includes(userType)) {
          return;
        }
        const wasAdmin = this.editingUserType() === "ADMIN";
        this.editingUserType.set(userType);
        const passwordControl = this.userForm.controls.password;
        if (userType === "ADMIN") {
          const passwordRequired = this.editingOriginalUserType() !== "ADMIN";
          if (!wasAdmin && passwordRequired) {
            passwordControl.setValue(this.defaultAdminPassword);
          }
          if (passwordRequired) {
            passwordControl.setValidators([Validators.required]);
          } else {
            passwordControl.clearValidators();
          }
        } else {
          passwordControl.setValue("");
          passwordControl.clearValidators();
        }
        passwordControl.updateValueAndValidity({ emitEvent: false });
        if (this.editingOriginalUserType() === "ADMIN" && userType === "ADMIN") {
          this.userForm.controls.doi.disable({ emitEvent: false });
          this.userForm.controls.doiType.disable({ emitEvent: false });
        } else {
          this.userForm.controls.doi.enable({ emitEvent: false });
          this.userForm.controls.doiType.enable({ emitEvent: false });
        }
      }
      toggleAdminPasswordVisibility() {
        this.showAdminPassword.update((visible) => !visible);
      }
      changeUserStatus(event) {
        const status = event.target.value;
        if (!["", "A", "I"].includes(status)) {
          return;
        }
        this.userStatus.set(status);
        this.loadUsers(0);
      }
      changeUserType(event) {
        const userType = event.target.value;
        if (!["", "STANDARD", "ADMIN"].includes(userType)) {
          return;
        }
        this.userType.set(userType);
        this.loadUsers(0);
      }
      registerStandard() {
        if (this.userForm.invalid) {
          this.userForm.markAllAsTouched();
          this.accessMessage.set("Completa correctamente todos los campos obligatorios.");
          return;
        }
        this.saving.set(true);
        const registrationStartedAt = Date.now();
        this.accessMessage.set("");
        this.userService.create(this.registrationPayload()).subscribe({
          next: () => {
            this.saving.set(false);
            const remainingMinimumTime = Math.max(0, 500 - (Date.now() - registrationStartedAt));
            setTimeout(() => {
              this.registrationModal.set("success");
              setTimeout(() => {
                this.registrationModal.set(null);
                this.cancelEdit();
                this.analysisForm.reset({ description: "" });
                this.analysisMessage.set("");
                this.view.set("standard-analysis");
                this.scrollToTop();
              }, 2e3);
            }, remainingMinimumTime);
          },
          error: () => {
            this.saving.set(false);
            this.registrationModal.set(null);
            this.accessMessage.set("No se pudo completar el registro. Verifica los datos e int\xE9ntalo nuevamente.");
          }
        });
      }
      submitAnalysis() {
        const selectedPdf = this.selectedPdf();
        const selectedAudio = this.selectedAudio();
        if (!selectedPdf && !selectedAudio && this.analysisForm.invalid) {
          this.analysisForm.markAllAsTouched();
          return;
        }
        const { doi, doiType } = this.accessForm.getRawValue();
        const { description } = this.analysisForm.getRawValue();
        this.submittingAnalysis.set(true);
        this.analysisMessage.set("");
        this.analysisResult.set(null);
        const adminAccessToken = this.adminAuthenticated() ? this.userService.getAccessToken() : "";
        const request = selectedPdf ? this.standardAnalysisService.createPdfAnalysis(doi, doiType, selectedPdf, adminAccessToken) : selectedAudio ? this.standardAnalysisService.createAudioAnalysis(doi, doiType, selectedAudio, adminAccessToken) : this.standardAnalysisService.createTextAnalysis(doi, doiType, description, adminAccessToken);
        request.subscribe({
          next: ({ analysisId }) => {
            this.analysisMessage.set("");
            this.pollAnalysis(analysisId, adminAccessToken);
          },
          error: () => {
            this.submittingAnalysis.set(false);
            this.analysisMessage.set("No se pudo iniciar el an\xE1lisis. Verifica tu sesi\xF3n, consentimiento e intenta nuevamente.");
          }
        });
      }
      onPdfSelected(event) {
        const input = event.target;
        const file = input.files?.[0];
        input.value = "";
        if (!file) {
          return;
        }
        if (file.type !== "application/pdf") {
          this.analysisMessage.set("Selecciona un archivo PDF v\xE1lido.");
          return;
        }
        if (file.size > 10 * 1024 * 1024) {
          this.analysisMessage.set("El PDF no puede superar los 10 MiB.");
          return;
        }
        this.selectedPdf.set(file);
        this.selectedAudio.set(null);
        this.analysisForm.controls.description.setValue("");
        this.analysisForm.controls.description.markAsUntouched();
        this.analysisMessage.set("");
      }
      removeSelectedPdf() {
        this.selectedPdf.set(null);
        this.analysisForm.controls.description.setValue("");
        this.analysisForm.controls.description.markAsUntouched();
        this.analysisMessage.set("");
      }
      toggleAudioRecording() {
        return __async(this, null, function* () {
          if (this.recordingAudio()) {
            this.mediaRecorder?.stop();
            return;
          }
          if (!navigator.mediaDevices?.getUserMedia || typeof MediaRecorder === "undefined") {
            this.analysisMessage.set("Tu navegador no permite grabar audio.");
            return;
          }
          try {
            this.recordingStream = yield navigator.mediaDevices.getUserMedia({ audio: true });
            const mimeType = ["audio/webm;codecs=opus", "audio/webm", "audio/mp4"].find((type) => MediaRecorder.isTypeSupported(type)) ?? "";
            this.mediaRecorder = new MediaRecorder(this.recordingStream, mimeType ? { mimeType } : void 0);
            this.recordingChunks = [];
            this.mediaRecorder.ondataavailable = (event) => {
              if (event.data.size > 0)
                this.recordingChunks.push(event.data);
            };
            this.mediaRecorder.onstop = () => {
              const type = this.mediaRecorder?.mimeType || "audio/webm";
              const extension = type.includes("mp4") ? "m4a" : "webm";
              const blob = new Blob(this.recordingChunks, { type });
              this.selectedAudio.set(new File([blob], `grabacion-${Date.now()}.${extension}`, { type }));
              this.selectedPdf.set(null);
              this.analysisForm.controls.description.setValue("");
              this.analysisForm.controls.description.markAsUntouched();
              this.recordingAudio.set(false);
              this.stopRecordingTimer();
              this.recordingStream?.getTracks().forEach((track) => track.stop());
              this.recordingStream = null;
              this.analysisMessage.set("");
            };
            this.mediaRecorder.start();
            this.recordingStartedAt = Date.now();
            this.recordingMilliseconds.set(0);
            this.recordingTimer = window.setInterval(() => {
              this.recordingMilliseconds.set(Date.now() - this.recordingStartedAt);
            }, 50);
            this.recordingAudio.set(true);
            this.analysisMessage.set("Grabando... pulsa el bot\xF3n para detener.");
          } catch {
            this.recordingAudio.set(false);
            this.stopRecordingTimer();
            this.recordingStream?.getTracks().forEach((track) => track.stop());
            this.recordingStream = null;
            this.analysisMessage.set("No se pudo acceder al micr\xF3fono. Revisa los permisos del navegador.");
          }
        });
      }
      stopAudioRecording() {
        if (this.recordingAudio()) {
          this.mediaRecorder?.stop();
        }
      }
      recordingTimeLabel() {
        const milliseconds = this.recordingMilliseconds();
        const minutes = Math.floor(milliseconds / 6e4).toString().padStart(2, "0");
        const seconds = Math.floor(milliseconds % 6e4 / 1e3).toString().padStart(2, "0");
        const remainder = Math.floor(milliseconds % 1e3 / 10).toString().padStart(2, "0");
        return `${minutes}:${seconds}:${remainder}`;
      }
      stopRecordingTimer() {
        if (this.recordingTimer !== null) {
          window.clearInterval(this.recordingTimer);
          this.recordingTimer = null;
        }
      }
      removeSelectedAudio() {
        this.selectedAudio.set(null);
        this.analysisForm.controls.description.setValue("");
        this.analysisForm.controls.description.markAsUntouched();
        this.analysisMessage.set("");
      }
      downloadAnalysisJson(analysisId, analysis) {
        const link = this.document.createElement("a");
        const url = URL.createObjectURL(new Blob([
          JSON.stringify(analysis, null, 2)
        ], { type: "application/json;charset=utf-8" }));
        link.href = url;
        link.download = `analysis-${analysisId}.json`;
        link.click();
        URL.revokeObjectURL(url);
      }
      downloadReportPdf() {
        const analysisId = this.analysisResult()?.analysisId;
        if (!analysisId || this.downloadingReportPdf()) {
          return;
        }
        const accessToken = this.adminAuthenticated() ? this.userService.getAccessToken() : "";
        this.downloadingReportPdf.set(true);
        this.reportPdfError.set("");
        this.standardAnalysisService.getReportPdf(analysisId, accessToken).subscribe({
          next: (blob) => {
            const url = URL.createObjectURL(blob);
            const link = this.document.createElement("a");
            link.href = url;
            link.download = `identipat-diagnostico-${analysisId}.pdf`;
            link.click();
            window.setTimeout(() => URL.revokeObjectURL(url));
            this.downloadingReportPdf.set(false);
          },
          error: () => {
            this.downloadingReportPdf.set(false);
            this.reportPdfError.set("No se pudo descargar el reporte PDF. Int\xE9ntalo nuevamente.");
          }
        });
      }
      pollAnalysis(analysisId, accessToken = "") {
        this.standardAnalysisService.getAnalysis(analysisId, accessToken).subscribe({
          next: (analysis) => {
            this.analysisResult.set(analysis);
            console.log("JSON del an\xE1lisis:", analysis);
            if (analysis.status === "COMPLETED" || analysis.status === "FAILED") {
              this.selectedPdf.set(null);
              this.selectedAudio.set(null);
              this.submittingAnalysis.set(false);
              this.analysisMessage.set(analysis.status === "COMPLETED" ? "" : "El an\xE1lisis no pudo completarse.");
              this.downloadAnalysisJson(analysisId, analysis);
              this.view.set("standard-result");
              this.scrollToTop();
              return;
            }
            window.setTimeout(() => this.pollAnalysis(analysisId, accessToken), 2e3);
          },
          error: (error) => {
            this.submittingAnalysis.set(false);
            this.analysisMessage.set("No se pudo consultar el resultado del an\xE1lisis.");
            console.error("No se pudo consultar el an\xE1lisis:", error);
          }
        });
      }
      protectionTypeLabel(type) {
        const labels = {
          INVENTION_PATENT: "Patente de invenci\xF3n",
          UTILITY_MODEL: "Modelo de utilidad",
          INDUSTRIAL_DESIGN: "Dise\xF1o industrial",
          DISTINCTIVE_SIGN: "Signo distintivo",
          COPYRIGHT: "Derechos de autor",
          OTHER: "Otra modalidad"
        };
        return labels[type] ?? type;
      }
      applicabilityLabel(applicability) {
        const labels = {
          POSSIBLE: "Posible",
          LIKELY: "Probable",
          UNLIKELY: "Poco probable",
          NO_POTENTIAL_MATCH: "Sin coincidencia potencial",
          INSUFFICIENT_INFORMATION: "Informaci\xF3n insuficiente"
        };
        return labels[applicability] ?? applicability;
      }
      submit() {
        const userId = this.editingUserId();
        if (userId === null) {
          return;
        }
        if (this.userForm.invalid) {
          this.userForm.markAllAsTouched();
          this.errorMessage.set("Completa correctamente todos los campos obligatorios.");
          return;
        }
        const formValue = this.userForm.getRawValue();
        const registrationPayload = this.registrationPayload();
        const userData = __spreadProps(__spreadValues({}, registrationPayload), { userType: this.editingUserType() });
        this.saving.set(true);
        this.errorMessage.set("");
        const request$ = this.editingOriginalUserType() === "ADMIN" && this.editingUserType() === "ADMIN" ? this.userService.updateProfile(userId, userData) : this.userService.update(userId, __spreadProps(__spreadValues({}, userData), {
          password: formValue.password || void 0
        }));
        request$.subscribe({
          next: () => {
            this.saving.set(false);
            this.cancelEdit();
            this.loadUsers();
          },
          error: () => {
            this.errorMessage.set("No se pudo guardar el usuario.");
            this.saving.set(false);
          }
        });
      }
      deleteEditingUser() {
        const userId = this.editingUserId();
        if (userId === null) {
          return;
        }
        const accepted = confirm(`\xBFEliminar al usuario #${userId}? Si tiene sesiones STANDARD, se conservar\xE1 como inactivo.`);
        if (!accepted) {
          return;
        }
        this.saving.set(true);
        this.editErrorMessage.set("");
        this.userService.delete(userId).subscribe({
          next: () => {
            this.saving.set(false);
            this.cancelEdit();
            this.loadUsers();
          },
          error: () => {
            this.saving.set(false);
            this.editErrorMessage.set("No se pudo eliminar o inactivar el usuario.");
          }
        });
      }
      startEdit(user) {
        if (!user.userId) {
          return;
        }
        this.editingUserId.set(user.userId);
        this.editErrorMessage.set("");
        this.editingUserType.set(user.userType);
        this.editingOriginalUserType.set(user.userType);
        this.showAdminPassword.set(false);
        const passwordControl = this.userForm.controls.password;
        passwordControl.clearValidators();
        passwordControl.updateValueAndValidity({ emitEvent: false });
        if (user.userType === "ADMIN") {
          this.userForm.controls.doi.disable({ emitEvent: false });
          this.userForm.controls.doiType.disable({ emitEvent: false });
        } else {
          this.userForm.controls.doi.enable({ emitEvent: false });
          this.userForm.controls.doiType.enable({ emitEvent: false });
        }
        this.userForm.setValue({
          firstName: user.firstName,
          paternalLastName: user.paternalLastName,
          maternalLastName: user.maternalLastName,
          doi: user.doi,
          doiType: user.doiType,
          birthDate: this.toDateInputValue(user.birthDate),
          gender: user.gender,
          email: user.email,
          phone: user.phone,
          mobilePhone: user.mobilePhone,
          profession: user.profession,
          password: "",
          termsAccepted: true
        });
        this.showEditModal.set(true);
      }
      cancelEdit() {
        this.editingUserId.set(null);
        this.editErrorMessage.set("");
        this.editingUserType.set("STANDARD");
        this.editingOriginalUserType.set("STANDARD");
        this.showAdminPassword.set(false);
        this.showEditModal.set(false);
        this.userForm.controls.doi.enable({ emitEvent: false });
        this.userForm.controls.doiType.enable({ emitEvent: false });
        this.userForm.controls.password.clearValidators();
        this.userForm.controls.password.updateValueAndValidity({ emitEvent: false });
        this.userForm.reset({
          firstName: "",
          paternalLastName: "",
          maternalLastName: "",
          doi: "",
          doiType: "",
          birthDate: "",
          gender: "",
          email: "",
          phone: "",
          mobilePhone: "",
          profession: "",
          password: "",
          termsAccepted: false
        });
      }
      toggleStatus(user) {
        if (!user.userId) {
          return;
        }
        const activating = user.status?.toUpperCase() === "I";
        const action = activating ? "activar" : "inactivar";
        const accepted = confirm(`\xBFSeguro que quieres ${action} al usuario #${user.userId}?`);
        if (!accepted) {
          return;
        }
        this.errorMessage.set("");
        this.userService.update(user.userId, __spreadProps(__spreadValues({}, user), { status: activating ? "A" : "I" })).subscribe({
          next: () => this.loadUsers(),
          error: () => this.errorMessage.set(`No se pudo ${action} el usuario.`)
        });
      }
      isStandard(user) {
        return user.userType?.toUpperCase() === "STANDARD";
      }
      isInactive(user) {
        return user.status?.toUpperCase() === "I";
      }
      isEditing(user) {
        return this.editingUserId() === user.userId;
      }
      loadProfileForm() {
        this.profileMessage.set("");
        this.passwordMessage.set("");
        this.showAdminPassword.set(false);
        this.securityForm.reset({ password: "", confirmPassword: "" });
        const admin = this.currentAdmin();
        if (!admin) {
          return;
        }
        this.profileForm.setValue({
          firstName: admin.firstName,
          paternalLastName: admin.paternalLastName,
          maternalLastName: admin.maternalLastName,
          birthDate: this.toDateInputValue(admin.birthDate),
          gender: admin.gender,
          email: admin.email,
          phone: admin.phone,
          mobilePhone: admin.mobilePhone,
          profession: admin.profession
        });
      }
      updateProfile() {
        const admin = this.currentAdmin();
        if (!admin?.userId) {
          this.profileMessage.set("No se pudo identificar al administrador autenticado.");
          return;
        }
        if (this.profileForm.invalid) {
          this.profileForm.markAllAsTouched();
          this.profileMessage.set("Completa correctamente todos los campos obligatorios.");
          return;
        }
        const formValue = this.profileForm.getRawValue();
        const profileData = __spreadProps(__spreadValues({}, admin), {
          firstName: formValue.firstName,
          paternalLastName: formValue.paternalLastName,
          maternalLastName: formValue.maternalLastName,
          birthDate: this.toBackendDate(this.profileForm.controls.birthDate.value),
          gender: formValue.gender,
          email: formValue.email,
          phone: formValue.phone,
          mobilePhone: formValue.mobilePhone,
          profession: formValue.profession,
          doi: admin.doi,
          doiType: admin.doiType,
          userType: admin.userType,
          status: admin.status
        });
        this.savingProfile.set(true);
        this.profileMessage.set("");
        this.userService.updateProfile(admin.userId, profileData).subscribe({
          next: () => {
            this.savingProfile.set(false);
            this.profileMessage.set("Tus datos se actualizaron correctamente.");
            this.loadUsers();
          },
          error: () => {
            this.savingProfile.set(false);
            this.profileMessage.set("No se pudo actualizar tu informaci\xF3n.");
          }
        });
      }
      updateAdminPassword() {
        const admin = this.currentAdmin();
        if (!admin?.userId) {
          this.passwordMessage.set("No se pudo identificar al administrador autenticado.");
          return;
        }
        if (this.securityForm.invalid || !this.securityForm.controls.password.value.trim()) {
          this.securityForm.markAllAsTouched();
          this.passwordMessage.set(this.securityForm.hasError("passwordMismatch") ? "Las contrase\xF1as no coinciden." : "Ingresa y confirma la nueva contrase\xF1a.");
          return;
        }
        this.savingPassword.set(true);
        this.passwordMessage.set("");
        this.userService.update(admin.userId, __spreadProps(__spreadValues({}, admin), {
          password: this.securityForm.controls.password.value
        })).subscribe({
          next: () => {
            this.savingPassword.set(false);
            this.passwordSuccessModal.set(true);
            this.securityForm.reset({ password: "", confirmPassword: "" });
            this.showAdminPassword.set(false);
          },
          error: () => {
            this.savingPassword.set(false);
            this.passwordMessage.set("No se pudo actualizar la contrase\xF1a.");
          }
        });
      }
      closePasswordSuccessModal() {
        this.passwordSuccessModal.set(false);
        this.document.defaultView?.location.reload();
      }
      registrationPayload() {
        const formValue = this.userForm.getRawValue();
        return {
          firstName: formValue.firstName,
          paternalLastName: formValue.paternalLastName,
          maternalLastName: formValue.maternalLastName,
          doi: formValue.doi,
          doiType: formValue.doiType,
          birthDate: this.toBackendDate(formValue.birthDate),
          gender: formValue.gender,
          email: formValue.email,
          phone: formValue.phone,
          mobilePhone: formValue.mobilePhone,
          profession: formValue.profession
        };
      }
      static ctorParameters = () => [];
    };
    App = __decorate([
      Component({
        selector: "app-root",
        imports: [CommonModule, ReactiveFormsModule],
        template: app_default,
        styles: [app_default2]
      })
    ], App);
  }
});

// src/app/services/admin-analysis.service.ts
var AdminAnalysisService;
var init_admin_analysis_service = __esm({
  "src/app/services/admin-analysis.service.ts"() {
    "use strict";
    init_tslib_es6();
    init_http();
    init_core();
    init_environment();
    AdminAnalysisService = class AdminAnalysisService2 {
      http = inject(HttpClient);
      endpoint = `${environment.apiBaseUrl}/analyses`;
      accessToken = "";
      setAccessToken(accessToken) {
        this.accessToken = accessToken;
      }
      clearAccessToken() {
        this.accessToken = "";
      }
      getReport(analysisId) {
        return this.http.get(`${this.endpoint}/${encodeURIComponent(analysisId)}/report`, {
          headers: { Authorization: `Bearer ${this.accessToken}` }
        });
      }
    };
    AdminAnalysisService = __decorate([
      Injectable({
        providedIn: "root"
      })
    ], AdminAnalysisService);
  }
});

// src/app/app.spec.ts
var require_app_spec = __commonJS({
  "src/app/app.spec.ts"(exports) {
    init_core();
    init_http();
    init_testing2();
    init_testing();
    init_esm();
    init_app3();
    init_admin_analysis_service();
    init_admin_report_disclaimer_service();
    init_standard_analysis_service();
    init_user_service();
    init_environment();
    describe("App", () => {
      beforeEach(() => __async(null, null, function* () {
        yield TestBed.configureTestingModule({
          imports: [App],
          providers: [provideZonelessChangeDetection(), provideHttpClient(), provideHttpClientTesting()]
        }).compileComponents();
      }));
      it("should create the app", () => {
        const fixture = TestBed.createComponent(App);
        const app = fixture.componentInstance;
        expect(app).toBeTruthy();
      });
      it("should fetch an admin diagnostic report by analysis id", () => {
        const service = TestBed.inject(AdminAnalysisService);
        const httpTesting = TestBed.inject(HttpTestingController);
        service.setAccessToken("test-token");
        service.getReport("analysis-123").subscribe();
        const reportRequest = httpTesting.expectOne(`${environment.apiBaseUrl}/analyses/analysis-123/report`);
        expect(reportRequest.request.method).toBe("GET");
        expect(reportRequest.request.headers.get("Authorization")).toBe("Bearer test-token");
        reportRequest.flush({});
      });
      it("should fetch all report disclaimers with the admin bearer token", () => {
        const service = TestBed.inject(AdminReportDisclaimerService);
        const httpTesting = TestBed.inject(HttpTestingController);
        service.getAll("test-token").subscribe();
        const disclaimerRequest = httpTesting.expectOne(`${environment.apiBaseUrl}/admin/report-disclaimers`);
        expect(disclaimerRequest.request.method).toBe("GET");
        expect(disclaimerRequest.request.headers.get("Authorization")).toBe("Bearer test-token");
        disclaimerRequest.flush([{
          id: "disclaimer-id",
          version: 1,
          title: "Aviso legal",
          content: "Texto del disclaimer",
          status: "ACTIVE",
          activatedAt: "2026-10-29T12:00:00Z"
        }]);
      });
      it("should show only the latest disclaimer when entering settings", () => {
        const fixture = TestBed.createComponent(App);
        const app = fixture.componentInstance;
        const httpTesting = TestBed.inject(HttpTestingController);
        app.view.set("admin");
        app.setAdminSection("settings");
        fixture.detectChanges();
        const disclaimersRequest = httpTesting.expectOne(`${environment.apiBaseUrl}/admin/report-disclaimers`);
        expect(disclaimersRequest.request.method).toBe("GET");
        disclaimersRequest.flush([
          {
            id: "old-id",
            version: 3,
            title: "Aviso anterior",
            content: "Contenido anterior",
            status: "RETIRED",
            activatedAt: null
          },
          {
            id: "latest-id",
            version: 8,
            title: "Aviso m\xE1s reciente",
            content: "Contenido m\xE1s reciente",
            status: "DRAFT",
            activatedAt: null
          }
        ]);
        fixture.detectChanges();
        const text = fixture.nativeElement.textContent ?? "";
        expect(app.latestReportDisclaimer()?.id).toBe("latest-id");
        expect(app.savedDisclaimerDraft()?.id).toBe("latest-id");
        expect(fixture.nativeElement.querySelector("#disclaimer-title")?.value).toBe("Aviso m\xE1s reciente");
        expect(text).not.toContain("Estado:");
        expect(text).not.toContain("\xDAltima versi\xF3n guardada:");
        expect(text).not.toContain("Activado:");
        expect(text).not.toContain("Aviso anterior");
      });
      it("should create a report disclaimer draft with the admin bearer token", () => {
        const service = TestBed.inject(AdminReportDisclaimerService);
        const httpTesting = TestBed.inject(HttpTestingController);
        service.createDraft("test-token", "Aviso legal", "Texto actualizado").subscribe();
        const draftRequest = httpTesting.expectOne(`${environment.apiBaseUrl}/admin/report-disclaimers`);
        expect(draftRequest.request.method).toBe("POST");
        expect(draftRequest.request.headers.get("Authorization")).toBe("Bearer test-token");
        expect(draftRequest.request.body).toEqual({ title: "Aviso legal", content: "Texto actualizado" });
        draftRequest.flush({
          id: "draft-id",
          version: 2,
          title: "Aviso legal",
          content: "Texto actualizado",
          status: "DRAFT",
          activatedAt: null
        }, { status: 201, statusText: "Created" });
      });
      it("should activate a report disclaimer draft with the admin bearer token", () => {
        const service = TestBed.inject(AdminReportDisclaimerService);
        const httpTesting = TestBed.inject(HttpTestingController);
        service.activate("test-token", "draft-id").subscribe();
        const activationRequest = httpTesting.expectOne(`${environment.apiBaseUrl}/admin/report-disclaimers/draft-id/activate`);
        expect(activationRequest.request.method).toBe("POST");
        expect(activationRequest.request.headers.get("Authorization")).toBe("Bearer test-token");
        expect(activationRequest.request.body).toBeNull();
        activationRequest.flush({
          id: "draft-id",
          version: 2,
          title: "Aviso legal",
          content: "Texto actualizado",
          status: "ACTIVE",
          activatedAt: "2026-10-29T13:00:00Z"
        });
      });
      it("should save disclaimer title and content together as a draft", () => {
        const fixture = TestBed.createComponent(App);
        const app = fixture.componentInstance;
        const disclaimerService = TestBed.inject(AdminReportDisclaimerService);
        const activeDisclaimer = {
          id: "active-id",
          version: 1,
          title: "Aviso legal",
          content: "Texto original",
          status: "ACTIVE",
          activatedAt: "2026-10-29T12:00:00Z"
        };
        spyOn(disclaimerService, "createDraft").and.returnValue(of(__spreadProps(__spreadValues({}, activeDisclaimer), {
          id: "draft-id",
          version: 2,
          title: "Aviso actualizado",
          content: "Texto actualizado",
          status: "DRAFT",
          activatedAt: null
        })));
        spyOn(disclaimerService, "activate").and.returnValue(of(__spreadProps(__spreadValues({}, activeDisclaimer), {
          id: "draft-id",
          version: 2,
          content: "Texto actualizado",
          status: "ACTIVE",
          activatedAt: "2026-10-29T13:00:00Z"
        })));
        const reloadSchedule = spyOn(app, "scheduleAdminSettingsReload");
        app.view.set("admin");
        app.adminSection.set("settings");
        app.latestReportDisclaimer.set(activeDisclaimer);
        app.disclaimerTitle.set(activeDisclaimer.title);
        app.savedDisclaimerTitle.set(activeDisclaimer.title);
        app.disclaimerContent.set(activeDisclaimer.content);
        app.savedDisclaimerContent.set(activeDisclaimer.content);
        fixture.detectChanges();
        const root = fixture.nativeElement;
        const titleInput = root.querySelector("input#disclaimer-title");
        const textarea = root.querySelector("textarea#disclaimer-content");
        const saveButton = Array.from(root.querySelectorAll("button")).find((button) => button.textContent?.includes("Guardar borrador"));
        expect(saveButton.disabled).toBeTrue();
        titleInput.value = "Aviso actualizado";
        titleInput.dispatchEvent(new Event("input"));
        textarea.value = "Texto actualizado";
        textarea.dispatchEvent(new Event("input"));
        fixture.detectChanges();
        expect(saveButton.disabled).toBeFalse();
        saveButton.click();
        fixture.detectChanges();
        expect(disclaimerService.createDraft).toHaveBeenCalledWith("", "Aviso actualizado", "Texto actualizado");
        expect(saveButton.disabled).toBeTrue();
        expect(fixture.nativeElement.textContent).toContain("Borrador guardado correctamente.");
        expect(fixture.nativeElement.textContent).not.toContain("Borrador v2");
        const publishButton = Array.from(root.querySelectorAll("button")).find((button) => button.textContent?.includes("Publicar borrador"));
        expect(publishButton.disabled).toBeFalse();
        publishButton.click();
        fixture.detectChanges();
        expect(root.querySelector('[role="dialog"]')).not.toBeNull();
        expect(disclaimerService.activate).not.toHaveBeenCalled();
        const confirmButton = Array.from(root.querySelectorAll("button")).find((button) => button.textContent?.includes("Confirmar publicaci\xF3n"));
        confirmButton.click();
        fixture.detectChanges();
        expect(disclaimerService.activate).toHaveBeenCalledWith("", "draft-id");
        expect(root.querySelector('[role="dialog"]')).toBeNull();
        expect(app.latestReportDisclaimer()?.status).toBe("ACTIVE");
        expect(app.savedDisclaimerDraft()).toBeNull();
        expect(root.textContent).toContain("Disclaimer publicado correctamente.");
        expect(reloadSchedule).toHaveBeenCalled();
      });
      it("should request a report PDF with ADMIN bearer or STANDARD session credentials", () => {
        const service = TestBed.inject(StandardAnalysisService);
        const httpTesting = TestBed.inject(HttpTestingController);
        service.getReportPdf("admin-analysis", "test-token").subscribe();
        const adminRequest = httpTesting.expectOne(`${environment.apiBaseUrl}/analyses/admin-analysis/report.pdf`);
        expect(adminRequest.request.method).toBe("GET");
        expect(adminRequest.request.responseType).toBe("blob");
        expect(adminRequest.request.headers.get("Authorization")).toBe("Bearer test-token");
        adminRequest.flush(new Blob(["pdf"], { type: "application/pdf" }));
        service.getReportPdf("standard-analysis").subscribe();
        const standardRequest = httpTesting.expectOne(`${environment.apiBaseUrl}/analyses/standard-analysis/report.pdf`);
        expect(standardRequest.request.withCredentials).toBeTrue();
        standardRequest.flush(new Blob(["pdf"], { type: "application/pdf" }));
      });
      it("should remove non-numeric characters from a DNI during registration", () => {
        const fixture = TestBed.createComponent(App);
        const app = fixture.componentInstance;
        const input = document.createElement("input");
        input.value = "12AB3456789";
        app.sanitizeDni({ target: input }, app.userForm.controls.doi);
        expect(input.value).toBe("12345678");
        expect(app.userForm.controls.doi.value).toBe("12345678");
      });
      it("should render the home as the first screen", () => {
        const fixture = TestBed.createComponent(App);
        fixture.detectChanges();
        const compiled = fixture.nativeElement;
        expect(compiled.querySelector("h1")?.textContent).toContain("Descubre c\xF3mo proteger la propiedad intelectual de tu creaci\xF3n");
        expect(compiled.textContent).not.toContain("Usuarios registrados");
      });
      it("should close the STANDARD session before exiting without fetching the session", () => {
        const fixture = TestBed.createComponent(App);
        const app = fixture.componentInstance;
        const httpTesting = TestBed.inject(HttpTestingController);
        app.view.set("standard-result");
        app.exitResult();
        const csrfRequest = httpTesting.expectOne(`${environment.apiBaseUrl}/standard-session/csrf`);
        expect(csrfRequest.request.method).toBe("GET");
        expect(csrfRequest.request.withCredentials).toBeTrue();
        csrfRequest.flush({ token: "csrf-token", headerName: "X-XSRF-TOKEN" });
        const closeRequest = httpTesting.expectOne(`${environment.apiBaseUrl}/standard-session`);
        expect(closeRequest.request.method).toBe("DELETE");
        expect(closeRequest.request.headers.get("X-XSRF-TOKEN")).toBe("csrf-token");
        expect(closeRequest.request.withCredentials).toBeTrue();
        closeRequest.flush(null, { status: 204, statusText: "No Content" });
        httpTesting.expectNone((request) => request.url === `${environment.apiBaseUrl}/standard-session` && request.method === "GET");
        expect(app.view()).toBe("home");
        expect(app.navigationPending()).toBeNull();
        expect(app.exitError()).toBe("");
      });
      it("should allow exiting when the STANDARD session has already expired", () => {
        const fixture = TestBed.createComponent(App);
        const app = fixture.componentInstance;
        const httpTesting = TestBed.inject(HttpTestingController);
        app.view.set("standard-result");
        app.exitResult();
        httpTesting.expectOne(`${environment.apiBaseUrl}/standard-session/csrf`).flush({ token: "csrf-token", headerName: "X-XSRF-TOKEN" });
        httpTesting.expectOne(`${environment.apiBaseUrl}/standard-session`).flush({ code: "STANDARD_SESSION_REQUIRED" }, { status: 401, statusText: "Unauthorized" });
        expect(app.view()).toBe("home");
        expect(app.navigationPending()).toBeNull();
        expect(app.exitError()).toBe("");
      });
      it("should load and navigate paginated admin users", () => {
        const fixture = TestBed.createComponent(App);
        const app = fixture.componentInstance;
        const httpTesting = TestBed.inject(HttpTestingController);
        const pageResponse = {
          content: [{
            userId: 1,
            firstName: "Ana",
            paternalLastName: "P\xE9rez",
            maternalLastName: "Rojas",
            doi: "12345678",
            doiType: "DNI",
            birthDate: "1990-01-01",
            gender: "F",
            email: "ana@example.com",
            phone: "123456789",
            mobilePhone: "987654321",
            userType: "STANDARD",
            profession: "Abogada",
            status: "A"
          }],
          page: 0,
          size: 20,
          totalElements: 42,
          totalPages: 3,
          first: true,
          last: false
        };
        app.loadUsers();
        httpTesting.expectOne((request) => request.url === `${environment.apiBaseUrl}/users` && request.params.get("page") === "0" && request.params.get("size") === "20").flush(pageResponse);
        expect(app.users()).toEqual(pageResponse.content);
        expect(app.userTotalElements()).toBe(42);
        expect(app.userTotalPages()).toBe(3);
        app.changeUserPage(1);
        httpTesting.expectOne((request) => request.url === `${environment.apiBaseUrl}/users` && request.params.get("page") === "1" && request.params.get("size") === "20").flush(__spreadProps(__spreadValues({}, pageResponse), { page: 1, first: false }));
        expect(app.userPage()).toBe(1);
        expect(app.users()).toHaveSize(1);
        app.updateUserSearch({ target: { value: "ana rojas" } });
        app.searchUsers();
        httpTesting.expectOne((request) => request.url === `${environment.apiBaseUrl}/users` && request.params.get("page") === "0" && request.params.get("size") === "20" && request.params.get("search") === "ana rojas").flush(__spreadProps(__spreadValues({}, pageResponse), { totalElements: 1, totalPages: 1 }));
        expect(app.userPage()).toBe(0);
        expect(app.userTotalElements()).toBe(1);
        app.changeUserStatus({ target: { value: "I" } });
        httpTesting.expectOne((request) => request.url === `${environment.apiBaseUrl}/users` && request.params.get("page") === "0" && request.params.get("size") === "20" && request.params.get("search") === "ana rojas" && request.params.get("status") === "I").flush(__spreadProps(__spreadValues({}, pageResponse), { content: [], totalElements: 0, totalPages: 0 }));
        expect(app.userStatus()).toBe("I");
        expect(app.userTotalElements()).toBe(0);
        app.changeUserType({ target: { value: "ADMIN" } });
        httpTesting.expectOne((request) => request.url === `${environment.apiBaseUrl}/users` && request.params.get("page") === "0" && request.params.get("size") === "20" && request.params.get("search") === "ana rojas" && request.params.get("status") === "I" && request.params.get("userType") === "ADMIN").flush(__spreadProps(__spreadValues({}, pageResponse), { content: [], totalElements: 0, totalPages: 0 }));
        expect(app.userType()).toBe("ADMIN");
        expect(app.userTotalElements()).toBe(0);
      });
      it("should request a password automatically when the document belongs to an admin", () => __async(null, null, function* () {
        const fixture = TestBed.createComponent(App);
        const app = fixture.componentInstance;
        const httpTesting = TestBed.inject(HttpTestingController);
        fixture.detectChanges();
        app.accessForm.setValue({ doiType: "DNI", doi: "12345678" });
        app.identifyDocument();
        httpTesting.expectOne(`${environment.apiBaseUrl}/users/identify`).flush({
          registered: true,
          passwordRequired: true
        });
        yield new Promise((resolve) => setTimeout(resolve, 500));
        fixture.detectChanges();
        expect(fixture.nativeElement.textContent).not.toContain("Acceso administrativo");
        expect(fixture.nativeElement.querySelector('input[type="password"]')).not.toBeNull();
      }));
      it("should show the verification modal for a registered standard user", () => {
        const fixture = TestBed.createComponent(App);
        const app = fixture.componentInstance;
        const httpTesting = TestBed.inject(HttpTestingController);
        fixture.detectChanges();
        app.accessForm.setValue({ doiType: "DNI", doi: "12345678" });
        app.identifyDocument();
        fixture.detectChanges();
        expect(fixture.nativeElement.querySelector(".access-progress-modal")).toBeNull();
        httpTesting.expectOne(`${environment.apiBaseUrl}/users/identify`).flush({
          registered: true,
          passwordRequired: false
        });
        fixture.detectChanges();
        expect(fixture.nativeElement.querySelector(".access-progress-modal")).not.toBeNull();
      });
      it("should show the verification modal after an admin submits the password", () => {
        const fixture = TestBed.createComponent(App);
        const app = fixture.componentInstance;
        const httpTesting = TestBed.inject(HttpTestingController);
        fixture.detectChanges();
        app.view.set("admin-login");
        app.accessForm.setValue({ doiType: "DNI", doi: "12345678" });
        app.loginForm.setValue({ password: "secret" });
        app.loginAdmin();
        fixture.detectChanges();
        expect(fixture.nativeElement.querySelector(".access-progress-modal")).not.toBeNull();
        httpTesting.expectOne(`${environment.apiBaseUrl}/users/login`).flush({}, {
          status: 401,
          statusText: "Unauthorized"
        });
      });
      it("should show a message for an unknown document without opening registration", () => __async(null, null, function* () {
        const fixture = TestBed.createComponent(App);
        const app = fixture.componentInstance;
        const httpTesting = TestBed.inject(HttpTestingController);
        fixture.detectChanges();
        app.accessForm.setValue({ doiType: "DNI", doi: "12345678" });
        app.identifyDocument();
        httpTesting.expectOne(`${environment.apiBaseUrl}/users/identify`).flush({
          registered: false,
          passwordRequired: false
        });
        expect(app.accessMessage()).toBe("");
        yield new Promise((resolve) => setTimeout(resolve, 500));
        fixture.detectChanges();
        expect(app.view()).toBe("home");
        expect(app.accessMessage()).toBe("Usuario no registrado");
        expect(fixture.nativeElement.textContent).toContain("Usuario no registrado");
      }));
      it("should open registration directly from the access screen", () => {
        const fixture = TestBed.createComponent(App);
        const app = fixture.componentInstance;
        fixture.detectChanges();
        const registerButton = fixture.nativeElement.querySelector(".access-register-button");
        registerButton.click();
        fixture.detectChanges();
        expect(app.view()).toBe("registration");
        expect(fixture.nativeElement.textContent).toContain("Completa tu registro");
      });
      it("should show the analysis form and admin panel access after a successful admin login", () => __async(null, null, function* () {
        const userService = TestBed.inject(UserService);
        userService.clearAccessToken();
        const fixture = TestBed.createComponent(App);
        const app = fixture.componentInstance;
        const httpTesting = TestBed.inject(HttpTestingController);
        fixture.detectChanges();
        app.accessForm.setValue({ doiType: "DNI", doi: "12345678" });
        app.identifyDocument();
        httpTesting.expectOne(`${environment.apiBaseUrl}/users/identify`).flush({
          registered: true,
          passwordRequired: true
        });
        app.loginForm.setValue({ password: "secret" });
        app.loginAdmin();
        httpTesting.expectOne(`${environment.apiBaseUrl}/users/login`).flush({
          tokenType: "Bearer",
          accessToken: "test-token"
        });
        yield new Promise((resolve) => setTimeout(resolve, 500));
        fixture.detectChanges();
        expect(app.view()).toBe("standard-analysis");
        expect(fixture.nativeElement.textContent).toContain("Cu\xE9ntanos qu\xE9 quieres proteger");
        expect(fixture.nativeElement.textContent).toContain("Panel de administraci\xF3n");
        userService.clearAccessToken();
      }));
      it("should show the predictions section without requesting the pending endpoint", () => {
        const fixture = TestBed.createComponent(App);
        const app = fixture.componentInstance;
        const httpTesting = TestBed.inject(HttpTestingController);
        app.view.set("admin");
        app.setAdminSection("predictions");
        fixture.detectChanges();
        httpTesting.expectNone(`${environment.apiBaseUrl}/admin/analyses`);
        expect(fixture.nativeElement.textContent).toContain("Historial de predicciones");
        expect(fixture.nativeElement.textContent).toContain("No hay predicciones registradas.");
      });
      it("should create only a standard user through the registration contract", () => {
        const fixture = TestBed.createComponent(App);
        const app = fixture.componentInstance;
        const httpTesting = TestBed.inject(HttpTestingController);
        app.view.set("registration");
        fixture.detectChanges();
        app.userForm.setValue({
          firstName: "Nombre de prueba",
          paternalLastName: "Apellido paterno",
          maternalLastName: "Apellido materno",
          doi: "12345678",
          doiType: "DNI",
          birthDate: "01/01/1990",
          gender: "FEMALE",
          email: "standard.placeholder@example.test",
          phone: "016543210",
          mobilePhone: "912345678",
          profession: "Profesi\xF3n de prueba",
          password: "",
          termsAccepted: true
        });
        fixture.detectChanges();
        const createButton = fixture.nativeElement.querySelector('.registration-actions button[type="submit"]');
        createButton.click();
        const registrationRequest = httpTesting.expectOne(`${environment.apiBaseUrl}/users`);
        expect(registrationRequest.request.method).toBe("POST");
        expect(registrationRequest.request.body).toEqual({
          firstName: "Nombre de prueba",
          paternalLastName: "Apellido paterno",
          maternalLastName: "Apellido materno",
          doi: "12345678",
          doiType: "DNI",
          birthDate: "01/01/1990",
          gender: "FEMALE",
          email: "standard.placeholder@example.test",
          phone: "016543210",
          mobilePhone: "912345678",
          profession: "Profesi\xF3n de prueba"
        });
        registrationRequest.flush({ registered: true });
      });
      it("should inactivate a user through an update without deleting it", () => {
        const fixture = TestBed.createComponent(App);
        const app = fixture.componentInstance;
        const httpTesting = TestBed.inject(HttpTestingController);
        const user = {
          userId: 7,
          firstName: "Nombre",
          paternalLastName: "Apellido",
          maternalLastName: "Materno",
          doi: "12345678",
          doiType: "DNI",
          birthDate: "01/01/1990",
          gender: "FEMALE",
          email: "user@example.test",
          phone: "016543210",
          mobilePhone: "912345678",
          userType: "STANDARD",
          profession: "Profesi\xF3n",
          status: "A"
        };
        spyOn(window, "confirm").and.returnValue(true);
        app.toggleStatus(user);
        const updateRequest = httpTesting.expectOne(`${environment.apiBaseUrl}/users/admin/7`);
        expect(updateRequest.request.method).toBe("PUT");
        expect(updateRequest.request.body).toEqual(__spreadProps(__spreadValues({}, user), { status: "I" }));
        httpTesting.expectNone(`${environment.apiBaseUrl}/users/7`);
        updateRequest.flush(__spreadProps(__spreadValues({}, user), { status: "I" }));
        const reloadRequest = httpTesting.expectOne((request) => request.url === `${environment.apiBaseUrl}/users` && request.method === "GET");
        expect(reloadRequest.request.params.get("page")).toBe("0");
        expect(reloadRequest.request.params.get("size")).toBe("20");
        reloadRequest.flush({
          content: [],
          page: 0,
          size: 20,
          totalElements: 0,
          totalPages: 0,
          first: true,
          last: true
        });
      });
      it("should delete the edited user and reload the user list", () => {
        const fixture = TestBed.createComponent(App);
        const app = fixture.componentInstance;
        const httpTesting = TestBed.inject(HttpTestingController);
        const user = {
          userId: 9,
          firstName: "Nombre",
          paternalLastName: "Apellido",
          maternalLastName: "Materno",
          doi: "12345678",
          doiType: "DNI",
          birthDate: "01/01/1990",
          gender: "FEMALE",
          email: "user@example.test",
          phone: "016543210",
          mobilePhone: "912345678",
          userType: "STANDARD",
          profession: "Profesi\xF3n",
          status: "A"
        };
        spyOn(window, "confirm").and.returnValue(true);
        app.startEdit(user);
        app.deleteEditingUser();
        const deleteRequest = httpTesting.expectOne(`${environment.apiBaseUrl}/users/9`);
        expect(deleteRequest.request.method).toBe("DELETE");
        deleteRequest.flush(null, { status: 204, statusText: "No Content" });
        expect(app.showEditModal()).toBeFalse();
        httpTesting.expectOne((request) => request.url === `${environment.apiBaseUrl}/users` && request.method === "GET").flush({
          content: [],
          page: 0,
          size: 20,
          totalElements: 0,
          totalPages: 0,
          first: true,
          last: true
        });
      });
      it("should reactivate an inactive user through an update", () => {
        const fixture = TestBed.createComponent(App);
        const app = fixture.componentInstance;
        const httpTesting = TestBed.inject(HttpTestingController);
        const user = {
          userId: 8,
          firstName: "Nombre",
          paternalLastName: "Apellido",
          maternalLastName: "Materno",
          doi: "87654321",
          doiType: "DNI",
          birthDate: "01/01/1990",
          gender: "FEMALE",
          email: "inactive@example.test",
          phone: "016543210",
          mobilePhone: "912345678",
          userType: "STANDARD",
          profession: "Profesi\xF3n",
          status: "I"
        };
        spyOn(window, "confirm").and.returnValue(true);
        app.toggleStatus(user);
        const updateRequest = httpTesting.expectOne(`${environment.apiBaseUrl}/users/admin/8`);
        expect(updateRequest.request.method).toBe("PUT");
        expect(updateRequest.request.body).toEqual(__spreadProps(__spreadValues({}, user), { status: "A" }));
        updateRequest.flush(__spreadProps(__spreadValues({}, user), { status: "A" }));
        const reloadRequest = httpTesting.expectOne((request) => request.url === `${environment.apiBaseUrl}/users` && request.method === "GET");
        expect(reloadRequest.request.params.get("page")).toBe("0");
        expect(reloadRequest.request.params.get("size")).toBe("20");
        reloadRequest.flush({
          content: [],
          page: 0,
          size: 20,
          totalElements: 0,
          totalPages: 0,
          first: true,
          last: true
        });
      });
      it("should update the administrator password from the security form", () => {
        const fixture = TestBed.createComponent(App);
        const app = fixture.componentInstance;
        const httpTesting = TestBed.inject(HttpTestingController);
        const admin = {
          userId: 1,
          firstName: "Admin",
          paternalLastName: "Principal",
          maternalLastName: "Sistema",
          doi: "12345678",
          doiType: "DNI",
          birthDate: "01/01/1990",
          gender: "OTHER",
          email: "admin@example.test",
          phone: "016543210",
          mobilePhone: "912345678",
          userType: "ADMIN",
          profession: "Administrador",
          status: "A"
        };
        app.adminProfile.set(admin);
        app.view.set("admin");
        app.adminSection.set("profile");
        app.profileForm.setValue({
          firstName: admin.firstName,
          paternalLastName: admin.paternalLastName,
          maternalLastName: admin.maternalLastName,
          birthDate: "1990-01-01",
          gender: admin.gender,
          email: admin.email,
          phone: admin.phone,
          mobilePhone: admin.mobilePhone,
          profession: admin.profession
        });
        app.securityForm.setValue({ password: "new-secret", confirmPassword: "different-secret" });
        app.updateAdminPassword();
        expect(app.passwordMessage()).toBe("Las contrase\xF1as no coinciden.");
        app.securityForm.controls.confirmPassword.setValue("new-secret");
        app.updateAdminPassword();
        const updateRequest = httpTesting.expectOne(`${environment.apiBaseUrl}/users/admin/1`);
        expect(updateRequest.request.method).toBe("PUT");
        expect(updateRequest.request.body).toEqual(__spreadProps(__spreadValues({}, admin), { password: "new-secret" }));
        updateRequest.flush(admin);
        expect(app.securityForm.controls.password.value).toBe("");
        expect(app.securityForm.controls.confirmPassword.value).toBe("");
        expect(app.passwordSuccessModal()).toBeTrue();
        fixture.detectChanges();
        expect(fixture.nativeElement.textContent).toContain("La contrase\xF1a se actualiz\xF3 correctamente.");
      });
      it("should assign the default admin password when changing the edit form user type", () => {
        const fixture = TestBed.createComponent(App);
        const app = fixture.componentInstance;
        const user = {
          userId: 10,
          firstName: "Usuario",
          paternalLastName: "Est\xE1ndar",
          maternalLastName: "Prueba",
          doi: "11223344",
          doiType: "DNI",
          birthDate: "01/01/1990",
          gender: "FEMALE",
          email: "standard@example.test",
          phone: "016543210",
          mobilePhone: "912345678",
          userType: "STANDARD",
          profession: "Profesional",
          status: "A"
        };
        app.startEdit(user);
        app.changeEditingUserType({ target: { value: "ADMIN" } });
        expect(app.editingUserType()).toBe("ADMIN");
        expect(app.userForm.controls.password.value).toBe("admin");
        expect(app.userForm.controls.password.valid).toBeTrue();
      });
      it("should edit an existing admin without exposing or updating their password", () => {
        const fixture = TestBed.createComponent(App);
        const app = fixture.componentInstance;
        const httpTesting = TestBed.inject(HttpTestingController);
        const admin = {
          userId: 11,
          firstName: "Admin",
          paternalLastName: "Principal",
          maternalLastName: "Sistema",
          doi: "12345678",
          doiType: "DNI",
          birthDate: "01/01/1990",
          gender: "FEMALE",
          email: "admin@example.test",
          phone: "016543210",
          mobilePhone: "912345678",
          userType: "ADMIN",
          profession: "Administrador",
          status: "A"
        };
        app.view.set("admin");
        app.adminSection.set("users");
        app.startEdit(admin);
        fixture.detectChanges();
        expect(fixture.nativeElement.querySelector('.modal-panel input[type="password"]')).toBeNull();
        expect(app.userForm.controls.password.valid).toBeTrue();
        expect(app.userForm.controls.doi.disabled).toBeTrue();
        app.userForm.controls.firstName.setValue("Admin actualizado");
        app.submit();
        const updateRequest = httpTesting.expectOne(`${environment.apiBaseUrl}/users/11`);
        expect(updateRequest.request.method).toBe("PUT");
        expect(updateRequest.request.body.password).toBeUndefined();
        updateRequest.flush(admin);
        httpTesting.expectOne((request) => request.url === `${environment.apiBaseUrl}/users` && request.method === "GET").flush({
          content: [],
          page: 0,
          size: 20,
          totalElements: 0,
          totalPages: 0,
          first: true,
          last: true
        });
      });
      it("should toggle visibility of the administrator security password with its checkbox", () => {
        const fixture = TestBed.createComponent(App);
        const app = fixture.componentInstance;
        const admin = {
          userId: 11,
          firstName: "Admin",
          paternalLastName: "Principal",
          maternalLastName: "Sistema",
          doi: "12345678",
          doiType: "DNI",
          birthDate: "01/01/1990",
          gender: "OTHER",
          email: "admin@example.test",
          phone: "016543210",
          mobilePhone: "912345678",
          userType: "ADMIN",
          profession: "Administrador",
          status: "A"
        };
        app.adminProfile.set(admin);
        app.view.set("admin");
        app.adminSection.set("profile");
        app.securityForm.controls.password.setValue("admin-secret");
        fixture.detectChanges();
        const passwordInput = fixture.nativeElement.querySelector("#profile-password");
        const visibilityCheckbox = fixture.nativeElement.querySelector('.terms-checkbox input[type="checkbox"]');
        expect(passwordInput.type).toBe("password");
        visibilityCheckbox.click();
        fixture.detectChanges();
        expect(passwordInput.type).toBe("text");
        expect(visibilityCheckbox.checked).toBeTrue();
      });
    });
  }
});
export default require_app_spec();
/*! Bundled license information:

@angular/common/fesm2022/module.mjs:
@angular/common/fesm2022/http.mjs:
@angular/common/fesm2022/http/testing.mjs:
@angular/forms/fesm2022/forms.mjs:
  (**
   * @license Angular v20.3.29
   * (c) 2010-2025 Google LLC. https://angular.dev/
   * License: MIT
   *)
*/
//# sourceMappingURL=spec-app.spec.js.map
