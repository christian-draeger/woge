package dev.woge.html;

import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

@State(Scope.Thread)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 2, time = 250, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 3, time = 250, timeUnit = TimeUnit.MILLISECONDS)
@Fork(1)
public class HtmlSinkBenchmark {
    private static final String ROW_START = "<li class=\"task-row grid gap-2\">";
    private static final String ROW_TEXT = "Escaped project task &amp; current status";
    private static final String ROW_END = "</li>";

    @Param({"32", "512"})
    public int rows;

    @Benchmark
    public String buffered() {
        var sink = new BufferedHtmlSink(256);
        writeRows(sink);
        return sink.content();
    }

    @Benchmark
    public long streaming() {
        var downstream = new CountingSink();
        var sink = new StreamingHtmlSink(downstream, 8 * 1024);
        writeRows(sink);
        sink.flush();
        return downstream.characters;
    }

    private void writeRows(HtmlSink sink) {
        for (int index = 0; index < rows; index++) {
            sink.write(ROW_START);
            sink.write(ROW_TEXT);
            sink.write(ROW_END);
        }
    }

    private static final class CountingSink implements HtmlSink {
        private long characters;

        @Override
        public void write(String value) {
            characters += value.length();
        }
    }
}
