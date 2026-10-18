package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;

// ORIGIN: Spark
// SCOPE: PARTIAL — only the group-only Aggregate→Project arm, with the <=1-row child instantiated as a unique single-column scan whose column is the group-by key, so every group is a singleton; the sort-removal arms and the distinct-flip arm are not encoded.
public record OptimizeOneRowPlan() implements RRule {
    // The rule's maxRows <= 1 precondition is modeled structurally: the child
    // is a scan whose sole column is a unique key, and the group-by expression
    // is exactly that column, so no group can hold more than one row.
    static final RelRN source = RelRN.scan("Source", RexRN.varType("Source_Type", false), true);
    static final RexRN key = source.field(0);

    @Override
    public RelRN before() {
        // Aggregate(groupingExpressions, no aggregate calls) over the child.
        return new RelRN.Aggregate(source, Seq.of(key), Seq.empty());
    }

    @Override
    public RelRN after() {
        // Project(groupingExpressions, child) — with every group a singleton,
        // the group key per group equals the per-row value of the key column.
        return source.project(Seq.of(key));
    }
}
