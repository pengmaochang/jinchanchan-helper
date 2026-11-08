package local.jcc.probe;

import android.content.Context;
import android.os.SystemClock;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;

/** Bounded, local text diagnostics survive logcat eviction; no screenshots or network. */
final class AssistTrace implements AutoCloseable {
    private final File file;
    private final ExecutorService writer = Executors.newSingleThreadExecutor();
    AssistTrace(Context context) { file = new File(context.getFilesDir(), "assist-trace.log"); }
    void record(String text) {
        final String line = System.currentTimeMillis() + " uptime=" + SystemClock.elapsedRealtime() + " " + text.replace('\n', ' ') + "\n";
        try { writer.execute(() -> {
            try {
                if (file.length() > 262144) {
                    File old = new File(file.getParentFile(), "assist-trace.previous.log");
                    if (old.exists() && !old.delete()) return;
                    if (!file.renameTo(old)) return;
                }
                try (FileOutputStream out = new FileOutputStream(file, true)) { out.write(line.getBytes(StandardCharsets.UTF_8)); }
            } catch (IOException ignored) {}
        }); } catch (RejectedExecutionException ignored) {}
    }
    @Override public void close() { writer.shutdown(); }
}
