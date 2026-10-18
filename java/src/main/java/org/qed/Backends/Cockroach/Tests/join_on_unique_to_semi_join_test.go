package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestJoinOnUniqueToSemiJoin(t *testing.T) {
	rule := loadRule(t, "JoinOnUniqueToSemiJoin")
	e := newEnv(t)
	l, lc := e.scan("u", 1)
	r, rc := e.scan("t", 1)
	on := e.filters(e.f.ConstructEq(e.v(lc[0]), e.v(rc[0])))
	beforeJoin := e.f.ConstructInnerJoin(l, r, on, &memo.JoinPrivate{})
	before := e.f.ConstructProject(beforeJoin, memo.ProjectionsExpr{}, beforeJoin.Relational().OutputCols.Intersection(l.Relational().OutputCols))
	afterJoin := e.f.ConstructSemiJoin(l, r, on, &memo.JoinPrivate{})
	after := e.f.ConstructProject(afterJoin, memo.ProjectionsExpr{}, afterJoin.Relational().OutputCols)
	e.expectRewrite(rule, before, after)

	badOn := e.filters(e.f.ConstructEq(e.v(lc[0]), e.num(1)))
	badJoin := e.f.ConstructInnerJoin(l, r, badOn, &memo.JoinPrivate{})
	nearMiss := e.f.ConstructProject(badJoin, memo.ProjectionsExpr{}, badJoin.Relational().OutputCols.Intersection(l.Relational().OutputCols))
	e.expectNoMatch(rule, nearMiss)
}
