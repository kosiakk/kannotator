package org.jetbrains.kannotator.controlFlow.builder.analysis

import org.objectweb.asm.tree.analysis.Frame
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.Opcodes.*
import org.objectweb.asm.tree.MethodInsnNode
import java.util.Collections
import org.jetbrains.kannotator.index.DeclarationIndex
import org.jetbrains.kannotator.declarations.*
import java.util.HashSet
import kotlinlib.bfs
import org.jetbrains.kannotator.controlFlow.builder.analysis.mutability.MutabilityAnnotation
import org.jetbrains.kannotator.controlFlow.builder.analysis.Mutability.*
import org.jetbrains.kannotator.annotationsInference.engine.*
import org.jetbrains.kannotator.runtime.annotations.AnalysisType

class MutabilityKey : AnalysisType {
    override fun toString() = "mutability"
}

val MUTABILITY_KEY = MutabilityKey()

public enum class Mutability: Qualifier {
    READ_ONLY,
    MUTABLE
}

object MutabilitySet: QualifierSet<Mutability> {
    public override val id: AnalysisType = MUTABILITY_KEY
    public override val initial: Mutability = READ_ONLY

    public override fun merge(q1: Mutability, q2: Mutability): Mutability {
        return if (q1 == q2) q1 else MUTABLE
    }

    public override fun contains(q: Qualifier): Boolean = q is Mutability
}

val mutableInterfaces: Map<String, List<String>> = hashMapOf(
        "java/util/Collection" to listOf("add", "remove", "addAll", "removeAll", "retainAll", "clear"),
        "java/util/List" to listOf("add", "remove", "addAll", "removeAll", "retainAll", "clear", "set", "add", "remove"),
        "java/util/Set" to listOf("add", "remove", "AddAll", "removeAll", "retainAll", "clear"),
        "java/util/Map" to listOf("put", "remove", "putAll", "clear"),
        "java/util/Map\$Entry" to listOf("setValue"),
        "java/util/Iterator" to listOf("remove"),
        "java/util/ListIterator" to listOf("remove", "set", "add")
)

val propagatingMutability: Map<String, List<String>> = hashMapOf(
        "java/util/Collection" to listOf("iterator"),
        "java/util/List" to listOf("iterator", "listIterator", "subList"),
        "java/util/Set" to listOf("iterator"),
        "java/util/SortedSet" to listOf("iterator", "subSet", "headSet", "tailSet"),
        "java/util/NavigableSet" to listOf("iterator", "subSet", "headSet", "tailSet", "descendingSet", "descendingIterator", ""),
        "java/util/Map" to listOf("keySet", "values", "entrySet"),
        "java/util/SortedMap" to listOf("keySet", "values", "entrySet", "subMap", "headMap", "tailMap"),
        "java/util/NavigableMap" to listOf("keySet", "values", "entrySet", "subMap", "headMap", "tailMap", "descendingMap", "navigableKeySet", "descendingKeySet")
)

fun MethodInsnNode.isMutatingInvocation() : Boolean =
        mutableInterfaces.containsInvocation(this)

fun MethodInsnNode.isMutabilityPropagatingInvocation() : Boolean =
        propagatingMutability.containsInvocation(this)

private fun Map<String, List<String>>.containsInvocation(instruction: MethodInsnNode) : Boolean {
    val className = instruction.owner!!
    val methodName = instruction.name!!

    var contains: Boolean = this@containsInvocation[className]?.contains(methodName) ?: false
    if (contains) {
        return true
    }

    // todo: Temporary solution which uses Reflection API to traverse inheritance graph

    val dottedName = className.replace('/', '.')

    val initialClasses = try {
        Collections.singleton(Class.forName(dottedName) as Class<Any>)
    } catch (e: Exception) {
        return false
    }

    bfs(initialClasses) {currentClass ->
        val superTypes = HashSet<Class<Any>>()
        for (intf in currentClass.interfaces) {
            superTypes.add(intf as Class<Any>)
        }
        val superClass = currentClass.superclass
        if (superClass != null) {
            superTypes.add(superClass as Class<Any>)
        }

        for (superType in superTypes) {
            contains = this@containsInvocation[superType.name.replace('.', '/')]?.contains(methodName) ?: false
            if (contains) break
        }

        if (contains) Collections.emptyList() else superTypes
    }

    return contains
}

val imposeMutable = {
    q: Mutability -> MUTABLE
}

fun <Q: Qualifier> imposeMutabilityOnFrameValues(
        frame: Frame<QualifiedValueSet<Q>>, frameValues: QualifiedValueSet<Q>?, analyzer: Analyzer<QualifiedValueSet<Q>>
): Frame<QualifiedValueSet<Q>> {
    updateQualifiers(frame, frameValues, MutabilitySet, true, imposeMutable)

    if (frameValues != null) {
        for (value in frameValues.values) {
            val createdAtInsn = value.base.createdAt
            if (createdAtInsn is MethodInsnNode && createdAtInsn.isMutabilityPropagatingInvocation()) {
                val createdAtFrame = analyzer.getInstructionFrame(createdAtInsn)!!
                imposeMutabilityOnFrameValues(frame, createdAtInsn.getReceiver(createdAtFrame), analyzer)
            }
        }
    }

    return frame
}

class MutabilityFrameTransformer<Q: Qualifier>(
        val annotations: Annotations<MutabilityAnnotation>,
        val declarationIndex: DeclarationIndex
): BasicFrameTransformer<Q>() {
    public override fun getPostFrame(
            insnNode: AbstractInsnNode,
            edgeKind: EdgeKind,
            preFrame: Frame<QualifiedValueSet<Q>>,
            executedFrame: Frame<QualifiedValueSet<Q>>,
            analyzer: Analyzer<QualifiedValueSet<Q>>
    ): Frame<QualifiedValueSet<Q>>? {
        val defFrame = super.getPostFrame(insnNode, edgeKind, preFrame, executedFrame, analyzer)

        val opcode = insnNode.opcode
        return when (opcode) {
            INVOKEVIRTUAL, INVOKEINTERFACE, INVOKEDYNAMIC, INVOKESTATIC, INVOKESPECIAL -> {
                val methodInsnNode = insnNode as MethodInsnNode
                val postFrame = executedFrame.copy()
                if (opcode == INVOKEINTERFACE && methodInsnNode.isMutatingInvocation()) {
                    imposeMutabilityOnFrameValues(postFrame, methodInsnNode.getReceiver(preFrame), analyzer)
                }

                generateAssertsForCallArguments(insnNode, declarationIndex, annotations,
                        { indexFromTop -> imposeMutabilityOnFrameValues(postFrame, preFrame.getStackFromTop(indexFromTop), analyzer)},
                        false,
                        { paramAnnotation -> paramAnnotation == MutabilityAnnotation.MUTABLE },
                        {}
                )

                postFrame
            }

            else -> defFrame
        }
    }
}

object MutabilityQualifierEvaluator: QualifierEvaluator<Mutability> {
    override fun evaluateQualifier(baseValue: TypedValue): Mutability = READ_ONLY
}

fun <Q: Qualifier> buildMutabilityAnnotations(
        method: Method,
        analysisResult: AnalysisResult<QualifiedValueSet<Q>>
) : Annotations<MutabilityAnnotation> {
    val positions = PositionsForMethod(method)

    val affectedValues = HashSet<QualifiedValue<Q>>()
    for (returnInsn in analysisResult.returnInstructions) {
        val resultFrame = analysisResult.mergedFrames[returnInsn]!!
        resultFrame.forEachValue { frameValue ->
            frameValue.values.forEach { v ->
                if (v.base.interesting && v.qualifier.extract<Mutability>(MutabilitySet) == MUTABLE) {
                    affectedValues.add(v)
                }
            }
        }
    }

    val result = AnnotationsImpl<MutabilityAnnotation>()
    for (value in affectedValues) {
        val pos = positions.forParameter(value.base.parameterIndex!!).position
        result.setIfNotNull(pos, MutabilityAnnotation.MUTABLE)
    }

    return result
}