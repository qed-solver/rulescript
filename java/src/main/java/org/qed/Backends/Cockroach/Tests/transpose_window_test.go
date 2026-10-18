package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestTransposeWindow(t *testing.T) {
	rule := loadRule(t, "TransposeWindow")
	e := newEnv(t)

	build := func(innerKeys []int) (memo.RelExpr, memo.RelExpr) {
		source, sc := e.scan("t", 3)
		innerInput, ic := e.scan("t", 3)
		innerW := e.f.Metadata().AddColumn("inner_w", types.Int)
		innerSet := opt.ColSet{}
		for _, k := range innerKeys {
			innerSet.Add(ic[k])
		}
		innerAgg := e.f.ConstructGroupBy(innerInput, memo.AggregationsExpr{
			e.f.ConstructAggregationsItem(e.f.ConstructMax(e.v(ic[2])), innerW),
		}, &memo.GroupingPrivate{GroupingCols: innerSet})
		var innerOn memo.FiltersExpr
		for _, k := range innerKeys {
			innerOn = append(innerOn, e.f.ConstructFiltersItem(e.f.ConstructEq(e.v(sc[k]), e.v(ic[k]))))
		}
		innerJoin := e.f.ConstructInnerJoin(source, innerAgg, innerOn, &memo.JoinPrivate{})
		innerWindow := e.f.ConstructProject(innerJoin, nil, opt.MakeColSet(sc[0], sc[1], sc[2], innerW))

		outerInput, oc := e.scan("t", 3)
		outerW := e.f.Metadata().AddColumn("outer_w", types.Int)
		outerAgg := e.f.ConstructGroupBy(outerInput, memo.AggregationsExpr{
			e.f.ConstructAggregationsItem(e.f.ConstructMax(e.v(oc[2])), outerW),
		}, &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(oc[0])})
		outerJoin := e.f.ConstructInnerJoin(innerWindow, outerAgg,
			e.filters(e.f.ConstructEq(e.v(sc[0]), e.v(oc[0]))), &memo.JoinPrivate{})
		before := e.f.ConstructProject(outerJoin, nil, opt.MakeColSet(sc[0], sc[1], sc[2], innerW, outerW))

		outerFirst := e.f.ConstructInnerJoin(source, outerAgg,
			e.filters(e.f.ConstructEq(e.v(sc[0]), e.v(oc[0]))), &memo.JoinPrivate{})
		outerWindow := e.f.ConstructProject(outerFirst, nil, opt.MakeColSet(sc[0], sc[1], sc[2], outerW))
		innerSecond := e.f.ConstructInnerJoin(outerWindow, innerAgg, innerOn, &memo.JoinPrivate{})
		want := e.f.ConstructProject(innerSecond, nil, opt.MakeColSet(sc[0], sc[1], sc[2], innerW, outerW))
		return before, want
	}

	before, want := build([]int{0, 1})
	e.expectRewriteUpToColumnNames(rule, before, want)

	nearMiss, _ := build([]int{0, 2})
	e.expectNoMatch(rule, nearMiss)
}
