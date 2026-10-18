package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestPruneGroupByCols(t *testing.T) {
	rule := loadRule(t, "PruneGroupByCols")
	e := newEnv(t)
	if _, err := e.cat.ExecuteDDL("CREATE TABLE four (a INT PRIMARY KEY, b INT, c INT, d INT)"); err != nil {
		t.Fatal(err)
	}
	scan, cols := e.scan("four", 4)
	sumCol := e.f.Metadata().AddColumn("sum", types.Int)
	minCol := e.f.Metadata().AddColumn("min", types.Int)
	aggs := memo.AggregationsExpr{
		e.f.ConstructAggregationsItem(e.f.ConstructSumInt(e.v(cols[1])), sumCol),
		e.f.ConstructAggregationsItem(e.f.ConstructMin(e.v(cols[2])), minCol),
	}
	grouped := e.f.ConstructGroupBy(scan, aggs, &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(cols[0])})
	before := e.f.ConstructProject(grouped, nil, opt.MakeColSet(cols[0], sumCol, minCol))

	pruned := e.f.ConstructProject(scan, nil, opt.MakeColSet(cols[0], cols[1], cols[2]))
	wantGrouped := e.f.ConstructGroupBy(pruned, memo.AggregationsExpr{
		e.f.ConstructAggregationsItem(e.f.ConstructSumInt(e.v(cols[1])), sumCol),
		e.f.ConstructAggregationsItem(e.f.ConstructMin(e.v(cols[2])), minCol),
	}, &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(cols[0])})
	want := e.f.ConstructProject(wantGrouped, nil, opt.MakeColSet(cols[0], sumCol, minCol))
	e.expectRewriteUpToFreshColumns(rule, before, want)

	nearScan, nearCols := e.scan("four", 4)
	nearSumCol := e.f.Metadata().AddColumn("sum", types.Int)
	nearMinCol := e.f.Metadata().AddColumn("min", types.Int)
	nearGrouped := e.f.ConstructGroupBy(nearScan, memo.AggregationsExpr{
		e.f.ConstructAggregationsItem(e.f.ConstructSumInt(e.v(nearCols[1])), nearSumCol),
		e.f.ConstructAggregationsItem(e.f.ConstructMin(e.v(nearCols[2])), nearMinCol),
	}, &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(nearCols[0])})
	nearMiss := e.f.ConstructProject(nearGrouped, nil, opt.MakeColSet(nearCols[0], nearSumCol))
	e.expectNoMatch(rule, nearMiss)
}
