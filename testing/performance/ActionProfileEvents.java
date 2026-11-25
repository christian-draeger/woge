import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedStackTrace;
import jdk.jfr.consumer.RecordingFile;
import java.nio.file.Path;
import java.time.Instant;
import java.util.stream.Collectors;

/** Extracts only timed GC and allocation events, without process properties or request data. */
class ActionProfileEvents {
    public static void main(String[] args) throws Exception {
        if (args.length != 3) {
            throw new IllegalArgumentException("Expected recording, measured start, measured end");
        }
        Instant start = Instant.parse(args[1]);
        Instant end = Instant.parse(args[2]);
        try (var input = new RecordingFile(Path.of(args[0]))) {
            while (input.hasMoreEvents()) {
                var event = input.readEvent();
                Instant observed = event.getStartTime();
                if (observed.isBefore(start) || !observed.isBefore(end)) continue;
                switch (event.getEventType().getName()) {
                    case "jdk.ObjectAllocationSample" -> printAllocation(event);
                    case "jdk.GarbageCollection" -> System.out.printf(
                            "gc\t%s\t%d\t%s\t%n",
                            observed, event.getDuration().toNanos(), event.getString("name")
                    );
                    default -> { }
                }
            }
        }
    }

    private static void printAllocation(RecordedEvent event) {
        RecordedStackTrace trace = event.getStackTrace();
        String frames = trace == null ? "" : trace.getFrames().stream()
                .map(frame -> frame.getMethod().getType().getName() + "#"
                        + frame.getMethod().getName() + ":" + frame.getLineNumber())
                .collect(Collectors.joining(";"));
        System.out.printf(
                "alloc\t%s\t%d\t%s\t%s%n",
                event.getStartTime(), event.getLong("weight"), event.getClass("objectClass").getName(), frames
        );
    }
}
