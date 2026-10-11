package rulescript_test

import (
	"strings"
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestPruneAggCols(t *testing.T) {
	rule := loadRule(t, "PruneAggCols")
	e := newEnv(t)
	s, cols := e.scan("t", 3)
	fcol := e.f.Metadata().AddColumn("f", types.Int)
	gcol := e.f.Metadata().AddColumn("g", types.Int)
	aggs := memo.AggregationsExpr{
		e.f.ConstructAggregationsItem(e.f.ConstructCount(e.v(cols[1])), fcol),
		e.f.ConstructAggregationsItem(e.f.ConstructCount(e.v(cols[2])), gcol),
	}
	grouped := e.f.ConstructGroupBy(s, aggs, &memo.GroupingPrivate{
		GroupingCols: opt.MakeColSet(cols[0]),
	})
	before := e.f.ConstructProject(grouped, nil, opt.MakeColSet(cols[0], fcol))
	pruned := e.f.ConstructGroupBy(s, aggs[:1], &memo.GroupingPrivate{
		GroupingCols: opt.MakeColSet(cols[0]),
	})
	want := e.f.ConstructProject(pruned, nil, opt.MakeColSet(cols[0], fcol))
	e.expectRewrite(rule, before, want)

	e.expectNoMatch(rule, e.f.ConstructProject(grouped, nil, opt.MakeColSet(cols[0], fcol, gcol)))
	wrongAgg := e.f.ConstructGroupBy(s, memo.AggregationsExpr{
		e.f.ConstructAggregationsItem(e.f.ConstructCount(e.v(cols[0])), fcol),
		aggs[1],
	}, &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(cols[0])})
	e.expectNoMatch(rule, e.f.ConstructProject(wrongAgg, nil, opt.MakeColSet(cols[0], fcol)))
}

func TestSQLPruneAggCols(t *testing.T) {
	sql := "SELECT a, cnt FROM (SELECT a, count(b) AS cnt, count(c) AS unused FROM t GROUP BY a) AS q"
	before, _ := planFor(t, sql, nil)
	if strings.Count(before, "count [as=") != 2 {
		t.Fatalf("expected two aggregation columns before rewrite:\n%s", before)
	}
	got, fired := planFor(t, sql, nil, "PruneAggCols")
	if strings.Join(fired, ",") != "PruneAggCols" || strings.Count(got, "count [as=") != 1 {
		t.Fatalf("expected one aggregation column after rewrite, got %v:\n%s", fired, got)
	}
}
