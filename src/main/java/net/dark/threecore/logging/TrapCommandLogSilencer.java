package net.dark.threecore.logging;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.filter.AbstractFilter;
import org.apache.logging.log4j.message.Message;

import java.util.regex.Pattern;

public final class TrapCommandLogSilencer extends AbstractFilter {
    private static final Pattern EMPTY_ENTITY_TELEPORT = Pattern.compile(
            "^Teleported\\s+to -?\\d+(?:\\.\\d+)?, -?\\d+(?:\\.\\d+)?, -?\\d+(?:\\.\\d+)?$"
    );
    private static final Pattern TELEPORT_FEEDBACK = Pattern.compile(
            "^Teleported\\b.*\\bto -?\\d+(?:\\.\\d+)?, -?\\d+(?:\\.\\d+)?, -?\\d+(?:\\.\\d+)?$"
    );
    private static final Pattern COMMAND_DAMAGE = Pattern.compile("^Applied \\d+(?:\\.\\d+)? damage to .+$");

    private final LoggerContext context;
    private final LoggerConfig rootLogger;

    private TrapCommandLogSilencer(LoggerContext context, LoggerConfig rootLogger) {
        super(Result.NEUTRAL, Result.DENY);
        this.context = context;
        this.rootLogger = rootLogger;
    }

    public static TrapCommandLogSilencer install() {
        LoggerContext context = (LoggerContext) LogManager.getContext(false);
        LoggerConfig root = context.getConfiguration().getRootLogger();
        TrapCommandLogSilencer filter = new TrapCommandLogSilencer(context, root);
        root.addFilter(filter);
        context.updateLoggers();
        return filter;
    }

    public void shutdown() {
        rootLogger.removeFilter(this);
        context.updateLoggers();
    }

    @Override
    public Result filter(LogEvent event) {
        if (event == null) return Result.NEUTRAL;
        Message message = event.getMessage();
        if (message == null) return Result.NEUTRAL;
        return shouldHide(message.getFormattedMessage()) ? Result.DENY : Result.NEUTRAL;
    }

    private static boolean shouldHide(String message) {
        if (message == null || message.isBlank()) return false;
        String text = normalizeCommandFeedback(message);
        return text.startsWith("Changed the block at ")
                || text.startsWith("Modified entity data of ")
                || text.startsWith("Removed effect Slowness from ")
                || text.startsWith("Successfully filled ")
                || EMPTY_ENTITY_TELEPORT.matcher(text).matches()
                || TELEPORT_FEEDBACK.matcher(text).matches()
                || COMMAND_DAMAGE.matcher(text).matches();
    }

    private static String normalizeCommandFeedback(String message) {
        String text = message.trim();
        int commandPrefix = text.indexOf("[@:");
        if (commandPrefix >= 0) {
            int start = commandPrefix + 3;
            int end = text.indexOf(']', start);
            if (end > start) return text.substring(start, end).trim();
            return text.substring(start).trim();
        }
        if (text.startsWith("[") && text.endsWith("]")) {
            int colon = text.indexOf(':');
            if (colon >= 0 && colon + 1 < text.length() - 1) {
                return text.substring(colon + 1, text.length() - 1).trim();
            }
        }
        return text;
    }
}
