package org.qed.Backends.Calcite;

import kala.collection.Seq;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.plan.RelOptTable;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.TableScan;
import org.apache.calcite.rel.core.Values;
import org.apache.calcite.rel.logical.LogicalFilter;
import org.apache.calcite.rel.logical.LogicalJoin;
import org.apache.calcite.rex.RexCall;
import org.apache.calcite.rex.RexCorrelVariable;
import org.apache.calcite.rex.RexFieldAccess;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.rex.RexInputRef;
import org.apache.calcite.rex.RexLiteral;
import org.apache.calcite.rex.RexSubQuery;
import org.apache.calcite.rel.type.RelDataType;
import org.apache.calcite.sql.SqlKind;
import org.apache.calcite.sql.SqlOperator;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.CodeGenerator;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RelType;
import org.qed.RexRN;
import org.qed.RuleBuilder;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class CalciteGenerator implements CodeGenerator<CalciteGenerator.Env> {
    private static final String ANY_RELATION = "RelNode";

    private RelRN afterRoot;

    @Override
    public String generate(RRule rule) {
        afterRoot = rule.after();
        return CodeGenerator.super.generate(rule);
    }

    @Override
    public Env preMatch(String unused) {
        return new Env();
    }

    @Override
    public Env postMatch(Env env) {
        env.root = env.last;
        return env;
    }

    @Override
    public Env preTransform(Env env) {
        env.afterRoot = afterRoot;
        return env;
    }

    @Override
    public String translate(String name, Env onMatch, Env transform) {
        var out = new StringBuilder("package org.qed.Backends.Calcite.Generated;\n\n");
        out.append("import org.apache.calcite.plan.RelOptRuleCall;\n");
        out.append("import org.apache.calcite.plan.RelOptUtil;\n");
        out.append("import org.apache.calcite.plan.RelRule;\n");
        out.append("import org.apache.calcite.rel.RelNode;\n");
        out.append("import org.apache.calcite.rel.core.Aggregate;\n");
        out.append("import org.apache.calcite.rel.core.AggregateCall;\n");
        out.append("import org.apache.calcite.rel.core.JoinRelType;\n");
        out.append("import org.apache.calcite.rel.logical.*;\n");
        out.append("import org.apache.calcite.rel.type.RelDataType;\n");
        out.append("import org.apache.calcite.rex.*;\n");
        out.append("import org.apache.calcite.sql.SqlAggFunction;\n");
        out.append("import org.apache.calcite.sql.SqlOperator;\n");
        out.append("import org.apache.calcite.sql.fun.SqlStdOperatorTable;\n");
        out.append("import org.apache.calcite.tools.RelBuilder;\n");
        out.append("import org.qed.Backends.Calcite.CalciteRuntime;\n");
        out.append("import org.qed.Backends.Calcite.EmptyConfig;\n\n");
        out.append("import java.util.HashMap;\nimport java.util.List;\nimport java.util.Map;\n\n");
        out.append("public class ").append(name).append(" extends RelRule<").append(name).append(".Config> {\n");
        out.append("\tprotected ").append(name).append("(Config config) {\n\t\tsuper(config);\n\t}\n\n");
        out.append("\t@Override\n\tpublic void onMatch(RelOptRuleCall call) {\n");
        out.append("\t\tfinal RelBuilder b = CalciteRuntime.builder(call);\n");
        out.append("\t\tfinal Map<String, RelDataType> types = new HashMap<>();\n");
        onMatch.prelude.forEach(line -> out.append("\t\t").append(line).append("\n"));
        transform.body.forEach(line -> out.append("\t\t").append(line).append("\n"));
        out.append("\t\tCalciteRuntime.transform(call, b);\n");
        out.append("\t}\n\n");
        out.append("\tpublic interface Config extends EmptyConfig {\n");
        out.append("\t\tConfig DEFAULT = new Config() {};\n\n");
        out.append("\t\t@Override\n\t\tdefault ").append(name).append(" toRule() {\n");
        out.append("\t\t\treturn new ").append(name).append("(this);\n\t\t}\n\n");
        out.append("\t\t@Override\n\t\tdefault String description() {\n");
        out.append("\t\t\treturn \"").append(name).append("\";\n\t\t}\n\n");
        out.append("\t\t@Override\n\t\tdefault RelRule.OperandTransform operandSupplier() {\n");
        out.append("\t\t\treturn ").append(onMatch.root.render()).append(";\n\t\t}\n\t}\n}\n");
        return out.toString();
    }


    @Override
    public Env onMatchScan(Env env, RelRN.Scan scan) {
        var operand = env.operand(ANY_RELATION, Operand.Kind.ANY, scan);
        if (env.bindScan(scan.name(), operand.index)) {
            env.check("r_" + operand.index + ".getRowType().getFieldCount() == 1");
            bindColumn(env, operand, 0, scan.ty());
            if (scan.unique()) {
                env.check("CalciteRuntime.isUnique(call, r_" + operand.index + ", 0)");
            }
        }
        return env.done(operand);
    }

    @Override
    public Env onMatchScanMany(Env env, RelRN.ScanMany scan) {
        var operand = env.operand(ANY_RELATION, Operand.Kind.ANY, scan);
        if (env.bindScan(scan.name(), operand.index)) {
            env.check("r_" + operand.index + ".getRowType().getFieldCount() == " + scan.tys().size());
            for (var i = 0; i < scan.tys().size(); i++) {
                bindColumn(env, operand, i, scan.tys().get(i));
            }
        }
        return env.done(operand);
    }

    private static String typeId(RelDataType type) {
        return type.toString().split("[: ]")[0];
    }

    private void bindColumn(Env env, Operand operand, int ordinal, RelType.VarType type) {
        var id = typeId(type);
        env.boundTypes.add(id);
        if (!type.isNullable() && env.operands.containsKey(operand.index)) {
            env.prefilter(operand, "!r_" + operand.index + ".getRowType().getFieldList().get(" + ordinal
                    + ").getType().isNullable()");
        }
        env.check("CalciteRuntime.bindColumn(types, " + literal(id) + ", " + type.isNullable() + ", r_"
                + operand.index + ", " + ordinal + ")");
    }

    @Override
    public Env onMatchFilter(Env env, RelRN.Filter filter) {
        var operand = env.operand("LogicalFilter", Operand.Kind.INPUTS, filter);
        matchRex(env, filter.cond(), "r_" + operand.index + ".getCondition()");
        operand.kids.add(matchChild(env, operand, "getInput()", filter.source()));
        return env.done(operand);
    }

    @Override
    public Env onMatchProject(Env env, RelRN.Project project) {
        var operand = env.operand("LogicalProject", Operand.Kind.INPUTS, project);
        env.check("r_" + operand.index + ".getProjects().size() == 1");
        matchRex(env, project.map(), "r_" + operand.index + ".getProjects().get(0)");
        operand.kids.add(matchChild(env, operand, "getInput()", project.source()));
        return env.done(operand);
    }

    @Override
    public Env onMatchProjectMany(Env env, RelRN.ProjectMany project) {
        var operand = env.operand("LogicalProject", Operand.Kind.INPUTS, project);
        env.check("r_" + operand.index + ".getProjects().size() == " + project.exprs().size());
        for (var i = 0; i < project.exprs().size(); i++) {
            matchRex(env, project.exprs().get(i), "r_" + operand.index + ".getProjects().get(" + i + ")");
        }
        operand.kids.add(matchChild(env, operand, "getInput()", project.source()));
        return env.done(operand);
    }

    @Override
    public Env onMatchJoin(Env env, RelRN.Join join) {
        return matchJoin(env, join, join.ty().semantics(), join.cond(), join.left(), join.right());
    }

    @Override
    public Env onMatchJoinWithSeparateConds(Env env, RelRN.JoinWithSeparateConds join) {
        return matchJoin(env, join, join.ty().semantics(), join.cond(), join.left(), join.right());
    }

    @Override
    public Env onMatchJoinWithPushedConds(Env env, RelRN.JoinWithPushedConds join) {
        return matchJoin(env, join, join.ty().semantics(), join.cond(), join.left(), join.right());
    }

    private Env matchJoin(Env env, RelRN pattern, JoinRelType type, RexRN cond, RelRN left, RelRN right) {
        var operand = env.operand("LogicalJoin", Operand.Kind.INPUTS, pattern);
        env.check("r_" + operand.index + ".getJoinType() == JoinRelType." + type.name());
        matchRex(env, cond, "r_" + operand.index + ".getCondition()");
        operand.kids.add(matchChild(env, operand, "getLeft()", left));
        operand.kids.add(matchChild(env, operand, "getRight()", right));
        return env.done(operand);
    }

    @Override
    public Env onMatchUnion(Env env, RelRN.Union union) {
        return matchSetOp(env, "LogicalUnion", union, union.all(), union.sources());
    }

    @Override
    public Env onMatchIntersect(Env env, RelRN.Intersect intersect) {
        return matchSetOp(env, "LogicalIntersect", intersect, intersect.all(), intersect.sources());
    }

    @Override
    public Env onMatchMinus(Env env, RelRN.Minus minus) {
        return matchSetOp(env, "LogicalMinus", minus, minus.all(), minus.sources());
    }

    private Env matchSetOp(Env env, String cls, RelRN pattern, boolean all, Seq<RelRN> sources) {
        var operand = env.operand(cls, Operand.Kind.INPUTS, pattern);
        env.check("r_" + operand.index + ".all == " + all);
        env.check("r_" + operand.index + ".getInputs().size() == " + sources.size());
        for (var i = 0; i < sources.size(); i++) {
            operand.kids.add(matchChild(env, operand, "getInputs().get(" + i + ")", sources.get(i)));
        }
        return env.done(operand);
    }

    @Override
    public Env onMatchEmpty(Env env, RelRN.Empty empty) {
        var operand = env.operand("LogicalValues", Operand.Kind.NONE, empty);
        env.check("r_" + operand.index + ".getTuples().isEmpty()");
        env.check("r_" + operand.index + ".getRowType().getFieldCount() == "
                + empty.sourceType().semantics().getRowType().getFieldCount());
        env.rels.putIfAbsent(empty.sourceType(), operand.index);
        var columns = empty.sourceType() instanceof RelRN.ScanMany many ? many.tys()
                : empty.sourceType() instanceof RelRN.Scan one ? Seq.of(one.ty()) : Seq.<RelType.VarType>empty();
        for (var i = 0; i < columns.size(); i++) {
            env.emptyTypes.putIfAbsent(typeId(columns.get(i)), "r_" + operand.index + ".getRowType().getFieldList().get("
                    + i + ").getType()");
        }
        return env.done(operand);
    }

    @Override
    public Env onMatchCorrelate(Env env, RelRN.Correlate correlate) {
        var operand = env.operand("LogicalCorrelate", Operand.Kind.INPUTS, correlate);
        if (operand.inline) {
            throw new UnsupportedConstructException("A correlate inside a subquery is not supported");
        }
        operand.kids.add(matchChild(env, correlate.left()));
        var filter = env.hidden("LogicalFilter", Operand.Kind.INPUTS);
        env.check("r_" + operand.index + ".getJoinType() == JoinRelType." + correlate.ty().name());
        var arguments = "ca_" + env.ids++;
        env.prelude.add("final RexNode[] " + arguments + " = CalciteRuntime.correlateArguments(r_" + operand.index
                + ");");
        var condition = "r_" + filter.index + ".getCondition()";
        if (isSymbol(correlate.cond())) {
            bindExpressionSymbol(env, correlate.cond(), condition, arguments, null,
                    correlate.left().semantics().getRowType().getFieldCount()
                            + correlate.right().semantics().getRowType().getFieldCount());
        } else {
            env.check("CalciteRuntime.isCall(" + condition + ", " + operatorReference(correlate.cond()) + ", "
                    + arguments + ")");
        }
        filter.kids.add(matchChild(env, correlate.right()));
        operand.kids.add(filter);
        return env.done(operand);
    }

    @Override
    public Env onMatchAggregate(Env env, RelRN.Aggregate aggregate) {
        var operand = env.operand("LogicalAggregate", Operand.Kind.INPUTS, aggregate);
        var lowered = aggregate.groupSet().anyMatch(e -> !isColumn(e))
                || aggregate.aggCalls().anyMatch(c -> c.operands().anyMatch(e -> !isColumn(e)));
        if (lowered && operand.inline) {
            throw new UnsupportedConstructException("An aggregate with computed keys inside a subquery is not supported");
        }
        var project = lowered ? env.hidden("LogicalProject", Operand.Kind.INPUTS) : null;
        var resolve = (java.util.function.Function<String, String>) column -> lowered
                ? "r_" + project.index + ".getProjects().get(" + column + ")"
                : "CalciteRuntime.ref(" + column + ")";
        var self = "r_" + operand.index;
        env.check(self + ".getGroupType() == Aggregate.Group.SIMPLE");
        env.check(self + ".getGroupSet().cardinality() == " + aggregate.groupSet().size());
        env.check(self + ".getAggCallList().size() == " + aggregate.aggCalls().size());
        var groups = "g_" + env.ids++;
        env.prelude.add("final List<Integer> " + groups + " = " + self + ".getGroupSet().asList();");
        for (var i = 0; i < aggregate.groupSet().size(); i++) {
            matchRex(env, aggregate.groupSet().get(i), resolve.apply(groups + ".get(" + i + ")"));
        }
        for (var j = 0; j < aggregate.aggCalls().size(); j++) {
            var call = aggregate.aggCalls().get(j);
            var variable = "a_" + env.ids++;
            env.prelude.add("final AggregateCall " + variable + " = " + self + ".getAggCallList().get(" + j + ");");
            env.check("!" + variable + ".hasFilter() && " + variable
                    + ".getCollation().getFieldCollations().isEmpty() && " + variable + ".isDistinct() == "
                    + call.distinct() + " && " + variable + ".getArgList().size() == " + call.operands().size());
            for (var k = 0; k < call.operands().size(); k++) {
                matchRex(env, call.operands().get(k), resolve.apply(variable + ".getArgList().get(" + k + ")"));
            }
            var function = aggregateFunction(call);
            if (function == null) {
                throw new UnsupportedConstructException("Aggregate call " + call.name() + " has no function");
            }
            if (isSymbol(function)) {
                if (env.aggregates.containsKey(function.getName())) {
                    throw new UnsupportedConstructException("Aggregate function " + function.getName()
                            + " occurs more than once in the pattern");
                }
                env.aggregates.put(function.getName(), variable);
            } else {
                env.check(variable + ".getAggregation() == " + operatorReference(function));
            }
        }
        if (project != null) {
            operand.kids.add(project);
            project.kids.add(matchChild(env, aggregate.source()));
        } else {
            operand.kids.add(matchChild(env, operand, "getInput()", aggregate.source()));
        }
        return env.done(operand);
    }

    private static boolean isColumn(RexRN expr) {
        return expr instanceof RexRN.Field || expr instanceof RexRN.JoinField
                || !(expr instanceof RexRN.Pred || expr instanceof RexRN.Proj || expr instanceof RexRN.GroupBy
                || expr instanceof RexRN.And || expr instanceof RexRN.Or || expr instanceof RexRN.Not
                || expr instanceof RexRN.True || expr instanceof RexRN.False)
                && expr.semantics() instanceof RexInputRef;
    }

    private static SqlOperator aggregateFunction(RelRN.AggCall call) {
        return call.operator() != null ? call.operator() : RuleBuilder.create().genericAggregateOp(call.name(),
                call.type());
    }

    @Override
    public Env onMatchCustom(Env env, RelRN custom) {
        if (custom.semantics() instanceof Values values && !values.getTuples().isEmpty()) {
            var operand = env.operand("LogicalValues", Operand.Kind.NONE, custom);
            env.check("CalciteRuntime.sameValues(call, r_" + operand.index + ", " + valuesArguments(values) + ")");
            return env.done(operand);
        }
        return matchNode(env, custom.semantics(), slots(custom), custom);
    }

    private Operand matchChild(Env env, RelRN pattern) {
        onMatch(env, pattern);
        return env.last;
    }

    private Operand matchChild(Env env, Operand parent, String accessor, RelRN pattern) {
        if (parent.inline) {
            env.inlineSource = "r_" + parent.index + "." + accessor;
        }
        onMatch(env, pattern);
        if (env.inlineSource != null) {
            throw new UnsupportedConstructException("A pattern inside a subquery is not supported: "
                    + pattern);
        }
        return env.last;
    }


    private void matchRex(Env env, RexRN pattern, String expr) {
        var saved = env.rex;
        var local = "e_" + env.ids++;
        env.prelude.add("final RexNode " + local + " = " + expr + ";");
        env.rex = local;
        onMatch(env, pattern);
        env.rex = saved;
    }

    @Override
    public Env onMatchPred(Env env, RexRN.Pred pred) {
        return matchCall(env, pred.operator(), pred.sources());
    }

    @Override
    public Env onMatchProj(Env env, RexRN.Proj proj) {
        return matchCall(env, proj.operator(), proj.sources());
    }

    @Override
    public Env onMatchGroupBy(Env env, RexRN.GroupBy groupBy) {
        return matchCall(env, groupBy.operator(), groupBy.sources());
    }

    private Env matchCall(Env env, SqlOperator operator, Seq<RexRN> sources) {
        var expr = env.rex;
        if (!isSymbol(operator)) {
            env.check(expr + " instanceof RexCall && ((RexCall) " + expr + ").getOperator() == "
                    + operatorReference(operator) + " && ((RexCall) " + expr + ").getOperands().size() == "
                    + sources.size());
            for (var i = 0; i < sources.size(); i++) {
                matchRex(env, sources.get(i), "((RexCall) " + expr + ").getOperands().get(" + i + ")");
            }
            return env;
        }
        var arguments = arguments(env, sources);
        var existing = env.symbols.get(operator.getName());
        if (existing != null) {
            if (existing.operator || arguments == null || !existing.signature.equals(arguments)) {
                throw new UnsupportedConstructException("Symbol " + operator.getName()
                        + " is matched more than once, or against different arguments");
            }
            env.check(existing.variable + ".equals(" + expr + ")");
            return env;
        }
        if (arguments != null) {
            var array = "args_" + env.ids++;
            env.prelude.add("final RexNode[] " + array + " = " + arguments + ";");
            bindExpressionSymbol(env, operator, expr, array, arguments, sources.size());
            return env;
        }
        var variable = "op_" + env.ids++;
        env.check(expr + " instanceof RexCall && ((RexCall) " + expr + ").getOperands().size() == "
                + sources.size());
        env.prelude.add("final SqlOperator " + variable + " = ((RexCall) " + expr + ").getOperator();");
        env.symbols.put(operator.getName(), new Symbol(variable, true, null, null, sources.size()));
        for (var i = 0; i < sources.size(); i++) {
            matchRex(env, sources.get(i), "((RexCall) " + expr + ").getOperands().get(" + i + ")");
        }
        return env;
    }

    private void bindExpressionSymbol(Env env, SqlOperator operator, String expr, String arguments, String signature,
                                      int arity) {
        var variable = "sym_" + env.ids++;
        env.prelude.add("final RexNode " + variable + " = " + expr + ";");
        env.check("CalciteRuntime.abstracts(" + variable + ", " + arguments + ")");
        env.symbols.put(operator.getName(), new Symbol(variable, false, arguments, signature, arity));
    }

    private String arguments(Env env, Seq<RexRN> sources) {
        var parts = new ArrayList<String>();
        for (var source : sources) {
            var part = argument(env, source);
            if (part == null) {
                return null;
            }
            parts.add(part);
        }
        return "new RexNode[]{" + String.join(", ", parts) + "}";
    }

    private String argument(Env env, RexRN source) {
        return switch (source) {
            case RexRN.Field field -> "CalciteRuntime.ref(" + field.ordinal() + ")";
            case RexRN.JoinField field -> "CalciteRuntime.ref(" + field.ordinal() + ")";
            case RexRN.Pred pred -> boundArgument(env, pred.operator(), pred.sources());
            case RexRN.Proj proj -> boundArgument(env, proj.operator(), proj.sources());
            case RexRN.GroupBy groupBy -> boundArgument(env, groupBy.operator(), groupBy.sources());
            case RexRN.True ignored -> null;
            case RexRN.False ignored -> null;
            case RexRN.And ignored -> null;
            case RexRN.Or ignored -> null;
            case RexRN.Not ignored -> null;
            default -> source.semantics() instanceof RexInputRef ref ? "CalciteRuntime.ref(" + ref.getIndex() + ")"
                    : null;
        };
    }

    private String boundArgument(Env env, SqlOperator operator, Seq<RexRN> sources) {
        var symbol = isSymbol(operator) ? env.symbols.get(operator.getName()) : null;
        if (symbol == null || symbol.operator || !symbol.signature.equals(arguments(env, sources))) {
            return null;
        }
        return symbol.variable;
    }

    @Override
    public Env onMatchCustom(Env env, RexRN custom) {
        var semantics = custom.semantics();
        if (semantics instanceof RexInputRef ref) {
            return matchRef(env, ref.getIndex());
        }
        if (semantics instanceof RexLiteral literal) {
            return matchLiteral(env, literal);
        }
        if (semantics instanceof RexSubQuery subQuery && subQuery.getKind() == SqlKind.EXISTS) {
            env.check(env.rex + " instanceof RexSubQuery && ((RexSubQuery) " + env.rex
                    + ").getKind() == org.apache.calcite.sql.SqlKind.EXISTS");
            env.inlineSource = "((RexSubQuery) " + env.rex + ").rel";
            onMatch(env, subQueryBody(custom));
            if (env.inlineSource != null) {
                throw new UnsupportedConstructException("The relation inside EXISTS is not supported");
            }
            return env;
        }
        return unimplementedOnMatch(env, custom);
    }

    private static RelRN subQueryBody(RexRN custom) {
        var components = custom.getClass().getRecordComponents();
        if (components == null || components.length != 1 || components[0].getType() != RelRN.class) {
            throw new UnsupportedConstructException("An EXISTS expression must hold exactly one relation: "
                    + custom);
        }
        try {
            var accessor = components[0].getAccessor();
            accessor.setAccessible(true);
            return (RelRN) accessor.invoke(custom);
        } catch (ReflectiveOperationException e) {
            throw new UnsupportedConstructException("The relation inside EXISTS is not accessible: " + e);
        }
    }

    private Env matchLiteral(Env env, RexLiteral literal) {
        if (literal.isNull()) {
            var type = literal.getType();
            var typeCheck = type instanceof RelType.VarType
                    ? "CalciteRuntime.hasBoundType(types, " + literal(typeId(type)) + ", " + env.rex + ")"
                    : env.rex + ".getType().getSqlTypeName() == org.apache.calcite.sql.type.SqlTypeName."
                    + type.getSqlTypeName().name();
            env.check("RexUtil.isNullLiteral(" + env.rex + ", true) && " + typeCheck);
            return env;
        }
        env.check("CalciteRuntime.isLiteral(" + env.rex + ", " + javaValue(literal) + ")");
        return env;
    }

    @Override
    public Env onMatchField(Env env, RexRN.Field field) {
        return matchRef(env, field.ordinal());
    }

    @Override
    public Env onMatchJoinField(Env env, RexRN.JoinField field) {
        return matchRef(env, field.ordinal());
    }

    private Env matchRef(Env env, int ordinal) {
        env.check(env.rex + " instanceof RexInputRef && ((RexInputRef) " + env.rex + ").getIndex() == " + ordinal);
        return env;
    }

    @Override
    public Env onMatchAnd(Env env, RexRN.And and) {
        var list = "c_" + env.ids++;
        env.prelude.add("final List<RexNode> " + list + " = CalciteRuntime.conjunctions(" + env.rex + ");");
        return matchList(env, list, flatten(and.sources(), RexRN.And.class));
    }

    @Override
    public Env onMatchOr(Env env, RexRN.Or or) {
        var list = "d_" + env.ids++;
        env.prelude.add("final List<RexNode> " + list + " = CalciteRuntime.disjunctions(" + env.rex + ");");
        return matchList(env, list, flatten(or.sources(), RexRN.Or.class));
    }

    private static <T extends RexRN> Seq<RexRN> flatten(Seq<RexRN> sources, Class<T> connective) {
        var flat = new ArrayList<RexRN>();
        for (var source : sources) {
            if (connective.isInstance(source)) {
                var nested = source instanceof RexRN.And and ? and.sources() : ((RexRN.Or) source).sources();
                flat.addAll(flatten(nested, connective).asJava());
            } else {
                flat.add(source);
            }
        }
        return Seq.from(flat);
    }

    private Env matchList(Env env, String list, Seq<RexRN> sources) {
        env.check(list + ".size() == " + sources.size());
        for (var i = 0; i < sources.size(); i++) {
            matchRex(env, sources.get(i), list + ".get(" + i + ")");
        }
        return env;
    }

    @Override
    public Env onMatchNot(Env env, RexRN.Not not) {
        env.check(env.rex + ".getKind() == org.apache.calcite.sql.SqlKind.NOT");
        matchRex(env, not.source(), "((RexCall) " + env.rex + ").getOperands().get(0)");
        return env;
    }

    @Override
    public Env onMatchTrue(Env env, RexRN literal) {
        env.check(env.rex + ".isAlwaysTrue()");
        return env;
    }

    @Override
    public Env onMatchFalse(Env env, RexRN literal) {
        env.check(env.rex + ".isAlwaysFalse()");
        return env;
    }


    @Override
    public Env transformScan(Env env, RelRN.Scan scan) {
        return pushScan(env, scan.name());
    }

    @Override
    public Env transformScanMany(Env env, RelRN.ScanMany scan) {
        return pushScan(env, scan.name());
    }

    private Env pushScan(Env env, String name) {
        var index = env.scans.get(name);
        if (index == null) {
            throw new UnsupportedConstructException("Scan " + name + " is not bound by the matched pattern");
        }
        env.body.add("b.push(r_" + index + ");");
        return env;
    }

    @Override
    public Env transformFilter(Env env, RelRN.Filter filter) {
        transform(env, filter.source());
        env.body.add("b.filter(" + rex(env, 1, filter.cond()) + ");");
        return env;
    }

    @Override
    public Env transformProject(Env env, RelRN.Project project) {
        transform(env, project.source());
        env.body.add("b.project(" + rex(env, 1, project.map()) + ");");
        return env;
    }

    @Override
    public Env transformProjectMany(Env env, RelRN.ProjectMany project) {
        transform(env, project.source());
        var exprs = project.exprs().map(e -> rex(env, 1, e)).joinToString(", ");
        env.body.add("b.project(List.<RexNode>of(" + exprs + "));");
        return env;
    }

    @Override
    public Env transformJoin(Env env, RelRN.Join join) {
        return buildJoin(env, join.ty().semantics(), join.cond(), join.left(), join.right());
    }

    @Override
    public Env transformJoinWithSeparateConds(Env env, RelRN.JoinWithSeparateConds join) {
        return buildJoin(env, join.ty().semantics(), join.cond(), join.left(), join.right());
    }

    @Override
    public Env transformJoinWithPushedConds(Env env, RelRN.JoinWithPushedConds join) {
        return buildJoin(env, join.ty().semantics(), join.cond(), join.left(), join.right());
    }

    private Env buildJoin(Env env, JoinRelType type, RexRN cond, RelRN left, RelRN right) {
        transform(env, left);
        transform(env, right);
        env.body.add("b.join(JoinRelType." + type.name() + ", " + rex(env, 2, cond) + ");");
        return env;
    }

    @Override
    public Env transformUnion(Env env, RelRN.Union union) {
        return buildSetOp(env, "union", union.all(), union.sources());
    }

    @Override
    public Env transformIntersect(Env env, RelRN.Intersect intersect) {
        return buildSetOp(env, "intersect", intersect.all(), intersect.sources());
    }

    @Override
    public Env transformMinus(Env env, RelRN.Minus minus) {
        return buildSetOp(env, "minus", minus.all(), minus.sources());
    }

    private Env buildSetOp(Env env, String method, boolean all, Seq<RelRN> sources) {
        for (var source : sources) {
            transform(env, source);
        }
        env.body.add("b." + method + "(" + all + ", " + sources.size() + ");");
        return env;
    }

    @Override
    public Env transformEmpty(Env env, RelRN.Empty empty) {
        var index = env.rels.get(empty.sourceType());
        if (index == null && empty.equals(env.afterRoot)) {
            index = 0;
        }
        if (index == null) {
            throw new UnsupportedConstructException("The row type of the empty relation is not bound by the pattern");
        }
        env.body.add("b.values(r_" + index + ".getRowType());");
        return env;
    }

    @Override
    public Env transformCorrelate(Env env, RelRN.Correlate correlate) {
        transform(env, correlate.left());
        transform(env, correlate.right());
        var arguments = "ca_" + env.ids++;
        String condition;
        if (isSymbol(correlate.cond())) {
            var symbol = env.symbols.get(correlate.cond().getName());
            var arity = correlate.left().semantics().getRowType().getFieldCount()
                    + correlate.right().semantics().getRowType().getFieldCount();
            if (symbol == null || symbol.operator || symbol.arity != arity) {
                throw new UnsupportedConstructException("Symbol " + correlate.cond().getName()
                        + " is not bound to an expression over the " + arity + " columns of the correlate");
            }
            condition = "CalciteRuntime.instantiate(" + symbol.variable + ", " + symbol.arguments + ", "
                    + arguments + ")";
        } else {
            condition = "b.call(" + operatorReference(correlate.cond()) + ", " + arguments + ")";
        }
        env.body.add("CalciteRuntime.correlate(b, JoinRelType." + correlate.ty().name() + ", " + arguments + " -> "
                + condition + ");");
        return env;
    }

    @Override
    public Env transformAggregate(Env env, RelRN.Aggregate aggregate) {
        transform(env, aggregate.source());
        var keys = aggregate.groupSet().map(e -> rex(env, 1, e)).joinToString(", ");
        var calls = new ArrayList<String>();
        for (var call : aggregate.aggCalls()) {
            var function = aggregateFunction(call);
            String reference;
            String alias;
            if (isSymbol(function)) {
                var bound = env.aggregates.get(function.getName());
                if (bound == null) {
                    throw new UnsupportedConstructException("Aggregate function " + function.getName()
                            + " is introduced by the rewrite but not bound by the matched pattern");
                }
                reference = "(SqlAggFunction) " + bound + ".getAggregation()";
                alias = bound + ".getName()";
            } else {
                reference = "(SqlAggFunction) " + operatorReference(function);
                alias = literal(call.name());
            }
            var operands = call.operands().map(e -> rex(env, 1, e)).joinToString(", ");
            calls.add("b.aggregateCall(" + reference + ", " + call.distinct() + ", null, " + alias
                    + ", List.<RexNode>of(" + operands + "))");
        }
        env.body.add("b.aggregate(b.groupKey(" + keys + ")" + (calls.isEmpty() ? "" : ", ")
                + String.join(", ", calls) + ");");
        return env;
    }

    @Override
    public Env transformCustom(Env env, RelRN custom) {
        if (custom.semantics() instanceof Values values && !values.getTuples().isEmpty()) {
            env.body.add("b.values(" + valuesArguments(values) + ");");
            return env;
        }
        transformNode(env, custom.semantics(), slots(custom));
        return env;
    }


    private static Seq<RelRN> slots(RelRN custom) {
        var found = new ArrayList<RelRN>();
        var components = custom.getClass().getRecordComponents();
        if (components != null) {
            for (var component : components) {
                if (!RelRN.class.isAssignableFrom(component.getType())) {
                    continue;
                }
                try {
                    var accessor = component.getAccessor();
                    accessor.setAccessible(true);
                    found.add((RelRN) accessor.invoke(custom));
                } catch (ReflectiveOperationException e) {
                    throw new UnsupportedConstructException("A relation of " + custom + " is not accessible: " + e);
                }
            }
        }
        return Seq.from(found);
    }

    private static RelRN slotOf(RelNode node, Seq<RelRN> slots) {
        for (var slot : slots) {
            if (slot.semantics().deepEquals(node)) {
                return slot;
            }
        }
        return null;
    }

    private Env matchNode(Env env, RelNode node, Seq<RelRN> slots, RelRN key) {
        var slot = slotOf(node, slots);
        if (slot != null) {
            return onMatch(env, slot);
        }
        if (node instanceof TableScan scan) {
            return matchConcreteScan(env, scan, key);
        }
        if (node instanceof LogicalFilter filter) {
            var operand = open(env, "LogicalFilter", key);
            env.check("r_" + operand.index + ".getVariablesSet().size() == " + filter.getVariablesSet().size());
            matchRexNode(env, filter.getCondition(), "r_" + operand.index + ".getCondition()", slots);
            operand.kids.add(matchNodeChild(env, operand, "getInput()", filter.getInput(), slots));
            return env.done(operand);
        }
        if (node instanceof LogicalJoin join) {
            var operand = open(env, "LogicalJoin", key);
            env.check("r_" + operand.index + ".getJoinType() == JoinRelType." + join.getJoinType().name());
            env.check("r_" + operand.index + ".getVariablesSet().size() == " + join.getVariablesSet().size());
            matchRexNode(env, join.getCondition(), "r_" + operand.index + ".getCondition()", slots);
            operand.kids.add(matchNodeChild(env, operand, "getLeft()", join.getLeft(), slots));
            operand.kids.add(matchNodeChild(env, operand, "getRight()", join.getRight(), slots));
            return env.done(operand);
        }
        throw new UnsupportedConstructException("A custom relation containing " + node.getRelTypeName()
                + " is not supported");
    }

    private Operand open(Env env, String type, RelRN key) {
        return key == null ? env.hidden(type, Operand.Kind.INPUTS) : env.operand(type, Operand.Kind.INPUTS, key);
    }

    private Operand matchNodeChild(Env env, Operand parent, String accessor, RelNode node, Seq<RelRN> slots) {
        if (parent.inline) {
            env.inlineSource = "r_" + parent.index + "." + accessor;
        }
        matchNode(env, node, slots, null);
        if (env.inlineSource != null) {
            throw new UnsupportedConstructException("A pattern inside a subquery is not supported: " + node);
        }
        return env.last;
    }

    private Env matchConcreteScan(Env env, TableScan scan, RelRN key) {
        var operand = key == null ? env.hidden(ANY_RELATION, Operand.Kind.ANY)
                : env.operand(ANY_RELATION, Operand.Kind.ANY, key);
        if (env.bindScan(tableName(scan), operand.index)) {
            var columns = scan.getRowType().getFieldList();
            env.check("r_" + operand.index + ".getRowType().getFieldCount() == " + columns.size());
            for (var i = 0; i < columns.size(); i++) {
                var type = columns.get(i).getType();
                if (type instanceof RelType.VarType variable) {
                    bindColumn(env, operand, i, variable);
                    continue;
                }
                env.check("r_" + operand.index + ".getRowType().getFieldList().get(" + i
                        + ").getType().getSqlTypeName() == org.apache.calcite.sql.type.SqlTypeName."
                        + type.getSqlTypeName().name());
                if (!type.isNullable() && env.operands.containsKey(operand.index)) {
                    env.prefilter(operand, "!r_" + operand.index + ".getRowType().getFieldList().get(" + i
                            + ").getType().isNullable()");
                }
            }
        }
        return env.done(operand);
    }

    private static String tableName(TableScan scan) {
        RelOptTable table = scan.getTable();
        return table.getQualifiedName().get(table.getQualifiedName().size() - 1);
    }

    private void matchRexNode(Env env, RexNode node, String expr, Seq<RelRN> slots) {
        var saved = env.rex;
        var local = "e_" + env.ids++;
        env.prelude.add("final RexNode " + local + " = " + expr + ";");
        env.rex = local;
        matchRexNode(env, node, slots);
        env.rex = saved;
    }

    private void matchRexNode(Env env, RexNode node, Seq<RelRN> slots) {
        var expr = env.rex;
        switch (node) {
            case RexInputRef ref -> matchRef(env, ref.getIndex());
            case RexLiteral literal -> matchLiteral(env, literal);
            case RexFieldAccess access when access.getReferenceExpr() instanceof RexCorrelVariable ->
                    env.check(expr + " instanceof RexFieldAccess && ((RexFieldAccess) " + expr
                            + ").getReferenceExpr() instanceof RexCorrelVariable && ((RexFieldAccess) " + expr
                            + ").getField().getIndex() == " + access.getField().getIndex());
            case RexSubQuery subQuery -> {
                env.check(expr + " instanceof RexSubQuery && ((RexSubQuery) " + expr
                        + ").getKind() == org.apache.calcite.sql.SqlKind." + subQuery.getKind().name()
                        + " && ((RexSubQuery) " + expr + ").getOperands().size() == " + subQuery.getOperands().size());
                for (var i = 0; i < subQuery.getOperands().size(); i++) {
                    matchRexNode(env, subQuery.getOperands().get(i), "((RexSubQuery) " + expr + ").getOperands().get("
                            + i + ")", slots);
                }
                env.inlineSource = "((RexSubQuery) " + expr + ").rel";
                matchNode(env, subQuery.rel, slots, null);
                if (env.inlineSource != null) {
                    throw new UnsupportedConstructException("The relation inside a subquery is not supported");
                }
            }
            case RexCall call -> {
                env.check(expr + " instanceof RexCall && ((RexCall) " + expr + ").getOperator() == "
                        + operatorReference(call.getOperator()) + " && ((RexCall) " + expr
                        + ").getOperands().size() == " + call.getOperands().size());
                for (var i = 0; i < call.getOperands().size(); i++) {
                    matchRexNode(env, call.getOperands().get(i), "((RexCall) " + expr + ").getOperands().get(" + i
                            + ")", slots);
                }
            }
            default -> throw new UnsupportedConstructException("The expression " + node + " is not supported in a "
                    + "custom relation");
        }
    }

    private void transformNode(Env env, RelNode node, Seq<RelRN> slots) {
        var slot = slotOf(node, slots);
        if (slot != null) {
            transform(env, slot);
            return;
        }
        if (node instanceof TableScan scan) {
            pushScan(env, tableName(scan));
            return;
        }
        if (node instanceof LogicalFilter filter) {
            transformNode(env, filter.getInput(), slots);
            var variables = declareCorrelations(env, filter.getVariablesSet(), "b.peek().getRowType()");
            var condition = "cond_" + env.ids++;
            var expression = rexNode(env, filter.getCondition(), 1, slots);
            env.body.add("final RexNode " + condition + " = " + expression + ";");
            env.body.add("b.push(LogicalFilter.create(b.build(), " + condition + variables + "));");
            return;
        }
        if (node instanceof LogicalJoin join) {
            transformNode(env, join.getLeft(), slots);
            transformNode(env, join.getRight(), slots);
            var variables = declareCorrelations(env, join.getVariablesSet(),
                    "b.getTypeFactory().createJoinType(b.peek(1).getRowType(), b.peek().getRowType())");
            var condition = "cond_" + env.ids++;
            var expression = rexNode(env, join.getCondition(), 2, slots);
            env.body.add("final RexNode " + condition + " = " + expression + ";");
            var right = "right_" + env.ids++;
            var left = "left_" + env.ids++;
            env.body.add("final RelNode " + right + " = b.build();");
            env.body.add("final RelNode " + left + " = b.build();");
            env.body.add("b.push(LogicalJoin.create(" + left + ", " + right + ", List.of(), " + condition
                    + ", " + (variables.isEmpty() ? "com.google.common.collect.ImmutableSet.of()"
                    : variables.substring(2)) + ", JoinRelType." + join.getJoinType().name() + "));");
            return;
        }
        throw new UnsupportedConstructException("A custom relation containing " + node.getRelTypeName()
                + " is not supported");
    }

    private String declareCorrelations(Env env, java.util.Set<org.apache.calcite.rel.core.CorrelationId> ids,
                                       String rowType) {
        if (ids.isEmpty()) {
            return "";
        }
        var names = new ArrayList<String>();
        for (var id : ids) {
            var number = env.ids++;
            env.body.add("final org.apache.calcite.rel.core.CorrelationId cor_" + number
                    + " = b.getCluster().createCorrel();");
            env.body.add("final RexNode cv_" + number + " = b.getRexBuilder().makeCorrel(" + rowType + ", cor_"
                    + number + ");");
            env.correlations.put(id, "cv_" + number);
            names.add("cor_" + number);
        }
        return ", com.google.common.collect.ImmutableSet.of(" + String.join(", ", names) + ")";
    }

    private String rexNode(Env env, RexNode node, int inputs, Seq<RelRN> slots) {
        switch (node) {
            case RexInputRef ref -> {
                return "CalciteRuntime.field(b, " + inputs + ", " + ref.getIndex() + ")";
            }
            case RexLiteral literal -> {
                if (literal.isNull()) {
                    return "b.getRexBuilder().makeNullLiteral(" + typeReference(env, literal.getType()) + ")";
                }
                return "b.literal(" + javaValue(literal) + ")";
            }
            case RexFieldAccess access when access.getReferenceExpr() instanceof RexCorrelVariable variable -> {
                var reference = env.correlations.get(variable.id);
                if (reference == null) {
                    throw new UnsupportedConstructException("A correlation variable is used outside its scope");
                }
                return "b.getRexBuilder().makeFieldAccess(" + reference + ", " + access.getField().getIndex() + ")";
            }
            case RexSubQuery subQuery -> {
                var operands = new ArrayList<String>();
                for (var operand : subQuery.getOperands()) {
                    operands.add(rexNode(env, operand, inputs, slots));
                }
                transformNode(env, subQuery.rel, slots);
                var sub = "sub_" + env.ids++;
                env.body.add("final RelNode " + sub + " = b.build();");
                return switch (subQuery.getKind()) {
                    case EXISTS -> "RexSubQuery.exists(" + sub + ")";
                    case SCALAR_QUERY -> "RexSubQuery.scalar(" + sub + ")";
                    case IN -> "RexSubQuery.in(" + sub + ", com.google.common.collect.ImmutableList.<RexNode>of("
                            + String.join(", ", operands) + "))";
                    default -> throw new UnsupportedConstructException("The subquery " + subQuery.getKind()
                            + " is not supported");
                };
            }
            case RexCall call -> {
                var operands = new ArrayList<String>();
                for (var operand : call.getOperands()) {
                    operands.add(rexNode(env, operand, inputs, slots));
                }
                return "b.call(" + operatorReference(call.getOperator())
                        + (operands.isEmpty() ? "" : ", " + String.join(", ", operands)) + ")";
            }
            default -> throw new UnsupportedConstructException("The expression " + node + " is not supported in a "
                    + "custom relation");
        }
    }

    private String rex(Env env, int inputs, RexRN target) {
        var savedInputs = env.inputs;
        env.inputs = inputs;
        transform(env, target);
        env.inputs = savedInputs;
        return env.out;
    }

    private Env emit(Env env, String expression) {
        env.out = expression;
        return env;
    }

    @Override
    public Env transformPred(Env env, RexRN.Pred pred) {
        return buildCall(env, pred.operator(), pred.sources());
    }

    @Override
    public Env transformProj(Env env, RexRN.Proj proj) {
        return buildCall(env, proj.operator(), proj.sources());
    }

    @Override
    public Env transformGroupBy(Env env, RexRN.GroupBy groupBy) {
        return buildCall(env, groupBy.operator(), groupBy.sources());
    }

    private Env buildCall(Env env, SqlOperator operator, Seq<RexRN> sources) {
        var inputs = env.inputs;
        var arguments = sources.map(source -> rex(env, inputs, source));
        var list = arguments.joinToString(", ");
        if (!isSymbol(operator)) {
            return emit(env, "b.call(" + operatorReference(operator) + (arguments.isEmpty() ? "" : ", " + list) + ")");
        }
        var symbol = env.symbols.get(operator.getName());
        if (symbol == null) {
            throw new UnsupportedConstructException("Symbol " + operator.getName()
                    + " is introduced by the rewrite but not bound by the matched pattern");
        }
        if (symbol.arity != arguments.size()) {
            throw new UnsupportedConstructException("Symbol " + operator.getName()
                    + " is applied to a different number of arguments than it was matched with");
        }
        if (symbol.operator) {
            return emit(env, "b.call(" + symbol.variable + (arguments.isEmpty() ? "" : ", " + list) + ")");
        }
        return emit(env, "CalciteRuntime.instantiate(" + symbol.variable + ", " + symbol.arguments + ", "
                + "new RexNode[]{" + list + "})");
    }

    @Override
    public Env transformCustom(Env env, RexRN custom) {
        var semantics = custom.semantics();
        if (semantics instanceof RexInputRef ref) {
            return emit(env, "CalciteRuntime.field(b, " + env.inputs + ", " + ref.getIndex() + ")");
        }
        if (semantics instanceof RexLiteral literal) {
            if (literal.isNull()) {
                return emit(env, "b.getRexBuilder().makeNullLiteral(" + typeReference(env, literal.getType()) + ")");
            }
            return emit(env, "b.literal(" + javaValue(literal) + ")");
        }
        if (semantics instanceof RexSubQuery subQuery && subQuery.getKind() == SqlKind.EXISTS) {
            var inputs = env.inputs;
            transform(env, subQueryBody(custom));
            env.inputs = inputs;
            var sub = "sub_" + env.ids++;
            env.body.add("final RelNode " + sub + " = b.build();");
            return emit(env, "RexSubQuery.exists(" + sub + ")");
        }
        return unimplementedTransform(env, custom);
    }

    private static String typeReference(Env env, RelDataType type) {
        if (!(type instanceof RelType.VarType)) {
            return "b.getTypeFactory().createSqlType(org.apache.calcite.sql.type.SqlTypeName."
                    + type.getSqlTypeName().name() + ")";
        }
        var id = typeId(type);
        if (env.boundTypes.contains(id)) {
            return "types.get(" + literal(id) + ")";
        }
        var reference = env.emptyTypes.get(id);
        if (reference == null) {
            throw new UnsupportedConstructException("The type " + id + " is introduced by the rewrite but not bound "
                    + "by the matched pattern");
        }
        return reference;
    }

    @Override
    public Env transformField(Env env, RexRN.Field field) {
        return emit(env, "CalciteRuntime.field(b, " + env.inputs + ", " + field.ordinal() + ")");
    }

    @Override
    public Env transformJoinField(Env env, RexRN.JoinField field) {
        return emit(env, "CalciteRuntime.field(b, " + env.inputs + ", " + field.ordinal() + ")");
    }

    @Override
    public Env transformAnd(Env env, RexRN.And and) {
        var inputs = env.inputs;
        return emit(env, "b.and(" + and.sources().map(s -> rex(env, inputs, s)).joinToString(", ") + ")");
    }

    @Override
    public Env transformOr(Env env, RexRN.Or or) {
        var inputs = env.inputs;
        return emit(env, "b.or(" + or.sources().map(s -> rex(env, inputs, s)).joinToString(", ") + ")");
    }

    @Override
    public Env transformNot(Env env, RexRN.Not not) {
        return emit(env, "b.not(" + rex(env, env.inputs, not.source()) + ")");
    }

    @Override
    public Env transformTrue(Env env, RexRN literal) {
        return emit(env, "b.literal(true)");
    }

    @Override
    public Env transformFalse(Env env, RexRN literal) {
        return emit(env, "b.literal(false)");
    }


    private static boolean isSymbol(SqlOperator operator) {
        return operator instanceof RuleBuilder.QedFunction || operator instanceof RuleBuilder.QedAggregateFunction;
    }

    private static final Map<SqlOperator, String> STANDARD_OPERATORS = new HashMap<>();

    static {
        for (var field : SqlStdOperatorTable.class.getFields()) {
            if (Modifier.isStatic(field.getModifiers()) && SqlOperator.class.isAssignableFrom(field.getType())) {
                try {
                    STANDARD_OPERATORS.putIfAbsent((SqlOperator) field.get(null), field.getName());
                } catch (IllegalAccessException e) {
                    throw new IllegalStateException(e);
                }
            }
        }
    }

    private static String operatorReference(SqlOperator operator) {
        var name = STANDARD_OPERATORS.get(operator);
        if (name == null) {
            throw new UnsupportedConstructException("Operator " + operator.getName()
                    + " is neither an uninterpreted symbol nor a standard Calcite operator");
        }
        return "SqlStdOperatorTable." + name;
    }

    private static String valuesArguments(Values values) {
        var names = values.getRowType().getFieldNames().stream().map(CalciteGenerator::literal)
                .collect(Collectors.joining(", "));
        var rows = new ArrayList<String>();
        for (var tuple : values.getTuples()) {
            for (var value : tuple) {
                rows.add(javaValue(value));
            }
        }
        return "new String[]{" + names + "}" + (rows.isEmpty() ? "" : ", " + String.join(", ", rows));
    }

    private static String javaValue(RexLiteral value) {
        if (value.isNull()) {
            throw new UnsupportedConstructException("Null constants in a values relation are not supported");
        }
        return switch (value.getType().getSqlTypeName()) {
            case BOOLEAN -> String.valueOf(value.getValueAs(Boolean.class));
            case TINYINT, SMALLINT, INTEGER -> String.valueOf(value.getValueAs(Integer.class));
            case BIGINT -> value.getValueAs(Long.class) + "L";
            case CHAR, VARCHAR -> literal(value.getValueAs(String.class));
            default -> throw new UnsupportedConstructException("Constants of type "
                    + value.getType().getSqlTypeName() + " in a values relation are not supported");
        };
    }

    private static String literal(String text) {
        return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    public static final class Operand {
        enum Kind { ANY, NONE, INPUTS }

        final int index;
        final String type;
        final Kind kind;
        final List<Operand> kids = new ArrayList<>();
        final List<String> predicates = new ArrayList<>();
        final boolean inline;

        Operand(int index, String type, Kind kind, boolean inline) {
            this.index = index;
            this.type = type;
            this.kind = kind;
            this.inline = inline;
        }

        String render() {
            var v = "s_" + index;
            var head = v + " -> " + v + ".operand(" + type + ".class)"
                    + (predicates.isEmpty() ? "" : ".predicate(r_" + index + " -> " + String.join(" && ", predicates) + ")");
            return head + switch (kind) {
                case ANY -> ".anyInputs()";
                case NONE -> ".noInputs()";
                case INPUTS -> kids.size() == 1 ? ".oneInput(" + kids.get(0).render() + ")"
                        : ".inputs(" + kids.stream().map(Operand::render).collect(Collectors.joining(", ")) + ")";
            };
        }
    }

    record Symbol(String variable, boolean operator, String arguments, String signature, int arity) {}

    public static final class Env {
        int ids = 0;
        int nextOperand = 0;
        Operand root;
        Operand last;
        RelRN afterRoot;
        final List<String> prelude = new ArrayList<>();
        final List<String> body = new ArrayList<>();
        final Map<RelRN, Integer> rels = new HashMap<>();
        final Map<Integer, Operand> operands = new HashMap<>();
        final Map<String, Integer> scans = new HashMap<>();
        final Map<String, Symbol> symbols = new HashMap<>();
        final Map<String, String> aggregates = new HashMap<>();
        final Map<org.apache.calcite.rel.core.CorrelationId, String> correlations = new HashMap<>();
        final java.util.Set<String> boundTypes = new java.util.HashSet<>();
        final Map<String, String> emptyTypes = new HashMap<>();
        int nextInline = 1000;
        String inlineSource;
        String rex;
        String out;
        int inputs = 1;

        Operand operand(String type, Operand.Kind kind, RelRN pattern) {
            var operand = hidden(type, kind);
            rels.put(pattern, operand.index);
            return operand;
        }

        Operand hidden(String type, Operand.Kind kind) {
            if (inlineSource == null) {
                var operand = new Operand(nextOperand++, type, kind, false);
                operands.put(operand.index, operand);
                prelude.add("final " + type + " r_" + operand.index + " = call.rel(" + operand.index + ");");
                return operand;
            }
            var source = inlineSource;
            inlineSource = null;
            var operand = new Operand(nextInline++, type, kind, true);
            if (!type.equals(ANY_RELATION)) {
                check(source + " instanceof " + type);
            }
            prelude.add("final " + type + " r_" + operand.index + " = (" + type + ") " + source + ";");
            return operand;
        }

        boolean bindScan(String name, int index) {
            var first = scans.putIfAbsent(name, index);
            if (first == null) {
                return true;
            }
            check("r_" + index + ".deepEquals(r_" + first + ")");
            return false;
        }

        private static final java.util.regex.Pattern LOCAL = java.util.regex.Pattern.compile(
                "r_(\\d+)\\.(getRowType\\(\\)\\.getFieldCount\\(\\) == \\d+|getJoinType\\(\\) == JoinRelType\\.\\w+"
                        + "|getProjects\\(\\)\\.size\\(\\) == \\d+|all == (true|false)|getInputs\\(\\)\\.size\\(\\) == \\d+"
                        + "|getTuples\\(\\)\\.isEmpty\\(\\))");

        void prefilter(Operand operand, String condition) {
            operand.predicates.add(condition);
        }

        void check(String condition) {
            var local = LOCAL.matcher(condition);
            if (local.matches() && operands.containsKey(Integer.parseInt(local.group(1)))) {
                prefilter(operands.get(Integer.parseInt(local.group(1))), condition);
                return;
            }
            prelude.add("if (!(" + condition + ")) {\n\t\t\treturn;\n\t\t}");
        }

        Env done(Operand operand) {
            last = operand;
            return this;
        }
    }
}
