package com.milaboratory.mixcr.trees;

import com.milaboratory.mixcr.util.Java9Util;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Comparator.comparing;

public class TreeBuilderByAncestors<T, E, M> {
    private final Function<M, BigDecimal> distance;
    /**
     * parent, observed -> reconstructed
     */
    private final BiFunction<E, T, E> asAncestor;
    private final BiFunction<E, E, M> mutationsBetween;
    private final BiFunction<E, M, E> mutate;
    private final BiFunction<M, M, M> findCommonMutations;

    private final Tree<ObservedOrReconstructed<T, E>> tree;
    private int observedNodesCount = 0;

    public TreeBuilderByAncestors(
            E root,
            Function<M, BigDecimal> distance,
            BiFunction<E, E, M> mutationsBetween,
            BiFunction<E, M, E> mutate,
            BiFunction<E, T, E> asAncestor,
            BiFunction<M, M, M> findCommonMutations
    ) {
        this.distance = distance;
        this.mutationsBetween = mutationsBetween;
        this.mutate = mutate;
        this.asAncestor = asAncestor;
        this.findCommonMutations = findCommonMutations;
        tree = new Tree<>(new Tree.Node<>(new Reconstructed<>(root, BigDecimal.ZERO)));
    }

    public int getObservedNodesCount() {
        return observedNodesCount;
    }

    /**
     * Assumptions:
     * 1. Nodes must be added in order by distance from the root
     * 2. findCommonAncestor is constructing ancestor based on root (left common mutations on nodes from root)
     * <p>
     * <p>
     * Invariants of the tree:
     * 1. Root node is always reconstructed. Root is not changing
     * 2. Leaves are always observed.
     * 3. Distance between observed and reconstructed could be zero (they are identical).
     * 4. Distance between observed can't be zero.
     * 5. Siblings have no common ancestors.
     */
    public TreeBuilderByAncestors<T, E, M> addNode(T toAdd) {
        observedNodesCount++;
        List<Tree.Node<ObservedOrReconstructed<T, E>>> nearestNodes = tree.allNodes()
                .filter(it -> it.getContent() instanceof Reconstructed<?, ?>)
                .collect(Collectors.groupingBy(compareWith ->
                        {
                            Reconstructed<T, E> nodeContent = (Reconstructed<T, E>) compareWith.getContent();
                            return distance.apply(mutationsBetween.apply(nodeContent.getContent(), asAncestor.apply(nodeContent.getContent(), toAdd)))
                                    .add(nodeContent.getMinDistanceFromObserved());
                        }
                ))
                .entrySet().stream()
                .min(Map.Entry.comparingByKey())
                .map(Map.Entry::getValue)
                .orElseThrow(IllegalArgumentException::new);
        Stream<Action> possibleActions = nearestNodes.stream().flatMap(chosenNode -> {
            //search for siblings with common mutations with the added node
            Optional<Replace> siblingToMergeWith = chosenNode.getLinks().stream()
                    .map(Tree.NodeLink::getNode)
                    .filter(it -> it.getContent() instanceof Reconstructed<?, ?>)
                    .map(sibling -> replaceChild(toAdd, chosenNode, sibling))
                    .filter(Objects::nonNull)
                    //choose a sibling with max score of common mutations
                    .max(comparing(Replace::getDistanceFromParentToCommon));
            if (siblingToMergeWith.isPresent()) {
                //the added node (A), the chosen node (B), the sibling (D) has common ancestor (C) with A
                //
                //       P                   P
                //       |                   |
                //     --*--               --*--
                //     |   |               |   |
                //     K   B      ==>      K   B
                //         |                   |
                //       --*--               --*--
                //       |   |               |   |
                //       R   D               R   C
                //                               |
                //                             --*--
                //                             |   |
                //                             A   D
                //
                // R and C has no common ancestors because R and D haven't one
                return Java9Util.stream(siblingToMergeWith);
            } else {
                //the added node (A), the chosen node (B), no siblings with common ancestors
                //
                //       P                   P
                //       |                   |
                //     --*--               --*--
                //     |   |               |   |
                //     K   B      ==>      K   B
                //         |                   |
                //       --*--               --*-----
                //       |   |               |   |  |
                //       R   T               R   T  A
                return Stream.concat(
                        Stream.of(insertAsDirectDescendant(toAdd, chosenNode)),
                        Java9Util.stream(Optional.ofNullable(chosenNode.getParent()).map(parent -> replaceChild(toAdd, parent, chosenNode)))
                );
            }
        });
        //optimize sum of distances from observed nodes
        List<Action> temp = possibleActions.collect(Collectors.toList());
        Optional<Action> bestAction = temp.stream().min(
                Comparator.<Action, BigDecimal>comparing(action -> action.changeOfDistance().add(action.distanceFromObserved()))
                        .thenComparing(Action::changeOfDistance)
                        .thenComparing(action -> action instanceof TreeBuilderByAncestors.Insert ? 1 : -1)
        );
        bestAction.orElseThrow(IllegalArgumentException::new).apply();
        return this;
    }

    private Insert insertAsDirectDescendant(T toAdd, Tree.Node<ObservedOrReconstructed<T, E>> parent) {
        E contentOfParent = ((Reconstructed<T, E>) parent.getContent()).getContent();
        E contentAsAncestor = asAncestor.apply(contentOfParent, toAdd);
        BigDecimal distanceBetweenParentAndAdded = distance.apply(mutationsBetween.apply(contentOfParent, contentAsAncestor));
        return new Insert(
                parent,
                toAdd,
                distanceBetweenParentAndAdded
        );
    }

    /**
     * Replacing node with subtree with common ancestor and leaves as added node and node that was here before
     *
     * <pre>
     *              P                   P
     *              |                   |
     *            --*--               --*--
     *            |   |    ==>        |   |
     *            R   B               R   C
     *                                    |
     *                                  --*--
     *                                  |   |
     *                                  A   B
     * </pre>
     * P - parent
     * A - toAdd
     * B - child
     * C - commonAncestor
     */
    private Replace replaceChild(T toAdd, Tree.Node<ObservedOrReconstructed<T, E>> parent, Tree.Node<ObservedOrReconstructed<T, E>> child) {
        Reconstructed<T, E> parentContent = (Reconstructed<T, E>) parent.getContent();
        E contentOfAdded = asAncestor.apply(parentContent.getContent(), toAdd);
        E childContent = ((Reconstructed<T, E>) child.getContent()).getContent();

        M mutationsToAdded = mutationsBetween.apply(parentContent.getContent(), contentOfAdded);
        M mutationsToChild = mutationsBetween.apply(parentContent.getContent(), childContent);

        M commonMutations = findCommonMutations.apply(mutationsToAdded, mutationsToChild);
        BigDecimal distanceFromParentToCommon = distance.apply(commonMutations);
        //if distance is zero than there is no common mutations
        if (distanceFromParentToCommon.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }

        E commonAncestor = mutate.apply(parentContent.getContent(), commonMutations);
        M fromCommonToChild = mutationsBetween.apply(commonAncestor, childContent);
        BigDecimal distanceFromCommonAncestorToChild = distance.apply(fromCommonToChild);
        //if distance is zero than result of replacement equals to insertion
        if (distanceFromCommonAncestorToChild.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        BigDecimal distanceFromCommonToAdded = distance.apply(mutationsBetween.apply(commonAncestor, contentOfAdded));

        BigDecimal minDistanceFromObserved = Stream.of(
                distanceFromCommonToAdded,
                distanceFromCommonAncestorToChild,
                parentContent.getMinDistanceFromObserved().add(distance.apply(commonMutations))
        ).min(BigDecimal::compareTo).get();
//        //commonAncestor is equal to added node, so minDistanceFromObserved is 0
//        if (distanceFromCommonToAdded.compareTo(BigDecimal.ZERO) == 0) {
//            minDistanceFromObserved = BigDecimal.ZERO;
//        } else {
//            minDistanceFromObserved = parentContent.getMinDistanceFromObserved().add(distance.apply(commonMutations));
//        }
        Tree.Node<ObservedOrReconstructed<T, E>> replacement = new Tree.Node<>(new Reconstructed<>(
                commonAncestor,
                minDistanceFromObserved
        ));
        Tree.Node<ObservedOrReconstructed<T, E>> rebuiltChild = new Tree.Node<>(new Reconstructed<>(mutate.apply(commonAncestor, fromCommonToChild), ((Reconstructed<T, E>) child.getContent()).getMinDistanceFromObserved()));
        child.getLinks().forEach(link -> rebuiltChild.addChild(link.getNode(), link.getDistance()));
        replacement.addChild(rebuiltChild, distanceFromCommonAncestorToChild);

        Tree.Node<ObservedOrReconstructed<T, E>> nodeToAdd;
        if (distanceFromCommonToAdded.compareTo(BigDecimal.ZERO) != 0) {
            nodeToAdd = new Tree.Node<>(new Reconstructed<>(asAncestor.apply(commonAncestor, toAdd), BigDecimal.ZERO));
            nodeToAdd.addChild(new Tree.Node<>(new Observed<>(toAdd)), BigDecimal.ZERO);
        } else {
            nodeToAdd = new Tree.Node<>(new Observed<>(toAdd));
        }
        replacement.addChild(nodeToAdd, distanceFromCommonToAdded);

        return new Replace(parent,
                child,
                replacement,
                distanceFromParentToCommon,
                distanceFromCommonAncestorToChild.add(distanceFromCommonToAdded)
        );
    }

    public Tree<ObservedOrReconstructed<T, E>> getTree() {
        return tree;
    }

    private static abstract class Action {
        abstract BigDecimal changeOfDistance();

        abstract BigDecimal distanceFromObserved();

        abstract void apply();
    }

    private class Insert extends Action {
        private final Tree.Node<ObservedOrReconstructed<T, E>> parent;
        private final T insertion;
        private final BigDecimal distanceFromParentAndInsertion;

        public Insert(Tree.Node<ObservedOrReconstructed<T, E>> parent, T insertion, BigDecimal distanceFromParentAndInsertion) {
            this.parent = parent;
            this.insertion = insertion;
            this.distanceFromParentAndInsertion = distanceFromParentAndInsertion;
        }

        @Override
        BigDecimal changeOfDistance() {
            return distanceFromParentAndInsertion;
        }

        @Override
        BigDecimal distanceFromObserved() {
            return ((Reconstructed<T, E>) parent.getContent()).getMinDistanceFromObserved();
        }

        @Override
        void apply() {
            Reconstructed<T, E> parentContent = (Reconstructed<T, E>) parent.getContent();
            Tree.Node<ObservedOrReconstructed<T, E>> nodeToInsert;
            if (distanceFromParentAndInsertion.compareTo(BigDecimal.ZERO) == 0) {
                parentContent.setMinDistanceFromObserved(BigDecimal.ZERO);
                nodeToInsert = new Tree.Node<>(new Observed<>(insertion));
            } else {
                nodeToInsert = new Tree.Node<>(new Reconstructed<>(asAncestor.apply(parentContent.getContent(), insertion), BigDecimal.ZERO));
                nodeToInsert.addChild(new Tree.Node<>(new Observed<>(insertion)), BigDecimal.ZERO);
            }
            parent.addChild(nodeToInsert, distanceFromParentAndInsertion);
        }
    }

    private class Replace extends Action {
        private final Tree.Node<ObservedOrReconstructed<T, E>> parent;
        private final Tree.Node<ObservedOrReconstructed<T, E>> replaceWhat;
        private final Tree.Node<ObservedOrReconstructed<T, E>> replacement;
        private final BigDecimal distanceFromParentToCommon;
        private final BigDecimal sumOfDistancesFromAncestor;

        public Replace(Tree.Node<ObservedOrReconstructed<T, E>> parent,
                       Tree.Node<ObservedOrReconstructed<T, E>> replaceWhat,
                       Tree.Node<ObservedOrReconstructed<T, E>> replacement,
                       BigDecimal distanceFromParentToCommon,
                       BigDecimal sumOfDistancesFromAncestor) {
            this.parent = parent;
            this.replaceWhat = replaceWhat;
            this.replacement = replacement;
            this.distanceFromParentToCommon = distanceFromParentToCommon;
            this.sumOfDistancesFromAncestor = sumOfDistancesFromAncestor;
        }

        BigDecimal getDistanceFromParentToCommon() {
            return distanceFromParentToCommon;
        }

        @Override
        BigDecimal changeOfDistance() {
            BigDecimal distanceFromParent = replaceWhat.getDistanceFromParent() != null ? replaceWhat.getDistanceFromParent() : BigDecimal.ZERO;
            return distanceFromParentToCommon.subtract(distanceFromParent).add(sumOfDistancesFromAncestor);
        }

        @Override
        BigDecimal distanceFromObserved() {
            return ((Reconstructed<T, E>) parent.getContent()).getMinDistanceFromObserved();
        }

        @Override
        void apply() {
            parent.replaceChild(replaceWhat, replacement, distanceFromParentToCommon);
        }
    }

    public static abstract class ObservedOrReconstructed<T, E> {
        public <R1, R2> ObservedOrReconstructed<R1, R2> map(Function<T, R1> mapObserved, Function<E, R2> mapReconstructed) {
            if (this instanceof Observed<?, ?>) {
                return new Observed<>(mapObserved.apply(((Observed<T, E>) this).getContent()));
            } else if (this instanceof Reconstructed<?, ?>) {
                Reconstructed<T, E> casted = (Reconstructed<T, E>) this;
                return new Reconstructed<>(mapReconstructed.apply(casted.getContent()), casted.getMinDistanceFromObserved());
            } else {
                throw new IllegalArgumentException();
            }
        }

        public <R> R convert(Function<T, R> mapObserved, Function<E, R> mapReconstructed) {
            if (this instanceof Observed<?, ?>) {
                return mapObserved.apply(((Observed<T, E>) this).getContent());
            } else if (this instanceof Reconstructed<?, ?>) {
                return mapReconstructed.apply(((Reconstructed<T, E>) this).getContent());
            } else {
                throw new IllegalArgumentException();
            }
        }
    }

    static class Observed<T, E> extends ObservedOrReconstructed<T, E> {
        private final T content;

        private Observed(T content) {
            this.content = content;
        }

        public T getContent() {
            return content;
        }
    }

    static class Reconstructed<T, E> extends ObservedOrReconstructed<T, E> {
        private final E content;
        /**
         * This number represents distance that will be added after removing of all reconstructed nodes from the tree.
         * <p>
         * zero for root
         * zero if had an observed child with distance zero
         * otherwise it is a distance from the nearest parent with minDistanceFromObserved equals zero
         */
        private BigDecimal minDistanceFromObserved;

        private Reconstructed(E content, BigDecimal minDistanceFromObserved) {
            this.content = content;
            this.minDistanceFromObserved = minDistanceFromObserved;
        }

        private void setMinDistanceFromObserved(BigDecimal minDistanceFromObserved) {
            this.minDistanceFromObserved = minDistanceFromObserved;
        }

        BigDecimal getMinDistanceFromObserved() {
            return minDistanceFromObserved;
        }

        public E getContent() {
            return content;
        }
    }
}
