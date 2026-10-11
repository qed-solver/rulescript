package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.apache.calcite.sql.SqlOperator;
import org.qed.RelRN;
import org.qed.RelType;
import org.qed.RRule;
import org.qed.RexRN;
import org.qed.RuleBuilder;

// SCOPE: PARTIAL — fixed three-column scan input whose Select filter and outer projection reference only columns 0 and 1, pruning exactly the never-referenced third column.
public record PruneSelectCols() implements RRule {
    // Select input: three columns. Columns 0 and 1 are needed (used by the
    // filter and by the outer projection); column 2 is never referenced and
    // is the column the rule prunes.
    static final RelRN source = RelRN.scanMany("Source", Seq.of(
            new RelType.VarType("S0_Type", true),
            new RelType.VarType("S1_Type", true),
            new RelType.VarType("S2_Type", true)));

    // (PruneCols $input $needed): the scan rewritten to output only the
    // needed columns (0, 1).
    static final RelRN pruned =
            source.project(Seq.from(new RexRN[]{source.field(0), source.field(1)}));

    // Shared uninterpreted filter symbol F, written only over the two needed
    // columns so the same symbol applies at both arities-free shapes.
    static final SqlOperator fOp = RuleBuilder.create().genericPredicateOp("F", true);
    // Shared uninterpreted outer projection symbol Top over the needed columns.
    static final SqlOperator top = RuleBuilder.create()
            .genericProjectionOp("Top", new RelType.VarType("Top_Type", true));

    @Override
    public RelRN before() {
        // Project(Top(f0, f1)) over Select(F(f0, f1), full three-column scan).
        RelRN filt = source.filter(new RexRN.Pred(fOp, Seq.from(new RexRN[]{
                source.field(0), source.field(1)})));
        return filt.project(new RexRN.Proj(top, Seq.from(new RexRN[]{
                filt.field(0), filt.field(1)})));
    }

    @Override
    public RelRN after() {
        // Project(Top(p0, p1)) over Select(F(p0, p1), scan pruned to columns 0, 1).
        RelRN filt = pruned.filter(new RexRN.Pred(fOp, Seq.from(new RexRN[]{
                pruned.field(0), pruned.field(1)})));
        return filt.project(new RexRN.Proj(top, Seq.from(new RexRN[]{
                filt.field(0), filt.field(1)})));
    }
}