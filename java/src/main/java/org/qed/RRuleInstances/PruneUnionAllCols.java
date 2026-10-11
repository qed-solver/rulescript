package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.apache.calcite.sql.SqlOperator;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;
import org.qed.RelType;
import org.qed.RuleBuilder;

// SCOPE: PARTIAL — both UnionAll inputs share the same three-column schema (types C0, C1, C2) with exactly columns 0 and 2 needed, and the outer projection is one uninterpreted projection over column 0 plus a single passthrough of column 2.
public record PruneUnionAllCols() implements RRule {
    // Both UnionAll inputs share one three-column schema. Column 1 (C1) is
    // never referenced by the outer projection: it is the column this rule
    // prunes. Column 0 is used by the outer projection G; column 2 is
    // passed through directly.
    static final RelType.VarType c0 = new RelType.VarType("C0_Type", true);
    static final RelType.VarType c1 = new RelType.VarType("C1_Type", true);
    static final RelType.VarType c2 = new RelType.VarType("C2_Type", true);

    static final RelRN left = RelRN.scanMany("L", Seq.of(c0, c1, c2));
    static final RelRN right = RelRN.scanMany("R", Seq.of(c0, c1, c2));

    // G — the outer (uninterpreted) projection over the UnionAll output;
    // here it consumes column 0 only, so column 1 is genuinely unneeded.
    static final SqlOperator gOp = RuleBuilder.create()
            .genericProjectionOp("G", new RelType.VarType("G_Type", true));

    // Before: Project(G(c0), c2) over UnionAll(L, R).
    static final RelRN unionBefore = left.union(true, right);
    static final RexRN topBefore = new RexRN.Proj(gOp, Seq.of(
            unionBefore.field(0), unionBefore.field(2)));

    // After: push a Project down into each UnionAll input selecting exactly
    // the needed columns (0, 2), then apply the same outer projection.
    static final RelRN leftPruned = left.project(Seq.of(
            left.field(0), left.field(2)));
    static final RelRN rightPruned = right.project(Seq.of(
            right.field(0), right.field(2)));
    static final RelRN unionAfter = leftPruned.union(true, rightPruned);
    static final RexRN topAfter = new RexRN.Proj(gOp, Seq.of(
            unionAfter.field(0), unionAfter.field(1)));

    @Override
    public RelRN before() {
        return unionBefore.project(topBefore);
    }

    @Override
    public RelRN after() {
        return unionAfter.project(topAfter);
    }
}
