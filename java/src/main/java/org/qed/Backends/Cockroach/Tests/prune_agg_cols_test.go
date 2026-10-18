package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestPruneAggCols(t *testing.T) {
	e := newEnv(t)
	rule := loadRule(t, "PruneAggCols")
	build := func(selectPruned bool) (memo.RelExpr, memo.RelExpr) {
		scan, cols := e.scan("t", 3)
		fCol := e.f.Metadata().AddColumn("f_result", types.Int)
		gCol := e.f.Metadata().AddColumn("g_result", types.Int)
		grouped := e.f.ConstructGroupBy(scan, memo.AggregationsExpr{
			e.f.ConstructAggregationsItem(e.f.ConstructSum(e.v(cols[1])), fCol),
			e.f.ConstructAggregationsItem(e.f.ConstructMax(e.v(cols[2])), gCol),
		}, &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(cols[0])})
		pass := opt.MakeColSet(cols[0], fCol)
		if selectPruned {
			pass.Add(gCol)
		}
		before := e.f.ConstructProject(grouped, memo.ProjectionsExpr{}, pass)

		pruned := e.f.ConstructGroupBy(scan, memo.AggregationsExpr{
			e.f.ConstructAggregationsItem(e.f.ConstructSum(e.v(cols[1])), fCol),
		}, &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(cols[0])})
		want := e.f.ConstructProject(pruned, memo.ProjectionsExpr{}, opt.MakeColSet(cols[0], fCol))
		return before, want
	}
	before, want := build(false)
	e.expectRewrite(rule, before, want)
	nearMiss, _ := build(true)
	e.expectNoMatch(rule, nearMiss)
}
