package com.vingame.glswing.file;

import com.vingame.glswing.component.GLParent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.function.BiFunction;

/** Dialog decorator with application-owned storage and language-independent purpose keys. */
public final class GLRememberingFileDialogs implements AutoCloseable {
    public interface DirectoryStore {
        Path load(String purpose);
        void save(String purpose, Path directory);
    }
    private final BiFunction<GLFileDialogRequest, GLParent, CompletionStage<GLFileDialogResult>> show;
    private final DirectoryStore store;
    private final Runnable close;
    public GLRememberingFileDialogs(GLFileDialogs dialogs, DirectoryStore store) {
        this(dialogs::show, store, dialogs::close);
    }
    public GLRememberingFileDialogs(
            BiFunction<GLFileDialogRequest, GLParent, CompletionStage<GLFileDialogResult>> show,
            DirectoryStore store, Runnable close) {
        this.show = Objects.requireNonNull(show);
        this.store = Objects.requireNonNull(store);
        this.close = Objects.requireNonNull(close);
    }
    public CompletionStage<GLFileDialogResult> show(String purpose, GLFileDialogRequest request, GLParent owner) {
        if (purpose == null || purpose.isBlank()) throw new IllegalArgumentException("A stable dialog purpose is required");
        Objects.requireNonNull(request);
        Path directory = request.initialDirectory();
        try {
            Path saved = store.load(purpose);
            if (saved != null && Files.isDirectory(saved)) directory = saved;
        } catch (RuntimeException ignored) { /* Persistence must not prevent opening a dialog. */ }
        var restored = new GLFileDialogRequest(request.title(), request.mode(), request.selectionMode(),
                request.multiple(), directory, request.suggestedFileName(), request.filters(), request.confirmOverwrite());
        return show.apply(restored, owner).thenApply(result -> {
            if (result.approved() && result.selectedPath() != null) {
                Path selected = result.selectedPath();
                Path folder = request.selectionMode() != GLFileSelectionMode.FILES && Files.isDirectory(selected)
                        ? selected : selected.getParent();
                if (folder != null) try { store.save(purpose, folder); } catch (RuntimeException ignored) { }
            }
            return result;
        });
    }
    @Override public void close() { close.run(); }
}
