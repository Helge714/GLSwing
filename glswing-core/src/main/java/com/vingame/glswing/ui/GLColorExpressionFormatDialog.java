package com.vingame.glswing.ui;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import com.vingame.glswing.component.GLContainer;
import com.vingame.glswing.component.GLDialog;
import com.vingame.glswing.component.GLParent;
import com.vingame.glswing.rendering.GLContext2D;

/** Theme-aware editor for functional color-expression output preferences. */
public final class GLColorExpressionFormatDialog {
	/** Accepted settings; a null model means that each source model is preserved. */
	public record Selection(GLColorModel preferredModel,
			GLColorExpressionFormat format) {
		public Selection {
			Objects.requireNonNull(format, "Color expression format");
		}
	}
	private record ModelChoice(GLColorModel model, String text) { }

	private GLColorExpressionFormatDialog() { }

	/** Opens the modal settings dialog and reports only explicitly accepted values. */
	public static GLDialog show(GLParent owner, GLColorModel preferredModel,
			GLColorExpressionFormat current, Consumer<Selection> accepted) {
		Objects.requireNonNull(owner, "Owner");
		Objects.requireNonNull(current, "Color expression format");
		Objects.requireNonNull(accepted, "Accepted callback");
		String preserve = owner.getGLLocalizer().text(
				"glswing.code.preserveColorModel");
		List<ModelChoice> choices = new ArrayList<>();
		choices.add(new ModelChoice(null, preserve));
		for (GLColorModel model : GLColorModel.values())
			choices.add(new ModelChoice(model, model.name()));
		GLComboBox<ModelChoice> models = new GLComboBox<>(choices);
		models.setItemTextProvider(ModelChoice::text);
		models.setSelectedItem(choices.stream()
				.filter(choice -> choice.model() == preferredModel)
				.findFirst().orElse(choices.get(0)));

		GLComboBox<GLColorExpressionFormat.ChannelNotation> notations =
				new GLComboBox<>(List.of(GLColorExpressionFormat.ChannelNotation.values()));
		notations.setItemTextProvider(value -> notationText(owner, value));
		notations.setSelectedItem(current.channelNotation());
		GLNumberSpinner precision = new GLNumberSpinner(0, 12,
				current.fractionDigits());
		precision.setIntegerValuesOnly(true);
		GLCheckBox trim = new GLCheckBox(owner.getGLLocalizer().text(
				"glswing.code.trimTrailingZeros"), current.trimTrailingZeros());
		GLLabel example = new GLLabel("");
		Runnable refreshExample = () -> {
			GLColorExpressionFormat next = new GLColorExpressionFormat(
					notations.getSelectedItem(), (int) Math.round(precision.getValue()),
					trim.isSelected());
			GLColorModel selected = models.getSelectedItem().model();
			GLColorModel output = selected == null ? GLColorModel.RGB : selected;
			String expression = output.format(output.channels(
					new Color(84, 120, 181, 191)), 191 / 255d, next);
			example.setText(owner.getGLLocalizer().text(
					"glswing.code.colorExample", expression));
		};
		models.addActionListener(event -> refreshExample.run());
		notations.addActionListener(event -> refreshExample.run());
		precision.addChangeListener(event -> refreshExample.run());
		trim.addActionListener(event -> refreshExample.run());
		refreshExample.run();

		GLButton apply = new GLButton(owner.getGLLocalizer().text("glswing.code.apply"));
		GLButton cancel = new GLButton(owner.getGLLocalizer().text("glswing.code.cancel"));
		GLDialogButtonBar actions = new GLDialogButtonBar()
				.addButton(cancel).addButton(apply);
		GLLabel modelLabel = new GLLabel(owner.getGLLocalizer().text(
				"glswing.code.colorModel"));
		GLLabel notationLabel = new GLLabel(owner.getGLLocalizer().text(
				"glswing.code.channelNotation"));
		GLLabel precisionLabel = new GLLabel(owner.getGLLocalizer().text(
				"glswing.code.decimalPlaces"));
		GLContainer content = new GLContainer() {
			@Override protected void record(GLContext2D context) { }
			@Override protected void resized(float width, float height) {
				float fieldX = 180;
				modelLabel.setBounds(16, 18, fieldX - 28, 28);
				models.setBounds(fieldX, 18, Math.max(1, width - fieldX - 16), 28);
				notationLabel.setBounds(16, 58, fieldX - 28, 28);
				notations.setBounds(fieldX, 58, Math.max(1, width - fieldX - 16), 28);
				precisionLabel.setBounds(16, 98, fieldX - 28, 28);
				precision.setBounds(fieldX, 98,
						Math.max(1, width - fieldX - 16), 28);
				trim.setBounds(16, 148, Math.max(1, width - 32), 28);
				example.setBounds(16, 188, Math.max(1, width - 32), 28);
				actions.setBounds(16, Math.max(224, height - 44),
						Math.max(1, width - 32), 28);
			}
		};
		content.addGL(modelLabel); content.addGL(models);
		content.addGL(notationLabel); content.addGL(notations);
		content.addGL(precisionLabel); content.addGL(precision);
		content.addGL(trim); content.addGL(example);
		content.addGL(actions);
		GLDialog dialog = new GLDialog(owner.getGLLocalizer().text(
				"glswing.code.colorFormattingTitle"), content);
		dialog.setDecoration(GLDialog.Decoration.THEMED);
		dialog.setSize(520, 304);
		dialog.setResizable(false);
		apply.addActionListener(event -> {
			accepted.accept(new Selection(models.getSelectedItem().model(),
					new GLColorExpressionFormat(notations.getSelectedItem(),
							(int) Math.round(precision.getValue()), trim.isSelected())));
			dialog.close();
		});
		cancel.addActionListener(event -> dialog.close());
		dialog.show(owner);
		return dialog;
	}

	private static String notationText(GLParent owner,
			GLColorExpressionFormat.ChannelNotation value) {
		return owner.getGLLocalizer().text(switch (value) {
		case BYTE -> "glswing.code.notationByte";
		case NORMALIZED_FLOAT -> "glswing.code.notationFloat";
		case PERCENT -> "glswing.code.notationPercent";
		});
	}
}
