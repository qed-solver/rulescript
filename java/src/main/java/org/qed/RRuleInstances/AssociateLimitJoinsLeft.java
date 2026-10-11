package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.SqlOperator;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;
import org.qed.RuleBuilder;

// SCOPE: PARTIAL — the identical Limit/ordering wrapper is omitted and join conditions are uninterpreted predicates over fixed single-column fields
public record AssociateLimitJoinsLeft() implements RRule {
    // Three single-column base relations.
    static final RelRN A = RelRN.scan("A", "A_Type");
    static final RelRN B = RelRN.scan("B", "B_Type");
    static final RelRN C = RelRN.scan("C", "C_Type");

    // Shared uninterpreted join conditions:
    // - p_ab: the LeftJoin ON clause (references A and B only).
    // - p_ac: the InnerJoin ON clause (references A and C only, never B —
    //   this is condition #2 of the CockroachDB rule, which makes the
    //   reordering valid).
    static final SqlOperator pabOp = RuleBuilder.create().genericPredicateOp("p_ab", true);
    static final SqlOperator pacOp = RuleBuilder.create().genericPredicateOp("p_ac", true);

    // ---- before() contexts ----
    // Row context (A,B): p_ab on the columns of A and B.
    static final RexRN pabBefore = new RexRN.Pred(pabOp, Seq.of(A.joinField(0, B), A.joinField(1, B)));
    // AB = A LEFT JOIN B ON p_ab; columns (A=0, B=1).
    static final RelRN AB = A.join(JoinRelType.LEFT, pabBefore, B);
    // Row context (A,B,C): p_ac on A and C (indices 0 and 2) — no B reference.
    static final RexRN pacBefore = new RexRN.Pred(pacOp, Seq.of(AB.joinField(0, C), AB.joinField(2, C)));

    // ---- after() contexts ----
    // Row context (A,C): p_ac on A and C (indices 0 and 1).
    static final RexRN pacAfter = new RexRN.Pred(pacOp, Seq.of(A.joinField(0, C), A.joinField(1, C)));
    // AC = A INNER JOIN C ON p_ac; columns (A=0, C=1).
    static final RelRN AC = A.join(JoinRelType.INNER, pacAfter, C);
    // Row context (A,C,B): p_ab on A and B (indices 0 and 2).
    static final RexRN pabAfter = new RexRN.Pred(pabOp, Seq.of(AC.joinField(0, B), AC.joinField(2, B)));
    // ACB = AC LEFT JOIN B ON p_ab; columns (A=0, C=1, B=2).
    static final RelRN ACB = AC.join(JoinRelType.LEFT, pabAfter, B);

    @Override
    public RelRN before() {
        // (A LEFT JOIN B ON p_ab) INNER JOIN C ON p_ac; columns (A,B,C).
        return AB.join(JoinRelType.INNER, pacBefore, C);
    }

    @Override
    public RelRN after() {
        // (A INNER JOIN C ON p_ac) LEFT JOIN B ON p_ab, reprojected to (A,B,C)
        // so both sides expose the same column order.
        return ACB.project(Seq.of(ACB.field(0), ACB.field(2), ACB.field(1)));
    }
}