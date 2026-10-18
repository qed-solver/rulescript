package org.qed.spark;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.spark.sql.catalyst.expressions.Alias$;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.NamedExpression;
import org.apache.spark.sql.catalyst.plans.logical.LocalRelation;
import org.apache.spark.sql.catalyst.plans.logical.LogicalPlan;
import org.apache.spark.sql.catalyst.plans.logical.Project;
import org.apache.spark.sql.catalyst.rules.Rule;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class RuleScriptRule extends Rule<LogicalPlan> {
    private final String name;
    private final JsonNode from;
    private final JsonNode to;

    private RuleScriptRule(String name, JsonNode from, JsonNode to) {
        this.name = name;
        this.from = from;
        this.to = to;
    }

    public static RuleScriptRule parse(String json) {
        try {
            var root = new ObjectMapper().readTree(json);
            return new RuleScriptRule(root.path("name").asText(), root.path("from"), root.path("to"));
        } catch (IOException e) {
            throw new IllegalArgumentException("Not a RuleScript rule", e);
        }
    }

    public static RuleScriptRule load(Path file) {
        try {
            return parse(Files.readString(file));
        } catch (IOException e) {
            throw new IllegalArgumentException("Cannot read " + file, e);
        }
    }

    public String name() {
        return name;
    }

    @Override
    public String ruleName() {
        return name;
    }

    @Override
    public LogicalPlan apply(LogicalPlan plan) {
        var withChildren = plan.mapChildren(child -> apply(child));
        var result = rewritten(withChildren);
        return result == null ? withChildren : result;
    }

    public LogicalPlan rewritten(LogicalPlan node) {
        var result = rewrite(node);
        return result == null || result.fastEquals(node) ? null : result;
    }

    public boolean matches(LogicalPlan node) {
        return rewrite(node) != null;
    }

    private LogicalPlan rewrite(LogicalPlan node) {
        var match = new Match();
        if (!match.rel(from, node) || !match.resolve()) return null;
        return restoreOutput(node, match.build(to), match);
    }

    private LogicalPlan restoreOutput(LogicalPlan old, LogicalPlan neu, Match match) {
        var oldCols = match.cols(from.path("id").asText());
        var newCols = match.cols(to.path("id").asText());
        if (oldCols.size() != newCols.size()) return null;
        var oldOutput = Catalyst.<Attribute>list(old.output());
        if (neu instanceof LocalRelation lr && lr.data().isEmpty() && oldOutput.size() == newCols.size()) return new LocalRelation(Catalyst.seq(oldOutput), Catalyst.<org.apache.spark.sql.catalyst.InternalRow>seq(List.of()), false, Match.noStream());
        var newOutput = Catalyst.<Attribute>list(neu.output());
        var same = oldOutput.size() == newOutput.size();
        for (int i = 0; same && i < oldOutput.size(); i++) same = oldOutput.get(i).exprId().equals(newOutput.get(i).exprId());
        if (same) return neu;
        var items = new ArrayList<NamedExpression>();
        for (var attr : oldOutput) {
            var position = -1;
            for (int i = 0; i < oldCols.size(); i++) if (oldCols.get(i).exprId().equals(attr.exprId())) position = i;
            if (position < 0) return null;
            var source = newCols.get(position);
            if (!source.dataType().equals(attr.dataType()) || !newOutput.stream().anyMatch(o -> o.exprId().equals(source.exprId()))) return null;
            items.add(source.exprId().equals(attr.exprId()) ? source : Alias$.MODULE$.apply((Expression) source, attr.name(), attr.exprId(), attr.qualifier(), scala.Option.empty(), Catalyst.<String>seq(List.of())));
        }
        return new Project(Catalyst.seq(items), neu);
    }
}
