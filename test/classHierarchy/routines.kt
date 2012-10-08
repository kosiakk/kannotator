package classHierarchy

import java.io.File
import junit.framework.Assert
import kotlin.test.fail
import kotlinlib.sortByToString
import org.jetbrains.kannotator.classHierarchy.HierarchyNode
import util.getAllClassesWithPrefix
import org.jetbrains.kannotator.classHierarchy.buildClassHierarchyGraph
import util.ClassesFromClassPath
import org.jetbrains.kannotator.classHierarchy.*

fun getClassesHierarchy(prefix: String): Collection<HierarchyNode<ClassData>> {
    val graph = buildClassHierarchyGraph(ClassesFromClassPath(getAllClassesWithPrefix(prefix)))

    return graph.nodes.filter {
        it.name.internal.startsWith(prefix)
    }.sortByToString()
}

fun assertEqualsOrCreate(expectedFile: File, actual: String) {
    if (!expectedFile.exists()) {
        expectedFile.getParentFile()!!.mkdirs()
        expectedFile.writeText(actual)
        fail("Expected data file file does not exist: ${expectedFile}. It is created from actual data")
    }

    val expected = expectedFile.readText()

    Assert.assertEquals(expected, actual)
}
