package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.apache.calcite.sql.SqlOperator;
import org.qed.RelRN;
import org.qed.RelType;
import org.qed.RRule;
import org.qed.RexRN;
import org.qed.RuleBuilder;

// SCOPE: PARTIAL — fixed three-column WithScan whose outer projection references only columns 0 and 1 through one uninterpreted synthesized column, pruning exactly the never-referenced third column
public record PruneWithScanCols() implements RRule {
    // WithScan input: three columns. Columns 0 and 1 are referenced by the
    // outer projection (the $needed set); column 2 is never referenced and is
    // the column the rule prunes from the WithScan's InCols/OutCols.
    static final RelRN source = RelRN.scanMany("WithScan", Seq.of(
            new RelType.VarType("W0_Type", true),
            new RelType.VarType("W1_Type", true),
            new RelType.VarType("W2_Type", true)));

    // The WithScan rewritten to output only the needed columns (0, 1) — the
    // source rule's (PruneCols $input $needed), i.e. the new WithScan whose
    // InCols/OutCols keep only the needed pairs.
    static final RelRN pruned =
            source.project(Seq.from(new RexRN[]{source.field(0), source.field(1)}));

    // Shared uninterpreted symbol: Top — the outer projection over the scan.
    static final SqlOperator top = RuleBuilder.create()
            .genericProjectionOp("Top", new RelType.VarType("Top_Type", true));

    @Override
    public RelRN before() {
        // Project(Top(w0, w1)) over the full three-column WithScan; w2 unused.
        return source.project(new RexRN.Proj(top, Seq.from(new RexRN[]{
                source.field(0), source.field(1)})));
    }

    @Override
    public RelRN after() {
        // Project(Top(w0, w1)) over the WithScan pruned to just the needed
        // columns; field indices refer to the pruned row (0 = w0, 1 = w1).
        return pruned.project(new RexRN.Proj(top, Seq.from(new RexRN[]{
                pruned.field(0), pruned.field(1)})));
    }
}
