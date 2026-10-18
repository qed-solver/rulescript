package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.SqlOperator;
import org.qed.RelRN;
import org.qed.RRule;
import org.qed.RexRN;
import org.qed.RuleBuilder;

// ORIGIN: Spark
// SCOPE: PARTIAL — LeftSemi only (not LeftAnti), TO_LEFT_BRANCH with a non-empty semi condition whose conjuncts reference only the child join's left leg and the semi-join right input, and an INNER child join, with single-column scans
public record PushLeftSemiLeftAntiThroughJoin() implements RRule {
    // X = left leg of the child join, Y = right leg of the child join,
    // Z = right input of the LeftSemi join.
    static final RelRN X = RelRN.scan("X", "Key_Type");
    static final RelRN Y = RelRN.scan("Y", "Key_Type");
    static final RelRN Z = RelRN.scan("Z", "Key_Type");

    // Shared uninterpreted predicate symbols; reusing the same operator
    // (hence name) in before() and after() ties the occurrences for QED,
    // mirroring Spark's reuse of the same Expression for joinCond/childCondition.
    static final SqlOperator cOp = RuleBuilder.create().genericPredicateOp("C", true);
    static final SqlOperator sOp = RuleBuilder.create().genericPredicateOp("S", true);

    // Child inner join J = X join_C Y, row layout (X, Y): flat 0 = X, flat 1 = Y.
    static final RexRN C = new RexRN.Pred(cOp,
            Seq.of(X.joinField(0, Y), X.joinField(1, Y)));
    static final RelRN J = X.join(JoinRelType.INNER, C, Y);

    // Semi-join condition S over (X, Z): in the top join's (X, Y, Z) row,
    // X is flat 0 and Z is flat 2 (J has 2 columns, so flat 2 binds to Z's
    // column 0). No Y reference, matching Spark's TO_LEFT_BRANCH
    // precondition that every conjunct references only
    // childLeft.output ++ semiRight.output.
    static final RexRN S = new RexRN.Pred(sOp,
            Seq.of(J.joinField(0, Z), J.joinField(2, Z)));

    @Override
    public RelRN before() {
        // Join(Join(X, Y, INNER, C), Z, LeftSemi, S): the semi-join emits
        // only the child join's fields (X, Y).
        return J.join(JoinRelType.SEMI, S, Z);
    }

    @Override
    public RelRN after() {
        // newLeft = Join(X, Z, LeftSemi, S) with S re-referenced onto the
        // (X, Z) row (flat 0 = X, flat 1 = Z); emits only X's field.
        RelRN xz = X.join(JoinRelType.SEMI,
                new RexRN.Pred(sOp, Seq.of(X.joinField(0, Z), X.joinField(1, Z))), Z);
        // Join(newLeft, Y, INNER, C) with C re-referenced onto the (X, Y)
        // row (flat 0 = X, flat 1 = Y), keeping the original child join type
        // and condition exactly as Spark does.
        return xz.join(JoinRelType.INNER,
                new RexRN.Pred(cOp, Seq.of(xz.joinField(0, Y), xz.joinField(1, Y))), Y);
    }
}
