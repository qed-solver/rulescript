package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.sql.type.SqlTypeName;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;
import org.qed.RuleBuilder;

// SCOPE: PARTIAL — only the `Eq` alternative of the source rule is encoded (a single RRule instance has one before/after pair), so the `Le`/`Ge` alternatives are not covered
public record SimplifySameVarEqualities() implements RRule {
    static final RelRN source = RelRN.scan("Source", "Source_Type");
    static final RexRN x = source.field(0);

    static final RexRN nullLit = new RexRN() {
        @Override
        public RexNode semantics() {
            var b = RuleBuilder.create();
            return b.getRexBuilder().makeNullLiteral(b.getTypeFactory().createSqlType(SqlTypeName.INTEGER));
        }
    };

    @Override
    public RelRN before() {
        // (Eq $left:(Variable) $right:(Variable) & VarsAreSame $left $right) with both vars the same column x
        return source.filter(new RexRN.Pred(SqlStdOperatorTable.EQUALS, Seq.of(x, x)));
    }

    @Override
    public RelRN after() {
        // (Or (IsNot $left (Null (TypeOf $left))) (Null (BoolType)))
        var isNotNull = new RexRN.Pred(SqlStdOperatorTable.IS_NOT_NULL, Seq.of(x));
        return source.filter(new RexRN.Or(Seq.of(isNotNull, nullLit)));
    }
}