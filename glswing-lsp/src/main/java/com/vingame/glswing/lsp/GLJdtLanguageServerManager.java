package com.vingame.glswing.lsp;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.prefs.Preferences;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;

/** Finds, installs and launches the Eclipse JDT Language Server. */
public final class GLJdtLanguageServerManager {
	public static final String DISTRIBUTION_VERSION = "1.60.0";
	private static final String ARCHIVE = "jdt-language-server-1.60.0-202606262232.tar.gz";
	private static final URI DOWNLOAD = URI.create(
			"https://download.eclipse.org/jdtls/milestones/1.60.0/" + ARCHIVE);
	private static final String INSTALLATION_KEY = "jdtls.installation";
	private final Preferences preferences = Preferences.userNodeForPackage(
			GLJdtLanguageServerManager.class);

	/** Returns the stored managed or user-selected installation when it is valid. */
	public Optional<Path> findInstallation() {
		String stored = preferences.get(INSTALLATION_KEY, "");
		if (!stored.isBlank()) {
			Path candidate = Path.of(stored).toAbsolutePath().normalize();
			if (isInstallation(candidate)) return Optional.of(candidate);
		}
		Path managed = managedDirectory();
		return isInstallation(managed) ? Optional.of(managed) : Optional.empty();
	}

	/** Validates and remembers an extracted JDT LS installation directory. */
	public void selectInstallation(Path directory) {
		Path normalized = directory.toAbsolutePath().normalize();
		if (!isInstallation(normalized))
			throw new IllegalArgumentException("The directory is not a JDT LS installation: "
					+ normalized);
		preferences.put(INSTALLATION_KEY, normalized.toString());
	}

	/** Downloads the pinned Eclipse distribution, verifies SHA-256 and extracts it. */
	public Path install(Consumer<String> progress) throws IOException, InterruptedException {
		Consumer<String> report = progress == null ? value -> { } : progress;
		Path destination = managedDirectory();
		if (isInstallation(destination)) { selectInstallation(destination); return destination; }
		Files.createDirectories(destination.getParent());
		Path work = Files.createTempDirectory(destination.getParent(), "jdtls-install-");
		Path archive = work.resolve(ARCHIVE);
		try {
			report.accept("Downloading JDT LS " + DISTRIBUTION_VERSION + " …");
			HttpClient client = HttpClient.newBuilder().followRedirects(
					HttpClient.Redirect.NORMAL).build();
			download(client, DOWNLOAD, archive);
			String expected = downloadText(client, URI.create(DOWNLOAD + ".sha256"))
					.strip().split("\\s+", 2)[0].toLowerCase(Locale.ROOT);
			report.accept("Verifying download …");
			String actual = sha256(archive);
			if (!actual.equals(expected)) throw new IOException(
					"JDT LS checksum mismatch; expected " + expected + " but got " + actual);
			report.accept("Extracting JDT LS …");
			extract(archive, work.resolve("content"));
			Path content = work.resolve("content");
			if (!isInstallation(content))
				throw new IOException("Downloaded archive does not contain a JDT LS installation");
			try { Files.move(content, destination, StandardCopyOption.ATOMIC_MOVE); }
			catch (java.nio.file.AtomicMoveNotSupportedException failure) {
				Files.move(content, destination);
			}
			selectInstallation(destination);
			report.accept("JDT LS installed");
			return destination;
		} finally { deleteTree(work); }
	}

	/** Creates the complete platform-specific server command for a workspace. */
	public List<String> command(Path installation, Path workspace) throws IOException {
		if (Runtime.version().feature() < 21)
			throw new IOException("JDT LS requires Java 21 or newer; this application uses Java "
					+ Runtime.version().feature());
		Path root = installation.toAbsolutePath().normalize();
		if (!isInstallation(root)) throw new IOException("Invalid JDT LS installation: " + root);
		Path launcher;
		try (var files = Files.list(root.resolve("plugins"))) {
			launcher = files.filter(path -> path.getFileName().toString()
					.startsWith("org.eclipse.equinox.launcher_") && path.toString().endsWith(".jar"))
					.sorted().reduce((first, second) -> second)
					.orElseThrow(() -> new IOException("JDT LS launcher JAR is missing"));
		}
		Path data = applicationDataDirectory().resolve("jdtls-workspaces")
				.resolve(Integer.toUnsignedString(workspace.toAbsolutePath().normalize().hashCode(), 36));
		Files.createDirectories(data);
		Path logging = applicationDataDirectory().resolve("jdtls-logging.properties");
		Files.createDirectories(logging.getParent());
		if (!Files.isRegularFile(logging))
			Files.writeString(logging, ".level=WARNING\n", StandardCharsets.UTF_8);
		String platform = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
		String config = platform.contains("win") ? "config_win"
				: platform.contains("mac") ? "config_mac" : "config_linux";
		return List.of(javaExecutable().toString(),
				"-Djava.util.logging.config.file=" + logging,
				"-Declipse.application=org.eclipse.jdt.ls.core.id1",
				"-Dosgi.bundles.defaultStartLevel=4",
				"-Declipse.product=org.eclipse.jdt.ls.core.product",
				"-Dlog.level=WARNING", "-Xmx1G", "--add-modules=ALL-SYSTEM",
				"--add-opens", "java.base/java.util=ALL-UNNAMED",
				"--add-opens", "java.base/java.lang=ALL-UNNAMED",
				"-jar", launcher.toString(), "-configuration", root.resolve(config).toString(),
				"-data", data.toString());
	}

	public Path managedDirectory() {
		return applicationDataDirectory().resolve("language-servers").resolve("jdtls")
				.resolve(DISTRIBUTION_VERSION);
	}

	private static boolean isInstallation(Path root) {
		if (!Files.isDirectory(root.resolve("plugins")) || !Files.isDirectory(root.resolve("config_win")))
			return false;
		try (var files = Files.list(root.resolve("plugins"))) {
			return files.anyMatch(path -> path.getFileName().toString()
					.startsWith("org.eclipse.equinox.launcher_") && path.toString().endsWith(".jar"));
		} catch (IOException failure) { return false; }
	}

	private static Path applicationDataDirectory() {
		String local = System.getenv("LOCALAPPDATA");
		if (local != null && !local.isBlank()) return Path.of(local).resolve("GLSwing");
		return Path.of(System.getProperty("user.home"), ".local", "share", "glswing");
	}

	private static Path javaExecutable() throws IOException {
		String executable = System.getProperty("os.name", "").toLowerCase(Locale.ROOT)
				.contains("win") ? "java.exe" : "java";
		Path java = Path.of(System.getProperty("java.home"), "bin", executable);
		if (!Files.isRegularFile(java)) throw new IOException("Java executable is missing: " + java);
		return java;
	}

	private static void download(HttpClient client, URI uri, Path target)
			throws IOException, InterruptedException {
		HttpResponse<Path> response = client.send(HttpRequest.newBuilder(uri).build(),
				HttpResponse.BodyHandlers.ofFile(target));
		if (response.statusCode() / 100 != 2)
			throw new IOException("Download failed with HTTP " + response.statusCode());
	}

	private static String downloadText(HttpClient client, URI uri)
			throws IOException, InterruptedException {
		HttpResponse<String> response = client.send(HttpRequest.newBuilder(uri).build(),
				HttpResponse.BodyHandlers.ofString());
		if (response.statusCode() / 100 != 2)
			throw new IOException("Checksum download failed with HTTP " + response.statusCode());
		return response.body();
	}

	private static String sha256(Path file) throws IOException {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			try (InputStream input = Files.newInputStream(file)) {
				byte[] buffer = new byte[64 * 1024];
				for (int read; (read = input.read(buffer)) >= 0;) digest.update(buffer, 0, read);
			}
			return HexFormat.of().formatHex(digest.digest());
		} catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
	}

	private static void extract(Path archive, Path destination) throws IOException {
		Files.createDirectories(destination);
		try (InputStream file = new BufferedInputStream(Files.newInputStream(archive));
				GzipCompressorInputStream gzip = new GzipCompressorInputStream(file);
				TarArchiveInputStream tar = new TarArchiveInputStream(gzip)) {
			for (TarArchiveEntry entry; (entry = tar.getNextEntry()) != null;) {
				Path target = destination.resolve(entry.getName()).normalize();
				if (!target.startsWith(destination)) throw new IOException("Unsafe archive entry");
				if (entry.isDirectory()) Files.createDirectories(target);
				else if (entry.isFile()) {
					Files.createDirectories(target.getParent());
					Files.copy(tar, target, StandardCopyOption.REPLACE_EXISTING);
				}
			}
		}
	}

	private static void deleteTree(Path root) throws IOException {
		if (!Files.exists(root)) return;
		try (var paths = Files.walk(root)) {
			for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList())
				Files.deleteIfExists(path);
		}
	}
}
