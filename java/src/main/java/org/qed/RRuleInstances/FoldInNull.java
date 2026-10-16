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
// SCOPE: PARTIAL — covers only the In alternative of the rule (a single before/after pair cannot express the In | NotIn disjunction).
public record FoldInNull() implements RRule {
    static final RelType.VarType V = new RelType.VarType("V", true);
    static final RelRN source = RelRN.scanMany("Source", Seq.of(V));

    // NULL literal of type V (the type of the In's left operand)
    static final RexRN nullV = new RexRN() {
        @Override
        public RexNode semantics() {
            return RuleBuilder.create().getRexBuilder().makeNullLiteral(V);
        }
    };

    // NULL literal of BOOLEAN type — the rule's RHS, (Null (BoolType))
    static final RexRN nullBool = new RexRN() {
        @Override
        public RexNode semantics() {
            RuleBuilder b = RuleBuilder.create();
            return b.getRexBuilder().makeNullLiteral(b.getTypeFactory().createSqlType(SqlTypeName.BOOLEAN));
        }
    };

    @Override
    public RelRN before() {
        // $left IN (Tuple [ (Null) ]) — a 1-element null tuple desugars to $left = NULL
        return source.filter(new RexRN.Pred(SqlStdOperatorTable.EQUALS, Seq.of(source.field(0), nullV)));
    }

    @Override
    public RelRN after() {
        // (Null (BoolType))
        return source.filter(nullBool);
    }
}