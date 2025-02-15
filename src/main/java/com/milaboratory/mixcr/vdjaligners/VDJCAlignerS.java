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
package com.milaboratory.mixcr.vdjaligners;

import com.milaboratory.core.Target;
import com.milaboratory.core.alignment.batch.AlignmentHit;
import com.milaboratory.core.alignment.batch.AlignmentResult;
import com.milaboratory.core.alignment.batch.BatchAlignerWithBaseParameters;
import com.milaboratory.core.alignment.batch.BatchAlignerWithBaseWithFilter;
import com.milaboratory.core.alignment.kaligner1.KAlignerParameters;
import com.milaboratory.core.alignment.kaligner2.KAlignerParameters2;
import com.milaboratory.core.sequence.NSQTuple;
import com.milaboratory.core.sequence.NucleotideSequence;
import com.milaboratory.mixcr.basictypes.SequenceHistory;
import com.milaboratory.mixcr.basictypes.VDJCAlignments;
import com.milaboratory.mixcr.basictypes.VDJCHit;
import com.milaboratory.mixcr.basictypes.tag.TagCount;
import com.milaboratory.util.BitArray;
import io.repseq.core.*;

import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;

public final class VDJCAlignerS extends VDJCAlignerAbstract {
    private static final ReferencePoint reqPointL = ReferencePoint.CDR3Begin.move(-3);
    private static final ReferencePoint reqPointR = ReferencePoint.CDR3End.move(3);

    public VDJCAlignerS(VDJCAlignerParameters parameters) {
        super(parameters);
    }

    VDJCAlignerS(boolean initialized,
                 VDJCAlignerParameters parameters,
                 EnumMap<GeneType, List<VDJCGene>> genesToAlign,
                 List<VDJCGene> usedGenes,
                 SingleDAligner singleDAligner,
                 EnumMap<GeneType, HashMap<String, BitArray>> filters,
                 BatchAlignerWithBaseWithFilter<NucleotideSequence, VDJCGene, AlignmentHit<NucleotideSequence, VDJCGene>> vAligner,
                 BatchAlignerWithBaseWithFilter<NucleotideSequence, VDJCGene, AlignmentHit<NucleotideSequence, VDJCGene>> jAligner,
                 BatchAlignerWithBaseWithFilter<NucleotideSequence, VDJCGene, AlignmentHit<NucleotideSequence, VDJCGene>> cAligner) {
        super(initialized, parameters, genesToAlign, usedGenes, singleDAligner, filters, vAligner, jAligner, cAligner);
    }

    VDJCAlignerS copyWithoutListener() {
        if (!initialized)
            throw new IllegalStateException();
        else
            return new VDJCAlignerS(
                    true, parameters, genesToAlign, usedGenes, singleDAligner,
                    filters, vAligner, jAligner, cAligner);
    }

    @Override
    protected BatchAlignerWithBaseParameters extractBatchParameters(KGeneAlignmentParameters init) {
        final BatchAlignerWithBaseParameters p = init.getParameters().clone();
        if (p instanceof KAlignerParameters) {
            KAlignerParameters p1 = (KAlignerParameters) p;
            p1.setAbsoluteMinScore(init.getMinSumScore());
            p1.setRelativeMinScore(init.getRelativeMinScore());
        } else if (p instanceof KAlignerParameters2) {
            KAlignerParameters2 p2 = (KAlignerParameters2) p;
            p2.setAbsoluteMinScore(init.getMinSumScore());
            p2.setRelativeMinScore(init.getRelativeMinScore());
        } else
            throw new RuntimeException();

        return p;
    }

    @Override
    protected VDJCAlignments process0(NSQTuple input) {
        // Different algorithms for
        // -OallowPartialAlignments=false and -OallowPartialAlignments=true
        return parameters.getAllowPartialAlignments() ?
                processPartial(input) :
                processStrict(input);
    }

    private VDJCAlignments processPartial(NSQTuple input) {
        Target[] targets = parameters.getReadsLayout().createTargets(input);

        KVJResultsForSingle[] results = new KVJResultsForSingle[targets.length];
        for (int i = 0; i < results.length; i++) {
            results[i] = align(targets[i]);
            results[i].performChainFilteringIfNeeded();
        }

        KVJResultsForSingle topResult = null;

        for (KVJResultsForSingle result : results) {
            if (!result.hasNoKVNorKJHits())
                result.alignDC();
            if (topResult == null || topResult.sumScore() < result.sumScore())
                topResult = result;
        }

        if (topResult.hasNoKVNorKJHits()) {
            onFailedAlignment(VDJCAlignmentFailCause.NoHits);
            return null;
        }

        // Checking minimal sum score
        if (topResult.sumScore() < parameters.getMinSumScore()) {
            onFailedAlignment(VDJCAlignmentFailCause.LowTotalScore);
            return null;
        }

        // Filtering hits basing on minSumScore
        topResult.calculateHits(parameters.getMinSumScore(), parameters.getMaxHits());

        VDJCAlignments alignment = topResult.toVDJCAlignments(input);

        // Final check
        if (!parameters.getAllowNoCDR3PartAlignments()) {
            // CDR3 Begin / End
            if (!alignment.getPartitionedTarget(0).getPartitioning().isAvailable(reqPointL)
                    && !alignment.getPartitionedTarget(0).getPartitioning().isAvailable(reqPointR)) {
                onFailedAlignment(VDJCAlignmentFailCause.NoCDR3Parts);
                return null;
            }
        }

        // Read successfully aligned

        onSuccessfulAlignment(alignment);
        return alignment;
    }

    private VDJCAlignments processStrict(NSQTuple input) {
        Target[] targets = parameters.getReadsLayout().createTargets(input);

        // Algorithm below relies on this fact
        assert targets.length <= 2;

        boolean anyIsFull = false,
                anyHasJ = false,
                anyHasV = false;

        KVJResultsForSingle[] results = new KVJResultsForSingle[targets.length];
        for (int i = 0; i < results.length; i++) {
            results[i] = align(targets[i]);
            results[i].performChainFilteringIfNeeded();
            anyIsFull |= results[i].hasKVAndJHits();
            anyHasJ |= results[i].hasKJHits();
            anyHasV |= results[i].hasKVHits();
        }

        if (!anyIsFull) {
            if (!anyHasJ && !anyHasV)
                onFailedAlignment(VDJCAlignmentFailCause.NoHits);
            else if (!anyHasJ)
                onFailedAlignment(VDJCAlignmentFailCause.NoJHits);
            else
                onFailedAlignment(VDJCAlignmentFailCause.NoVHits);
            return null;
        }

        KVJResultsForSingle topResult = null;

        // Calculating best result
        for (KVJResultsForSingle result : results)
            if (result.hasKVAndJHits())
                topResult = topResult != null ? null : result;

        if (topResult == null) { // Both results are full
            // Finalizing alignment for both results to determine who is the best
            for (KVJResultsForSingle result : results) {
                result.alignDC();
                if (topResult == null || topResult.sumScore() < result.sumScore())
                    topResult = result;
            }
        } else
            // Align C and D genes only for best result
            topResult.alignDC();

        // Checking minimal sum score
        if (topResult.sumScore() < parameters.getMinSumScore()) {
            onFailedAlignment(VDJCAlignmentFailCause.LowTotalScore);
            return null;
        }

        // Filtering hits basing on minSumScore
        topResult.calculateHits(parameters.getMinSumScore(), parameters.getMaxHits());

        if (topResult.hasVAndJHits()) {
            VDJCAlignments alignment = topResult.toVDJCAlignments(input);
            onSuccessfulAlignment(alignment);
            return alignment;
        } else {
            onFailedAlignment(VDJCAlignmentFailCause.LowTotalScore);
            return null;
        }
    }

    // TODO all this ifs can be simplified
    private KVJResultsForSingle align(Target target) {
        NucleotideSequence sequence = target.targets[0].getSequence();

        ensureInitialized();

        AlignmentResult<AlignmentHit<NucleotideSequence, VDJCGene>> vResult, jResult;

        switch (parameters.getVJAlignmentOrder()) {
            case VThenJ:
                vResult = vAligner.align(sequence);

                // If there is no results for V return
                if (!vResult.hasHits())
                    return new KVJResultsForSingle(target,
                            vResult, // V result is empty
                            // If -OallowPartialAlignments=true try align J gene
                            parameters.getAllowPartialAlignments() ?
                                    jAligner.align(sequence) : null);

                // Returning result
                int jFrom = vResult.getBestHit().getAlignment().getSequence2Range().getTo() - parameters.getVJOverlapWindow();
                jFrom = jFrom < 0 ? 0 : jFrom;
                return new KVJResultsForSingle(target, vResult,
                        jAligner.align(sequence,
                                jFrom,
                                sequence.size(),
                                getFilter(GeneType.Joining, vResult.getHits())));
            case JThenV:
                jResult = jAligner.align(sequence);

                // If there is no results for J return
                if (!jResult.hasHits())
                    return new KVJResultsForSingle(target,
                            // If -OallowPartialAlignments=true try align V gene
                            parameters.getAllowPartialAlignments() ?
                                    vAligner.align(sequence) : null,
                            jResult); // J result is empty

                // Returning result
                int vTo = jResult.getBestHit().getAlignment().getSequence2Range().getFrom() + parameters.getVJOverlapWindow();
                vTo = vTo > sequence.size() ? sequence.size() : vTo;
                return new KVJResultsForSingle(target, vAligner.align(sequence, 0,
                        vTo,
                        getFilter(GeneType.Variable, jResult.getHits())),
                        jResult);
        }

        throw new IllegalArgumentException("vjAlignmentOrder not set.");
    }

    final class KVJResultsForSingle {
        final Target target;
        final List<AlignmentHit<NucleotideSequence, VDJCGene>> vResult, jResult;
        AlignmentHit<NucleotideSequence, VDJCGene>[] vHits, jHits;
        VDJCHit[] dHits = null, cHits = null;

        public KVJResultsForSingle(Target target,
                                   AlignmentResult<AlignmentHit<NucleotideSequence, VDJCGene>> vResult,
                                   AlignmentResult<AlignmentHit<NucleotideSequence, VDJCGene>> jResult) {
            this.target = target;
            this.vResult = vResult == null ?
                    Collections.<AlignmentHit<NucleotideSequence, VDJCGene>>emptyList() :
                    vResult.getHits();
            this.jResult = jResult == null ?
                    Collections.<AlignmentHit<NucleotideSequence, VDJCGene>>emptyList() :
                    jResult.getHits();
        }

        Chains getVJCommonChains() {
            return getChains(vResult).intersection(getChains(jResult));
        }

        Chains getChains(List<AlignmentHit<NucleotideSequence, VDJCGene>> result) {
            Chains c = Chains.EMPTY;
            for (AlignmentHit<NucleotideSequence, VDJCGene> hit : result)
                c = c.merge(hit.getRecordPayload().getChains());
            return c;
        }

        /**
         * Filter V/J hits with common chain only
         */
        void performChainFilteringIfNeeded() {
            // Check if parameters allow chimeras
            if (parameters.isAllowChimeras())
                return;

            // Calculate common chains
            Chains commonChains = getVJCommonChains();

            if (commonChains.isEmpty())
                // Exceptional case, or partial alignment
                return;

            // Filtering V genes
            for (int i = vResult.size() - 1; i >= 0; --i)
                if (!vResult.get(i).getRecordPayload().getChains().intersects(commonChains))
                    vResult.remove(i);

            // Filtering J genes
            for (int i = jResult.size() - 1; i >= 0; --i)
                if (!jResult.get(i).getRecordPayload().getChains().intersects(commonChains))
                    jResult.remove(i);
        }

        @SuppressWarnings("unchecked")
        public void calculateHits(float minTotalScore, int maxHits) {
            float preThreshold = minTotalScore - sumScore();
            this.vHits = hasKVHits() ? extractHits(preThreshold + vResult.get(0).getAlignment().getScore(),
                    vResult, maxHits) : new AlignmentHit[0];
            this.jHits = hasKJHits() ? extractHits(preThreshold + jResult.get(0).getAlignment().getScore(),
                    jResult, maxHits) : new AlignmentHit[0];
        }

        public void alignDC() {
            NucleotideSequence sequence = target.targets[0].getSequence();

            boolean doCAlignment = cAligner != null;

            if (parameters.getAllowNoCDR3PartAlignments())
                doCAlignment &= hasKVAndJHits() || hasKJHits();
            else
                doCAlignment &= hasKJHits();

            if (doCAlignment) {
                int from = hasKJHits() ?
                        jResult.get(0).getAlignment().getSequence2Range().getTo()
                        : 0;
                AlignmentResult<AlignmentHit<NucleotideSequence, VDJCGene>> res = cAligner
                        .align(sequence, from, sequence.size(),
                                getFilter(GeneType.Constant, vResult, jResult));

                cHits = createHits(res.getHits(), parameters.getFeatureToAlign(GeneType.Constant));
            } else
                cHits = new VDJCHit[0];

            if (singleDAligner != null && hasKVAndJHits()) {
                //Alignment of D gene
                int from = vResult.get(0).getAlignment().getSequence2Range().getTo(),
                        to = jResult.get(0).getAlignment().getSequence2Range().getFrom();
                List<PreVDJCHit> dResult = from > to ?
                        Collections.<PreVDJCHit>emptyList() :
                        singleDAligner.align0(sequence, getPossibleDLoci(
                                        wrapAlignmentHits(vHits),
                                        wrapAlignmentHits(jHits), cHits),
                                from, to);
                dHits = PreVDJCHit.convert(getDGenesToAlign(),
                        parameters.getFeatureToAlign(GeneType.Diversity), dResult);
            }

        }

        public boolean hasNoKVNorKJHits() {
            return vResult.isEmpty() && jResult.isEmpty();
        }

        public boolean hasKVAndJHits() {
            return !vResult.isEmpty() && !jResult.isEmpty();
        }

        public boolean hasKVHits() {
            return !vResult.isEmpty();
        }

        public boolean hasKJHits() {
            return !jResult.isEmpty();
        }

        public boolean hasVAndJHits() {
            return vHits != null && vHits.length > 0 &&
                    jHits != null && jHits.length > 0;
        }

        public VDJCHit[] getVHits(GeneFeature feature) {
            return createHits(vHits, feature);
        }

        public VDJCHit[] getJHits(GeneFeature feature) {
            return createHits(jHits, feature);
        }

        public float sumScore() {
            float score = 0.0f;
            if (vResult != null && !vResult.isEmpty())
                score += vResult.get(0).getAlignment().getScore();
            if (jResult != null && !jResult.isEmpty())
                score += jResult.get(0).getAlignment().getScore();
            if (parameters.doIncludeDScore() && dHits != null && dHits.length > 0)
                score += dHits[0].getScore();
            if (parameters.doIncludeCScore() && cHits != null && cHits.length > 0)
                score += cHits[0].getScore();
            return score;
        }

        public VDJCAlignments toVDJCAlignments(NSQTuple input) {
            EnumMap<GeneType, VDJCHit[]> hits = new EnumMap<>(GeneType.class);

            hits.put(GeneType.Variable, getVHits(parameters.getFeatureToAlign(GeneType.Variable)));
            hits.put(GeneType.Joining, getJHits(parameters.getFeatureToAlign(GeneType.Joining)));

            if (dHits != null)
                hits.put(GeneType.Diversity, dHits);

            if (cHits != null)
                hits.put(GeneType.Constant, cHits);

            return new VDJCAlignments(hits, TagCount.NO_TAGS_1, target.targets,
                    new SequenceHistory[]{
                            new SequenceHistory.RawSequence(input.getId(), (byte) 0,
                                    target.getRCStateOfTarget(0),
                                    target.targets[0].size())},
                    parameters.isSaveOriginalSequence() ? new NSQTuple[]{input} : null);
        }
    }

    public static VDJCHit[] createHits(AlignmentHit<NucleotideSequence, VDJCGene>[] kHits, GeneFeature feature) {
        VDJCHit[] hits = new VDJCHit[kHits.length];
        for (int i = 0; i < kHits.length; i++)
            hits[i] = new VDJCHit(kHits[i].getRecordPayload(), kHits[i].getAlignment(), feature);
        return hits;
    }

    public static VDJCHit[] createHits(List<AlignmentHit<NucleotideSequence, VDJCGene>> kHits, GeneFeature feature) {
        VDJCHit[] hits = new VDJCHit[kHits.size()];
        for (int i = 0; i < kHits.size(); i++)
            hits[i] = new VDJCHit(kHits.get(i).getRecordPayload(), kHits.get(i).getAlignment(), feature);
        return hits;
    }

    private static AlignmentHit<NucleotideSequence, VDJCGene>[] extractHits(float minScore,
                                                                            List<AlignmentHit<NucleotideSequence, VDJCGene>> result,
                                                                            int maxHits) {
        int count = 0;
        for (AlignmentHit<NucleotideSequence, VDJCGene> hit : result)
            if (hit.getAlignment().getScore() >= minScore) {
                if (++count >= maxHits)
                    break;
            } else
                break;

        AlignmentHit<NucleotideSequence, VDJCGene>[] res = new AlignmentHit[count];
        for (int i = 0; i < count; ++i)
            res[i] = result.get(i);

        return res;
    }
}

