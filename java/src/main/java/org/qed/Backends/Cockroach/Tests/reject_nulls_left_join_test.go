package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestRejectNullsLeftJoin(t *testing.T) {
	rule := loadRule(t, "RejectNullsLeftJoin")

	t.Run("right column rejects null extension", func(t *testing.T) {
		e := newEnv(t)
		left, leftCols := e.scan("u", 1)
		right, rightCols := e.scan("t", 1)
		on := e.filters(e.f.ConstructEq(e.v(leftCols[0]), e.v(rightCols[0])))
		filter := e.filters(e.f.ConstructIsNot(e.v(rightCols[0]), memo.NullSingleton))
		beforeJoin := e.f.ConstructLeftJoin(left, right, on, memo.EmptyJoinPrivate)
		before := e.f.ConstructSelect(beforeJoin, filter)
		wantJoin := e.f.ConstructInnerJoin(left, right, on, memo.EmptyJoinPrivate)
		want := e.f.ConstructSelect(wantJoin, filter)
		e.expectRewrite(rule, before, want)
	})

	t.Run("left column is not null rejecting", func(t *testing.T) {
		e := newEnv(t)
		left, leftCols := e.scan("u", 1)
		right, rightCols := e.scan("t", 1)
		on := e.filters(e.f.ConstructEq(e.v(leftCols[0]), e.v(rightCols[0])))
		filter := e.filters(e.f.ConstructIsNot(e.v(leftCols[0]), memo.NullSingleton))
		join := e.f.ConstructLeftJoin(left, right, on, memo.EmptyJoinPrivate)
		plan := e.f.ConstructSelect(join, filter)
		e.expectNoMatch(rule, plan)
	})
}
