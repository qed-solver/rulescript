package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.apache.calcite.sql.SqlOperator;
import org.qed.RelRN;
import org.qed.RelType;
import org.qed.RRule;
import org.qed.RuleBuilder;

// ORIGIN: CockroachDB
// SCOPE: PARTIAL — the With's input is fixed to a single column with exactly one uninterpreted projection expression and one passthrough column; the original rule applies at any arity
public record PruneWithCols() implements RRule {
    // The With's $input, modeled as a single-column scan (the standard stand-in
    // for an arbitrary sub-relation in this DSL).
    static final RelRN input = RelRN.scan("Input", "Input_Type");

    // (With $binding $input $private): CockroachDB's WithOp passes its input
    // through unchanged ("WithOp passes through its input unchanged",
    // prune_cols_funcs.go), so it has no bag-semantic effect on rows and is
    // modeled as the identity projection over its input, exactly as the
    // similarly transparent Barrier was modeled in
    // PushLeakproofProjectionsIntoPermeableBarrier. The binding name and the
    // WithPrivate metadata (reference count, etc.) have no effect on row
    // values, so they are not modeled.
    static final RelRN with = input.project(input.field(0));

    // The single uninterpreted expression standing in for $projections.
    static final SqlOperator exprOp = RuleBuilder.create()
        .genericProjectionOp("Expr", new RelType.VarType("Expr_Type", true));

    @Override
    public RelRN before() {
        // (Project (With $binding $input $private) $projections $passthrough)
        return with.project(Seq.of(with.proj(exprOp), with.field(0)));
    }

    @Override
    public RelRN after() {
        // (With $binding (Project $input $projections $passthrough) $private):
        // the same With (identity projection) over the project pushed below it.
        RelRN below = input.project(Seq.of(input.proj(exprOp), input.field(0)));
        return below.project(below.fields());
    }
}