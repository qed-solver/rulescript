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

// SCOPE: PARTIAL — only the Eq branch of the 18-operator fold (eq(Null, x) ⇒ Null) is encoded; the other 17 branches are structurally identical but each requires its own before()/after() pair
public record FoldNullComparisonLeft() implements RRule {
    static final RelType.VarType V = new RelType.VarType("V", true);
    static final RelRN source = RelRN.scan("Source", V, false);
    static final RexRN x = source.field(0);

    static final RexRN nullV = new RexRN() {
        @Override
        public RexNode semantics() {
            return RuleBuilder.create().getRexBuilder().makeNullLiteral(V);
        }
    };

    static final RexRN nullBool = new RexRN() {
        @Override
        public RexNode semantics() {
            var b = RuleBuilder.create();
            return b.getRexBuilder().makeNullLiteral(
                b.getTypeFactory().createSqlType(SqlTypeName.BOOLEAN));
        }
    };

    @Override
    public RelRN before() {
        return source.filter(new RexRN.Pred(SqlStdOperatorTable.EQUALS, Seq.of(nullV, x)));
    }

    @Override
    public RelRN after() {
        return source.filter(nullBool);
    }
}
