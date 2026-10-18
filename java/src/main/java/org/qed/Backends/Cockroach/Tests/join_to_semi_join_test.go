package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestJoinToSemiJoin(t *testing.T) {
	rule := loadRule(t, "JoinToSemiJoin")
	e := newEnv(t)
	left, leftCols := e.scan("u", 1)
	right, rightCols := e.scan("t", 1)
	grouping := opt.MakeColSet(rightCols[0])
	agg := e.f.ConstructGroupBy(right, memo.EmptyAggregationsExpr, &memo.GroupingPrivate{GroupingCols: grouping})
	cond := e.filters(e.f.ConstructEq(e.v(leftCols[0]), e.v(rightCols[0])))
	join := e.f.ConstructInnerJoin(left, agg, cond, &memo.JoinPrivate{})
	before := e.f.ConstructProject(join, memo.EmptyProjectionsExpr, opt.MakeColSet(leftCols[0]))

	semi := e.f.ConstructSemiJoin(left, right, cond, &memo.JoinPrivate{})
	after := e.f.ConstructProject(semi, memo.EmptyProjectionsExpr, opt.MakeColSet(leftCols[0]))
	e.expectRewriteUpToFreshColumns(rule, before, after)

	left2, leftCols2 := e.scan("u", 1)
	right2, rightCols2 := e.scan("t", 2)
	grouping2 := opt.MakeColSet(rightCols2...)
	agg2 := e.f.ConstructGroupBy(right2, memo.EmptyAggregationsExpr, &memo.GroupingPrivate{GroupingCols: grouping2})
	cond2 := e.filters(e.f.ConstructEq(e.v(leftCols2[0]), e.v(rightCols2[0])))
	join2 := e.f.ConstructInnerJoin(left2, agg2, cond2, &memo.JoinPrivate{})
	nearMiss := e.f.ConstructProject(join2, memo.EmptyProjectionsExpr, opt.MakeColSet(leftCols2[0]))
	e.expectNoMatch(rule, nearMiss)

}
