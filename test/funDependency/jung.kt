package tests.funDependency

import edu.uci.ics.jung.algorithms.layout.KKLayout
import edu.uci.ics.jung.algorithms.layout.StaticLayout
import edu.uci.ics.jung.algorithms.layout.TreeLayout
import edu.uci.ics.jung.algorithms.shortestpath.MinimumSpanningForest2
import edu.uci.ics.jung.graph.DelegateForest
import edu.uci.ics.jung.graph.DelegateTree
import edu.uci.ics.jung.graph.DirectedGraph
import edu.uci.ics.jung.graph.DirectedSparseMultigraph
import edu.uci.ics.jung.graph.util.EdgeType
import edu.uci.ics.jung.visualization.VisualizationViewer
import edu.uci.ics.jung.visualization.control.DefaultModalGraphMouse
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.geom.Point2D
import javax.swing.JFrame
import org.apache.commons.collections15.Transformer
import org.apache.commons.collections15.functors.ConstantTransformer

/** utility to display a dependency graph via jung libraries */
fun <V, E> displayJungGraph(
        graph: DirectedGraph<V, E>,
        vertexLabelTransformer: Transformer<V, String>?,
        edgeLabelTransformer: Transformer<E, String>?
) {
    val layout = KKLayout(graph);
    layout.size = Dimension(800, 800); // sets the initial size of the space
    // The BasicVisualizationServer<V,E> is parameterized by the edge types
    val prim = MinimumSpanningForest2<V, E>(graph, DelegateForest(), DelegateTree.getFactory(), ConstantTransformer(1.0) as Transformer<E, Double>)
    val tree = prim.forest;
    val treeLayout = TreeLayout(tree)
    val graphAsTree = StaticLayout(graph, treeLayout as Transformer<V, Point2D>)
    //    treeLayout.setSize(Dimension(800, 800))

    val vv = VisualizationViewer(graphAsTree);
    //    val vv = VisualizationViewer(layout);
    vv.preferredSize = Dimension(850, 850); //Sets the viewing area size

    if (vertexLabelTransformer != null) {
        vv.renderContext.vertexLabelTransformer = vertexLabelTransformer
    }

    if (edgeLabelTransformer != null) {
        vv.renderContext.edgeLabelTransformer = edgeLabelTransformer
    }

    val gm = DefaultModalGraphMouse<V, E>()
    vv.graphMouse = gm

    //    MinimumSpanningForest2<Instruction, ControlFlowEdge>(
    //            graph,
    //            DelegateForest<Instruction, ControlFlowEdge?>(),
    //            DelegateTree.getFactory<Instruction, ControlFlowEdge?>(),
    //            ConstantTransformer<Double?>(1.0) as Transformer<ControlFlowEdge, Double>)


    //    val magnifyViewSupport =
    //        ViewLensSupport<Instruction, ControlFlowEdge>(vv, MagnifyShapeTransformer(vv,
    //        		vv.getRenderContext().getMultiLayerTransformer().getTransformer(Layer.VIEW)),
    //                ModalLensGraphMouse(LensMagnificationGraphMousePlugin(1.toFloat(), 6.toFloat(), 0.2.toFloat())));
    //    magnifyViewSupport.activate(true)

    val frame = JFrame("Simple Graph View");
    frame.defaultCloseOperation = JFrame.EXIT_ON_CLOSE;
    frame.contentPane!!.add(vv, BorderLayout.CENTER);
    frame.contentPane!!.add(gm.modeComboBox!!, BorderLayout.NORTH);

    frame.pack();
    frame.isVisible = true;
}
