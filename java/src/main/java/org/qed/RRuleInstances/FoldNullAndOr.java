package org.qed.RRuleInstances;

import org.apache.calcite.rex.RexNode;
import org.apache.calcite.sql.type.SqlTypeName;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;
import org.qed.RuleBuilder;

// SCOPE: PARTIAL — only the And branch of the rule (And(Null,Null) => Null); the Or branch is structurally identical but not expressible in a single before()/after() pair
public record FoldNullAndOr() implements RRule {
    static final RelRN source = RelRN.scan("Source", "Source_Type");

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
        return source.filter(RexRN.and(nullLit, nullLit));
    }

    @Override
    public RelRN after() {
        return source.filter(nullLit);
    }
}
