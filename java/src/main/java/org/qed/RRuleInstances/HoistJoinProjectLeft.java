package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.SqlOperator;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;
import org.qed.RelType;
import org.qed.RuleBuilder;

// SCOPE: PARTIAL — inner join only; the left input's project is a pure column reordering (swap) of the raw left input and the join condition is a single uninterpreted predicate
public record HoistJoinProjectLeft() implements RRule {
    // Two-column raw left input; single-column raw right input.
    static final RelRN left = RelRN.scanMany("L", Seq.of(
            new RelType.VarType("L0_Type", true),
            new RelType.VarType("L1_Type", true)));
    static final RelRN right = RelRN.scan("R", "R_Type");

    // Shared uninterpreted join condition C over the 3-column join row.
    // Reusing the same name in before() and after() tells QED the
    // occurrences are the same symbol.
    static final SqlOperator cOp = RuleBuilder.create().genericPredicateOp("C", true);

    // Before: Join(C, Project([f1, f0], L), R).
    // The left project is a pure remapping: it reorders L's columns to (L1, L0).
    static final RelRN leftProject =
            left.project(Seq.of(left.field(1), left.field(0)));
    // Join row of leftProject x right: 0 = L1, 1 = L0, 2 = R.
    static final RexRN condBefore = new RexRN.Pred(cOp, Seq.of(
            leftProject.joinField(0, right),
            leftProject.joinField(1, right),
            leftProject.joinField(2, right)));

    // After: Project([L1, L0, R], Join(C', L, R)), where C' unbinds C's
    // references back to the raw input columns: C(L1, L0, R) referenced via
    // the raw join's fields (1, 0, 2).
    // Raw join row: 0 = L0, 1 = L1, 2 = R.
    static final RexRN l1 = left.joinField(1, right);
    static final RexRN l0 = left.joinField(0, right);
    static final RexRN r0 = left.joinField(2, right);
    static final RexRN condAfter = new RexRN.Pred(cOp, Seq.of(l1, l0, r0));

    @Override
    public RelRN before() {
        return leftProject.join(JoinRelType.INNER, condBefore, right);
    }

    @Override
    public RelRN after() {
        return left.join(JoinRelType.INNER, condAfter, right)
                .project(Seq.of(l1, l0, r0));
    }
}
