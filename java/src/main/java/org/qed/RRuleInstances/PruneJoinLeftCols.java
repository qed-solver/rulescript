package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.SqlOperator;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;
import org.qed.RelType;
import org.qed.RuleBuilder;

// SCOPE: PARTIAL — inner join whose left input has one key column (referenced by the on-clause and the outer projection) and one extra column that is pruned, with the on-clause and outer projection uninterpreted over the retained (key, right) columns
public record PruneJoinLeftCols() implements RRule {
    // Left input: two columns — col 0 is the join key referenced by the
    // condition/projection, col 1 is the column the rule prunes away.
    static final RelRN left = RelRN.scanMany("L", Seq.of(
            new RelType.VarType("L0_Type", true),
            new RelType.VarType("L1_Type", true)));
    // Right input: one column.
    static final RelRN right = RelRN.scan("R", "R_Type");

    // Shared uninterpreted symbols, reused in before() and after():
    // C — the join on-clause, over (L0, R0);
    // G — the outer projection, over (L0, R0).
    static final SqlOperator cOp = RuleBuilder.create().genericPredicateOp("C", true);
    static final SqlOperator gOp = RuleBuilder.create().genericProjectionOp("G", new RelType.VarType("G_Type", true));

    // Before: Project(G(l0, r0)) over Join(C(l0, r0), L, R).
    // Join row: 0 = L0, 1 = L1, 2 = R0.
    static final RexRN l0Before = left.joinField(0, right);
    static final RexRN r0Before = left.joinField(2, right);
    static final RexRN condBefore = new RexRN.Pred(cOp, Seq.of(l0Before, r0Before));
    static final RelRN joinBefore = left.join(JoinRelType.INNER, condBefore, right);
    static final RexRN topBefore = new RexRN.Proj(gOp, Seq.of(
            joinBefore.field(0), joinBefore.field(2)));

    // After: the same join with the left side projected down to just the key
    // column first, then the same outer projection.
    static final RelRN leftPruned = left.project(left.field(0));
    static final RexRN l0After = leftPruned.joinField(0, right);
    static final RexRN r0After = leftPruned.joinField(1, right);
    static final RexRN condAfter = new RexRN.Pred(cOp, Seq.of(l0After, r0After));
    static final RelRN joinAfter = leftPruned.join(JoinRelType.INNER, condAfter, right);
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
