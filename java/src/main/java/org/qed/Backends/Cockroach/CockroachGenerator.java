package org.qed.Backends.Cockroach;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import kala.collection.Seq;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.TableScan;
import org.apache.calcite.rel.logical.LogicalFilter;
import org.apache.calcite.rel.logical.LogicalJoin;
import org.apache.calcite.rex.RexCall;
import org.apache.calcite.rex.RexCorrelVariable;
import org.apache.calcite.rex.RexFieldAccess;
import org.apache.calcite.rex.RexInputRef;
import org.apache.calcite.rex.RexLiteral;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.rex.RexSubQuery;
import org.apache.calcite.sql.SqlKind;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.ArrayList;
import java.util.List;

public final class CockroachGenerator {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Map<String, String> ids = new LinkedHashMap<>();
    private final Map<String, List<TypeRef>> typeRefs = new LinkedHashMap<>();

    private record TypeRef(String of, int ordinal) { }

    public String generate(RRule rule) {
        return generate(rule, "normalize");
    }

    public String generate(RRule rule, String mode) {
        try {
            return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(new CockroachGenerator().rule(rule, mode))
                    + "\n";
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private ObjectNode rule(RRule rule, String mode) {
        if (!mode.equals("normalize")) {
            throw new UnsupportedOperationException("Unsupported mode: " + mode);
        }
        var node = MAPPER.createObjectNode();
        node.put("name", rule.name());
        node.put("mode", mode);
        collectTypeRefs(rule.before());
        var from = rel(rule.before(), true);
        var to = rel(rule.after(), true);
        if (countOp(from, "exists") > 1 || countOp(to, "exists") > 1 || countOp(from, "any") > 1 ||
                countOp(to, "exists") > countOp(from, "exists") + countOp(from, "any")) {
            throw new UnsupportedOperationException("Unsupported Exists private mapping: expected at most one matched Exists");
        }
        var positionalScans = new HashSet<String>();
        collectJoinFieldRelations(rel(rule.before(), false), positionalScans);
        collectJoinFieldRelations(rel(rule.after(), false), positionalScans);
        constrainPositionalScans(from, positionalScans);
        constrainPositionalScans(to, positionalScans);
        if (setCount(to) > 0 && setCount(from) == 0 &&
                (!to.path("op").asText().equals("set") || setCount(to) != 1
                        || rootOutputArity(from) < 0
                        || differentArity(rootOutputArity(from), rootOutputArity((ObjectNode) to.get("left")))
                        || differentArity(rootOutputArity(from), rootOutputArity((ObjectNode) to.get("right"))))) {
            throw new UnsupportedOperationException("Unsupported new set operation: root output column mapping");
        }
        var matchedEmptyIds = new HashSet<String>();
        collectEmptyIds(from, matchedEmptyIds);
        checkNewEmpty(to, true, matchedEmptyIds);
        computedKeys(from, true);
        computedKeys(to, false);
        var preds = new LinkedHashMap<String, PredicatePattern>();
        var projs = new HashSet<String>();
        var groups = new HashSet<String>();
        var aggs = new HashSet<String>();
        checkRel(from, true, preds, projs, groups, aggs);
        checkRel(to, false, preds, projs, groups, aggs);
        var matchedValues = new ArrayList<ObjectNode>();
        collectValues(from, matchedValues);
        mapNewValues(to, true, from, matchedValues);
        var matchedProjectIds = new HashSet<String>();
        collectProjectIds(from, matchedProjectIds);
        var keptProjectIds = new HashSet<String>();
        collectProjectIds(to, keptProjectIds);
        var replacedProjects = new ArrayList<ObjectNode>();
        collectProjects(from, replacedProjects);
        replacedProjects.removeIf(project -> keptProjectIds.contains(project.path("id").asText()));
        checkConcreteProjectMappings(to, true, from, matchedProjectIds, replacedProjects);
        node.set("from", from);
        node.set("to", to);
        return node;
    }

    private static void computedKeys(JsonNode node, boolean from) {
        if (node == null) {
            return;
        }
        if (node.isObject() && node.path("op").asText().equals("aggregate") && from) {
            for (var group : node.withArray("groups")) {
                if (group.path("op").asText().equals("proj")) {
                    ((ObjectNode) group).put("op", "group_key");
                }
            }
            for (var agg : node.withArray("aggs")) {
                for (var arg : agg.withArray("args")) {
                    if (arg.path("op").asText().equals("proj")) {
                        ((ObjectNode) arg).put("op", "group_key");
                    }
                }
            }
        }
        if (node.isObject() && node.path("op").asText().equals("project") && !from) {
            for (var expr : node.withArray("exprs")) {
                if (expr.path("op").asText().equals("group_key")) {
                    ((ObjectNode) expr).put("op", "proj");
                }
            }
        }
        for (var child : node) {
            computedKeys(child, from);
        }
    }

    private static void collectValues(ObjectNode rel, List<ObjectNode> found) {
        switch (rel.path("op").asText()) {
            case "values" -> found.add(rel);
            case "filter", "project", "aggregate" -> collectValues((ObjectNode) rel.get("input"), found);
            case "join", "set" -> {
                collectValues((ObjectNode) rel.get("left"), found);
                collectValues((ObjectNode) rel.get("right"), found);
            }
            default -> { }
        }
    }

    private static int countOp(JsonNode node, String op) {
        if (node == null) return 0;
        int count = node.isObject() && node.path("op").asText().equals(op) ? 1 : 0;
        for (var child : node) count += countOp(child, op);
        return count;
    }

    private static void mapNewValues(ObjectNode rel, boolean root, ObjectNode from, List<ObjectNode> matched) {
        switch (rel.path("op").asText()) {
            case "values" -> {
                if (matched.stream().anyMatch(v -> v.path("id").asText().equals(rel.path("id").asText()))) {
                    return;
                }
                int width = rel.withArray("rows").get(0).size();
                if (root) {
                    if (rootOutputArity(from) != width) {
                        throw new UnsupportedOperationException("Unsupported new root Values: output column mapping");
                    }
                    return;
                }
                ObjectNode source = null;
                List<Integer> mapping = null;
                for (var candidate : matched) {
                    if (candidate.withArray("rows").size() != rel.withArray("rows").size()) continue;
                    var ordinals = new ArrayList<Integer>();
                    var used = new HashSet<Integer>();
                    boolean valid = true;
                    for (int target = 0; target < width; target++) {
                        int match = -1;
                        for (int input = 0; input < candidate.withArray("rows").get(0).size(); input++) {
                            boolean same = true;
                            for (int row = 0; row < rel.withArray("rows").size(); row++) {
                                same &= rel.withArray("rows").get(row).get(target).equals(
                                        candidate.withArray("rows").get(row).get(input));
                            }
                            if (same) {
                                if (match >= 0) { valid = false; break; }
                                match = input;
                            }
                        }
                        if (match < 0 || !used.add(match)) { valid = false; break; }
                        ordinals.add(match);
                    }
                    if (valid) {
                        if (source != null) {
                            throw new UnsupportedOperationException("Unsupported ambiguous new Values column mapping");
                        }
                        source = candidate;
                        mapping = ordinals;
                    }
                }
                if (source == null) {
                    var projects = new ArrayList<ObjectNode>();
                    collectProjects(from, projects);
                    if (rel.withArray("rows").size() == 1) {
                        for (var project : projects) {
                            var ordinals = new ArrayList<Integer>();
                            var used = new HashSet<Integer>();
                            var valid = true;
                            for (int target = 0; target < width && valid; target++) {
                                int match = -1;
                                for (int index = 0; index < project.withArray("exprs").size(); index++) {
                                    if (project.withArray("exprs").get(index).equals(rel.withArray("rows").get(0).get(target))) {
                                        if (match >= 0) { valid = false; break; }
                                        match = index;
                                    }
                                }
                                if (match < 0 || !used.add(match)) { valid = false; }
                                else { ordinals.add(match); }
                            }
                            if (valid) {
                                if (source != null) {
                                    throw new UnsupportedOperationException("Unsupported ambiguous new Values column mapping");
                                }
                                source = project;
                                mapping = ordinals;
                            }
                        }
                    }
                }
                if (source == null) {
                    throw new UnsupportedOperationException("Unsupported new nested Values column mapping");
                }
                rel.put("reuse_cols_of", source.path("id").asText());
                var ordinals = rel.putArray("ordinals");
                mapping.forEach(ordinals::add);
            }
            case "filter", "project", "aggregate" -> mapNewValues((ObjectNode) rel.get("input"), false, from, matched);
            case "join", "set" -> {
                mapNewValues((ObjectNode) rel.get("left"), false, from, matched);
                mapNewValues((ObjectNode) rel.get("right"), false, from, matched);
            }
            default -> { }
        }
    }

    private void addTypeRefs(RelRN source, String of) {
        switch (source) {
            case RelRN.Scan scan -> typeRefs.computeIfAbsent(scan.ty().toString(), k -> new ArrayList<>())
                    .add(new TypeRef(of, 0));
            case RelRN.ScanMany scan -> {
                for (int i = 0; i < scan.tys().size(); i++) {
                    typeRefs.computeIfAbsent(scan.tys().get(i).toString(), k -> new ArrayList<>())
                            .add(new TypeRef(of, i));
                }
            }
            default -> { }
        }
    }

    private void collectTypeRefs(RelRN rel) {
        switch (rel) {
            case RelRN.Scan scan -> addTypeRefs(scan, id(scan));
            case RelRN.ScanMany scan -> addTypeRefs(scan, id(scan));
            case RelRN.Empty empty -> addTypeRefs(empty.sourceType(), id(empty));
            case RelRN.Filter filter -> collectTypeRefs(filter.source());
            case RelRN.Project project -> collectTypeRefs(project.source());
            case RelRN.ProjectMany project -> collectTypeRefs(project.source());
            case RelRN.Aggregate aggregate -> collectTypeRefs(aggregate.source());
            case RelRN.Join join -> { collectTypeRefs(join.left()); collectTypeRefs(join.right()); }
            case RelRN.Union set -> set.sources().forEach(this::collectTypeRefs);
            case RelRN.Intersect set -> set.sources().forEach(this::collectTypeRefs);
            case RelRN.Minus set -> set.sources().forEach(this::collectTypeRefs);
            default -> { }
        }
    }

    private static void collectJoinFieldRelations(JsonNode node, Set<String> ids) {
        if (node.isObject()) {
            if (node.path("op").asText().equals("joinfield")) {
                ids.add(node.path("left").asText());
                ids.add(node.path("right").asText());
            }
            node.elements().forEachRemaining(child -> collectJoinFieldRelations(child, ids));
        } else if (node.isArray()) {
            node.elements().forEachRemaining(child -> collectJoinFieldRelations(child, ids));
        }
    }

    private static void constrainPositionalScans(JsonNode node, Set<String> ids) {
        if (node.isObject()) {
            if (node.path("op").asText().equals("scan") && ids.contains(node.path("id").asText())
                    && !node.has("arity")) {
                ((ObjectNode) node).put("arity", 1);
            }
            node.elements().forEachRemaining(child -> constrainPositionalScans(child, ids));
        } else if (node.isArray()) {
            node.elements().forEachRemaining(child -> constrainPositionalScans(child, ids));
        }
    }

    private static int joinCount(ObjectNode rel) {
        return switch (rel.path("op").asText()) {
            case "filter", "project", "aggregate" -> joinCount((ObjectNode) rel.get("input"));
            case "join" -> 1 + joinCount((ObjectNode) rel.get("left")) + joinCount((ObjectNode) rel.get("right"));
            case "set" -> joinCount((ObjectNode) rel.get("left")) + joinCount((ObjectNode) rel.get("right"));
            default -> 0;
        };
    }

    private static int setCount(ObjectNode rel) {
        return switch (rel.path("op").asText()) {
            case "filter", "project", "aggregate" -> setCount((ObjectNode) rel.get("input"));
            case "join" -> setCount((ObjectNode) rel.get("left")) + setCount((ObjectNode) rel.get("right"));
            case "set" -> 1 + setCount((ObjectNode) rel.get("left")) + setCount((ObjectNode) rel.get("right"));
            default -> 0;
        };
    }

    private static void collectEmptyIds(ObjectNode rel, Set<String> ids) {
        switch (rel.path("op").asText()) {
            case "empty" -> ids.add(rel.path("id").asText());
            case "filter", "project", "aggregate" -> collectEmptyIds((ObjectNode) rel.get("input"), ids);
            case "join", "set" -> {
                collectEmptyIds((ObjectNode) rel.get("left"), ids);
                collectEmptyIds((ObjectNode) rel.get("right"), ids);
            }
            default -> { }
        }
    }

    private static void collectProjects(ObjectNode rel, List<ObjectNode> found) {
        switch (rel.path("op").asText()) {
            case "project" -> {
                found.add(rel);
                collectProjects((ObjectNode) rel.get("input"), found);
            }
            case "filter", "aggregate" -> collectProjects((ObjectNode) rel.get("input"), found);
            case "join", "set" -> {
                collectProjects((ObjectNode) rel.get("left"), found);
                collectProjects((ObjectNode) rel.get("right"), found);
            }
            default -> { }
        }
    }

    private static void collectProjectIds(ObjectNode rel, Set<String> ids) {
        switch (rel.path("op").asText()) {
            case "project" -> {
                ids.add(rel.path("id").asText());
                collectProjectIds((ObjectNode) rel.get("input"), ids);
            }
            case "filter", "aggregate" -> collectProjectIds((ObjectNode) rel.get("input"), ids);
            case "join", "set" -> {
                collectProjectIds((ObjectNode) rel.get("left"), ids);
                collectProjectIds((ObjectNode) rel.get("right"), ids);
            }
            default -> { }
        }
    }

    private static void checkConcreteProjectMappings(ObjectNode rel, boolean root, ObjectNode from,
                                                     Set<String> matchedIds, List<ObjectNode> replaced) {
        switch (rel.path("op").asText()) {
            case "project" -> {
                boolean concrete = false;
                for (var expr : rel.withArray("exprs")) {
                    concrete |= Set.of("scalar", "true", "false", "null", "int", "field_alias", "and_scalar", "or", "not")
                            .contains(expr.path("op").asText());
                }
                int rootArity = rootOutputArity(from);
                if (concrete && !matchedIds.contains(rel.path("id").asText())
                        && !(root && (rootArity < 0 || rootArity == rel.withArray("exprs").size()))) {
                    var candidates = replaced.stream()
                            .filter(c -> c.withArray("exprs").size() == rel.withArray("exprs").size()).toList();
                    if (candidates.size() == 1) {
                        rel.put("reuse_cols_of", candidates.get(0).path("id").asText());
                    } else if (root) {
                        throw new UnsupportedOperationException("Unsupported concrete Project output column mapping");
                    }
                }
                checkConcreteProjectMappings((ObjectNode) rel.get("input"), false, from, matchedIds, replaced);
            }
            case "filter" -> checkConcreteProjectMappings((ObjectNode) rel.get("input"), root, from, matchedIds,
                    replaced);
            case "aggregate" -> checkConcreteProjectMappings((ObjectNode) rel.get("input"), false, from, matchedIds,
                    replaced);
            case "join", "set" -> {
                checkConcreteProjectMappings((ObjectNode) rel.get("left"), false, from, matchedIds, replaced);
                checkConcreteProjectMappings((ObjectNode) rel.get("right"), false, from, matchedIds, replaced);
            }
            default -> { }
        }
    }

    private static boolean differentArity(int expected, int actual) {
        return actual >= 0 && actual != expected;
    }

    private static int rootOutputArity(ObjectNode rel) {
        return switch (rel.path("op").asText()) {
            case "filter" -> rootOutputArity((ObjectNode) rel.get("input"));
            case "project" -> rel.withArray("exprs").size();
            case "aggregate" -> rel.withArray("groups").size() + rel.withArray("aggs").size();
            case "empty", "scan" -> rel.path("arity").asInt(-1);
            case "values" -> rel.withArray("rows").isEmpty() ? -1 : rel.withArray("rows").get(0).size();
            case "join" -> {
                int left = rootOutputArity((ObjectNode) rel.get("left"));
                int right = rootOutputArity((ObjectNode) rel.get("right"));
                yield left < 0 || right < 0 ? -1 : left + right;
            }
            case "set" -> rootOutputArity((ObjectNode) rel.get("left"));
            default -> -1;
        };
    }

    private static void checkNewEmpty(ObjectNode rel, boolean root, Set<String> matchedIds) {
        switch (rel.path("op").asText()) {
            case "empty" -> {
                if (!root && !matchedIds.contains(rel.path("id").asText())) {
                    throw new UnsupportedOperationException("Unsupported new nested Empty: output column mapping");
                }
            }
            case "filter", "project", "aggregate" -> checkNewEmpty((ObjectNode) rel.get("input"), false, matchedIds);
            case "join", "set" -> {
                checkNewEmpty((ObjectNode) rel.get("left"), false, matchedIds);
                checkNewEmpty((ObjectNode) rel.get("right"), false, matchedIds);
            }
            default -> { }
        }
    }

    private static void checkRel(ObjectNode rel, boolean from, Map<String, PredicatePattern> preds, Set<String> projs,
                                 Set<String> groups, Set<String> aggs) {
        switch (rel.path("op").asText()) {
            case "scan" -> { }
            case "empty" -> { }
            case "values" -> {
                int width = -1;
                for (var row : rel.withArray("rows")) {
                    if (!row.isArray() || (width >= 0 && row.size() != width)) {
                        throw new UnsupportedOperationException("Unsupported Values row width");
                    }
                    for (var cell : row) {
                        if (!Set.of("int", "true", "false").contains(cell.path("op").asText())) {
                            throw new UnsupportedOperationException("Unsupported Values literal: " + cell);
                        }
                    }
                    width = row.size();
                }
                if (width < 0) throw new UnsupportedOperationException("Unsupported empty concrete Values");
            }
            case "filter" -> {
                checkCond((ObjectNode) rel.get("cond"), from, preds, projs);
                checkRel((ObjectNode) rel.get("input"), from, preds, projs, groups, aggs);
            }
            case "project" -> {
                var fields = new HashSet<String>();
                for (var expr : rel.withArray("exprs")) {
                    if (expr.path("op").asText().equals("proj")) {
                        checkProj((ObjectNode) expr, from, projs);
                    } else if (expr.path("op").asText().equals("field")) {
                        if (!fields.add(expr.toString())) {
                            ((ObjectNode) expr).put("op", "field_alias");
                        }
                    } else if (Set.of("scalar", "true", "false", "null", "int", "and", "and_scalar", "or", "not")
                            .contains(expr.path("op").asText())) {
                        checkScalar((ObjectNode) expr, from, preds, projs);
                    } else {
                        throw new UnsupportedOperationException("Unsupported project expression: "
                                + expr.path("op").asText());
                    }
                }
                checkRel((ObjectNode) rel.get("input"), from, preds, projs, groups, aggs);
            }
            case "join" -> {
                if (!Set.of("inner", "left", "right", "full", "semi", "anti")
                        .contains(rel.path("type").asText())) {
                    throw new UnsupportedOperationException("Unsupported join type: " + rel.path("type").asText());
                }
                if (rel.path("apply").asBoolean() && !Set.of("inner", "semi", "left", "anti").contains(rel.path("type").asText())) {
                    throw new UnsupportedOperationException("Unsupported Apply join type: " + rel.path("type").asText());
                }
                checkCond((ObjectNode) rel.get("cond"), from, preds, projs);
                checkRel((ObjectNode) rel.get("left"), from, preds, projs, groups, aggs);
                checkRel((ObjectNode) rel.get("right"), from, preds, projs, groups, aggs);
            }
            case "set" -> {
                if (!Set.of("union", "intersect", "except").contains(rel.path("type").asText())) {
                    throw new UnsupportedOperationException("Unsupported set type: " + rel.path("type").asText());
                }
                checkRel((ObjectNode) rel.get("left"), from, preds, projs, groups, aggs);
                checkRel((ObjectNode) rel.get("right"), from, preds, projs, groups, aggs);
            }
            case "aggregate" -> {
                for (var group : rel.withArray("groups")) {
                    switch (group.path("op").asText()) {
                        case "field" -> { }
                        case "group_key" -> {
                            var name = group.path("name").asText();
                            if (from && !groups.add(name)) {
                                throw new UnsupportedOperationException("Unsupported repeated grouping symbol: " + name);
                            }
                            if (from) {
                                projs.add(name);
                            }
                            if (!from && !groups.contains(name)) {
                                throw new UnsupportedOperationException("Unsupported unbound grouping symbol: " + name);
                            }
                            checkArgs((ObjectNode) group, from, projs);
                        }
                        default -> throw new UnsupportedOperationException("Unsupported grouping key: "
                                + group.path("op").asText());
                    }
                }
                for (var agg : rel.withArray("aggs")) {
                    var name = agg.path("name").asText();
                    if (from && !aggs.add(name)) {
                        throw new UnsupportedOperationException("Unsupported repeated aggregate symbol: " + name);
                    }
                    if (!from && !aggs.contains(name)) {
                        throw new UnsupportedOperationException("Unsupported unbound aggregate symbol: " + name);
                    }
                    for (var arg : agg.withArray("args")) {
                        if (arg.path("op").asText().equals("group_key")) {
                            var keyName = arg.path("name").asText();
                            if (from) {
                                groups.add(keyName);
                                projs.add(keyName);
                            }
                            if (!from && !groups.contains(keyName)) {
                                throw new UnsupportedOperationException("Unsupported unbound grouping symbol: " + keyName);
                            }
                            checkArgs((ObjectNode) arg, from, projs);
                        }
                    }
                    checkArgs((ObjectNode) agg, from, projs);
                }
                checkRel((ObjectNode) rel.get("input"), from, preds, projs, groups, aggs);
            }
            default -> throw new UnsupportedOperationException("Unsupported relational operator: "
                    + rel.path("op").asText());
        }
    }

    private static void checkCond(ObjectNode cond, boolean from, Map<String, PredicatePattern> preds, Set<String> projs) {
        switch (cond.path("op").asText()) {
            case "true", "false" -> { }
            case "pred" -> {
                checkPredicateSymbol(cond, from, preds, false);
                checkArgs(cond, from, projs);
            }
            case "and" -> {
                for (var arg : cond.withArray("args")) {
                    if (arg.path("op").asText().equals("and")) {
                        cond.put("op", "and_scalar");
                        checkScalar(cond, from, preds, projs);
                        return;
                    }
                }
                for (var arg : cond.withArray("args")) {
                    booleanNull((ObjectNode) arg);
                    checkCond((ObjectNode) arg, from, preds, projs);
                }
            }
            case "scalar", "not", "or", "and_scalar", "in_single", "not_in_single", "exists", "any", "field" ->
                    checkScalar(cond, from, preds, projs);
            case "null" -> {
                if (!cond.path("type").asText().equals("bool")) {
                    throw new UnsupportedOperationException("Unsupported non-boolean NULL filter: "
                            + cond.path("type").asText());
                }
                checkScalar(cond, from, preds, projs);
            }
            default -> throw new UnsupportedOperationException("Unsupported condition expression: "
                    + cond.path("op").asText());
        }
    }

    private static void checkScalar(ObjectNode expr, boolean from, Map<String, PredicatePattern> preds, Set<String> projs) {
        switch (expr.path("op").asText()) {
            case "field", "joinfield", "true", "false", "int" -> { }
            case "exists" -> checkRel((ObjectNode) expr.get("input"), from, preds, projs,
                    new HashSet<>(), new HashSet<>());
            case "any" -> {
                if (!expr.path("cmp").asText().equals("=")) {
                    throw new UnsupportedOperationException("Unsupported quantified comparison: "
                            + expr.path("cmp").asText());
                }
                checkScalar((ObjectNode) expr.get("scalar"), from, preds, projs);
                checkRel((ObjectNode) expr.get("input"), from, preds, projs, new HashSet<>(), new HashSet<>());
            }
            case "null" -> {
                if (expr.path("type").asText().equals("same_as")) {
                    if (expr.withArray("refs").isEmpty()) {
                        throw new UnsupportedOperationException("Unsupported NULL type reference: empty refs");
                    }
                } else if (!Set.of("bool", "int").contains(expr.path("type").asText())) {
                    throw new UnsupportedOperationException("Unsupported NULL literal type: " + expr.path("type").asText());
                }
            }
            case "proj" -> checkProj(expr, from, projs);
            case "pred" -> {
                if (from) {
                    for (var arg : expr.withArray("args")) {
                        if (arg.path("op").asText().equals("proj")) {
                            throw new UnsupportedOperationException("Unsupported scalar predicate argument: proj");
                        }
                    }
                }
                checkPredicateSymbol(expr, from, preds, true);
                checkArgs(expr, from, projs);
            }
            case "not" -> {
                booleanNull((ObjectNode) expr.get("arg"));
                checkScalar((ObjectNode) expr.get("arg"), from, preds, projs);
            }
            case "or" -> {
                if (expr.withArray("args").size() < 2) {
                    throw new UnsupportedOperationException("Unsupported or arity: " + expr.withArray("args").size());
                }
                for (var arg : expr.withArray("args")) {
                    booleanNull((ObjectNode) arg);
                    checkScalar((ObjectNode) arg, from, preds, projs);
                }
            }
            case "and", "and_scalar" -> {
                expr.put("op", "and_scalar");
                if (expr.withArray("args").size() < 2) {
                    throw new UnsupportedOperationException("Unsupported scalar and arity: " + expr.withArray("args").size());
                }
                for (var arg : expr.withArray("args")) {
                    booleanNull((ObjectNode) arg);
                    checkScalar((ObjectNode) arg, from, preds, projs);
                }
            }
            case "in_single", "not_in_single" -> {
                if (expr.withArray("args").size() != 2) {
                    throw new UnsupportedOperationException("Unsupported singleton membership arity: "
                            + expr.withArray("args").size());
                }
                for (var arg : expr.withArray("args")) {
                    checkScalar((ObjectNode) arg, from, preds, projs);
                }
            }
            case "scalar" -> {
                var operator = expr.path("operator").asText();
                if (!Set.of("=", "<>", "<", "<=", ">", ">=", "IS NOT DISTINCT FROM",
                        "IS NULL", "IS NOT NULL", "IS NOT FALSE", "IS TRUE", "IS NOT TRUE", "-", "+").contains(operator)) {
                    throw new UnsupportedOperationException("Unsupported concrete operator: " + operator);
                }
                int arity = Set.of("IS NULL", "IS NOT NULL", "IS NOT FALSE", "IS TRUE", "IS NOT TRUE").contains(operator)
                        ? 1 : operator.equals("-") ? expr.withArray("args").size() : 2;
                if (expr.withArray("args").size() != arity || arity < 1 || arity > 2) {
                    throw new UnsupportedOperationException("Unsupported scalar arity for " + operator + ": "
                            + expr.withArray("args").size());
                }
                for (var arg : expr.withArray("args")) {
                    checkScalar((ObjectNode) arg, from, preds, projs);
                }
            }
            default -> throw new UnsupportedOperationException("Unsupported scalar operand: "
                    + expr.path("op").asText());
        }
    }

    private record PredicatePattern(JsonNode args, boolean scalar) { }

    private static void booleanNull(ObjectNode node) {
        if (node.path("op").asText().equals("null")) {
            node.put("type", "bool");
        }
    }

    private static void checkPredicateSymbol(ObjectNode expr, boolean from,
                                             Map<String, PredicatePattern> preds, boolean scalar) {
        var name = expr.path("name").asText();
        if (from) {
            var previous = preds.putIfAbsent(name, new PredicatePattern(expr.path("args"), scalar));
            if (previous != null && (!sameArguments(previous.args(), expr.path("args"))
                    || previous.scalar() != scalar)) {
                throw new UnsupportedOperationException("Unsupported repeated predicate symbol with different arguments or context: "
                        + name);
            }
        } else if (!preds.containsKey(name)) {
            throw new UnsupportedOperationException("Unsupported unbound predicate symbol: " + name);
        }
    }

    private static boolean sameArguments(JsonNode left, JsonNode right) {
        if (left.isObject() && right.isObject()) {
            var names = new HashSet<String>();
            left.fieldNames().forEachRemaining(names::add);
            right.fieldNames().forEachRemaining(names::add);
            for (var name : names) {
                if (Set.of("of", "left", "right", "rels", "id").contains(name)) {
                    continue;
                }
                if (!sameArguments(left.path(name), right.path(name))) {
                    return false;
                }
            }
            return true;
        }
        if (left.isArray() && right.isArray()) {
            if (left.size() != right.size()) {
                return false;
            }
            for (int i = 0; i < left.size(); i++) {
                if (!sameArguments(left.get(i), right.get(i))) {
                    return false;
                }
            }
            return true;
        }
        return left.equals(right);
    }

    private static void checkProj(ObjectNode proj, boolean from, Set<String> projs) {
        if (from) {
            projs.add(proj.path("name").asText());
        }
        if (!from && !projs.contains(proj.path("name").asText())) {
            throw new UnsupportedOperationException("Unsupported unbound projection symbol: "
                    + proj.path("name").asText());
        }
        checkArgs(proj, from, projs);
    }

    private static void checkArgs(ObjectNode symbol, boolean from, Set<String> projs) {
        for (var arg : symbol.withArray("args")) {
            switch (arg.path("op").asText()) {
                case "field", "joinfield", "row" -> { }
                case "group_key" -> {
                    if (!symbol.path("op").asText().equals("agg")) {
                        throw new UnsupportedOperationException("Unsupported symbol argument: group_key");
                    }
                }
                case "proj" -> {
                    if (from && !symbol.path("op").asText().equals("pred") && !symbol.path("op").asText().equals("proj")) {
                        throw new UnsupportedOperationException("Unsupported bound symbol argument: proj");
                    }
                    checkProj((ObjectNode) arg, from, projs);
                }
                case "true", "false", "int" -> {
                    if (from) {
                        throw new UnsupportedOperationException("Unsupported symbol argument: " + arg.path("op").asText());
                    }
                }
                default -> throw new UnsupportedOperationException("Unsupported symbol argument: "
                        + arg.path("op").asText());
            }
        }
    }

    private String id(RelRN rel) {
        var key = rel instanceof RelRN.Empty ? rel.toString() : rel(rel, false).toString();
        return ids.computeIfAbsent(key, k -> "r" + ids.size());
    }

    private ObjectNode rel(RelRN rel, boolean tagged) {
        var node = MAPPER.createObjectNode();
        switch (rel) {
            case RelRN.Scan scan -> {
                node.put("op", "scan");
                node.put("name", scan.name());
                if (scan.unique()) {
                    node.put("unique", true);
                }
                if (scan.unique() || !scan.ty().isNullable()) {
                    node.put("arity", 1);
                }
                if (!scan.ty().isNullable()) {
                    node.putArray("non_null").add(0);
                }
            }
            case RelRN.ScanMany scan -> {
                node.put("op", "scan");
                node.put("name", scan.name());
                node.put("arity", scan.tys().size());
                var nonNull = node.putArray("non_null");
                for (int i = 0; i < scan.tys().size(); i++) {
                    if (!scan.tys().get(i).isNullable()) {
                        nonNull.add(i);
                    }
                }
                if (nonNull.isEmpty()) {
                    node.remove("non_null");
                }
            }
            case RelRN.Empty empty -> {
                node.put("op", "empty");
                node.put("arity", empty.sourceType().semantics().getRowType().getFieldCount());
            }
            case RelRN.Filter filter -> {
                node.put("op", "filter");
                node.set("cond", rex(filter.cond(), tagged, filter.source()));
                node.set("input", rel(filter.source(), tagged));
            }
            case RelRN.Project project -> {
                node.put("op", "project");
                node.set("exprs", MAPPER.createArrayNode().add(rex(project.map(), tagged, project.source())));
                node.set("input", rel(project.source(), tagged));
            }
            case RelRN.ProjectMany project -> {
                node.put("op", "project");
                var exprs = MAPPER.createArrayNode();
                project.exprs().forEach(e -> exprs.add(rex(e, tagged, project.source())));
                node.set("exprs", exprs);
                node.set("input", rel(project.source(), tagged));
            }
            case RelRN.Join join -> {
                if (!(join.ty() instanceof RelRN.Join.JoinType.ConcreteJoinType concrete)) {
                    throw unsupported(rel);
                }
                node.put("op", "join");
                node.put("type", concrete.type().name().toLowerCase());
                node.set("cond", rex(join.cond(), tagged, join));
                node.set("left", rel(join.left(), tagged));
                node.set("right", rel(join.right(), tagged));
            }
            case RelRN.Correlate corr -> {
                var type = corr.ty().name().toLowerCase();
                if (!Set.of("inner", "semi", "left", "anti").contains(type)) {
                    throw new UnsupportedOperationException("Unsupported Correlate join type: " + type);
                }
                node.put("op", "join");
                node.put("type", type);
                node.put("apply", true);
                node.set("cond", rex(new RexRN.Pred(corr.cond(), corr.left().joinFields(corr.right())), tagged, corr));
                node.set("left", rel(corr.left(), tagged));
                node.set("right", rel(corr.right(), tagged));
            }
            case RelRN.Union union -> set(node, "union", union.all(), union.sources(), tagged,
                    sources -> new RelRN.Union(union.all(), sources));
            case RelRN.Intersect intersect -> set(node, "intersect", intersect.all(), intersect.sources(), tagged,
                    sources -> new RelRN.Intersect(intersect.all(), sources));
            case RelRN.Minus minus -> set(node, "except", minus.all(), minus.sources(), tagged,
                    sources -> new RelRN.Minus(minus.all(), sources));
            case RelRN.Aggregate aggregate -> {
                node.put("op", "aggregate");
                var groups = MAPPER.createArrayNode();
                aggregate.groupSet().forEach(g -> groups.add(rex(g, tagged, aggregate.source())));
                node.set("groups", groups);
                var aggs = MAPPER.createArrayNode();
                aggregate.aggCalls().forEach(a -> aggs.add(aggCall(a, tagged, aggregate.source())));
                node.set("aggs", aggs);
                node.set("input", rel(aggregate.source(), tagged));
            }
            default -> {
                if (!(rel.semantics() instanceof org.apache.calcite.rel.core.Values values)) {
                    return lifted(rel, tagged);
                }
                node.put("op", "values");
                var rows = node.putArray("rows");
                for (var tuple : values.getTuples()) {
                    var row = rows.addArray();
                    for (var literal : tuple) {
                        if (literal.isNull()) {
                            throw new UnsupportedOperationException("Unsupported Values literal: " + literal);
                        }
                        var cell = row.addObject();
                        if (literal.getType().getSqlTypeName() == org.apache.calcite.sql.type.SqlTypeName.INTEGER) {
                            cell.put("op", "int");
                            cell.put("value", literal.getValueAs(Integer.class));
                        } else if (literal.getType().getSqlTypeName() == org.apache.calcite.sql.type.SqlTypeName.BOOLEAN) {
                            cell.put("op", Boolean.TRUE.equals(literal.getValueAs(Boolean.class)) ? "true" : "false");
                        } else {
                            throw new UnsupportedOperationException("Unsupported Values literal: " + literal);
                        }
                    }
                }
            }
        }
        if (tagged) {
            node.put("id", id(rel));
        }
        return node;
    }

    private void set(ObjectNode node, String type, boolean all, Seq<RelRN> sources, boolean tagged,
                     java.util.function.Function<Seq<RelRN>, RelRN> make) {
        if (sources.size() == 1) {
            node.setAll(rel(sources.get(0), tagged));
            return;
        }
        if (sources.size() > 2) {
            var left = make.apply(sources.take(sources.size() - 1));
            node.setAll(rel(make.apply(Seq.of(left, sources.get(sources.size() - 1))), tagged));
            return;
        }
        if (sources.isEmpty()) {
            throw new UnsupportedOperationException("Unsupported set operation arity: 0");
        }
        node.put("op", "set");
        node.put("type", type);
        node.put("all", all);
        node.set("left", rel(sources.get(0), tagged));
        node.set("right", rel(sources.get(1), tagged));
    }

    private ObjectNode aggCall(RelRN.AggCall agg, boolean tagged, RelRN context) {
        if (agg.operator() != null && !(agg.operator() instanceof org.qed.RuleBuilder.QedAggregateFunction)) {
            throw new UnsupportedOperationException("Unsupported concrete aggregate operator: "
                    + agg.operator().getName());
        }
        var node = MAPPER.createObjectNode();
        node.put("op", "agg");
        node.put("name", agg.name());
        node.put("distinct", agg.distinct());
        node.set("args", arguments(agg.operands(), tagged, false, context));
        return node;
    }

    private ObjectNode rex(RexRN rex, boolean tagged, RelRN context) {
        var node = MAPPER.createObjectNode();
        switch (rex) {
            case RexRN.Pred pred -> {
                if (pred.operator() instanceof org.qed.RuleBuilder.QedFunction) {
                    symbol(node, "pred", pred.operator().getName(), pred.sources(), tagged, context);
                } else {
                    node.put("op", "scalar");
                    node.put("operator", pred.operator().getName());
                    var args = node.putArray("args");
                    pred.sources().forEach(s -> args.add(rex(s, tagged, context)));
                }
            }
            case RexRN.Proj proj -> {
                if (proj.operator().getKind() == org.apache.calcite.sql.SqlKind.MINUS_PREFIX
                        || proj.operator().getKind() == org.apache.calcite.sql.SqlKind.MINUS) {
                    node.put("op", "scalar");
                    node.put("operator", "-");
                    var args = node.putArray("args");
                    proj.sources().forEach(s -> args.add(rex(s, tagged, context)));
                } else if (!(proj.operator() instanceof org.qed.RuleBuilder.QedFunction)) {
                    node.put("op", "scalar");
                    node.put("operator", proj.operator().getName());
                    var args = node.putArray("args");
                    proj.sources().forEach(s -> args.add(rex(s, tagged, context)));
                } else {
                    symbol(node, "proj", proj.operator().getName(), proj.sources(), tagged, context);
                }
            }
            case RexRN.GroupBy group -> {
                if (!(group.operator() instanceof org.qed.RuleBuilder.QedFunction)) {
                    throw new UnsupportedOperationException("Unsupported concrete grouping operator: "
                            + group.operator().getName());
                }
                symbol(node, "group_key", group.operator().getName(), group.sources(), tagged, context);
            }
            case RexRN.And and -> list(node, "and", and.sources(), tagged, context);
            case RexRN.Or or -> {
                if (or.sources().size() == 1) {
                    singletonMembership(node, "in_single", or, tagged, context);
                } else {
                    list(node, "or", or.sources(), tagged, context);
                }
            }
            case RexRN.Not not -> {
                if (not.source() instanceof RexRN.Or or && or.sources().size() == 1) {
                    singletonMembership(node, "not_in_single", or, tagged, context);
                } else {
                    node.put("op", "not");
                    node.set("arg", rex(not.source(), tagged, context));
                }
            }
            case RexRN.True ignored -> node.put("op", "true");
            case RexRN.False ignored -> node.put("op", "false");
            case RexRN.Field field -> {
                node.put("op", "field");
                node.put("ordinal", field.ordinal());
                node.put("of", id(field.source()));
            }
            case RexRN.JoinField field -> {
                if (tagged) {
                    node.put("op", "field");
                    node.put("ordinal", field.ordinal());
                    node.put("of", id(context));
                } else {
                    node.put("op", "joinfield");
                    node.put("ordinal", field.ordinal());
                    node.put("left", id(field.left()));
                    node.put("right", id(field.right()));
                }
            }
            default -> {
                if (rex.semantics() instanceof org.apache.calcite.rex.RexInputRef ref) {
                    node.put("op", "field");
                    node.put("ordinal", ref.getIndex());
                    node.put("of", id(context));
                } else if (endsInExists(rex.semantics())) {
                    var components = rex.getClass().getRecordComponents();
                    RelRN body = null;
                    var guards = new ArrayList<ObjectNode>();
                    try {
                        for (var component : components == null ? new java.lang.reflect.RecordComponent[0] : components) {
                            component.getAccessor().setAccessible(true);
                            var value = component.getAccessor().invoke(rex);
                            if (value instanceof RelRN rel && body == null) {
                                body = rel;
                            } else if (value instanceof RexRN guard && !guard.semantics().isAlwaysTrue()) {
                                guards.add(rex(guard, tagged, context));
                            } else if (!(value instanceof RexRN)) {
                                body = null;
                                break;
                            }
                        }
                    } catch (ReflectiveOperationException e) {
                        throw new UnsupportedOperationException("Unsupported Exists relation access: "
                                + constructName(rex.getClass()), e);
                    }
                    if (body == null) {
                        throw new UnsupportedOperationException("Unsupported Exists relation component: "
                                + constructName(rex.getClass()));
                    }
                    var exists = MAPPER.createObjectNode();
                    exists.put("op", "exists");
                    exists.set("input", rel(body, tagged));
                    if (guards.isEmpty()) {
                        node.setAll(exists);
                    } else {
                        node.put("op", "and");
                        var args = node.putArray("args");
                        guards.forEach(args::add);
                        args.add(exists);
                    }
                } else if (rex.semantics() instanceof org.apache.calcite.rex.RexLiteral literal && literal.isNull()) {
                    node.put("op", "null");
                    var sqlType = literal.getType().getSqlTypeName();
                    if (sqlType == org.apache.calcite.sql.type.SqlTypeName.BOOLEAN) {
                        node.put("type", "bool");
                    } else if (sqlType == org.apache.calcite.sql.type.SqlTypeName.INTEGER) {
                        node.put("type", "int");
                    } else if (literal.getType() instanceof org.qed.RelType.VarType) {
                        var refs = typeRefs.get(literal.getType().toString());
                        if (refs == null || refs.isEmpty()) {
                            throw new UnsupportedOperationException("Unsupported unbound NULL type variable: "
                                    + literal.getType());
                        }
                        node.put("type", "same_as");
                        var out = node.putArray("refs");
                        for (var ref : refs) {
                            out.addObject().put("of", ref.of()).put("ordinal", ref.ordinal());
                        }
                    } else {
                        throw new UnsupportedOperationException("Unsupported NULL literal type: "
                                + literal.getType());
                    }
                } else if (rex.semantics() instanceof org.apache.calcite.rex.RexLiteral literal
                        && literal.getType().getSqlTypeName() == org.apache.calcite.sql.type.SqlTypeName.INTEGER) {
                    node.put("op", "int");
                    node.put("value", literal.getValueAs(Integer.class));
                } else {
                    throw new UnsupportedOperationException("Unsupported expression construct: "
                            + constructName(rex.getClass()));
                }
            }
        }
        return node;
    }

    private static boolean endsInExists(org.apache.calcite.rex.RexNode node) {
        if (node instanceof org.apache.calcite.rex.RexCall call && call.getKind() == org.apache.calcite.sql.SqlKind.AND) {
            node = call.getOperands().get(call.getOperands().size() - 1);
        }
        return node instanceof org.apache.calcite.rex.RexSubQuery subquery
                && subquery.getKind() == org.apache.calcite.sql.SqlKind.EXISTS;
    }

    private record Lift(List<RelRN> rels) { }

    private final Map<org.apache.calcite.rel.core.CorrelationId, RelNode> correlations = new HashMap<>();

    private ObjectNode lifted(RelRN custom, boolean tagged) {
        var rels = new ArrayList<RelRN>();
        var components = custom.getClass().getRecordComponents();
        if (components != null) {
            for (var component : components) {
                if (!RelRN.class.isAssignableFrom(component.getType())) {
                    continue;
                }
                try {
                    var accessor = component.getAccessor();
                    accessor.setAccessible(true);
                    rels.add((RelRN) accessor.invoke(custom));
                } catch (ReflectiveOperationException e) {
                    throw new UnsupportedOperationException("Unsupported relation component: "
                            + constructName(custom.getClass()), e);
                }
            }
        }
        return liftRel(custom.semantics(), new Lift(rels), tagged);
    }

    private String liftId(RelNode node, Lift lift) {
        return ids.computeIfAbsent(liftRel(node, lift, false).toString(), k -> "r" + ids.size());
    }

    private ObjectNode liftRel(RelNode node, Lift lift, boolean tagged) {
        for (var slot : lift.rels()) {
            if (slot.semantics().deepEquals(node)) {
                return rel(slot, tagged);
            }
        }
        var out = MAPPER.createObjectNode();
        switch (node) {
            case TableScan scan -> {
                var table = scan.getTable().getQualifiedName();
                out.put("op", "scan");
                out.put("name", table.get(table.size() - 1));
                var columns = scan.getRowType().getFieldList();
                out.put("arity", columns.size());
                var nonNull = MAPPER.createArrayNode();
                for (int i = 0; i < columns.size(); i++) {
                    if (!columns.get(i).getType().isNullable()) {
                        nonNull.add(i);
                    }
                }
                if (!nonNull.isEmpty()) {
                    out.set("non_null", nonNull);
                }
            }
            case LogicalFilter filter -> {
                filter.getVariablesSet().forEach(id -> correlations.put(id, filter.getInput()));
                out.put("op", "filter");
                out.set("cond", liftRex(filter.getCondition(), filter.getInput(), lift, tagged));
                out.set("input", liftRel(filter.getInput(), lift, tagged));
            }
            case LogicalJoin join -> {
                join.getVariablesSet().forEach(id -> correlations.put(id, join));
                out.put("op", "join");
                out.put("type", join.getJoinType().name().toLowerCase());
                out.set("cond", liftRex(join.getCondition(), join, lift, tagged));
                out.set("left", liftRel(join.getLeft(), lift, tagged));
                out.set("right", liftRel(join.getRight(), lift, tagged));
            }
            default -> throw new UnsupportedOperationException("Unsupported relational operator: "
                    + node.getRelTypeName());
        }
        if (tagged) {
            out.put("id", liftId(node, lift));
        }
        return out;
    }

    private ObjectNode liftField(RelNode context, int ordinal, Lift lift, boolean tagged) {
        var out = MAPPER.createObjectNode();
        if (context instanceof LogicalJoin join && !tagged) {
            out.put("op", "joinfield");
            out.put("ordinal", ordinal);
            out.put("left", liftId(join.getLeft(), lift));
            out.put("right", liftId(join.getRight(), lift));
        } else {
            out.put("op", "field");
            out.put("ordinal", ordinal);
            out.put("of", liftId(context, lift));
        }
        return out;
    }

    private ObjectNode liftRex(RexNode rex, RelNode context, Lift lift, boolean tagged) {
        var out = MAPPER.createObjectNode();
        switch (rex) {
            case RexInputRef ref -> {
                return liftField(context, ref.getIndex(), lift, tagged);
            }
            case RexFieldAccess access when access.getReferenceExpr() instanceof RexCorrelVariable variable -> {
                var owner = correlations.get(variable.id);
                if (owner == null) {
                    throw new UnsupportedOperationException("Unsupported correlation variable outside its scope");
                }
                return liftField(owner, access.getField().getIndex(), lift, tagged);
            }
            case RexLiteral literal -> literal(out, literal);
            case RexSubQuery subQuery -> {
                switch (subQuery.getKind()) {
                    case EXISTS -> {
                        out.put("op", "exists");
                        out.set("input", liftRel(subQuery.rel, lift, tagged));
                    }
                    case IN -> {
                        if (subQuery.getOperands().size() != 1) {
                            throw new UnsupportedOperationException("Unsupported multi-column IN subquery");
                        }
                        out.put("op", "any");
                        out.put("cmp", "=");
                        out.set("scalar", liftRex(subQuery.getOperands().get(0), context, lift, tagged));
                        out.set("input", liftRel(subQuery.rel, lift, tagged));
                    }
                    default -> throw new UnsupportedOperationException("Unsupported subquery: "
                            + subQuery.getKind());
                }
            }
            case RexCall call -> {
                var args = MAPPER.createArrayNode();
                for (var operand : call.getOperands()) {
                    args.add(liftRex(operand, context, lift, tagged));
                }
                switch (call.getKind()) {
                    case NOT -> {
                        out.put("op", "not");
                        out.set("arg", args.get(0));
                    }
                    case OR -> {
                        out.put("op", "or");
                        out.set("args", args);
                    }
                    case AND -> {
                        out.put("op", "and");
                        out.set("args", args);
                    }
                    default -> {
                        out.put("op", "scalar");
                        out.put("operator", call.getOperator().getName());
                        out.set("args", args);
                    }
                }
            }
            default -> throw new UnsupportedOperationException("Unsupported expression construct: " + rex);
        }
        return out;
    }

    private void literal(ObjectNode node, RexLiteral literal) {
        var sqlType = literal.getType().getSqlTypeName();
        if (literal.isNull()) {
            node.put("op", "null");
            if (sqlType == org.apache.calcite.sql.type.SqlTypeName.BOOLEAN) {
                node.put("type", "bool");
            } else if (sqlType == org.apache.calcite.sql.type.SqlTypeName.INTEGER) {
                node.put("type", "int");
            } else if (literal.getType() instanceof org.qed.RelType.VarType) {
                var refs = typeRefs.get(literal.getType().toString());
                if (refs == null || refs.isEmpty()) {
                    throw new UnsupportedOperationException("Unsupported unbound NULL type variable: "
                            + literal.getType());
                }
                node.put("type", "same_as");
                var out = node.putArray("refs");
                for (var ref : refs) {
                    out.addObject().put("of", ref.of()).put("ordinal", ref.ordinal());
                }
            } else {
                throw new UnsupportedOperationException("Unsupported NULL literal type: " + literal.getType());
            }
        } else if (sqlType == org.apache.calcite.sql.type.SqlTypeName.INTEGER) {
            node.put("op", "int");
            node.put("value", literal.getValueAs(Integer.class));
        } else if (sqlType == org.apache.calcite.sql.type.SqlTypeName.BOOLEAN) {
            node.put("op", Boolean.TRUE.equals(literal.getValueAs(Boolean.class)) ? "true" : "false");
        } else {
            throw new UnsupportedOperationException("Unsupported literal: " + literal);
        }
    }

    private void singletonMembership(ObjectNode node, String op, RexRN.Or or,
                                     boolean tagged, RelRN context) {
        if (!(or.sources().get(0) instanceof RexRN.Pred pred)
                || !pred.operator().getName().equals("=") || pred.sources().size() != 2) {
            throw new UnsupportedOperationException("Unsupported unary or operand: expected binary equality");
        }
        node.put("op", op);
        var args = node.putArray("args");
        pred.sources().forEach(s -> args.add(rex(s, tagged, context)));
    }

    private void list(ObjectNode node, String op, Seq<RexRN> sources, boolean tagged, RelRN context) {
        node.put("op", op);
        var args = MAPPER.createArrayNode();
        sources.forEach(s -> args.add(rex(s, tagged, context)));
        node.set("args", args);
    }

    private void symbol(ObjectNode node, String op, String name, Seq<RexRN> sources, boolean tagged, RelRN context) {
        node.put("op", op);
        node.put("name", name);
        node.set("args", arguments(sources, tagged, op.equals("pred"), context));
    }

    private ArrayNode arguments(Seq<RexRN> sources, boolean tagged, boolean wholePredicateRow, RelRN context) {
        var args = MAPPER.createArrayNode();
        var row = wholePredicateRow ? wholeRow(sources) : null;
        if (row != null) {
            var node = MAPPER.createObjectNode();
            node.put("op", "row");
            var of = MAPPER.createArrayNode();
            if (tagged && sources.get(0) instanceof RexRN.JoinField) {
                of.add(id(context));
            } else {
                row.forEach(r -> of.add(id(r)));
            }
            node.set("rels", of);
            args.add(node);
            return args;
        }
        sources.forEach(s -> args.add(rex(s, tagged, context)));
        return args;
    }

    private static java.util.List<RelRN> wholeRow(Seq<RexRN> sources) {
        if (sources.isEmpty()) {
            return null;
        }
        if (sources.allMatch(s -> s instanceof RexRN.Field)) {
            var source = ((RexRN.Field) sources.get(0)).source();
            var count = source.semantics().getRowType().getFieldCount();
            for (var i = 0; i < sources.size(); i++) {
                var field = (RexRN.Field) sources.get(i);
                if (!field.source().equals(source) || field.ordinal() != i) {
                    return null;
                }
            }
            return sources.size() == count ? java.util.List.of(source) : null;
        }
        if (sources.allMatch(s -> s instanceof RexRN.JoinField)) {
            var first = (RexRN.JoinField) sources.get(0);
            var total = first.left().semantics().getRowType().getFieldCount()
                    + first.right().semantics().getRowType().getFieldCount();
            for (var i = 0; i < sources.size(); i++) {
                var field = (RexRN.JoinField) sources.get(i);
                if (!field.left().equals(first.left()) || !field.right().equals(first.right())
                        || field.ordinal() != i) {
                    return null;
                }
            }
            return sources.size() == total ? java.util.List.of(first.left(), first.right()) : null;
        }
        return null;
    }

    private static UnsupportedOperationException unsupported(RelRN rel) {
        return new UnsupportedOperationException("Unsupported relational operator: " + constructName(rel.getClass()));
    }

    private static String constructName(Class<?> type) {
        if (type.isSynthetic()) {
            return "Java lambda";
        }
        if (type.isAnonymousClass()) {
            return "anonymous " + type.getInterfaces()[0].getSimpleName();
        }
        return type.getSimpleName();
    }
}
