package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.SqlOperator;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;
import org.qed.RuleBuilder;

// SCOPE: FULL
public record RightAssociateJoinsLeft() implements RRule {
    // A = $outsideLeft (ab), B = $insideLeft (xy), C = $insideRight (uv),
    // each modeled as a single-column base relation.
    static final RelRN A = RelRN.scan("A", "A_Type");
    static final RelRN B = RelRN.scan("B", "B_Type");
    static final RelRN C = RelRN.scan("C", "C_Type");

    // Shared conjuncts of $outsideOn, split exactly as in the source rule:
    // - p_ab: ExtractBoundConditions — IsBoundBy OutputCols2($insideLeft, $outsideLeft),
    //   i.e. referencing only A and B (e.g. the example's a = x). Extracted into the
    //   new inner join (A INNER JOIN B).
    // - p_abc: ExtractUnboundConditions — not IsBoundBy, i.e. referencing C (possibly
    //   also A and B) (e.g. the example's b = u). Stays in the outer join with C.
    static final SqlOperator pabOp = RuleBuilder.create().genericPredicateOp("p_ab", true);
    static final SqlOperator pabcOp = RuleBuilder.create().genericPredicateOp("p_abc", true);

    // ---- before: A INNER JOIN (B INNER JOIN C ON true) ON p_ab(A,B) AND p_abc(A,B,C) ----
    // The inner join's ON is [] (true) in the source rule.
    static final RelRN BC = B.join(JoinRelType.INNER, RexRN.trueLiteral(), C);
    // Row context over A INNER JOIN (B INNER JOIN C): 0=A, 1=B, 2=C.
    static final RexRN beforeCond = RexRN.and(
            new RexRN.Pred(pabOp, Seq.of(A.joinField(0, BC), A.joinField(1, BC))),
            new RexRN.Pred(pabcOp, Seq.of(A.joinField(0, BC), A.joinField(1, BC), A.joinField(2, BC))));
    static final RelRN beforeJoin = A.join(JoinRelType.INNER, beforeCond, BC);

    // ---- after: (A INNER JOIN B ON p_ab(A,B)) INNER JOIN C ON p_abc(A,B,C) ----
    // Row context over A INNER JOIN B: 0=A, 1=B — p_ab is unchanged under reassociation.
    static final RelRN AB = A.join(JoinRelType.INNER,
            new RexRN.Pred(pabOp, Seq.of(A.joinField(0, B), A.joinField(1, B))), B);
    // Row context over (A INNER JOIN B) INNER JOIN C: 0=A, 1=B, 2=C — p_abc is unchanged.
    static final RelRN afterJoin = AB.join(JoinRelType.INNER,
            new RexRN.Pred(pabcOp, Seq.of(AB.joinField(0, C), AB.joinField(1, C), AB.joinField(2, C))), C);

    @Override
    public RelRN before() {
        // ab INNER JOIN (xy INNER JOIN uv ON true) ON (a=x AND b=u); output row (A,B,C).
        return beforeJoin;
    }

    @Override
    public RelRN after() {
        // (ab INNER JOIN xy ON a=x) INNER JOIN uv ON b=u; output row (A,B,C).
        return afterJoin;
    }
}
