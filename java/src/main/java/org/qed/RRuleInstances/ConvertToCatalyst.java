package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.apache.calcite.sql.SqlOperator;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;
import org.qed.RelType;
import org.qed.RuleBuilder;

// ORIGIN: Spark
// SCOPE: PARTIAL — one shared subexpression e read twice by a single predicate F under a Filter; the widened column is projected back to keep the output width
public record ConvertToCatalyst() implements RRule {
    // The operator's child: one column x.
    static final RelRN source = RelRN.scan("S", "S_Type");
    static final RexRN x = source.field(0);

    // The argument the UDF body reads more than once: uninterpreted e(x),
    // the Alias(_udf_param_N) that convertOperator appends below the operator.
    static final SqlOperator eOp =
            RuleBuilder.create().genericProjectionOp("E", new RelType.VarType("E_Type", true));
    static final RexRN e = new RexRN.Proj(eOp, Seq.of(x));

    // The operator's predicate: an uninterpreted boolean expression F
    // (the transpiled Python UDF call) taking the shared argument twice.
    static final SqlOperator fOp =
            RuleBuilder.create().genericPredicateOp("F", true);
    static final RexRN predBefore = new RexRN.Pred(fOp, Seq.of(e, e));

    @Override
    public RelRN before() {
        // Filter(F(e(x), e(x)), S) — e(x) evaluated once per read.
        return source.filter(predBefore);
    }

    @Override
    public RelRN after() {
        // Project(x, e(x), S): widened child with the shared argument as a column.
        RelRN widened = source.project(Seq.of(x, e));
        RexRN t = widened.field(1);
        // Filter over the widened child reads the column t in both slots.
        RexRN predAfter = new RexRN.Pred(fOp, Seq.of(t, t));
        // Filter passes its child's output up, so project x back to restore the schema.
        return widened.filter(predAfter).project(Seq.of(widened.field(0)));
    }
}
