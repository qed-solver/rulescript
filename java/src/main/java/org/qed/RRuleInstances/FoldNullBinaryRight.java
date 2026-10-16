package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.sql.type.SqlTypeName;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RelType;
import org.qed.RexRN;
import org.qed.RuleBuilder;

// ORIGIN: CockroachDB
// SCOPE: PARTIAL — the binary operator is instantiated as the concrete `<` (null right arg implies null result, the rule's not-AllowNullArgs case), since QED only models null propagation for concrete primitive operators and arithmetic operators cannot infer return types over the virtual operand type V
public record FoldNullBinaryRight() implements RRule {
    static final RelType.VarType V = new RelType.VarType("V", true);
    static final RelRN source = RelRN.scan("Source", V, false);
    static final RexRN x = source.field(0); // $left: arbitrary left input

    // the null constant of the operand type V — the (Null) $right branch
    static final RexRN nullV = new RexRN() {
        @Override
        public RexNode semantics() {
            return RuleBuilder.create().getRexBuilder().makeNullLiteral(V);
        }
    };

    // the null constant of boolean type — the folded result in filter position
    static final RexRN nullBool = new RexRN() {
        @Override
        public RexNode semantics() {
            RuleBuilder b = RuleBuilder.create();
            return b.getRexBuilder().makeNullLiteral(b.getTypeFactory().createSqlType(SqlTypeName.BOOLEAN));
        }
    };

    @Override
    public RelRN before() {
        // Binary($left, (Null)) for a < operator that does not allow null args: x < NULL
        return source.filter(new RexRN.Pred(SqlStdOperatorTable.LESS_THAN, Seq.of(x, nullV)));
    }

    @Override
    public RelRN after() {
        // FoldNullBinary: the operator folds to a null value (boolean null in a filter)
        return source.filter(nullBool);
    }
}
