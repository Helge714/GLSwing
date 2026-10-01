package com.vingame.glswing.node.composite;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.function.Consumer;

import com.vingame.glswing.component.GLContainer;
import com.vingame.glswing.history.GLHistoryManager;
import com.vingame.glswing.layout.GLSize;
import com.vingame.glswing.layout.GLConstraints;
import com.vingame.glswing.node.document.GLNodeGraphAsset;
import com.vingame.glswing.node.model.GLGraphNode;
import com.vingame.glswing.node.model.GLNodeEndpoint;
import com.vingame.glswing.node.model.GLNodeGraph;
import com.vingame.glswing.node.model.GLNodePortDescriptor;
import com.vingame.glswing.node.model.GLNodeTypeDescriptor;
import com.vingame.glswing.node.model.GLNodeTypeRegistry;
import com.vingame.glswing.node.model.GLNodeValue;
import com.vingame.glswing.node.model.GLNodeValueTypeDescriptor;
import com.vingame.glswing.node.model.GLNodeValueTypeRegistry;
import com.vingame.glswing.node.model.GLPortDirection;
import com.vingame.glswing.node.model.GLPortTiming;
import com.vingame.glswing.rendering.GLContext2D;
import com.vingame.glswing.rendering.GLStroke;
import com.vingame.glswing.ui.GLButton;
import com.vingame.glswing.ui.GLCheckBox;
import com.vingame.glswing.ui.GLComboBox;
import com.vingame.glswing.ui.GLControlState;
import com.vingame.glswing.ui.GLDefaultListModel;
import com.vingame.glswing.ui.GLLabel;
import com.vingame.glswing.ui.GLList;
import com.vingame.glswing.ui.GLLayoutGroup;
import com.vingame.glswing.ui.GLNumberSpinner;
import com.vingame.glswing.ui.GLScrollPane;
import com.vingame.glswing.ui.GLSlider;
import com.vingame.glswing.ui.GLTextEditor;
import com.vingame.glswing.ui.GLUiKeys;

/**
 * Public composite-contract editor. Inputs and outputs are presented separately;
 * low-level endpoint mappings and connection limits are maintained internally.
 */
public final class GLCompositeInterfaceEditor extends GLContainer {
	private final GLCompositeGraphRegistry composites;
	private final GLNodeTypeRegistry types;
	private final GLNodeValueTypeRegistry valueTypes;
	private final GLNodeInputEditorRegistry inputEditors;
	private final GLHistoryManager history;
	private final GLLabel title = new GLLabel("Composite interface");
	private final GLLabel compositeNameLabel = new GLLabel("Composite name");
	private final GLTextEditor compositeName = new GLTextEditor();
	private final GLLabel inputsLabel = new GLLabel("Inputs");
	private final GLDefaultListModel<GLCompositePortMapping> inputModel =
			new GLDefaultListModel<>();
	private final GLList<GLCompositePortMapping> inputs = new GLList<>(inputModel);
	private final GLScrollPane inputScroll = new GLScrollPane(inputs);
	private final GLButton addInput = new SymbolButton(Symbol.ADD);
	private final GLButton removeInput = new SymbolButton(Symbol.REMOVE);
	private final GLButton inputUp = new SymbolButton(Symbol.UP);
	private final GLButton inputDown = new SymbolButton(Symbol.DOWN);
	private final GLLayoutGroup inputListGroup = new GLLayoutGroup();
	private final GLLabel outputsLabel = new GLLabel("Outputs");
	private final GLDefaultListModel<GLCompositePortMapping> outputModel =
			new GLDefaultListModel<>();
	private final GLList<GLCompositePortMapping> outputs = new GLList<>(outputModel);
	private final GLScrollPane outputScroll = new GLScrollPane(outputs);
	private final GLButton addOutput = new SymbolButton(Symbol.ADD);
	private final GLButton removeOutput = new SymbolButton(Symbol.REMOVE);
	private final GLButton outputUp = new SymbolButton(Symbol.UP);
	private final GLButton outputDown = new SymbolButton(Symbol.DOWN);
	private final GLLayoutGroup outputListGroup = new GLLayoutGroup();
	private final GLLabel settingsLabel = new GLLabel("Port settings");
	private final GLLabel nameLabel = new GLLabel("Name");
	private final GLTextEditor portId = new GLTextEditor();
	private final GLLabel typeLabel = new GLLabel("Value type");
	private final GLComboBox<GLNodeValueTypeDescriptor> valueType = new GLComboBox<>();
	private final GLLabel editorLabel = new GLLabel("Input editor");
	private final GLComboBox<GLNodeInputEditorDescriptor> inputEditor = new GLComboBox<>();
	private final GLCheckBox socketVisible = new GLCheckBox("Show node socket");
	private final GLLabel numericTypeLabel = new GLLabel("Numeric type");
	private final GLComboBox<String> numericType = new GLComboBox<>();
	private final GLLabel sliderPresentationLabel = new GLLabel("Presentation");
	private final GLComboBox<GLSlider.Presentation> sliderPresentation = new GLComboBox<>();
	private final GLLabel minimumLabel = new GLLabel("Minimum");
	private final LimitToggle useMinimum = new LimitToggle(true);
	private final GLNumberSpinner minimum = new GLNumberSpinner(0);
	private final GLLayoutGroup minimumGroup = new GLLayoutGroup();
	private final GLLabel maximumLabel = new GLLabel("Maximum");
	private final LimitToggle useMaximum = new LimitToggle(false);
	private final GLNumberSpinner maximum = new GLNumberSpinner(1);
	private final GLLayoutGroup maximumGroup = new GLLayoutGroup();
	private final GLLabel defaultLabel = new GLLabel("Default value");
	private final GLNumberSpinner defaultValue = new GLNumberSpinner(0);
	private final GLLabel status = new GLLabel("");
    private GLContainer applicationGeneral, applicationEditors;
    private final com.vingame.glswing.ui.GLTabbedPane sections=new com.vingame.glswing.ui.GLTabbedPane();
    private boolean sectionsReady;
    private boolean sectionTabsVisible=true;
    public void setSectionTabsVisible(boolean visible){sectionTabsVisible=visible;sections.setVisible(visible);updateEditorOptionVisibility();}
    public void setGeneralSettings(GLContainer content){if(applicationGeneral!=null)removeGL(applicationGeneral);applicationGeneral=content;if(content!=null)addGL(content);updateEditorOptionVisibility();}
    public void setEditorSettings(GLContainer content){if(applicationEditors!=null)removeGL(applicationEditors);applicationEditors=content;if(content!=null)addGL(content);updateEditorOptionVisibility();}
    public void selectSection(int index){sections.setSelectedIndex(index);}
    public int selectedSection(){return sections.getSelectedIndex();}
    private GLContainer applicationSettings;
    private Runnable applicationRefresh=()->{};
    /** Optional application controls; the Core does not interpret their metadata. */
    public void setApplicationSettings(GLContainer content,Runnable refresh){
        if(applicationSettings!=null)removeGL(applicationSettings);
        applicationSettings=content;applicationRefresh=refresh==null?()->{}:refresh;
        if(content!=null)addGL(content);applicationRefresh.run();invalidateGLLayout();
    }
    private boolean applicationOwnsInputEditors;
    public void setApplicationOwnsInputEditors(boolean value){applicationOwnsInputEditors=value;updateEditorOptionVisibility();}
    public void updateSelectedPort(GLCompositePortMapping replacement){
        if(selectedPort()==null)return;
        GLCompositeInterfaceTransactions.updatePort(composites,types,assetId,editingPortId,replacement,dependentGraphs.get(),history,"Change input presentation");
        refresh();changeListener.run();
    }
    public GLCompositePortMapping selectedPort(){return findMapping(editingPortId);}
    public void updateSelectedEditorOptions(Map<String,GLNodeValue> options){
        var current=selectedPort();if(current==null)return;
        var replacement=new GLCompositePortMapping(current.publicPort(),current.internalEndpoint(),
            new GLCompositePortPresentation(current.presentation().inputEditorId(),current.presentation().socketVisible(),options),current.additionalEndpoints());
        GLCompositeInterfaceTransactions.updatePort(composites,types,assetId,editingPortId,replacement,dependentGraphs.get(),history,"Change input editor options");
        refresh();changeListener.run();
    }
	private String assetId;
	private String editingPortId;
	private GLPortDirection editingDirection;
	private boolean synchronizing;
	private Runnable changeListener = () -> { };
	private Consumer<String> compositeNameChangeListener = value -> { };
	private Supplier<? extends Collection<GLNodeGraph>> dependentGraphs = List::of;

	public GLCompositeInterfaceEditor(GLCompositeGraphRegistry composites,
			GLNodeTypeRegistry types, GLHistoryManager history) {
		this(composites, types, new GLNodeValueTypeRegistry(),
				new GLNodeInputEditorRegistry(), history);
	}

	public GLCompositeInterfaceEditor(GLCompositeGraphRegistry composites,
			GLNodeTypeRegistry types, GLNodeValueTypeRegistry valueTypes,
			GLNodeInputEditorRegistry inputEditors, GLHistoryManager history) {
		if (composites == null || types == null || valueTypes == null
				|| inputEditors == null || history == null)
			throw new IllegalArgumentException("Composite registries and history required");
		this.composites = composites; this.types = types;
		this.valueTypes = valueTypes; this.inputEditors = inputEditors;
		this.history = history;
		// Form controls keep transient editing history separate; committed contract
		// changes are recorded explicitly in the supplied document history.
		setGLHistoryManager(new GLHistoryManager());
		inputs.setItemTextProvider(this::portText);
		outputs.setItemTextProvider(this::portText);
		inputs.setDragReorderingEnabled(true);
		outputs.setDragReorderingEnabled(true);
		inputScroll.setHorizontalScrollBarPolicy(GLScrollPane.ScrollBarPolicy.NEVER);
		outputScroll.setHorizontalScrollBarPolicy(GLScrollPane.ScrollBarPolicy.NEVER);
		inputScroll.setContentTracksViewportWidth(true);
		outputScroll.setContentTracksViewportWidth(true);
		inputs.setReorderHandler((list, item, from, to) ->
				reorderPorts(GLPortDirection.INPUT, from, to));
		outputs.setReorderHandler((list, item, from, to) ->
				reorderPorts(GLPortDirection.OUTPUT, from, to));
		inputs.setCellEditor(new PortNameEditor());
		outputs.setCellEditor(new PortNameEditor());
		inputs.addActivationListener((list, item, index) -> list.startEditingAt(index));
		outputs.addActivationListener((list, item, index) -> list.startEditingAt(index));
		valueType.setItemTextProvider(GLNodeValueTypeDescriptor::displayName);
		inputEditor.setItemTextProvider(GLNodeInputEditorDescriptor::displayName);
		numericType.addItem("Float"); numericType.addItem("Integer");
		sliderPresentation.addItem(GLSlider.Presentation.BAR);
		sliderPresentation.addItem(GLSlider.Presentation.BUTTONS);
		sliderPresentation.setItemTextProvider(value ->
				value == GLSlider.Presentation.BAR ? "Bar" : "Buttons");
		minimumGroup.addGL(useMinimum, GLConstraints.at(0, 0).fillBoth().connectAll());
		minimumGroup.addGL(minimum, GLConstraints.at(1, 0).weight(1, 1)
				.fillBoth().connectAll());
		maximumGroup.addGL(useMaximum, GLConstraints.at(0, 0).fillBoth().connectAll());
		maximumGroup.addGL(maximum, GLConstraints.at(1, 0).weight(1, 1)
				.fillBoth().connectAll());
		configureListGroup(inputListGroup, inputScroll, addInput, removeInput,
				inputUp, inputDown);
		configureListGroup(outputListGroup, outputScroll, addOutput, removeOutput,
				outputUp, outputDown);
		inputs.getSelectionModel().addSelectionListener((source, before, after) ->
				selectPort(inputs, outputs));
		outputs.getSelectionModel().addSelectionListener((source, before, after) ->
				selectPort(outputs, inputs));
		addInput.addActionListener(event -> beginNew(GLPortDirection.INPUT));
		addOutput.addActionListener(event -> beginNew(GLPortDirection.OUTPUT));
		removeInput.addActionListener(event -> removeSelected(inputs));
		removeOutput.addActionListener(event -> removeSelected(outputs));
		inputUp.addActionListener(event -> inputs.moveSelectedItemBy(-1));
		inputDown.addActionListener(event -> inputs.moveSelectedItemBy(1));
		outputUp.addActionListener(event -> outputs.moveSelectedItemBy(-1));
		outputDown.addActionListener(event -> outputs.moveSelectedItemBy(1));
		valueType.addActionListener(event -> {
			if (synchronizing) return;
			synchronizing = true;
			try { refreshCompatibleEditors(); }
			finally { synchronizing = false; }
			applyPortLive();
		});
		inputEditor.addActionListener(event -> {
			updateEditorOptionVisibility(); applyPortLive();
		});
		socketVisible.addActionListener(event -> applyPortLive());
		numericType.addActionListener(event -> applyPortLive());
		sliderPresentation.addActionListener(event -> applyPortLive());
		useMinimum.addActionListener(event -> {
			updateLimitToolTips(); applyPortLive();
		});
		useMaximum.addActionListener(event -> {
			updateLimitToolTips(); applyPortLive();
		});
		minimum.addChangeListener(event -> applyPortLive());
		maximum.addChangeListener(event -> applyPortLive());
		defaultValue.addChangeListener(event -> applyPortLive());
		portId.addActionListener(event -> applyPort());
		compositeName.addActionListener(event -> commitCompositeName());
		addGL(sections);
        for(String caption:List.of("General","Inputs / Outputs","Editors"))sections.addTab(caption,new GLContainer(){@Override protected void record(GLContext2D context){}});
        sectionsReady=true;sections.addSelectionListener((source,before,after)->updateEditorOptionVisibility());
        addGL(title); addGL(compositeNameLabel); addGL(compositeName);
		addGL(inputsLabel); addGL(inputListGroup);
		addGL(outputsLabel); addGL(outputListGroup);
		addGL(settingsLabel); addGL(nameLabel); addGL(portId); addGL(typeLabel);
		addGL(valueType); addGL(editorLabel); addGL(inputEditor); addGL(socketVisible);
		addGL(numericTypeLabel); addGL(numericType);
		addGL(sliderPresentationLabel); addGL(sliderPresentation);
		addGL(minimumLabel); addGL(minimumGroup); addGL(maximumLabel);
		addGL(maximumGroup); addGL(defaultLabel); addGL(defaultValue); addGL(status);
		setIntrinsicPreferredGLSize(new GLSize(320, 816));
		updateLocalizedText(); updateEnabledState();
	}

	private static void configureListGroup(GLLayoutGroup group, GLScrollPane list,
			GLButton add, GLButton remove, GLButton up, GLButton down) {
		list.setPreferredGLSize(240, 80);
		for (GLButton button : List.of(add, remove, up, down))
			button.setPreferredGLSize(22, 20);
		group.addGL(list, GLConstraints.at(0, 0).span(1, 4).weight(1, 1)
				.fillBoth().connectAll());
		group.addGL(add, GLConstraints.at(1, 0).fillBoth().connectAll());
		group.addGL(remove, GLConstraints.at(1, 1).fillBoth().connectAll());
		group.addGL(up, GLConstraints.at(1, 2).fillBoth().connectAll());
		group.addGL(down, GLConstraints.at(1, 3).fillBoth().connectAll());
	}

	public String getAssetId() { return assetId; }
	public GLNodeValueTypeRegistry valueTypes() { return valueTypes; }
	public GLNodeInputEditorRegistry inputEditors() { return inputEditors; }

	public void setAssetId(String value) {
		if (java.util.Objects.equals(assetId, value)) return;
		assetId = value; editingPortId = null; editingDirection = null; refresh();
	}

	public void setChangeListener(Runnable listener) {
		changeListener = listener == null ? () -> { } : listener;
	}

	/** Updates the displayed composite name without firing an edit. */
	public void setCompositeName(String value) {
		synchronizing = true;
		try { compositeName.setText(value == null ? "" : value); }
		finally { synchronizing = false; }
	}

	public String getCompositeName() { return compositeName.getText(); }

	/** Receives committed, non-blank composite-name edits. */
	public void setCompositeNameChangeListener(Consumer<String> listener) {
		compositeNameChangeListener = listener == null ? value -> { } : listener;
	}

	private void commitCompositeName() {
		if (synchronizing) return;
		String value = compositeName.getText().strip();
		if (value.isEmpty()) { refresh(); return; }
		compositeNameChangeListener.accept(value);
	}

	public void setDependentGraphsSupplier(
			Supplier<? extends Collection<GLNodeGraph>> supplier) {
		dependentGraphs = supplier == null ? List::of : supplier;
	}

	/** Reloads port lists and application registrations. */
	public void refresh() {
		synchronizing = true;
		try {
			inputModel.clear(); outputModel.clear();
			GLNodeGraphAsset asset = asset();
			if (asset != null && asset.compositeDescriptor() != null)
				for (GLCompositePortMapping mapping
						: asset.compositeDescriptor().portMappings()) {
					if(mapping.presentation().applicationManaged())continue;
					ensureValueType(mapping.publicPort().valueTypeId());
					if (mapping.publicPort().direction() == GLPortDirection.INPUT)
						inputModel.addElement(mapping);
					else outputModel.addElement(mapping);
				}
			refreshValueTypes();
			GLCompositePortMapping selected = findMapping(editingPortId);
			if (selected != null && !selected.presentation().applicationManaged()) {
				GLList<GLCompositePortMapping> list = editingDirection
						== GLPortDirection.INPUT ? inputs : outputs;
				list.setSelectedIndex(indexOf(list, selected));
				loadPort(selected);
			} else if (inputs.getItemCount() > 0) {
				inputs.setSelectedIndex(0); loadPort(inputs.getSelectedValue());
			} else if (outputs.getItemCount() > 0) {
				outputs.setSelectedIndex(0); loadPort(outputs.getSelectedValue());
			} else clearEditor();
		} finally { synchronizing = false; }
		updateEnabledState(); applicationRefresh.run(); updateEditorOptionVisibility();
	}

	@Override protected void resized(float width, float height) {
		float pad = 12, content = Math.max(0, width - pad * 2), y = 10;
		sections.setBounds(pad,0,content,38);y=sectionTabsVisible?46:8;
        if(sections.getSelectedIndex()==2){
            if(applicationEditors!=null){float h=applicationEditors.getPreferredGLSize().height();applicationEditors.setBounds(pad,y,content,h);y+=h;}
            setIntrinsicPreferredGLSize(new GLSize(320,y+pad));return;
        }
        if(sections.getSelectedIndex()==0){
            compositeNameLabel.setBounds(pad,y,content,18);y+=20;compositeName.setBounds(pad,y,content,30);y+=36;
            if(applicationGeneral!=null){float h=applicationGeneral.getPreferredGLSize().height();applicationGeneral.setBounds(pad,y,content,h);y+=h;}
            setIntrinsicPreferredGLSize(new GLSize(320,y+pad));return;
        }
		inputsLabel.setBounds(pad, y, content, 20); y += 22;
		inputListGroup.setBounds(pad, y, content, 80); y += 88;
		outputsLabel.setBounds(pad, y, content, 20); y += 22;
		outputListGroup.setBounds(pad, y, content, 80); y += 88;
		settingsLabel.setBounds(pad, y, content, 22); y += 25;
		nameLabel.setBounds(pad, y, content, 18); y += 20;
		portId.setBounds(pad, y, content, 30); y += 36;
		typeLabel.setBounds(pad, y, content, 18); y += 20;
		valueType.setBounds(pad, y, content, 30); y += 36;
		if (editorLabel.isVisible()) {
			editorLabel.setBounds(pad, y, content, 18); y += 20;
			inputEditor.setBounds(pad, y, content, 30); y += 35;

		}
		if(socketVisible.isVisible()){socketVisible.setBounds(pad,y,content,28);y+=33;}
		if (numericTypeLabel.isVisible()) {
			numericTypeLabel.setBounds(pad, y, content, 18); y += 20;
			numericType.setBounds(pad, y, content, 30); y += 35;
			sliderPresentationLabel.setBounds(pad, y, content, 18); y += 20;
			sliderPresentation.setBounds(pad, y, content, 30); y += 35;
			minimumLabel.setBounds(pad, y, content, 18); y += 20;
			minimumGroup.setBounds(pad, y, content, 30); y += 35;
			maximumLabel.setBounds(pad, y, content, 18); y += 20;
			maximumGroup.setBounds(pad, y, content, 30); y += 35;
			defaultLabel.setBounds(pad, y, content, 18); y += 20;
			defaultValue.setBounds(pad, y, content, 30); y += 36;
		}
        if(applicationSettings!=null){float h=applicationSettings.getPreferredGLSize().height();applicationSettings.setBounds(pad,y,content,h);y+=h+8;}
		status.setBounds(pad, y, content, 22); y += 22 + pad;
		setIntrinsicPreferredGLSize(new GLSize(320, y));
	}

	@Override protected void record(GLContext2D context) {
		context.fillUIRoundRect(0, 0, getWidth(), getHeight(), 6,
				getGLTheme().values().getColor("compositeInterface.background"));
	}

	@Override protected void localizerChanged() { updateLocalizedText(); }

	private void selectPort(GLList<GLCompositePortMapping> source,
			GLList<GLCompositePortMapping> other) {
		if (synchronizing || source.getSelectedValue() == null) return;
		synchronizing = true;
		try { other.setSelectedIndex(-1); loadPort(source.getSelectedValue()); }
		finally { synchronizing = false; }
	}

	private void loadPort(GLCompositePortMapping mapping) {
		if (mapping == null) { clearEditor(); return; }
		editingPortId = mapping.publicPort().id();
		editingDirection = mapping.publicPort().direction();
		portId.setText(editingPortId);
		valueType.setSelectedItem(valueTypes.find(mapping.publicPort().valueTypeId())
				.orElse(null));
		refreshCompatibleEditors();
		GLNodeInputEditorDescriptor selected = inputEditors.find(
				mapping.presentation().inputEditorId()).orElse(null);
		if (selected != null) inputEditor.getItems().stream()
				.filter(item -> item.id().equals(selected.id())).findFirst()
				.ifPresent(inputEditor::setSelectedItem);
		socketVisible.setSelected(mapping.presentation().socketVisible());
		loadEditorOptions(mapping.presentation().editorOptions());
		updateEditorOptionVisibility(); updateEnabledState(); status.setText(""); applicationRefresh.run(); updateEditorOptionVisibility();
	}

	private void beginNew(GLPortDirection direction) {
		editingPortId = null; editingDirection = direction;
		inputs.setSelectedIndex(-1); outputs.setSelectedIndex(-1);
		portId.setText(nextPortId(direction));
		refreshValueTypes();
		if (!valueType.getItems().isEmpty()) valueType.setSelectedIndex(0);
		refreshCompatibleEditors();
		if (direction == GLPortDirection.INPUT)
			inputEditor.setSelectedItem(inputEditors.find(GLNodeInputEditorRegistry.NONE)
					.orElse(null));
		else inputEditor.setSelectedItem(null);
		socketVisible.setSelected(true); loadEditorOptions(Map.of());
		updateEditorOptionVisibility(); updateEnabledState();
		applyPort();
		portId.requestGLFocus(portId); portId.selectAll();
	}

    private java.util.function.UnaryOperator<GLCompositePortMapping> portPresentationTransform = java.util.function.UnaryOperator.identity();
    /** Application metadata normalization, applied before the undoable interface transaction. */
    public void setPortPresentationTransform(java.util.function.UnaryOperator<GLCompositePortMapping> transform) {
        portPresentationTransform = java.util.Objects.requireNonNull(transform);
    }

	private void applyPortLive() {
		if (!synchronizing && editingPortId != null) applyPort();
	}

	private void applyPort() {
		GLNodeValueTypeDescriptor selectedType = valueType.getSelectedItem();
		GLNodeInputEditorDescriptor selectedEditor = inputEditor.getSelectedItem();
		String id = portId.getText().strip();
		if (editingDirection == null || selectedType == null || id.isEmpty()) {
			status.setText("Enter a name and choose a value type."); return;
		}
		String editorId = editingDirection == GLPortDirection.INPUT
				&& selectedEditor != null ? selectedEditor.id()
						: GLNodeInputEditorRegistry.NONE;
		boolean showSocket = editingDirection == GLPortDirection.OUTPUT
				|| socketVisible.isSelected();
		if (!showSocket && GLNodeInputEditorRegistry.NONE.equals(editorId)) {
			status.setText("An input needs an editor, a socket, or both."); return;
		}
		GLCompositePortMapping current = findMapping(editingPortId);
		GLNodeEndpoint endpoint = current == null ? null : current.internalEndpoint();
		try {
			boolean required = editingDirection == GLPortDirection.INPUT
					&& GLNodeInputEditorRegistry.NONE.equals(editorId);
			int limit = editingDirection == GLPortDirection.INPUT ? 1 : Integer.MAX_VALUE;
			GLNodePortDescriptor port = new GLNodePortDescriptor(id, editingDirection,
					selectedType.id(), GLPortTiming.CURRENT, required, limit);
			Map<String, GLNodeValue> options = editorOptions(selectedEditor);
			GLCompositePortMapping mapping = new GLCompositePortMapping(port, endpoint,
					new GLCompositePortPresentation(editorId, showSocket, options),
                    current == null ? List.of() : current.additionalEndpoints());
            mapping = portPresentationTransform.apply(mapping);
			if (editingPortId == null)
				GLCompositeInterfaceTransactions.addPort(composites, types, assetId,
						mapping, history, "Add composite port");
			else GLCompositeInterfaceTransactions.updatePort(composites, types, assetId,
					editingPortId, mapping, dependentGraphs.get(), history,
					"Edit composite port");
			editingPortId = id; status.setText("Interface updated.");
			refresh(); changeListener.run();
		} catch (RuntimeException failure) { status.setText(failure.getMessage()); }
	}

	private void removeSelected(GLList<GLCompositePortMapping> list) {
		GLCompositePortMapping mapping = list.getSelectedValue();
		if (mapping == null) return;
		try {
			GLCompositeInterfaceTransactions.removePort(composites, types, assetId,
					mapping.publicPort().id(), dependentGraphs.get(), history,
					"Remove composite port");
			editingPortId = null; editingDirection = null; refresh(); changeListener.run();
		} catch (RuntimeException failure) { status.setText(failure.getMessage()); }
	}

	private void refreshValueTypes() {
		GLNodeValueTypeDescriptor selected = valueType.getSelectedItem();
		valueType.removeAllItems();
		valueTypes.entries().forEach(valueType::addItem);
		if (selected != null) valueType.setSelectedItem(valueTypes.find(selected.id())
				.orElse(null));
	}

	private void refreshCompatibleEditors() {
		if (synchronizing && editingDirection == null) return;
		GLNodeInputEditorDescriptor selected = inputEditor.getSelectedItem();
		inputEditor.removeAllItems();
		GLNodeValueTypeDescriptor type = valueType.getSelectedItem();
		if (editingDirection == GLPortDirection.INPUT && type != null)
			inputEditors.compatibleWith(type.id()).forEach(inputEditor::addItem);
		if (selected != null) inputEditor.getItems().stream()
				.filter(item -> item.id().equals(selected.id())).findFirst()
				.ifPresent(inputEditor::setSelectedItem);
		if (inputEditor.getSelectedItem() == null && !inputEditor.getItems().isEmpty())
			inputEditor.setSelectedIndex(0);
		updateEditorOptionVisibility();
	}

	private void updateEditorOptionVisibility() {
		boolean ports=!sectionsReady||sections.getSelectedIndex()==1;
        title.setVisible(false);compositeNameLabel.setVisible(!ports&&sections.getSelectedIndex()==0);compositeName.setVisible(compositeNameLabel.isVisible());
        for(GLContainer control:List.of(inputsLabel,inputListGroup,outputsLabel,outputListGroup,settingsLabel,nameLabel,portId,typeLabel,valueType,status))control.setVisible(ports);
        if(applicationGeneral!=null)applicationGeneral.setVisible(sections.getSelectedIndex()==0);
        if(applicationEditors!=null)applicationEditors.setVisible(sections.getSelectedIndex()==2);
        if(applicationSettings!=null)applicationSettings.setVisible(ports);
        boolean input = ports&&editingDirection == GLPortDirection.INPUT;
		editorLabel.setVisible(input&&!applicationOwnsInputEditors); inputEditor.setVisible(editorLabel.isVisible());
		socketVisible.setVisible(input);
		boolean slider = input && !applicationOwnsInputEditors && inputEditor.getSelectedItem() != null
				&& GLNodeInputEditorRegistry.SLIDER.equals(inputEditor.getSelectedItem().id());
		numericTypeLabel.setVisible(slider); numericType.setVisible(slider);
		sliderPresentationLabel.setVisible(slider); sliderPresentation.setVisible(slider);
		minimumLabel.setVisible(slider); minimumGroup.setVisible(slider);
		maximumLabel.setVisible(slider); maximumGroup.setVisible(slider);
		defaultLabel.setVisible(slider); defaultValue.setVisible(slider);
		updateSliderBoundsState();
        // A section change also needs to position its controls when the enclosing
        // viewport keeps this editor at the same size (e.g. a tall sidebar).
        resized(getWidth(), getHeight());
		invalidateGLLayout();
	}

	private void updateSliderBoundsState() {
		boolean integer = "Integer".equals(numericType.getSelectedItem());
		minimum.setIntegerValuesOnly(integer);
		maximum.setIntegerValuesOnly(integer);
		defaultValue.setIntegerValuesOnly(integer);
	}

	private Map<String, GLNodeValue> editorOptions(GLNodeInputEditorDescriptor editor) {
		if (editor == null || GLNodeInputEditorRegistry.NONE.equals(editor.id()))
			return Map.of();
		Map<String, GLNodeValue> result = new LinkedHashMap<>(editor.defaultOptions());
		GLCompositePortMapping current = findMapping(editingPortId);
		if (current != null && current.presentation().inputEditorId().equals(editor.id()))
			result.putAll(current.presentation().editorOptions());
		if (!applicationOwnsInputEditors && GLNodeInputEditorRegistry.SLIDER.equals(editor.id())) {
			double min = minimum.getValue();
			double max = maximum.getValue();
			if (!Double.isFinite(min) || !Double.isFinite(max) || min >= max)
				throw new IllegalArgumentException("Slider maximum must exceed minimum");
			result.put("minimum", GLNodeValue.of(min));
			result.put("maximum", GLNodeValue.of(max));
			result.put("minimumEnabled", GLNodeValue.of(useMinimum.isSelected()));
			result.put("maximumEnabled", GLNodeValue.of(useMaximum.isSelected()));
			result.put("default", GLNodeValue.of(defaultValue.getValue()));
			result.put("presentation", GLNodeValue.of(sliderPresentation.getSelectedItem()
					== GLSlider.Presentation.BUTTONS ? "buttons" : "bar"));
			result.put("numericType", GLNodeValue.of(
					"Integer".equals(numericType.getSelectedItem()) ? "integer" : "float"));
		}
		return result;
	}

	private void loadEditorOptions(Map<String, GLNodeValue> options) {
		minimum.setValue(optionNumber(options.get("minimum"), 0));
		maximum.setValue(optionNumber(options.get("maximum"), 1));
		defaultValue.setValue(optionNumber(options.get("default"), 0));
		useMinimum.setLimitSelected(optionBoolean(
				options.get("minimumEnabled"), true));
		useMaximum.setLimitSelected(optionBoolean(
				options.get("maximumEnabled"), true));
		updateLimitToolTips();
		sliderPresentation.setSelectedItem("buttons".equalsIgnoreCase(
				optionText(options.get("presentation"), "bar"))
						? GLSlider.Presentation.BUTTONS : GLSlider.Presentation.BAR);
		String kind = optionText(options.get("numericType"), "float");
		numericType.setSelectedItem("integer".equalsIgnoreCase(kind) ? "Integer" : "Float");
		updateSliderBoundsState();
	}

	private static double optionNumber(GLNodeValue value, double fallback) {
		if (value instanceof GLNodeValue.DecimalValue item) return item.value();
		if (value instanceof GLNodeValue.IntegerValue item) return item.value();
		return fallback;
	}

	private static boolean optionBoolean(GLNodeValue value, boolean fallback) {
		return value instanceof GLNodeValue.BooleanValue item ? item.value() : fallback;
	}

	private static String optionText(GLNodeValue value, String fallback) {
		return value == null ? fallback : String.valueOf(value.toJavaValue());
	}

	private void ensureValueType(String id) {
		if (valueTypes.find(id).isEmpty())
			valueTypes.register(new GLNodeValueTypeDescriptor(id, id));
	}

	private GLCompositePortMapping findMapping(String id) {
		GLNodeGraphAsset asset = asset();
		return id == null || asset == null || asset.compositeDescriptor() == null ? null
				: asset.compositeDescriptor().portMappings().stream()
						.filter(mapping -> mapping.publicPort().id().equals(id))
						.findFirst().orElse(null);
	}

	private String nextPortId(GLPortDirection direction) {
		String base = direction == GLPortDirection.INPUT ? "input" : "output";
		for (int number = 1; ; number++) {
			String candidate = number == 1 ? base : base + number;
			if (findMapping(candidate) == null) return candidate;
		}
	}

	private GLNodeGraphAsset asset() {
		return assetId == null ? null : composites.findAsset(assetId).orElse(null);
	}

	private void clearEditor() {
		editingPortId = null; editingDirection = null; portId.setText("");
		valueType.setSelectedItem(null); inputEditor.setSelectedItem(null);
		socketVisible.setSelected(true); loadEditorOptions(Map.of());
		updateEditorOptionVisibility();
	}

	private void updateEnabledState() {
		boolean active = asset() != null;
		addInput.setEnabled(active); addOutput.setEnabled(active);
		removeInput.setEnabled(active && inputs.getSelectedValue() != null);
		removeOutput.setEnabled(active && outputs.getSelectedValue() != null);
		inputUp.setEnabled(active && inputs.getSelectedIndex() > 0);
		inputDown.setEnabled(active && inputs.getSelectedIndex() >= 0
				&& inputs.getSelectedIndex() + 1 < inputs.getItemCount());
		outputUp.setEnabled(active && outputs.getSelectedIndex() > 0);
		outputDown.setEnabled(active && outputs.getSelectedIndex() >= 0
				&& outputs.getSelectedIndex() + 1 < outputs.getItemCount());
	}

	private String portText(GLCompositePortMapping mapping) {
		if (mapping == null) return "";
		String type = valueTypes.find(mapping.publicPort().valueTypeId())
				.map(GLNodeValueTypeDescriptor::displayName)
				.orElse(mapping.publicPort().valueTypeId());
		return mapping.publicPort().id() + "  ·  " + type;
	}

	private boolean reorderPorts(GLPortDirection direction, int from, int to) {
		List<GLCompositePortMapping> directional = new ArrayList<>(direction
				== GLPortDirection.INPUT ? inputModel.elements() : outputModel.elements());
		if (from < 0 || to < 0 || from >= directional.size() || to >= directional.size()
				|| from == to) return false;
		GLCompositePortMapping moved = directional.remove(from);
		directional.add(to, moved);
		List<GLCompositePortMapping> ordered = new ArrayList<>();
		if (direction == GLPortDirection.INPUT) {
			ordered.addAll(directional); ordered.addAll(outputModel.elements());
		} else {
			ordered.addAll(inputModel.elements()); ordered.addAll(directional);
		}
		var editable = ordered.iterator();
        ordered = asset().compositeDescriptor().portMappings().stream()
                .map(p -> p.presentation().applicationManaged() ? p : editable.next()).toList();
		try {
			GLCompositeInterfaceTransactions.replacePorts(composites, types, assetId,
					ordered, history, "Reorder composite ports");
			editingPortId = moved.publicPort().id(); editingDirection = direction;
			refresh(); changeListener.run(); return true;
		} catch (RuntimeException failure) {
			status.setText(failure.getMessage()); return false;
		}
	}

	private boolean renamePort(GLCompositePortMapping mapping, String name) {
		String id = name == null ? "" : name.strip();
		if (id.isEmpty()) return false;
		if (id.equals(mapping.publicPort().id())) return true;
		GLNodePortDescriptor old = mapping.publicPort();
		try {
			GLNodePortDescriptor renamed = new GLNodePortDescriptor(id, old.direction(),
					old.valueTypeId(), old.timing(), old.required(),
					old.maximumConnections());
			GLCompositePortMapping replacement = new GLCompositePortMapping(renamed,
					mapping.internalEndpoint(), mapping.presentation(), mapping.additionalEndpoints());
			GLCompositeInterfaceTransactions.updatePort(composites, types, assetId,
					old.id(), replacement, dependentGraphs.get(), history,
					"Rename composite port");
			editingPortId = id; editingDirection = old.direction();
			refresh(); changeListener.run(); return true;
		} catch (RuntimeException failure) {
			status.setText(failure.getMessage()); return false;
		}
	}

	private static int indexOf(GLList<GLCompositePortMapping> list,
			GLCompositePortMapping mapping) {
		for (int index = 0; index < list.getItemCount(); index++)
			if (list.getItemAt(index).publicPort().id()
					.equals(mapping.publicPort().id())) return index;
		return -1;
	}

	private final class PortNameEditor implements GLList.CellEditor<GLCompositePortMapping> {
		@Override public GLContainer createEditor(GLList<GLCompositePortMapping> list,
				GLCompositePortMapping value, int index,
				GLList.EditController controller) {
			GLTextEditor editor = new GLTextEditor(value.publicPort().id());
			editor.addActionListener(event -> controller.commit());
			editor.addCancelListener(event -> controller.cancel());
			java.awt.EventQueue.invokeLater(editor::selectAll);
			return editor;
		}
		@Override public void layoutEditor(GLList<GLCompositePortMapping> list,
				GLCompositePortMapping value, int index, GLContainer editor,
				float width, float height) {
			editor.setBounds(2, 1, Math.max(0, width - 4), Math.max(0, height - 2));
		}
		@Override public boolean commitEditor(GLList<GLCompositePortMapping> list,
				GLCompositePortMapping value, int index, GLContainer editor) {
			return renamePort(value, ((GLTextEditor) editor).getText());
		}
	}

	private enum Symbol { ADD, REMOVE, UP, DOWN }
	private static final class LimitToggle extends GLCheckBox {
		private final boolean minimum;
		private LimitToggle(boolean minimum) {
			super("");
			this.minimum = minimum;
			setPreferredGLSize(30, 30);
			addActionListener(event -> invalidateRecording());
		}
		private void setLimitSelected(boolean selected) {
			if (isSelected() == selected) return;
			setSelected(selected);
			invalidateRecording();
		}
		@Override protected void controlStateChanged(GLControlState oldState,
				GLControlState newState) {
			super.controlStateChanged(oldState, newState);
			invalidateRecording();
		}
		@Override protected void record(GLContext2D context) {
			if (getWidth() <= 0 || getHeight() <= 0) return;
			var style = getGLTheme().buttonStyle();
			Color background = isSelected()
					? getGLTheme().values().getColor("palette.accent", style.hoverBackground())
					: style.background(getControlState());
			Color border = isFocused() ? style.focusColor() : style.borderColor();
			float radius = layoutCornerRadius(style.cornerRadius());
			float borderWidth = layoutBorderWidth(style.borderWidth());
			if (borderWidth > 0)
				context.fillAndDrawUIRoundRect(0, 0, getWidth(), getHeight(),
						radius, borderWidth, background, border,
						GLStroke.WidthSpace.SCREEN);
			else context.fillUIRoundRect(0, 0, getWidth(), getHeight(), radius,
					background);
			Color icon = style.text(getControlState());
			float cx = getWidth() * .5f, cy = getHeight() * .5f;
			GLStroke previous = context.getStroke();
			try {
				context.setStroke(new GLStroke(1.5f, GLStroke.Cap.ROUND,
						GLStroke.Join.ROUND, 4, 0, 0.25f,
						GLStroke.WidthSpace.SCREEN));
				float edge = minimum ? cx - 4 : cx + 4;
				context.drawUILine(edge, cy - 6, edge, cy + 6, icon);
				float tip = minimum ? cx + 4 : cx - 4;
				context.drawUILine(edge + (minimum ? 2 : -2), cy, tip, cy, icon);
				context.drawUILine(tip, cy, tip + (minimum ? -3 : 3), cy - 3, icon);
				context.drawUILine(tip, cy, tip + (minimum ? -3 : 3), cy + 3, icon);
				if (!isSelected())
					context.drawUILine(cx - 6, cy + 6, cx + 6, cy - 6, icon);
			} finally { context.setStroke(previous); }
			context.addUIHitArea(0, 0, getWidth(), getHeight());
		}
	}
	private static final class SymbolButton extends GLButton {
		private final Symbol symbol;
		private SymbolButton(Symbol symbol) { super(""); this.symbol = symbol; }
		@Override protected void controlStateChanged(GLControlState oldState,
				GLControlState newState) {
			super.controlStateChanged(oldState, newState);
			invalidateRecording();
		}
		@Override protected void record(GLContext2D context) {
			super.record(context);
			Color color = getGLTheme().buttonStyle().text(getControlState());
			float cx = getWidth() * .5f, cy = getHeight() * .5f, size = 5;
			GLStroke previous = context.getStroke();
			context.setStroke(new GLStroke(1.5f, GLStroke.Cap.ROUND,
					GLStroke.Join.ROUND, 4, 0, 0.25f, GLStroke.WidthSpace.SCREEN));
			try {
				switch (symbol) {
				case ADD -> {
					context.drawUILine(cx - size, cy, cx + size, cy, color);
					context.drawUILine(cx, cy - size, cx, cy + size, color);
				}
				case REMOVE -> context.drawUILine(cx - size, cy, cx + size, cy, color);
				case UP -> {
					context.drawUILine(cx - size, cy + 2, cx, cy - 3, color);
					context.drawUILine(cx, cy - 3, cx + size, cy + 2, color);
				}
				case DOWN -> {
					context.drawUILine(cx - size, cy - 2, cx, cy + 3, color);
					context.drawUILine(cx, cy + 3, cx + size, cy - 2, color);
				}
				}
			} finally { context.setStroke(previous); }
		}
	}

	private void updateLocalizedText() {
		title.setText(getGLLocalizer().text(GLUiKeys.NODE_COMPOSITE_INTERFACE));
		compositeNameLabel.setText(getGLLocalizer().text(
				GLUiKeys.NODE_COMPOSITE_NAME));
		inputsLabel.setText(getGLLocalizer().text(GLUiKeys.NODE_COMPOSITE_INPUTS));
		outputsLabel.setText(getGLLocalizer().text(GLUiKeys.NODE_COMPOSITE_OUTPUTS));
		settingsLabel.setText(getGLLocalizer().text(
				GLUiKeys.NODE_COMPOSITE_PORT_SETTINGS));
		nameLabel.setText(getGLLocalizer().text(GLUiKeys.NODE_COMPOSITE_PORT_NAME));
		typeLabel.setText(getGLLocalizer().text(
				GLUiKeys.NODE_COMPOSITE_VALUE_TYPE_NAME));
		editorLabel.setText(getGLLocalizer().text(GLUiKeys.NODE_COMPOSITE_INPUT_EDITOR));
		socketVisible.setText(getGLLocalizer().text(GLUiKeys.NODE_COMPOSITE_SHOW_SOCKET));
		numericTypeLabel.setText(getGLLocalizer().text(
				GLUiKeys.NODE_COMPOSITE_NUMERIC_TYPE));
		minimumLabel.setText(getGLLocalizer().text(GLUiKeys.NODE_COMPOSITE_MINIMUM));
		maximumLabel.setText(getGLLocalizer().text(GLUiKeys.NODE_COMPOSITE_MAXIMUM));
		sliderPresentationLabel.setText(getGLLocalizer().text(
				GLUiKeys.NODE_COMPOSITE_SLIDER_PRESENTATION));
		defaultLabel.setText(getGLLocalizer().text(GLUiKeys.NODE_COMPOSITE_DEFAULT_VALUE));
		updateLimitToolTips();
	}

	private void updateLimitToolTips() {
		useMinimum.setToolTipText(getGLLocalizer().text(useMinimum.isSelected()
				? GLUiKeys.NODE_COMPOSITE_MINIMUM_BOUNDED
				: GLUiKeys.NODE_COMPOSITE_MINIMUM_OPEN));
		useMaximum.setToolTipText(getGLLocalizer().text(useMaximum.isSelected()
				? GLUiKeys.NODE_COMPOSITE_MAXIMUM_BOUNDED
				: GLUiKeys.NODE_COMPOSITE_MAXIMUM_OPEN));
	}
}
