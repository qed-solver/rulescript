package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestProjectAggregateMerge(t *testing.T) {
	e := newEnv(t)
	rule := loadRule(t, "ProjectAggregateMerge")
	scan, cols := e.scan("t", 3)
	md := e.f.Metadata()
	groupCol := md.AddColumn("group", types.Int)
	keyedScan := e.f.ConstructProject(scan, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.v(cols[0]), groupCol),
	}, opt.MakeColSet(cols...))
	countCol := md.AddColumn("count", types.Int)
	sumCol := md.AddColumn("sum", types.Int)
	maxCol := md.AddColumn("max", types.Int)
	grouping := &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(groupCol)}
	aggs := memo.AggregationsExpr{
		e.f.ConstructAggregationsItem(e.f.ConstructSum(e.v(cols[0])), countCol),
		e.f.ConstructAggregationsItem(e.f.ConstructMax(e.v(cols[0])), sumCol),
		e.f.ConstructAggregationsItem(e.f.ConstructMin(e.v(cols[0])), maxCol),
	}
	beforeAgg := e.f.ConstructGroupBy(keyedScan, aggs, grouping)
	before := e.f.ConstructProject(beforeAgg, memo.EmptyProjectionsExpr, opt.MakeColSet(groupCol, countCol, maxCol))

	wantAgg := e.f.ConstructGroupBy(keyedScan, memo.AggregationsExpr{aggs[0], aggs[2]}, grouping)
	want := e.f.ConstructProject(wantAgg, memo.EmptyProjectionsExpr, opt.MakeColSet(groupCol, countCol, maxCol))
	e.expectRewrite(rule, before, want)

	nearMiss := e.f.ConstructProject(beforeAgg, memo.EmptyProjectionsExpr, opt.MakeColSet(groupCol, countCol, sumCol))
	e.expectNoMatch(rule, nearMiss)
}
