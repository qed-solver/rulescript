package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.sql.type.SqlTypeName;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;
import org.qed.RuleBuilder;

// SCOPE: PARTIAL — only the unary-minus branch (-(Null) ⇒ Null) is encoded; other unary operators are structurally identical but each requires its own before()/after() pair
public record FoldNullUnary() implements RRule {
    static final RelRN source = RelRN.scan("Source", "Source_Type");

    static final RexRN nullInt = new RexRN() {
        @Override
        public RexNode semantics() {
            var b = RuleBuilder.create();
            return b.getRexBuilder().makeNullLiteral(
                b.getTypeFactory().createSqlType(SqlTypeName.INTEGER));
        }
    };

    @Override
    public RelRN before() {
        return source.project(new RexRN.Proj(SqlStdOperatorTable.UNARY_MINUS, Seq.of(nullInt)));
    }

    @Override
    public RelRN after() {
        return source.project(nullInt);
    }
}
