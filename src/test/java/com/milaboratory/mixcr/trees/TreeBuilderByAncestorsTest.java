package com.milaboratory.mixcr.trees;

import com.google.common.collect.Lists;
import com.milaboratory.mixcr.trees.TreeBuilderByAncestors.Observed;
import com.milaboratory.mixcr.trees.TreeBuilderByAncestors.ObservedOrReconstructed;
import com.milaboratory.mixcr.trees.TreeBuilderByAncestors.Reconstructed;
import org.junit.Ignore;
import org.junit.Test;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TreeBuilderByAncestorsTest {
    private final TreePrinter<ObservedOrReconstructed<List<Integer>, List<Integer>>> treePrinter = new NewickTreePrinter<>(
            node -> node.convert(this::print, content -> "'" + print(content) + "'"),
            true,
            false
    );
    private final NewickTreePrinter<List<Integer>> treePrinterOnlyReal = new NewickTreePrinter<>(
            this::print,
            true,
            false
    );


    /**
     * <pre>
     *          |
     *         (A)-0-A
     * =======
     *  A ⊂ B,
     *  M1: A*M1 = B
     * =======
     *          |
     *     ----(A)-0-A
     *     |
     *    |M1|
     *     |
     *    (B)-0-B
     * </pre>
     */
    @Test
    public void addDirectAncestor() {
        Tree<ObservedOrReconstructed<List<Integer>, List<Integer>>> tree = treeBuilder(3)
                .addNode(Lists.newArrayList(1, 0, 0))
                .addNode(Lists.newArrayList(1, 1, 0))
                .getTree();
        assertTree("(((110:0)'110':1,100:0)'100':1)'000';", tree);
    }

    /**
     * <pre>
     *          |
     *     ----(A)-0-A
     *     |
     *    |M1|
     *     |
     *    (B)-0-B
     * =======
     *  A ⊂ C, B ⊄ C
     *  M2: A*M2 = C,
     *  M1⋂M2 = ∅
     * =======
     *          |
     *     ----(A)-------------
     *     |         |        |
     *    |M1|      |M2|      0
     *     |         |        |
     *    (B)-0-B   (C)-0-C   A
     * </pre>
     */
    @Test
    public void addSecondDirectAncestor() {
        Tree<ObservedOrReconstructed<List<Integer>, List<Integer>>> tree = treeBuilder(3)
                .addNode(Lists.newArrayList(1, 0, 0))
                .addNode(Lists.newArrayList(1, 1, 0))
                .addNode(Lists.newArrayList(1, 0, 1))
                .getTree();
        assertTree("(((101:0)'101':1,(110:0)'110':1,100:0)'100':1)'000';", tree);
    }

    @Ignore
    @Test
    public void addSecondDirectAncestor2() {
        Tree<List<Integer>> original = new Tree<>(
                new Tree.Node<>(parseNode("0000000000"))
                        .addChild(new Tree.Node<>(parseNode("7070500060"))
                                .addChild(new Tree.Node<>(parseNode("7370500000"))
                                        .addChild(new Tree.Node<>(parseNode("7370504030")))))
        );
        compareTrees(original, rebuildTree(original));
    }

    @Test
    public void insertAsDescendantInsteadOfSiblingIfItChangeDistancesLess() {
        Tree<List<Integer>> original = new Tree<>(
                new Tree.Node<>(parseNode("0000"))
                        .addChild(new Tree.Node<>(parseNode("0010"))
                                .addChild(new Tree.Node<>(parseNode("0712"))))
                        .addChild(new Tree.Node<>(parseNode("0002"))
                                .addChild(new Tree.Node<>(parseNode("7702"))
                                        .addChild(new Tree.Node<>(parseNode("7782")))))
        );
        compareTrees(original, rebuildTree(original));
    }

    /**
     * <pre>
     *          |
     *     ----(A)-------------
     *     |         |        |
     *    |M1|      |M2|      0
     *     |         |        |
     *    (B)-0-B   (C)-0-C   A
     * =======
     *  A ⊂ D, B ⊄ D, C ⊄ D
     *  M3: A*M3 = D,
     *  M1⋂M3 = ∅, M2⋂M3 = ∅,
     * =======
     *          |
     *     ----(A)----------------------
     *     |         |        |        |
     *    |M1|      |M2|     |M3|      0
     *     |         |        |        |
     *    (B)-0-B   (C)-0-C  (D)-0-D   A
     * </pre>
     */
    @Test
    public void addThirdDirectAncestor() {
        Tree<ObservedOrReconstructed<List<Integer>, List<Integer>>> tree = treeBuilder(3)
                .addNode(Lists.newArrayList(0, 0, 0))
                .addNode(Lists.newArrayList(1, 0, 0))
                .addNode(Lists.newArrayList(0, 1, 0))
                .addNode(Lists.newArrayList(0, 0, 1))
                .getTree();
        assertTree("((001:0)'001':1,(010:0)'010':1,(100:0)'100':1,000:0)'000';", tree);
    }

    /**
     * <pre>
     *          |
     *         (A)-0-A
     * =======
     *  A ⊄ B,
     *  C: C ⊂ A, C ⊂ B, A ⊂ C
     *  M1: C*M1 = A,
     *  M2: C*M2 = B
     * =======
     *          |
     *     ----(C)----
     *     |         |
     *    |M1|      |M2|
     *     |         |
     *    (A)-0-A   (B)-0-B
     * </pre>
     */
    @Test
    public void addNodeWithIntersection() {
        Tree<ObservedOrReconstructed<List<Integer>, List<Integer>>> tree = treeBuilder(3)
                .addNode(Lists.newArrayList(1, 1, 0))
                .addNode(Lists.newArrayList(1, 0, 1))
                .getTree();
        assertTree("(((101:0)'101':1,(110:0)'110':1)'100':1)'000';", tree);
    }

    /**
     * <pre>
     *          |
     *     ----(A)------------
     *     |         |       |
     *    |M1|      |M2|     0
     *     |         |       |
     *    (B)-0-B   (C)-0-C  A
     * =======
     *  D ⊄ A, D ⊄ B
     *  B <-> D < A <-> D
     *  B <-> D < C <-> D
     *  E: E ⊂ B, E ⊂ D, A ⊂ E
     *  M3: A*M3 = E,
     *  M4: E*M4 = B,
     *  M5: E*M5 = D
     * =======
     *            |
     *       ----(A)------------
     *       |         |       |
     *      |M3|      |M2|     0
     *       |         |       |
     *  ----(E)----   (C)-0-C  A
     *  |         |
     * |M4|      |M5|
     *  |         |
     * (B)-0-B   (D)-0-D
     *
     * </pre>
     */
    @Test
    public void addNodeWithIntersectionOnSecondLevel() {
        Tree<ObservedOrReconstructed<List<Integer>, List<Integer>>> tree = treeBuilder(5)
                .addNode(Lists.newArrayList(0, 0, 0, 0, 0))//A
                .addNode(Lists.newArrayList(0, 0, 0, 0, 1))//C
                .addNode(Lists.newArrayList(1, 1, 1, 0, 0))//B
                .addNode(Lists.newArrayList(1, 0, 1, 1, 0))//D
                .getTree();
        assertTree("(((10110:0)'10110':1,(11100:0)'11100':1)'10100':2,(00001:0)'00001':1,00000:0)'00000';", tree);
    }

    /**
     * <pre>
     *          |
     *     ----(A)------------
     *     |         |       |
     *    |M1|      |M2|     0
     *     |         |       |
     *    (B)-0-B   (C)-0-C  A
     * =======
     *  D ⊄ A, D ⊄ B
     *  B <-> D > A <-> D
     *  B <-> D < C <-> D
     *  E: E ⊂ B, E ⊂ D, A ⊂ E
     *  M3: A*M3 = E,
     *  M4: E*M4 = B,
     *  M5: E*M5 = D
     * =======
     *            |
     *       ----(A)------------
     *       |         |       |
     *      |M3|      |M2|     0
     *       |         |       |
     *  ----(E)----   (C)-0-C  A
     *  |         |
     * |M4|      |M5|
     *  |         |
     * (B)-0-B   (D)-0-D
     *
     * </pre>
     */
    @Test
    public void addNodeWithIntersectionOnSecondLevelNearerRoot() {
        Tree<ObservedOrReconstructed<List<Integer>, List<Integer>>> tree = treeBuilder(6)
                .addNode(Lists.newArrayList(0, 0, 0, 0, 0, 0))//A
                .addNode(Lists.newArrayList(0, 0, 0, 0, 0, 1))//C
                .addNode(Lists.newArrayList(1, 1, 1, 0, 0, 0))//B
                .addNode(Lists.newArrayList(1, 0, 0, 1, 1, 0))//D
                .getTree();
        assertTree("(((100110:0)'100110':2,(111000:0)'111000':2)'100000':1,(000001:0)'000001':1,000000:0)'000000';", tree);
    }

    /**
     * If there are several nodes with the same distance from added node with must choose those that will minimize change of distances
     */
    @Test
    public void chooseBestResultOfInsertion() {
        Tree<ObservedOrReconstructed<List<Integer>, List<Integer>>> tree = treeBuilder(5)
                .addNode(Lists.newArrayList(0, 0, 0, 0, 0))
                .addNode(Lists.newArrayList(1, 0, 1, 0, 0))
                .addNode(Lists.newArrayList(0, 1, 1, 0, 1))
                .addNode(Lists.newArrayList(1, 1, 1, 0, 1))
                .addNode(Lists.newArrayList(1, 0, 1, 1, 1))
                .getTree();
        assertTree("((((10111:0)'10111':2,10100:0)'10100':1,((11101:0)'11101':1,01101:0)'01101':2)'00100':1,00000:0)'00000';", tree);
    }

    @Test
    public void chooseInsertionNearerToRealNode() {
        Tree<List<Integer>> original = new Tree<>(
                new Tree.Node<>(parseNode("0000000000"))
                        .addChild(new Tree.Node<>(parseNode("1010000000"))
                                .addChild(new Tree.Node<>(parseNode("1110000001"))
                                        .addChild(new Tree.Node<>(parseNode("1110001101")))))
                        .addChild(new Tree.Node<>(parseNode("0000000001"))
                                .addChild(new Tree.Node<>(parseNode("0100000001"))
                                        .addChild(new Tree.Node<>(parseNode("0110001001"))
                                                .addChild(new Tree.Node<>(parseNode("1111001001"))
                                                        .addChild(new Tree.Node<>(parseNode("1111001101")))))))
                        .addChild(new Tree.Node<>(parseNode("0100000000"))
                                .addChild(new Tree.Node<>(parseNode("1100100000"))
                                        .addChild(new Tree.Node<>(parseNode("1110100000"))
                                                .addChild(new Tree.Node<>(parseNode("1110101000"))
                                                        .addChild(new Tree.Node<>(parseNode("1110101011")))))))
        );
        compareTrees(original, rebuildTree(original));
    }

    @Test
    public void chooseInsertionNearerToRealNodeWithReplaceOfReal() {
        Tree<List<Integer>> original = new Tree<>(
                new Tree.Node<>(parseNode("00000000"))
                        .addChild(new Tree.Node<>(parseNode("60000030"))
                                .addChild(new Tree.Node<>(parseNode("65000030"))
                                        .addChild(new Tree.Node<>(parseNode("65230030")))
                                        .addChild(new Tree.Node<>(parseNode("65000237"))))
                                .addChild(new Tree.Node<>(parseNode("60200032"))
                                        .addChild(new Tree.Node<>(parseNode("65250032")))
                                        .addChild(new Tree.Node<>(parseNode("60230032")))))
        );
        compareTrees(original, rebuildTree(original));
    }

    @Test
    public void commonAncestorCouldNotHaveMutationsThatNotContainsInAParent() {
        Tree<List<Integer>> original = new Tree<>(
                new Tree.Node<>(parseNode("0000"))
                        .addChild(new Tree.Node<>(parseNode("6000"))
                                .addChild(new Tree.Node<>(parseNode("6077"))))
                        .addChild(new Tree.Node<>(parseNode("0007"))
                                .addChild(new Tree.Node<>(parseNode("1077"))))
        );
        //direct comparison of 1077 and 6077 will yield 0077, but with parent 6000 we want them to yield 6077
        compareTrees(original, rebuildTree(original));
    }

    private void compareTrees(Tree<List<Integer>> original, Tree<ObservedOrReconstructed<List<Integer>, List<Integer>>> result) {
        boolean assertion = compareSumOfDistances(original, result);
        if (!assertion) {
            System.out.println("expected:");
            System.out.println(treePrinterOnlyReal.print(calculateDistances(original, this::distance)));
            System.out.println("actual:");
            System.out.println(treePrinterOnlyReal.print(calculateDistances(withoutSynthetic(result), this::distance)));
            System.out.println(treePrinter.print(result));
        }
        assertTrue(assertion);
    }

    private Tree<ObservedOrReconstructed<List<Integer>, List<Integer>>> rebuildTree(Tree<List<Integer>> original) {
        return rebuildTree(original, true);
    }

    private Tree<ObservedOrReconstructed<List<Integer>, List<Integer>>> rebuildTree(Tree<List<Integer>> original, Boolean print) {
        List<Integer> root = IntStream.range(0, original.getRoot().getContent().size())
                .mapToObj(it -> 0)
                .collect(Collectors.toList());
        TreeBuilderByAncestors<List<Integer>, List<Integer>, List<Integer>> treeBuilder = treeBuilder(root.size());
        original.allNodes().map(Tree.Node::getContent)
                .sorted(Comparator.comparing(lead -> distance(root, lead)))
                .forEach(toAdd -> {
                    if (print) {
                        System.out.println(treePrinter.print(treeBuilder.getTree()));
                        System.out.println(print(toAdd));
                    }
                    treeBuilder.addNode(toAdd);
                });
        return treeBuilder.getTree();
    }

    @Ignore
    @Test
    public void randomizedTest() {
        List<Long> failedSeeds = IntStream.range(0, 100_000)
                .mapToObj(it -> ThreadLocalRandom.current().nextLong())
                .filter(seed -> {
                    Random random = new Random(seed);

//                    int arrayLength = 10;
//                    int depth = 5;
//                    Supplier<Integer> branchesCount = () -> 1 + random.nextInt(2);
//                    Supplier<Integer> mutationsCount = () -> 1 + random.nextInt(2);

                    int arrayLength = 8;
                    int depth = 3;
                    Supplier<Integer> branchesCount = () -> 1 + random.nextInt(2);
                    Supplier<Integer> mutationsCount = () -> 1 + random.nextInt(2);

                    boolean print = false;

                    List<Integer> root = new ArrayList<>();
                    for (int i = 0; i < arrayLength; i++) {
                        root.add(0);
                    }

                    Tree.Node<List<Integer>> rootNode = new Tree.Node<>(root);
                    Tree<List<Integer>> original = new Tree<>(rootNode);
                    Set<List<Integer>> insertedLeaves = new HashSet<>();
                    insertedLeaves.add(root);

                    for (int branchNumber = 0; branchNumber < branchesCount.get(); branchNumber++) {
                        List<Tree.Node<List<Integer>>> nodes = Collections.singletonList(rootNode);
                        for (int j = 0; j < depth; j++) {
                            nodes = nodes.stream()
                                    .flatMap(node -> insetChildren(random, mutationsCount, insertedLeaves, node, branchesCount).stream())
                                    .collect(Collectors.toList());
                            if (nodes.isEmpty()) break;
                        }
                    }

                    Tree<ObservedOrReconstructed<List<Integer>, List<Integer>>> rebuild = rebuildTree(original, print);

                    boolean success = compareSumOfDistances(original, rebuild);
                    if (!success) {
                        System.out.println("expected:");
                        System.out.println(treePrinterOnlyReal.print(original));
                        System.out.println("actual:");
                        System.out.println(treePrinterOnlyReal.print(calculateDistances(withoutSynthetic(rebuild), this::distance)));
                        System.out.println(treePrinter.print(this.calculateDistances(rebuild, (a, b) -> distance(getContent(a), getContent(b)))));
                        System.out.println("seed:");
                        System.out.println(seed);
                        System.out.println();
                    }
                    return !success;
                })
                .collect(Collectors.toList());

        assertEquals(Collections.emptyList(), failedSeeds);
    }

    private boolean compareSumOfDistances(Tree<List<Integer>> original, Tree<ObservedOrReconstructed<List<Integer>, List<Integer>>> result) {
        BigDecimal sumOfDistancesInOriginal = sumOfDistances(calculateDistances(original, this::distance));
        BigDecimal sumOfDistancesInConstructed = sumOfDistances(calculateDistances(withoutSynthetic(result), this::distance));
        return sumOfDistancesInOriginal.compareTo(sumOfDistancesInConstructed) >= 0;
    }

    private List<Tree.Node<List<Integer>>> insetChildren(Random random, Supplier<Integer> mutationsCount, Set<List<Integer>> insertedLeaves, Tree.Node<List<Integer>> parent, Supplier<Integer> branchesCount) {
        return IntStream.range(0, branchesCount.get())
                .mapToObj(index -> {
                    List<Integer> possiblePositionsToMutate = IntStream.range(0, parent.getContent().size())
                            .filter(i -> parent.getContent().get(i) == 0)
                            .boxed()
                            .collect(Collectors.toList());
                    Collections.shuffle(possiblePositionsToMutate, random);
                    possiblePositionsToMutate = possiblePositionsToMutate
                            .subList(0, Math.min(possiblePositionsToMutate.size() - 1, mutationsCount.get()));
                    List<Integer> leaf = new ArrayList<>(parent.getContent());
                    possiblePositionsToMutate.forEach(it -> leaf.set(it, random.nextInt(9)));
                    if (insertedLeaves.contains(leaf)) {
                        return null;
                    } else {
                        insertedLeaves.add(leaf);
                        Tree.Node<List<Integer>> inserted = new Tree.Node<>(leaf);
                        parent.addChild(inserted, distance(parent.getContent(), leaf));
                        return inserted;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private BigDecimal sumOfDistances(Tree<?> tree) {
        return tree.getRoot().sumOfDistancesToDescendants();
    }

    private Tree<List<Integer>> withoutSynthetic(Tree<ObservedOrReconstructed<List<Integer>, List<Integer>>> original) {
        List<Integer> rootContent = ((Reconstructed<List<Integer>, List<Integer>>) original.getRoot().getContent()).getContent();
        Tree.Node<List<Integer>> rootNode = new Tree.Node<>(rootContent);
        copyRealNodes(original.getRoot(), rootNode);
        return new Tree<>(rootNode);
    }

    private <T> Tree<T> calculateDistances(Tree<T> tree, BiFunction<T, T, BigDecimal> distance) {
        Tree.Node<T> rootNode = new Tree.Node<>(tree.getRoot().getContent());
        copyWithDistance(tree.getRoot(), rootNode, distance);
        return new Tree<>(rootNode);
    }

    private <T> void copyWithDistance(Tree.Node<T> copyFrom, Tree.Node<T> copyTo, BiFunction<T, T, BigDecimal> distance) {
        for (Tree.NodeLink<T> link : copyFrom.getLinks()) {
            Tree.Node<T> from = link.getNode();
            Tree.Node<T> node = new Tree.Node<>(from.getContent());
            copyTo.addChild(node, distance.apply(copyFrom.getContent(), from.getContent()));
            copyWithDistance(from, node, distance);
        }
    }

    private void copyRealNodes(Tree.Node<ObservedOrReconstructed<List<Integer>, List<Integer>>> copyFrom,
                               Tree.Node<List<Integer>> copyTo) {
        for (Tree.NodeLink<ObservedOrReconstructed<List<Integer>, List<Integer>>> link : copyFrom.getLinks()) {
            if (Objects.equals(link.getDistance(), BigDecimal.ZERO)) {
                continue;
            }
            Tree.Node<ObservedOrReconstructed<List<Integer>, List<Integer>>> node = link.getNode();

            if (node.getContent() instanceof TreeBuilderByAncestors.Observed<?, ?>) {
                List<Integer> content = ((Observed<List<Integer>, List<Integer>>) node.getContent()).getContent();
                copyTo.addChild(new Tree.Node<>(content));
            } else if (node.getContent() instanceof TreeBuilderByAncestors.Reconstructed<?, ?>) {
                Optional<Observed<List<Integer>, List<Integer>>> realWithDistanceZero = node.getLinks().stream()
                        .filter(it -> Objects.equals(it.getDistance(), BigDecimal.ZERO))
                        .map(it -> (Observed<List<Integer>, List<Integer>>) it.getNode().getContent())
                        .findAny();
                Tree.Node<List<Integer>> nextNode;
                if (realWithDistanceZero.isPresent()) {
                    nextNode = new Tree.Node<>(realWithDistanceZero.get().getContent());
                    copyTo.addChild(nextNode);
                } else {
                    nextNode = copyTo;
                }
                copyRealNodes(node, nextNode);
            } else {
                throw new IllegalArgumentException();
            }
        }
    }

    private List<Integer> parseNode(String node) {
        return node.chars().mapToObj(number -> Integer.valueOf(String.valueOf((char) number))).collect(Collectors.toList());
    }

    private List<Integer> getContent(ObservedOrReconstructed<List<Integer>, List<Integer>> content) {
        return content.convert(Function.identity(), Function.identity());
    }

    private void assertTree(String expected, Tree<ObservedOrReconstructed<List<Integer>, List<Integer>>> tree) {
        assertEquals(expected, treePrinter.print(tree));
    }

    private TreeBuilderByAncestors<List<Integer>, List<Integer>, List<Integer>> treeBuilder(int sizeOfNode) {
        List<Integer> root = IntStream.range(0, sizeOfNode).mapToObj(it -> 0).collect(Collectors.toList());
        return new TreeBuilderByAncestors<>(
                root,
                mutation -> BigDecimal.valueOf(mutation.stream().filter(it -> it != -1).count()),
                (from, to) -> {
                    List<Integer> result = new ArrayList<>();
                    for (int i = 0; i < from.size(); i++) {
                        if (Objects.equals(from.get(i), to.get(i))) {
                            result.add(-1);
                        } else {
                            result.add(to.get(i));
                        }
                    }
                    return result;
                },
                (base, mutation) -> {
                    List<Integer> result = new ArrayList<>();
                    for (int i = 0; i < base.size(); i++) {
                        if (mutation.get(i) == -1) {
                            result.add(base.get(i));
                        } else {
                            result.add(mutation.get(i));
                        }
                    }
                    return result;
                },
                Function.identity(),
                (firstMutation, secondMutation) -> {
                    List<Integer> result = new ArrayList<>();
                    for (int i = 0; i < firstMutation.size(); i++) {
                        if (Objects.equals(firstMutation.get(i), secondMutation.get(i))) {
                            result.add(firstMutation.get(i));
                        } else {
                            result.add(-1);
                        }
                    }
                    return result;
                }
        );
    }

    private BigDecimal distance(List<Integer> first, List<Integer> second) {
        int result = 0;
        for (int i = 0; i < first.size(); i++) {
            if (!Objects.equals(first.get(i), second.get(i))) {
                result++;
            }
        }
        return BigDecimal.valueOf(result);
    }

    private String print(List<Integer> node) {
        return node.stream().map(String::valueOf).collect(Collectors.joining(""));
    }
}
