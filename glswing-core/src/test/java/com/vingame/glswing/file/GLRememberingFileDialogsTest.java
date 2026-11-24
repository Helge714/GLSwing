package com.vingame.glswing.file;
import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
class GLRememberingFileDialogsTest {
    @TempDir Path root;
    @Test void stableKeysCancelDirectoryAndMissingFolder() throws Exception {
        var saved=new HashMap<String,Path>();var seen=new GLFileDialogRequest[1];
        var result=new GLFileDialogResult[]{GLFileDialogResult.cancelled()};
        var store=new GLRememberingFileDialogs.DirectoryStore(){
            public Path load(String key){return saved.get(key);}
            public void save(String key,Path folder){saved.put(key,folder);}
        };
        try(var dialogs=new GLRememberingFileDialogs((request,owner)->{seen[0]=request;return CompletableFuture.completedFuture(result[0]);},store,()->{})){
            var chosen=Files.createDirectory(root.resolve("chosen"));
            result[0]=GLFileDialogResult.approved(List.of(chosen.resolve("file.png")));
            dialogs.show("export",GLFileDialogRequest.save().title("Exportieren").build(),null).toCompletableFuture().join();
            assertEquals(chosen,saved.get("export"));
            result[0]=GLFileDialogResult.cancelled();
            dialogs.show("export",GLFileDialogRequest.save().title("Export").build(),null).toCompletableFuture().join();
            assertEquals(chosen,seen[0].initialDirectory());assertEquals(chosen,saved.get("export"));
            result[0]=GLFileDialogResult.approved(List.of(chosen));
            dialogs.show("folder",GLFileDialogRequest.open().selectionMode(GLFileSelectionMode.DIRECTORIES).build(),null).toCompletableFuture().join();
            assertEquals(chosen,saved.get("folder"));
            saved.put("export",root.resolve("missing"));result[0]=GLFileDialogResult.cancelled();
            dialogs.show("export",GLFileDialogRequest.save().initialDirectory(root).build(),null).toCompletableFuture().join();
            assertEquals(root,seen[0].initialDirectory());
        }
    }
    @Test void persistenceFailureDoesNotLoseApprovedResult(){
        var expected=GLFileDialogResult.approved(List.of(root.resolve("result")));
        var store=new GLRememberingFileDialogs.DirectoryStore(){
            public Path load(String key){throw new SecurityException();}
            public void save(String key,Path folder){throw new SecurityException();}
        };
        try(var dialogs=new GLRememberingFileDialogs((request,owner)->CompletableFuture.completedFuture(expected),store,()->{})){
            assertEquals(expected,dialogs.show("open",GLFileDialogRequest.open().build(),null).toCompletableFuture().join());
        }
    }
}
