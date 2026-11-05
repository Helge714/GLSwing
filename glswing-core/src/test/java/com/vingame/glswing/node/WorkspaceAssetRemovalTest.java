package com.vingame.glswing.node;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import com.vingame.glswing.history.GLHistoryManager;
import com.vingame.glswing.node.binding.*;
import com.vingame.glswing.node.composite.*;
import com.vingame.glswing.node.document.*;
import com.vingame.glswing.node.model.*;
class WorkspaceAssetRemovalTest {
 @Test void activeAssetsAreProtectedAndRemovedForwardDocumentsCannotReappear(){
  var registry=new GLCompositeGraphRegistry();var descriptor=new GLCompositeNodeDescriptor("test.group","test.asset",1,List.of());
  var asset=new GLNodeGraphAsset("test.asset",1,GLNodeGraphAssetKind.COMPOSITE,new GLNodeGraph().snapshot(),GLNodeEditorViewState.empty(),Map.of(),descriptor);
  var document=new GLNodeGraphDocument(new GLNodeGraphAsset("root",1,GLNodeGraphAssetKind.PROJECT,new GLNodeGraph().snapshot(),GLNodeEditorViewState.empty()),Map.of(asset.id(),asset),List.of());
  registry.registerDocument(document);var types=new GLNodeTypeRegistry();types.register(descriptor.nodeType());
  GLNodeViewFactory factory=(node,type)->new GLNode("Node");
  try(var workspace=new GLNodeWorkspace(types,registry,new GLNodeCatalog(),factory,GLGraphCyclePolicy.DELAYED_FEEDBACK,new GLHistoryManager())){
   workspace.openDocument("Root",document);assertTrue(workspace.openComposite(asset.id()));
   assertThrows(IllegalArgumentException.class,()->workspace.excludeEmbeddedAsset(asset.id()));
   assertTrue(workspace.navigateBack());assertTrue(workspace.canNavigateForward());
   workspace.excludeEmbeddedAsset(asset.id());registry.unregisterAsset(asset.id());
   assertFalse(workspace.canNavigateForward());assertFalse(workspace.navigateForward());assertTrue(workspace.snapshotDocument().embeddedAssets().isEmpty());
   registry.registerAsset(asset);workspace.includeEmbeddedAsset(asset.id());assertTrue(workspace.openComposite(asset.id()));
  }
 }
}
