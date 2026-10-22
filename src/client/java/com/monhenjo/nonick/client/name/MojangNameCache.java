package com.monhenjo.nonick.client.name;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Asks Mojang which account owns a UUID. The session server returns the
 * current account name even when a server rewrote the profile name in the
 * player info packet, which is the only way to recover those nicknames.
 *
 * <p>Requests are serialized and spaced out, and answers are cached on disk so
 * reconnecting does not hammer the API.
 */
public final class MojangNameCache {
	private static final String[] ENDPOINTS = {
		"https://sessionserver.mojang.com/session/minecraft/profile/",
		"https://api.minecraftservices.com/minecraft/profile/lookup/",
		"https://api.mojang.com/minecraft/profile/lookup/"
	};
	private static final String USER_AGENT = "NoNick/1.0.0 (Fabric client mod)";
	private static final Type CACHE_TYPE = new TypeToken<Map<String, Entry>>() {
	}.getType();

	public record Result(UUID id, String name, boolean missing, boolean retry) {
	}

	private static final class Entry {
		String name;
		long fetchedAt;
		boolean missing;
	}

	private final Path file;
	private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
	private final HttpClient http = HttpClient.newBuilder()
		.connectTimeout(Duration.ofSeconds(10))
		.followRedirects(HttpClient.Redirect.NORMAL)
		.build();
	private final ExecutorService executor = Executors.newSingleThreadExecutor(task -> {
		Thread thread = new Thread(task, "NoNick-Mojang");
		thread.setDaemon(true);
		return thread;
	});
	private final Map<UUID, Entry> cache = new ConcurrentHashMap<>();
	private final Set<UUID> inFlight = ConcurrentHashMap.newKeySet();
	private final ConcurrentLinkedQueue<Result> results = new ConcurrentLinkedQueue<>();
	private final AtomicBoolean dirty = new AtomicBoolean();
	private volatile long backoffUntil;
	private volatile long lastSave;

	public MojangNameCache(Path configDir) {
		this.file = configDir.resolve("nonick-names.json");
		load();
	}

	public String cachedName(UUID id, int cacheHours) {
		Entry entry = cache.get(id);
		if (entry == null || entry.missing || entry.name == null || entry.name.isBlank()) {
			return null;
		}
		if (!fresh(entry, cacheHours)) {
			return null;
		}
		return entry.name;
	}

	public boolean hasFreshAnswer(UUID id, int cacheHours) {
		Entry entry = cache.get(id);
		return entry != null && fresh(entry, cacheHours);
	}

	public void request(UUID id, int cacheHours) {
		if (hasFreshAnswer(id, cacheHours) || !inFlight.add(id)) {
			return;
		}
		if (id.version() != 4) {
			// Offline-mode UUIDs are derived from the name, so Mojang has no
			// account to look up. Do not waste a request.
			cache.put(id, missingEntry());
			dirty.set(true);
			inFlight.remove(id);
			results.add(new Result(id, null, true, false));
			return;
		}
		if (System.currentTimeMillis() < backoffUntil) {
			inFlight.remove(id);
			return;
		}

		executor.submit(() -> fetch(id));
	}

	public Result poll() {
		return results.poll();
	}

	public void saveIfDirty() {
		if (!dirty.get() || System.currentTimeMillis() - lastSave < 30_000L) {
			return;
		}
		save();
	}

	public void save() {
		lastSave = System.currentTimeMillis();
		dirty.set(false);
		Map<String, Entry> serializable = new ConcurrentHashMap<>();
		cache.forEach((id, entry) -> serializable.put(id.toString(), entry));
		try {
			Files.createDirectories(file.getParent());
			Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
			Files.writeString(tmp, gson.toJson(serializable, CACHE_TYPE));
			Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (IOException ignored) {
			// A cache that cannot be written is not worth crashing a game over.
		}
	}

	public void clear() {
		cache.clear();
		inFlight.clear();
		results.clear();
		dirty.set(true);
		save();
	}

	public void forceRequest(UUID id) {
		cache.remove(id);
		backoffUntil = 0L;
		request(id, 1);
	}

	public int size() {
		return cache.size();
	}

	public void close() {
		save();
		executor.shutdownNow();
	}

	private void fetch(UUID id) {
		try {
			if (System.currentTimeMillis() < backoffUntil) {
				results.add(new Result(id, null, false, true));
				return;
			}

			String stripped = id.toString().replace("-", "");
			boolean sawMissing = false;

			for (String endpoint : ENDPOINTS) {
				try {
					Thread.sleep(120L);
					HttpRequest request = HttpRequest.newBuilder()
						.uri(URI.create(endpoint + stripped))
						.timeout(Duration.ofSeconds(10))
						.header("User-Agent", USER_AGENT)
						.header("Accept", "application/json")
						.GET()
						.build();
					HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());

					if (response.statusCode() == 200) {
						JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
						String name = json.has("name") ? json.get("name").getAsString() : null;
						if (name != null && !name.isBlank()) {
							results.add(new Result(id, name, false, false));
							return;
						}
						sawMissing = true;
					} else if (response.statusCode() == 204 || response.statusCode() == 404) {
						sawMissing = true;
					} else if (response.statusCode() == 429) {
						backoffUntil = System.currentTimeMillis() + 60_000L;
					}
				} catch (InterruptedException interrupted) {
					Thread.currentThread().interrupt();
					return;
				} catch (Exception ignored) {
					// Try the next official endpoint.
				}
			}

			if (sawMissing) {
				results.add(new Result(id, null, true, false));
			} else {
				backoffUntil = System.currentTimeMillis() + 60_000L;
				results.add(new Result(id, null, false, true));
			}
		} finally {
			inFlight.remove(id);
		}
	}

	public void applyResult(Result result) {
		if (result.retry()) {
			return;
		}
		Entry entry = new Entry();
		entry.name = result.name();
		entry.missing = result.missing() || result.name() == null;
		entry.fetchedAt = System.currentTimeMillis();
		cache.put(result.id(), entry);
		dirty.set(true);
	}

	private void load() {
		if (!Files.isRegularFile(file)) {
			return;
		}
		try {
			String json = Files.readString(file);
			Map<String, Entry> loaded = gson.fromJson(json, CACHE_TYPE);
			if (loaded == null) {
				return;
			}
			loaded.forEach((key, entry) -> {
				if (entry == null || entry.name == null && !entry.missing) {
					return;
				}
				try {
					cache.put(UUID.fromString(key), entry);
				} catch (IllegalArgumentException ignored) {
					// Ignore hand-edited keys that are not UUIDs.
				}
			});
		} catch (Exception ignored) {
			// Corrupt cache, start fresh.
		}
	}

	private static boolean fresh(Entry entry, int cacheHours) {
		long ttl = Math.max(1, cacheHours) * 3_600_000L;
		return System.currentTimeMillis() - entry.fetchedAt < ttl;
	}

	private static Entry missingEntry() {
		Entry entry = new Entry();
		entry.missing = true;
		entry.fetchedAt = System.currentTimeMillis();
		return entry;
	}
}
