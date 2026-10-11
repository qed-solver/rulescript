package org.qed.RRuleInstances;

import org.apache.calcite.rex.RexNode;
import org.apache.calcite.sql.type.SqlTypeName;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;
import org.qed.RuleBuilder;
import kala.collection.Seq;

// SCOPE: PARTIAL — only the Or-with-Null branch (filter(Or(p, Null)) => filter(p)) over a plain scan, relying on filters treating a Null conjunct as False; the True-removal, False/contradiction, And-flattening, and Is branches are not captured
public record SimplifySelectFilters() implements RRule {
    static final RelRN source = RelRN.scan("Source", "Source_Type");
    static final RexRN p = source.pred("p");

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
        return source.filter(new RexRN.Or(Seq.of(p, nullLit)));
    }

    @Override
    public RelRN after() {
        return source.filter(p);
    }
}
