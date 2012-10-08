package org.jetbrains.kannotator.classHierarchy

import org.jetbrains.kannotator.declarations.ClassName
import org.jetbrains.kannotator.declarations.Method

trait HierarchyGraph<D> {
    val nodes: Collection<HierarchyNode<D>>
}

trait HierarchyEdge<D> {
    val parent: HierarchyNode<D>
    val child: HierarchyNode<D>
}

trait HierarchyNode<D> {
    val children: Collection<HierarchyEdge<D>>
    val parents: Collection<HierarchyEdge<D>>

    // TODO This is a workaround for KT-2920 No bridge generated for a property returning a generic type
    fun data(): D
}
