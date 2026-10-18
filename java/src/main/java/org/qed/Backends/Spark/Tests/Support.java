package org.qed.spark;

import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.catalyst.InternalRow;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.AttributeReference;
import org.apache.spark.sql.catalyst.expressions.GenericInternalRow;
import org.apache.spark.sql.catalyst.expressions.NamedExpression$;
import org.apache.spark.sql.catalyst.plans.logical.LocalRelation;
import org.apache.spark.sql.catalyst.plans.logical.LogicalPlan;
import org.apache.spark.sql.types.IntegerType$;
import org.apache.spark.sql.types.Metadata;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

final class Support {
    private Support() { }

    private static SparkSession session;

    static synchronized SparkSession session() {
        if (session == null) {
            session = SparkSession.builder().master("local[2]").appName("rulescript-spark-tests")
                    .config("spark.ui.enabled", "false").config("spark.sql.shuffle.partitions", "2")
                    .config("spark.sql.adaptive.enabled", "false").config("spark.sql.ansi.enabled", "false")
                    .getOrCreate();
            session.sparkContext().setLogLevel("ERROR");
        }
        return session;
    }

    private static Boolean executable;

    static synchronized boolean canExecute() {
        if (executable == null) {
            try {
                session();
                executable = true;
            } catch (Exception | LinkageError e) {
                System.out.println("results are not compared: no Spark session could start (" + e.getMessage() + ")");
                executable = false;
            }
        }
        return executable;
    }

    static Path generated() {
        return Path.of(System.getProperty("rulescript.generated", "Generated"));
    }

    static Attribute attribute(String name, boolean nullable) {
        return new AttributeReference(name, IntegerType$.MODULE$, nullable, Metadata.empty(),
                NamedExpression$.MODULE$.newExprId(), Catalyst.<String>seq(List.of()));
    }

    static LocalRelation relation(String name, int arity, List<Integer> nonNull, Random random) {
        var attrs = new ArrayList<Attribute>();
        for (int i = 0; i < arity; i++) {
            attrs.add(attribute(name + "_" + i, !nonNull.contains(i)));
        }
        var rows = new ArrayList<InternalRow>();
        for (int r = 0, n = 2 + random.nextInt(6); r < n; r++) {
            var cells = new Object[arity];
            for (int i = 0; i < arity; i++) {
                cells[i] = attrs.get(i).nullable() && random.nextInt(5) == 0 ? null : random.nextInt(4);
            }
            rows.add(new GenericInternalRow(cells));
        }
        return new LocalRelation(Catalyst.seq(attrs), Catalyst.seq(rows), false, Match.noStream());
    }

    static List<String> run(LogicalPlan plan) {
        var rows = org.apache.spark.sql.classic.Dataset$.MODULE$
                .ofRows((org.apache.spark.sql.classic.SparkSession) session(), plan).collectAsList();
        var out = new ArrayList<String>();
        rows.forEach(row -> out.add(row.toString()));
        java.util.Collections.sort(out);
        return out;
    }
}
