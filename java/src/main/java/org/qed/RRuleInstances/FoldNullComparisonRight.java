package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.sql.type.SqlTypeName;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;
import org.qed.RuleBuilder;

// SCOPE: PARTIAL — restricts FoldNullComparisonRight to the Eq (equality) comparison branch, folding `left = NULL` to `NULL` for an arbitrary boolean left input; the other ~20 comparison operators in the rule each require a distinct concrete operator
public record FoldNullComparisonRight() implements RRule {
    static final RelRN source = RelRN.scan("Source", "Source_Type");

    // the null constant (boolean, matching the comparison's boolean result / filter type)
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
        // left = NULL  (equality comparison whose right input is the null constant)
        return source.filter(new RexRN.Pred(SqlStdOperatorTable.EQUALS,
            Seq.of(source.pred("left"), nullLit)));
    }

    @Override
    public RelRN after() {
        // folded to a bare NULL (BoolType)
        return source.filter(nullLit);
    }
}
