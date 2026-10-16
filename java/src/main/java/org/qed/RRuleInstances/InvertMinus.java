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

// InvertMinus rewrites -(a - b) to (b - a) if the operand types allow it.

// ORIGIN: CockroachDB
// SCOPE: FULL
public record InvertMinus() implements RRule {

    /**
     * A 2-column scan whose both columns carry a single concrete INTEGER type.
     * Real numeric operators (MINUS / UNARY_MINUS) need operand types with a
     * SqlTypeName to infer a return type — VarType columns fail with
     * "Cannot infer return type for -". QED maps all numeric types to the same
     * integer theory, so INTEGER stands in for any additive type, and the
     * guard CanConstructBinary(Minus, b, a) is a tautology for same-type
     * operands.
     */
    record IntScan(String name) implements RelRN {
        @Override
        public RelNode semantics() {
            RelType intTy = RelType.fromString("INTEGER", true);
            Seq<String> colNames = Seq.of("col-" + name + "-0", "col-" + name + "-1");
            Seq<RelDataType> colTypes = Seq.<RelDataType>of(intTy, intTy);
            var table = new QedTable(name, colNames, colTypes, Set.empty(), Set.empty());
            return RuleBuilder.create().addTable(table).scan(name).build();
        }
    }

    static final RelRN source = new IntScan("Source");

    @Override
    public RelRN before() {
        RexRN a = source.field(0);
        RexRN b = source.field(1);
        RexRN diff = new RexRN.Proj(SqlStdOperatorTable.MINUS, Seq.of(a, b));
        return source.project(new RexRN.Proj(SqlStdOperatorTable.UNARY_MINUS, Seq.of(diff)));
    }

    @Override
    public RelRN after() {
        RexRN a = source.field(0);
        RexRN b = source.field(1);
        return source.project(new RexRN.Proj(SqlStdOperatorTable.MINUS, Seq.of(b, a)));
    }
}