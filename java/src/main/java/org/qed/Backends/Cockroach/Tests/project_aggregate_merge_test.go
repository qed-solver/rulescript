package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestProjectAggregateMerge(t *testing.T) {
	rule := loadRule(t, "ProjectAggregateMerge")
	e := newEnv(t)
	s, c := e.scan("t", 3)
	f1 := e.f.Metadata().AddColumn("f1", types.Int)
	f2 := e.f.Metadata().AddColumn("f2", types.Int)
	f3 := e.f.Metadata().AddColumn("f3", types.Int)
	grouping := func() *memo.GroupingPrivate {
		return &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(c[0])}
	}

	wide := e.f.ConstructGroupBy(s, memo.AggregationsExpr{
		e.f.ConstructAggregationsItem(e.f.ConstructCount(e.v(c[0])), f1),
		e.f.ConstructAggregationsItem(e.f.ConstructCount(e.v(c[0])), f2),
		e.f.ConstructAggregationsItem(e.f.ConstructCount(e.v(c[0])), f3),
	}, grouping())
	before := e.f.ConstructProject(wide, nil, opt.MakeColSet(c[0], f1, f3))

	narrow := e.f.ConstructGroupBy(s, memo.AggregationsExpr{
		e.f.ConstructAggregationsItem(e.f.ConstructCount(e.v(c[0])), f1),
		e.f.ConstructAggregationsItem(e.f.ConstructCount(e.v(c[0])), f3),
	}, grouping())
	want := e.f.ConstructProject(narrow, nil, opt.MakeColSet(c[0], f1, f3))
	e.expectRewrite(rule, before, want)

	e.expectNoMatch(rule, e.f.ConstructProject(wide, nil, opt.MakeColSet(c[0], f1, f2)))
	e.expectNoMatch(rule, e.f.ConstructProject(wide, nil, opt.MakeColSet(f1, f3)))
	e.expectNoMatch(rule, e.f.ConstructProject(
		e.f.ConstructSelect(wide, e.filters(e.f.ConstructEq(e.v(c[0]), e.num(1)))),
		nil, opt.MakeColSet(c[0], f1, f3)))
}
