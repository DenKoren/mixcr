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
@file:Suppress("LocalVariableName")

package com.milaboratory.mixcr.export

import com.milaboratory.core.sequence.NucleotideSequence
import com.milaboratory.mixcr.trees.SHMTreeForPostanalysis
import io.repseq.core.GeneFeature.CDR3
import io.repseq.core.GeneType.VJ_REFERENCE
import kotlin.collections.set
import kotlin.math.log2

object SHMTreeFieldsExtractorsFactory : FieldExtractorsFactory<SHMTreeForPostanalysis>() {
    override val presets: Map<String, List<FieldCommandArgs>> = buildMap {
        this["full"] = listOf(
            FieldCommandArgs("-treeId"),
            FieldCommandArgs("-uniqClonesCount"),
            FieldCommandArgs("-totalClonesCount"),
            FieldCommandArgs("-wildcardsScore"),
            FieldCommandArgs("-ndnOfMRCA"),
            FieldCommandArgs("-vHit"),
            FieldCommandArgs("-jHit"),
        )

        this["min"] = listOf(
            FieldCommandArgs("-treeId"),
            FieldCommandArgs("-vHit"),
            FieldCommandArgs("-jHit"),
        )
    }

    override val defaultPreset: String = "full"

    override fun allAvailableFields(): List<Field<SHMTreeForPostanalysis>> = buildList {
        this += FieldParameterless(
            Order.treeMainParams + 100,
            "-treeId",
            "SHM tree id",
            "Tree id",
            "treeId"
        ) { it.meta.treeId.toString() }

        this += FieldParameterless(
            Order.treeMainParams + 200,
            "-uniqClonesCount",
            "Number of uniq clones in the SHM tree",
            "Uniq clones count",
            "uniqClonesCount"
        ) { shmTree ->
            shmTree.tree.allNodes().sumOf { it.node.content.clones.count() }.toString()
        }

        this += FieldParameterless(
            Order.treeMainParams + 300,
            "-totalClonesCount",
            "Total sum of counts of clones in the SHM tree",
            "Total clones count",
            "totalClonesCount"
        ) { shmTree ->
            shmTree.tree.allNodes().sumOf { (_, node) -> node.content.clones.sumOf { it.clone.count } }.toString()
        }

        VJ_REFERENCE.forEach { type ->
            val l = type.letter
            this += FieldParameterless(
                Order.orderForBestHit(type),
                "-${l.lowercaseChar()}Hit",
                "Export best $l hit",
                "Best $l hit",
                "best${l}Hit"
            ) {
                it.meta.rootInfo.VJBase.geneIds[type].name
            }
        }

        this += FieldParameterless(
            Order.treeStats + 100,
            "-wildcardsScore",
            "Count of possible nucleotide sequences of CDR3 in MRCA",
            "Wildcards score",
            "wildcardsScore"
        ) { shmTree ->
            val CDR3Sequence = shmTree.mrca.targetNSequence(CDR3)!!
            val wildcardSized = (0 until CDR3Sequence.size())
                .map { CDR3Sequence.codeAt(it) }
                .filter { NucleotideSequence.ALPHABET.isWildcard(it) }
                .map { NucleotideSequence.ALPHABET.codeToWildcard(it) }
                .map { log2(it.basicSize().toDouble()) }
            wildcardSized.sum().toString()
        }

        this += FieldParameterless(
            Order.treeStats + 200,
            "-ndnOfMRCA",
            "NDN nucleotide sequence of MRCA",
            "mrcaNDN",
            "mrcaNDN"
        ) { shmTree ->
            shmTree.mrca.NDN.toString()
        }
    }
}
