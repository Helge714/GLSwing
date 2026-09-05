package org.example;

import java.util.ServiceLoader;

import com.vingame.glswing.file.GLFileDialogBackend;
import com.vingame.glswing.file.GLFileDialogs;
import com.vingame.glswing.input.GLInputMaps;
import com.vingame.glswing.lsp.GLLanguageServerRegistry;
import com.vingame.glswing.ui.GLButton;
import com.vingame.glswing.ui.GLThemes;

public final class GLSwingConsumer {
    private GLSwingConsumer() {
    }

    public static void main(String[] args) {
        GLButton button = new GLButton("Ready");
        require("Ready".equals(button.getText()), "Core controls are unavailable");
        require(GLThemes.DEFAULT != null && GLThemes.DARK != null && GLThemes.LIGHT != null,
                "Bundled themes are unavailable");
        require(GLInputMaps.DEFAULT != null, "The bundled input map is unavailable");

        GLLanguageServerRegistry languages = GLLanguageServerRegistry.standard();
        require(languages.forLanguage("java").isPresent(),
                "The packaged LSP integration did not register Java support");

        boolean swtProvider = ServiceLoader.load(GLFileDialogBackend.class).stream()
                .map(ServiceLoader.Provider::type)
                .anyMatch(type -> type.getName().equals(
                        "com.vingame.glswing.filedialogs.swt.GLSwtFileDialogBackend"));
        require(swtProvider, "The packaged SWT service provider is unavailable");
        try (GLFileDialogs dialogs = GLFileDialogs.auto()) {
            require(dialogs.isNativeAvailable(),
                    "The SWT file-dialog backend could not be discovered");
        }

        ClassLoader loader = GLSwingConsumer.class.getClassLoader();
        require(loader.getResource("com/vingame/glswing/themes/default.gltheme") != null,
                "The default theme resource is missing from the core artifact");
        require(loader.getResource("com/vingame/glswing/input/default.glinput") != null,
                "The default input-map resource is missing from the core artifact");

        System.out.println("Packaged GLSwing consumer smoke test passed.");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
