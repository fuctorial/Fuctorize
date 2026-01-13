package ru.fuctorial.fuctorize.utils.http;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.ByteArrayOutputStream;
import java.io.FilterInputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.net.URLStreamHandlerFactory;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.Date;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Global HTTP hook that wraps java.net URL connections to capture request/response details.
 * Installed once via URL.setURLStreamHandlerFactory and delegates to the default handlers.
 */
public final class HttpSniffer implements URLStreamHandlerFactory {

    private static volatile boolean installed = false;
    private static final Deque<Capture> recent = new ArrayDeque<Capture>(8);
    private static final Object lock = new Object();

    private static final Charset UTF8 = StandardCharsets.UTF_8;
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private HttpSniffer() {}

    public static void installOnce() {
        if (installed) return;
        synchronized (HttpSniffer.class) {
            if (installed) return;
            try {
                URL.setURLStreamHandlerFactory(new HttpSniffer());
                installed = true;
                System.out.println("Fuctorize/HttpSniffer: URLStreamHandlerFactory installed.");
            } catch (Error e) {
                System.err.println("Fuctorize/HttpSniffer: Could not install factory (already set): " + e);
            } catch (Throwable t) {
                System.err.println("Fuctorize/HttpSniffer: Unexpected error installing factory: ");
                t.printStackTrace();
            }
        }
    }

    @Override
    public URLStreamHandler createURLStreamHandler(String protocol) {
        if ("http".equalsIgnoreCase(protocol) || "https".equalsIgnoreCase(protocol)) {
            try {
                final URLStreamHandler defaultHandler = createDefaultHandler(protocol);
                return new DelegatingHandler(defaultHandler);
            } catch (Throwable t) {
                System.err.println("Fuctorize/HttpSniffer: Failed to create delegating handler for protocol " + protocol);
            }
        }
        return null; // fallback to JVM defaults for other protocols
    }

    private static URLStreamHandler createDefaultHandler(String protocol) throws Exception {
        // Reflectively instantiate JRE default handlers to avoid compile-time dependency on sun.*
        String cls = "sun.net.www.protocol." + protocol + ".Handler";
        Class<?> c = Class.forName(cls);
        return (URLStreamHandler) c.newInstance();
    }

    private static class DelegatingHandler extends URLStreamHandler {
        private final URLStreamHandler delegate;
        DelegatingHandler(URLStreamHandler delegate) { this.delegate = delegate; }

        @Override
        protected URLConnection openConnection(URL u) throws IOException {
            try {
                URLConnection conn = new URL(null, u.toString(), delegate).openConnection();
                if (conn instanceof HttpURLConnection) {
                    return new LoggingHttpURLConnection((HttpURLConnection) conn);
                }
                return conn;
            } catch (IOException e) {
                throw e;
            } catch (Throwable t) {
                throw new IOException(t);
            }
        }
    }

    private static class LoggingHttpURLConnection extends HttpURLConnection {
        private final HttpURLConnection d;
        private final Map<String, List<String>> setHeaders = new LinkedHashMap<String, List<String>>();
        private final ByteArrayOutputStream reqBody = new ByteArrayOutputStream();
        private final ByteArrayOutputStream respBody = new ByteArrayOutputStream();
        private boolean captured = false;

        protected LoggingHttpURLConnection(HttpURLConnection delegate) {
            super(delegate.getURL());
            this.d = delegate;
        }

        private void recordHeader(String key, String value) {
            if (key == null || value == null) return;
            List<String> list = setHeaders.get(key);
            if (list == null) {
                list = new java.util.ArrayList<String>();
                setHeaders.put(key, list);
            }
            list.add(value);
        }

        private void ensureCapturedOnce() {
            if (captured) return;
            captured = true;
            Capture c = new Capture();
            c.time = System.currentTimeMillis();
            c.url = d.getURL() != null ? d.getURL().toString() : null;
            try { c.method = getRequestMethod(); } catch (Exception ignored) {}
            c.requestHeaders = new LinkedHashMap<String, List<String>>();
            // Merge headers set via API and getRequestProperties (if available)
            try {
                Map<String, List<String>> props = d.getRequestProperties();
                if (props != null) c.requestHeaders.putAll(props);
            } catch (Throwable ignored) {}
            if (!setHeaders.isEmpty()) {
                for (Map.Entry<String, List<String>> e : setHeaders.entrySet()) {
                    c.requestHeaders.put(e.getKey(), new java.util.ArrayList<String>(e.getValue()));
                }
            }
            c.requestBody = reqBody;
            c.connection = d;
            // Defer response fields until later
            synchronized (lock) {
                if (recent.size() >= 8) recent.removeFirst();
                recent.addLast(c);
            }
        }

        // --- Overrides delegating to 'd' with capture ---
        @Override public void connect() throws IOException { ensureCapturedOnce(); d.connect(); }
        @Override public void disconnect() { d.disconnect(); }
        @Override public boolean usingProxy() { return d.usingProxy(); }

        @Override public void setRequestMethod(String method) throws java.net.ProtocolException { d.setRequestMethod(method); this.method = method; }
        @Override public String getRequestMethod() { try { return d.getRequestMethod(); } catch (Throwable t) { return method; } }

        @Override public void setRequestProperty(String key, String value) { recordHeader(key, value); d.setRequestProperty(key, value); }
        @Override public void addRequestProperty(String key, String value) { recordHeader(key, value); d.addRequestProperty(key, value); }

        @Override public OutputStream getOutputStream() throws IOException {
            ensureCapturedOnce();
            final OutputStream os = d.getOutputStream();
            return new FilterOutputStream(os) {
                @Override public void write(int b) throws IOException { reqBody.write(b); out.write(b); }
                @Override public void write(byte[] b) throws IOException { if (b!=null) reqBody.write(b); out.write(b); }
                @Override public void write(byte[] b, int off, int len) throws IOException { if (b!=null) reqBody.write(b, off, len); out.write(b, off, len); }
            };
        }

        @Override public InputStream getInputStream() throws IOException {
            ensureCapturedOnce();
            try {
                InputStream is = d.getInputStream();
                updateResponseMeta();
                return new TeeInputStream(is, respBody);
            } catch (IOException e) {
                // Try error stream to still capture body
                InputStream err = d.getErrorStream();
                updateResponseMeta();
                if (err != null) return new TeeInputStream(err, respBody);
                throw e;
            }
        }

        private void updateResponseMeta() {
            try {
                Capture c = getCurrentCapture();
                if (c != null) {
                    if (c.responseCode == 0) {
                        try { c.responseCode = d.getResponseCode(); } catch (Throwable ignored) {}
                        try { c.responseMessage = d.getResponseMessage(); } catch (Throwable ignored) {}
                        try { c.responseHeaders = d.getHeaderFields(); } catch (Throwable ignored) {}
                        c.responseBody = respBody;
                    }
                }
            } catch (Throwable ignored) {}
        }

        private Capture getCurrentCapture() {
            synchronized (lock) {
                return recent.peekLast();
            }
        }

        @Override public int getResponseCode() throws IOException { ensureCapturedOnce(); int code = d.getResponseCode(); updateResponseMeta(); return code; }
        @Override public String getResponseMessage() throws IOException { ensureCapturedOnce(); String msg = d.getResponseMessage(); updateResponseMeta(); return msg; }
        @Override public Map<String, List<String>> getHeaderFields() { return d.getHeaderFields(); }

        // Delegate remaining getters/setters to 'd'
        @Override public Object getContent() throws IOException { return d.getContent(); }
        @Override public Object getContent(Class[] classes) throws IOException { return d.getContent(classes); }
        @Override public long getIfModifiedSince() { return d.getIfModifiedSince(); }
        @Override public InputStream getErrorStream() { return d.getErrorStream(); }
        @Override public long getLastModified() { return d.getLastModified(); }
        @Override public java.security.Permission getPermission() throws IOException { return d.getPermission(); }
        @Override public Map<String, List<String>> getRequestProperties() { return d.getRequestProperties(); }
        @Override public String getRequestProperty(String key) { return d.getRequestProperty(key); }
        @Override public URL getURL() { return d.getURL(); }
        @Override public boolean getDoInput() { return d.getDoInput(); }
        @Override public void setDoInput(boolean doinput) { d.setDoInput(doinput); }
        @Override public boolean getDoOutput() { return d.getDoOutput(); }
        @Override public void setDoOutput(boolean dooutput) { d.setDoOutput(dooutput); }
        @Override public boolean getAllowUserInteraction() { return d.getAllowUserInteraction(); }
        @Override public void setAllowUserInteraction(boolean allowuserinteraction) { d.setAllowUserInteraction(allowuserinteraction); }
        @Override public boolean getDefaultUseCaches() { return d.getDefaultUseCaches(); }
        @Override public void setDefaultUseCaches(boolean defaultusecaches) { d.setDefaultUseCaches(defaultusecaches); }
        @Override public boolean getUseCaches() { return d.getUseCaches(); }
        @Override public void setUseCaches(boolean usecaches) { d.setUseCaches(usecaches); }
        // URLConnection in Java 8 does not expose a dedicated accessor beyond getIfModifiedSince
        @Override public void setIfModifiedSince(long ifmodifiedsince) { d.setIfModifiedSince(ifmodifiedsince); }
        @Override public int getConnectTimeout() { return d.getConnectTimeout(); }
        @Override public void setConnectTimeout(int timeout) { d.setConnectTimeout(timeout); }
        @Override public int getReadTimeout() { return d.getReadTimeout(); }
        @Override public void setReadTimeout(int timeout) { d.setReadTimeout(timeout); }
    }

    private static class TeeInputStream extends FilterInputStream {
        private final OutputStream copy;
        TeeInputStream(InputStream in, OutputStream copy) { super(in); this.copy = copy; }
        @Override public int read() throws IOException { int b = super.read(); if (b >= 0) copy.write(b); return b; }
        @Override public int read(byte[] b, int off, int len) throws IOException { int n = super.read(b, off, len); if (n > 0) copy.write(b, off, n); return n; }
        @Override public void close() throws IOException { try { copy.flush(); } catch (Throwable ignored) {} super.close(); }
    }

    private static class Capture {
        long time;
        String url;
        String method;
        Map<String, List<String>> requestHeaders;
        ByteArrayOutputStream requestBody;
        int responseCode;
        String responseMessage;
        Map<String, List<String>> responseHeaders;
        ByteArrayOutputStream responseBody;
        HttpURLConnection connection;
    }

    public static String getLastCaptureSummary() {
        Capture c;
        synchronized (lock) {
            c = recent.peekLast();
        }
        if (c == null) return null;
        StringBuilder sb = new StringBuilder();
        sb.append("HTTP Capture\n");
        sb.append("Time: ").append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date(c.time))).append('\n');
        if (c.url != null) sb.append("URL: ").append(c.url).append('\n');
        if (c.method != null) sb.append("Method: ").append(c.method).append('\n');

        if (c.requestHeaders != null && !c.requestHeaders.isEmpty()) {
            sb.append("Request Headers:\n");
            for (Map.Entry<String, List<String>> e : c.requestHeaders.entrySet()) {
                String key = e.getKey(); List<String> vals = e.getValue();
                if (key == null) continue;
                if (vals == null || vals.isEmpty()) {
                    sb.append("  ").append(key).append(':').append('\n');
                } else {
                    for (String v : vals) sb.append("  ").append(key).append(": ").append(v).append('\n');
                }
            }
        }

        String reqText = toText(c.requestBody);
        if (reqText != null && !reqText.isEmpty()) {
            sb.append("Request Body:\n");
            sb.append(reqText).append('\n');
        }

        sb.append("Response: ").append(c.responseCode).append(' ');
        if (c.responseMessage != null) sb.append(c.responseMessage);
        sb.append('\n');

        if (c.responseHeaders != null && !c.responseHeaders.isEmpty()) {
            sb.append("Response Headers:\n");
            for (Map.Entry<String, List<String>> e : c.responseHeaders.entrySet()) {
                String key = e.getKey(); List<String> vals = e.getValue();
                if (key == null) continue;
                if (vals == null || vals.isEmpty()) {
                    sb.append("  ").append(key).append(':').append('\n');
                } else {
                    for (String v : vals) sb.append("  ").append(key).append(": ").append(v).append('\n');
                }
            }
        }

        String respText = toText(c.responseBody);
        if (respText != null && !respText.isEmpty()) {
            sb.append("Response Body:\n");
            sb.append(respText).append('\n');
        }

        return sb.toString();
    }

    public static String getLastCaptureJson() {
        Capture c;
        synchronized (lock) {
            c = recent.peekLast();
        }
        if (c == null) return null;
        JsonObject root = new JsonObject();
        root.addProperty("time", c.time);
        if (c.url != null) root.addProperty("url", c.url);
        if (c.method != null) root.addProperty("method", c.method);

        JsonObject req = new JsonObject();
        if (c.requestHeaders != null && !c.requestHeaders.isEmpty()) {
            req.add("headers", mapToJson(c.requestHeaders));
        }
        String reqText = toText(c.requestBody);
        if (reqText != null && !reqText.isEmpty()) req.addProperty("body", reqText);
        root.add("request", req);

        JsonObject resp = new JsonObject();
        resp.addProperty("code", c.responseCode);
        if (c.responseMessage != null) resp.addProperty("message", c.responseMessage);
        if (c.responseHeaders != null && !c.responseHeaders.isEmpty()) {
            resp.add("headers", mapToJson(c.responseHeaders));
        }
        String respText = toText(c.responseBody);
        if (respText != null && !respText.isEmpty()) resp.addProperty("body", respText);
        root.add("response", resp);

        return GSON.toJson(root);
    }

    private static JsonObject mapToJson(Map<String, List<String>> map) {
        JsonObject obj = new JsonObject();
        for (Map.Entry<String, List<String>> e : map.entrySet()) {
            String key = e.getKey();
            if (key == null) continue;
            List<String> vals = e.getValue();
            if (vals == null) {
                obj.add(key, new JsonArray());
            } else {
                JsonArray arr = new JsonArray();
                for (String v : vals) arr.add(v);
                obj.add(key, arr);
            }
        }
        return obj;
    }

    private static String toText(ByteArrayOutputStream baos) {
        if (baos == null) return null;
        byte[] data = baos.toByteArray();
        if (data.length == 0) return null;
        // Prefer UTF-8 printable text; otherwise return HEX trimmed
        String asText = new String(data, UTF8);
        if (isMostlyPrintable(asText)) {
            if (asText.length() > 8000) return asText.substring(0, 8000) + "...";
            return asText;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(data.length, 4096); i++) {
            sb.append(String.format("%02X ", data[i]));
        }
        if (data.length > 4096) sb.append("...");
        return "HEX: " + sb.toString().trim();
    }

    private static boolean isMostlyPrintable(String s) {
        if (s == null || s.isEmpty()) return false;
        int printable = 0;
        int len = s.length();
        for (int i = 0; i < len; i++) {
            char c = s.charAt(i);
            if ((c >= 32 && c < 127) || Character.isWhitespace(c)) printable++;
        }
        return printable > (len * 0.6);
    }
}
