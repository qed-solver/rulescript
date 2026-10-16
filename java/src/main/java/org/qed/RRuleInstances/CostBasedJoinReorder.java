package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.SqlOperator;
import org.qed.RelRN;
import org.qed.RRule;
import org.qed.RexRN;
import org.qed.RuleBuilder;

// ORIGIN: Spark
// SCOPE: PARTIAL — a single concrete 4-way inner-join reordering instance (left-deep ((A⋈B)⋈C)⋈D to bushy (A⋈B)⋈(C⋈D)) with fixed pairwise uninterpreted conditions and single-column inputs, rather than the DP cost model, condition placement, and output-order handling
public record CostBasedJoinReorder() implements RRule {
    static final RelRN A = RelRN.scan("A", "A_Type");
    static final RelRN B = RelRN.scan("B", "B_Type");
    static final RelRN C = RelRN.scan("C", "C_Type");
    static final RelRN D = RelRN.scan("D", "D_Type");

    // Shared uninterpreted 2-argument predicate symbols for the conjunctive
    // join conditions; each occurrence applies its symbol in the SAME argument
    // order (first item's column, second item's column) on both sides.
    static final SqlOperator pAB = RuleBuilder.create().genericPredicateOp("pAB", true);
    static final SqlOperator pAC = RuleBuilder.create().genericPredicateOp("pAC", true);
    static final SqlOperator pBC = RuleBuilder.create().genericPredicateOp("pBC", true);
    static final SqlOperator pCD = RuleBuilder.create().genericPredicateOp("pCD", true);

    // before: left-deep 4-way join ((A ⋈_{cAB} B) ⋈_{cAC ∧ cBC} C) ⋈_{cCD} D,
    // with each condition attached at the first join node where both of its
    // referenced items are available (the DP's buildJoin placement).
    static final RexRN abCond = new RexRN.Pred(pAB, Seq.of(A.joinField(0, B), A.joinField(1, B)));
    static final RelRN ab = A.join(JoinRelType.INNER, abCond, B);

    // ab has layout (A, B): joinField(0)=A, joinField(1)=B, joinField(2)=C.
    static final RexRN abcCond = RexRN.and(
            new RexRN.Pred(pAC, Seq.of(ab.joinField(0, C), ab.joinField(2, C))),
            new RexRN.Pred(pBC, Seq.of(ab.joinField(1, C), ab.joinField(2, C))));
    static final RelRN abc = ab.join(JoinRelType.INNER, abcCond, C);

    // abc has layout (A, B, C): joinField(2)=C, joinField(3)=D.
    static final RexRN dCond = new RexRN.Pred(pCD, Seq.of(abc.joinField(2, D), abc.joinField(3, D)));
    static final RelRN beforeJoin = abc.join(JoinRelType.INNER, dCond, D);

    // after: bushy 4-way join (A ⋈_{cAB} B) ⋈_{cAC ∧ cBC} (C ⋈_{cCD} D), the
    // reordering the DP search can pick when it has lower cost.
    static final RexRN cdCond = new RexRN.Pred(pCD, Seq.of(C.joinField(0, D), C.joinField(1, D)));
    static final RelRN cd = C.join(JoinRelType.INNER, cdCond, D);

    // ab has layout (A, B), cd has layout (C, D): in the root join
    // joinField(0)=A, joinField(1)=B, joinField(2)=C, joinField(3)=D — the
    // same (A, B, C, D) output layout as the before side, so no trailing
    // output-order project is needed, and pAC/pBC keep their argument order.
    static final RexRN afterCond = RexRN.and(
            new RexRN.Pred(pAC, Seq.of(ab.joinField(0, cd), ab.joinField(2, cd))),
            new RexRN.Pred(pBC, Seq.of(ab.joinField(1, cd), ab.joinField(2, cd))));
    static final RelRN afterJoin = ab.join(JoinRelType.INNER, afterCond, cd);

    @Override
    public RelRN before() {
        return beforeJoin;
    }

    @Override
    public RelRN after() {
        return afterJoin;
    }
}