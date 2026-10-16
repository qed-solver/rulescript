package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RelType;
import org.qed.RexRN;
import org.qed.RuleBuilder;

// ORIGIN: CockroachDB
// SCOPE: PARTIAL — encodes the rule for the concrete normal null-propagating function EQUALS, with the NULL literal as its second argument and an arbitrary first argument (a scan column), in projection position; full generality (any null-propagating function, any argument position, any scalar position) is unprovable because QED does not lift null through uninterpreted function symbols (re-verified: the uninterpreted f(x, NULL) -> NULL encoding produces a clean QED JSON yet yields provable=false)
public record FoldFunctionWithNullArg() implements RRule {
    static final RelType.VarType V = new RelType.VarType("V", true);
    static final RelRN source = RelRN.scan("Source", V, false);
    static final RexRN x = source.field(0);

    // the NULL argument of the function
    static final RexRN nullV = new RexRN() {
        @Override
        public RexNode semantics() {
            return RuleBuilder.create().getRexBuilder().makeNullLiteral(V);
        }
    };

    // the folded result: Null of the function's return type (BOOLEAN for EQUALS)
    static final RexRN nullBool = new RexRN() {
        @Override
        public RexNode semantics() {
            return RuleBuilder.create().getRexBuilder().makeNullLiteral(RelType.fromString("BOOLEAN", true));
        }
    };

    @Override
    public RelRN before() {
        // EQUALS(x, NULL): a normal function (not an aggregate, window, or generator)
        // whose second input is the null constant and whose first input is an
        // arbitrary value; such a function is never invoked with a null argument
        return source.project(new RexRN.Proj(SqlStdOperatorTable.EQUALS, Seq.of(x, nullV)));
    }

    @Override
    public RelRN after() {
        // folded to a bare Null of the function's return type
        return source.project(nullBool);
    }
}
