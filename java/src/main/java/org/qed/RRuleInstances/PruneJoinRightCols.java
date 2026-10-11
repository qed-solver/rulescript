package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.SqlOperator;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;
import org.qed.RelType;
import org.qed.RuleBuilder;

// SCOPE: PARTIAL — inner join whose right input has one key column (referenced by the on-clause and the outer projection) and one extra column that is pruned, with the on-clause and outer projection uninterpreted over the retained (left, key) columns
public record PruneJoinRightCols() implements RRule {
    // Left input: one column.
    static final RelRN left = RelRN.scan("L", "L_Type");
    // Right input: two columns — col 0 is the join key referenced by the
    // condition/projection, col 1 is the column the rule prunes away.
    static final RelRN right = RelRN.scanMany("R", Seq.of(
            new RelType.VarType("R0_Type", true),
            new RelType.VarType("R1_Type", true)));

    // Shared uninterpreted symbols, reused in before() and after():
    // C — the join on-clause, over (L0, R0);
    // G — the outer projection, over (L0, R0).
    static final SqlOperator cOp = RuleBuilder.create().genericPredicateOp("C", true);
    static final SqlOperator gOp = RuleBuilder.create().genericProjectionOp("G", new RelType.VarType("G_Type", true));

    // Before: Project(G(l0, r0)) over Join(C(l0, r0), L, R).
    // Join row: 0 = L0, 1 = R0, 2 = R1.
    static final RexRN l0Before = right.joinField(0, left);
    static final RexRN r0Before = right.joinField(1, left);
    static final RexRN condBefore = new RexRN.Pred(cOp, Seq.of(l0Before, r0Before));
    static final RelRN joinBefore = left.join(JoinRelType.INNER, condBefore, right);
    static final RexRN topBefore = new RexRN.Proj(gOp, Seq.of(
            joinBefore.field(0), joinBefore.field(1)));

    // After: the same join with the right side projected down to just the key
    // column first, then the same outer projection.
    static final RelRN rightPruned = right.project(right.field(0));
    static final RexRN l0After = rightPruned.joinField(0, left);
    static final RexRN r0After = rightPruned.joinField(1, left);
    static final RexRN condAfter = new RexRN.Pred(cOp, Seq.of(l0After, r0After));
    static final RelRN joinAfter = left.join(JoinRelType.INNER, condAfter, rightPruned);
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