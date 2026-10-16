package org.qed.RRuleInstances;

import org.apache.calcite.sql.SqlOperator;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RelType;
import org.qed.RexRN;
import org.qed.RuleBuilder;
import kala.collection.Seq;

// ORIGIN: Spark
// SCOPE: PARTIAL — encodes only the unary form U(If(c, t, f)) ⟹ If(c, U(t), U(f)); Spark's CaseWhen and binary-expression-with-foldable-operand variants are omitted
public record PushFoldableIntoBranches() implements RRule {
    static final RelType.VarType val = new RelType.VarType("VAL", true);
    static final RelType.VarType valOut = new RelType.VarType("VAL_OUT", true);

    // 3-operand operator named "CASE" so QED deserializes the node as a genuine
    // CASE (ite); the local object only carries the name and result type.
    static final SqlOperator caseOp = new RuleBuilder.QedFunction("CASE", valOut);

    static final RelRN source = RelRN.scanMany("Source", Seq.of(val, val));
    static final RexRN cond = source.pred("cond");
    static final RexRN t = source.field(0);
    static final RexRN f = source.field(1);

    static final RexRN ite = new RexRN.Proj(caseOp, Seq.of(cond, t, f));

    @Override
    public RelRN before() {
        return source.project(ite.proj("U", "VAL_OUT"));
    }

    @Override
    public RelRN after() {
        RexRN iteU = new RexRN.Proj(caseOp, Seq.of(cond, t.proj("U", "VAL_OUT"), f.proj("U", "VAL_OUT")));
        return source.project(iteU);
    }
}
