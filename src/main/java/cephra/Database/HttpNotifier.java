package cephra.Database;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * HttpNotifier — fire-and-forget HTTP POST to notify.php.
 *
 * Whenever Java makes a significant state change (ticket status,
 * bay status, charging complete) it calls HttpNotifier.send() so
 * the PHP web side learns about it immediately instead of waiting
 * for the next poll cycle.
 *
 * All calls are async and non-blocking — failures are logged but
 * never throw, so the Java app is never affected by web-side issues.
 */
public final class HttpNotifier {

    private static final String NOTIFY_URL =
            "http://127.0.0.1/Cephra/Appweb/notify.php";

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    private HttpNotifier() {}

    /**
     * Notify the web layer of a ticket status change.
     */
    public static void ticketStatusChanged(String ticketId, String username,
                                           String oldStatus, String newStatus) {
        String payload = String.format(
            "{\"event_type\":\"ticket_status_changed\","
          + "\"payload\":{\"ticket_id\":\"%s\",\"username\":\"%s\","
          + "\"old_status\":\"%s\",\"new_status\":\"%s\"}}",
            escape(ticketId), escape(username),
            escape(oldStatus), escape(newStatus)
        );
        sendAsync(payload);
    }

    /**
     * Notify the web layer of a bay status change.
     */
    public static void bayStatusChanged(String bayNumber, String oldStatus,
                                        String newStatus, String username) {
        String payload = String.format(
            "{\"event_type\":\"bay_status_changed\","
          + "\"payload\":{\"bay_number\":\"%s\","
          + "\"old_status\":\"%s\",\"new_status\":\"%s\","
          + "\"current_username\":\"%s\"}}",
            escape(bayNumber), escape(oldStatus),
            escape(newStatus), escape(username == null ? "" : username)
        );
        sendAsync(payload);
    }

    /**
     * Notify the web layer that charging completed for a user.
     */
    public static void chargingCompleted(String ticketId, String username) {
        String payload = String.format(
            "{\"event_type\":\"charging_completed\","
          + "\"payload\":{\"ticket_id\":\"%s\",\"username\":\"%s\"}}",
            escape(ticketId), escape(username)
        );
        sendAsync(payload);
    }

    // ----------------------------------------------------------------

    private static void sendAsync(String jsonBody) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(NOTIFY_URL))
                    .timeout(Duration.ofSeconds(3))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            CLIENT.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                  .exceptionally(ex -> {
                      // Web side unavailable — log quietly, never crash Java
                      System.err.println("HttpNotifier: " + ex.getMessage());
                      return null;
                  });
        } catch (Exception e) {
            System.err.println("HttpNotifier: failed to build request: " + e.getMessage());
        }
    }

    /** Minimal JSON string escaping for the values we embed. */
    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }
}
