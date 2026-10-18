package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestSimplifyJoinNotNullEquality(t *testing.T) {
	rule := loadRule(t, "SimplifyJoinNotNullEquality")

	t.Run("rewrites_not_false_equality_on_non_null_columns", func(t *testing.T) {
		e := newEnv(t)
		left, leftCols := e.scan("u", 1)
		right, rightCols := e.scan("t", 1)
		equality := e.f.ConstructEq(e.v(leftCols[0]), e.v(rightCols[0]))
		rest := e.f.ConstructEq(e.v(leftCols[0]), e.num(7))
		before := e.f.ConstructInnerJoin(left, right, e.filters(e.f.ConstructIsNot(equality, memo.FalseSingleton), rest), &memo.JoinPrivate{})
		want := e.f.ConstructInnerJoin(left, right, e.filters(equality, rest), &memo.JoinPrivate{})
		e.expectRewrite(rule, before, want)
	})

	t.Run("does_not_rewrite_nullable_columns", func(t *testing.T) {
		e := newEnv(t)
		left, leftCols := e.scan("u", 2)
		right, rightCols := e.scan("t", 2)
		equality := e.f.ConstructEq(e.v(leftCols[1]), e.v(rightCols[1]))
		rest := e.f.ConstructEq(e.v(leftCols[0]), e.v(rightCols[0]))
		plan := e.f.ConstructInnerJoin(left, right, e.filters(e.f.ConstructIsNot(equality, memo.FalseSingleton), rest), &memo.JoinPrivate{})
		e.expectNoMatch(rule, plan)
	})
}
