package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestPushFilterIntoJoinLeft(t *testing.T) {
	rule := loadRule(t, "PushFilterIntoJoinLeft")
	t.Run("pushes left-only conjunct", func(t *testing.T) {
		e := newEnv(t)
		left, leftCols := e.scan("u", 1)
		right, rightCols := e.scan("t", 1)
		leftPredicate := e.f.ConstructEq(e.v(leftCols[0]), e.num(7))
		joinPredicate := e.f.ConstructEq(e.v(leftCols[0]), e.v(rightCols[0]))
		before := e.f.ConstructInnerJoin(left, right, e.filters(leftPredicate, joinPredicate), &memo.JoinPrivate{})
		filteredLeft := e.f.ConstructSelect(left, e.filters(leftPredicate))
		want := e.f.ConstructInnerJoin(filteredLeft, right, e.filters(joinPredicate), &memo.JoinPrivate{})
		e.expectRewrite(rule, before, want)
	})
	t.Run("pushes left-only conjunct written after the join conjunct", func(t *testing.T) {
		e := newEnv(t)
		left, leftCols := e.scan("u", 1)
		right, rightCols := e.scan("t", 1)
		spanningPredicate := e.f.ConstructEq(e.v(leftCols[0]), e.v(rightCols[0]))
		leftPredicate := e.f.ConstructEq(e.v(leftCols[0]), e.num(7))
		before := e.f.ConstructInnerJoin(left, right, e.filters(spanningPredicate, leftPredicate), &memo.JoinPrivate{})
		want := e.f.ConstructInnerJoin(e.f.ConstructSelect(left, e.filters(leftPredicate)), right, e.filters(spanningPredicate), &memo.JoinPrivate{})
		e.expectRewrite(rule, before, want)
	})
	t.Run("does not push when every conjunct spans both inputs", func(t *testing.T) {
		e := newEnv(t)
		left, leftCols := e.scan("u", 1)
		right, rightCols := e.scan("t", 1)
		first := e.f.ConstructEq(e.v(leftCols[0]), e.v(rightCols[0]))
		second := e.f.ConstructLt(e.v(leftCols[0]), e.v(rightCols[0]))
		before := e.f.ConstructInnerJoin(left, right, e.filters(first, second), &memo.JoinPrivate{})
		e.expectNoMatch(rule, before)
	})
}
