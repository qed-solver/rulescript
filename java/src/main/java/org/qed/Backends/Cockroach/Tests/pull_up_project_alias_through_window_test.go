package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestPullUpProjectAliasThroughWindow(t *testing.T) {
	e := newEnv(t)
	rule := loadRule(t, "PullUpProjectAliasThroughWindow")

	window := func(usesAlias bool) (memo.RelExpr, memo.RelExpr) {
		base, cols := e.scan("nn", 2)
		x := e.f.Metadata().AddColumn("x", types.Int)
		lower := e.f.ConstructProject(base, memo.ProjectionsExpr{
			e.f.ConstructProjectionsItem(e.f.ConstructPlus(e.v(cols[1]), e.num(1)), x),
		}, opt.MakeColSet(cols[0], cols[1]))

		base2, cols2 := e.scan("nn", 2)
		x2 := e.f.Metadata().AddColumn("x", types.Int)
		lower2 := e.f.ConstructProject(base2, memo.ProjectionsExpr{
			e.f.ConstructProjectionsItem(e.f.ConstructPlus(e.v(cols2[1]), e.num(1)), x2),
		}, opt.MakeColSet(cols2[0], cols2[1]))
		w := e.f.Metadata().AddColumn("w", types.Int)
		operand := cols2[1]
		if usesAlias {
			operand = x2
		}
		agg := e.f.ConstructGroupBy(lower2, memo.AggregationsExpr{
			e.f.ConstructAggregationsItem(e.f.ConstructSum(e.v(operand)), w),
		}, &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(cols2[0])})

		joined := e.f.ConstructInnerJoin(lower, agg,
			e.filters(e.f.ConstructEq(e.v(cols[0]), e.v(cols2[0]))), &memo.JoinPrivate{})
		win := e.f.ConstructProject(joined, memo.EmptyProjectionsExpr, opt.MakeColSet(cols[0], cols[1], x, w))
		before := e.f.ConstructProject(win, memo.EmptyProjectionsExpr, opt.MakeColSet(cols[0], cols[1], x, w))

		newLower := e.f.ConstructProject(base, nil, opt.MakeColSet(cols[0], cols[1]))
		newLower2 := e.f.ConstructProject(base2, nil, opt.MakeColSet(cols2[0], cols2[1]))
		agg2 := e.f.ConstructGroupBy(newLower2, memo.AggregationsExpr{
			e.f.ConstructAggregationsItem(e.f.ConstructSum(e.v(operand)), w),
		}, &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(cols2[0])})
		joined2 := e.f.ConstructInnerJoin(newLower, agg2,
			e.filters(e.f.ConstructEq(e.v(cols[0]), e.v(cols2[0]))), &memo.JoinPrivate{})
		win2 := e.f.ConstructProject(joined2, memo.EmptyProjectionsExpr, opt.MakeColSet(cols[0], cols[1], w))
		want := e.f.ConstructProject(win2, memo.ProjectionsExpr{
			e.f.ConstructProjectionsItem(e.f.ConstructPlus(e.v(cols[1]), e.num(1)), x),
		}, opt.MakeColSet(cols[0], cols[1], w))
		return before, want
	}

	before, want := window(false)
	e.expectRewriteUpToFreshColumns(rule, before, want)
	nearMiss, _ := window(true)
	e.expectNoMatch(rule, nearMiss)
}
