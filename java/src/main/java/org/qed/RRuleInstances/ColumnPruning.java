package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;
import org.qed.RuleBuilder;

// ORIGIN: Spark
// SCOPE: PARTIAL — Only the Project-over-Project case is covered: the inner Project's output is pruned to exactly the columns the outer Project references (here columns 0 and 2 of 4); Spark's other pruning cases (Aggregate/Expand/Union/Window/LeftExistenceJoin/etc.) are not encoded.
public record ColumnPruning() implements RRule {
    // Source relation with 3 columns
    static final RelRN R = RelRN.scanMany("R", Seq.of(
            RexRN.varType("R_type_0", true),
            RexRN.varType("R_type_1", true),
            RexRN.varType("R_type_2", true)));

    // Four derived columns produced by the inner project, each an uninterpreted expression over R
    static final Seq<RexRN> innerCols = Seq.of(
            R.proj("p2_c0", "C_type"),
            R.proj("p2_c1", "C_type"),
            R.proj("p2_c2", "C_type"),
            R.proj("p2_c3", "C_type"));

    @Override
    public RelRN before() {
        // Inner project outputs all 4 columns; outer project references only col 0 and col 2
        RelRN inner = R.project(innerCols);
        RexRN outer0 = new RexRN.Proj(RuleBuilder.create().genericProjectionOp("g0", RexRN.varType("C_type", true)), Seq.of(inner.field(0)));
        RexRN outer1 = new RexRN.Proj(RuleBuilder.create().genericProjectionOp("g1", RexRN.varType("C_type", true)), Seq.of(inner.field(2)));
        return inner.project(Seq.of(outer0, outer1));
    }

    @Override
    public RelRN after() {
        // Prune the inner project to the referenced columns (original positions 0 and 2, order preserved)
        RelRN prunedInner = R.project(Seq.of(innerCols.get(0), innerCols.get(2)));
        RexRN outer0 = new RexRN.Proj(RuleBuilder.create().genericProjectionOp("g0", RexRN.varType("C_type", true)), Seq.of(prunedInner.field(0)));
        RexRN outer1 = new RexRN.Proj(RuleBuilder.create().genericProjectionOp("g1", RexRN.varType("C_type", true)), Seq.of(prunedInner.field(1)));
        return prunedInner.project(Seq.of(outer0, outer1));
    }
}
