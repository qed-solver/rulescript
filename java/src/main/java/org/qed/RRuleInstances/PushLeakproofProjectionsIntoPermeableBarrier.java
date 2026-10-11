package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.apache.calcite.sql.SqlOperator;
import org.qed.RelRN;
import org.qed.RelType;
import org.qed.RRule;
import org.qed.RexRN;
import org.qed.RuleBuilder;

// SCOPE: PARTIAL — the barrier's input is fixed to a single column with exactly one (uninterpreted) projection expression and one passthrough column; the original rule applies to any arity
public record PushLeakproofProjectionsIntoPermeableBarrier() implements RRule {
    // $input, modeled as a single-column scan (as in EliminateRedundantBarrier
    // and PushLeakproofFiltersIntoPermeableBarrier).
    static final RelRN input = RelRN.scan("Input", "Input_Type");

    // Barrier: no bag-semantic effect on rows — modeled as the identity
    // projection over its input. The $leakproofPermeable flag (matched via
    // (If $leakproofPermeable) in the source rule) is a flow-control knob
    // with no effect on row values, so it is not modeled.
    static final RelRN barrier = input.project(input.field(0));

    // $projections: one uninterpreted (leakproof) projection expression,
    // shared as the same symbol instance on both sides (as MergeProjects
    // shares its "Top" operator). $passthrough: the single passthrough column.
    static final SqlOperator exprOp = RuleBuilder.create()
            .genericProjectionOp("Expr", new RelType.VarType("Expr_Type", true));
    static final RexRN passthrough = input.field(0);

    @Override
    public RelRN before() {
        // (Project (Barrier $input) $projections $passthrough)
        return barrier.project(Seq.of(barrier.proj(exprOp), barrier.field(0)));
    }

    @Override
    public RelRN after() {
        // (Barrier (Project $input $projections $passthrough))
        // The synthesized expression is the same uninterpreted symbol applied
        // to the same input column, so it composes directly below the barrier;
        // the barrier's identity projection keeps the (synthesized, passthrough)
        // column shape.
        RelRN below = input.project(Seq.of(input.proj(exprOp), passthrough));
        return below.project(below.fields());
    }
}
