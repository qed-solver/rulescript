package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.apache.calcite.sql.SqlOperator;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;
import org.qed.RuleBuilder;

// ORIGIN: Spark
// SCOPE: PARTIAL — single-column input and one scalar deterministic external UDF f applied to that column, extracted out of a Project operator into a dedicated lower UDF-evaluation projection that evaluates f once and passes the input column through (the upper operator keeps a non-UDF expression g).
public record PlanExternalUDFs() implements RRule {
    static final RelRN source = RelRN.scan("R", "R_Type");

    // The external scalar UDF, modeled as an uninterpreted deterministic projection operator "f".
    static final SqlOperator fOp = RuleBuilder.create().genericProjectionOp("f", RexRN.varType("f_type", true));
    // A normal (JVM-evaluable) expression kept in the operator, modeled as uninterpreted operator "g".
    static final SqlOperator gOp = RuleBuilder.create().genericProjectionOp("g", RexRN.varType("g_type", true));

    static final RexRN fOfX = new RexRN.Proj(fOp, Seq.of(source.field(0)));
    static final RexRN gOfX = new RexRN.Proj(gOp, Seq.of(source.field(0)));

    // Before extraction: the operator computes both f(x) (the external UDF) and g(x).
    static final RelRN before = source.project(Seq.of(fOfX, gOfX));

    // The dedicated UDF extraction node: evaluates the UDF f once, exposes it as a fresh
    // column, and passes through the input column x for the operator's remaining expressions.
    static final RelRN evalNode = source.project(Seq.of(fOfX, source.field(0)));

    // After extraction: the operator references the produced UDF column instead of recomputing f,
    // and keeps its g expression over the input column coming from the extraction node.
    static final RexRN gOfPass = new RexRN.Proj(gOp, Seq.of(evalNode.field(1)));
    static final RelRN after = evalNode.project(Seq.of(evalNode.field(0), gOfPass));

    @Override
    public RelRN before() { return before; }

    @Override
    public RelRN after() { return after; }
}
