package dev.salamiclient.util;

import org.jetbrains.annotations.Nullable;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;

/**
 * Antworten der Salami-Server mit Obergrenze: ein kaputter oder übernommener Server soll das Spiel nicht mit einer
 * riesigen Antwort oder einer endlosen Zeile auf der Live-Verbindung den Speicher füllen lassen.
 */
public final class LimitedBody {
	private LimitedBody() {}

	/** Antwort oder Zeile größer als erlaubt. */
	public static final class TooLargeException extends IOException {
		TooLargeException(String message) {
			super(message);
		}
	}

	/** Wie {@code BodyHandlers.ofString(UTF_8)}, bricht aber ab, sobald mehr als {@code maxBytes} ankommen. */
	public static HttpResponse.BodyHandler<String> utf8(int maxBytes) {
		return info -> new Collector(maxBytes, info.headers().firstValueAsLong("content-length").orElse(-1L));
	}

	private static final class Collector implements HttpResponse.BodySubscriber<String> {
		private final int max;
		private final long declared;
		private final CompletableFuture<String> result = new CompletableFuture<>();
		private final ByteArrayOutputStream out = new ByteArrayOutputStream();
		private Flow.@Nullable Subscription subscription;

		Collector(int max, long declared) {
			this.max = max;
			this.declared = declared;
		}

		@Override
		public CompletionStage<String> getBody() {
			return result;
		}

		@Override
		public void onSubscribe(Flow.Subscription subscription) {
			this.subscription = subscription;
			if (declared > max) {
				fail();
				return;
			}
			subscription.request(Long.MAX_VALUE);
		}

		@Override
		public void onNext(List<ByteBuffer> items) {
			if (result.isDone()) {
				return;
			}
			for (ByteBuffer buffer : items) {
				int length = buffer.remaining();
				if ((long) out.size() + length > max) {
					fail();
					return;
				}
				byte[] chunk = new byte[length];
				buffer.get(chunk);
				out.write(chunk, 0, length);
			}
		}

		@Override
		public void onError(Throwable error) {
			result.completeExceptionally(error);
		}

		@Override
		public void onComplete() {
			result.complete(out.toString(StandardCharsets.UTF_8));
		}

		private void fail() {
			result.completeExceptionally(new TooLargeException("Antwort größer als " + max + " Bytes"));
			Flow.Subscription current = subscription;
			if (current != null) {
				current.cancel();
			}
		}
	}

	/**
	 * Liest UTF-8-Zeilen (Ende {@code \n}, {@code \r\n} oder {@code \r}) wie {@code BufferedReader.readLine()}, aber
	 * eine Zeile über {@code maxBytes} beendet das Lesen mit {@link TooLargeException}.
	 */
	public static final class LineReader {
		private final InputStream in;
		private final int max;
		private final ByteArrayOutputStream line = new ByteArrayOutputStream();
		private boolean skipLf;

		public LineReader(InputStream in, int maxBytes) {
			this.in = new BufferedInputStream(in);
			this.max = maxBytes;
		}

		/** Nächste Zeile ohne Zeilenende; {@code null} am Ende des Streams. */
		public @Nullable String readLine() throws IOException {
			line.reset();
			while (true) {
				int b = in.read();
				if (b == -1) {
					return line.size() == 0 ? null : line.toString(StandardCharsets.UTF_8);
				}
				if (skipLf) {
					skipLf = false;
					if (b == '\n') {
						continue;
					}
				}
				if (b == '\n') {
					return line.toString(StandardCharsets.UTF_8);
				}
				if (b == '\r') {
					skipLf = true;
					return line.toString(StandardCharsets.UTF_8);
				}
				if (line.size() >= max) {
					throw new TooLargeException("Zeile länger als " + max + " Bytes");
				}
				line.write(b);
			}
		}
	}
}
