/*
 * Copyright (c) 2014-2022, MiLaboratories Inc. All Rights Reserved
 *
 * Before downloading or accessing the software, please read carefully the
 * License Agreement available at:
 * https://github.com/milaboratory/mixcr/blob/develop/LICENSE
 *
 * By downloading or accessing the software, you accept and agree to be bound
 * by the terms of the License Agreement. If you do not want to agree to the terms
 * of the Licensing Agreement, you must not download or access the software.
 */
package com.milaboratory.mixcr.trees

import com.milaboratory.primitivio.PrimitivI
import com.milaboratory.primitivio.PrimitivO
import com.milaboratory.primitivio.Serializer
import com.milaboratory.primitivio.annotations.Serializable
import com.milaboratory.primitivio.readObjectRequired

@Serializable(by = SHMTreeSerializer::class)
class SHMTree(
    val tree: Tree<CloneOrFoundAncestor>,
    val rootInfo: RootInfo,
    val treeId: TreeWithMetaBuilder.TreeId
)

class SHMTreeSerializer : Serializer<SHMTree> {
    override fun write(output: PrimitivO, obj: SHMTree) {
        TreeSerializer.writeTree(output, obj.tree)
        output.writeObject(obj.rootInfo)
        output.writeInt(obj.treeId.id)
    }

    override fun read(input: PrimitivI): SHMTree {
        val tree = TreeSerializer.readTree<CloneOrFoundAncestor>(input)
        val rootInfo = input.readObjectRequired<RootInfo>()
        val treeId = input.readInt()
        return SHMTree(
            tree,
            rootInfo,
            TreeWithMetaBuilder.TreeId(treeId, rootInfo.VJBase)
        )
    }

    override fun isReference(): Boolean = false

    override fun handlesReference(): Boolean = false
}



