package com.chessfantasy.israel.api;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.chessfantasy.israel.model.Player;
import com.google.gson.Gson;

import java.io.File;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Downloads FIDE's full monthly rating list and keeps only Israeli
 * (federation = ISR) rated players — potentially thousands, from the elite
 * down to club and junior players. The list is an XML file inside a zip, so it
 * is streamed and filtered on the fly (never fully held in memory) by reading
 * one &lt;player&gt; block at a time, and the ISR result is cached to
 * app-private storage so it is downloaded only when the user asks.
 *
 * FIDE XML list: https://ratings.fide.com/download/standard_rating_list_xml.zip
 */
public class FideFullListLoader {

    private static final String LIST_URL =
            "https://ratings.fide.com/download/standard_rating_list_xml.zip";
    private static final String CACHE_FILE = "isr_pool.json";
    /** Safety cap on how many players we keep, to bound memory/storage. */
    private static final int MAX_PLAYERS = 30000;

    public interface ProgressListener {
        void onProgress(long bytesRead, long totalBytes);
    }

    public interface DoneListener {
        void onDone(int isrCount, String error);
    }

    /** Persisted shape of the cached pool. */
    public static class Pool {
        public long updatedAt;
        public List<Player> players = new ArrayList<>();
    }

    private final Context appContext;
    private final Gson gson = new Gson();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public FideFullListLoader(Context context) {
        this.appContext = context.getApplicationContext();
    }

    private File cacheFile() {
        return new File(appContext.getFilesDir(), CACHE_FILE);
    }

    public boolean isCached() {
        return cacheFile().exists();
    }

    /** Loads the cached ISR pool, or an empty pool if none/failed. */
    public Pool loadCached() {
        File file = cacheFile();
        if (!file.exists()) return new Pool();
        try (Reader reader = new InputStreamReader(new java.io.FileInputStream(file),
                StandardCharsets.UTF_8)) {
            Pool pool = gson.fromJson(reader, Pool.class);
            if (pool == null) pool = new Pool();
            if (pool.players == null) pool.players = new ArrayList<>();
            return pool;
        } catch (Exception e) {
            return new Pool();
        }
    }

    /**
     * Downloads + filters + caches on a background thread. Callbacks are posted
     * to the main thread. On success {@code error} is null.
     */
    public void downloadAsync(ProgressListener progress, DoneListener done) {
        executor.execute(() -> {
            String error = null;
            int count = 0;
            HttpURLConnection connection = null;
            try {
                URL url = new URL(LIST_URL);
                connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(60000);
                connection.setInstanceFollowRedirects(true);
                connection.setRequestProperty("User-Agent", "ChessFantasyIsrael/1.0");
                int code = connection.getResponseCode();
                if (code != 200) {
                    error = "FIDE download failed (HTTP " + code + ")";
                } else {
                    long total = connection.getContentLengthLong();
                    try (InputStream raw = connection.getInputStream();
                         CountingInputStream counting = new CountingInputStream(raw,
                                 (read) -> mainHandler.post(() -> progress.onProgress(read, total)));
                         ZipInputStream zip = new ZipInputStream(counting)) {
                        ZipEntry entry = zip.getNextEntry();
                        if (entry == null) {
                            error = "FIDE archive was empty";
                        } else {
                            Reader reader = new InputStreamReader(zip, StandardCharsets.UTF_8);
                            List<Player> isr = parseIsrXml(reader, MAX_PLAYERS);
                            count = isr.size();
                            Pool pool = new Pool();
                            pool.updatedAt = System.currentTimeMillis();
                            pool.players = isr;
                            writeCache(pool);
                        }
                    }
                }
            } catch (Exception e) {
                error = "Couldn't download the FIDE list (" + e.getClass().getSimpleName() + ")";
            } finally {
                if (connection != null) connection.disconnect();
            }
            final String fError = error;
            final int fCount = count;
            mainHandler.post(() -> done.onDone(fCount, fError));
        });
    }

    private void writeCache(Pool pool) throws IOException {
        File tmp = new File(appContext.getFilesDir(), CACHE_FILE + ".tmp");
        try (Writer writer = new java.io.OutputStreamWriter(
                new java.io.FileOutputStream(tmp), StandardCharsets.UTF_8)) {
            gson.toJson(pool, writer);
        }
        if (!tmp.renameTo(cacheFile())) {
            // Fallback: overwrite directly.
            try (Writer writer = new java.io.OutputStreamWriter(
                    new java.io.FileOutputStream(cacheFile()), StandardCharsets.UTF_8)) {
                gson.toJson(pool, writer);
            }
            tmp.delete();
        }
    }

    // ---------------------------------------------------------------- parsing

    /**
     * Streams the FIDE XML list from {@code reader}, returning the Israeli rated
     * players. Reads one &lt;player&gt;…&lt;/player&gt; block at a time so the
     * whole (large) document is never held in memory. Package-private for
     * testing.
     */
    static List<Player> parseIsrXml(Reader reader, int cap) throws IOException {
        List<Player> out = new ArrayList<>();
        StringBuilder buf = new StringBuilder();
        char[] chunk = new char[8192];
        final String close = "</player>";
        final String open = "<player";
        int n;
        while ((n = reader.read(chunk)) != -1) {
            buf.append(chunk, 0, n);
            int endIdx;
            while ((endIdx = buf.indexOf(close)) >= 0) {
                int startIdx = buf.indexOf(open);
                int cut = endIdx + close.length();
                if (startIdx < 0 || startIdx > endIdx) {
                    buf.delete(0, cut); // stray close tag, skip
                    continue;
                }
                String block = buf.substring(startIdx, cut);
                buf.delete(0, cut);
                Player p = fromBlock(block);
                if (p != null) {
                    out.add(p);
                    if (out.size() >= cap) return out;
                }
            }
            // Guard against an unterminated block bloating memory.
            if (buf.length() > 1_000_000) buf.delete(0, buf.length() - 100_000);
        }
        return out;
    }

    /** Builds a Player from one &lt;player&gt; block, or null if not an ISR rated player. */
    static Player fromBlock(String block) {
        if (!"ISR".equals(tag(block, "country"))) return null;
        Integer rating = parseInt(tag(block, "rating"));
        if (rating == null || rating <= 0) return null; // FIDE-rated players only
        long id = parseLong(tag(block, "fideid"));
        if (id <= 0) return null;

        Player p = new Player();
        p.fideId = id;
        p.id = "fide_" + id;
        p.name = displayName(unescape(tag(block, "name")));
        p.hebrewName = "";
        p.title = normalizeTitle(tag(block, "title"));
        p.rating = rating;
        p.ilId = 0;
        p.chessComUser = "";
        p.achievements = "";
        return p;
    }

    /** Value of the first &lt;name&gt;…&lt;/name&gt; element in a block, or "". */
    static String tag(String block, String name) {
        String open = "<" + name + ">";
        String close = "</" + name + ">";
        int i = block.indexOf(open);
        if (i < 0) return "";
        int j = block.indexOf(close, i + open.length());
        if (j < 0) return "";
        return block.substring(i + open.length(), j).trim();
    }

    static String unescape(String s) {
        if (s == null || s.indexOf('&') < 0) return s;
        return s.replace("&amp;", "&").replace("&apos;", "'").replace("&quot;", "\"")
                .replace("&lt;", "<").replace("&gt;", ">");
    }

    /** "Lastname, Firstname" -> "Firstname Lastname"; otherwise unchanged. */
    static String displayName(String raw) {
        if (raw == null) return "";
        String name = raw.trim();
        int comma = name.indexOf(',');
        if (comma > 0 && comma < name.length() - 1) {
            String last = name.substring(0, comma).trim();
            String first = name.substring(comma + 1).trim();
            if (!first.isEmpty() && !last.isEmpty()) return first + " " + last;
        }
        return name;
    }

    /** Keep only recognised FIDE titles; drop stray tokens. */
    static String normalizeTitle(String raw) {
        if (raw == null) return "";
        String t = raw.trim();
        switch (t) {
            case "GM": case "IM": case "FM": case "CM":
            case "WGM": case "WIM": case "WFM": case "WCM":
                return t;
            default:
                return "";
        }
    }

    private static Integer parseInt(String s) {
        if (s == null) return null;
        s = s.trim();
        if (s.isEmpty()) return null;
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static long parseLong(String s) {
        if (s == null) return 0;
        s = s.trim();
        if (s.isEmpty()) return 0;
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** Wraps a stream to report how many bytes have been read (for progress). */
    private static class CountingInputStream extends FilterInputStream {
        interface Counter {
            void onCount(long total);
        }

        private final Counter counter;
        private long total;
        private long lastReported;

        CountingInputStream(InputStream in, Counter counter) {
            super(in);
            this.counter = counter;
        }

        @Override
        public int read() throws IOException {
            int b = super.read();
            if (b >= 0) bump(1);
            return b;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            int n = super.read(b, off, len);
            if (n > 0) bump(n);
            return n;
        }

        private void bump(long n) {
            total += n;
            // Throttle callbacks to ~every 256 KB.
            if (total - lastReported >= 262144) {
                lastReported = total;
                counter.onCount(total);
            }
        }
    }
}
