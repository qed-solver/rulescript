package org.qed.RRuleInstances;
import org.qed.*;
import kala.collection.Seq;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.SqlOperator;

// SCOPE: PARTIAL — INNER join only; right Project a pure column remap (passthrough, no computed projections); 1-col left / 2-col right
public record TryDecorrelateProject() implements RRule {
  static final RelRN L = RelRN.scan("L","L_Type");
  static final RelRN R = RelRN.scanMany("R", Seq.of(
      new RelType.VarType("R0_Type",true), new RelType.VarType("R1_Type",true)));
  static final SqlOperator on = RuleBuilder.create().genericPredicateOp("on", true);
  static final RelRN rightProj = R.project(Seq.of(R.field(1), R.field(0)));
  @Override public RelRN before() {
    return L.join(JoinRelType.INNER, new RexRN.Pred(on, Seq.of(
        L.joinField(0,rightProj), L.joinField(1,rightProj), L.joinField(2,rightProj))), rightProj);
  }
  @Override public RelRN after() {
    RelRN j = L.join(JoinRelType.INNER, RexRN.trueLiteral(), R);
    RelRN p = j.project(Seq.of(j.field(0), j.field(2), j.field(1)));
    return p.filter(new RexRN.Pred(on, Seq.of(p.field(0), p.field(1), p.field(2))));
  }
}
