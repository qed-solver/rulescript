package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.SqlOperator;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;
import org.qed.RuleBuilder;

// SCOPE: FULL
public record LeftAssociateJoinsRight() implements RRule {
    // xy, uv, ab as three single-column base relations:
    // A = $insideLeft (xy), B = $insideRight (uv), C = $outsideRight (ab).
    static final RelRN A = RelRN.scan("A", "A_Type");
    static final RelRN B = RelRN.scan("B", "B_Type");
    static final RelRN C = RelRN.scan("C", "C_Type");

    // Shared uninterpreted conjuncts of $outsideOn:
    // - p_ax: the conjunct NOT IsBoundBy OutputCols2($insideRight, $outsideRight),
    //   i.e. it may reference any of the three relations over the full (A,B,C)
    //   join-row context — e.g. the example's a = x, where a is a column of C (ab)
    //   and x a column of A (xy). It stays in the outer join's condition in both
    //   before and after (ExtractUnboundConditions).
    // - p_bu: the conjunct IsBoundBy OutputCols2($insideRight, $outsideRight),
    //   i.e. referencing only B and C — e.g. the example's b = u, where b is a
    //   column of C (ab) and u a column of B (uv). It is extracted into the new
    //   inner join (ExtractBoundConditions).
    static final SqlOperator paxOp = RuleBuilder.create().genericPredicateOp("p_ax", true);
    static final SqlOperator pbuOp = RuleBuilder.create().genericPredicateOp("p_bu", true);

    // ---- before: (A INNER JOIN B ON true) INNER JOIN C ON p_ax(A,B,C) AND p_bu(B,C) ----
    // The inner join's ON is [] (true) in the source rule.
    static final RelRN AB = A.join(JoinRelType.INNER, RexRN.trueLiteral(), B);
    // Row context over AB ⋈ C: 0=A, 1=B, 2=C.
    static final RexRN beforeCond = RexRN.and(
            new RexRN.Pred(paxOp, Seq.of(AB.joinField(0, C), AB.joinField(1, C), AB.joinField(2, C))),
            new RexRN.Pred(pbuOp, Seq.of(AB.joinField(1, C), AB.joinField(2, C))));
    static final RelRN beforeJoin = AB.join(JoinRelType.INNER, beforeCond, C);

    // ---- after: A INNER JOIN (B INNER JOIN C ON p_bu(B,C)) ON p_ax(A,B,C) ----
    // Row context over B ⋈ C: 0=B, 1=C — p_bu is unchanged under reassociation.
    static final RelRN BC = B.join(JoinRelType.INNER,
            new RexRN.Pred(pbuOp, Seq.of(B.joinField(0, C), B.joinField(1, C))), C);
    // Row context over A ⋈ (B ⋈ C): 0=A, 1=B, 2=C — p_ax is unchanged under reassociation.
    static final RelRN afterJoin = A.join(JoinRelType.INNER,
            new RexRN.Pred(paxOp, Seq.of(A.joinField(0, BC), A.joinField(1, BC), A.joinField(2, BC))), BC);

    @Override
    public RelRN before() {
        // (xy INNER JOIN uv ON true) INNER JOIN ab ON (a=x AND b=u); output row (A,B,C).
        return beforeJoin;
    }

    @Override
    public RelRN after() {
        // xy INNER JOIN (uv INNER JOIN ab ON b=u) ON a=x; output row (A,B,C).
        return afterJoin;
    }
}
