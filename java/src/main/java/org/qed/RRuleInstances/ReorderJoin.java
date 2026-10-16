package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.SqlOperator;
import org.qed.RelRN;
import org.qed.RRule;
import org.qed.RexRN;
import org.qed.RuleBuilder;

// ORIGIN: Spark
// SCOPE: PARTIAL — a single concrete 4-way inner-join reordering (left-deep (A,D,C,B) input re-jointed as ((A x B) x D) x C) with fixed uninterpreted conditions pAB, pCD, qDBC, single-column scans, and the trailing output-order project, rather than the general condition-partitioning / star-schema / CBO logic
public record ReorderJoin() implements RRule {
    static final RelRN A = RelRN.scan("A", "A_Type");
    static final RelRN B = RelRN.scan("B", "B_Type");
    static final RelRN C = RelRN.scan("C", "C_Type");
    static final RelRN D = RelRN.scan("D", "D_Type");

    // Uninterpreted conjunctive join conditions; each symbol is applied in
    // the same argument order on both sides:
    // pAB(A.col, B.col), pCD(D.col, C.col), qDBC(D.col, B.col, C.col).
    static final SqlOperator pAB = RuleBuilder.create().genericPredicateOp("pAB", true);
    static final SqlOperator pCD = RuleBuilder.create().genericPredicateOp("pCD", true);
    static final SqlOperator qDBC = RuleBuilder.create().genericPredicateOp("qDBC", true);

    // before: the original left-deep plan in input order (A, D, C, B).
    // A x D has no evaluable condition (cross join, true literal); pCD is
    // attached where C joins (context (A,D,C): D=1, C=2); pAB and qDBC are
    // attached where B joins (context (A,D,C,B): A=0, D=1, C=2, B=3).
    static final RelRN aD = A.join(JoinRelType.INNER, RexRN.trueLiteral(), D);
    static final RexRN pCD_before = new RexRN.Pred(pCD, Seq.of(aD.joinField(1, C), aD.joinField(2, C)));
    static final RelRN aDC = aD.join(JoinRelType.INNER, pCD_before, C);
    static final RexRN pAB_before = new RexRN.Pred(pAB, Seq.of(aDC.joinField(0, B), aDC.joinField(3, B)));
    static final RexRN qDBC_before = new RexRN.Pred(qDBC, Seq.of(aDC.joinField(1, B), aDC.joinField(3, B), aDC.joinField(2, B)));
    static final RelRN beforeJoin = aDC.join(JoinRelType.INNER, RexRN.and(pAB_before, qDBC_before), B);

    // after: createOrderedJoin picks B right after A (the only item sharing
    // a condition with A), then D (no evaluable condition, cross join),
    // then C carrying the remaining conditions:
    // ((A x_{pAB} B) x_{true} D) x_{pCD and qDBC} C, layout (A,B,D,C).
    static final RexRN pAB_after = new RexRN.Pred(pAB, Seq.of(A.joinField(0, B), A.joinField(1, B)));
    static final RelRN ab = A.join(JoinRelType.INNER, pAB_after, B);
    static final RelRN abd = ab.join(JoinRelType.INNER, RexRN.trueLiteral(), D);
    static final RexRN pCD_after = new RexRN.Pred(pCD, Seq.of(abd.joinField(2, C), abd.joinField(3, C)));
    static final RexRN qDBC_after = new RexRN.Pred(qDBC, Seq.of(abd.joinField(2, C), abd.joinField(1, C), abd.joinField(3, C)));
    static final RelRN afterJoin = abd.join(JoinRelType.INNER, RexRN.and(pCD_after, qDBC_after), C);

    // The reorder changed the column order ((A,B,D,C) vs (A,D,C,B)), so the
    // rule's sameOutput check injects Project(p.output, reordered):
    // fields [0,2,3,1] restore the original (A,D,C,B) ordering.
    static final RelRN afterProject = afterJoin.project(afterJoin.fields(0, 2, 3, 1));

    @Override
    public RelRN before() {
        return beforeJoin;
    }

    @Override
    public RelRN after() {
        return afterProject;
    }
}