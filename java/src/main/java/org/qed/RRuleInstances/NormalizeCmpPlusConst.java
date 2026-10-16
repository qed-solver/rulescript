package org.qed.RRuleInstances;

import kala.collection.Seq;
import kala.collection.Set;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.type.RelDataType;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.QedTable;
import org.qed.RelRN;
import org.qed.RelType;
import org.qed.RexRN;
import org.qed.RRule;
import org.qed.RuleBuilder;

// NormalizeCmpPlusConst rewrites (a + c1) < c2 to a < (c2 - c1): it builds a
// constant expression tree on one side of a comparison by pulling the added
// constant over the operator as a subtraction.

// ORIGIN: CockroachDB
// SCOPE: PARTIAL — only the Lt operator of the original rule's Eq|Ge|Gt|Le|Lt comparison family is covered
public record NormalizeCmpPlusConst() implements RRule {

    /**
     * A 3-column scan (a, c1, c2) whose columns all carry the concrete
     * INTEGER type. Real arithmetic/comparison operators (PLUS, MINUS,
     * LESS_THAN) need operand types with a SqlTypeName to infer a return
     * type — VarType columns fail with "Cannot infer return type". QED maps
     * all numeric types to one integer theory, so INTEGER stands in for any
     * comparable additive type. The original rule's Const/FoldBinary and
     * ArithmeticErrorsOnOverflow guards are planning-time constructibility
     * side-conditions, not semantic constraints on the value identity.
     */
    record IntScan(String name) implements RelRN {
        @Override
        public RelNode semantics() {
            RelType intTy = RelType.fromString("INTEGER", true);
            Seq<String> colNames = Seq.of("col-" + name + "-0", "col-" + name + "-1", "col-" + name + "-2");
            Seq<RelDataType> colTypes = Seq.<RelDataType>of(intTy, intTy, intTy);
            var table = new QedTable(name, colNames, colTypes, Set.empty(), Set.empty());
            return RuleBuilder.create().addTable(table).scan(name).build();
        }
    }

    static final RelRN source = new IntScan("Source");

    @Override
    public RelRN before() {
        RexRN a = source.field(0);
        RexRN c1 = source.field(1);
        RexRN c2 = source.field(2);
        RexRN sum = new RexRN.Proj(SqlStdOperatorTable.PLUS, Seq.of(a, c1));
        return source.filter(new RexRN.Pred(SqlStdOperatorTable.LESS_THAN, Seq.of(sum, c2)));
    }

    @Override
    public RelRN after() {
        RexRN a = source.field(0);
        RexRN c1 = source.field(1);
        RexRN c2 = source.field(2);
        RexRN diff = new RexRN.Proj(SqlStdOperatorTable.MINUS, Seq.of(c2, c1));
        return source.filter(new RexRN.Pred(SqlStdOperatorTable.LESS_THAN, Seq.of(a, diff)));
    }
}