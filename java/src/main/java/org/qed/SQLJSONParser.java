package org.qed;

import com.fasterxml.jackson.databind.ObjectMapper;
import kala.collection.mutable.MutableArrayList;
import kala.collection.mutable.MutableList;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.schema.SchemaPlus;
import org.apache.calcite.sql2rel.RelFieldTrimmer;
import org.apache.calcite.tools.RelBuilder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.List;

public class SQLJSONParser {

    private final MutableList<RelNode> relNodes;

    public SQLJSONParser() {
        relNodes = new MutableArrayList<>();
    }

    public SQLJSONParser(List<RelNode> nodes) {
        relNodes = MutableArrayList.from(nodes);
    }

    public void parseDML(SchemaPlus context, String dml) throws Exception {
        RawPlanner planner = new RawPlanner(context);
        relNodes.append(planner.rel(planner.parse(dml)));
    }

    public void dumpOutput(RelBuilder builder, String path) throws IOException {
        var trimmer = new RelFieldTrimmer(null, builder);
        var nodes = relNodes.map(trimmer::trim);
        var scanner = new RelScanner();
        nodes.forEach(scanner::scan);
        var pruner = new RelPruner(scanner.usages().toImmutableMap());
        var rNodes = nodes.map(pruner);
        new ObjectMapper().writerWithDefaultPrettyPrinter()
                .writeValue(new File(path + ".json"), JSONSerializer.serialize(rNodes));
        RelRacketShuttle.dumpToRacket(rNodes.asJava(), Paths.get(path + ".rkt"));
    }
}
