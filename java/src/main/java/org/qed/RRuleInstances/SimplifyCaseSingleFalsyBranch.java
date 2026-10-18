package org.qed.RRuleInstances;

import org.apache.calcite.rex.RexNode;
import org.apache.calcite.sql.type.SqlTypeName;
import kala.collection.Seq;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;
import org.qed.RuleBuilder;

// ORIGIN: CockroachDB
// SCOPE: PARTIAL — assumes the single WHEN branch's THEN value is the FALSE constant and the ELSE value is the NULL constant (one of the four False|Null combinations the rule matches)
public record SimplifyCaseSingleFalsyBranch() implements RRule {
    static final RelRN source = RelRN.scan("Source", "Source_Type");

    // The WHEN condition: arbitrary boolean expression (uninterpreted symbol).
    static final RexRN cond = source.pred("cond");

    // THEN value: constant False (leakproof, per the rule's note).
    static final RexRN whenVal = RexRN.falseLiteral();

    // ELSE value: constant Null (leakproof, per the rule's note).
    static final RexRN elseVal = new RexRN() {
        @Override
        public RexNode semantics() {
            var b = RuleBuilder.create();
            return b.getRexBuilder().makeNullLiteral(
                b.getTypeFactory().createSqlType(SqlTypeName.BOOLEAN));
        }
    };

    // 3VL-equal expansion of CASE WHEN cond THEN False ELSE Null END:
    // (cond AND False) OR (NOT cond AND Null).
    // For every 3-valued assignment of cond it takes exactly the same value as
    // the CASE (T -> False, F -> Null, N -> Null), and in particular is never
    // TRUE, so the filter before the rewrite drops every row.
    static final RexRN caseExpr = new RexRN.Or(Seq.of(
        RexRN.and(cond, whenVal),
        RexRN.and(new RexRN.Not(cond), elseVal)));

    @Override
    public RelRN before() {
        return source.filter(caseExpr);
    }

    @Override
    public RelRN after() {
        return source.filter(elseVal);
    }
}
