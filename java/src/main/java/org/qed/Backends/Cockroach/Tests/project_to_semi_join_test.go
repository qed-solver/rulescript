package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestProjectToSemiJoin(t *testing.T) {
	e := newEnv(t)
	rule := loadRule(t, "ProjectToSemiJoin")
	left, lc := e.scan("u", 2)
	right, rc := e.scan("t", 2)
	group := e.f.ConstructGroupBy(right, memo.EmptyAggregationsExpr, &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(rc[1])})
	cond := e.filters(e.f.ConstructEq(e.v(lc[0]), e.v(rc[1])))
	join := e.f.ConstructInnerJoin(left, group, cond, &memo.JoinPrivate{})
	p1 := e.f.ConstructProjectionsItem(e.v(lc[1]), e.f.Metadata().AddColumn("first", types.Int))
	p2 := e.f.ConstructProjectionsItem(e.v(lc[0]), e.f.Metadata().AddColumn("second", types.Int))
	before := e.f.ConstructProject(join, memo.ProjectionsExpr{p1, p2}, opt.ColSet{})
	semiCond := e.filters(e.f.ConstructEq(e.v(lc[0]), e.v(rc[1])))
	semi := e.f.ConstructSemiJoin(left, right, semiCond, &memo.JoinPrivate{})
	w1 := e.f.ConstructProjectionsItem(e.v(lc[1]), p1.Col)
	w2 := e.f.ConstructProjectionsItem(e.v(lc[0]), p2.Col)
	want := e.f.ConstructProject(e.f.ConstructProject(semi, nil, opt.MakeColSet(lc[0], lc[1])), memo.ProjectionsExpr{w1, w2}, opt.ColSet{})
	e.expectRewrite(rule, before, want)

	nearMissGroup := e.f.ConstructGroupBy(right, memo.EmptyAggregationsExpr, &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(rc[0])})
	nearMissJoin := e.f.ConstructInnerJoin(left, nearMissGroup, cond, &memo.JoinPrivate{})
	nearMiss := e.f.ConstructProject(nearMissJoin, memo.ProjectionsExpr{p1, p2}, opt.ColSet{})
	e.expectNoMatch(rule, nearMiss)
}
