package com.vingame.glswing.node.composite;

import java.util.Objects;
import java.util.List;
import java.util.function.Function;

import com.vingame.glswing.component.GLContainer;
import com.vingame.glswing.node.GLNode;
import com.vingame.glswing.node.GLNodePortOrder;
import com.vingame.glswing.node.GLNodeResizeMode;
import com.vingame.glswing.node.binding.GLNodeViewFactory;
import com.vingame.glswing.node.model.GLGraphNode;
import com.vingame.glswing.node.model.GLNodeTypeDescriptor;
import com.vingame.glswing.node.model.GLPortDirection;
import com.vingame.glswing.ui.GLLabel;

/**
 * Projects composite contracts into regular node views and delegates leaf-node
 * rendering to an application factory.
 */
public final class GLCompositeNodeViewFactory implements GLNodeViewFactory {
	private final GLCompositeGraphRegistry composites;
	private final GLNodeInputEditorRegistry inputEditors;
	private final GLNodeViewFactory leafFactory;
	private final Function<String, String> titleProvider;
	private GLNodeViewFactory applicationFactory;
    public void setApplicationFactory(GLNodeViewFactory factory){applicationFactory=factory;}
	private GLNodePortOrder portOrder = GLNodePortOrder.INPUTS_FIRST;

	public GLCompositeNodeViewFactory(GLCompositeGraphRegistry composites,
			GLNodeInputEditorRegistry inputEditors, GLNodeViewFactory leafFactory) {
		this(composites, inputEditors, leafFactory, Function.identity());
	}

	public GLCompositeNodeViewFactory(GLCompositeGraphRegistry composites,
			GLNodeInputEditorRegistry inputEditors, GLNodeViewFactory leafFactory,
			Function<String, String> titleProvider) {
		this.composites = Objects.requireNonNull(composites);
		this.inputEditors = Objects.requireNonNull(inputEditors);
		this.leafFactory = Objects.requireNonNull(leafFactory);
		this.titleProvider = Objects.requireNonNull(titleProvider);
	}

	public GLNodePortOrder getPortOrder() { return portOrder; }

	/** Sets the visual row order used for projected composite nodes. */
	public void setPortOrder(GLNodePortOrder order) {
		portOrder = Objects.requireNonNull(order, "Node port order");
	}

	@Override public GLNode createNode(GLGraphNode node, GLNodeTypeDescriptor type) {
		GLCompositeNodeDescriptor descriptor = composites.findComposite(type.id())
				.orElse(null);
		if (descriptor == null) return leafFactory.createNode(node, type);
        if(applicationFactory!=null)return applicationFactory.createNode(node,type);
		CompositeView view = new CompositeView(titleProvider.apply(type.id()), descriptor,
				portOrder);
		view.setPortOrder(portOrder);
		view.setResizeMode(GLNodeResizeMode.HORIZONTAL);
		for (GLCompositePortMapping mapping : orderedMappings(descriptor)) {
			var port = mapping.publicPort();
			if (port.direction() == GLPortDirection.OUTPUT) {
				view.addOutput(port.id(), new GLLabel(port.id()));
				continue;
			}
			GLContainer content = inputEditors.create(new GLNodeInputEditorContext(
					node, port, mapping.presentation())).orElseGet(() -> new GLLabel(port.id()));
			{
				var input = view.addInput(port.id(), content);
                input.setSocketVisible(mapping.presentation().socketVisible());
				input.setContentHiddenWhenConnected(!GLNodeInputEditorRegistry.NONE.equals(
						mapping.presentation().inputEditorId()));
			}
			
		}
		return view;
	}

	@Override public boolean isViewCurrent(GLNode view, GLGraphNode node,
			GLNodeTypeDescriptor type) {
		GLCompositeNodeDescriptor descriptor = composites.findComposite(type.id())
				.orElse(null);
        if(descriptor!=null&&applicationFactory!=null)return applicationFactory.isViewCurrent(view,node,type);
		return descriptor == null
				? !(view instanceof CompositeView)
				: view instanceof CompositeView composite
						&& composite.descriptor.equals(descriptor)
						&& composite.portOrder == portOrder;
	}

    @Override public void transferViewState(GLNode previous, GLNode replacement,
            GLGraphNode node, GLNodeTypeDescriptor type) {
        if (composites.findComposite(type.id()).isEmpty())
            leafFactory.transferViewState(previous,replacement,node,type);
        else if (applicationFactory != null)
            applicationFactory.transferViewState(previous,replacement,node,type);
    }

	@Override public void updateNode(GLNode view, GLGraphNode node,
			GLNodeTypeDescriptor type) {
        if(composites.findComposite(type.id()).isPresent()&&applicationFactory!=null){applicationFactory.updateNode(view,node,type);return;}
		if (!(view instanceof CompositeView)) {
			leafFactory.updateNode(view, node, type);
			return;
		}
		String title = titleProvider.apply(type.id());
		view.setTitle(title == null || title.isBlank() ? type.id() : title);
		var preferred = view.getPreferredGLSize();
		view.setSize(preferred.width(), preferred.height());
	}

	private List<GLCompositePortMapping> orderedMappings(
			GLCompositeNodeDescriptor descriptor) {
		if (portOrder == GLNodePortOrder.DECLARATION_ORDER)
			return descriptor.portMappings();
		List<GLCompositePortMapping> ordered = new java.util.ArrayList<>(
				descriptor.portMappings());
		GLPortDirection first = portOrder == GLNodePortOrder.OUTPUTS_FIRST
				? GLPortDirection.OUTPUT : GLPortDirection.INPUT;
		ordered.sort(java.util.Comparator.comparingInt(mapping ->
				mapping.publicPort().direction() == first ? 0 : 1));
		return List.copyOf(ordered);
	}

	private static final class CompositeView extends GLNode {
		private final GLCompositeNodeDescriptor descriptor;
		private final GLNodePortOrder portOrder;
		private CompositeView(String title, GLCompositeNodeDescriptor descriptor,
				GLNodePortOrder portOrder) {
			super(title == null || title.isBlank() ? descriptor.nodeTypeId() : title);
			this.descriptor = descriptor;
			this.portOrder = portOrder;
		}
	}
}
