package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestPushLeakproofJoinIntoPermeableBarrierRight(t *testing.T) {
	rule := loadRule(t, "PushLeakproofJoinIntoPermeableBarrierRight")
	e := newEnv(t)
	left, lc := e.scan("t", 2)
	right, rc := e.scan("u", 2)
	barrier := e.f.ConstructProject(right, memo.EmptyProjectionsExpr, right.Relational().OutputCols)
	cond := e.filters(
		e.f.ConstructEq(e.v(lc[0]), e.v(rc[0])),
		e.f.ConstructEq(e.v(lc[1]), e.v(rc[1])),
	)
	before := e.f.ConstructInnerJoin(left, barrier, cond, &memo.JoinPrivate{})
	join := e.f.ConstructInnerJoin(left, right, cond, &memo.JoinPrivate{})
	want := e.f.ConstructProject(join, memo.EmptyProjectionsExpr, join.Relational().OutputCols)
	e.expectRewrite(rule, before, want)

	nearMiss := e.f.ConstructLeftJoin(left, barrier, cond, &memo.JoinPrivate{})
	e.expectNoMatch(rule, nearMiss)
}
