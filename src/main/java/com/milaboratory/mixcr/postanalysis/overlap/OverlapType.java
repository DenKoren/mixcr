package com.milaboratory.mixcr.postanalysis.overlap;

import java.util.Arrays;

/**
 *
 */
public enum OverlapType {
    D("Diversity", "Relative overlap diversity normalized"),
    SharedClonotypes("Clonotype share", "Number of shared clonotypes"),
    F1("Frequencies", "Geometric mean of relative overlap frequencies"),
    F2("Drequencies (clonotype-wise)", "Сlonotype-wise sum of geometric mean frequencies"),
    Jaccard("Jaccard", "Jaccard overlap"),
    R_Intersection("Pearson", "Pearson correlation of clonotype frequencies, restricted only to the overlapping clonotypes"),
    R_All("Pearson (all)", "Pearson correlation of clonotype frequencies (outer merge)");

    public final String name;
    public final String description;

    OverlapType(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public static OverlapType byName(String name) {
        return Arrays.stream(values()).filter(s -> s.name.equals(name)).findFirst().orElse(null);
    }
}
