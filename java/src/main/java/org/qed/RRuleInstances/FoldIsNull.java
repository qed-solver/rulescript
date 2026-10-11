package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.sql.type.SqlTypeName;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;
import org.qed.RuleBuilder;

// SCOPE: FULL
public record FoldIsNull() implements RRule {
    static final RelRN source = RelRN.scan("Source", "Source_Type");
    static final RexRN nullLit = new RexRN() {
        @Override
        public RexNode semantics() {
            var b = RuleBuilder.create();
            return b.getRexBuilder().makeNullLiteral(b.getTypeFactory().createSqlType(SqlTypeName.INTEGER));
        }
    };

    @Override
    public RelRN before() {
        return source.filter(new RexRN.Pred(SqlStdOperatorTable.IS_NULL, Seq.of(nullLit)));
    }

    @Override
    public RelRN after() {
        return source.filter(RexRN.trueLiteral());
    }
}
