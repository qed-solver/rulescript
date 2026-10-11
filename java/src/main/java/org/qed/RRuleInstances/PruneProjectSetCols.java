package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.SqlOperator;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;
import org.qed.RelType;
import org.qed.RuleBuilder;

// SCOPE: PARTIAL — the ProjectSet input has one used column and one unused column that is pruned, the zip emits a single column of rows, and the emitted rows depend on the input only through an uninterpreted membership predicate that does not reference the pruned column
public record PruneProjectSetCols() implements RRule {
    // Inner input of the ProjectSet: two columns — col 0 is referenced by the
    // zip and the outer projection (hence needed); col 1 is never referenced
    // and is the column this rule prunes away.
    static final RelRN inner = RelRN.scanMany("L", Seq.of(
            new RelType.VarType("L0_Type", true),
            new RelType.VarType("L1_Type", true)));

    // E: the rows emitted by the ProjectSet zip for one input row.
    // ProjectSet's row-producing semantics are uninterpretable to QED, so
    // ProjectSet($innerInput, $zip) is abstracted as an INNER join of the
    // input with E under an uninterpreted membership predicate
    // M(input column, emitted row); the identity is then proven uniformly
    // for every M.
    static final RelRN emitted = RelRN.scan("E", "E_Type");

    // Shared uninterpreted symbols, reused in before() and after():
    // M — the zip's row-emission (membership) predicate over (L0, E0); by
    //     the rule's CanPruneCols side condition (ZipOuterCols ∪
    //     ProjectionOuterCols ∪ passthrough ⊆ needed) it does not mention
    //     the pruned column L1.
    // G — the outer projection, over the ProjectSet output columns (L0, E0).
    static final SqlOperator mOp = RuleBuilder.create().genericPredicateOp("M", true);
    static final SqlOperator gOp = RuleBuilder.create()
            .genericProjectionOp("G", new RelType.VarType("G_Type", true));

    // Before: Project(G(l0, e0)) over ProjectSet(L, zip)
    //        ≈ Join(M(l0, e0), L, E).
    // Join row: 0 = L0, 1 = L1, 2 = E0.
    static final RexRN condBefore = new RexRN.Pred(mOp, Seq.of(
            inner.joinField(0, emitted), inner.joinField(2, emitted)));
    static final RelRN joinBefore = inner.join(JoinRelType.INNER, condBefore, emitted);
    static final RexRN topBefore = new RexRN.Proj(gOp, Seq.of(
            joinBefore.field(0), joinBefore.field(2)));

    // After: the same join with the inner input projected down to just col 0
    // (PruneCols $innerInput $needed) first, then the same outer projection.
    // Join row: 0 = L0, 1 = E0.
    static final RelRN innerPruned = inner.project(inner.field(0));
    static final RexRN condAfter = new RexRN.Pred(mOp, Seq.of(
            innerPruned.joinField(0, emitted), innerPruned.joinField(1, emitted)));
    static final RelRN joinAfter = innerPruned.join(JoinRelType.INNER, condAfter, emitted);
    static final RexRN topAfter = new RexRN.Proj(gOp, Seq.of(
            joinAfter.field(0), joinAfter.field(1)));

    @Override
    public RelRN before() {
        return joinBefore.project(topBefore);
    }

    @Override
    public RelRN after() {
        return joinAfter.project(topAfter);
    }
}
