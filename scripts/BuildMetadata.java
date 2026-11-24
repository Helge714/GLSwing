import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** JDK-only package-build tool; does not modify source files or Maven versions. */
class BuildMetadata {
    public static void main(String[] args) throws Exception {
        if (args.length != 4) throw new IllegalArgumentException("root output version artifactId required");
        Path root = Path.of(args[0]);
        String commit = git(root, "rev-parse", "HEAD");
        String status = git(root, "status", "--porcelain", "--untracked-files=normal");
        String now = Instant.now().toString();
        String run = System.getenv("GITHUB_RUN_NUMBER"), attempt = System.getenv("GITHUB_RUN_ATTEMPT");
        boolean ci = "true".equals(System.getenv("GITHUB_ACTIONS"))
                && run != null && run.matches("[0-9]+") && attempt != null && attempt.matches("[0-9]+");
        String id = ci ? "github-" + run + "." + attempt + "." + System.getenv("GITHUB_RUN_ID")
                : "local-" + now.replace(":", "").replace("-", "") + "-" + UUID.randomUUID().toString().substring(0, 8);
        var values = new Properties();
        values.setProperty("version", args[2]);
        values.setProperty("artifact", args[3]);
        values.setProperty("build.id", id);
        values.setProperty("build.kind", ci ? "github" : "local");
        values.setProperty("build.time", now);
        values.setProperty("git.commit", commit == null ? "unknown" : commit.strip());
        values.setProperty("git.dirty", status == null ? "unknown" : Boolean.toString(!status.isBlank()));
        Path output = Path.of(args[1]);
        Files.createDirectories(output.getParent());
        try (var writer = Files.newBufferedWriter(output, StandardCharsets.UTF_8)) {
            values.store(writer, "Generated package identity; not a source version");
        }
        System.out.println(args[3] + " " + args[2] + " — " + id);
    }

    private static String git(Path root, String... args) throws Exception {
        var command = new ArrayList<>(List.of("git", "-C", root.toString()));
        command.addAll(List.of(args));
        // Redirect to a temporary file to avoid pipe deadlocks on large worktrees.
        Path capture = Files.createTempFile("build-git-", ".txt");
        try {
            Process process;
            try { process = new ProcessBuilder(command).redirectOutput(capture.toFile())
                    .redirectError(ProcessBuilder.Redirect.DISCARD).start(); }
            catch (IOException missingGit) { return null; }
            if (!process.waitFor(15, TimeUnit.SECONDS)) { process.destroyForcibly(); return null; }
            return process.exitValue() == 0 ? Files.readString(capture) : null;
        } finally { Files.deleteIfExists(capture); }
    }
}
