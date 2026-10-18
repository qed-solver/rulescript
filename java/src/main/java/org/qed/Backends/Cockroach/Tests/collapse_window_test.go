package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

type collapseWindowParts struct {
	left, a1in, a2in memo.RelExpr
	lc, a1c, a2c     []opt.ColumnID
	f1, f2           opt.ColumnID
}

func collapseWindowPlan(e *env, p collapseWindowParts, outerJoinLeft bool) memo.RelExpr {
	agg1 := e.f.ConstructGroupBy(p.a1in,
		memo.AggregationsExpr{e.f.ConstructAggregationsItem(e.f.ConstructMax(e.v(p.a1c[1])), p.f1)},
		&memo.GroupingPrivate{GroupingCols: opt.MakeColSet(p.a1c[0])})
	joined1 := e.f.ConstructInnerJoin(p.left, agg1,
		e.filters(e.f.ConstructEq(e.v(p.lc[0]), e.v(p.a1c[0]))), &memo.JoinPrivate{})
	childWin := e.f.ConstructProject(joined1, memo.ProjectionsExpr{},
		opt.MakeColSet(p.lc[0], p.lc[1], p.f1))
	agg2 := e.f.ConstructGroupBy(p.a2in,
		memo.AggregationsExpr{e.f.ConstructAggregationsItem(e.f.ConstructMax(e.v(p.a2c[1])), p.f2)},
		&memo.GroupingPrivate{GroupingCols: opt.MakeColSet(p.a2c[0])})
	on := e.filters(e.f.ConstructEq(e.v(p.lc[0]), e.v(p.a2c[0])))
	var outer memo.RelExpr
	if outerJoinLeft {
		outer = e.f.ConstructLeftJoin(childWin, agg2, on, &memo.JoinPrivate{})
	} else {
		outer = e.f.ConstructInnerJoin(childWin, agg2, on, &memo.JoinPrivate{})
	}
	return e.f.ConstructProject(outer, memo.ProjectionsExpr{},
		opt.MakeColSet(p.lc[0], p.lc[1], p.f1, p.f2))
}

func TestCollapseWindow(t *testing.T) {
	rule := loadRule(t, "CollapseWindow")
	e := newEnv(t)
	var p collapseWindowParts
	p.left, p.lc = e.scan("nn", 2)
	p.a1in, p.a1c = e.scan("nn", 2)
	p.a2in, p.a2c = e.scan("nn", 2)
	p.f1 = e.f.Metadata().AddColumn("f1", types.Int)
	p.f2 = e.f.Metadata().AddColumn("f2", types.Int)
	before := collapseWindowPlan(e, p, false)

	grouped := e.f.ConstructGroupBy(p.a1in,
		memo.AggregationsExpr{
			e.f.ConstructAggregationsItem(e.f.ConstructMax(e.v(p.a1c[1])), p.f1),
			e.f.ConstructAggregationsItem(e.f.ConstructMax(e.v(p.a1c[1])), p.f2),
		},
		&memo.GroupingPrivate{GroupingCols: opt.MakeColSet(p.a1c[0])})
	joined := e.f.ConstructInnerJoin(p.left, grouped,
		e.filters(e.f.ConstructEq(e.v(p.lc[0]), e.v(p.a1c[0]))), &memo.JoinPrivate{})
	want := e.f.ConstructProject(joined, memo.ProjectionsExpr{},
		opt.MakeColSet(p.lc[0], p.lc[1], p.f1, p.f2))

	e.expectRewrite(rule, before, want)
	e.expectNoMatch(rule, collapseWindowPlan(e, p, true))
}
