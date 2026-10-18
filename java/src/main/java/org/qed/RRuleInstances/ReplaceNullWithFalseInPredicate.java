package org.qed.RRuleInstances;

import org.apache.calcite.rex.RexNode;
import org.apache.calcite.sql.type.SqlTypeName;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;
import org.qed.RuleBuilder;

// ORIGIN: Spark
// SCOPE: PARTIAL — encodes only the Filter context where a NULL boolean literal sits in one arm of a top-level And, i.e. Filter(And(p, NULL), R) => Filter(And(p, FALSE), R); the Or/If/CaseWhen/Not-In/Like-match-all/Join-and-write-action contexts of the rule are not encoded
public record ReplaceNullWithFalseInPredicate() implements RRule {
    static final RelRN source = RelRN.scan("Source", "Source_Type");
    static final RexRN p = source.pred("p");

    // a bare NULL of boolean type (the Literal(null, BooleanType) of the source rule)
    static final RexRN nullLit = new RexRN() {
        @Override
        public RexNode semantics() {
            var b = RuleBuilder.create();
            return b.getRexBuilder().makeNullLiteral(
                b.getTypeFactory().createSqlType(SqlTypeName.BOOLEAN));
        }
    };

    @Override
    public RelRN before() {
        // Filter(And(p, NULL), R): null literal under the top-level And
        return source.filter(RexRN.and(p, nullLit));
    }

    @Override
    public RelRN after() {
        // Filter(And(p, FALSE), R): the null replaced by FalseLiteral
        return source.filter(RexRN.and(p, RexRN.falseLiteral()));
    }
}