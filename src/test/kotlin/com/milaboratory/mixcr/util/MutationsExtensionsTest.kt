package com.milaboratory.mixcr.util

import com.milaboratory.core.mutations.Mutation
import com.milaboratory.core.mutations.Mutations
import com.milaboratory.core.sequence.NucleotideSequence
import com.milaboratory.mixcr.trees.generateMutations
import com.milaboratory.mixcr.trees.generateSequence
import io.kotest.matchers.shouldBe
import org.junit.Ignore
import org.junit.Test
import kotlin.random.Random

class MutationsExtensionsTest {
    @Test
    fun `intersection for substitutions`() {
        mutations("ST0G,ST1G").intersection(mutations("ST0G,ST2G")) shouldBe mutations("ST0G")
        mutations("ST1G").intersection(mutations("ST0G,ST1G")) shouldBe mutations("ST1G")
        mutations("ST0G,ST1G").intersection(mutations("ST1G")) shouldBe mutations("ST1G")
    }

    @Test
    fun `intersection of three mutations without one in the center`() {
        mutations("ST0G,ST1G,ST2G").intersection(mutations("ST1G")) shouldBe mutations("ST1G")
    }

    @Test
    fun `intersection of three mutations without two in the end`() {
        mutations("ST0G,ST1G,ST2G").intersection(mutations("ST1G,ST2G")) shouldBe mutations("ST1G,ST2G")
    }

    @Test
    fun `intersection of duplicated insertion with itself`() {
        mutations("I0G,I0G").intersection(mutations("I0G")) shouldBe mutations("I0G")
        mutations("I0G").intersection(mutations("I0G,I0G")) shouldBe mutations("I0G")
    }

    @Test
    fun `intersection of two insertions and one insertion`() {
        mutations("I1C,ST2G").intersection(mutations("I1T,I1C,ST2G")) shouldBe mutations("I1C,ST2G")
        mutations("I1T,I1C,ST2G").intersection(mutations("I1C,ST2G")) shouldBe mutations("I1C,ST2G")
    }

    @Test
    fun `intersection of two different insertions`() {
        mutations("I1C,I2C").intersection(mutations("I1C,I2C")) shouldBe mutations("I1C,I2C")
    }

    @Test
    fun `intersection by second insertions`() {
        mutations("I0G,I0T").intersection(mutations("I0T")) shouldBe mutations("I0T")
        mutations("I0T").intersection(mutations("I0T,I0G")) shouldBe mutations("I0T")
    }

    @Test
    fun `intersection by second insertion in the middle of two others`() {
        mutations("I0G").intersection(mutations("I0T,I0G,I0T")) shouldBe mutations("I0G")
        mutations("I0T,I0G,I0T").intersection(mutations("I0G")) shouldBe mutations("I0G")
    }

    @Test
    fun `intersection of substitution after insertion`() {
        mutations("I0G,ST0G").intersection(mutations("ST0G")) shouldBe mutations("ST0G")
        mutations("ST0G").intersection(mutations("I0G,ST0G")) shouldBe mutations("ST0G")
    }

    @Test
    fun `without for substitutions`() {
        mutations("ST0G,ST1G").without(mutations("ST0G")) shouldBe mutations("ST1G")
    }

    @Test
    fun `three mutations without one in the center`() {
        mutations("ST0G,ST1G,ST2G").without(mutations("ST1G")) shouldBe mutations("ST0G,ST2G")
    }

    @Test
    fun `three mutations without two in the end`() {
        mutations("ST0G,ST1G,ST2G").without(mutations("ST1G,ST2G")) shouldBe mutations("ST0G")
    }

    @Test
    fun `without for duplicated insertion`() {
        mutations("I0G,I0G").without(mutations("I0G")) shouldBe mutations("I0G")
    }

    @Test
    fun `without second insertions`() {
        mutations("I0G,I0T").without(mutations("I0T")) shouldBe mutations("I0G")
    }

    @Ignore
    @Test
    fun `random test of without`() {
        RandomizedTest.randomized(::testWithout, numberOfRuns = 100000)
    }

    @Test
    fun `reproduce test of without`() {
        RandomizedTest.reproduce(
            ::testWithout,
            4750412158561094728L,
            3502722435274504377L,
            -1598204848989057487L,
        )
    }

    @Ignore
    @Test
    fun `random test of intersection`() {
        RandomizedTest.randomized(::testIntersection, numberOfRuns = 100000)
    }

    @Test
    fun `reproduce test of intersection`() {
        RandomizedTest.reproduce(
            ::testIntersection,
            7112278539570627394L,
            6313853897278610290L,
            1436838224206222452L,
            4912652181881843442L,
            7380827987566875796L,
            8956308861977725090L,
            2382159383607284620L,
        )
    }

    private fun testWithout(random: Random, print: Boolean) {
        val parent = random.generateSequence(20)
        val original = random.generateMutations(parent)
        if (original.isEmpty) {
            return
        }
        val subsetOfIndexes = random.sampleOfIndexes(original)
        val mutationsToSubtract = subsetOfIndexes
            .asSequence()
            .map { original.getMutation(it) }
            .asMutations(NucleotideSequence.ALPHABET)
        val mutationsLeft = (0 until original.size())
            .asSequence()
            .filter { it !in subsetOfIndexes }
            .map { original.getMutation(it) }
            .asMutations(NucleotideSequence.ALPHABET)
        val result = original.without(mutationsToSubtract)
        if (print) {
            println("original: ${original.encode(",")}")
            println("subtract: ${mutationsToSubtract.encode(",")}")
            println("    left: ${mutationsLeft.encode(",")}")
            println("  result: ${result.encode(",")}")
        }
        result shouldBe mutationsLeft
    }

    private fun testIntersection(random: Random, print: Boolean) {
        val parent = random.generateSequence(20)
        val original = random.generateMutations(parent)
        if (original.isEmpty) {
            return
        }
        val intersectionIndexes = random.sampleOfIndexes(original)
        val firstSubsetOfIndexes = random.sampleOfIndexes(original) - intersectionIndexes.toSet()
        val secondSubsetOfIndexes =
            random.sampleOfIndexes(original) - intersectionIndexes.toSet() - firstSubsetOfIndexes.toSet()
        val intersection = intersectionIndexes
            .asSequence()
            .map { original.getMutation(it) }
            .asMutations(NucleotideSequence.ALPHABET)
        val firstDiff = firstSubsetOfIndexes.map { original.getMutation(it) }
        val secondDiff = secondSubsetOfIndexes.map { original.getMutation(it) }
        //different indexes yield the same insertion
        if (firstDiff.any { it in secondDiff }) {
            return
        }

        val firstMutations = (intersectionIndexes + firstSubsetOfIndexes)
            .sorted()
            .asSequence()
            .map { original.getMutation(it) }
            .asMutations(NucleotideSequence.ALPHABET)
        val secondMutations = (intersectionIndexes + secondSubsetOfIndexes)
            .sorted()
            .asSequence()
            .map { original.getMutation(it) }
            .asMutations(NucleotideSequence.ALPHABET)
        val firstResult = firstMutations.intersection(secondMutations)
        val secondResult = secondMutations.intersection(firstMutations)
        if (print) {
            println("    original: ${original.encode(",")}")
            println("       first: ${firstMutations.encode(",")}")
            println("      second: ${secondMutations.encode(",")}")
            println("intersection: ${intersection.encode(",")}")
            println(" firstResult: ${firstResult.encode(",")}")
            println("secondResult: ${secondResult.encode(",")}")
        }
        firstResult shouldBe intersection
        secondResult shouldBe intersection
    }

    private fun Random.sampleOfIndexes(original: Mutations<NucleotideSequence>): List<Int> =
        (0 until nextInt(original.size()))
            .map { nextInt(original.size()) }
            .distinct()
            .groupBy { Mutation.getPosition(original.getMutation(it)) }
            .values
            .asSequence()
            .map { it.sorted() }
            .map { mutationIndexes ->
                var lastPosition = -1
                mutationIndexes.map { index ->
                    val firstTheSameMutationIndex = original.asSequence()
                        .withIndex()
                        .first { (i, mutation) -> i > lastPosition && original.getMutation(index) == mutation }
                        .index
                    lastPosition = firstTheSameMutationIndex
                    firstTheSameMutationIndex
                }
            }
            .flatten()
            .distinct()
            .sorted()
            .toList()

    private fun mutations(mutations: String) = Mutations(NucleotideSequence.ALPHABET, mutations)
}
