package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.apache.calcite.sql.SqlOperator;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;
import org.qed.RelType;
import org.qed.RuleBuilder;

// ORIGIN: CockroachDB
// SCOPE: PARTIAL — the mutation's input has exactly two columns of which only the first is in the needed FetchCols, and the mutation's output is an uninterpreted per-row function of exactly that needed column (its other children — unique checks, fk checks, remaining private data — are held fixed)
public record PruneMutationFetchCols() implements RRule {
    // The mutation's input row: two fetched columns. Column 0 is needed by the
    // mutation's per-row behavior (kept in the pruned FetchCols); column 1 is
    // in FetchCols but never referenced — the column the rule prunes.
    static final RelRN input = RelRN.scanMany("I", Seq.of(
            new RelType.VarType("I0_Type", true),
            new RelType.VarType("I1_Type", true)));

    // Shared uninterpreted symbol M — the Update | Upsert | Delete mutation's
    // per-row behavior, which (by the rule's NeededMutationFetchCols
    // precondition) depends only on the needed fetched column. The mutation's
    // other children (unique checks, fk checks, private data other than
    // FetchCols) are held fixed and absorbed into M.
    static final SqlOperator mOp = RuleBuilder.create()
            .genericProjectionOp("M", new RelType.VarType("M_Type", true));

    // The mutation after the rewrite: its FetchCols set is pruned to just the
    // needed column, so the row it fetches from its input is the pruned row
    // (the only way to model a shrunken FetchCols in the fixed-schema DSL).
    static final RelRN prunedFetch = input.project(input.field(0));

    @Override
    public RelRN before() {
        // Mutation with the unneeded column still in FetchCols: the per-row
        // function M is fed only the needed fetched column of the full row.
        return input.project(new RexRN.Proj(mOp, Seq.of(input.field(0))));
    }

    @Override
    public RelRN after() {
        // Same mutation with FetchCols pruned: M is fed the fetched column of
        // the pruned row.
        return prunedFetch.project(new RexRN.Proj(mOp, Seq.of(prunedFetch.field(0))));
    }
}
