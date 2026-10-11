package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.sql.type.SqlTypeName;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;
import org.qed.RuleBuilder;

// SCOPE: PARTIAL — only the `Ne` alternative of the source rule is encoded (a single RRule instance has one before/after pair), so the `Lt`/`Gt` alternatives are not covered
public record SimplifySameVarInequalities() implements RRule {
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
        // (Ne $left:(Variable) $right:(Variable) & VarsAreSame $left $right) with both vars the same column x
        return source.filter(new RexRN.Pred(SqlStdOperatorTable.NOT_EQUALS, Seq.of(x, x)));
    }

    @Override
    public RelRN after() {
        // (And (Is $left (Null (TypeOf $left))) (Null (BoolType)))
        var isNull = new RexRN.Pred(SqlStdOperatorTable.IS_NULL, Seq.of(x));
        return source.filter(new RexRN.And(Seq.of(isNull, nullLit)));
    }
}