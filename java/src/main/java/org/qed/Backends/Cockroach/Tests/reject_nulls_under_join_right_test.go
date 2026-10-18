package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestRejectNullsUnderJoinRight(t *testing.T) {
	rule := loadRule(t, "RejectNullsUnderJoinRight")
	t.Run("match", func(t *testing.T) {
		e := newEnv(t)
		left, leftCols := e.scan("u", 1)
		right, rightCols := e.scan("t", 1)
		eq := e.f.ConstructEq(e.v(leftCols[0]), e.v(rightCols[0]))
		notNull := e.f.ConstructIsNot(e.v(rightCols[0]), memo.NullSingleton)
		before := e.f.ConstructInnerJoin(left, right, e.filters(e.f.ConstructAnd(eq, notNull)), &memo.JoinPrivate{})
		filteredRight := e.f.ConstructSelect(right, e.filters(e.f.ConstructIsNot(e.v(rightCols[0]), memo.NullSingleton)))
		want := e.f.ConstructInnerJoin(left, filteredRight, e.filters(eq, notNull), &memo.JoinPrivate{})
		e.expectRewrite(rule, before, want)
	})
	t.Run("near_miss_without_null_rejection", func(t *testing.T) {
		e := newEnv(t)
		left, leftCols := e.scan("u", 1)
		right, rightCols := e.scan("t", 1)
		eq := e.f.ConstructEq(e.v(leftCols[0]), e.v(rightCols[0]))
		other := e.f.ConstructEq(e.v(rightCols[0]), e.num(1))
		before := e.f.ConstructInnerJoin(left, right, e.filters(e.f.ConstructAnd(eq, other)), &memo.JoinPrivate{})
		e.expectNoMatch(rule, before)
	})
}
