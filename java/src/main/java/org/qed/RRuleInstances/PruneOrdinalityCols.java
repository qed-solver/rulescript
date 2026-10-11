package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.apache.calcite.sql.SqlOperator;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RelType;
import org.qed.RexRN;
import org.qed.RuleBuilder;

// SCOPE: PARTIAL — the ordinality-generated column is modeled as one uninterpreted function O of the retained input columns, shared by both sides and consumed by the outer projection, so row-position/ordering semantics are not captured
public record PruneOrdinalityCols() implements RRule {
    // Ordinality input: 3 columns — c0/c1 are needed (referenced by the outer
    // projection), c2 is referenced by neither the projection nor the
    // ordinality ordering (so it is not in the needed columns) and is the
    // column the rule prunes.
    static final RelRN input = RelRN.scanMany("IN", Seq.of(
            new RelType.VarType("C0_Type", true),
            new RelType.VarType("C1_Type", true),
            new RelType.VarType("C2_Type", true)));

    // O — the ordinality-generated column, ONE shared uninterpreted symbol on
    // both sides: the same logical quantity re-derived after pruning. It is a
    // function of only the retained columns c0/c1, encoding the rule's premise
    // that the ordinality ordering references no pruned column (such a column
    // would land in NeededOrdinalityCols and could not be pruned).
    static final SqlOperator oOp = RuleBuilder.create().genericProjectionOp("O", new RelType.VarType("O_Type", true));

    // G — the outer projection, shared across both sides: the rewrite leaves it
    // unchanged. It consumes the ordinality column, so the Ordinality layer is
    // not a no-op in this encoding: any change to O's value changes G's output.
    static final SqlOperator gOp = RuleBuilder.create().genericProjectionOp("G", new RelType.VarType("G_Type", true));

    @Override
    public RelRN before() {
        // Ordinality over the full 3-column input: row = (c0, c1, c2, O(c0,c1)).
        RelRN ord = input.project(Seq.of(
                input.field(0), input.field(1), input.field(2),
                new RexRN.Proj(oOp, Seq.of(input.field(0), input.field(1)))));
        // Outer project keeps the needed input columns c0/c1 and the
        // ordinality column O, skipping the pruned column c2.
        return ord.project(new RexRN.Proj(gOp, Seq.of(
                ord.field(0), ord.field(1), ord.field(3))));
    }

    @Override
    public RelRN after() {
        // (PruneCols $input $needed): keep only the needed columns c0, c1.
        RelRN pruned = input.project(Seq.of(input.field(0), input.field(1)));
        // Ordinality over the pruned input (PruneOrderingOrdinality):
        // row = (c0, c1, O(c0,c1)) — the same O re-derived from the pruned row.
        RelRN ord = pruned.project(Seq.of(
                pruned.field(0), pruned.field(1),
                new RexRN.Proj(oOp, Seq.of(pruned.field(0), pruned.field(1)))));
        // Same outer projection G over the needed input columns and the
        // ordinality column.
        return ord.project(new RexRN.Proj(gOp, Seq.of(
                ord.field(0), ord.field(1), ord.field(2))));
    }
}