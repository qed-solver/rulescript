package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.apache.calcite.sql.SqlOperator;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;
import org.qed.RelType;
import org.qed.RuleBuilder;

// ORIGIN: Spark
// SCOPE: PARTIAL — 2-field struct with exactly three WithField operations in which field a is updated twice and field b once (no DeleteField, case-sensitive names), each value expression uninterpreted and evaluated against the original struct row.
public record OptimizeUpdateFields() implements RRule {
    // structExpr modeled as a 2-field row: col 0 = field a (A_Type), col 1 = field b (B_Type).
    static final RelRN source = RelRN.scanMany("T", Seq.of(
            RexRN.varType("A_Type", true),
            RexRN.varType("B_Type", true)));
    static final RexRN a = source.field(0);
    static final RexRN b = source.field(1);

    // WithField value expressions, uninterpreted. Spark evaluates every
    // WithField(valExpr) against structExpr (the original row a, b), so each
    // E_i is a function of (a, b).
    static final SqlOperator e1Op =
            RuleBuilder.create().genericProjectionOp("E1", new RelType.VarType("A_Type", true));
    static final SqlOperator e2Op =
            RuleBuilder.create().genericProjectionOp("E2", new RelType.VarType("B_Type", true));
    static final SqlOperator e3Op =
            RuleBuilder.create().genericProjectionOp("E3", new RelType.VarType("A_Type", true));
    static final RexRN e1 = new RexRN.Proj(e1Op, Seq.of(a, b)); // value of WF(a, ...) #1
    static final RexRN e2 = new RexRN.Proj(e2Op, Seq.of(a, b)); // value of WF(b, ...)
    static final RexRN e3 = new RexRN.Proj(e3Op, Seq.of(a, b)); // value of WF(a, ...) #2

    @Override
    public RelRN before() {
        // Denotation of UpdateFields((a,b), [WF(a,E1), WF(b,E2), WF(a,E3)]):
        // one projection per WithField replacing that field and passing the
        // others through; cols 0-1 carry the ORIGINAL row so each value
        // expression is evaluated against structExpr, not an intermediate struct.
        // s1: WF(a, E1) -> (a, b, E1(a,b), b)
        RelRN s1 = source.project(Seq.of(a, b, e1, b));
        // s2: WF(b, E2) -> (a, b, E1(a,b), E2(a,b))
        RelRN s2 = s1.project(Seq.of(s1.field(0), s1.field(1), s1.field(2), e2));
        // s3: WF(a, E3) -> (a, b, E3(a,b), E2(a,b))   (E1 is overwritten)
        RelRN s3 = s2.project(Seq.of(s2.field(0), s2.field(1), e3, s2.field(3)));
        // final struct (field order a, b) -> (E3(a,b), E2(a,b))
        return s3.project(Seq.of(s3.field(2), s3.field(3)));
    }

    @Override
    public RelRN after() {
        // Denotation of the deduplicated UpdateFields((a,b), [WF(a,E3), WF(b,E2)])
        // (last value per name wins, first-occurrence field order kept), using
        // the same per-WithField projection denotation with the carry columns.
        // t1: WF(a, E3) -> (a, b, E3(a,b), b)
        RelRN t1 = source.project(Seq.of(a, b, e3, b));
        // t2: WF(b, E2) -> (a, b, E3(a,b), E2(a,b))
        RelRN t2 = t1.project(Seq.of(t1.field(0), t1.field(1), t1.field(2), e2));
        // final struct -> (E3(a,b), E2(a,b))
        return t2.project(Seq.of(t2.field(2), t2.field(3)));
    }
}